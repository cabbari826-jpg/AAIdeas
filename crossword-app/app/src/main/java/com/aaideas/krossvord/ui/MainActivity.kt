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
import androidx.compose.foundation.background
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.aaideas.krossvord.update.UpdateBanner

class MainActivity : ComponentActivity() {
    private val vm: GameViewModel by viewModels()

    /** Set until the packs are read; the screens (and the view model) come after. */
    private var ready by androidx.compose.runtime.mutableStateOf(com.aaideas.krossvord.data.PackCache.isLoaded)
    private var pendingIntent: android.content.Intent? = null

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        if (ready) openFrom(intent) else pendingIntent = intent
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
        pendingIntent = intent
        if (!ready) {
            // Read the level packs off the main thread; a plain splash shows meanwhile.
            lifecycleScope.launch(kotlinx.coroutines.Dispatchers.Default) {
                com.aaideas.krossvord.data.PackCache.get(applicationContext)
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { ready = true }
            }
        }
        if (com.aaideas.krossvord.remind.DailyReminder.isOn(this)) com.aaideas.krossvord.remind.DailyReminder.schedule(this)
        setContent {
            if (!ready) { Splash(); return@setContent }
            LaunchedEffect(Unit) { pendingIntent?.let { openFrom(it) }; pendingIntent = null }
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
                                Screen.COLLECTION -> CollectionScreen(vm)
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

/** Shown for the moment the packs are being read: plain, so it draws at once on any phone. */
@androidx.compose.runtime.Composable
private fun Splash() {
    val dark = isSystemInDarkTheme()
    Box(
        Modifier.fillMaxSize().background(if (dark) androidx.compose.ui.graphics.Color(0xFF141218) else androidx.compose.ui.graphics.Color(0xFFFEF7FF)),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.text.BasicText(
            "Açar Söz",
            style = androidx.compose.ui.text.TextStyle(
                fontSize = androidx.compose.ui.unit.TextUnit(32f, androidx.compose.ui.unit.TextUnitType.Sp),
                color = if (dark) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color(0xFF1D1B20),
            ),
        )
    }
}
