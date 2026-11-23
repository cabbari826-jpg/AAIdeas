package com.aaideas.krossvord.ui

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint

/** Set while the Şüşə theme is on: floating panels (the bottom menu) blur what scrolls under them. */
val LocalHaze = staticCompositionLocalOf<HazeState?> { null }

/** Real blur needs Android 12 (RenderEffect). Older phones get see-through panels without blur. */
val glassBlurSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** Phones where the glass look may stutter: no real blur, or little memory. */
fun isWeakForGlass(ctx: Context): Boolean {
    if (!glassBlurSupported) return true
    val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
    return am.isLowRamDevice || info.totalMem < 3L * 1024 * 1024 * 1024
}

/** Frosted style: light tint over the blur; without blur (old phones) a denser tint keeps text readable. */
fun glassStyle(dark: Boolean): HazeStyle = HazeDefaults.style(
    backgroundColor = if (dark) Color(0xFF15112A) else Color(0xFFE9E0F7),
    tint = HazeTint(if (dark) Color(0x33FFFFFF) else Color(0x66FFFFFF)),
    blurRadius = HazeDefaults.blurRadius,
    noiseFactor = 0f,
).copy(fallbackTint = HazeTint(if (dark) Color(0xCC241E3D) else Color(0xCCF6F1FF)))

/**
 * The colourful light behind the glass: a few soft radial glows, drawn once per size change.
 * Static (no animation), so it costs one cheap draw.
 */
@Composable
fun GlassBackdrop(dark: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) { if (dark) glowsDark() else glowsLight() }
}

private fun DrawScope.glow(cx: Float, cy: Float, r: Float, c: Color) {
    val center = Offset(size.width * cx, size.height * cy)
    val radius = maxOf(size.width, size.height) * r
    drawCircle(Brush.radialGradient(listOf(c, c.copy(alpha = 0f)), center, radius), radius, center)
}

private fun DrawScope.glowsLight() {
    drawRect(Color(0xFFE8DEF8))
    glow(0.12f, 0.08f, 0.45f, Color(0xFFB69DF8))
    glow(0.92f, 0.22f, 0.38f, Color(0xFFFFC94D))
    glow(0.25f, 0.62f, 0.42f, Color(0xFF7FD1AE))
    glow(0.95f, 0.88f, 0.5f, Color(0xFF8E74D0))
}

private fun DrawScope.glowsDark() {
    drawRect(Color(0xFF120F1E))
    glow(0.85f, 0.1f, 0.45f, Color(0xFF4B3A8C))
    glow(0.08f, 0.55f, 0.42f, Color(0xFF1E5A6B))
    glow(0.75f, 0.85f, 0.4f, Color(0xFF8A3B6E))
}

/** Asked before switching a weak phone to Şüşə. */
@Composable
fun GlassWarning(onYes: () -> Unit, onNo: () -> Unit) {
    AlertDialog(
        onDismissRequest = onNo,
        title = { Text("Şüşə mövzusu") },
        text = {
            Text(
                if (glassBlurSupported) "Telefonunuzda bu mövzu oyunu bir az ləngidə bilər. Yenə də açılsın?"
                else "Bu telefonda şüşə bulanıq olmayacaq, sadəcə şəffaf görünəcək. Oyun bir az ləngiyə bilər. Yenə də açılsın?"
            )
        },
        confirmButton = { TextButton(onClick = onYes) { Text("Bəli") } },
        dismissButton = { TextButton(onClick = onNo) { Text("Xeyr") } },
    )
}

/** True while the Şüşə theme is on; glass-only touches (rims, sheen, gaps) read it. */
val LocalGlass = staticCompositionLocalOf { false }

/**
 * The glass rim and sheen from the mockup: a light diagonal sheen under the panel's own see-through
 * fill, and a 1 dp rim that is bright on top and fades downward. Does nothing outside the Şüşə theme.
 */
@Composable
fun Modifier.glassy(shape: Shape): Modifier {
    if (!LocalGlass.current) return this
    val dark = androidx.compose.material3.MaterialTheme.colorScheme.background.luminance() < 0.5f
    return this.drawWithCache {
        val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
        val sheen = Brush.linearGradient(
            0f to Color.White.copy(alpha = if (dark) 0.16f else 0.42f),
            0.4f to Color.White.copy(alpha = 0f),
            0.7f to Color.White.copy(alpha = 0f),
            1f to Color.White.copy(alpha = if (dark) 0.06f else 0.16f),
            start = Offset.Zero, end = Offset(size.width * 0.7f, size.height),
        )
        val rim = Brush.verticalGradient(
            listOf(Color.White.copy(alpha = if (dark) 0.45f else 0.9f), Color.White.copy(alpha = if (dark) 0.12f else 0.35f)),
        )
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx())
        onDrawWithContent {
            drawPath(path, sheen)
            drawContent()
            drawPath(path, rim, style = stroke)
        }
    }
}
