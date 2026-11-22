package com.aaideas.krossvord.ui

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Frames of the light that runs along a picked word, on a small 720x1520 phone. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h690dp-xhdpi")
class SweepShotTest {
    @get:Rule val rule = createComposeRule()

    @Test fun sweepFrames() {
        val vm = GameViewModel(ApplicationProvider.getApplicationContext<Application>())
        vm.restartLevel()
        vm.finishTutorial()
        val word = vm.game!!.board.level.entries.maxBy { it.cells.size }
        vm.selectEntry(word)
        rule.mainClock.autoAdvance = false
        rule.setContent { KrossvordTheme { Surface(Modifier.fillMaxSize()) { PuzzleScreen(vm, isPremium = false) } } }
        rule.mainClock.advanceTimeBy(300)
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/sweep-1.png")
        rule.mainClock.advanceTimeBy(300)
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/sweep-2.png")
    }

    @Test fun clueCardSmall() {
        val vm = GameViewModel(ApplicationProvider.getApplicationContext<Application>())
        vm.restartLevel()
        vm.finishTutorial()
        vm.tapClueOf(vm.game!!.activeEntry!!)
        vm.easeActiveClue()
        rule.setContent { KrossvordTheme { Surface(Modifier.fillMaxSize()) { PuzzleScreen(vm, isPremium = false) } } }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/clue-card-small.png")
    }

    @Test fun statsScreen() {
        val vm = GameViewModel(ApplicationProvider.getApplicationContext<Application>())
        rule.setContent { KrossvordTheme { Surface(Modifier.fillMaxSize()) { StatsScreen(vm) } } }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/stats.png")
    }

    @Test fun homeMenuOnSmallPhone() {
        val vm = GameViewModel(ApplicationProvider.getApplicationContext<Application>())
        rule.setContent {
            KrossvordTheme {
                Surface(Modifier.fillMaxSize()) {
                    androidx.compose.foundation.layout.Box {
                        ExpressiveHome(vm)
                        GlassBottomMenu(vm.screen, vm::open, Modifier.align(androidx.compose.ui.Alignment.BottomCenter))
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/home-small.png")
    }
}
