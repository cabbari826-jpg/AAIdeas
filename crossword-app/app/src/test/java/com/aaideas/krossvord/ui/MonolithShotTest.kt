package com.aaideas.krossvord.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The owner's "monolith" pass: solid dialogs in Şüşə, equal menu tabs in every theme. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h852dp-xxhdpi")
class MonolithShotTest {
    @get:Rule val rule = createComposeRule()

    private fun glassVm(): GameViewModel {
        val vm = GameViewModel(ApplicationProvider.getApplicationContext<Application>())
        vm.unlockAllSkinsForTesting(); vm.changeSkin(Skin.SUSE)
        vm.startLevel(0); vm.restartLevel(); vm.finishTutorial()
        return vm
    }

    @Test fun glassKeywordDialog() {
        val vm = glassVm()
        rule.mainClock.autoAdvance = false
        rule.setContent {
            KrossvordTheme(ThemeMode.LIGHT, skin = Skin.SUSE) {
                MaxGlassColors(on = true, dark = false, liquid = true) {
                    Box(Modifier.fillMaxSize()) {
                        GlassBackdrop(false, Modifier.fillMaxSize())
                        PuzzleScreen(vm, isPremium = false)
                        // The keyword window's text field never idles under Robolectric; same window, text only.
                        SolidAlertDialog(
                            onDismissRequest = {},
                            title = { androidx.compose.material3.Text("Açar sözü təxmin et") },
                            text = { androidx.compose.material3.Text("Düz cavab: +2 pulsuz ipucu.") },
                            confirmButton = { androidx.compose.material3.Button(onClick = {}) { androidx.compose.material3.Text("Yoxla") } },
                            dismissButton = { androidx.compose.material3.TextButton(onClick = {}) { androidx.compose.material3.Text("Bağla") } },
                        )
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(1500)
        captureScreenRoboImage("build/outputs/roborazzi/mono-keyword-dialog.png")
    }

    @Test fun glassResultDialog() {
        val vm = glassVm()
        val result = LevelResult(xp = 20, stars = 2, seconds = 119, praise = "Mükəmməl!", keyword = "HAMAM", medals = emptyList(),
            fact = "Azərbaycanın ən yüksək zirvəsi Bazardüzüdür: 4466 metr.")
        rule.mainClock.autoAdvance = false
        rule.setContent {
            KrossvordTheme(ThemeMode.LIGHT, skin = Skin.SUSE) {
                MaxGlassColors(on = true, dark = false, liquid = true) {
                    Box(Modifier.fillMaxSize()) {
                        GlassBackdrop(false, Modifier.fillMaxSize())
                        PuzzleScreen(vm, isPremium = false)
                        LevelResultDialog("Səviyyə 11", result, {}, {})
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(1500)
        captureScreenRoboImage("build/outputs/roborazzi/mono-result-dialog.png")
    }

    /** Step 1 of the frosted preview: the level screen alone (blurred outside the test, see tools). */
    @Test fun glassBackground() {
        val vm = glassVm()
        rule.mainClock.autoAdvance = false
        rule.setContent {
            KrossvordTheme(ThemeMode.LIGHT, skin = Skin.SUSE) {
                MaxGlassColors(on = true, dark = false, liquid = true) {
                    Box(Modifier.fillMaxSize()) {
                        GlassBackdrop(false, Modifier.fillMaxSize())
                        PuzzleScreen(vm, isPremium = false)
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(1500)
        captureScreenRoboImage("build/outputs/roborazzi/mono-bg.png")
    }

    /** Step 2: the dialogs over the blurred screen, as the phone's window blur shows them. */
    @Test fun frostedDialogs() {
        val file = java.io.File("build/outputs/roborazzi/mono-bg-blur.png")
        org.junit.Assume.assumeTrue(file.exists())
        val bmp = android.graphics.BitmapFactory.decodeFile(file.path).asImageBitmap()
        GlassPreview.forceFrosted = true
        try {
            run {
                rule.mainClock.autoAdvance = false
                rule.setContent {
                    KrossvordTheme(ThemeMode.LIGHT, skin = Skin.SUSE) {
                        MaxGlassColors(on = true, dark = false, liquid = true) {
                            Box(Modifier.fillMaxSize()) {
                                androidx.compose.foundation.Image(bmp, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.FillBounds)
                                SolidAlertDialog(
                                    onDismissRequest = {},
                                    title = { androidx.compose.material3.Text("Açar sözü təxmin et") },
                                    text = { androidx.compose.material3.Text("Düz cavab: +2 pulsuz ipucu.") },
                                    confirmButton = { androidx.compose.material3.Button(onClick = {}) { androidx.compose.material3.Text("Yoxla") } },
                                    dismissButton = { androidx.compose.material3.TextButton(onClick = {}) { androidx.compose.material3.Text("Bağla") } },
                                )
                            }
                        }
                    }
                }
                rule.mainClock.advanceTimeBy(1500)
                captureScreenRoboImage("build/outputs/roborazzi/frost-keyword.png")
            }
        } finally { GlassPreview.forceFrosted = false }
    }

    @Test fun frostedResult() {
        val file = java.io.File("build/outputs/roborazzi/mono-bg-blur.png")
        org.junit.Assume.assumeTrue(file.exists())
        val bmp = android.graphics.BitmapFactory.decodeFile(file.path).asImageBitmap()
        GlassPreview.forceFrosted = true
        try {
            val result = LevelResult(xp = 20, stars = 2, seconds = 119, praise = "Mükəmməl!", keyword = "HAMAM", medals = emptyList(),
                fact = "Azərbaycanın ən yüksək zirvəsi Bazardüzüdür: 4466 metr.")
            rule.mainClock.autoAdvance = false
            rule.setContent {
                KrossvordTheme(ThemeMode.LIGHT, skin = Skin.SUSE) {
                    MaxGlassColors(on = true, dark = false, liquid = true) {
                        Box(Modifier.fillMaxSize()) {
                            androidx.compose.foundation.Image(bmp, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.FillBounds)
                            LevelResultDialog("Səviyyə 11", result, {}, {})
                        }
                    }
                }
            }
            rule.mainClock.advanceTimeBy(1500)
            captureScreenRoboImage("build/outputs/roborazzi/frost-result.png")
        } finally { GlassPreview.forceFrosted = false }
    }

    /** Dark Şüşə with two letters chosen: the chosen tiles must stay bright. */
    @Test fun glassDarkPicked() {
        val vm = glassVm()
        val letters = vm.columnLetters()
        vm.pickLetter(letters.indexOf('O'))
        rule.mainClock.autoAdvance = false
        rule.setContent {
            KrossvordTheme(ThemeMode.DARK, skin = Skin.SUSE) {
                MaxGlassColors(on = true, dark = true, liquid = true) {
                    Box(Modifier.fillMaxSize()) {
                        GlassBackdrop(true, Modifier.fillMaxSize())
                        PuzzleScreen(vm, isPremium = false)
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(1500)
        captureScreenRoboImage("build/outputs/roborazzi/glass-dark-picked.png")
    }

    /** Bigger phone font (the app's own large mode goes up to 1.5x) and a narrow phone: labels must stay centred. */
    @Config(qualifiers = "w360dp-h740dp-xhdpi")
    @Test fun menuBigFont() {
        rule.setContent {
            val d = androidx.compose.ui.platform.LocalDensity.current
            Column {
                for (scale in listOf(1f, 1.15f, 1.3f, 1.5f)) {
                    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density, scale)) {
                        KrossvordTheme(ThemeMode.LIGHT, skin = Skin.KLASSIK) {
                            Surface(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(vertical = 4.dp)) {
                                    listOf(Screen.HOME, Screen.STATS).forEach { tab ->
                                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { GlassBottomMenu(tab, {}) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        rule.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/mono-menus-bigfont.png")
    }

    @Test fun menuEveryTheme() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            Column {
                for ((skin, mode) in listOf(Skin.KLASSIK to ThemeMode.LIGHT, Skin.KLASSIK to ThemeMode.DARK, Skin.SUSE to ThemeMode.LIGHT)) {
                    KrossvordTheme(mode, skin = skin) {
                        Surface(Modifier.fillMaxWidth()) {
                            Box(Modifier.fillMaxWidth()) {
                                if (skin.glass) GlassBackdrop(false, Modifier.matchParentSize())
                                Column(Modifier.padding(vertical = 4.dp)) {
                                    listOf(Screen.HOME, Screen.STATS).forEach { tab ->
                                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { GlassBottomMenu(tab, {}) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(1500)
        captureScreenRoboImage("build/outputs/roborazzi/mono-menus.png")
    }
}
