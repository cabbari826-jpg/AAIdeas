package com.aaideas.krossvord.model

import java.text.Normalizer
import java.util.Locale

/**
 * Azerbaijani text rules shared by the player and (later) the generator.
 * Version "az-v1": NFC, then upper case with Azerbaijani rules (i → İ, ı → I).
 * Every Azerbaijani letter is exactly one grid cell.
 */
object AzText {
    const val NORMALIZATION_VERSION = "az-v1"

    private val AZ = Locale.forLanguageTag("az")

    /** The 32 letters of the Azerbaijani Latin alphabet, in alphabet order. */
    val ALPHABET: List<Char> = "ABCÇDEƏFGĞHXIİJKQLMNOÖPRSŞTUÜVYZ".toList()
    private val alphabetSet = ALPHABET.toSet()

    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFC).uppercase(AZ)

    fun normalizeLetter(c: Char): Char? {
        val n = normalize(c.toString())
        return if (n.length == 1 && n[0] in alphabetSet) n[0] else null
    }

    /** Splits an answer into cells; fails loudly on anything that is not an Azerbaijani letter. */
    fun letters(answer: String): List<Char> {
        val n = normalize(answer)
        return n.map { ch ->
            require(ch in alphabetSet) { "Not an Azerbaijani letter: '$ch' in \"$answer\"" }
            ch
        }
    }
}
