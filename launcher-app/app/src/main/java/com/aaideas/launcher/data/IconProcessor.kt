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
        val bgLayer = if (adaptive) (d as AdaptiveIconDrawable).background else null
        var bg = if (adaptive) backgroundColor(bgLayer, full) else ringColor(full)

        // 1. A real themed (monochrome) icon. Some apps put their full-colour picture there
        //    (a solid block or a smudge once made white), so it must look like a glyph to be used.
        val mono = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && d is AdaptiveIconDrawable) d.monochrome else null
        if (mono != null) {
            val m = clean(layerAlpha(mono, size))
            if (isGlyph(m) && !isColourPicture(mono, size)) return finish(m, full, bg, size)
        }

        // 2. Everything that differs from the background is the drawing.
        var mask = clean(if (bgLayer != null) differenceFrom(full, layerOnly(bgLayer, size)) else differenceMask(full, bg))

        // 3. A picture-icon (old square icon, or the whole old icon in the foreground): the glyph
        //    is the white (or black) drawing on the coloured art, found by lightness.
        if (coverage(mask) > 0.6f || (!adaptive && ringShare(full) < 0.5f)) {
            val pick = listOf(toneMask(full, light = true), toneMask(full, light = false)).map { clean(it) }.firstOrNull { isGlyph(it) }
            if (pick != null) {
                val third = full.width / 3
                val a = averageOutside(full, pick, 0, third) ?: ringColor(full)
                val b = averageOutside(full, pick, full.width - third, full.width) ?: a
                return GlassIcon(normalize(pick, size).asImageBitmap(), readable(a), color2 = readable(b).takeIf { distance(a, b) > 0.12f })
            }
        }

        // 4. A plate inside the icon (atb360: purple card on white; Azercell-like: white card):
        //    the plate is the real background and the drawing is what sits on it.
        repeat(2) {
            if (isGlyph(mask)) return@repeat
            val plate = plateColor(full, mask) ?: return@repeat
            val inner = clean(insidePlate(full, mask, plate))
            if (coverage(inner) >= 0.01f) { bg = plate; mask = inner }
        }
        if (isGlyph(mask)) return finish(mask, full, bg, size)
        // A solid but shaped logo (not a plain square): its silhouette, like a themed icon.
        if (coverage(mask) in 0.012f..0.6f && solidness(mask) < 0.93f) return finish(mask, full, bg, size)

        // 5. Nothing clean to pull out (photo-like art): the original picture.
        return GlassIcon(full.asImageBitmap(), average(full, null) ?: 0xFF64748B.toInt(), whiteGlyph = false)
    }

    private fun finish(mask: Bitmap, full: Bitmap, bg: Int, size: Int) =
        GlassIcon(normalize(mask, size).asImageBitmap(), readable(glassColor(full, mask, bg)))

    /** A drawing, not a block and not a speck. */
    private fun isGlyph(m: Bitmap): Boolean = coverage(m) in 0.012f..0.5f && solidness(m) < 0.8f

    /** Drops faint pixels (soft edges of a picture pasted on a background) that would show as a frame. */
    private fun clean(m: Bitmap): Bitmap {
        val w = m.width; val h = m.height
        val px = IntArray(w * h); m.getPixels(px, 0, w, 0, 0, w, h)
        for (i in px.indices) {
            val a = px[i] ushr 24
            px[i] = if (a < 70) 0 else (((a - 70) * 255 / 185) shl 24) or 0xFFFFFF
        }
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { setPixels(px, 0, w, 0, 0, w, h) }
    }

    /** A monochrome layer that is really a colour picture (many saturated pixels). */
    private fun isColourPicture(mono: Drawable, size: Int): Boolean {
        val bmp = layerOnly(mono, size)
        val px = IntArray(size * size); bmp.getPixels(px, 0, size, 0, 0, size, size)
        var n = 0; var col = 0
        for (c in px) {
            if ((c ushr 24) < 200) continue
            n++
            val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
            if (max(r, max(g, b)) - min(r, min(g, b)) > 60) col++
        }
        return n > 0 && col > n * 0.25f
    }

    /** The colour filling most of the drawing's box, if one colour does (a card / plate). */
    private fun plateColor(full: Bitmap, mask: Bitmap): Int? {
        val w = full.width; val h = full.height
        val px = IntArray(w * h); full.getPixels(px, 0, w, 0, 0, w, h)
        val m = IntArray(w * h); mask.getPixels(m, 0, w, 0, 0, w, h)
        val counts = HashMap<Int, Int>(); var n = 0
        for (i in px.indices) {
            if ((m[i] ushr 24) < 200 || (px[i] ushr 24) < 230) continue
            val q = px[i] and 0xE0E0E0
            counts[q] = (counts[q] ?: 0) + 1; n++
        }
        val top = counts.maxByOrNull { it.value } ?: return null
        if (n == 0 || top.value < n * 0.55f) return null
        var r = 0L; var g = 0L; var b = 0L; var k = 0L
        for (i in px.indices) if ((m[i] ushr 24) >= 200 && (px[i] and 0xE0E0E0) == top.key) {
            r += (px[i] shr 16) and 0xFF; g += (px[i] shr 8) and 0xFF; b += px[i] and 0xFF; k++
        }
        return argb(r / k, g / k, b / k)
    }

    /** Inside the plate's box: what differs from the plate colour (text and marks on the card). */
    private fun insidePlate(full: Bitmap, plateMask: Bitmap, plate: Int): Bitmap {
        val w = full.width; val h = full.height
        val m = IntArray(w * h); plateMask.getPixels(m, 0, w, 0, 0, w, h)
        var l = w; var t = h; var r = -1; var b = -1
        for (y in 0 until h) for (x in 0 until w) if ((m[y * w + x] ushr 24) > 128) {
            l = min(l, x); r = max(r, x); t = min(t, y); b = max(b, y)
        }
        val diff = differenceMask(full, plate)
        if (r < 0) return diff
        // Stay a little inside the card so its own rounded edge is not taken for a drawing.
        val inset = ((r - l) * 0.06f).toInt()
        val px = IntArray(w * h); diff.getPixels(px, 0, w, 0, 0, w, h)
        for (y in 0 until h) for (x in 0 until w) {
            if (x < l + inset || x > r - inset || y < t + inset || y > b - inset) px[y * w + x] = 0
        }
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { setPixels(px, 0, w, 0, 0, w, h) }
    }

    /**
     * Keeps the hue but makes sure a white glyph stays readable on it: light colours
     * (pale blue, light green, light grey) are darkened just enough; dark ones stay as they are.
     */
    fun readable(c: Int): Int {
        val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
        val lum = (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
        if (lum <= 0.56f) return c
        val k = 0.56f / lum
        return argb((r * k).toLong(), (g * k).toLong(), (b * k).toLong())
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
        if (lightness(bg) < 0.78f) return bg
        // Light background: the logo's own main colour (most common, not an average of many).
        val w = full.width; val h = full.height
        val px = IntArray(w * h); full.getPixels(px, 0, w, 0, 0, w, h)
        val m = IntArray(w * h); mask.getPixels(m, 0, w, 0, 0, w, h)
        val counts = HashMap<Int, Int>()
        for (i in px.indices) {
            if ((m[i] ushr 24) < 160 || (px[i] ushr 24) < 200) continue
            val q = px[i] and 0xE0E0E0
            if (lightness(q or (0xFF shl 24)) > 0.85f) continue
            counts[q] = (counts[q] ?: 0) + 1
        }
        // Many-coloured logo with a grey part: a real colour of the logo, not its grey.
        val vivid = counts.filterKeys { saturation(it) > 0.35f }
        if (vivid.values.sum() > counts.values.sum() * 0.3f) { counts.clear(); counts.putAll(vivid) }
        // White logo on a light background: the background, made readable later.
        val top = counts.maxByOrNull { it.value }?.key ?: return bg
        var r = 0L; var g = 0L; var b = 0L; var n = 0L
        for (i in px.indices) if ((m[i] ushr 24) >= 160 && (px[i] and 0xE0E0E0) == top) {
            r += (px[i] shr 16) and 0xFF; g += (px[i] shr 8) and 0xFF; b += px[i] and 0xFF; n++
        }
        return if (n == 0L) 0xFF64748B.toInt() else argb(r / n, g / n, b / n)
    }

    private fun saturation(c: Int): Float {
        val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
        val hi = max(r, max(g, b)); val lo = min(r, min(g, b))
        return if (hi == 0) 0f else (hi - lo) / hi.toFloat()
    }

    private fun lightness(c: Int): Float {
        val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
        return (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
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
            val k = ((dist - 0.04f) / 0.10f).coerceIn(0f, 1f)
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
