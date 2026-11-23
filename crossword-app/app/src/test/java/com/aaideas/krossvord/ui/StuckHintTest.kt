package com.aaideas.krossvord.ui

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aaideas.krossvord.data.DownloadedPacks
import com.aaideas.krossvord.data.PackCache
import com.aaideas.krossvord.data.ProgressBackup
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The gentle "stuck?" pill: not before STUCK_SECONDS, then it shows (frames advanced by hand). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h760dp-xhdpi")
class StuckHintTest {
    private fun vm() = GameViewModel(ApplicationProvider.getApplicationContext<Application>())

    @get:org.junit.Rule val rule = androidx.compose.ui.test.junit4.createComposeRule()

    @Test fun stuckHintShowsAfterAWhile() {
        val vm = vm()
        vm.startLevel(0)
        vm.restartLevel()
        vm.finishTutorial()
        rule.mainClock.autoAdvance = false
        rule.setContent { KrossvordTheme { Surface(Modifier.fillMaxSize()) { PuzzleScreen(vm, isPremium = false) } } }
        rule.mainClock.advanceTimeBy(30_000)
        rule.onAllNodesWithText("Çətindir", substring = true).assertCountEquals(0)
        rule.mainClock.advanceTimeBy(STUCK_SECONDS * 1000 + 2_000)
        rule.onNodeWithText("Çətindir", substring = true).assertExists()
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/v21-stuck.png")
    }

    /** The other looks for the same moment, for the owner to choose from. */
    private fun styleShot(style: StuckStyle) {
        val vm = vm()
        vm.startLevel(0)
        vm.restartLevel()
        vm.finishTutorial()
        vm.stuckSeconds = 1
        vm.stuckStyle = style
        rule.mainClock.autoAdvance = false
        rule.setContent { KrossvordTheme { Surface(Modifier.fillMaxSize()) { PuzzleScreen(vm, isPremium = false) } } }
        rule.mainClock.advanceTimeBy(1_000 + 4_500)
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/v21-stuck-${style.name.lowercase()}.png")
    }

    @Test fun stuckOwl() = styleShot(StuckStyle.OWL)
    @Test fun stuckGlow() = styleShot(StuckStyle.GLOW)
    @Test fun stuckKey() = styleShot(StuckStyle.KEY)
    @Test fun stuckKeyTall() = styleShot(StuckStyle.KEY_TALL)
}

/** The whole settings page in one tall picture (new switches and the backup buttons are low down). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h3400dp-xhdpi")
class SettingsTallShotTest {
    @Test fun settingsWhole() {
        val vm = GameViewModel(ApplicationProvider.getApplicationContext<Application>())
        captureRoboImage("build/outputs/roborazzi/v21-settings-all.png") {
            KrossvordTheme { Surface(Modifier.fillMaxSize()) { SettingsScreen(vm, isPremium = false) } }
        }
    }
}
