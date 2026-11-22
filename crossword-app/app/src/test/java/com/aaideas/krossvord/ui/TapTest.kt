package com.aaideas.krossvord.ui

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Real taps on the letter columns, through the UI, not the view model. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class TapTest {
    @get:Rule val rule = createComposeRule()

    @Test fun tappingALetterPutsItIntoTheWord() {
        val vm = GameViewModel(ApplicationProvider.getApplicationContext<Application>())
        vm.startLevel(0)
        vm.restartLevel()
        rule.setContent { KrossvordTheme { PuzzleScreen(vm, isPremium = false) } }
        rule.onAllNodesWithContentDescription("Hərf O")[0].performClick()
        rule.waitForIdle()
        assertEquals(1, vm.draft().size)
        // Tapping the lit tile again takes the letter back.
        rule.onAllNodesWithContentDescription("seçilib 1", substring = true, useUnmergedTree = true)[0].performClick()
        rule.waitForIdle()
        assertEquals(0, vm.draft().size)
        rule.onAllNodesWithContentDescription("Hərf O")[0].performClick()
        rule.waitForIdle()
        rule.onAllNodesWithContentDescription("Hərf D")[0].performClick()
        rule.waitForIdle()
        assertEquals("OD solved", 1, vm.game!!.solvedCount)
    }

    @Test fun tappingTheClueMakesItEasier() {
        val vm = GameViewModel(ApplicationProvider.getApplicationContext<Application>())
        vm.startLevel(0)
        vm.restartLevel()
        rule.setContent { KrossvordTheme { PuzzleScreen(vm, isPremium = false) } }
        rule.onNodeWithText("→ Yanğın səbəbi", substring = true).performClick()
        rule.waitForIdle()
        rule.onNodeWithText("→ Ocaqda yanır", substring = true, useUnmergedTree = true).assertExists()
    }
}
