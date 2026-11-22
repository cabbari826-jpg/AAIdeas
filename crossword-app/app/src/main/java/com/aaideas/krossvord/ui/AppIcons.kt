package com.aaideas.krossvord.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.aaideas.krossvord.R
import java.util.Calendar

enum class IconKind { STANDARD, PREMIUM, HOLIDAY }

/**
 * Launcher icons. Each is an activity-alias in the manifest. Alias names never change: ".LauncherTile"
 * once showed a gold tile and now shows Xalça, so a phone that had it on keeps an app icon after the update.
 */
enum class AppIcon(val alias: String, val label: String, val preview: Int, val kind: IconKind) {
    STANDARD(".Launcher", "Standart", R.mipmap.ic_launcher_round, IconKind.STANDARD),
    XALCA(".LauncherTile", "Xalça", R.mipmap.ic_launcher_xalca_round, IconKind.PREMIUM),
    GECE(".LauncherNight", "Gecə", R.mipmap.ic_launcher_night_round, IconKind.PREMIUM),
    ULDUZ(".LauncherUlduz", "Ulduz", R.mipmap.ic_launcher_ulduz_round, IconKind.PREMIUM),
    XARIBULBUL(".LauncherXaribulbul", "Xarıbülbül", R.mipmap.ic_launcher_xaribulbul_round, IconKind.PREMIUM),
    BAYRAQ(".LauncherBayraq", "Bayraq", R.mipmap.ic_launcher_bayraq_round, IconKind.PREMIUM),
    KEY(".LauncherKey", "Açar", R.mipmap.ic_launcher_key_round, IconKind.PREMIUM),
    NOVRUZ(".LauncherNovruz", "Novruz", R.mipmap.ic_launcher_novruz_round, IconKind.HOLIDAY),
    YENI_IL(".LauncherYeniIl", "Yeni il", R.mipmap.ic_launcher_yeniil_round, IconKind.HOLIDAY);

    private fun component(ctx: Context) = ComponentName(ctx.packageName, ctx.packageName + alias)

    companion object {
        private fun prefs(ctx: Context) = ctx.getSharedPreferences("app_icon", Context.MODE_PRIVATE)

        /** The launcher entry that is on now. */
        fun current(ctx: Context): AppIcon = entries.firstOrNull { icon ->
            when (ctx.packageManager.getComponentEnabledSetting(icon.component(ctx))) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> icon == STANDARD
                else -> false
            }
        } ?: STANDARD

        /** What the player picked in Settings (holiday icons are never picked, only shown in season). */
        fun chosen(ctx: Context): AppIcon =
            entries.firstOrNull { it.name == prefs(ctx).getString("chosen", null) } ?: STANDARD

        fun holidaysOn(ctx: Context): Boolean = prefs(ctx).getBoolean("holidays", false)

        /** Novruz from 15 March to 5 April, New Year from 20 December to 7 January. */
        fun holidayFor(month: Int, day: Int): AppIcon? = when {
            Skin.isNovruz(month, day) -> NOVRUZ
            (month == 12 && day >= 20) || (month == 1 && day <= 7) -> YENI_IL
            else -> null
        }

        /** The icon that should be on: a holiday one in season if allowed, else the choice, else standard. */
        fun target(chosen: AppIcon, holidays: Boolean, isPremium: Boolean, month: Int, day: Int): AppIcon {
            if (holidays) holidayFor(month, day)?.let { return it }
            return if (chosen.kind == IconKind.PREMIUM && !isPremium) STANDARD else chosen
        }

        /**
         * Put the right icon on. Called when the app opens and when premium or a setting changes,
         * so holiday icons come and go the first time the player opens the game in or after the season.
         * Note for Play Billing: call this only once the purchase state is known, or a premium icon resets.
         */
        fun sync(ctx: Context, isPremium: Boolean, today: Calendar = Calendar.getInstance()): AppIcon {
            val want = target(chosen(ctx), holidaysOn(ctx), isPremium,
                today.get(Calendar.MONTH) + 1, today.get(Calendar.DAY_OF_MONTH))
            if (current(ctx) != want) set(ctx, want)
            return want
        }

        fun choose(ctx: Context, icon: AppIcon, isPremium: Boolean): AppIcon {
            prefs(ctx).edit().putString("chosen", icon.name).apply()
            return sync(ctx, isPremium)
        }

        fun setHolidays(ctx: Context, on: Boolean, isPremium: Boolean): AppIcon {
            prefs(ctx).edit().putBoolean("holidays", on).apply()
            return sync(ctx, isPremium)
        }

        /** Turn on [icon]'s launcher entry and turn off the others. The launcher may take a moment to update. */
        private fun set(ctx: Context, icon: AppIcon) {
            val pm = ctx.packageManager
            pm.setComponentEnabledSetting(icon.component(ctx), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            entries.filter { it != icon }.forEach {
                pm.setComponentEnabledSetting(it.component(ctx), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            }
        }
    }
}

/** Settings block: pick the app icon (extra ones with premium) and turn holiday icons on or off. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppIconPicker(isPremium: Boolean) {
    val ctx = LocalContext.current
    var chosen by remember(isPremium) { mutableStateOf(AppIcon.chosen(ctx).let { if (it.kind == IconKind.PREMIUM && !isPremium) AppIcon.STANDARD else it }) }
    var holidays by remember { mutableStateOf(AppIcon.holidaysOn(ctx)) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Tətbiqin ikonu", style = MaterialTheme.typography.headlineSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AppIcon.entries.filter { it.kind != IconKind.HOLIDAY }.forEach { icon ->
                val locked = icon.kind == IconKind.PREMIUM && !isPremium
                IconTile(icon, selected = icon == chosen, locked = locked) {
                    AppIcon.choose(ctx, icon, isPremium); chosen = icon
                }
            }
        }
        Text(
            if (isPremium) "Ana ekranda ikon bir neçə saniyəyə dəyişir." else "Əlavə ikonlar Premium ilə açılır.",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Bayram ikonları", style = MaterialTheme.typography.titleMedium)
                Text("Novruzda və Yeni ildə ikon özü dəyişir, sonra geri qayıdır.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(holidays, { holidays = it; AppIcon.setHolidays(ctx, it, isPremium) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AppIcon.entries.filter { it.kind == IconKind.HOLIDAY }.forEach { IconTile(it, selected = false, locked = false, onPick = null) }
        }
    }
}

@Composable
private fun IconTile(icon: AppIcon, selected: Boolean, locked: Boolean, onPick: (() -> Unit)?) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    // Adaptive icons are not plain vectors, so draw each once into a small bitmap.
    val bmp = remember(icon) {
        androidx.core.content.ContextCompat.getDrawable(ctx, icon.preview)!!.toBitmap(144, 144).asImageBitmap()
    }
    Column(
        Modifier.width(76.dp).clip(RoundedCornerShape(16.dp))
            .then(if (onPick != null) Modifier.clickable(enabled = !locked && !selected, onClick = onPick) else Modifier)
            .padding(vertical = 6.dp)
            .semantics { contentDescription = icon.label + if (locked) ", Premium ilə" else if (selected) ", seçilib" else "" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Image(
                bmp, null,
                Modifier.size(56.dp).clip(RoundedCornerShape(50)).alpha(if (locked) 0.45f else 1f)
                    .border(if (selected) 3.dp else 0.dp, if (selected) cs.primary else cs.background, RoundedCornerShape(50)),
            )
            if (locked) Text("🔒", fontSize = 18.sp)
        }
        Text(icon.label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}
