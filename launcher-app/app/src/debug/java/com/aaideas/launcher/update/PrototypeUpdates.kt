package com.aaideas.launcher.update

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** A launcher prototype build published by CI as a GitHub pre-release tagged `steklo-<build>`. */
data class PrototypeRelease(val build: Int, val apkUrl: String) {
    val versionName get() = "0.1.$build"
}

object PrototypeUpdates {
    const val TAG_PREFIX = "steklo-"
    const val APK_NAME = "steklo.apk"

    /**
     * The repository also publishes the crossword prototype (as "latest"), so the
     * launcher reads the release list and takes the newest `steklo-` build.
     */
    fun parseList(json: String): PrototypeRelease? = runCatching {
        (Json.parseToJsonElement(json) as JsonArray).mapNotNull { el ->
            val root = el as JsonObject
            val tag = root["tag_name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            if (!tag.startsWith(TAG_PREFIX)) return@mapNotNull null
            val build = tag.removePrefix(TAG_PREFIX).toIntOrNull() ?: return@mapNotNull null
            val url = (root["assets"] as? JsonArray).orEmpty().map { it as JsonObject }
                .firstOrNull { it["name"]?.jsonPrimitive?.content == APK_NAME }
                ?.get("browser_download_url")?.jsonPrimitive?.content ?: return@mapNotNull null
            PrototypeRelease(build, url)
        }.maxByOrNull { it.build }
    }.getOrNull()

    suspend fun fetchLatest(repo: String): PrototypeRelease? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL("https://api.github.com/repos/$repo/releases?per_page=40").openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            try {
                if (conn.responseCode != 200) return@runCatching null
                parseList(conn.inputStream.bufferedReader().readText())
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    suspend fun download(context: Context, release: PrototypeRelease, onProgress: (Int) -> Unit): File? {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(dir, APK_NAME)
        val ok = withContext(Dispatchers.IO) {
            runCatching {
                val conn = URL(release.apkUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                conn.instanceFollowRedirects = true
                try {
                    if (conn.responseCode != 200) return@runCatching false
                    val total = conn.contentLengthLong
                    var done = 0L
                    var last = -1
                    val part = File(dir, "$APK_NAME.part")
                    conn.inputStream.use { input ->
                        part.outputStream().use { out ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                done += n
                                val p = if (total > 0) (done * 100 / total).toInt() else 0
                                if (p != last) { last = p; withContext(Dispatchers.Main) { onProgress(p) } }
                            }
                        }
                    }
                    part.renameTo(target)
                } finally {
                    conn.disconnect()
                }
            }.getOrDefault(false)
        }
        return target.takeIf { ok && it.length() > 0 }
    }
}
