package com.aaideas.krossvord.ui

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
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

    @Test fun levelResult() {
        val medal = com.aaideas.krossvord.model.Medals.all(emptyList()).first()
        rule.mainClock.autoAdvance = false
        rule.setContent {
            KrossvordTheme {
                Surface(Modifier.fillMaxSize()) {
                    LevelResultDialog("Səviyyə 3", LevelResult(rankUp = com.aaideas.krossvord.model.Ranks.ALL[1], xp = 25, stars = 3, seconds = 154, praise = "Afərin!", keyword = "QALA", medals = listOf(medal)), {}, {})
                }
            }
        }
        rule.mainClock.advanceTimeBy(500)
        com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/roborazzi/result-anim.png")
        rule.mainClock.advanceTimeBy(2000)
        com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/roborazzi/result.png")
    }

    @Test fun holidayPackOnlyAroundNewYear() {
        val vm = GameViewModel(ApplicationProvider.getApplicationContext<Application>())
        val p = vm.packs.indexOfFirst { it.packId == "az-yeniil" }
        assert(p >= 0)
        vm.today = { java.time.LocalDate.of(2026, 11, 22) }
        assert(!vm.packOpen(p)) { "closed in November" }
        assert(vm.holiday == null)
        vm.today = { java.time.LocalDate.of(2026, 12, 28) }
        assert(vm.packOpen(p)) { "open before New Year" }
        vm.today = { java.time.LocalDate.of(2026, 12, 30) }
        assert(vm.holiday?.id == "yeni_il")
        vm.today = { java.time.LocalDate.of(2027, 1, 1) }
        rule.setContent { KrossvordTheme { Surface(Modifier.fillMaxSize()) { androidx.compose.foundation.layout.Column(Modifier.padding(16.dp)) { HolidayCard(vm.holiday!!, vm) } } } }
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/holiday-card.png")
    }

    @Test fun welcomeAndBirthday() {
        val vm = GameViewModel(ApplicationProvider.getApplicationContext<Application>())
        vm.today = { java.time.LocalDate.of(2027, 3, 21) }
        vm.onAppOpen()
        assert(vm.welcome == GameViewModel.Welcome.PROFILE)
        // A text field inside a dialog never idles under Robolectric, so the cards are shot without the dialog.
        rule.mainClock.autoAdvance = false
        rule.setContent {
            KrossvordTheme {
                Surface(Modifier.fillMaxSize(), color = androidx.compose.ui.graphics.Color(0xFF555555)) {
                    androidx.compose.foundation.layout.Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                        vm.welcome?.let { WelcomeContent(vm, it) }
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(1000)
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/welcome-profile.png")
        val before = vm.lives
        rule.runOnUiThread { vm.saveProfile("Ana", java.time.MonthDay.of(3, 21), 1960) }   // entered on the birthday itself
        assert(vm.welcome == GameViewModel.Welcome.BIRTHDAY)
        assert(vm.lives == before + 1)
        rule.mainClock.advanceTimeBy(1000)
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/welcome-birthday.png")
        rule.runOnUiThread { vm.dismissWelcome(); vm.onAppOpen() }   // same day again: no second gift, no second greeting
        assert(vm.welcome == null && vm.lives == before + 1)
        rule.runOnUiThread { vm.today = { java.time.LocalDate.of(2027, 3, 22) }; vm.onAppOpen() }
        assert(vm.welcome == GameViewModel.Welcome.DAILY)
        rule.mainClock.advanceTimeBy(1000)
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/welcome-daily.png")
    }

    @Test fun dailyScreen() {
        val vm = GameViewModel(ApplicationProvider.getApplicationContext<Application>())
        vm.today = { java.time.LocalDate.of(2026, 11, 22) }
        // Played three days before today too, and the proverb answered right.
        repeat(3) { i -> vm.today = { java.time.LocalDate.of(2026, 11, 19 + i) }; vm.pickProverb(vm.proverbOptions.first()) }
        vm.today = { java.time.LocalDate.of(2026, 11, 22) }
        val xp0 = vm.xp
        vm.pickProverb(vm.proverb.answer)
        assert(vm.xp > xp0)
        assert(vm.streakRun == 4) { "run ${vm.streakRun}" }
        rule.setContent { KrossvordTheme { Surface(Modifier.fillMaxSize()) { DailyScreen(vm) } } }
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/daily.png")
    }

    @Test fun kidsModeStartsWithEasiestClueAndKeepsLives() {
        val vm = GameViewModel(ApplicationProvider.getApplicationContext<Application>())
        vm.changeKidsMode(true)
        vm.startLevel(0)
        val g = vm.game!!
        val e = g.board.level.entries.first()
        assert(g.clueTier(e) == e.clueLevels.lastIndex)
        assert(!vm.outOfLives)
        vm.changeKidsMode(false)
        vm.restartLevel()
        assert(vm.game!!.clueTier(e) == 0)
    }

    @Test fun tutorialShots() {
        val vm = GameViewModel(ApplicationProvider.getApplicationContext<Application>())
        vm.startLevel(0)
        vm.restartLevel()
        vm.replayTutorial()
        rule.setContent { KrossvordTheme { PuzzleScreen(vm, isPremium = false) } }
        rule.waitForIdle()
        com.github.takahirom.roborazzi.captureScreenRoboImage("build/outputs/roborazzi/tutorial-ask.png")
        rule.runOnUiThread { vm.startCoach() }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(500)
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/tutorial-1.png")
        rule.runOnUiThread { vm.nextCoach(); vm.nextCoach() }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/tutorial-3.png")
        rule.runOnUiThread { vm.finishTutorial() }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/puzzle-top.png")
    }

    @Test fun dailyMiniPlaysAndCountsTheTask() {
        val vm = GameViewModel(ApplicationProvider.getApplicationContext<Application>())
        vm.finishTutorial()
        val mini = vm.miniToday!!
        vm.startMini()
        assert(vm.playingMini && vm.screen == Screen.PUZZLE)
        vm.setInput(InputMode.KEYBOARD)
        mini.entries.forEach { e ->
            vm.selectEntry(e)
            e.cells.forEachIndexed { k, c -> if (vm.game!!.typed[c] == null) { vm.tapCell(c); vm.type(e.letters[k]) } }
        }
        assert(vm.miniSolvedToday)
        vm.nextLevel()
        assert(vm.screen == Screen.DAILY && !vm.playingMini)
        assert(mini.id !in vm.completed)
    }

    @Test fun collectionScreen() {
        val vm = GameViewModel(ApplicationProvider.getApplicationContext<Application>())
        vm.restartLevel()
        vm.setInput(InputMode.KEYBOARD)
        repeat(3) { val e = vm.game!!.activeEntry!!; e.answer.forEach { vm.type(it) } }
        rule.setContent { KrossvordTheme { Surface(Modifier.fillMaxSize()) { CollectionScreen(vm) } } }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/collection.png")
        org.junit.Assert.assertTrue(vm.collection.size >= 3)
    }
}
