package com.mdobak.vacuums.ui.map

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.mdobak.vacuums.data.*
import com.mdobak.vacuums.ui.theme.AppColors
import kotlinx.serialization.json.JsonElement
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class ShapeKind(val label: String, val isLine: Boolean) {
    Zone("Zone", false),
    NoGo("No-go zone", false),
    NoMop("No-mop zone", false),
    Wall("Virtual wall", true),
    Threshold("Threshold", true),
    Curtain("Curtain", true),
    Ramp("Ramp", false),
    SplitLine("Split line", true),
}

/** One editable shape. Lines use [a] and [b]; rectangles use [rect]. All values in cm. */
data class EditShape(
    val id: Int,
    val kind: ShapeKind,
    val a: Offset = Offset.Zero,
    val b: Offset = Offset.Zero,
    val rect: Rect = Rect.Zero,
    /** Ramp only: index of the corner (TL, TR, BR, BL) where the ramp edge starts. */
    val rotation: Int = 0,
) {
    fun corners(): List<Offset> = listOf(rect.topLeft, rect.topRight, rect.bottomRight, rect.bottomLeft)
    fun rampCorners(): List<Offset> = corners().let { c -> List(4) { c[(it + rotation) % 4] } }
}

private enum class Handle { Move, A, B, C0, C1, C2, C3 }

/** State and gesture logic of the shape editor. */
@Stable
class ShapeEditor : MapGestureHandler {
    val shapes = mutableStateListOf<EditShape>()
    var selectedId by mutableStateOf<Int?>(null)
    var dirty by mutableStateOf(false)
    private var nextId = 1
    private var drag: Pair<Int, Handle>? = null
    /** Called when the user taps outside all shapes. */
    var onEmptyTap: ((Offset, Float) -> Unit)? = null

    val selected: EditShape? get() = shapes.firstOrNull { it.id == selectedId }

    fun clear() { shapes.clear(); selectedId = null; dirty = false }

    fun addLine(kind: ShapeKind, a: Offset, b: Offset, select: Boolean = true, markDirty: Boolean = true): EditShape {
        val s = EditShape(nextId++, kind, a = a, b = b)
        shapes += s
        if (select) selectedId = s.id
        if (markDirty) dirty = true
        return s
    }

    fun addRect(kind: ShapeKind, r: Rect, rotation: Int = 0, select: Boolean = true, markDirty: Boolean = true): EditShape {
        val s = EditShape(nextId++, kind, rect = r, rotation = rotation)
        shapes += s
        if (select) selectedId = s.id
        if (markDirty) dirty = true
        return s
    }

    /** Adds a new shape in the middle of [view] (cm). */
    fun addNew(kind: ShapeKind, view: Rect) {
        val c = view.center
        val size = (min(view.width, view.height) * 0.28f).coerceIn(60f, 400f)
        if (kind.isLine) addLine(kind, Offset(c.x - size / 2, c.y), Offset(c.x + size / 2, c.y))
        else addRect(kind, Rect(c.x - size / 2, c.y - size / 2, c.x + size / 2, c.y + size / 2))
    }

    fun deleteSelected() {
        val id = selectedId ?: return
        shapes.removeAll { it.id == id }
        selectedId = null
        dirty = true
    }

    fun rotateSelectedRamp() {
        val s = selected ?: return
        replace(s.copy(rotation = (s.rotation + 1) % 4))
        dirty = true
    }

    private fun replace(s: EditShape) {
        val i = shapes.indexOfFirst { it.id == s.id }
        if (i >= 0) shapes[i] = s
    }

    private fun hit(s: EditShape, p: Offset, tol: Float): Handle? {
        if (s.kind.isLine) {
            if ((p - s.a).getDistance() < tol) return Handle.A
            if ((p - s.b).getDistance() < tol) return Handle.B
            if (distToSegment(p, s.a, s.b) < tol * 0.8f) return Handle.Move
            return null
        }
        val cs = s.corners()
        // The resize handles are only active on the selected shape.
        if (s.id == selectedId) {
            cs.forEachIndexed { i, c -> if ((p - c).getDistance() < tol) return Handle.entries[3 + i] }
        }
        if (s.rect.inflate(tol * 0.3f).contains(p)) return Handle.Move
        return null
    }

    private fun findHit(p: Offset, tol: Float): Pair<EditShape, Handle>? {
        selected?.let { s -> hit(s, p, tol)?.let { return s to it } }
        for (s in shapes.asReversed()) hit(s, p, tol)?.let { return s to it }
        return null
    }

    override fun onDragStart(cm: Offset, toleranceCm: Float): Boolean {
        val h = findHit(cm, toleranceCm) ?: return false
        selectedId = h.first.id
        drag = h.first.id to h.second
        return true
    }

    override fun onDrag(deltaCm: Offset) {
        val (id, handle) = drag ?: return
        val s = shapes.firstOrNull { it.id == id } ?: return
        val n = when (handle) {
            Handle.Move -> if (s.kind.isLine) s.copy(a = s.a + deltaCm, b = s.b + deltaCm) else s.copy(rect = s.rect.translate(deltaCm))
            Handle.A -> s.copy(a = s.a + deltaCm)
            Handle.B -> s.copy(b = s.b + deltaCm)
            else -> {
                val i = handle.ordinal - 3
                val cs = s.corners().toMutableList()
                cs[i] = cs[i] + deltaCm
                val opp = cs[(i + 2) % 4]
                val moved = cs[i]
                val r = Rect(min(opp.x, moved.x), min(opp.y, moved.y), max(opp.x, moved.x), max(opp.y, moved.y))
                if (r.width < 20f || r.height < 20f) s else s.copy(rect = r)
            }
        }
        replace(n)
        dirty = true
    }

    override fun onDragEnd() { drag = null }

    override fun onTap(cm: Offset, toleranceCm: Float) {
        val h = findHit(cm, toleranceCm)
        if (h != null) selectedId = h.first.id
        else {
            selectedId = null
            onEmptyTap?.invoke(cm, toleranceCm)
        }
    }

    fun draw(scope: DrawScope, t: MapTransform, c: AppColors) = with(scope) {
        val handleR = 7.dp.toPx()
        for (s in shapes) {
            val sel = s.id == selectedId
            val color = colorOf(s.kind, c)
            if (s.kind.isLine) {
                val a = t.toScreen(s.a); val b = t.toScreen(s.b)
                when (s.kind) {
                    ShapeKind.Threshold, ShapeKind.Curtain -> drawAnnotationLine(a, b, s.kind == ShapeKind.Curtain, c)
                    ShapeKind.SplitLine -> {
                        drawLine(Color.Black.copy(alpha = 0.5f), a, b, 5.dp.toPx(), StrokeCap.Round)
                        drawLine(Color.White, a, b, 3.dp.toPx(), StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 5.dp.toPx())))
                    }
                    else -> {
                        drawLine(Color.Black.copy(alpha = 0.45f), a, b, 7.dp.toPx(), StrokeCap.Round)
                        drawLine(color, a, b, 4.5.dp.toPx(), StrokeCap.Round)
                    }
                }
                if (sel || s.kind == ShapeKind.SplitLine) {
                    handle(a, handleR, color); handle(b, handleR, color)
                }
            } else {
                val cs = s.corners().map { t.toScreen(it) }
                if (s.kind == ShapeKind.Ramp) {
                    drawRamp(s.rampCorners().map { t.toScreen(it) }, c)
                } else {
                    val path = Path().apply { moveTo(cs[0].x, cs[0].y); cs.drop(1).forEach { lineTo(it.x, it.y) }; close() }
                    drawPath(path, color.copy(alpha = if (sel) 0.28f else 0.18f))
                    drawPath(path, color, style = Stroke(width = (if (sel) 2.5 else 1.5).dp.toPx()))
                }
                if (sel) cs.forEach { handle(it, handleR, color) }
            }
        }
    }

    private fun DrawScope.handle(p: Offset, r: Float, color: Color) {
        drawCircle(Color.Black.copy(alpha = 0.35f), r + 2.dp.toPx(), p)
        drawCircle(Color.White, r, p)
        drawCircle(color, r * 0.45f, p)
    }

    fun loadRestrictions(e: JsonElement?) {
        clear()
        e["virtualWalls"].arr?.forEach { w ->
            val p = w["points"]
            addLine(ShapeKind.Wall, pt(p["pA"]), pt(p["pB"]), select = false, markDirty = false)
        }
        e["restrictedZones"].arr?.forEach { z ->
            val p = z["points"]
            val pts = listOf(pt(p["pA"]), pt(p["pB"]), pt(p["pC"]), pt(p["pD"]))
            addRect(if (z["type"].str == "mop") ShapeKind.NoMop else ShapeKind.NoGo, bbox(pts), select = false, markDirty = false)
        }
        dirty = false
    }

    fun restrictionsJson(): JsonElement = jsonObj(
        "virtualWalls" to shapes.filter { it.kind == ShapeKind.Wall }.map {
            mapOf("points" to mapOf("pA" to xy(it.a), "pB" to xy(it.b)))
        },
        "restrictedZones" to shapes.filter { it.kind == ShapeKind.NoGo || it.kind == ShapeKind.NoMop }.map {
            mapOf("type" to if (it.kind == ShapeKind.NoMop) "mop" else "regular", "points" to rectPoints(it.rect))
        },
    )

    fun loadAnnotations(map: ValetudoMap) {
        clear()
        for (e in map.entities) {
            val p = e.points
            when (e.type) {
                "threshold", "curtain" -> if (p.size >= 4) addLine(
                    if (e.type == "threshold") ShapeKind.Threshold else ShapeKind.Curtain,
                    Offset(p[0], p[1]), Offset(p[2], p[3]), select = false, markDirty = false,
                )
                "ramp" -> if (p.size >= 8) {
                    val pts = List(4) { Offset(p[it * 2], p[it * 2 + 1]) }
                    val r = bbox(pts)
                    val corners = listOf(r.topLeft, r.topRight, r.bottomRight, r.bottomLeft)
                    val rot = corners.indices.minByOrNull { (corners[it] - pts[0]).getDistance() } ?: 0
                    addRect(ShapeKind.Ramp, r, rot, select = false, markDirty = false)
                }
            }
        }
        dirty = false
    }

    fun annotationsJson(): JsonElement = jsonArr(shapes.mapNotNull { s ->
        when (s.kind) {
            ShapeKind.Threshold -> mapOf("type" to "threshold", "points" to listOf(xy(s.a), xy(s.b)))
            ShapeKind.Curtain -> mapOf("type" to "curtain", "points" to listOf(xy(s.a), xy(s.b)))
            ShapeKind.Ramp -> mapOf("type" to "ramp", "points" to s.rampCorners().map { xy(it) })
            else -> null
        }
    })

    fun zonesJson(): List<Map<String, Any>> = shapes.filter { it.kind == ShapeKind.Zone }.map { mapOf("points" to rectPoints(it.rect)) }

    companion object {
        fun colorOf(k: ShapeKind, c: AppColors): Color = when (k) {
            ShapeKind.Zone -> c.zone
            ShapeKind.NoGo, ShapeKind.Wall -> c.noGo
            ShapeKind.NoMop -> c.noMop
            ShapeKind.Threshold, ShapeKind.Curtain, ShapeKind.Ramp -> c.annotation
            ShapeKind.SplitLine -> Color.White
        }

        private fun pt(e: JsonElement?) = Offset((e["x"].dbl ?: 0.0).toFloat(), (e["y"].dbl ?: 0.0).toFloat())
        private fun xy(o: Offset) = mapOf("x" to o.x.roundToInt(), "y" to o.y.roundToInt())
        private fun bbox(pts: List<Offset>) = Rect(pts.minOf { it.x }, pts.minOf { it.y }, pts.maxOf { it.x }, pts.maxOf { it.y })
        private fun rectPoints(r: Rect) = mapOf(
            "pA" to xy(r.topLeft), "pB" to xy(r.topRight), "pC" to xy(r.bottomRight), "pD" to xy(r.bottomLeft),
        )

        fun distToSegment(p: Offset, a: Offset, b: Offset): Float {
            val ab = b - a
            val len2 = ab.x * ab.x + ab.y * ab.y
            if (len2 == 0f) return (p - a).getDistance()
            val t = (((p.x - a.x) * ab.x + (p.y - a.y) * ab.y) / len2).coerceIn(0f, 1f)
            return (p - (a + ab * t)).getDistance()
        }
    }
}
