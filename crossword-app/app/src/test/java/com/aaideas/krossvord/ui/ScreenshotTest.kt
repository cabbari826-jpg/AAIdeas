package com.aaideas.krossvord.ui

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.aaideas.krossvord.model.Cell
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Screenshots of key screens; images land in app/build/outputs/roborazzi. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class ScreenshotTest {
    private fun vm() = GameViewModel(ApplicationProvider.getApplicationContext<Application>())

    private fun shoot(vm: GameViewModel, name: String) =
        captureRoboImage("build/outputs/roborazzi/$name.png") {
            KrossvordTheme { Surface(Modifier.fillMaxSize()) { PuzzleScreen(vm, isPremium = false) } }
        }

    /** Default mode: anagram columns, all clues visible; OD solved, two letters of the next word picked. */
    @Test fun puzzleColumns() {
        val vm = vm()
        vm.startLevel(0)
        vm.restartLevel()
        val letters = vm.columnLetters()
        "OD".forEach { ch -> vm.pickLetter(letters.indexOf(ch)) }
        vm.tapCell(Cell(0, 1)) // OTAQ ↓
        val next = vm.columnLetters()
        vm.pickLetter(next.indexOf('T'))
        vm.pickLetter(next.indexOf('A'))
        shoot(vm, "puzzle")
    }

    /** Wrong words use up the shared lives (5 for a new player): the player is offered an ad, the word of the day, or waiting. */
    @Test fun outOfLives() {
        val vm = vm()
        vm.startLevel(0)
        vm.restartLevel()
        repeat(com.aaideas.krossvord.model.Lives.START) {
            val letters = vm.columnLetters()
            // "DO" instead of "OD": always wrong.
            vm.pickLetter(letters.indexOf('D'))
            vm.pickLetter(letters.indexOf('O'))
            vm.clearDraft()
        }
        org.junit.Assert.assertTrue(vm.outOfLives)
        shoot(vm, "puzzle-no-lives")
    }

    @Test fun hardLevel() {
        val vm = vm()
        vm.startLevel(2)
        vm.restartLevel()
        shoot(vm, "puzzle-level3")
    }

    @Test fun newLevels() {
        val vm = vm()
        for (n in listOf(5, 12, 25)) {
            vm.startLevel(n - 1)
            vm.restartLevel()
            shoot(vm, "level-$n")
        }
    }

    @Test fun themedPacks() {
        val vm = vm()
        val music = vm.packs.indexOfFirst { it.packId == "az-musiqi" }
        vm.startLevel(vm.packLevels(music).first().first)
        vm.restartLevel()
        shoot(vm, "theme-musiqi-1")
        val geo = vm.packs.indexOfFirst { it.packId == "az-cografiya" }
        vm.startLevel(vm.packLevels(geo).first().first)
        vm.restartLevel()
        shoot(vm, "theme-cografiya-1")
        vm.open(Screen.LEVELS)
        captureRoboImage("build/outputs/roborazzi/levels.png") { KrossvordTheme { LevelsScreen(vm) } }
    }

    @Test fun puzzleTiles() {
        val vm = vm()
        vm.setInput(InputMode.TILES)
        vm.startLevel(0)
        vm.restartLevel()
        shoot(vm, "puzzle-tiles")
    }

    @Test fun puzzleKeyboardMode() {
        val vm = vm()
        vm.setInput(InputMode.KEYBOARD)
        vm.startLevel(0)
        vm.restartLevel()
        shoot(vm, "puzzle-keyboard")
    }

    @Test fun home() {
        val vm = vm()
        captureRoboImage("build/outputs/roborazzi/home.png") {
            KrossvordTheme { Surface(Modifier.fillMaxSize()) { HomeScreen(vm) } }
        }
    }

    @Test fun wordOfDay() {
        val vm = vm()
        vm.daily.refresh()
        "KƏ".forEach { vm.daily.type(it) }
        captureRoboImage("build/outputs/roborazzi/word-of-day.png") {
            KrossvordTheme { Surface(Modifier.fillMaxSize()) { WordOfDayScreen(vm.daily, isPremium = false, onBack = {}) } }
        }
    }

    @Test fun clueCard() {
        val vm = vm()
        vm.restartLevel()
        vm.finishTutorial()
        val e = vm.game!!.activeEntry!!
        vm.tapClueOf(e)
        vm.easeActiveClue()
        shoot(vm, "clue-card")
    }
}
