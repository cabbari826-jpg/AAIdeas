package com.aaideas.krossvord.data

import android.content.Context
import com.aaideas.krossvord.model.Pack

/**
 * Bundled packs plus downloaded ones, read once per process. The activity reads them on a
 * background thread while a light splash shows, so slow phones never freeze on start; anything
 * that asks earlier (tests, a restored screen) just reads them right away. After a download,
 * [reload] reads the downloaded packs again (the bundled ones stay in memory).
 */
object PackCache {
    @Volatile private var bundled: List<Pack>? = null
    @Volatile private var packs: List<Pack>? = null

    val isLoaded: Boolean get() = packs != null

    fun get(context: Context): List<Pack> =
        packs ?: synchronized(this) { packs ?: read(context).also { packs = it } }

    fun reload(context: Context): List<Pack> = synchronized(this) { read(context).also { packs = it } }

    private fun read(context: Context): List<Pack> {
        val app = context.applicationContext
        val base = bundled ?: PackRepository(app).bundledPacks().also { bundled = it }
        val dl = DownloadedPacks(app)
        val downloaded = dl.installed()
        if (downloaded.isEmpty()) return base
        val after = dl.cachedIndex()?.packs.orEmpty().associate { it.packId to it.after }
        return DownloadedPacks.merge(base, downloaded, after)
    }
}
