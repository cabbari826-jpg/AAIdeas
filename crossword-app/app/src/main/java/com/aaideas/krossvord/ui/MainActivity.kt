package com.aaideas.krossvord.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.aaideas.krossvord.update.UpdateBanner

class MainActivity : ComponentActivity() {
    private val vm: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KrossvordTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val isPremium by vm.premium.isPremium.collectAsState()
                    BackHandler(enabled = vm.screen != Screen.HOME) { vm.back() }
                    Box(Modifier.safeDrawingPadding()) {
                        when (vm.screen) {
                            Screen.HOME -> HomeScreen(vm)
                            Screen.LEVELS -> LevelsScreen(vm)
                            Screen.PUZZLE -> PuzzleScreen(vm, isPremium)
                            Screen.SETTINGS -> SettingsScreen(vm, isPremium)
                            Screen.WORD_OF_DAY -> WordOfDayScreen(vm.daily, isPremium, vm::back)
                        }
                    }
                    AdPlaceholderHost(vm.ads)
                    UpdateBanner() // prototype only; empty in the release build
                }
            }
        }
    }
}
