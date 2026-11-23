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
import androidx.compose.ui.unit.sp
import com.aaideas.krossvord.model.Place

/**
 * A small map of Azerbaijan (Natural Earth outline, public domain) with a route: travelled part
 * solid, the rest dashed, a dot and name for each stop, and the car where the player is.
 * One Canvas, no images, so it costs almost nothing on weak phones.
 */
@Composable
fun AzMap(places: List<Place>, position: Float, modifier: Modifier = Modifier) {
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
    val w = (AzMapShape.MAX_LON - AzMapShape.MIN_LON) * k
    val h = AzMapShape.MAX_LAT - AzMapShape.MIN_LAT
    Canvas(modifier.aspectRatio(w / h)) {
        fun at(lon: Float, lat: Float) = Offset(
            (lon - AzMapShape.MIN_LON) * k / w * size.width,
            (AzMapShape.MAX_LAT - lat) / h * size.height,
        )
        AzMapShape.POLYGONS.forEach { pts ->
            val path = Path()
            for (i in pts.indices step 2) {
                val o = at(pts[i], pts[i + 1])
                if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
            }
            path.close()
            drawPath(path, land)
            drawPath(path, border, style = Stroke(width = 1.5f))
        }
        val pts = places.map { at(it.lon, it.lat) }
        val car = pointAt(pts, position)
        // Road ahead (dashed), then the travelled part on top.
        for (i in 0 until pts.lastIndex) drawLine(todo, pts[i], pts[i + 1], strokeWidth = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
        val full = position.toInt()
        for (i in 0 until full.coerceAtMost(pts.lastIndex)) drawLine(done, pts[i], pts[i + 1], strokeWidth = 7f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        if (full < pts.lastIndex) drawLine(done, pts[full], car, strokeWidth = 7f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        pts.forEachIndexed { i, o ->
            val reached = i <= position
            drawCircle(if (reached) done else cs.surface, radius = 9f, center = o)
            drawCircle(if (reached) done else todo, radius = 9f, center = o, style = Stroke(width = 3f))
        }
        // Names of the start, the end and the stop the car is at or heading to.
        val named = setOf(0, pts.lastIndex, kotlin.math.ceil(position).toInt().coerceAtMost(pts.lastIndex))
        named.forEach { i ->
            val t = measurer.measure(places[i].name, TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = ink))
            val o = pts[i]
            var x = o.x - t.size.width / 2f
            x = x.coerceIn(2f, size.width - t.size.width - 2f)
            val y = if (o.y > size.height * 0.75f) o.y - t.size.height - 12f else o.y + 12f
            drawRoundRect(labelBg, topLeft = Offset(x - 3f, y - 1f), size = androidx.compose.ui.geometry.Size(t.size.width + 6f, t.size.height + 2f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f))
            drawText(t, topLeft = Offset(x, y))
        }
        val carText = measurer.measure("🚗", TextStyle(fontSize = 20.sp))
        drawText(carText, topLeft = Offset(car.x - carText.size.width / 2f, car.y - carText.size.height + 4f))
    }
}

private fun pointAt(pts: List<Offset>, position: Float): Offset {
    if (pts.isEmpty()) return Offset.Zero
    val i = position.toInt().coerceIn(0, pts.lastIndex)
    if (i >= pts.lastIndex) return pts.last()
    val f = position - i
    return pts[i] + (pts[i + 1] - pts[i]) * f
}
