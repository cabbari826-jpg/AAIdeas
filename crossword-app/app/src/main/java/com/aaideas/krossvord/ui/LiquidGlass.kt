package com.aaideas.krossvord.ui

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * "Maksimum şüşə" as real glass (Android 13+, Lab only): what is behind a panel is drawn again
 * inside it through an AGSL lens. Near the rounded edge the picture bends inward (refraction),
 * the colours split a little (dispersion), a light rim catches the light from the top left, and
 * the middle stays clear with only a soft frost. Nothing here runs on older phones or in tests.
 */
object LiquidGlass {
    val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    // The lens. Coordinates are in the panel's recorded layer, which is [pad] larger on each side
    // so the blur and the bend have real picture to sample at the edge.
    const val SHADER = """
uniform shader content;
uniform float2 size;
uniform float pad;
uniform float radius;
uniform float depth;
uniform float bevel;
uniform float dispersion;
uniform float dark;

float sdRound(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

half4 main(float2 xy) {
    float2 halfSize = size * 0.5;
    float2 p = xy - pad - halfSize;
    float r = min(radius, min(halfSize.x, halfSize.y));
    float d = sdRound(p, halfSize, r);
    if (d > 0.5) return content.eval(xy);
    float e = 0.75;
    float2 n = float2(sdRound(p + float2(e, 0.0), halfSize, r) - sdRound(p - float2(e, 0.0), halfSize, r),
                      sdRound(p + float2(0.0, e), halfSize, r) - sdRound(p - float2(0.0, e), halfSize, r));
    n = n / max(length(n), 0.0001);
    // 0 in the flat middle, 1 at the very edge; a circular-ish bevel like a glass slab.
    float t = clamp(1.0 + d / bevel, 0.0, 1.0);
    float bend = (1.0 - sqrt(1.0 - t * t)) * depth;
    float2 base = xy - n * bend;
    float2 split = n * dispersion * t;
    float3 c = float3(content.eval(base - split).r, content.eval(base).g, content.eval(base + split).b);
    // Light from the top left: a bright rim where the edge faces it, a faint one opposite.
    float facing = dot(n, normalize(float2(-1.0, -1.4)));
    float rim = smoothstep(-2.5, 0.0, d) * (0.55 * max(facing, 0.0) + 0.18 * max(-facing, 0.0));
    float sheen = pow(t, 3.0) * 0.12;
    c = mix(c, float3(1.0), clamp(rim + sheen, 0.0, 1.0));
    // A thin tint keeps text on the panel readable without hiding what is behind.
    float3 tint = dark > 0.5 ? float3(0.09, 0.07, 0.16) : float3(1.0);
    c = mix(c, tint, dark > 0.5 ? 0.28 : 0.22);
    return half4(half3(c), 1.0);
}
"""
}

/** The moving light behind everything (cards look through it). */
val LocalGlassBackdrop = staticCompositionLocalOf<GraphicsLayer?> { null }

/** The whole screen with its content (the floating menu looks through it, so passing text bends). */
val LocalGlassScreen = staticCompositionLocalOf<GraphicsLayer?> { null }

/** Records what this node draws into [layer] and draws it as usual. */
fun Modifier.recordInto(layer: GraphicsLayer?): Modifier =
    if (layer == null) this else drawWithContent {
        layer.record { this@drawWithContent.drawContent() }
        drawLayer(layer)
    }

/**
 * Draws [source] (recorded at the root's origin) behind this panel through the glass lens.
 * [corner] is the panel's corner radius. Does nothing when [source] is null.
 */
fun Modifier.liquidGlass(source: GraphicsLayer?, shape: androidx.compose.ui.graphics.Shape, dark: Boolean, frost: androidx.compose.ui.unit.Dp = 6.dp): Modifier =
    if (source == null || !LiquidGlass.supported) this else composed {
        val lens = rememberGraphicsLayer()
        var at by remember { mutableStateOf(Offset.Zero) }
        // A shader that fails to compile on some GPU just means no lens, never a crash.
        val shader = remember { if (Build.VERSION.SDK_INT >= 33) runCatching { RuntimeShader(LiquidGlass.SHADER) }.getOrNull() else null }
        this.onGloballyPositioned { at = it.positionInRoot() }.drawWithContent {
            if (shader != null && Build.VERSION.SDK_INT >= 33) {
                val pad = 24.dp.toPx()
                val w = size.width.toInt() + 2 * pad.toInt()
                val h = size.height.toInt() + 2 * pad.toInt()
                val corner = (shape as? androidx.compose.foundation.shape.CornerBasedShape)?.topStart?.toPx(size, this) ?: 16.dp.toPx()
                runCatching { configure(shader, size.width, size.height, pad, corner, dark) }
                lens.renderEffect = effect(shader, frost.toPx())
                lens.clip = false
                val origin = at
                lens.record(IntSize(w, h)) {
                    translate(pad - origin.x, pad - origin.y) { drawLayer(source) }
                }
                lens.topLeft = androidx.compose.ui.unit.IntOffset(-pad.toInt(), -pad.toInt())
                // Show only the panel itself; the padding was there for sampling.
                val outline = shape.createOutline(size, layoutDirection, this)
                val path = androidx.compose.ui.graphics.Path().apply { addOutline(outline) }
                clipPath(path) { drawLayer(lens) }
            }
            drawContent()
        }
    }

@RequiresApi(33)
private fun configure(s: RuntimeShader, w: Float, h: Float, pad: Float, corner: Float, dark: Boolean) {
    s.setFloatUniform("size", w, h)
    s.setFloatUniform("pad", pad)
    s.setFloatUniform("radius", corner)
    s.setFloatUniform("depth", minOf(w, h) * 0.22f)
    s.setFloatUniform("bevel", minOf(minOf(w, h) * 0.32f, corner * 1.4f + 6f))
    s.setFloatUniform("dispersion", 4f)
    s.setFloatUniform("dark", if (dark) 1f else 0f)
}

/** Soft frost first, then the lens on top of it. */
@RequiresApi(33)
private fun effect(s: RuntimeShader, blur: Float) = android.graphics.RenderEffect.createChainEffect(
    android.graphics.RenderEffect.createRuntimeShaderEffect(s, "content"),
    android.graphics.RenderEffect.createBlurEffect(blur, blur, android.graphics.Shader.TileMode.CLAMP),
).asComposeRenderEffect()

@Suppress("unused")
private val Unused = Color.Transparent
