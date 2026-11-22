package com.aaideas.krossvord.data

import android.content.Context
import com.aaideas.krossvord.model.Pack

/** Loads level packs bundled in assets/packs. Downloadable packs come later. */
class PackRepository(private val context: Context) {
    fun bundledPacks(): List<Pack> =
        context.assets.list("packs").orEmpty()
            .filter { it.endsWith(".json") }
            .sorted()
            .map { name -> context.assets.open("packs/$name").bufferedReader(Charsets.UTF_8).use { Pack.parse(it.readText()) } }
}
