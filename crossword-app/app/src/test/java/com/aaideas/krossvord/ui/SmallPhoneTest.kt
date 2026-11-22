package com.aaideas.krossvord.ui

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A small low-end phone (720x1520 HD+, like a Redmi 8): 360dp wide, ~690dp left after system bars. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h690dp-xhdpi")
class SmallPhoneTest {
    private fun shoot(name: String, setup: GameViewModel.() -> Unit) {
        val vm = GameViewModel(ApplicationProvider.getApplicationContext<Application>())
        vm.setup()
        captureRoboImage("build/outputs/roborazzi/$name.png") {
            KrossvordTheme { Surface(Modifier.fillMaxSize()) { PuzzleScreen(vm, isPremium = false) } }
        }
    }

    @Test fun puzzleColumns() = shoot("small-columns") { restartLevel(); finishTutorial() }
    @Test fun puzzleKeyboard() = shoot("small-keyboard") { restartLevel(); finishTutorial(); setInput(InputMode.KEYBOARD) }
}
