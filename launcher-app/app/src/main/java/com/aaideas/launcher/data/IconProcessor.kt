package com.aaideas.launcher.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.max
import kotlin.math.min

/**
 * An app icon ready for glass: a glyph drawn on top of the glass and the app's
 * own colour that tints the glass.
 *
 * [whiteGlyph] is true when the glyph is the app's monochrome (themed) icon
 * drawn white — the look the owner chose. Apps without one keep their coloured
 * logo; their glass gets a weaker tint so the logo stays readable.
 */
class GlassIcon(val glyph: ImageBitmap, val color: Int, val whiteGlyph: Boolean)

object IconProcessor {
    private const val NEUTRAL = 0xFF64748B.toInt()

    fun process(d: Drawable, size: Int): GlassIcon {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && d is AdaptiveIconDrawable) {
            val mono = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) d.monochrome else null
            val color = brandColor(d.background, d.foreground)
            return if (mono != null) {
                GlassIcon(renderLayer(mono, size, white = true), color, whiteGlyph = true)
            } else {
                GlassIcon(renderLayer(d.foreground, size, white = false), color, whiteGlyph = false)
            }
        }
        // Old-style icon: keep it whole, a bit smaller, inside the glass.
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val inset = (size * 0.18f).toInt()
        d.setBounds(inset, inset, size - inset, size - inset)
        d.draw(Canvas(bmp))
        return GlassIcon(bmp.asImageBitmap(), vivid(average(bmp, weightBySaturation = true)), whiteGlyph = false)
    }

    /**
     * Adaptive layers are 108 dp with the visible part in the middle 72 dp, so the
     * layer is drawn at 1.5x and centred: the glyph keeps the size the app designed.
     */
    private fun renderLayer(layer: Drawable?, size: Int, white: Boolean): ImageBitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        if (layer != null) {
            val l = layer.mutate()
            val pad = size / 4
            l.setBounds(-pad, -pad, size + pad, size + pad)
            if (white) l.colorFilter = PorterDuffColorFilter(android.graphics.Color.WHITE, PorterDuff.Mode.SRC_IN)
            l.draw(Canvas(bmp))
        }
        return bmp.asImageBitmap()
    }

    /** The app's colour: its icon background, or the logo's colour when the background is white/grey. */
    private fun brandColor(bg: Drawable?, fg: Drawable?): Int {
        val bgColor = bg?.let { average(sample(it), weightBySaturation = false) }
        if (bgColor != null && saturation(bgColor) > 0.22f && value(bgColor) > 0.12f) return vivid(bgColor)
        val fgColor = fg?.let { average(sample(it), weightBySaturation = true) }
        if (fgColor != null && saturation(fgColor) > 0.22f) return vivid(fgColor)
        // Dark grey/black background (e.g. a black app icon): a slate glass.
        return NEUTRAL
    }

    private fun sample(d: Drawable): Bitmap {
        val s = 24
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val l = d.mutate()
        l.setBounds(-s / 4, -s / 4, s + s / 4, s + s / 4)
        l.draw(Canvas(bmp))
        return bmp
    }

    /** Average colour of opaque pixels; optionally weighting colourful pixels higher. */
    fun average(bmp: Bitmap, weightBySaturation: Boolean): Int {
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        var r = 0.0; var g = 0.0; var b = 0.0; var w = 0.0
        for (c in px) {
            val a = (c ushr 24) and 0xFF
            if (a < 128) continue
            val weight = if (weightBySaturation) 0.05 + saturation(c) else 1.0
            r += ((c shr 16) and 0xFF) * weight
            g += ((c shr 8) and 0xFF) * weight
            b += (c and 0xFF) * weight
            w += weight
        }
        if (w == 0.0) return NEUTRAL
        return (0xFF shl 24) or ((r / w).toInt() shl 16) or ((g / w).toInt() shl 8) or (b / w).toInt()
    }

    /** Keeps the hue but limits brightness, so a white glyph stays readable on the tinted glass. */
    fun vivid(c: Int): Int {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(c, hsv)
        hsv[1] = max(hsv[1], 0.35f).coerceAtMost(0.9f)
        hsv[2] = hsv[2].coerceIn(0.45f, 0.82f)
        return android.graphics.Color.HSVToColor(hsv)
    }

    fun saturation(c: Int): Float {
        val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
        val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
        return if (mx == 0) 0f else (mx - mn) / mx.toFloat()
    }

    private fun value(c: Int): Float {
        val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
        return max(r, max(g, b)) / 255f
    }
}
