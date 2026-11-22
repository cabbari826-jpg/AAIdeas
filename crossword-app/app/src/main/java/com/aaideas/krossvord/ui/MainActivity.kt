package com.aaideas.krossvord.ui

import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.aaideas.krossvord.update.UpdateBanner

class MainActivity : ComponentActivity() {
    private val vm: GameViewModel by viewModels()

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        openFrom(intent)
    }

    /** The word-of-the-day reminder opens that screen directly. */
    private fun openFrom(intent: android.content.Intent?) {
        val remind = com.aaideas.krossvord.remind.DailyReminder
        if (intent?.getStringExtra(remind.EXTRA_OPEN) == remind.OPEN_WORD_OF_DAY) {
            intent.removeExtra(remind.EXTRA_OPEN)
            vm.open(Screen.WORD_OF_DAY)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openFrom(intent)
        if (com.aaideas.krossvord.remind.DailyReminder.isOn(this)) com.aaideas.krossvord.remind.DailyReminder.schedule(this)
        setContent {
            val mode = vm.themeMode
            val dark = mode == ThemeMode.DARK || (mode == ThemeMode.SYSTEM && isSystemInDarkTheme())
            // Status/nav bar icons must flip with the in-app choice, not only with the phone setting.
            LaunchedEffect(dark) {
                val bar = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bar, navigationBarStyle = bar)
            }
            KrossvordTheme(mode, vm.dynamicColor) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val isPremium by vm.premium.isPremium.collectAsState()
                    BackHandler(enabled = vm.screen != Screen.HOME) { vm.back() }
                    val menu = vm.screen != Screen.PUZZLE && vm.screen != Screen.WORD_OF_DAY
                    Box(Modifier.safeDrawingPadding()) {
                        // Leave room under the floating menu so it never hides the last item (home scrolls under it).
                        Box(if (menu && vm.screen != Screen.HOME) Modifier.padding(bottom = BottomMenuHeight) else Modifier) {
                            when (vm.screen) {
                                Screen.HOME -> ExpressiveHome(vm)
                                Screen.LEVELS -> LevelsScreen(vm)
                                Screen.PUZZLE -> PuzzleScreen(vm, isPremium)
                                Screen.SETTINGS -> SettingsScreen(vm, isPremium)
                                Screen.WORD_OF_DAY -> WordOfDayScreen(vm.daily, isPremium, vm::back)
                                Screen.STATS -> StatsScreen(vm)
                            }
                        }
                        if (menu) GlassBottomMenu(vm.screen, vm::open, Modifier.align(Alignment.BottomCenter))
                    }
                    AdPlaceholderHost(vm.ads)
                    UpdateBanner() // prototype only; empty in the release build
                }
            }
        }
    }
}
