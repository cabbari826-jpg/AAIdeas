package com.aaideas.launcher

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.lifecycleScope
import com.aaideas.launcher.data.AppEntry
import com.aaideas.launcher.data.AppRepository
import com.aaideas.launcher.data.HomeLayout
import com.aaideas.launcher.data.Prefs
import com.aaideas.launcher.data.Wallpaper
import com.aaideas.launcher.data.WallpaperKind
import com.aaideas.launcher.data.Weather
import com.aaideas.launcher.data.WeatherNow
import com.aaideas.launcher.ui.AppMenuOverlay
import com.aaideas.launcher.ui.Backdrop
import com.aaideas.launcher.ui.GlassStyle
import com.aaideas.launcher.ui.HomeScreen
import com.aaideas.launcher.ui.HomeUi
import com.aaideas.launcher.ui.Ink
import com.aaideas.launcher.ui.LocalBackdrop
import com.aaideas.launcher.ui.LocalGlass
import com.aaideas.launcher.ui.LocalInk
import com.aaideas.launcher.ui.SearchOverlay
import com.aaideas.launcher.ui.SettingsOverlay
import com.aaideas.launcher.ui.SettingsUi
import com.aaideas.launcher.update.UpdateBanner
import com.aaideas.launcher.update.UpdateSettingsRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface Overlay {
    data object None : Overlay
    data object Search : Overlay
    data object Settings : Overlay
    data class Menu(val app: AppEntry, val anchor: Rect, val shortcuts: List<ShortcutInfo>) : Overlay
}

class MainActivity : ComponentActivity() {
    private lateinit var prefs: Prefs
    private lateinit var repo: AppRepository
    /** "Home" pressed while the launcher is already showing. */
    private val homePressed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        repo = AppRepository(applicationContext, lifecycleScope)
        repo.iconSizePx = (60 * 1.2f * resources.displayMetrics.density).toInt()
        repo.start()
        setContent { LauncherRoot() }
    }

    override fun onDestroy() {
        repo.stop()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homePressed.tryEmit(Unit)
    }

    private fun isDefaultHome(): Boolean {
        val ri = packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
        return ri?.activityInfo?.packageName == packageName
    }

    private fun requestDefaultHome() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = getSystemService(RoleManager::class.java)
            if (rm.isRoleAvailable(RoleManager.ROLE_HOME)) rm.createRequestRoleIntent(RoleManager.ROLE_HOME) else Intent(Settings.ACTION_HOME_SETTINGS)
        } else Intent(Settings.ACTION_HOME_SETTINGS)
        runCatching { startActivityForResult(intent, 1) }
            .onFailure { runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) } }
    }

    private fun safeStart(intent: Intent) {
        runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    @Composable
    private fun LauncherRoot() {
        val context = LocalContext.current
        val density = LocalDensity.current
        val scope = rememberCoroutineScope()
        var window by remember { mutableStateOf(IntSize.Zero) }
        var wallpaper by remember { mutableStateOf<ImageBitmap?>(null) }
        var blurred by remember { mutableStateOf<ImageBitmap?>(null) }
        var previews by remember { mutableStateOf<Map<WallpaperKind, ImageBitmap>>(emptyMap()) }
        var photoVersion by remember { mutableIntStateOf(0) }
        var overlay by remember { mutableStateOf<Overlay>(Overlay.None) }
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        var weather by remember { mutableStateOf<WeatherNow?>(null) }
        var hasLocation by remember { mutableStateOf(hasLocationPermission()) }
        var isDefault by remember { mutableStateOf(isDefaultHome()) }

        // Wallpaper and its blurred copy: made off the main thread whenever the
        // wallpaper, the blur setting or the screen size changes.
        LaunchedEffect(window, prefs.wallpaper, photoVersion) {
            if (window.width == 0) return@LaunchedEffect
            wallpaper = withContext(Dispatchers.Default) {
                Wallpaper.render(context, prefs.wallpaper, window.width, window.height).asImageBitmap()
            }
        }
        LaunchedEffect(wallpaper, prefs.blur) {
            val w = wallpaper ?: return@LaunchedEffect
            blurred = withContext(Dispatchers.Default) {
                Wallpaper.blurred(w.asAndroidBitmap(), prefs.blur, density.density).asImageBitmap()
            }
        }
        LaunchedEffect(photoVersion) {
            previews = withContext(Dispatchers.Default) {
                buildMap {
                    for (k in listOf(WallpaperKind.SEA, WallpaperKind.SAND, WallpaperKind.NIGHT)) put(k, Wallpaper.render(context, k, 124, 220).asImageBitmap())
                    if (Wallpaper.hasPhoto(context)) put(WallpaperKind.PHOTO, Wallpaper.render(context, WallpaperKind.PHOTO, 124, 220).asImageBitmap())
                }
            }
        }
        // Clock: tick at the start of every minute.
        LaunchedEffect(Unit) {
            while (true) {
                now = System.currentTimeMillis()
                delay(60_000 - now % 60_000 + 50)
            }
        }
        // Weather: on every return to the home screen, at most every 30 minutes.
        var weatherAt by remember { mutableLongStateOf(0L) }
        LifecycleResumeEffect(hasLocation) {
            isDefault = isDefaultHome()
            now = System.currentTimeMillis()
            if (hasLocation && System.currentTimeMillis() - weatherAt > 30 * 60_000) {
                scope.launch { Weather.load(context)?.let { weather = it; weatherAt = System.currentTimeMillis() } }
            }
            onPauseOrDispose { }
        }
        val locationAsk = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            hasLocation = ok
        }
        val photoPick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
            if (uri != null && window.width > 0) scope.launch {
                val ok = withContext(Dispatchers.IO) { Wallpaper.savePhoto(context, uri, window.width, window.height) }
                if (ok) { prefs.changeWallpaper(WallpaperKind.PHOTO); photoVersion++ }
            }
        }

        val pager = rememberPagerState { 1 + (repo.apps.size + 19) / 20 }
        LaunchedEffect(Unit) {
            homePressed.collect {
                if (overlay != Overlay.None) overlay = Overlay.None else pager.animateScrollToPage(0)
            }
        }
        BackHandler(enabled = true) {
            if (overlay != Overlay.None) overlay = Overlay.None
            else scope.launch { pager.animateScrollToPage(0) }
        }

        val layout = remember(repo.apps, repo.dockPackages, repo.favouritePackages, prefs.hidden) {
            HomeLayout.arrange(repo.apps, repo.dockPackages, repo.favouritePackages, prefs.hidden)
        }
        val ink = if (prefs.darkInk) Ink(Color(0xFF1B1F24), Color(0x59FFFFFF)) else Ink(Color.White, Color(0x73000000))
        LaunchedEffect(prefs.darkInk) {
            WindowCompat.getInsetsController(this@MainActivity.window, this@MainActivity.window.decorView).isAppearanceLightStatusBars = prefs.darkInk
        }
        val iconSize = (60 * prefs.iconScale / 100f).dp

        fun open(app: AppEntry, r: Rect) {
            overlay = Overlay.None
            repo.launch(app, android.graphics.Rect(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt()))
        }

        Box(Modifier.fillMaxSize().onSizeChanged { window = it }) {
            wallpaper?.let { Image(it, null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize()) }
            CompositionLocalProvider(
                LocalBackdrop provides Backdrop(blurred, window),
                LocalGlass provides GlassStyle(prefs.whiteness / 100f, prefs.tintStrength / 100f),
                LocalInk provides ink,
            ) {
                val blurHome = overlay != Overlay.None && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                HomeScreen(
                    HomeUi(
                        layout = layout,
                        icons = repo.icons,
                        colors = prefs.colors,
                        iconSize = iconSize,
                        labels = prefs.labels,
                        now = now,
                        weather = weather,
                        weatherNeedsPermission = !hasLocation,
                        showMakeDefault = !isDefault && !prefs.homePrompted,
                        onOpen = ::open,
                        onMenu = { app, r -> overlay = Overlay.Menu(app, r, repo.shortcuts(app)) },
                        onSearch = { overlay = Overlay.Search },
                        onSettings = { overlay = Overlay.Settings },
                        onClock = { safeStart(Intent(AlarmClock.ACTION_SHOW_ALARMS)) },
                        onCalendar = { safeStart(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)) },
                        onWeather = { if (!hasLocation) locationAsk.launch(Manifest.permission.ACCESS_COARSE_LOCATION) },
                        onMakeDefault = { prefs.changeHomePrompted(); requestDefaultHome() },
                        onDismissMakeDefault = { prefs.changeHomePrompted() },
                    ),
                    pager = pager,
                    modifier = if (blurHome) Modifier.graphicsLayer { renderEffect = BlurEffect(36f, 36f, TileMode.Clamp) } else Modifier,
                )
                when (val o = overlay) {
                    Overlay.None -> Unit
                    Overlay.Search -> SearchOverlay(
                        apps = layout.dock + layout.favourites + layout.rest,
                        icons = repo.icons, colors = prefs.colors, iconSize = iconSize,
                        onOpen = ::open,
                        onMenu = { app, r -> overlay = Overlay.Menu(app, r, repo.shortcuts(app)) },
                        onWebSearch = { q ->
                            overlay = Overlay.None
                            safeStart(Intent(Intent.ACTION_WEB_SEARCH).putExtra(android.app.SearchManager.QUERY, q))
                        },
                        onDismiss = { overlay = Overlay.None },
                    )
                    Overlay.Settings -> SettingsOverlay(
                        SettingsUi(
                            prefs = prefs,
                            previews = previews,
                            hiddenApps = repo.apps.filter { it.key in prefs.hidden },
                            isDefault = isDefault,
                            onPickPhoto = { photoPick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            onMakeDefault = { requestDefaultHome() },
                            onBack = { overlay = Overlay.None },
                            extra = { UpdateSettingsRow() },
                        ),
                    )
                    is Overlay.Menu -> AppMenuOverlay(
                        app = o.app, anchor = o.anchor, icon = repo.icons[o.app.key], customColor = prefs.colors[o.app.key],
                        iconSize = iconSize, shortcuts = o.shortcuts,
                        onShortcut = { overlay = Overlay.None; repo.startShortcut(it) },
                        onColor = { prefs.setColor(o.app.key, it); overlay = Overlay.None },
                        onDetails = { overlay = Overlay.None; repo.openDetails(o.app) },
                        onHide = { prefs.hide(o.app.key); overlay = Overlay.None },
                        onUninstall = { overlay = Overlay.None; repo.uninstall(o.app) },
                        onDismiss = { overlay = Overlay.None },
                    )
                }
                UpdateBanner()
            }
        }
    }

    private fun hasLocationPermission() =
        checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
}

