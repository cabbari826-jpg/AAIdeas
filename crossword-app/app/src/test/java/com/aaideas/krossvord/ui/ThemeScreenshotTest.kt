package com.aaideas.krossvord.ui

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.dp
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

    private fun shoot(name: String, mode: ThemeMode, skin: Skin = Skin.KLASSIK, screen: @Composable () -> Unit) =
        captureRoboImage("build/outputs/roborazzi/$name.png") {
            KrossvordTheme(mode, skin = skin) { Surface(Modifier.fillMaxSize()) { screen() } }
        }

    /** Every theme on the same half-solved puzzle, light and (for some) dark. */
    @Test fun skins() {
        val vm = vm()
        vm.startLevel(0); vm.restartLevel()
        val letters = vm.columnLetters()
        "OD".forEach { ch -> vm.pickLetter(letters.indexOf(ch)) }
        Skin.entries.forEach { skin -> shoot("skin-${skin.id}", ThemeMode.LIGHT, skin) { PuzzleScreen(vm, isPremium = false) } }
        listOf(Skin.XALCA, Skin.KAGIZ).forEach { skin -> shoot("skin-${skin.id}-dark", ThemeMode.DARK, skin) { PuzzleScreen(vm, isPremium = false) } }
    }

    @Test fun skinPicker() {
        val vm = vm()
        shoot("skin-picker", ThemeMode.LIGHT) { SkinPicker(Skin.KAGIZ, Skin.free + Skin.XALCA, {}, {}) }
    }

    @Test fun giftThemes() {
        org.junit.Assert.assertEquals(Skin.free, Skin.earned(emptySet(), 0, java.util.Calendar.getInstance().apply { set(2026, 0, 10) }))
        org.junit.Assert.assertTrue(Skin.XALCA in Skin.earned(emptySet(), 7))
        org.junit.Assert.assertTrue(Skin.QEZET in Skin.earned(setOf("rank_1"), 0))
        org.junit.Assert.assertTrue(Skin.NOVRUZ in Skin.earned(emptySet(), 0, java.util.Calendar.getInstance().apply { set(2027, 2, 20) }))
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
        AppIcon.entries.forEach { draw(it.preview, "icon-" + it.name.lowercase()) }
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

    /** Update 2.1 screens in Şüşə: home, "Bu gün", the map, packs, settings. */
    @Config(qualifiers = "w393dp-h1300dp-xxhdpi")
    @Test fun glassUpdate21() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("progress", 0).edit().putInt("month_key", 202611).putInt("month_levels", 9).commit()
        val vm = vm()
        vm.unlockAllSkinsForTesting(); vm.changeSkin(Skin.SUSE)
        val screens: List<Pair<String, @Composable () -> Unit>> = listOf(
            "home" to { ExpressiveHome(vm) },
            "today" to { DailyScreen(vm) },
            "map" to { JourneyScreen(vm) },
            "packs" to { PacksScreen(vm) },
            "settings" to { SettingsScreen(vm, isPremium = false) },
        )
        for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) for ((name, screen) in screens) {
            val tab = when (name) { "today" -> Screen.DAILY; "settings" -> Screen.SETTINGS; else -> Screen.HOME }
            shoot("glass21-$name-${mode.name.lowercase()}", mode, Skin.SUSE) {
                androidx.compose.foundation.layout.Box {
                    GlassBackdrop(mode == ThemeMode.DARK, androidx.compose.ui.Modifier.matchParentSize())
                    screen()
                    GlassBottomMenu(tab, {}, androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.BottomCenter))
                }
            }
        }
    }

    /** Bottom menu variants for the owner: current, shorter, and shorter with bolder fonts. */
    @Config(qualifiers = "w393dp-h1000dp-xxhdpi")
    @Test fun menuVariants() {
        fun font(name: String): androidx.compose.ui.text.font.FontFamily {
            val f = java.io.File.createTempFile(name, ".ttf")
            javaClass.getResourceAsStream("/menufonts/$name.ttf")!!.use { i -> f.outputStream().use { i.copyTo(it) } }
            return androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Font(f, androidx.compose.ui.text.font.FontWeight.Bold))
        }
        val variants = listOf<Pair<String, androidx.compose.ui.text.font.FontFamily?>>(
            "0 · indiki" to null,
            "A · Roboto qalın" to androidx.compose.ui.text.font.FontFamily.Default,
            "B · Nunito" to font("nunito_800"),
            "C · Montserrat" to font("montserrat_700"),
            "D · Manrope" to font("manrope_800"),
        )
        for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            shoot("menu-variants-${mode.name.lowercase()}", mode, Skin.SUSE) {
                androidx.compose.foundation.layout.Box {
                    GlassBackdrop(mode == ThemeMode.DARK, androidx.compose.ui.Modifier.matchParentSize())
                    androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(top = 12.dp)) {
                        variants.forEachIndexed { i, (name, f) ->
                            androidx.compose.material3.Text(name, androidx.compose.ui.Modifier.padding(start = 24.dp), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                                GlassBottomMenu(Screen.HOME, {}, compact = i > 0, font = f)
                            }
                        }
                    }
                }
            }
        }
    }

    @Test fun glassTheme() {
        val vm = vm()
        vm.unlockAllSkinsForTesting(); vm.changeSkin(Skin.SUSE)
        vm.open(Screen.HOME)
        for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            val suffix = mode.name.lowercase()
            shoot("glass-home-$suffix", mode, Skin.SUSE) {
                androidx.compose.foundation.layout.Box {
                    GlassBackdrop(mode == ThemeMode.DARK, androidx.compose.ui.Modifier.matchParentSize())
                    ExpressiveHome(vm)
                    GlassBottomMenu(Screen.HOME, {}, androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.BottomCenter))
                }
            }
        }
        vm.startLevel(0); vm.restartLevel()
        vm.finishTutorial()
        vm.pickLetter(vm.columnLetters().indexOfFirst { it != null })
        for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
            shoot("glass-puzzle-${mode.name.lowercase()}", mode, Skin.SUSE) {
                androidx.compose.foundation.layout.Box {
                    GlassBackdrop(mode == ThemeMode.DARK, androidx.compose.ui.Modifier.matchParentSize())
                    PuzzleScreen(vm, isPremium = false)
                }
            }
        }
        // Big clue text and more letters (a player's settings): the grid must keep its full width.
        vm.changeBigClues(true); vm.changeExtraLetters(true)
        shoot("glass-puzzle-big", ThemeMode.LIGHT, Skin.SUSE) {
            androidx.compose.foundation.layout.Box {
                GlassBackdrop(false, androidx.compose.ui.Modifier.matchParentSize())
                PuzzleScreen(vm, isPremium = false)
            }
        }
    }

    @Test fun solvedLevel() {
        val vm = vm()
        vm.startLevel(0); vm.restartLevel(); vm.finishTutorial()
        for (e in vm.game!!.board.level.entries) {
            vm.selectEntry(e)
            val g = vm.game!!
            e.cells.filter { g.typed[it] != g.board.solution[it] }.forEach { vm.type(g.board.solution[it]!!) }
        }
        shoot("solved-dialog", ThemeMode.LIGHT) { PuzzleScreen(vm, isPremium = false) }
        vm.dismissCompletion()
        shoot("solved-screen", ThemeMode.LIGHT) { PuzzleScreen(vm, isPremium = false) }
        vm.unlockAllSkinsForTesting(); vm.changeSkin(Skin.SUSE)
        shoot("solved-screen-glass", ThemeMode.LIGHT, Skin.SUSE) {
            androidx.compose.foundation.layout.Box {
                GlassBackdrop(false, androidx.compose.ui.Modifier.matchParentSize())
                PuzzleScreen(vm, isPremium = false)
            }
        }
    }

    @Test fun holidayHomes() {
        val vm = vm()
        vm.open(Screen.HOME)
        for ((name, day) in listOf(
            "yeniil" to java.time.LocalDate.of(2026, 12, 28),
            "novruz" to java.time.LocalDate.of(2027, 3, 18),
            "ramazan" to java.time.LocalDate.of(2027, 2, 20),
            "qurban" to java.time.LocalDate.of(2027, 5, 17),
            "bayram" to java.time.LocalDate.of(2027, 5, 28),
            "zefer" to java.time.LocalDate.of(2026, 11, 8),
        )) {
            vm.today = { day }
            shoot("home-holiday-$name", ThemeMode.LIGHT) { androidx.compose.foundation.layout.Box { ExpressiveHome(vm) } }
        }
        vm.today = { java.time.LocalDate.of(2027, 2, 20) }
        shoot("home-holiday-ramazan-dark", ThemeMode.DARK) { androidx.compose.foundation.layout.Box { ExpressiveHome(vm) } }
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
        shoot("home-light", ThemeMode.LIGHT, screen = home)
        shoot("home-dark", ThemeMode.DARK, screen = home)
    }

    @Test fun settingsLightAndDark() {
        val vm = vm()
        shoot("settings-light", ThemeMode.LIGHT) { SettingsScreen(vm, isPremium = false) }
        shoot("settings-premium", ThemeMode.LIGHT) { SettingsScreen(vm, isPremium = true) }
        shoot("settings-dark", ThemeMode.DARK) { SettingsScreen(vm, isPremium = false) }
    }
}
