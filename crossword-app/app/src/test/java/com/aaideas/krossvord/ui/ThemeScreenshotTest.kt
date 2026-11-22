package com.aaideas.krossvord.ui

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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

    /** The launcher icon (adaptive icon layers drawn together) and its themed variant. */
    @Test fun launcherIcon() {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        fun draw(res: Int, name: String) {
            val d = androidx.core.content.ContextCompat.getDrawable(ctx, res)!!
            val bmp = android.graphics.Bitmap.createBitmap(432, 432, android.graphics.Bitmap.Config.ARGB_8888)
            d.setBounds(0, 0, 432, 432)
            d.draw(android.graphics.Canvas(bmp))
            bmp.captureRoboImage("build/outputs/roborazzi/$name.png")
        }
        draw(com.aaideas.krossvord.R.mipmap.ic_launcher, "icon")
        draw(com.aaideas.krossvord.R.drawable.ic_launcher_monochrome, "icon-themed")
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

    @Test fun homeLightAndDark() {
        val vm = vm()
        vm.startLevel(0); vm.restartLevel()
        val letters = vm.columnLetters()
        "OD".forEach { ch -> vm.pickLetter(letters.indexOf(ch)) }
        vm.open(Screen.HOME)
        val home = @Composable {
            androidx.compose.foundation.layout.Box {
                ExpressiveHome(vm)
                GlassBottomMenu(Screen.HOME, {}, Modifier.align(androidx.compose.ui.Alignment.BottomCenter))
            }
        }
        shoot("home-light", ThemeMode.LIGHT, home)
        shoot("home-dark", ThemeMode.DARK, home)
    }

    @Test fun settingsLightAndDark() {
        val vm = vm()
        shoot("settings-light", ThemeMode.LIGHT) { SettingsScreen(vm, isPremium = false) }
        shoot("settings-dark", ThemeMode.DARK) { SettingsScreen(vm, isPremium = false) }
    }
}
