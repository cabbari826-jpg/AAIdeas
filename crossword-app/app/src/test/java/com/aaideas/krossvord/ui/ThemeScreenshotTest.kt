package com.aaideas.krossvord.ui

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aaideas.krossvord.model.Cell
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Light/dark screenshots and the theme choice surviving a restart. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class ThemeScreenshotTest {
    private fun vm() = GameViewModel(ApplicationProvider.getApplicationContext<Application>())

    private fun shoot(name: String, mode: ThemeMode, screen: @Composable () -> Unit) =
        captureRoboImage("build/outputs/roborazzi/$name.png") {
            KrossvordTheme(mode) { Surface(Modifier.fillMaxSize()) { screen() } }
        }

    @Test fun themeChoiceIsRemembered() {
        vm().changeTheme(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, vm().themeMode)
    }

    @Test fun puzzleDark() {
        val vm = vm()
        vm.startLevel(0)
        vm.restartLevel()
        val letters = vm.columnLetters()
        "OD".forEach { ch -> vm.pickLetter(letters.indexOf(ch)) }
        vm.tapCell(Cell(0, 1))
        shoot("puzzle-dark", ThemeMode.DARK) { PuzzleScreen(vm, isPremium = false) }
    }

    @Test fun homeDark() {
        val vm = vm()
        shoot("home-dark", ThemeMode.DARK) { HomeScreen(vm) }
    }

    @Test fun settingsLightAndDark() {
        val vm = vm()
        shoot("settings-light", ThemeMode.LIGHT) { SettingsScreen(vm, isPremium = false) }
        shoot("settings-dark", ThemeMode.DARK) { SettingsScreen(vm, isPremium = false) }
    }
}
