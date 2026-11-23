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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Update 2.1: downloadable packs, weekly surprise, my words, stuck hint, backup file, auto large mode. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h1100dp-xhdpi")
class UpdateTwoOneTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private fun vm() = GameViewModel(app).also { it.today = { java.time.LocalDate.of(2026, 11, 24) } }

    private fun shoot(name: String, content: @androidx.compose.runtime.Composable () -> Unit) =
        captureRoboImage("build/outputs/roborazzi/$name.png") { KrossvordTheme { Surface(Modifier.fillMaxSize()) { content() } } }

    @Test fun homeShowsWeeklySurpriseAndMorePacks() {
        val vm = vm()
        assertTrue(vm.weeklyPack != null)
        shoot("v21-home") { ExpressiveHome(vm) }
    }

    @Test fun weeklyPackOfNextWeekIsClosed() {
        val vm = vm()
        val p = vm.packs.indexOfFirst { it.packId == "az-hefte" }
        assertTrue(vm.packOpen(p))
        vm.today = { java.time.LocalDate.of(2026, 11, 20) }
        assertFalse(vm.packOpen(p))
    }

    @Test fun packsScreenAndDownloadedPackPlays() {
        val dl = DownloadedPacks(app)
        val text = java.io.File("../content/downloadable/az-starter-plus.json").readText()
        // The list as the release has it, kept from the last check.
        app.getSharedPreferences("packs_dl", 0).edit().putString(
            "index",
            """{"formatVersion":1,"packs":[{"packId":"az-starter-plus","title":"Başlanğıc +","levels":10,"contentVersion":1,"file":"az-starter-plus.json","bytes":29580,"after":"az-starter"},
               {"packId":"hefte-2","title":"Həftənin sürprizi 2","levels":3,"contentVersion":1,"file":"hefte-2.json","bytes":9100}]}""",
        ).commit()
        val before = vm()
        shoot("v21-packs") { PacksScreen(before) }
        try {
            dl.save("az-starter-plus", text)
            val vm = vm()
            vm.reloadPacks(PackCache.reload(app))
            assertEquals("az-starter-plus", vm.packs[1].packId)
            assertEquals(1, vm.haveVersion("az-starter-plus"))
            vm.startLevel(vm.packLevels(1).first().first)
            assertTrue(vm.game != null)
            shoot("v21-packs-downloaded") { PacksScreen(vm) }
        } finally {
            dl.remove("az-starter-plus")
            PackCache.reload(app)
        }
    }

    @Test fun wrongWordGoesToMyWords() {
        val vm = vm()
        vm.startLevel(0)
        vm.restartLevel()
        val letters = vm.columnLetters()
        vm.pickLetter(letters.indexOf('D'))
        vm.pickLetter(letters.indexOf('O'))
        assertTrue("OD" in vm.mistakeWords)
        vm.openMistakes = true
        shoot("v21-my-words") { CollectionScreen(vm) }
        vm.markLearned("OD")
        assertFalse("OD" in vm.mistakeWords)
    }

    @Test fun backupFileRoundTrip() {
        val vm = vm()
        vm.startLevel(0)
        vm.changeVibrate(false)
        app.getSharedPreferences("progress", 0).edit().putInt("xp", 345).putStringSet("completed", setOf("a", "b")).commit()
        val file = ProgressBackup.export(app)
        app.getSharedPreferences("progress", 0).edit().clear().commit()
        assertFalse(ProgressBackup.import(app, "{\"app\":\"other\"}"))
        assertTrue(ProgressBackup.import(app, file))
        val p = app.getSharedPreferences("progress", 0)
        assertEquals(345, p.getInt("xp", 0))
        assertEquals(setOf("a", "b"), p.getStringSet("completed", null))
        assertFalse(p.getBoolean("vibrate_on", true))
    }

    @Test fun textSizeFollowsPhoneFontUntilChosen() {
        val vm = vm()
        assertTrue(vm.largeOn(1.2f))
        assertEquals(1.3f, vm.appFontScale(1.2f), 0.001f)
        assertFalse(vm.largeOn(1.0f))
        vm.changeTextScale(1f)
        assertFalse(vm.largeOn(1.2f))          // "Adi": the phone's own size, no more
        vm.changeTextScale(1.5f)
        assertEquals(1.5f, vm.appFontScale(1.0f), 0.001f)
        assertEquals(1.5f, vm.shownTextScale(1.0f), 0.001f)
    }

    @Test fun roadMapOpensAPlaceEveryEightLevels() {
        val m = com.aaideas.krossvord.model.Journey
        assertEquals(1, m.opened(0)); assertEquals(8, m.toNext(0))
        assertEquals(2, m.opened(8)); assertEquals(7, m.toNext(9))
        assertEquals(m.CITIES.size, m.opened(10_000)); assertEquals(null, m.toNext(10_000))
        val vm = vm()
        val done = vm.levels.take(17).map { it.id }.toSet()
        app.getSharedPreferences("progress", 0).edit().putStringSet("completed", done).commit()
        val vm2 = vm()
        shoot("v21-map") { JourneyScreen(vm2) }
    }

    @Test fun monthTripMovesWithLevelsAndRewardsTheEnd() {
        val c = com.aaideas.krossvord.model.Challenge
        val west = c.ROUTES[0]
        assertEquals("Bakı", c.reached(west, 0).name)
        assertEquals("Qazax", c.reached(west, c.GOAL).name)
        assertEquals(0, c.toNextStop(west, c.GOAL))
        assertTrue(c.toNextStop(west, 0) in 1..3)
        app.getSharedPreferences("progress", 0).edit().putInt("month_key", 202611).putInt("month_levels", 9).commit()
        val vm = vm()
        assertEquals(9, vm.monthLevels)
        val hints = vm.freeHints
        app.getSharedPreferences("progress", 0).edit().putInt("month_levels", c.GOAL - 1).commit()
        val vm2 = vm()
        vm2.startLevel(0); vm2.restartLevel()
        // Solve level 1 by revealing every letter (premium on, so hints are free).
        vm2.premium.purchase()
        repeat(200) { if (vm2.game?.isComplete != true) vm2.hint() }
        assertEquals(c.GOAL, vm2.monthLevels)
        assertTrue(vm2.result?.trip.orEmpty().startsWith("🏁"))
        assertEquals(hints + c.REWARD_HINTS, vm2.freeHints)
    }

    @Test fun mapScreenWithMonthTrip() {
        app.getSharedPreferences("progress", 0).edit().putInt("month_key", 202611).putInt("month_levels", 9).commit()
        val vm = vm()
        shoot("v21-map-month") { JourneyScreen(vm) }
    }

    @Test fun restWindowAfterAnHourOfPlay() {
        val vm = vm()
        vm.startLevel(0)
        assertFalse(vm.restDue)
        vm.addSittingForTest(REST_EVERY_SECONDS + 5)
        vm.startLevel(1)
        assertTrue(vm.restDue)
        shoot("v21-rest") { RestDialog(vm) }
        vm.closeRest(goHome = false)
        vm.startLevel(2)
        assertFalse(vm.restDue)          // next one only after another hour
    }

    @Test fun everyThirdDayInARowGivesAFreeHint() {
        val s = com.aaideas.krossvord.model.Streak
        assertTrue(s.hintDue(setOf(10L, 11L, 12L), 12L, -1))
        assertFalse(s.hintDue(setOf(10L, 11L, 12L), 12L, 12L))
        assertFalse(s.hintDue(setOf(11L, 12L), 12L, -1))
        assertTrue(s.hintDue((1L..6L).toSet(), 6L, 3L))
    }

    @Test(timeout = 60_000) fun settingsShowNewSwitches() {
        val vm = vm()
        captureRoboImage("build/outputs/roborazzi/v21-settings.png") {
            KrossvordTheme { Surface(Modifier.fillMaxSize()) { SettingsScreen(vm, isPremium = false) } }
        }
    }
}
