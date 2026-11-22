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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val mode = vm.themeMode
            val dark = mode == ThemeMode.DARK || (mode == ThemeMode.SYSTEM && isSystemInDarkTheme())
            // Status/nav bar icons must flip with the in-app choice, not only with the phone setting.
            LaunchedEffect(dark) {
                val bar = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bar, navigationBarStyle = bar)
            }
            KrossvordTheme(mode) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val isPremium by vm.premium.isPremium.collectAsState()
                    BackHandler(enabled = vm.screen != Screen.HOME) { vm.back() }
                    Box(Modifier.safeDrawingPadding()) {
                        when (vm.screen) {
                            Screen.HOME -> HomeScreen(vm)
                            Screen.LEVELS -> LevelsScreen(vm)
                            Screen.PUZZLE -> PuzzleScreen(vm, isPremium)
                            Screen.SETTINGS -> SettingsScreen(vm, isPremium)
                        }
                    }
                    AdPlaceholderHost(vm.ads)
                    UpdateBanner() // prototype only; empty in the release build
                }
            }
        }
    }
}
