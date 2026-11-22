package com.aaideas.krossvord.ui

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
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
        // The tutorial points at the active clue cell; tapping it opens the clue in a big card.
        rule.onNodeWithText("Haşiyəli ipucu", substring = true).assertExists()
        rule.onNodeWithContentDescription("İpucu: Yanğın səbəbi").performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Böyük ipucu: Yanğın səbəbi").assertExists()
        // The card's button opens the next, easier clue (the second one is free).
        rule.onNodeWithText("Daha asan ipucu (pulsuz)").performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Böyük ipucu: Ocaqda yanır").assertExists()
        // The third one needs an ad.
        rule.onNodeWithText("Daha asan ipucu ▶ reklam").assertExists()
        rule.onNodeWithContentDescription("Bağla").performClick()
        rule.waitForIdle()
        assertEquals(null, vm.clueCard)
        rule.onNodeWithContentDescription("İpucu: Ocaqda yanır").assertExists()
        rule.onNodeWithText("Aydındır").performClick()
        rule.waitForIdle()
        assertEquals(TUTORIAL_DONE, vm.tutorialStep)
        // Tapping another word's clue cell selects that word and opens its card.
        val other = vm.game!!.board.level.entries[1]
        rule.onNodeWithContentDescription("İpucu: ${vm.game!!.clueText(other)}").performClick()
        rule.waitForIdle()
        assertEquals(other, vm.clueCard)
        assertEquals(other, vm.game!!.activeEntry)
    }

    @Test fun homeResetWipesProgressButKeepsSettings() {
        val vm = GameViewModel(ApplicationProvider.getApplicationContext<Application>())
        vm.resetAllProgress()
        vm.setInput(InputMode.TILES)
        vm.startLevel(0)
        for (e in vm.game!!.board.level.entries) {
            vm.selectEntry(e)
            val g = vm.game!!
            e.cells.filter { g.typed[it] != g.board.solution[it] }.forEach { vm.type(g.board.solution[it]!!) }
        }
        assertEquals(1, vm.completed.size)
        rule.setContent { KrossvordTheme { HomeScreen(vm) } }
        rule.onNodeWithText("Yenidən başla").performClick()
        rule.onNodeWithText("Sıfırla").performClick()
        rule.waitForIdle()
        assertEquals(0, vm.completed.size)
        assertEquals(InputMode.TILES, vm.inputMode)
        assertEquals(1, vm.currentLevelNumber)
        vm.setInput(InputMode.COLUMNS)
    }
}
