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
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.nativeCanvas
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
import dev.chrisbanes.haze.hazeEffect
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.getValue

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
fun GlassBackdrop(dark: Boolean, modifier: Modifier = Modifier, moving: Boolean = false) {
    if (!moving) { Canvas(modifier) { if (dark) glowsDark() else glowsLight() }; return }
    // Maksimum şüşə: the glows drift slowly in circles (one turn in ~24 s), so the blur under the cards lives.
    val t by androidx.compose.animation.core.rememberInfiniteTransition(label = "glow").animateFloat(
        0f, (2 * Math.PI).toFloat(),
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(24_000, easing = androidx.compose.animation.core.LinearEasing)),
        label = "t",
    )
    Canvas(modifier) { if (dark) glowsDark(t) else glowsLight(t) }
}

private fun dx(t: Float, k: Float) = 0.18f * kotlin.math.sin(t * k)
private fun dy(t: Float, k: Float) = 0.12f * kotlin.math.cos(t * k)

/** An oval glow, like CSS radial-gradient(rx ry at cx cy, c 0, transparent 70%): sizes are parts of the screen. */
private fun DrawScope.glow(cx: Float, cy: Float, rx: Float, ry: Float, c: Color) {
    val center = Offset(size.width * cx, size.height * cy)
    val r = size.width * rx
    scale(scaleX = 1f, scaleY = size.height * ry / r, pivot = center) {
        drawCircle(Brush.radialGradient(0f to c, 0.7f to c.copy(alpha = 0f), center = center, radius = r), r, center)
    }
}

// Same glows as the mockup.
private fun DrawScope.glowsLight(t: Float = 0f) {
    drawRect(Color(0xFFE8DEF8))
    glow(0.15f + dx(t, 1f), 0.10f + dy(t, 1f), 0.60f, 0.40f, Color(0xFFB69DF8))
    glow(0.90f - dx(t, 1f), 0.25f + dy(t, 2f), 0.50f, 0.35f, Color(0xFFFFC94D))
    glow(0.30f + dx(t, 2f), 0.80f - dy(t, 1f), 0.60f, 0.45f, Color(0xFF7FD1AE))
    glow(0.95f - dx(t, 1f), 0.90f - dy(t, 1f), 0.70f, 0.50f, Color(0xFF6750A4))
    if (t != 0f) glow(0.5f + 2 * dx(t, 1f), 0.5f + 3 * dy(t, 1f), 0.35f, 0.25f, Color(0xFFFF8FB1))
}

private fun DrawScope.glowsDark(t: Float = 0f) {
    drawRect(Color(0xFF120F1E))
    glow(0.80f - dx(t, 1f), 0.15f + dy(t, 1f), 0.55f, 0.40f, Color(0xFF4B3A8C))
    glow(0.10f + dx(t, 1f), 0.60f + dy(t, 2f), 0.50f, 0.40f, Color(0xFF1E5A6B))
    glow(0.70f + dx(t, 2f), 0.85f - dy(t, 1f), 0.40f, 0.30f, Color(0xFF8A3B6E))
    if (t != 0f) glow(0.5f + 2 * dx(t, 1f), 0.45f + 3 * dy(t, 1f), 0.35f, 0.25f, Color(0xFF3A7BD5))
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

/**
 * "Effektləri azalt": the lighter glass with no shadows and no live blur. On by itself on weak phones
 * (see [isWeakForGlass]) until the player flips the switch in Settings; their choice is kept.
 */
object GlassLite {
    private var state: androidx.compose.runtime.MutableState<Boolean>? = null
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("glass", Context.MODE_PRIVATE)
    fun isOn(ctx: Context): Boolean = (state ?: androidx.compose.runtime.mutableStateOf(
        prefs(ctx).let { if (it.contains("lite")) it.getBoolean("lite", false) else isWeakForGlass(ctx) },
    ).also { state = it }).value
    fun set(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean("lite", on).apply()
        isOn(ctx); state!!.value = on
    }
}

/**
 * "Maksimum şüşə" (experiment, Android 12+): every glass card really blurs the light behind it, and
 * that light slowly moves, so colour drifts under the cards like on iPhone's Liquid Glass. Costs
 * battery and frames on weak phones, so it is off unless switched on (on by default only in the Lab build).
 */
object GlassMax {
    private var state: androidx.compose.runtime.MutableState<Boolean>? = null
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("glass", Context.MODE_PRIVATE)
    fun isOn(ctx: Context): Boolean = glassBlurSupported && (state ?: androidx.compose.runtime.mutableStateOf(
        prefs(ctx).getBoolean("max", com.aaideas.krossvord.BuildConfig.LAB),
    ).also { state = it }).value
    fun set(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean("max", on).apply()
        isOn(ctx); state?.value = on
    }
}

/** Set in "Maksimum şüşə": the moving backdrop that every glass card blurs. */
val LocalCardHaze = staticCompositionLocalOf<HazeState?> { null }

/** Card frost: a stronger blur than the menu, a light tint so text stays readable. */
fun cardGlassStyle(dark: Boolean): HazeStyle = HazeDefaults.style(
    backgroundColor = if (dark) Color(0xFF15112A) else Color(0xFFE9E0F7),
    tint = HazeTint(if (dark) Color(0x40221C38) else Color(0x59FFFFFF)),
    blurRadius = 28.dp,
    noiseFactor = 0.04f,
)

/** Set while reduced glass effects are on. */
val LocalGlassLite = staticCompositionLocalOf { false }

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
    val lite = LocalGlassLite.current
    val cardHaze = LocalCardHaze.current
    // Maksimum şüşə: the card frosts the moving light behind it (the drop shadow would be clipped, so none).
    val base = if (cardHaze != null) this.then(Modifier.clip(shape).hazeEffect(cardHaze, cardGlassStyle(dark))) else this
    return base.drawWithCache {
        val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
        val sheen = Brush.linearGradient(
            0f to Color.White.copy(alpha = if (dark) 0.16f else 0.5f),
            0.4f to Color.White.copy(alpha = 0f),
            0.7f to Color.White.copy(alpha = 0f),
            1f to Color.White.copy(alpha = if (dark) 0.06f else 0.16f),
            start = Offset.Zero, end = Offset(size.width * 0.7f, size.height),
        )
        val rim = Brush.verticalGradient(
            listOf(Color.White.copy(alpha = if (dark) 0.45f else 0.9f), Color.White.copy(alpha = if (dark) 0.12f else 0.35f)),
        )
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx())
        // The soft shadow under each panel (CSS 0 10px 30px), drawn only outside the panel: under a
        // see-through panel it would show through as a grey box. Blurred shadows need Android 9.
        val shadow = if (!lite && cardHaze == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val dy = minOf(10.dp.toPx(), size.minDimension * 0.12f)
            Path().apply { addPath(path, Offset(0f, dy)) }.asAndroidPath() to android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = if (dark) android.graphics.Color.argb(90, 0, 0, 0) else android.graphics.Color.argb(56, 40, 20, 80)
                maskFilter = android.graphics.BlurMaskFilter(minOf(15.dp.toPx(), size.minDimension * 0.25f), android.graphics.BlurMaskFilter.Blur.NORMAL)
            }
        } else null
        onDrawWithContent {
            if (shadow != null) clipPath(path, ClipOp.Difference) {
                drawIntoCanvas { it.nativeCanvas.drawPath(shadow.first, shadow.second) }
            }
            drawPath(path, sheen)
            drawContent()
            drawPath(path, rim, style = stroke)
        }
    }
}

/**
 * Maksimum şüşə: glass cards get see-through fills so the blur shows (normal Şüşə keeps them nearly
 * solid). Dialogs keep their solid colour: they float over the grid, where see-through would hurt reading.
 */
@Composable
fun MaxGlassColors(on: Boolean, dark: Boolean, content: @Composable () -> Unit) {
    if (!on) { content(); return }
    val cs = androidx.compose.material3.MaterialTheme.colorScheme
    fun Color.see(a: Float) = copy(alpha = a)
    androidx.compose.material3.MaterialTheme(
        colorScheme = cs.copy(
            surfaceContainerHigh = if (dark) Color(0x66221C38) else Color(0x73FFFFFF),
            surfaceContainer = if (dark) Color(0x4D221C38) else Color(0x59FFFFFF),
            primaryContainer = cs.primaryContainer.see(0.6f),
            secondaryContainer = cs.secondaryContainer.see(0.6f),
            tertiaryContainer = cs.tertiaryContainer.see(0.6f),
        ),
        typography = androidx.compose.material3.MaterialTheme.typography,
        shapes = androidx.compose.material3.MaterialTheme.shapes,
        content = content,
    )
}
