package com.aaideas.launcher.ui

import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.IntSize

/**
 * "Жидкое стекло" (Android 13+), the same lens as in the crossword: inside a glass element the
 * wallpaper is drawn again through an AGSL shader. Near the rounded edge the picture bends
 * inward (refraction), the colours split a little (dispersion) and a light rim catches the
 * light from the top left; the middle stays flat. It samples the launcher's own blurred
 * wallpaper, so nothing is copied from the screen and it costs one shader per element.
 */
object LiquidLens {
    val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    private const val SHADER = """
uniform shader wall;
uniform float2 size;
uniform float2 origin;
uniform float scale;
uniform float radius;
uniform float depth;
uniform float bevel;
uniform float dispersion;

float sdRound(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}
half3 at(float2 window) { return wall.eval(window * scale).rgb; }

half4 main(float2 xy) {
    float2 halfSize = size * 0.5;
    float2 p = xy - halfSize;
    float r = min(radius, min(halfSize.x, halfSize.y));
    float d = sdRound(p, halfSize, r);
    float e = 0.75;
    float2 n = float2(sdRound(p + float2(e, 0.0), halfSize, r) - sdRound(p - float2(e, 0.0), halfSize, r),
                      sdRound(p + float2(0.0, e), halfSize, r) - sdRound(p - float2(0.0, e), halfSize, r));
    n = n / max(length(n), 0.0001);
    float t = clamp(1.0 + d / bevel, 0.0, 1.0);
    float bend = (1.0 - sqrt(1.0 - t * t)) * depth;
    float2 base = origin + xy - n * bend;
    float2 split = n * dispersion * t;
    float3 c = float3(at(base - split).r, at(base).g, at(base + split).b);
    float facing = dot(n, normalize(float2(-1.0, -1.4)));
    float rim = smoothstep(-2.5, 0.0, d) * (0.6 * max(facing, 0.0) + 0.2 * max(-facing, 0.0));
    float sheen = pow(t, 3.0) * 0.14;
    c = mix(c, float3(1.0), clamp(rim + sheen, 0.0, 1.0));
    return half4(half3(c), 1.0);
}
"""

    /** One element's lens; [place] moves it when pages scroll, without rebuilding. */
    class Lens(val shader: Any, val brush: ShaderBrush)

    /** Null when the phone can't compile the shader: the element then falls back to plain glass. */
    fun brush(wall: ImageBitmap, window: IntSize, size: Size, corner: Float): Lens? =
        if (!supported || window.width == 0) null else runCatching { make(wall, window, size, corner) }.getOrNull()

    fun place(lens: Lens, pos: Offset, size: Size) {
        if (Build.VERSION.SDK_INT >= 33) runCatching { (lens.shader as RuntimeShader).setFloatUniform("origin", pos.x, pos.y) }
    }

    @RequiresApi(33)
    private fun make(wall: ImageBitmap, window: IntSize, size: Size, corner: Float): Lens {
        val s = RuntimeShader(SHADER)
        s.setInputShader("wall", BitmapShader(wall.asAndroidBitmap(), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
        val small = minOf(size.width, size.height)
        s.setFloatUniform("size", size.width, size.height)
        s.setFloatUniform("origin", 0f, 0f)
        s.setFloatUniform("scale", wall.width.toFloat() / window.width)
        s.setFloatUniform("radius", corner)
        s.setFloatUniform("depth", small * 0.26f)
        s.setFloatUniform("bevel", minOf(small * 0.34f, corner * 1.4f + 6f))
        s.setFloatUniform("dispersion", 4f)
        return Lens(s, ShaderBrush(s))
    }
}
