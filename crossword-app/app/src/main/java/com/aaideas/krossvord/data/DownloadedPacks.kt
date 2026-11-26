package com.aaideas.krossvord.data

import android.content.Context
import com.aaideas.krossvord.model.Pack
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** One pack offered for download, as listed in index.json of the GitHub release "packs" (Açar Söz Studio writes it). */
@Serializable
data class RemotePack(
    val packId: String,
    val title: String,
    val levels: Int = 0,
    val contentVersion: Int = 1,
    val file: String,
    val bytes: Long = 0,
    /** The bundled pack this one continues; it is listed right after it. */
    val after: String? = null,
)

@Serializable
data class PackIndex(val formatVersion: Int = 1, val packs: List<RemotePack> = emptyList())

/**
 * Packs downloaded from the release "packs" and kept in the app's files, so they play offline.
 * A file is written only after it parsed and validated as a pack, so a cut-off download never
 * breaks the game. Network calls block: call them off the main thread.
 */
class DownloadedPacks(context: Context) {
    private val dir = File(context.filesDir, DIR)
    private val prefs = context.getSharedPreferences("packs_dl", Context.MODE_PRIVATE)

    /** Every downloaded pack that still reads; a broken file is skipped (and can be downloaded again). */
    fun installed(): List<Pack> =
        dir.listFiles { f -> f.name.endsWith(".json") }.orEmpty().sortedBy { it.name }.mapNotNull { f ->
            runCatching { Pack.parse(f.readText(Charsets.UTF_8)) }.getOrNull()
        }

    fun installedVersions(): Map<String, Int> = installed().associate { it.packId to it.contentVersion }

    /** The list as last seen online, for showing the screen without internet. */
    fun cachedIndex(): PackIndex? = prefs.getString(KEY_INDEX, null)?.let { runCatching { json.decodeFromString(PackIndex.serializer(), it) }.getOrNull() }

    /** Day (epoch day) the list was last checked automatically. */
    var lastCheckDay: Long
        get() = prefs.getLong("last_check_day", -1)
        set(v) = prefs.edit().putLong("last_check_day", v).apply()

    /** Reads index.json from the release; also keeps it for offline use. */
    fun fetchIndex(): PackIndex {
        val text = get(BASE + "index.json", MAX_INDEX_BYTES)
        val index = json.decodeFromString(PackIndex.serializer(), text)
        prefs.edit().putString(KEY_INDEX, text).apply()
        return index
    }

    /** Downloads one pack, checks it and saves it. Returns the pack. */
    fun download(r: RemotePack): Pack {
        require(SAFE_ID.matches(r.packId) && SAFE_FILE.matches(r.file)) { "bad pack name" }
        val text = get(BASE + r.file, MAX_PACK_BYTES)
        val pack = Pack.parse(text)
        require(pack.packId == r.packId) { "pack id ${pack.packId} != ${r.packId}" }
        save(pack.packId, text)
        return pack
    }

    /** Writes through a temporary file, so a half-written pack is never read. */
    fun save(packId: String, text: String) {
        dir.mkdirs()
        val tmp = File(dir, "$packId.json.tmp")
        tmp.writeText(text, Charsets.UTF_8)
        val target = File(dir, "$packId.json")
        if (!tmp.renameTo(target)) { target.delete(); tmp.renameTo(target) }
    }

    fun remove(packId: String) { File(dir, "$packId.json").delete() }

    private fun get(url: String, limit: Int): String {
        var u = URL(url)
        repeat(5) {
            val c = (u.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 20_000
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/octet-stream")
            }
            try {
                val code = c.responseCode
                if (code in 300..399) {
                    // GitHub sends release files from another host; follow it ourselves (https only).
                    val next = URL(u, c.getHeaderField("Location") ?: error("redirect without location"))
                    require(next.protocol == "https") { "insecure redirect" }
                    u = next
                    return@repeat
                }
                if (code != 200) error("HTTP $code")
                val bytes = c.inputStream.use { input ->
                    val out = java.io.ByteArrayOutputStream()
                    val buf = ByteArray(16 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        require(out.size() <= limit) { "file too big" }
                    }
                    out.toByteArray()
                }
                return String(bytes, Charsets.UTF_8)
            } finally { c.disconnect() }
        }
        error("too many redirects")
    }

    companion object {
        const val DIR = "packs"
        const val BASE = "https://github.com/cabbari826-jpg/AAIdeas/releases/download/packs/"
        private const val KEY_INDEX = "index"
        private const val MAX_INDEX_BYTES = 256 * 1024
        private const val MAX_PACK_BYTES = 4 * 1024 * 1024
        private val SAFE_ID = Regex("[a-z0-9-]{1,40}")
        private val SAFE_FILE = Regex("[a-z0-9-]{1,40}\\.json")
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Bundled packs with the downloaded ones put in: each right after the pack it continues
         * ("after"), others at the end. A downloaded pack never replaces a bundled one.
         */
        fun merge(bundled: List<Pack>, downloaded: List<Pack>, after: Map<String, String?>): List<Pack> {
            val ids = bundled.map { it.packId }.toSet()
            val extra = downloaded.filter { it.packId !in ids }
            val extraIds = extra.map { it.packId }.toSet()
            val out = bundled.toMutableList()
            val tail = mutableListOf<Pack>()
            for (p in extra) {
                val anchor = after[p.packId]?.let { a -> out.indexOfFirst { it.packId == a } } ?: -1
                if (anchor >= 0) {
                    // Several packs after the same one stay in order.
                    var at = anchor + 1
                    while (at < out.size && out[at].packId in extraIds) at++
                    out.add(at, p)
                } else tail += p
            }
            return out + tail
        }
    }
}
