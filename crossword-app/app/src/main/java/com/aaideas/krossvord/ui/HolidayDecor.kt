package com.aaideas.krossvord.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aaideas.krossvord.model.Holidays
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.random.Random

/** Seasons that dress up the home screen. Each picture is a few shapes on one Canvas: nothing to load, cheap on any phone. */
enum class DecorSeason(val label: String) {
    YENI_IL("Qar adamı"), NOVRUZ("Novruz süfrəsi"), RAMAZAN("Ramazan ayı"), QURBAN("Qurban bayramı"),
    /** National days: fireworks in the flag's colours. */
    BAYRAM("Bayram atəşfəşanlığı"),
    /** 8 November: the flag and a red carnation, honouring the fallen and the veterans. */
    ZEFER("Zəfər günü"),
    /** 20 January and 27 September: days of remembrance, a carnation only, nothing festive. */
    ANIM("Anım günü");

    companion object {
        /** Ramazan month runs for about 30 days before the bayram; Qurban shows from the eve to two days after. */
        fun on(day: LocalDate): DecorSeason? {
            val m = day.monthValue; val d = day.dayOfMonth
            if (m == 11 && d == 8) return ZEFER
            if ((m == 1 && d == 20) || (m == 9 && d == 27)) return ANIM
            if ((m == 5 && d == 28) || (m == 6 && (d == 15 || d == 26)) || (m == 11 && (d == 9 || d == 12 || d == 17))) return BAYRAM
            if ((m == 12 && d >= 20) || (m == 1 && d <= 7)) return YENI_IL
            if (Skin.isNovruz(m, d)) return NOVRUZ
            if (ramazanDay(day) != null) return RAMAZAN
            val qurban = Holidays.ALL.first { it.id == "qurban" }.moving
            if (qurban.any { !day.isBefore(it.minusDays(1)) && !day.isAfter(it.plusDays(2)) }) return QURBAN
            return null
        }

        /** Day of the fast (1..30) or 30 on the bayram itself and the day after; null outside Ramazan. */
        fun ramazanDay(day: LocalDate): Int? {
            for (bayram in Holidays.ALL.first { it.id == "ramazan" }.moving) {
                val start = bayram.minusDays(30)
                val n = ChronoUnit.DAYS.between(start, day).toInt()
                if (n in 0..29) return n + 1
                if (n in 30..31) return 30
            }
            return null
        }
    }
}

/** The small picture in the corner of the home screen, beside the title. */
@Composable
fun HolidayDecor(season: DecorSeason, today: LocalDate, modifier: Modifier = Modifier) {
    val bg = MaterialTheme.colorScheme.background
    val ink = MaterialTheme.colorScheme.onBackground
    Column(modifier.semantics { contentDescription = season.label }, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(width = 112.dp, height = 56.dp)) {
            when (season) {
                DecorSeason.YENI_IL -> snowman()
                DecorSeason.NOVRUZ -> novruz()
                DecorSeason.RAMAZAN -> moon((DecorSeason.ramazanDay(today) ?: 30) / 30f, bg)
                DecorSeason.QURBAN -> lamb(ink)
                DecorSeason.BAYRAM -> fireworks(1f)
                DecorSeason.ZEFER -> { flag(); carnation(Offset(size.width * 0.78f, size.height)) }
                DecorSeason.ANIM -> carnation(Offset(size.width / 2, size.height))
            }
        }
        if (season == DecorSeason.ZEFER || season == DecorSeason.ANIM) {
            Text(
                "Şəhidlərimizin ruhu şad olsun",
                style = MaterialTheme.typography.labelSmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.width(120.dp),
            )
        }
        if (season == DecorSeason.RAMAZAN) {
            val n = DecorSeason.ramazanDay(today) ?: 30
            Text(if (n >= 30) "Bayram mübarək!" else "Ramazan: $n / 30", style = MaterialTheme.typography.labelSmall)
        }
    }
}

private val Snow = Color(0xFFFFFFFF)
private val SnowShade = Color(0xFFDCE6F2)

private fun DrawScope.snowman() {
    val u = size.height / 56f
    val cx = size.width / 2
    fun c(x: Float, y: Float) = Offset(cx + x * u, y * u)
    drawOval(Color(0x22000000), c(-22f, 50f), Size(44f * u, 6f * u))
    drawCircle(SnowShade, 15.5f * u, c(0f, 40f)); drawCircle(Snow, 14f * u, c(0f, 40f))
    drawCircle(SnowShade, 11f * u, c(0f, 20f)); drawCircle(Snow, 10f * u, c(0f, 20f))
    // hat
    drawRect(Color(0xFF2B2B3A), c(-7f, 2f), Size(14f * u, 9f * u))
    drawRect(Color(0xFF2B2B3A), c(-10.5f, 10f), Size(21f * u, 2.5f * u))
    // scarf
    drawRoundRect(Color(0xFFD93A3A), c(-10f, 27f), Size(20f * u, 4f * u), CornerRadius(2f * u))
    drawRoundRect(Color(0xFFD93A3A), c(4f, 28f), Size(4f * u, 10f * u), CornerRadius(2f * u))
    // face and buttons
    drawCircle(Color(0xFF2B2B3A), 1.3f * u, c(-3.5f, 18f)); drawCircle(Color(0xFF2B2B3A), 1.3f * u, c(3.5f, 18f))
    drawPath(Path().apply { moveTo(cx, 20.5f * u); lineTo(cx + 9f * u, 22f * u); lineTo(cx, 23f * u); close() }, Color(0xFFF08A24))
    for (y in listOf(36f, 42f)) drawCircle(Color(0xFF2B2B3A), 1.4f * u, c(0f, y))
    // stick arms
    drawLine(Color(0xFF7A5230), c(-10f, 33f), c(-22f, 25f), 1.8f * u, StrokeCap.Round)
    drawLine(Color(0xFF7A5230), c(10f, 33f), c(22f, 26f), 1.8f * u, StrokeCap.Round)
}

private fun DrawScope.novruz() {
    val u = size.height / 56f
    fun o(x: Float, y: Float) = Offset(x * u, y * u)
    // səməni: green sprouts on a plate with a red ribbon
    for (i in 0..8) {
        val x = 10f + i * 2.6f
        drawLine(if (i % 2 == 0) Color(0xFF5DB043) else Color(0xFF8BD17C), o(x, 44f), o(x + (i - 4) * 0.8f, 16f + kotlin.math.abs(i - 4) * 2.2f), 1.8f * u, StrokeCap.Round)
    }
    drawRoundRect(Color(0xFFD93A3A), o(8f, 36f), Size(25f * u, 3.5f * u), CornerRadius(1.5f * u))
    drawOval(Color(0xFFE0A93A), o(4f, 42f), Size(33f * u, 8f * u))
    // tonqal: logs and a flame
    drawLine(Color(0xFF7A5230), o(42f, 50f), o(58f, 44f), 3f * u, StrokeCap.Round)
    drawLine(Color(0xFF7A5230), o(42f, 44f), o(58f, 50f), 3f * u, StrokeCap.Round)
    drawPath(Path().apply {
        moveTo(50f * u, 14f * u); cubicTo(58f * u, 26f * u, 60f * u, 38f * u, 50f * u, 46f * u)
        cubicTo(40f * u, 38f * u, 43f * u, 28f * u, 50f * u, 14f * u); close()
    }, Color(0xFFF08A24))
    drawPath(Path().apply {
        moveTo(50f * u, 26f * u); cubicTo(55f * u, 33f * u, 55f * u, 40f * u, 50f * u, 45f * u)
        cubicTo(45f * u, 40f * u, 46f * u, 33f * u, 50f * u, 26f * u); close()
    }, Color(0xFFFFD24A))
    // şəkərbura: a half-moon pastry with a pinched pattern
    drawArc(Color(0xFFF2D6A2), 180f, 180f, true, o(64f, 28f), Size(22f * u, 22f * u))
    for (i in 0..4) {
        val x = 67.5f + i * 3.8f
        drawLine(Color(0xFFC79A5A), o(x, 34f), o(x + 1.2f, 38.5f), 1f * u, StrokeCap.Round)
    }
    // paxlava: a diamond with an almond
    drawPath(Path().apply {
        moveTo(99f * u, 26f * u); lineTo(108f * u, 38f * u); lineTo(99f * u, 50f * u); lineTo(90f * u, 38f * u); close()
    }, Color(0xFFC0782E))
    drawPath(Path().apply {
        moveTo(99f * u, 29f * u); lineTo(105.5f * u, 38f * u); lineTo(99f * u, 47f * u); lineTo(92.5f * u, 38f * u); close()
    }, Color(0xFFE0A050), style = Stroke(0.8f * u))
    drawOval(Color(0xFF8A5228), o(97.2f, 35.5f), Size(3.6f * u, 5f * u))
}

/** A moon that fills through the month: a thin crescent on day 1, full on the bayram. */
private fun DrawScope.moon(fill: Float, bg: Color) {
    val r = size.height * 0.4f
    val c = Offset(size.width / 2, size.height / 2)
    drawCircle(Color(0x33E8C55A), r * 1.25f, c)
    drawCircle(Color(0xFFF2CF5B), r, c)
    if (fill < 1f) drawCircle(bg, r, Offset(c.x - r * (0.3f + 1.8f * fill), c.y - r * 0.05f))
    // a small star beside it
    rotate(18f, Offset(c.x + r * 1.7f, c.y - r * 0.5f)) {
        val s = Offset(c.x + r * 1.7f, c.y - r * 0.5f)
        drawPath(Path().apply {
            for (i in 0 until 10) {
                val a = Math.PI / 5 * i - Math.PI / 2
                val rr = if (i % 2 == 0) r * 0.32f else r * 0.13f
                val p = Offset(s.x + (rr * kotlin.math.cos(a)).toFloat(), s.y + (rr * kotlin.math.sin(a)).toFloat())
                if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
            close()
        }, Color(0xFFF2CF5B))
    }
}

private fun DrawScope.lamb(ink: Color) {
    val u = size.height / 56f
    val cx = size.width / 2
    fun c(x: Float, y: Float) = Offset(cx + x * u, y * u)
    val wool = Color(0xFFF7F3EA); val shade = Color(0xFFE2DACB); val face = Color(0xFF3B3030)
    drawOval(Color(0x22000000), c(-20f, 49f), Size(40f * u, 5f * u))
    for (x in listOf(-12f, -5f, 6f, 12f)) drawRoundRect(face, c(x - 1.5f, 36f), Size(3f * u, 13f * u), CornerRadius(1.5f * u))
    for ((x, y) in listOf(-14f to 30f, -8f to 24f, 0f to 22f, 8f to 24f, 13f to 30f, -8f to 36f, 0f to 37f, 8f to 36f)) {
        drawCircle(shade, 8f * u, c(x, y + 1f)); drawCircle(wool, 7.5f * u, c(x, y))
    }
    drawCircle(wool, 6f * u, c(20f, 22f))
    drawOval(face, c(19f, 18f), Size(11f * u, 14f * u))
    drawOval(face, c(15.5f, 20f), Size(6f * u, 3.5f * u))
    drawCircle(Color.White, 1.4f * u, c(26f, 23f)); drawCircle(ink.copy(alpha = 1f), 0.7f * u, c(26.3f, 23.2f))
}

private val FlagBlue = Color(0xFF00B5E2)
private val FlagRed = Color(0xFFEF3340)
private val FlagGreen = Color(0xFF509E2F)

/** Three bursts in the flag's colours; [p] from 0 to 1 grows them (1 = the still picture). */
private fun DrawScope.fireworks(p: Float, alpha: Float = 1f) {
    val bursts = listOf(Triple(0.22f, 0.42f, FlagBlue), Triple(0.5f, 0.3f, FlagRed), Triple(0.78f, 0.45f, FlagGreen))
    bursts.forEachIndexed { i, (x, y, col) ->
        val local = ((p * 1.4f) - i * 0.2f).coerceIn(0f, 1f)
        if (local <= 0f) return@forEachIndexed
        val c = Offset(size.width * x, size.height * y)
        val r = size.height * 0.36f * local
        for (k in 0 until 12) {
            val a = Math.PI * 2 / 12 * k
            val dir = Offset(kotlin.math.cos(a).toFloat(), kotlin.math.sin(a).toFloat())
            drawLine(col.copy(alpha = alpha), c + dir * (r * 0.45f), c + dir * r, size.height * 0.035f, StrokeCap.Round)
            drawCircle(Color(0xFFFFD24A).copy(alpha = alpha), size.height * 0.028f, c + dir * (r * 1.08f))
        }
    }
}

/** The state flag on a pole, gently waving. */
private fun DrawScope.flag() {
    val u = size.height / 56f
    val x0 = 20f * u; val w = 40f * u; val h = 27f * u; val top = 6f * u
    drawLine(Color(0xFF8A8A8A), Offset(x0, top - 2f * u), Offset(x0, 54f * u), 2f * u, StrokeCap.Round)
    fun band(y0: Float, col: Color) = drawPath(Path().apply {
        moveTo(x0, top + y0); cubicTo(x0 + w * 0.35f, top + y0 - 3f * u, x0 + w * 0.65f, top + y0 + 3f * u, x0 + w, top + y0)
        lineTo(x0 + w, top + y0 + h / 3); cubicTo(x0 + w * 0.65f, top + y0 + h / 3 + 3f * u, x0 + w * 0.35f, top + y0 + h / 3 - 3f * u, x0, top + y0 + h / 3)
        close()
    }, col)
    band(0f, FlagBlue); band(h / 3, FlagRed); band(2 * h / 3, FlagGreen)
    val c = Offset(x0 + w * 0.47f, top + h / 2)
    drawCircle(Color.White, 3.4f * u, c); drawCircle(FlagRed, 2.8f * u, c + Offset(1.1f * u, 0f))
    drawCircle(Color.White, 1.3f * u, c + Offset(5f * u, 0f))
}

/** A red carnation (qərənfil), the flower of remembrance. [base] is the bottom of the stem. */
private fun DrawScope.carnation(base: Offset) {
    val u = size.height / 56f
    val head = base + Offset(0f, -40f * u)
    drawLine(Color(0xFF4E8A3A), base, head + Offset(0f, 4f * u), 1.8f * u, StrokeCap.Round)
    drawPath(Path().apply {
        moveTo(base.x, base.y - 16f * u); quadraticBezierTo(base.x - 9f * u, base.y - 22f * u, base.x - 11f * u, base.y - 28f * u)
        quadraticBezierTo(base.x - 4f * u, base.y - 23f * u, base.x, base.y - 18f * u); close()
    }, Color(0xFF4E8A3A))
    drawPath(Path().apply {
        moveTo(head.x - 4f * u, head.y + 2f * u); lineTo(head.x + 4f * u, head.y + 2f * u); lineTo(head.x, head.y + 8f * u); close()
    }, Color(0xFF4E8A3A))
    for ((dx, dy, r) in listOf(Triple(-5f, -2f, 5f), Triple(5f, -2f, 5f), Triple(0f, -5f, 5.5f), Triple(-2.5f, 1f, 4.5f), Triple(2.5f, 1f, 4.5f))) {
        drawCircle(Color(0xFFB3122A), r * u, head + Offset(dx * u, dy * u))
    }
    for ((dx, dy) in listOf(-4f to -3f, 3f to -4f, 0f to 0f)) drawCircle(Color(0xFFD62839), 2.2f * u, head + Offset(dx * u, dy * u))
}

/** Fireworks for a few seconds when the app opens on a national day, then they stop. */
@Composable
fun FireworksOnce(modifier: Modifier = Modifier) {
    if (fireworksDone) return
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        t.animateTo(1f, tween(durationMillis = 4000, easing = LinearEasing))
        fireworksDone = true
    }
    Canvas(modifier) {
        val p = t.value
        val alpha = if (p > 0.7f) (1f - p) / 0.3f else 1f
        // bursts spread over the top third of the screen
        drawIntoBursts(Size(size.width, size.height / 3), p, alpha)
    }
}

private fun DrawScope.drawIntoBursts(area: Size, p: Float, alpha: Float) {
    val bursts = listOf(Triple(0.2f, 0.45f, FlagBlue), Triple(0.55f, 0.3f, FlagRed), Triple(0.82f, 0.55f, FlagGreen), Triple(0.38f, 0.75f, Color(0xFFFFD24A)))
    bursts.forEachIndexed { i, (x, y, col) ->
        val local = ((p * 1.6f) - i * 0.18f).coerceIn(0f, 1f)
        if (local <= 0f) return@forEachIndexed
        val c = Offset(area.width * x, area.height * y)
        val r = area.width * 0.16f * local
        for (k in 0 until 14) {
            val a = Math.PI * 2 / 14 * k
            val dir = Offset(kotlin.math.cos(a).toFloat(), kotlin.math.sin(a).toFloat())
            drawCircle(col.copy(alpha = alpha * (1f - local * 0.5f)), 3f * density, c + dir * r)
        }
    }
}

private var fireworksDone = false

/** Snow falls for a few seconds when the app opens in the New Year season, then stops for good. */
@Composable
fun SnowFall(modifier: Modifier = Modifier) {
    if (snowDone) return
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        t.animateTo(1f, tween(durationMillis = 6000, easing = LinearEasing))
        snowDone = true
    }
    val flakes = remember {
        val r = Random(7)
        List(36) { floatArrayOf(r.nextFloat(), r.nextFloat() * 0.6f - 0.6f, 0.6f + r.nextFloat() * 0.8f, 2f + r.nextFloat() * 3f, r.nextFloat() * 6.28f) }
    }
    Canvas(modifier) {
        val p = t.value
        val alpha = if (p > 0.8f) (1f - p) / 0.2f else 1f
        for (f in flakes) {
            val y = (f[1] + p * f[2] * 1.6f) * size.height
            if (y < 0 || y > size.height) continue
            val x = (f[0] + 0.03f * kotlin.math.sin(f[4] + p * 12f)) * size.width
            drawCircle(Color.White.copy(alpha = 0.9f * alpha), f[3] * density / 1.5f, Offset(x, y))
            drawCircle(Color(0xFF9DB3D0).copy(alpha = 0.5f * alpha), f[3] * density / 1.5f, Offset(x, y), style = Stroke(0.6f * density))
        }
    }
}

/** Once per app run, so going back to the home screen does not snow again. */
private var snowDone = false
