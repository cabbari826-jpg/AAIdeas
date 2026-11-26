package com.aaideas.launcher.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * An app icon ready for glass: a white glyph on top and the colour of the original
 * icon for the glass under it.
 */
class GlassIcon(
    val glyph: ImageBitmap,
    val color: Int,
    val whiteGlyph: Boolean = true,
    /** Second colour for icons whose art is a gradient (top-left [color] to bottom-right this). */
    val color2: Int? = null,
)

/**
 * Turns any app icon into "white glyph + the icon's own colour", like themed icons on Pixel,
 * also for apps that ship no monochrome icon:
 * 1. draw the icon as the user normally sees it (background + foreground);
 * 2. find its background colour — that becomes the glass colour, as is (black stays black);
 * 3. every pixel that differs from the background becomes white, the rest transparent;
 * 4. trim to the glyph and scale it to one common size, so all icons look equally big.
 * Apps with a real monochrome icon (Android 13+) use it in step 3 instead.
 */
object IconProcessor {
    /** Glyph's longer side as a share of the tile: same for every app (studio rule: equal sizes). */
    private const val GLYPH = 0.54f
    private const val GLYPH_WIDE = 0.72f

    fun process(d: Drawable, size: Int): GlassIcon {
        val full = composite(d, size)
        val adaptive = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && d is AdaptiveIconDrawable
        var bg = if (adaptive) backgroundColor((d as AdaptiveIconDrawable).background, full) else ringColor(full)
        val mono = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && d is AdaptiveIconDrawable) d.monochrome else null
        var mask = when {
            mono != null -> layerAlpha(mono, size)
            // Compare with the background layer pixel by pixel: gradients in it never become glyph.
            adaptive && (d as AdaptiveIconDrawable).background != null -> differenceFrom(full, layerOnly(d.background!!, size))
            else -> differenceMask(full, bg)
        }
        // A picture-icon: an old full-square icon, or the whole old icon packed into the
        // foreground. Comparing with one background colour then marks the whole square, so the
        // glyph is found by lightness instead: the white (or black) drawing on the coloured art.
        if (mono == null && (coverage(mask) > 0.6f || ringShare(full) < 0.5f)) {
            val light = toneMask(full, light = true)
            val dark = toneMask(full, light = false)
            val pick = listOf(light, dark).firstOrNull { coverage(it) in 0.015f..0.45f }
            if (pick == null) {
                // Nothing clean to pull out (a photo-like icon): keep the original picture, as Pixel does.
                return GlassIcon(full.asImageBitmap(), average(full, null) ?: 0xFF64748B.toInt(), whiteGlyph = false)
            }
            // Glass colour = the art around the glyph, averaged (gradients give their middle shade).
            val third = full.width / 3
            val a = averageOutside(full, pick, 0, third) ?: ringColor(full)
            val b = averageOutside(full, pick, full.width - third, full.width) ?: a
            return GlassIcon(normalize(pick, size).asImageBitmap(), a, color2 = b.takeIf { distance(a, b) > 0.12f })
        }
        // A "glyph" that is just a solid block (white square instead of a logo) is not a drawing:
        // then show the original icon rather than an empty white tile.
        if (mono == null && solidness(mask) > 0.85f) {
            return GlassIcon(full.asImageBitmap(), average(full, null) ?: 0xFF64748B.toInt(), whiteGlyph = false)
        }
        val color = glassColor(full, mask, bg)
        return GlassIcon(normalize(mask, size).asImageBitmap(), color)
    }

    /** Average colour of the art outside the glyph, on the diagonal band from [from] to [to]. */
    private fun averageOutside(full: Bitmap, mask: Bitmap, from: Int, to: Int): Int? {
        val w = full.width; val h = full.height
        val px = IntArray(w * h); full.getPixels(px, 0, w, 0, 0, w, h)
        val m = IntArray(w * h); mask.getPixels(m, 0, w, 0, 0, w, h)
        var r = 0L; var g = 0L; var b = 0L; var n = 0L
        for (i in px.indices) {
            val d = (i % w + i / w) / 2
            if (d !in from until to) continue
            val c = px[i]
            if ((c ushr 24) < 200 || (m[i] ushr 24) > 60) continue
            r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF; n++
        }
        return if (n == 0L) null else argb(r / n, g / n, b / n)
    }

    private fun distance(a: Int, b: Int): Float {
        val dr = ((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)
        val dg = ((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)
        val db = (a and 0xFF) - (b and 0xFF)
        return sqrt((dr * dr + dg * dg + db * db).toFloat()) / 441.7f
    }

    /** Share of the outer ring taken by its most common colour: low means a gradient or a picture. */
    private fun ringShare(bmp: Bitmap): Float {
        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h); bmp.getPixels(px, 0, w, 0, 0, w, h)
        val counts = HashMap<Int, Int>(); var total = 0
        val e = w / 10
        for (y in 0 until h) for (x in 0 until w) {
            if (x in e until w - e && y in e until h - e) continue
            val c = px[y * w + x]
            if ((c ushr 24) < 230) continue
            val q = c and 0xE0E0E0
            counts[q] = (counts[q] ?: 0) + 1; total++
        }
        if (total == 0) return 1f
        return (counts.values.maxOrNull() ?: 0) / total.toFloat()
    }

    /** White where the icon is near-white and greyish ([light]), or near-black ([light] = false). */
    private fun toneMask(full: Bitmap, light: Boolean): Bitmap {
        val w = full.width; val h = full.height
        val px = IntArray(w * h); full.getPixels(px, 0, w, 0, 0, w, h)
        val out = IntArray(w * h)
        for (i in px.indices) {
            val c = px[i]
            val a = c ushr 24
            if (a < 5) continue
            val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
            val hi = max(r, max(g, b)); val lo = min(r, min(g, b))
            val sat = if (hi == 0) 0f else (hi - lo) / hi.toFloat()
            val k = if (light) ((hi / 255f - 0.80f) / 0.12f).coerceIn(0f, 1f) * ((0.35f - sat) / 0.15f).coerceIn(0f, 1f)
            else ((0.22f - hi / 255f) / 0.10f).coerceIn(0f, 1f)
            out[i] = ((k * a).toInt() shl 24) or 0xFFFFFF
        }
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { setPixels(out, 0, w, 0, 0, w, h) }
    }

    /** The icon as shown in a normal launcher, filling the square (no mask). */
    private fun composite(d: Drawable, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && d is AdaptiveIconDrawable) {
            val pad = size / 4 // layers are 108 dp, the visible part the middle 72 dp
            for (l in listOfNotNull(d.background, d.foreground)) {
                val m = l.mutate()
                m.setBounds(-pad, -pad, size + pad, size + pad)
                m.draw(c)
            }
        } else {
            d.setBounds(0, 0, size, size)
            d.draw(c)
        }
        return bmp
    }

    private fun layerAlpha(layer: Drawable, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val l = layer.mutate()
        val pad = size / 4
        l.setBounds(-pad, -pad, size + pad, size + pad)
        l.colorFilter = PorterDuffColorFilter(android.graphics.Color.WHITE, PorterDuff.Mode.SRC_IN)
        l.draw(Canvas(bmp))
        return bmp
    }

    /** Adaptive icon background: its layer's average colour (falls back to the edge ring). */
    private fun backgroundColor(bgLayer: Drawable?, full: Bitmap): Int {
        if (bgLayer == null) return ringColor(full)
        val s = 24
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val l = bgLayer.mutate()
        l.setBounds(-s / 4, -s / 4, s + s / 4, s + s / 4)
        l.draw(Canvas(bmp))
        return average(bmp, null) ?: ringColor(full)
    }

    /** Colour along the outer edge of the visible icon (legacy and full-bleed icons). */
    fun ringColor(bmp: Bitmap): Int {
        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h); bmp.getPixels(px, 0, w, 0, 0, w, h)
        // Bounding box of opaque pixels (round legacy icons have transparent corners).
        var l = w; var t = h; var r = -1; var b = -1
        for (y in 0 until h) for (x in 0 until w) if ((px[y * w + x] ushr 24) > 200) {
            l = min(l, x); r = max(r, x); t = min(t, y); b = max(b, y)
        }
        if (r < 0) return 0xFF64748B.toInt()
        val cx = (l + r) / 2f; val cy = (t + b) / 2f
        val rx = (r - l) / 2f; val ry = (b - t) / 2f
        val counts = HashMap<Int, Int>()
        for (y in t..b) for (x in l..r) {
            val c = px[y * w + x]
            if ((c ushr 24) < 230) continue
            val nx = (x - cx) / rx; val ny = (y - cy) / ry
            val dist = max(kotlin.math.abs(nx), kotlin.math.abs(ny))
            val round = sqrt(nx * nx + ny * ny)
            if (dist < 0.82f && round < 0.82f) continue
            // Quantise so near-equal shades count together; the most common one is the background.
            val q = c and 0xF0F0F0
            counts[q] = (counts[q] ?: 0) + 1
        }
        val top = counts.maxByOrNull { it.value }?.key ?: return average(bmp, null) ?: 0xFF64748B.toInt()
        // Average the real pixels of that shade for an exact colour.
        var rs = 0L; var gs = 0L; var bs = 0L; var n = 0L
        for (c in px) if ((c ushr 24) >= 230 && (c and 0xF0F0F0) == top) {
            rs += (c shr 16) and 0xFF; gs += (c shr 8) and 0xFF; bs += c and 0xFF; n++
        }
        return argb(rs / n, gs / n, bs / n)
    }

    /** White where the icon differs from its background colour; soft edges kept. */
    private fun differenceMask(full: Bitmap, bg: Int): Bitmap {
        val w = full.width; val h = full.height
        val px = IntArray(w * h); full.getPixels(px, 0, w, 0, 0, w, h)
        val br = (bg shr 16) and 0xFF; val bgG = (bg shr 8) and 0xFF; val bb = bg and 0xFF
        val out = IntArray(w * h)
        for (i in px.indices) {
            val c = px[i]
            val a = (c ushr 24) / 255f
            if (a < 0.02f) continue
            val dr = ((c shr 16) and 0xFF) - br
            val dg = ((c shr 8) and 0xFF) - bgG
            val db = (c and 0xFF) - bb
            val dist = sqrt((dr * dr + dg * dg + db * db).toFloat()) / 441.7f
            val k = ((dist - 0.10f) / 0.18f).coerceIn(0f, 1f)
            val alpha = (k * a * 255).toInt()
            out[i] = (alpha shl 24) or 0xFFFFFF
        }
        // Pixels outside the icon's own shape (transparent) never count.
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.setPixels(out, 0, w, 0, 0, w, h)
        return bmp
    }

    /**
     * Glass colour = the icon's background, exactly. Only when the background is (near) white
     * the glass takes the logo's colour instead, otherwise a white glyph would vanish.
     */
    private fun glassColor(full: Bitmap, mask: Bitmap, bg: Int): Int {
        if (!isNearWhite(bg)) return bg
        val w = full.width; val h = full.height
        val px = IntArray(w * h); full.getPixels(px, 0, w, 0, 0, w, h)
        val m = IntArray(w * h); mask.getPixels(m, 0, w, 0, 0, w, h)
        var r = 0.0; var g = 0.0; var b = 0.0; var n = 0.0
        for (i in px.indices) {
            if ((m[i] ushr 24) < 160 || (px[i] ushr 24) < 200) continue
            val c = px[i]
            r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF; n++
        }
        if (n == 0.0) return 0xFF64748B.toInt()
        val logo = argb((r / n).toLong(), (g / n).toLong(), (b / n).toLong())
        return if (isNearWhite(logo)) 0xFF64748B.toInt() else logo
    }

    private fun layerOnly(layer: Drawable, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val l = layer.mutate()
        val pad = size / 4
        l.setBounds(-pad, -pad, size + pad, size + pad)
        l.draw(Canvas(bmp))
        return bmp
    }

    private fun differenceFrom(full: Bitmap, base: Bitmap): Bitmap {
        val w = full.width; val h = full.height
        val a = IntArray(w * h); full.getPixels(a, 0, w, 0, 0, w, h)
        val b = IntArray(w * h); base.getPixels(b, 0, w, 0, 0, w, h)
        val out = IntArray(w * h)
        for (i in a.indices) {
            val c = a[i]; val k0 = b[i]
            val dr = ((c shr 16) and 0xFF) - ((k0 shr 16) and 0xFF)
            val dg = ((c shr 8) and 0xFF) - ((k0 shr 8) and 0xFF)
            val db = (c and 0xFF) - (k0 and 0xFF)
            val dist = sqrt((dr * dr + dg * dg + db * db).toFloat()) / 441.7f
            val k = ((dist - 0.10f) / 0.18f).coerceIn(0f, 1f)
            out[i] = ((k * (c ushr 24)).toInt() shl 24) or 0xFFFFFF
        }
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { setPixels(out, 0, w, 0, 0, w, h) }
    }

    private fun coverage(mask: Bitmap): Float {
        val w = mask.width; val h = mask.height
        val px = IntArray(w * h); mask.getPixels(px, 0, w, 0, 0, w, h)
        return px.count { (it ushr 24) > 128 } / px.size.toFloat()
    }

    /** How much of the glyph's own bounding box it fills (1 = a solid rectangle). */
    private fun solidness(mask: Bitmap): Float {
        val w = mask.width; val h = mask.height
        val px = IntArray(w * h); mask.getPixels(px, 0, w, 0, 0, w, h)
        var l = w; var t = h; var r = -1; var b = -1; var n = 0
        for (y in 0 until h) for (x in 0 until w) if ((px[y * w + x] ushr 24) > 128) {
            l = min(l, x); r = max(r, x); t = min(t, y); b = max(b, y); n++
        }
        if (r < 0) return 1f
        return n / ((r - l + 1f) * (b - t + 1f))
    }

    /** Trims the glyph to its content and centres it at one common size. */
    private fun normalize(mask: Bitmap, size: Int): Bitmap {
        val w = mask.width; val h = mask.height
        val px = IntArray(w * h); mask.getPixels(px, 0, w, 0, 0, w, h)
        var l = w; var t = h; var r = -1; var b = -1
        for (y in 0 until h) for (x in 0 until w) if ((px[y * w + x] ushr 24) > 40) {
            l = min(l, x); r = max(r, x); t = min(t, y); b = max(b, y)
        }
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        if (r < 0) return out
        val bw = (r - l + 1).toFloat(); val bh = (b - t + 1).toFloat()
        // Square-ish glyphs get GLYPH of the tile; wide word-logos may use more width so they stay readable.
        val scale = min(GLYPH_WIDE * size / bw, GLYPH * size / bh)
        val dw = bw * scale; val dh = bh * scale
        val dst = RectF((size - dw) / 2f, (size - dh) / 2f, (size + dw) / 2f, (size + dh) / 2f)
        Canvas(out).drawBitmap(mask, Rect(l, t, r + 1, b + 1), dst, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    private fun average(bmp: Bitmap, skip: ((Int) -> Boolean)?): Int? {
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        var r = 0L; var g = 0L; var b = 0L; var n = 0L
        for (c in px) {
            if ((c ushr 24) < 128 || skip?.invoke(c) == true) continue
            r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF; n++
        }
        return if (n == 0L) null else argb(r / n, g / n, b / n)
    }

    private fun argb(r: Long, g: Long, b: Long) = (0xFF shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()

    fun isNearWhite(c: Int): Boolean {
        val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
        return min(r, min(g, b)) > 215
    }
}
