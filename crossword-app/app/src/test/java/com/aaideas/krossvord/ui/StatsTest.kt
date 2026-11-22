package com.aaideas.krossvord.ui

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class StatsTest {
    @Test fun playTimeReads() {
        assertEquals("0 dəq", playTimeText(30))
        assertEquals("12 dəq", playTimeText(12 * 60 + 5))
        assertEquals("3 saat 05 dəq", playTimeText(3 * 3600 + 5 * 60))
    }

    @Test fun solvingAWordCountsItAndPlaysWithoutCrashing() {
        val vm = GameViewModel(ApplicationProvider.getApplicationContext<Application>())
        vm.restartLevel()
        vm.setInput(InputMode.KEYBOARD)
        val before = vm.wordsFound
        val e = vm.game!!.activeEntry!!
        e.answer.forEach { vm.type(it) }
        assertEquals(before + 1, vm.wordsFound)
    }
}
