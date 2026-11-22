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

    @Test fun puzzleInProgress() {
        val vm = vm()
        vm.startLevel(0)
        vm.restartLevel()
        "ODAŞ".forEach(vm::type) // two words solved
        vm.tapCell(Cell(0, 1))   // clue cell for OTAQ ↓ (O is already in place)
        vm.type('T')
        captureRoboImage("build/outputs/roborazzi/puzzle.png") {
            KrossvordTheme { Surface(Modifier.fillMaxSize()) { PuzzleScreen(vm, isPremium = false) } }
        }
    }

    @Test fun puzzleKeyboardMode() {
        val vm = vm()
        vm.setKeyboardMode(true)
        vm.startLevel(0)
        vm.restartLevel()
        captureRoboImage("build/outputs/roborazzi/puzzle-keyboard.png") {
            KrossvordTheme { Surface(Modifier.fillMaxSize()) { PuzzleScreen(vm, isPremium = false) } }
        }
    }

    @Test fun home() {
        val vm = vm()
        captureRoboImage("build/outputs/roborazzi/home.png") {
            KrossvordTheme { Surface(Modifier.fillMaxSize()) { HomeScreen(vm) } }
        }
    }
}
