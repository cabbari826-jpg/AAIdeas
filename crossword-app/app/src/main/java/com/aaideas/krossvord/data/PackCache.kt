package com.aaideas.krossvord.data

import android.content.Context
import com.aaideas.krossvord.model.Pack

/**
 * Bundled packs, read once per process. The activity reads them on a background thread
 * while a light splash shows, so slow phones never freeze on start; anything that asks
 * earlier (tests, a restored screen) just reads them right away.
 */
object PackCache {
    @Volatile private var packs: List<Pack>? = null

    val isLoaded: Boolean get() = packs != null

    fun get(context: Context): List<Pack> =
        packs ?: synchronized(this) { packs ?: PackRepository(context.applicationContext).bundledPacks().also { packs = it } }
}
