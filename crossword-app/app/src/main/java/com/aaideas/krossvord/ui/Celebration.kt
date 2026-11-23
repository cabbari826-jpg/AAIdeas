package com.aaideas.krossvord.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.cos
import kotlin.math.sin

/** "1 dəq 05 san", "45 san". */
fun levelTimeText(seconds: Long): String =
    if (seconds >= 60) "${seconds / 60} dəq ${"%02d".format(seconds % 60)} san" else "$seconds san"

/**
 * The card after a solved level: a short burst of confetti, 1–3 stars popping in, a word of praise,
 * the time taken (only here, never as a ticking clock during play) and any new medals.
 */
@Composable
fun LevelResultDialog(title: String, result: LevelResult, onNext: () -> Unit, onClose: () -> Unit) {
    SolidDialog(onDismissRequest = onClose) {
        Box(contentAlignment = Alignment.Center) {
            // A fixed width: the window used to grow with its longest line (the button row), so it looked different level to level.
            Surface(Modifier.width(300.dp).glassy(RoundedCornerShape(24.dp), lens = false, shadow = false), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = if (LocalGlass.current) 0.dp else 6.dp) {
                Column(
                    Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(result.praise, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Stars(result.stars)
                    Text("$title həll olundu", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                    if (result.seconds > 0) Text("Vaxt: ${levelTimeText(result.seconds)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (result.xp > 0) Text("+${result.xp} XP", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    result.rankUp?.let { r ->
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                            Text(
                                "🎖 Yeni rütbə: ${r.title}" + if (r.themeId != null) "\nHədiyyə: yeni mövzu" else "",
                                Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                            )
                        }
                    }
                    result.keyword?.let { Text("Açar söz: $it", style = MaterialTheme.typography.titleMedium) }
                    result.medals.forEach { m ->
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                            Text("${m.icon} Yeni medal: ${m.title}", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    result.trip?.let { t ->
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                            Text(t, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                        }
                    }
                    // A new place on the road map: its fact replaces the usual one.
                    result.city?.let { c ->
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Text("🗺 Yeni yer açıldı: ${c.icon} ${c.name}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                Text(c.fact, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    if (result.city == null) result.fact?.let { f ->
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Text("💡 Bilirsinizmi?", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                Text(f, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    Button(onClick = onNext, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) { Text("Növbəti səviyyə ▶") }
                    TextButton(onClick = onClose) { Text("Bağla") }
                }
            }
            Confetti(Modifier.matchParentSizeSafe())
        }
    }
}

// Confetti covers the card without taking taps (drawn only, no pointer input).
private fun Modifier.matchParentSizeSafe() = this.then(Modifier.fillMaxSize())

@Composable
private fun Stars(count: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.semantics { contentDescription = "$count ulduz" },
    ) {
        for (i in 0 until 3) {
            val scale = remember { Animatable(0f) }
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(150L + i * 180L)
                scale.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
            }
            Text(
                if (i < count) "★" else "☆",
                fontSize = 40.sp,
                color = if (i < count) Color(0xFFFFB300) else MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value },
            )
        }
    }
}

/** One burst, about 1.6 s, then gone. Fewer pieces on weak phones. Finite, so tests stay idle-able. */
@Composable
private fun Confetti(modifier: Modifier) {
    val context = LocalContext.current
    val n = remember { if (isLowEndPhone(context)) 24 else 60 }
    val pieces = remember {
        val r = java.util.Random(7)
        List(n) { Piece(r.nextFloat() * 6.283f, 0.35f + r.nextFloat() * 0.65f, PALETTE[r.nextInt(PALETTE.size)], r.nextFloat() * 360f) }
    }
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(1600, easing = LinearEasing)) }
    Canvas(modifier) {
        val p = t.value
        if (p >= 1f) return@Canvas
        val c = Offset(size.width / 2, size.height * 0.3f)
        val reach = size.minDimension * 0.6f
        pieces.forEach { pc ->
            val d = reach * pc.speed * (1 - (1 - p) * (1 - p))
            val x = c.x + cos(pc.angle) * d
            val y = c.y + sin(pc.angle) * d + size.height * 0.35f * p * p // falls a little
            rotate(pc.spin * p, Offset(x, y)) {
                drawRect(pc.color.copy(alpha = 1f - p), Offset(x - 5f, y - 3f), Size(10f, 6f))
            }
        }
    }
}

private class Piece(val angle: Float, val speed: Float, val color: Color, val spin: Float)

private val PALETTE = listOf(Color(0xFFE53935), Color(0xFFFFB300), Color(0xFF43A047), Color(0xFF1E88E5), Color(0xFF8E24AA))

