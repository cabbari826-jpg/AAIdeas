package com.aaideas.launcher.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Wallpaper the launcher draws itself (it needs the pixels to blur them for glass). */
enum class WallpaperKind { SEA, SAND, NIGHT, PHOTO }

/**
 * All user settings, as Compose state backed by SharedPreferences.
 * Defaults are the owner's sketch from 2026-11-26: "Море", soft square icons,
 * clear glass, app-colour tint 90 %, dark text.
 */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    var blur by mutableStateOf(sp.getInt("blur", 11)); private set
    var whiteness by mutableStateOf(sp.getInt("whiteness", 11)); private set
    var tintStrength by mutableStateOf(sp.getInt("tint", 90)); private set
    var iconScale by mutableStateOf(sp.getInt("iconScale", 100)); private set
    var labels by mutableStateOf(sp.getBoolean("labels", true)); private set
    var darkInk by mutableStateOf(sp.getBoolean("darkInk", true)); private set
    var wallpaper by mutableStateOf(runCatching { WallpaperKind.valueOf(sp.getString("wallpaper", null)!!) }.getOrDefault(WallpaperKind.SEA)); private set
    var hidden by mutableStateOf(sp.getStringSet("hidden", emptySet())!!.toSet()); private set
    /** App key -> ARGB colour the owner picked for its glass ("Цвет стекла"). */
    var colors by mutableStateOf(readColors()); private set
    /** "Жидкое стекло": edges bend the wallpaper like a glass lens (Android 13+). */
    var liquid by mutableStateOf(sp.getBoolean("liquid", false)); private set
    var homePrompted by mutableStateOf(sp.getBoolean("homePrompted", false)); private set

    fun changeBlur(v: Int) { blur = v; sp.edit().putInt("blur", v).apply() }
    fun changeWhiteness(v: Int) { whiteness = v; sp.edit().putInt("whiteness", v).apply() }
    fun changeTintStrength(v: Int) { tintStrength = v; sp.edit().putInt("tint", v).apply() }
    fun changeIconScale(v: Int) { iconScale = v; sp.edit().putInt("iconScale", v).apply() }
    fun changeLabels(v: Boolean) { labels = v; sp.edit().putBoolean("labels", v).apply() }
    fun changeDarkInk(v: Boolean) { darkInk = v; sp.edit().putBoolean("darkInk", v).apply() }
    fun changeWallpaper(v: WallpaperKind) { wallpaper = v; sp.edit().putString("wallpaper", v.name).apply() }
    fun changeLiquid(v: Boolean) { liquid = v; sp.edit().putBoolean("liquid", v).apply() }
    fun changeHomePrompted() { homePrompted = true; sp.edit().putBoolean("homePrompted", true).apply() }

    fun hide(key: String) { hidden = hidden + key; sp.edit().putStringSet("hidden", hidden).apply() }
    fun unhide(key: String) { hidden = hidden - key; sp.edit().putStringSet("hidden", hidden).apply() }

    fun setColor(key: String, argb: Int?) {
        colors = if (argb == null) colors - key else colors + (key to argb)
        sp.edit().putStringSet("colors", colors.map { "${it.value}|${it.key}" }.toSet()).apply()
    }

    private fun readColors(): Map<String, Int> =
        sp.getStringSet("colors", emptySet())!!.mapNotNull { s ->
            val i = s.indexOf('|')
            if (i < 0) null else s.substring(i + 1) to (s.substring(0, i).toIntOrNull() ?: return@mapNotNull null)
        }.toMap()
}
