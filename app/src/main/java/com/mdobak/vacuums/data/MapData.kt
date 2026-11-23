package com.mdobak.vacuums.data

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

class MapLayer(
    val type: String,
    val segmentId: String?,
    val name: String?,
    val material: String?,
    val active: Boolean,
    /** Runs as x, y, count triples, in map pixels. */
    val runs: IntArray,
    val midX: Float,
    val midY: Float,
    val area: Int?,
)

class MapEntity(
    val type: String,
    val cls: String,
    /** x0, y0, x1, y1 ... in centimeters. */
    val points: FloatArray,
    val meta: JsonObject?,
) {
    val angle: Float? get() = meta["angle"].dbl?.toFloat()
}

/** A parsed Valetudo map. Layers use map pixels of [pixelSize] cm; entities use cm. */
class ValetudoMap(
    val sizeX: Int,
    val sizeY: Int,
    val pixelSize: Int,
    val layers: List<MapLayer>,
    val entities: List<MapEntity>,
    val nonce: String,
) {
    val segments: List<MapLayer> = layers.filter { it.type == "segment" && it.segmentId != null }

    // Bounding box of all layers in map pixels.
    val minX: Int
    val minY: Int
    val width: Int
    val height: Int

    /** Grid of cell codes: 0 = empty, 1 = floor, 2 = wall, 10 + i = segment i. */
    val grid: IntArray

    /** Palette slot (0..5) of each segment so that adjacent segments get different colors. */
    val segmentColorSlot: IntArray

    init {
        var x0 = Int.MAX_VALUE; var y0 = Int.MAX_VALUE; var x1 = Int.MIN_VALUE; var y1 = Int.MIN_VALUE
        for (l in layers) {
            val r = l.runs
            var i = 0
            while (i + 2 < r.size) {
                val x = r[i]; val y = r[i + 1]; val c = r[i + 2]
                if (x < x0) x0 = x
                if (x + c - 1 > x1) x1 = x + c - 1
                if (y < y0) y0 = y
                if (y > y1) y1 = y
                i += 3
            }
        }
        if (x0 == Int.MAX_VALUE) { x0 = 0; y0 = 0; x1 = 0; y1 = 0 }
        minX = x0 - 2; minY = y0 - 2
        width = x1 - x0 + 5; height = y1 - y0 + 5
        grid = IntArray(width * height)
        fun fill(l: MapLayer, code: Int) {
            val r = l.runs
            var i = 0
            while (i + 2 < r.size) {
                val y = r[i + 1] - minY
                val xs = r[i] - minX
                val base = y * width
                for (k in 0 until r[i + 2]) grid[base + xs + k] = code
                i += 3
            }
        }
        layers.filter { it.type == "floor" }.forEach { fill(it, 1) }
        segments.forEachIndexed { idx, l -> fill(l, 10 + idx) }
        layers.filter { it.type == "wall" }.forEach { fill(it, 2) }
        segmentColorSlot = colorSegments()
    }

    private fun colorSegments(): IntArray {
        val n = segments.size
        val adj = Array(n) { BooleanArray(n) }
        val reach = 4
        for (y in 0 until height) for (x in 0 until width) {
            val a = grid[y * width + x] - 10
            if (a < 0) continue
            for (d in 1..reach) {
                if (x + d < width) { val b = grid[y * width + x + d] - 10; if (b >= 0 && b != a) { adj[a][b] = true; adj[b][a] = true } }
                if (y + d < height) { val b = grid[(y + d) * width + x] - 10; if (b >= 0 && b != a) { adj[a][b] = true; adj[b][a] = true } }
            }
        }
        val slots = IntArray(n) { -1 }
        val order = (0 until n).sortedByDescending { i -> adj[i].count { it } }
        for (i in order) {
            val used = (0 until n).filter { adj[i][it] && slots[it] >= 0 }.map { slots[it] }.toSet()
            slots[i] = (0..5).firstOrNull { it !in used } ?: (i % 6)
        }
        return slots
    }

    fun segmentIndexAtPixel(px: Int, py: Int): Int {
        val x = px - minX; val y = py - minY
        if (x < 0 || y < 0 || x >= width || y >= height) return -1
        val c = grid[y * width + x]
        return if (c >= 10) c - 10 else -1
    }

    fun segmentAtCm(xCm: Float, yCm: Float): MapLayer? {
        val i = segmentIndexAtPixel((xCm / pixelSize).toInt(), (yCm / pixelSize).toInt())
        return if (i >= 0) segments[i] else null
    }

    /** The segment with a pixel nearest to [xCm]/[yCm], in a radius of [radiusCm]. */
    fun segmentNearCm(xCm: Float, yCm: Float, radiusCm: Float): MapLayer? {
        val cx = (xCm / pixelSize).toInt(); val cy = (yCm / pixelSize).toInt()
        val r = (radiusCm / pixelSize).toInt().coerceIn(0, 40)
        var best = -1; var bestD = Int.MAX_VALUE
        for (dy in -r..r) for (dx in -r..r) {
            val d = dx * dx + dy * dy
            if (d > r * r || d >= bestD) continue
            val i = segmentIndexAtPixel(cx + dx, cy + dy)
            if (i >= 0) { best = i; bestD = d }
        }
        return if (best >= 0) segments[best] else null
    }

    fun entities(type: String) = entities.filter { it.type == type }
    val robot: MapEntity? get() = entities.firstOrNull { it.type == "robot_position" }
    val charger: MapEntity? get() = entities.firstOrNull { it.type == "charger_location" }

    val isEmpty get() = layers.isEmpty()

    companion object {
        fun parse(e: JsonElement): ValetudoMap {
            val layers = e["layers"].arr?.map { l ->
                val md = l["metaData"]
                val compressed = l["compressedPixels"].arr
                val runs: IntArray = if (compressed != null && compressed.isNotEmpty()) {
                    IntArray(compressed.size) { compressed[it].int ?: 0 }
                } else {
                    // Legacy format: x, y pairs.
                    val p = l["pixels"].arr ?: emptyList()
                    val out = IntArray(p.size / 2 * 3)
                    var j = 0
                    var i = 0
                    while (i + 1 < p.size) { out[j] = p[i].int ?: 0; out[j + 1] = p[i + 1].int ?: 0; out[j + 2] = 1; j += 3; i += 2 }
                    out
                }
                val dims = l["dimensions"]
                MapLayer(
                    type = l["type"].str ?: "floor",
                    segmentId = md["segmentId"].str,
                    name = md["name"].str,
                    material = md["material"].str,
                    active = md["active"].bool == true,
                    runs = runs,
                    midX = (dims["x"]["mid"].dbl ?: 0.0).toFloat(),
                    midY = (dims["y"]["mid"].dbl ?: 0.0).toFloat(),
                    area = md["area"].int,
                )
            } ?: emptyList()
            val entities = e["entities"].arr?.map { en ->
                val pts = en["points"].arr
                MapEntity(
                    type = en["type"].str ?: "",
                    cls = en["__class"].str ?: "",
                    points = FloatArray(pts?.size ?: 0) { (pts!![it].dbl ?: 0.0).toFloat() },
                    meta = en["metaData"].obj,
                )
            } ?: emptyList()
            return ValetudoMap(
                sizeX = e["size"]["x"].int ?: 0,
                sizeY = e["size"]["y"].int ?: 0,
                pixelSize = e["pixelSize"].int ?: 5,
                layers = layers,
                entities = entities,
                nonce = e["metaData"]["nonce"].str ?: "",
            )
        }
    }
}
