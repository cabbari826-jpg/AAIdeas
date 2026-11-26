package com.aaideas.krossvord.data

import android.content.Context
import com.aaideas.krossvord.model.Pack

/** Loads level packs bundled in assets/packs (main pack first, then themes). Downloadable packs come later. */
class PackRepository(private val context: Context) {
    fun bundledPacks(): List<Pack> =
        context.assets.list("packs").orEmpty()
            .filter { it.endsWith(".json") }
            .sortedWith(compareBy({ it != MAIN_PACK }, { it }))
            .map { name -> context.assets.open("packs/$name").bufferedReader(Charsets.UTF_8).use { Pack.parse(it.readText()) } }
}

private const val MAIN_PACK = "az-starter.json"
