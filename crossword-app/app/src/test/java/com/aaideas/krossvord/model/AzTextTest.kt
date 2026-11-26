package com.aaideas.krossvord.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AzTextTest {
    @Test fun dottedAndDotlessI() {
        assertEquals("İ", AzText.normalize("i"))
        assertEquals("I", AzText.normalize("ı"))
        assertEquals('İ', AzText.normalizeLetter('i'))
        assertEquals('I', AzText.normalizeLetter('ı'))
    }

    @Test fun decomposedLettersBecomeOneCell() {
        val decomposed = "ŞU" // S + combining cedilla + U
        assertEquals(listOf('Ş', 'U'), AzText.letters(decomposed))
        assertEquals(listOf('Ə', 'L'), AzText.letters("əl"))
        assertEquals(5, AzText.letters("şəhər").size)
    }

    @Test fun alphabetHas32Letters() {
        assertEquals(32, AzText.ALPHABET.toSet().size)
        assertNull(AzText.normalizeLetter('W'))
        assertNull(AzText.normalizeLetter('1'))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonAzerbaijaniLetters() { AzText.letters("WOW") }
}
