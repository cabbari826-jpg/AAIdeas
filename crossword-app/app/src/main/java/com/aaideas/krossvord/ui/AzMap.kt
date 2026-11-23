package com.aaideas.krossvord.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clipToBounds
import com.aaideas.krossvord.model.Place

/**
 * A small map of Azerbaijan (Natural Earth outline, public domain) with a route: travelled part
 * solid, the rest dashed, a dot and name for each stop, and the car where the player is.
 * One Canvas, no images, so it costs almost nothing on weak phones.
 */
@Composable
fun AzMap(places: List<Place>, position: Float, modifier: Modifier = Modifier, close: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val land = cs.secondaryContainer
    val border = cs.outline
    val done = cs.primary
    val todo = cs.outline
    val ink = cs.onSurface
    val dark = cs.background.red + cs.background.green + cs.background.blue < 1.5f
    val labelBg = if (dark) Color(0xCC1D1B20) else Color(0xCCFFFFFF)
    // Longitude is squeezed by cos(latitude) so the country keeps its shape.
    val k = remember { kotlin.math.cos(Math.toRadians(40.2)).toFloat() }
    // Close view: only the part of the country around the route, so the stops are bigger.
    val view = remember(places, close) { if (close) routeWindow(places, k) else floatArrayOf(AzMapShape.MIN_LON, AzMapShape.MAX_LON, AzMapShape.MIN_LAT, AzMapShape.MAX_LAT) }
    val minLon = view[0]; val maxLat = view[3]
    val w = (view[1] - view[0]) * k
    val h = view[3] - view[2]
    Canvas(modifier.aspectRatio(w / h).clipToBounds()) {
        fun at(lon: Float, lat: Float) = Offset(
            (lon - minLon) * k / w * size.width,
            (maxLat - lat) / h * size.height,
        )
        val dot = 7.dp.toPx()
        val thin = 2.5.dp.toPx()
        val thick = 4.5.dp.toPx()
        AzMapShape.POLYGONS.forEach { pts ->
            val path = Path()
            for (i in pts.indices step 2) {
                val o = at(pts[i], pts[i + 1])
                if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
            }
            path.close()
            drawPath(path, land)
            drawPath(path, border, style = Stroke(width = 1.dp.toPx()))
        }
        val pts = places.map { at(it.lon, it.lat) }
        val car = pointAt(pts, position)
        // Road ahead (dashed), then the travelled part on top.
        for (i in 0 until pts.lastIndex) drawLine(todo, pts[i], pts[i + 1], strokeWidth = thin, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx())))
        val full = position.toInt()
        for (i in 0 until full.coerceAtMost(pts.lastIndex)) drawLine(done, pts[i], pts[i + 1], strokeWidth = thick, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        if (full < pts.lastIndex) drawLine(done, pts[full], car, strokeWidth = thick, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        pts.forEachIndexed { i, o ->
            val reached = i <= position
            drawCircle(if (reached) done else cs.surface, radius = dot, center = o)
            drawCircle(if (reached) done else todo, radius = dot, center = o, style = Stroke(width = 2.dp.toPx()))
        }
        // Only the names that matter, so they stay big and readable: start, finish, where the car is and the next stop.
        val here = position.toInt().coerceAtMost(pts.lastIndex)
        val named = linkedSetOf(0, pts.lastIndex, (here + 1).coerceAtMost(pts.lastIndex), here)
        val carText = measurer.measure("🚗", TextStyle(fontSize = if (close) 26.sp else 20.sp))
        val carTop = Offset(car.x - carText.size.width / 2f, car.y - carText.size.height + 4f)
        // Labels never cover a stop, the car or another label: right, left, below or above the dot.
        val carBox = androidx.compose.ui.geometry.Rect(carTop, androidx.compose.ui.geometry.Size(carText.size.width.toFloat(), carText.size.height.toFloat()))
            .let { androidx.compose.ui.geometry.Rect(it.left + it.width * 0.1f, it.top + it.height * 0.25f, it.right - it.width * 0.1f, it.bottom - 4f) }
        val placed = mutableListOf(carBox)
        pts.forEach { placed += androidx.compose.ui.geometry.Rect(it, dot + 1f) }
        val pad = 3.dp.toPx()
        named.forEach { i ->
            val t = measurer.measure(places[i].name, TextStyle(fontSize = if (close) 14.sp else 12.sp, fontWeight = FontWeight.SemiBold, color = ink))
            val o = pts[i]
            val tw = t.size.width.toFloat(); val th = t.size.height.toFloat()
            val gap = dot + pad
            val far = gap + carBox.width / 2
            val spots = listOf(
                Offset(o.x + gap, o.y - th / 2), Offset(o.x - gap - tw, o.y - th / 2),
                Offset(o.x - tw / 2, o.y + gap), Offset(o.x - tw / 2, o.y - gap - th),
                Offset(o.x + far, o.y - th / 2), Offset(o.x - far - tw, o.y - th / 2),
                Offset(o.x + gap, o.y + gap / 2), Offset(o.x - gap - tw, o.y + gap / 2),
            )
            fun box(p: Offset) = androidx.compose.ui.geometry.Rect(p.x - pad, p.y - 1f, p.x + tw + pad, p.y + th + 1f)
            val at = spots.firstOrNull { p ->
                val b = box(p)
                b.left >= 0 && b.top >= 0 && b.right <= size.width && b.bottom <= size.height && placed.none { it.overlaps(b) }
            } ?: return@forEach
            val b = box(at)
            placed += b
            drawRoundRect(labelBg, topLeft = b.topLeft, size = b.size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f))
            drawText(t, topLeft = at)
        }
        drawText(carText, topLeft = carTop)
    }
}

/** [minLon, maxLon, minLat, maxLat] around the route with a margin, at least 45% of the country wide, kept inside the map. */
private fun routeWindow(places: List<Place>, k: Float): FloatArray {
    val fullW = (AzMapShape.MAX_LON - AzMapShape.MIN_LON)
    val fullH = (AzMapShape.MAX_LAT - AzMapShape.MIN_LAT)
    var lo = places.minOf { it.lon }; var hi = places.maxOf { it.lon }
    var bo = places.minOf { it.lat }; var to = places.maxOf { it.lat }
    fun grow(a: Float, b: Float, min: Float, full: Float, from: Float): Pair<Float, Float> {
        val span = maxOf(b - a + full * 0.24f, min)
        val mid = (a + b) / 2
        var s = mid - span / 2
        // A little sea or neighbour land around the edges is fine, so stops at the border keep room for a name.
        val edge = full * 0.06f
        s = s.coerceIn(from - edge, maxOf(from - edge, from + full + edge - span))
        return s to s + span
    }
    grow(lo, hi, fullW * 0.45f, fullW, AzMapShape.MIN_LON).let { lo = it.first; hi = it.second }
    grow(bo, to, fullH * 0.45f, fullH, AzMapShape.MIN_LAT).let { bo = it.first; to = it.second }
    // Not flatter than 4:3 or taller than square, so the card keeps a calm size.
    val wk = (hi - lo) * k
    val ht = to - bo
    if (wk / ht > 4f / 3f) grow(bo, to, wk * 3f / 4f, fullH, AzMapShape.MIN_LAT).let { bo = it.first; to = it.second }
    else if (wk / ht < 1f) grow(lo, hi, ht / k, fullW, AzMapShape.MIN_LON).let { lo = it.first; hi = it.second }
    return floatArrayOf(lo, hi, bo, to)
}

private fun pointAt(pts: List<Offset>, position: Float): Offset {
    if (pts.isEmpty()) return Offset.Zero
    val i = position.toInt().coerceIn(0, pts.lastIndex)
    if (i >= pts.lastIndex) return pts.last()
    val f = position - i
    return pts[i] + (pts[i + 1] - pts[i]) * f
}
