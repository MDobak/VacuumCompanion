package com.mdobak.vacuums.ui.map

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mdobak.vacuums.data.MapEntity
import com.mdobak.vacuums.data.ValetudoMap
import com.mdobak.vacuums.ui.theme.AppColors
import com.mdobak.vacuums.ui.theme.Theme
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Pan and zoom state. Screen = (mapPixel - (minX, minY)) * scale + offset. */
@Stable
class MapViewport {
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)
    var size by mutableStateOf(IntSize.Zero)
    var fitScale = 1f
    internal var fittedFor: String? = null

    fun fit(map: ValetudoMap, view: IntSize, padding: Float) {
        if (view.width == 0 || view.height == 0) return
        val w = view.width - 2 * padding
        val h = view.height - 2 * padding
        val s = min(w / map.width, h / map.height).coerceAtLeast(0.1f)
        fitScale = s
        scale = s
        offset = Offset((view.width - map.width * s) / 2f, (view.height - map.height * s) / 2f)
    }

    fun zoomAt(centroid: Offset, zoom: Float) {
        val newScale = (scale * zoom).coerceIn(fitScale * 0.6f, fitScale * 14f)
        val f = newScale / scale
        offset = centroid - (centroid - offset) * f
        scale = newScale
    }
}

/** Maps centimeters to screen pixels. */
class MapTransform(val map: ValetudoMap, private val vp: MapViewport) {
    val pxPerCm: Float get() = vp.scale / map.pixelSize
    fun toScreen(xCm: Float, yCm: Float) = Offset(
        (xCm / map.pixelSize - map.minX) * vp.scale + vp.offset.x,
        (yCm / map.pixelSize - map.minY) * vp.scale + vp.offset.y,
    )
    fun toScreen(p: Offset) = toScreen(p.x, p.y)
    fun toCm(screen: Offset) = Offset(
        ((screen.x - vp.offset.x) / vp.scale + map.minX) * map.pixelSize,
        ((screen.y - vp.offset.y) / vp.scale + map.minY) * map.pixelSize,
    )
    fun lenToScreen(cm: Float) = cm * pxPerCm
    fun lenToCm(px: Float) = px / pxPerCm
}

/** Callbacks for touch on the map. Positions are in centimeters. */
interface MapGestureHandler {
    fun onTap(cm: Offset, toleranceCm: Float) {}
    /** Return true to start a drag of an editable item. */
    fun onDragStart(cm: Offset, toleranceCm: Float): Boolean = false
    fun onDrag(deltaCm: Offset) {}
    fun onDragEnd() {}
}

data class MapLayerOptions(
    val showPath: Boolean = true,
    val coverage: Boolean = false,
    val showObstacles: Boolean = true,
    val showRestrictions: Boolean = true,
    val showAnnotations: Boolean = true,
    val showLabels: Boolean = true,
    val showActiveZones: Boolean = true,
    val showGoToTarget: Boolean = true,
)

@Composable
fun rememberMapViewport() = remember { MapViewport() }

@Composable
fun MapCanvas(
    map: ValetudoMap,
    modifier: Modifier = Modifier,
    viewport: MapViewport = rememberMapViewport(),
    selected: List<String> = emptyList(),
    showOrder: Boolean = false,
    options: MapLayerOptions = MapLayerOptions(),
    handler: MapGestureHandler? = null,
    interactive: Boolean = true,
    fitPadding: Float = 48f,
    overlay: DrawScope.(MapTransform) -> Unit = {},
) {
    val colors = Theme.colors
    val selectedSet = selected.toSet()
    val base = remember(map, colors, selectedSet) { renderBase(map, colors, selectedSet) }
    val paths = remember(map) { buildPaths(map, "path") }
    val predicted = remember(map) { buildPaths(map, "predicted_path") }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val slop = LocalViewConfiguration.current.touchSlop
    val currentHandler by rememberUpdatedState(handler)
    val mapKey = "${map.width / 25},${map.height / 25}"

    val currentMap by rememberUpdatedState(map)
    LaunchedEffect(map.width / 25, map.height / 25, viewport.size) {
        if (viewport.size.width > 0 && viewport.fittedFor != mapKey) {
            viewport.fit(map, viewport.size, fitPadding)
            viewport.fittedFor = mapKey
        }
    }

    Box(
        modifier
            .onSizeChanged { s ->
                if (viewport.size != s) {
                    viewport.size = s
                    viewport.fittedFor = null
                }
            }
            .then(if (interactive) Modifier.pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val t = MapTransform(currentMap, viewport)
                    val tol = t.lenToCm(with(density) { 22.dp.toPx() })
                    var dragging = currentHandler?.onDragStart(t.toCm(down.position), tol) == true
                    var moved = false
                    var multi = false
                    var pan = Offset.Zero
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        if (pressed == 0) break
                        if (pressed > 1) multi = true
                        if (dragging && !multi) {
                            val ch = event.changes.firstOrNull { it.id == down.id } ?: continue
                            val d = ch.positionChange()
                            if (d != Offset.Zero) {
                                currentHandler?.onDrag(Offset(t.lenToCm(d.x), t.lenToCm(d.y)))
                                ch.consume()
                            }
                        } else {
                            if (dragging) { currentHandler?.onDragEnd(); dragging = false }
                            val zoom = event.calculateZoom()
                            val p = event.calculatePan()
                            if (!moved) {
                                pan += p
                                if (pan.getDistance() > slop || zoom != 1f) moved = true
                            }
                            if (moved) {
                                viewport.zoomAt(event.calculateCentroid(), zoom)
                                viewport.offset += p
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                    if (dragging) currentHandler?.onDragEnd()
                    else if (!moved && !multi) currentHandler?.onTap(t.toCm(down.position), tol)
                }
            } else Modifier)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val t = MapTransform(map, viewport)
            val s = viewport.scale
            withTransform({
                translate(viewport.offset.x, viewport.offset.y)
                scale(s, s, Offset.Zero)
            }) {
                drawImage(base, filterQuality = FilterQuality.None)
                // Paths are in map pixel space relative to (minX, minY).
                if (options.coverage) {
                    drawPath(paths, colors.mapCoverage, style = Stroke(width = 30f / map.pixelSize, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                if (options.showPath) {
                    drawPath(paths, colors.mapPath.copy(alpha = 0.75f),
                        style = Stroke(width = 1.1f.dp.toPx() / s, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                drawPath(predicted, colors.mapPath.copy(alpha = 0.6f),
                    style = Stroke(width = 1.5f.dp.toPx() / s, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx() / s, 4.dp.toPx() / s))))
            }
            drawEntities(map, t, colors, options)
            if (options.showLabels) drawLabels(map, t, measurer, selected, showOrder, colors)
            overlay(t)
            drawDockAndRobot(map, t, colors)
        }
    }
}

private fun blend(a: Int, b: Int, f: Float): Int {
    val ia = 1 - f
    val r = ((a shr 16 and 0xFF) * ia + (b shr 16 and 0xFF) * f).toInt()
    val g = ((a shr 8 and 0xFF) * ia + (b shr 8 and 0xFF) * f).toInt()
    val bl = ((a and 0xFF) * ia + (b and 0xFF) * f).toInt()
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
}

fun renderBase(map: ValetudoMap, c: AppColors, selected: Set<String>): ImageBitmap {
    val w = max(1, map.width)
    val h = max(1, map.height)
    val px = IntArray(w * h)
    val floor = c.mapFloor.toArgb()
    val wall = c.mapWall.toArgb()
    val bg = c.mapBackground.toArgb()
    val white = if (c.isDark) Color.White.toArgb() else Color.Black.toArgb()
    val segColors = IntArray(map.segments.size) { i ->
        val col = c.mapSegments[map.segmentColorSlot[i] % c.mapSegments.size].toArgb()
        val id = map.segments[i].segmentId
        when {
            selected.isEmpty() -> col
            id in selected -> blend(col, if (c.isDark) Color.White.toArgb() else col, if (c.isDark) 0.22f else 0f)
            else -> blend(col, bg, 0.55f)
        }
    }
    for (i in px.indices) {
        val v = map.grid[i]
        px[i] = when {
            v == 0 -> 0
            v == 1 -> floor
            v == 2 -> wall
            else -> segColors[v - 10]
        }
    }
    // Make the outer edge of selected rooms visible.
    if (selected.isNotEmpty()) {
        val sel = BooleanArray(map.segments.size) { map.segments[it].segmentId in selected }
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val v = map.grid[y * w + x]
            if (v < 10 || !sel[v - 10]) continue
            val edge = map.grid[y * w + x - 1] != v || map.grid[y * w + x + 1] != v || map.grid[(y - 1) * w + x] != v || map.grid[(y + 1) * w + x] != v
            if (edge) px[y * w + x] = blend(px[y * w + x], white, 0.5f)
        }
    }
    val bmp = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    return bmp.asImageBitmap()
}

private fun buildPaths(map: ValetudoMap, type: String): Path {
    val p = Path()
    val ps = map.pixelSize.toFloat()
    for (e in map.entities) {
        if (e.type != type) continue
        val pts = e.points
        if (pts.size < 2) continue
        p.moveTo(pts[0] / ps - map.minX, pts[1] / ps - map.minY)
        var i = 2
        while (i + 1 < pts.size) {
            p.lineTo(pts[i] / ps - map.minX, pts[i + 1] / ps - map.minY)
            i += 2
        }
    }
    return p
}

fun DrawScope.drawPolygonCm(t: MapTransform, pts: FloatArray, fill: Color, stroke: Color, dashed: Boolean = false) {
    if (pts.size < 6) return
    val path = Path()
    val s0 = t.toScreen(pts[0], pts[1])
    path.moveTo(s0.x, s0.y)
    var i = 2
    while (i + 1 < pts.size) { val s = t.toScreen(pts[i], pts[i + 1]); path.lineTo(s.x, s.y); i += 2 }
    path.close()
    drawPath(path, fill)
    drawPath(path, stroke, style = Stroke(width = 1.5.dp.toPx(), pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())) else null))
}

private fun DrawScope.drawEntities(map: ValetudoMap, t: MapTransform, c: AppColors, o: MapLayerOptions) {
    for (e in map.entities) {
        when (e.type) {
            "no_go_area" -> if (o.showRestrictions) drawPolygonCm(t, e.points, c.noGo.copy(alpha = 0.18f), c.noGo.copy(alpha = 0.8f))
            "no_mop_area" -> if (o.showRestrictions) drawPolygonCm(t, e.points, c.noMop.copy(alpha = 0.18f), c.noMop.copy(alpha = 0.8f))
            "virtual_wall" -> if (o.showRestrictions && e.points.size >= 4) {
                drawLine(c.wall, t.toScreen(e.points[0], e.points[1]), t.toScreen(e.points[2], e.points[3]), 4.dp.toPx(), StrokeCap.Round)
            }
            "active_zone" -> if (o.showActiveZones) drawPolygonCm(t, e.points, c.zone.copy(alpha = 0.15f), c.zone.copy(alpha = 0.8f), dashed = true)
            "carpet" -> drawPolygonCm(t, e.points, Color.Gray.copy(alpha = 0.15f), Color.Gray.copy(alpha = 0.4f), dashed = true)
            "threshold", "curtain" -> if (o.showAnnotations && e.points.size >= 4) {
                drawAnnotationLine(t.toScreen(e.points[0], e.points[1]), t.toScreen(e.points[2], e.points[3]), e.type == "curtain", c)
            }
            "ramp" -> if (o.showAnnotations && e.points.size >= 8) {
                drawRamp(List(4) { t.toScreen(e.points[it * 2], e.points[it * 2 + 1]) }, c)
            }
            "go_to_target" -> if (o.showGoToTarget && e.points.size >= 2) drawPin(t.toScreen(e.points[0], e.points[1]), c.zone)
            "obstacle" -> if (o.showObstacles && e.points.size >= 2) drawObstacle(t.toScreen(e.points[0], e.points[1]), e)
        }
    }
}

fun DrawScope.drawAnnotationLine(a: Offset, b: Offset, curtain: Boolean, c: AppColors) {
    drawLine(Color.Black.copy(alpha = 0.5f), a, b, 6.dp.toPx(), StrokeCap.Round)
    drawLine(c.annotation, a, b, 3.5.dp.toPx(), StrokeCap.Round,
        pathEffect = if (curtain) PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())) else null)
}

fun DrawScope.drawRamp(corners: List<Offset>, c: AppColors) {
    val path = Path().apply {
        moveTo(corners[0].x, corners[0].y)
        corners.drop(1).forEach { lineTo(it.x, it.y) }
        close()
    }
    drawPath(path, c.annotation.copy(alpha = 0.18f))
    drawPath(path, c.annotation.copy(alpha = 0.8f), style = Stroke(1.5.dp.toPx()))
    // Stripes parallel to the first edge show the ramp direction.
    val a = corners[0]; val b = corners[1]; val d = corners[3]
    for (k in 1..5) {
        val f = k / 6f
        val p1 = a + (d - a) * f
        val p2 = b + (d - a) * f
        drawLine(c.annotation.copy(alpha = 0.6f), p1, p2, 1.dp.toPx())
    }
}

fun DrawScope.drawPin(p: Offset, color: Color) {
    val r = 9.dp.toPx()
    drawCircle(Color.Black.copy(alpha = 0.35f), r + 2.dp.toPx(), p + Offset(0f, 1.dp.toPx()))
    drawCircle(color, r, p)
    drawCircle(Color.White, r * 0.4f, p)
}

private fun DrawScope.drawObstacle(p: Offset, e: MapEntity) {
    val r = 5.dp.toPx()
    drawCircle(Color(0xFF1B1F27), r + 1.5.dp.toPx(), p)
    drawCircle(Color(0xFFF59E0B), r, p)
}

private fun DrawScope.drawDockAndRobot(map: ValetudoMap, t: MapTransform, c: AppColors) {
    map.charger?.let { ch ->
        if (ch.points.size >= 2) {
            val p = t.toScreen(ch.points[0], ch.points[1])
            val r = 9.dp.toPx()
            drawCircle(Color.Black.copy(alpha = 0.35f), r + 2.dp.toPx(), p)
            drawCircle(Color(0xFF2B6CB0), r, p)
            val bolt = Path().apply {
                moveTo(p.x + r * 0.12f, p.y - r * 0.62f)
                lineTo(p.x - r * 0.38f, p.y + r * 0.08f)
                lineTo(p.x - r * 0.02f, p.y + r * 0.08f)
                lineTo(p.x - r * 0.14f, p.y + r * 0.62f)
                lineTo(p.x + r * 0.38f, p.y - r * 0.1f)
                lineTo(p.x + r * 0.02f, p.y - r * 0.1f)
                close()
            }
            drawPath(bolt, Color.White)
        }
    }
    map.robot?.let { rb ->
        if (rb.points.size >= 2) {
            val p = t.toScreen(rb.points[0], rb.points[1])
            // Robot size is about 35 cm; keep a readable minimum.
            val r = max(t.lenToScreen(17f), 9.dp.toPx())
            drawCircle(c.glow.copy(alpha = 0.25f), r * 1.7f, p)
            drawCircle(Color.Black.copy(alpha = 0.4f), r + 1.5.dp.toPx(), p)
            drawCircle(Color(0xFFF4F6F9), r, p)
            drawCircle(Color(0xFF9AA4B2), r * 0.42f, p, style = Stroke(1.5.dp.toPx()))
            val a = Math.toRadians((rb.angle ?: 0f).toDouble())
            val dir = Offset(sin(a).toFloat(), -cos(a).toFloat())
            drawCircle(c.glow, r * 0.2f, p + dir * (r * 0.7f))
        }
    }
}

private fun DrawScope.drawLabels(
    map: ValetudoMap,
    t: MapTransform,
    measurer: androidx.compose.ui.text.TextMeasurer,
    selected: List<String>,
    showOrder: Boolean,
    c: AppColors,
) {
    val style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color.White)
    for (seg in map.segments) {
        val name = seg.name ?: "Room ${seg.segmentId}"
        val center = t.toScreen((seg.midX) * map.pixelSize, (seg.midY) * map.pixelSize)
        val order = selected.indexOf(seg.segmentId)
        val label = if (showOrder && order >= 0) "${order + 1}  $name" else name
        val layout = measurer.measure(label, style)
        val pad = 6.dp.toPx()
        val w = layout.size.width + pad * 2
        val h = layout.size.height + pad
        val tl = Offset(center.x - w / 2, center.y - h / 2)
        val sel = order >= 0
        drawRoundRect(
            if (sel) c.glow.copy(alpha = 0.95f) else Color(0xCC12161D),
            tl, Size(w, h), CornerRadius(h / 2),
        )
        drawText(layout, color = if (sel) Color(0xFF221402) else Color.White, topLeft = Offset(tl.x + pad, tl.y + pad / 2))
    }
}

/** Hit test of an obstacle near [cm]. */
fun ValetudoMap.obstacleAt(cm: Offset, tolCm: Float): MapEntity? =
    entities.filter { it.type == "obstacle" && it.points.size >= 2 }
        .minByOrNull { (Offset(it.points[0], it.points[1]) - cm).getDistance() }
        ?.takeIf { (Offset(it.points[0], it.points[1]) - cm).getDistance() < tolCm }

/** Visible area of the map, in cm. */
fun MapTransform.visibleRectCm(size: IntSize): Rect {
    val a = toCm(Offset.Zero)
    val b = toCm(Offset(size.width.toFloat(), size.height.toFloat()))
    return Rect(a, b)
}
