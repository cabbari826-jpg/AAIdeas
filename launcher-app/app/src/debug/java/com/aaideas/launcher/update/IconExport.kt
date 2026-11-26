package com.aaideas.launcher.update

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.UserManager
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Prototype only: packs every app icon as the phone gives it (separate background, foreground
 * and monochrome layers) into one zip and opens the share sheet, so the icon processing can be
 * checked against the owner's real icons instead of guesses.
 */
object IconExport {
    private const val SIZE = 216

    suspend fun share(context: Context): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val la = context.getSystemService(LauncherApps::class.java)
            val um = context.getSystemService(UserManager::class.java)
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val zip = File(dir, "icons.zip")
            ZipOutputStream(zip.outputStream().buffered()).use { out ->
                val notes = StringBuilder()
                for (user in um.userProfiles) for (info in la.getActivityList(null, user)) {
                    val name = info.componentName.packageName + "_" + info.componentName.className.substringAfterLast('.')
                    val d = runCatching { info.getIcon(context.resources.displayMetrics.densityDpi) }.getOrNull() ?: continue
                    notes.append(name).append('\t').append(info.label).append('\t').append(d.javaClass.simpleName)
                    fun put(part: String, bmp: Bitmap) {
                        out.putNextEntry(ZipEntry("$name/$part.png")); bmp.compress(Bitmap.CompressFormat.PNG, 100, out); out.closeEntry()
                    }
                    put("full", draw(d, false))
                    if (Build.VERSION.SDK_INT >= 26 && d is AdaptiveIconDrawable) {
                        d.background?.let { put("bg", draw(it, true)) }
                        d.foreground?.let { put("fg", draw(it, true)) }
                        if (Build.VERSION.SDK_INT >= 33) d.monochrome?.let { put("mono", draw(it, true)); notes.append("\tmono") }
                    }
                    notes.append('\n')
                }
                out.putNextEntry(ZipEntry("apps.txt")); out.write(notes.toString().toByteArray()); out.closeEntry()
            }
            val uri = FileProvider.getUriForFile(context, context.packageName + ".updates", zip)
            val send = Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            withContext(Dispatchers.Main) {
                context.startActivity(Intent.createChooser(send, "Значки для проверки").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            true
        }.getOrDefault(false)
    }

    /** [layer] = an adaptive layer: drawn 108/72 larger, like the launcher does. */
    private fun draw(d: Drawable, layer: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val m = d.constantState?.newDrawable()?.mutate() ?: d.mutate()
        if (layer) { val pad = SIZE / 4; m.setBounds(-pad, -pad, SIZE + pad, SIZE + pad) } else if (Build.VERSION.SDK_INT >= 26 && m is AdaptiveIconDrawable) {
            val pad = SIZE / 4
            val c = Canvas(bmp)
            for (l in listOfNotNull(m.background, m.foreground)) { l.setBounds(-pad, -pad, SIZE + pad, SIZE + pad); l.draw(c) }
            return bmp
        } else m.setBounds(0, 0, SIZE, SIZE)
        m.draw(Canvas(bmp))
        return bmp
    }
}
