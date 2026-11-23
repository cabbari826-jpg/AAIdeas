package com.aaideas.krossvord.ui

import android.os.Bundle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dev.chrisbanes.haze.hazeSource
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
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

    /**
     * Back in front (also after a night in the background, when the process was kept): pick up
     * the new day's word, tasks and lives, and set the reminder again.
     */
    override fun onStart() {
        super.onStart()
        if (ready) vm.onResumeDay()
        if (com.aaideas.krossvord.remind.DailyReminder.isOn(this)) com.aaideas.krossvord.remind.DailyReminder.schedule(this)
    }

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
        // Branded launch screen; it stays up until the level packs are read, so no blank frame in between.
        installSplashScreen().setKeepOnScreenCondition { !ready }
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
            LaunchedEffect(Unit) { pendingIntent?.let { openFrom(it) }; pendingIntent = null; vm.onAppOpen() }
            val mode = vm.themeMode
            val isPremiumNow by vm.premium.isPremium.collectAsState()
            val skin = vm.activeSkin(isPremiumNow)
            // App icon: holiday icon in season (if turned on), premium icon only while premium lasts.
            LaunchedEffect(isPremiumNow) { AppIcon.sync(this@MainActivity, isPremiumNow) }
            val dark = skin.alwaysDark || mode == ThemeMode.DARK || (mode == ThemeMode.SYSTEM && isSystemInDarkTheme())
            // Status/nav bar icons must flip with the in-app choice, not only with the phone setting.
            LaunchedEffect(dark) {
                val bar = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bar, navigationBarStyle = bar)
            }
            // Large mode: all text at least 1.3x, whatever the phone's own font setting.
            val base = androidx.compose.ui.platform.LocalDensity.current
            // Follows the phone's own big font until the player sets the switch.
            val systemScale = androidx.compose.ui.platform.LocalConfiguration.current.fontScale
            val scale = vm.appFontScale(systemScale)
            val density = if (scale != base.fontScale) androidx.compose.ui.unit.Density(base.density, scale) else base
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides density) {
            LaunchedEffect(vm.screen) { if (vm.screen == Screen.HOME || vm.screen == Screen.SETTINGS) vm.refreshSkins(com.aaideas.krossvord.model.Ranks.unlockedThemes(vm.xp).toSet()) }
            KrossvordTheme(mode, vm.dynamicColor, skin) {
                // Şüşə: the colourful backdrop shows through, so the window surface is clear.
                Surface(Modifier.fillMaxSize(), color = if (skin.glass) androidx.compose.ui.graphics.Color.Transparent else MaterialTheme.colorScheme.background,
                    // A clear surface would leave text black on the dark glass; say the colour outright.
                    contentColor = MaterialTheme.colorScheme.onBackground) {
                    val glassLite = skin.glass && GlassLite.isOn(this@MainActivity)
                    val haze = if (skin.glass && !glassLite) androidx.compose.runtime.remember { dev.chrisbanes.haze.HazeState() } else null
                    val glassMax = haze != null && GlassMax.isOn(this@MainActivity)
                    // Android 13+: real glass lens (LiquidGlass.kt); Android 12: frosted cards (Haze).
                    val liquid = glassMax && LiquidGlass.supported
                    val cardHaze = if (glassMax && !liquid) androidx.compose.runtime.remember { dev.chrisbanes.haze.HazeState() } else null
                    // The moving light's clock: the backdrop and every glass lens read the same time.
                    val glassClock = if (liquid) rememberGlassClock() else null
                    val backdrop = glassClock?.let { LensLight(it, dark) }
                    val screenLayer = if (liquid) androidx.compose.ui.graphics.rememberGraphicsLayer() else null
                    val isPremium by vm.premium.isPremium.collectAsState()
                    BackHandler(enabled = vm.screen != Screen.HOME) { vm.back() }
                    val menu = vm.screen != Screen.PUZZLE && vm.screen != Screen.WORD_OF_DAY
                    androidx.compose.runtime.CompositionLocalProvider(LocalHaze provides haze, LocalGlassLite provides glassLite, LocalCardHaze provides cardHaze, LocalGlassBackdrop provides backdrop, LocalGlassScreen provides screenLayer) {
                    MaxGlassColors(glassMax, dark, liquid) {
                    // The backdrop and screens are the blur source; the floating menu (outside it) frosts them.
                    Box(Modifier.fillMaxSize().then(if (haze != null && !liquid) Modifier.hazeSource(haze) else Modifier).recordInto(screenLayer)) {
                        if (skin.glass) GlassBackdrop(dark, Modifier.fillMaxSize().then(if (cardHaze != null) Modifier.hazeSource(cardHaze) else Modifier), moving = glassMax, clock = glassClock)
                    // Screens with the menu run on under it and under the gesture bar: a hard edge behind the pill
                    // looked like a ledge. Their lists end with BottomMenuHeight of space so nothing stays hidden.
                    Box(if (menu) Modifier.windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.safeDrawing.only(
                        androidx.compose.foundation.layout.WindowInsetsSides.Top + androidx.compose.foundation.layout.WindowInsetsSides.Horizontal))
                        else Modifier.safeDrawingPadding()) {
                        Box {
                            when (vm.screen) {
                                Screen.HOME -> ExpressiveHome(vm)
                                Screen.LEVELS -> LevelsScreen(vm)
                                Screen.PUZZLE -> PuzzleScreen(vm, isPremium)
                                Screen.SETTINGS -> SettingsScreen(vm, isPremium)
                                Screen.WORD_OF_DAY -> WordOfDayScreen(vm.daily, isPremium, vm::back)
                                Screen.STATS -> StatsScreen(vm)
                                Screen.COLLECTION -> CollectionScreen(vm)
                                Screen.DAILY -> DailyScreen(vm)
                                Screen.PACKS -> PacksScreen(vm)
                                Screen.MAP -> JourneyScreen(vm)
                            }
                        }
                    }
                    }
                    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                        if (menu) GlassBottomMenu(vm.screen, vm::open, Modifier.align(Alignment.BottomCenter))
                    }
                    }
                    }
                    WelcomeHost(vm)
                    RestDialog(vm)
                    AdPlaceholderHost(vm.ads)
                    UpdateBanner() // prototype only; empty in the release build
                }
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
