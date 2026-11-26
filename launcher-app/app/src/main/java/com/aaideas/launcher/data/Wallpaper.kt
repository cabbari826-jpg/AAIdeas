package com.aaideas.launcher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.net.Uri
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The launcher paints its own wallpaper. Android does not let an ordinary app read
 * the system wallpaper's pixels any more, and glass needs them to blur.
 * Presets are drawn in code (no image files, tiny APK); a photo is picked by the user.
 */
object Wallpaper {
    private fun photoFile(context: Context) = File(context.filesDir, "wallpaper.jpg")

    fun hasPhoto(context: Context) = photoFile(context).exists()

    /** Full-screen bitmap for [kind], [w] x [h]. */
    fun render(context: Context, kind: WallpaperKind, w: Int, h: Int): Bitmap {
        if (kind == WallpaperKind.PHOTO) loadPhoto(context, w, h)?.let { return it }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        fun linear(angleTop: Int, mid: Int, bottom: Int) {
            p.shader = LinearGradient(w * 0.1f, 0f, w * 0.9f, h.toFloat(), intArrayOf(angleTop, mid, bottom), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        }
        fun blob(cx: Float, cy: Float, rx: Float, ry: Float, color: Int) {
            val m = Matrix().apply { setScale(1f, ry / rx, cx * w, cy * h) }
            p.shader = RadialGradient(cx * w, cy * h, rx * w, color, color and 0x00FFFFFF, Shader.TileMode.CLAMP).apply { setLocalMatrix(m) }
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        }
        when (kind) {
            WallpaperKind.SEA, WallpaperKind.PHOTO -> {
                linear(0xFFCFE6EA.toInt(), 0xFF5AA3B3.toInt(), 0xFF14485A.toInt())
                blob(0.75f, 0.25f, 0.6f, 0.4f * h / w, 0xFF9FD0D8.toInt())
                blob(0.30f, 0.75f, 0.7f, 0.4f * h / w, 0xFF1E6F86.toInt())
            }
            WallpaperKind.SAND -> {
                linear(0xFFB9A58A.toInt(), 0xFFA08767.toInt(), 0xFF6E5640.toInt())
                blob(0.30f, 0.40f, 0.5f, 0.35f * h / w, 0xFFCDB89C.toInt())
                blob(0.70f, 0.70f, 0.6f, 0.4f * h / w, 0xFF7A5A3E.toInt())
            }
            WallpaperKind.NIGHT -> {
                linear(0xFF1C2733.toInt(), 0xFF121820.toInt(), 0xFF0B0F14.toInt())
                blob(0.25f, 0.20f, 0.7f, 0.45f * h / w, 0xFF2F4256.toInt())
                blob(0.80f, 0.75f, 0.6f, 0.4f * h / w, 0xFF3B2F4F.toInt())
            }
        }
        return bmp
    }

    /** Copies the picked photo into app storage, scaled to the screen (keeps the APK's data small). */
    fun savePhoto(context: Context, uri: Uri, w: Int, h: Int): Boolean = runCatching {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, opts) }
        var sample = 1
        while (opts.outWidth / (sample * 2) >= w && opts.outHeight / (sample * 2) >= h) sample *= 2
        val src = context.contentResolver.openInputStream(uri)!!.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }!!
        val out = centerCrop(src, w, h)
        photoFile(context).outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        true
    }.getOrDefault(false)

    private fun loadPhoto(context: Context, w: Int, h: Int): Bitmap? {
        val f = photoFile(context)
        if (!f.exists()) return null
        val src = BitmapFactory.decodeFile(f.path) ?: return null
        return centerCrop(src, w, h)
    }

    private fun centerCrop(src: Bitmap, w: Int, h: Int): Bitmap {
        val scale = max(w / src.width.toFloat(), h / src.height.toFloat())
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val m = Matrix().apply {
            setScale(scale, scale)
            postTranslate((w - src.width * scale) / 2f, (h - src.height * scale) / 2f)
        }
        Canvas(out).drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    /**
     * The blurred copy that every glass element shows through. Made once per
     * wallpaper and blur setting at a quarter of the screen size (fast even on
     * old phones) and stretched back up when drawn, which smooths it further.
     */
    fun blurred(src: Bitmap, blurDp: Int, density: Float): Bitmap {
        val sw = max(1, src.width / 4)
        val sh = max(1, src.height / 4)
        val small = Bitmap.createScaledBitmap(src, sw, sh, true)
        val radius = (blurDp * density / 4f / 1.7f).roundToInt()
        if (radius < 1) return small
        val px = IntArray(sw * sh)
        small.getPixels(px, 0, sw, 0, 0, sw, sh)
        repeat(3) { boxBlur(px, sw, sh, radius) }
        val out = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, sw, 0, 0, sw, sh)
        return out
    }

    /** Separable box blur (horizontal then vertical); three passes look Gaussian. */
    fun boxBlur(px: IntArray, w: Int, h: Int, r: Int) {
        val tmp = IntArray(px.size)
        pass(px, tmp, w, h, r, horizontal = true)
        pass(tmp, px, w, h, r, horizontal = false)
    }

    private fun pass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        val div = 2 * r + 1
        for (line in 0 until lines) {
            var sr = 0; var sg = 0; var sb = 0
            fun at(i: Int): Int {
                val j = i.coerceIn(0, len - 1)
                return if (horizontal) src[line * w + j] else src[j * w + line]
            }
            for (i in -r..r) { val c = at(i); sr += (c shr 16) and 0xFF; sg += (c shr 8) and 0xFF; sb += c and 0xFF }
            for (i in 0 until len) {
                val o = if (horizontal) line * w + i else i * w + line
                dst[o] = (0xFF shl 24) or ((sr / div) shl 16) or ((sg / div) shl 8) or (sb / div)
                val add = at(i + r + 1); val rem = at(i - r)
                sr += ((add shr 16) and 0xFF) - ((rem shr 16) and 0xFF)
                sg += ((add shr 8) and 0xFF) - ((rem shr 8) and 0xFF)
                sb += (add and 0xFF) - (rem and 0xFF)
            }
        }
    }
}
