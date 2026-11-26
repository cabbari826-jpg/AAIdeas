package com.aaideas.launcher.data

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.UserManager
import android.util.SizeF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.text.Collator
import java.util.Locale
import kotlin.math.abs

/** A widget on the widgets page: its system id and the height the owner gave it. */
data class PlacedWidget(val id: Int, val heightDp: Int)

/** One widget in the picker. [info] is null only in screenshot tests. */
class WidgetChoice(
    val info: AppWidgetProviderInfo?,
    val appLabel: String,
    val label: String,
    val preview: ImageBitmap?,
    val cells: String,
)

/**
 * Hosts other apps' widgets (AppWidgetHost). The list of placed widgets is kept in
 * SharedPreferences as "id:height" so it survives restarts; widgets whose app is gone are dropped.
 */
class WidgetRepository(private val context: Context) {
    private val manager = AppWidgetManager.getInstance(context)
    private val host = Host(context)
    private val sp = context.getSharedPreferences("widgets", Context.MODE_PRIVATE)
    private val views = HashMap<Int, HostView>()
    private val density = context.resources.displayMetrics.density

    val placed = mutableStateListOf<PlacedWidget>().apply {
        addAll(
            sp.getString("list", "").orEmpty().split(',').mapNotNull { s ->
                val (id, h) = s.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
                PlacedWidget(id.toIntOrNull() ?: return@mapNotNull null, h.toIntOrNull() ?: 160)
            }.filter { runCatching { manager.getAppWidgetInfo(it.id) }.getOrNull() != null },
        )
    }

    fun startListening() = runCatching { host.startListening() }
    fun stopListening() = runCatching { host.stopListening() }

    private fun save() = sp.edit().putString("list", placed.joinToString(",") { "${it.id}:${it.heightDp}" }).apply()

    /** Every widget offered by installed apps (all profiles), grouped by app name. */
    fun choices(): List<WidgetChoice> {
        val um = context.getSystemService(UserManager::class.java)
        val collator = Collator.getInstance(Locale("ru")).apply { strength = Collator.PRIMARY }
        val pm = context.packageManager
        return um.userProfiles.flatMap { runCatching { manager.getInstalledProvidersForProfile(it) }.getOrDefault(emptyList()) }
            .filter { it.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0 }
            .map { info ->
                val app = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(info.provider.packageName, 0)).toString() }
                    .getOrDefault(info.provider.packageName)
                val preview = runCatching {
                    (info.loadPreviewImage(context, 0) ?: info.loadIcon(context, 0))?.let { d ->
                        val w = d.intrinsicWidth.takeIf { it > 0 } ?: (120 * density).toInt()
                        val h = d.intrinsicHeight.takeIf { it > 0 } ?: (120 * density).toInt()
                        val k = minOf(1f, 600f / maxOf(w, h))
                        d.toBitmap((w * k).toInt().coerceAtLeast(1), (h * k).toInt().coerceAtLeast(1)).asImageBitmap()
                    }
                }.getOrNull()
                WidgetChoice(info, app, info.loadLabel(pm) ?: app, preview, cells(info))
            }
            .sortedWith { a, b -> collator.compare(a.appLabel, b.appLabel).takeIf { it != 0 } ?: collator.compare(a.label, b.label) }
    }

    private fun cells(info: AppWidgetProviderInfo): String {
        if (Build.VERSION.SDK_INT >= 31 && info.targetCellWidth > 0 && info.targetCellHeight > 0) return "${info.targetCellWidth}×${info.targetCellHeight}"
        fun n(px: Int) = ((px / density + 30) / 70).toInt().coerceIn(1, 4)
        return "${n(info.minWidth)}×${n(info.minHeight)}"
    }

    /** Starting height for a new widget: what it asks for, within reason. */
    private fun startHeight(info: AppWidgetProviderInfo): Int {
        val asked = if (Build.VERSION.SDK_INT >= 31 && info.targetCellHeight > 0) info.targetCellHeight * 90 else (info.minHeight / density).toInt()
        return ((asked + 9) / 10 * 10).coerceIn(MIN_H, MAX_H)
    }

    /** Step 1: a new id. Returns the id and whether the system already allows binding it. */
    fun allocate(info: AppWidgetProviderInfo): Pair<Int, Boolean> {
        val id = host.allocateAppWidgetId()
        val ok = runCatching { manager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null) }.getOrDefault(false)
        return id to ok
    }

    fun info(id: Int): AppWidgetProviderInfo? = runCatching { manager.getAppWidgetInfo(id) }.getOrNull()

    fun needsConfigure(info: AppWidgetProviderInfo): Boolean {
        if (info.configure == null) return false
        // Android 12+: the widget may say setup is optional (it can be done later from the widget).
        if (Build.VERSION.SDK_INT >= 31 &&
            info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL != 0 &&
            info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE != 0) return false
        return true
    }

    fun startConfigure(activity: android.app.Activity, id: Int, requestCode: Int): Boolean =
        runCatching { host.startAppWidgetConfigureActivityForResult(activity, id, 0, requestCode, null) }.isSuccess

    /** Last step: the widget goes to the end of the widgets page. */
    fun add(id: Int) {
        val info = info(id) ?: return discard(id)
        if (placed.none { it.id == id }) { placed += PlacedWidget(id, startHeight(info)); save() }
    }

    fun discard(id: Int) { runCatching { host.deleteAppWidgetId(id) } }

    fun remove(id: Int) {
        placed.removeAll { it.id == id }
        views.remove(id)
        discard(id)
        save()
    }

    fun move(id: Int, by: Int) {
        val i = placed.indexOfFirst { it.id == id }
        val j = i + by
        if (i < 0 || j !in placed.indices) return
        val w = placed.removeAt(i); placed.add(j, w); save()
    }

    fun resize(id: Int, byDp: Int) {
        val i = placed.indexOfFirst { it.id == id }
        if (i < 0) return
        placed[i] = placed[i].copy(heightDp = (placed[i].heightDp + byDp).coerceIn(MIN_H, MAX_H)); save()
    }

    /** The widget's view, made once and kept so it is not rebuilt on every page swipe. */
    fun view(id: Int, onLongPress: () -> Unit): HostView? {
        val v = views[id] ?: run {
            val info = info(id) ?: return null
            (host.createView(context.applicationContext, id, info) as? HostView)?.also { views[id] = it }
        } ?: return null
        v.onLongPress = onLongPress
        return v
    }

    /** Tell the widget its real size so it picks the right layout. */
    fun report(id: Int, wDp: Float, hDp: Float) {
        val v = views[id] ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= 31) v.updateAppWidgetSize(Bundle(), listOf(SizeF(wDp, hDp)))
            else @Suppress("DEPRECATION") v.updateAppWidgetSize(null, wDp.toInt(), hDp.toInt(), wDp.toInt(), hDp.toInt())
        }
    }

    private class Host(context: Context) : AppWidgetHost(context, HOST_ID) {
        override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): AppWidgetHostView = HostView(context)
    }

    companion object {
        const val HOST_ID = 0x5355
        const val MIN_H = 80
        const val MAX_H = 520
    }
}

/**
 * The widget's view, with a long press that works even over its buttons
 * (the widget itself never sees a long press, like on Pixel).
 */
class HostView(context: Context) : AppWidgetHostView(context) {
    var onLongPress: (() -> Unit)? = null
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var x0 = 0f
    private var y0 = 0f
    private var fired = false
    private val check = Runnable {
        fired = true
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        onLongPress?.invoke()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        track(ev)
        return fired
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        track(ev)
        // Keep the gesture even where the widget has nothing to tap, so a long press is seen.
        return true
    }

    private fun track(ev: MotionEvent) {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                fired = false; x0 = ev.x; y0 = ev.y
                removeCallbacks(check)
                postDelayed(check, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> if (abs(ev.x - x0) > slop || abs(ev.y - y0) > slop) removeCallbacks(check)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> removeCallbacks(check)
        }
    }
}
