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
import com.aaideas.launcher.ui.HOME_PAGE
import com.aaideas.launcher.ui.WidgetMenuOverlay
import com.aaideas.launcher.ui.WidgetPage
import com.aaideas.launcher.ui.WidgetPickerOverlay
import com.aaideas.launcher.data.WidgetChoice
import com.aaideas.launcher.data.WidgetRepository
import android.appwidget.AppWidgetManager
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.viewinterop.AndroidView
import com.aaideas.launcher.ui.LiquidLens
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
    data object WidgetPicker : Overlay
    data class WidgetMenu(val id: Int) : Overlay
}

private const val REQ_BIND_WIDGET = 11
private const val REQ_CONFIGURE_WIDGET = 12

class MainActivity : ComponentActivity() {
    private lateinit var prefs: Prefs
    private lateinit var repo: AppRepository
    private lateinit var widgets: WidgetRepository
    /** Widget id waiting for the system "allow" dialog or the widget's own setup screen. */
    private var pendingWidget = -1
    /** "Home" pressed while the launcher is already showing. */
    private val homePressed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        repo = AppRepository(applicationContext, lifecycleScope)
        repo.iconSizePx = (60 * 1.2f * resources.displayMetrics.density).toInt()
        repo.start()
        widgets = WidgetRepository(this)
        setContent { LauncherRoot() }
    }

    override fun onStart() {
        super.onStart()
        widgets.startListening()
    }

    override fun onStop() {
        widgets.stopListening()
        super.onStop()
    }

    /** Adding a widget: pick → (system allows binding) → (widget's own setup) → placed. */
    private fun pickWidget(c: WidgetChoice) {
        val info = c.info ?: return
        val (id, bound) = widgets.allocate(info)
        pendingWidget = id
        if (bound) { configureOrAdd(id); return }
        val ask = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
        runCatching { @Suppress("DEPRECATION") startActivityForResult(ask, REQ_BIND_WIDGET) }
            .onFailure { widgets.discard(id); pendingWidget = -1 }
    }

    private fun configureOrAdd(id: Int) {
        val info = widgets.info(id) ?: run { widgets.discard(id); pendingWidget = -1; return }
        if (widgets.needsConfigure(info) && widgets.startConfigure(this, id, REQ_CONFIGURE_WIDGET)) return
        widgets.add(id); pendingWidget = -1
    }

    @Deprecated("Widget binding and setup still report back here")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION") super.onActivityResult(requestCode, resultCode, data)
        val id = pendingWidget.takeIf { it != -1 } ?: data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1) ?: -1
        if (id == -1) return
        when (requestCode) {
            REQ_BIND_WIDGET -> if (resultCode == RESULT_OK) configureOrAdd(id) else { widgets.discard(id); pendingWidget = -1 }
            REQ_CONFIGURE_WIDGET -> { if (resultCode == RESULT_OK) widgets.add(id) else widgets.discard(id); pendingWidget = -1 }
        }
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
        var weatherNote by remember { mutableStateOf<String?>(null) }
        var askedLocation by remember { mutableStateOf(false) }
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
                weatherAt = System.currentTimeMillis()
                scope.launch { refreshWeather(context) { w, note -> if (w != null) weather = w; weatherNote = note; if (w == null) weatherAt = 0L } }
            }
            onPauseOrDispose { }
        }
        val locationAsk = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            hasLocation = ok
            askedLocation = true
            if (ok) scope.launch { weatherNote = "Загрузка…"; refreshWeather(context) { w, note -> if (w != null) weather = w; weatherNote = note } }
        }
        val photoPick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
            if (uri != null && window.width > 0) scope.launch {
                val ok = withContext(Dispatchers.IO) { Wallpaper.savePhoto(context, uri, window.width, window.height) }
                if (ok) { prefs.changeWallpaper(WallpaperKind.PHOTO); photoVersion++ }
            }
        }

        val pager = rememberPagerState(initialPage = HOME_PAGE) { HOME_PAGE + 1 + (repo.apps.size + 19) / 20 }
        LaunchedEffect(Unit) {
            homePressed.collect {
                if (overlay != Overlay.None) overlay = Overlay.None else pager.animateScrollToPage(HOME_PAGE)
            }
        }
        BackHandler(enabled = true) {
            if (overlay != Overlay.None) overlay = Overlay.None
            else scope.launch { pager.animateScrollToPage(HOME_PAGE) }
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
            val bounds = android.graphics.Rect(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt())
            // The app's window grows out of the tapped icon (like Pixel), not the default side slide.
            val opts = if (bounds.width() > 0) android.app.ActivityOptions.makeScaleUpAnimation(
                this@MainActivity.window.decorView, bounds.left, bounds.top, bounds.width(), bounds.height(),
            ).toBundle() else null
            repo.launch(app, bounds, opts)
        }

        Box(Modifier.fillMaxSize().onSizeChanged { window = it }) {
            wallpaper?.let { Image(it, null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize()) }
            CompositionLocalProvider(
                LocalBackdrop provides Backdrop(blurred, window),
                LocalGlass provides GlassStyle(prefs.whiteness / 100f, prefs.tintStrength / 100f, liquid = prefs.liquid && LiquidLens.supported),
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
                        weatherNote = weatherNote,
                        onWeather = {
                            when {
                                hasLocation -> scope.launch {
                                    weatherNote = "Обновляю…"
                                    refreshWeather(context) { w, note -> if (w != null) weather = w; weatherNote = note }
                                }
                                // Refused for good earlier: the system no longer shows its dialog, open app settings.
                                askedLocation && !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION) ->
                                    safeStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                                else -> locationAsk.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                            }
                        },
                        onMakeDefault = { prefs.changeHomePrompted(); requestDefaultHome() },
                        onDismissMakeDefault = { prefs.changeHomePrompted() },
                        onSwipeDown = { expandNotifications() },
                        onSwipeUp = { overlay = Overlay.Search },
                        widgetPage = {
                            WidgetPage(widgets.placed, onAdd = { overlay = Overlay.WidgetPicker }) { w, mod ->
                                val view = remember(w.id) { widgets.view(w.id) { overlay = Overlay.WidgetMenu(w.id) } }
                                if (view != null) {
                                    BoxWithConstraints(mod) {
                                        val wDp = maxWidth.value; val hDp = maxHeight.value
                                        LaunchedEffect(wDp, hDp) { widgets.report(w.id, wDp, hDp) }
                                        AndroidView(factory = { (view.parent as? android.view.ViewGroup)?.removeView(view); view }, modifier = Modifier.fillMaxSize())
                                    }
                                }
                            }
                        },
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
                    Overlay.WidgetPicker -> {
                        var choices by remember { mutableStateOf<List<WidgetChoice>?>(null) }
                        LaunchedEffect(Unit) { choices = withContext(Dispatchers.Default) { widgets.choices() } }
                        WidgetPickerOverlay(choices, onPick = { overlay = Overlay.None; pickWidget(it) }, onBack = { overlay = Overlay.None })
                    }
                    is Overlay.WidgetMenu -> {
                        val i = widgets.placed.indexOfFirst { it.id == o.id }
                        WidgetMenuOverlay(
                            canUp = i > 0, canDown = i in 0 until widgets.placed.lastIndex,
                            onUp = { widgets.move(o.id, -1); overlay = Overlay.None },
                            onDown = { widgets.move(o.id, 1); overlay = Overlay.None },
                            onGrow = { widgets.resize(o.id, 40) },
                            onShrink = { widgets.resize(o.id, -40) },
                            onRemove = { widgets.remove(o.id); overlay = Overlay.None },
                            onDismiss = { overlay = Overlay.None },
                        )
                    }
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

    private suspend fun refreshWeather(context: android.content.Context, done: (WeatherNow?, String?) -> Unit) {
        when (val r = Weather.load(context)) {
            is Weather.Result.Ok -> done(r.now, null)
            is Weather.Result.Fail -> done(null, r.why)
        }
    }

    /**
     * Pulls down the notification shade, as every launcher does on a swipe down.
     * StatusBarManager.expandNotificationsPanel is not in the public SDK, but launchers are
     * allowed to call it (EXPAND_STATUS_BAR is a normal permission).
     */
    @android.annotation.SuppressLint("WrongConstant")
    private fun expandNotifications() {
        runCatching {
            val sbm = getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sbm)
        }
    }

    private fun hasLocationPermission() =
        checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
}

