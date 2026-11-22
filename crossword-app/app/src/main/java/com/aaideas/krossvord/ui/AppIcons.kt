package com.aaideas.krossvord.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aaideas.krossvord.R

/** Launcher icons. Each is an activity-alias in the manifest; [alias] names never change. */
enum class AppIcon(val alias: String, val label: String, val preview: Int, val premium: Boolean) {
    STANDARD(".Launcher", "Standart", R.mipmap.ic_launcher_round, false),
    TILE(".LauncherTile", "Qızıl Ə", R.mipmap.ic_launcher_tile_round, true),
    KEY(".LauncherKey", "Açar", R.mipmap.ic_launcher_key_round, true);

    private fun component(ctx: Context) = ComponentName(ctx.packageName, ctx.packageName + alias)

    companion object {
        fun current(ctx: Context): AppIcon = entries.firstOrNull { icon ->
            when (ctx.packageManager.getComponentEnabledSetting(icon.component(ctx))) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> icon == STANDARD
                else -> false
            }
        } ?: STANDARD

        /** Turn on [icon]'s launcher entry and turn off the others. The launcher may take a moment to update. */
        fun set(ctx: Context, icon: AppIcon) {
            val pm = ctx.packageManager
            pm.setComponentEnabledSetting(icon.component(ctx), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            entries.filter { it != icon }.forEach {
                pm.setComponentEnabledSetting(it.component(ctx), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            }
        }
    }
}

/** Settings block: pick the app icon. The two extra icons are for premium players. */
@Composable
fun AppIconPicker(isPremium: Boolean) {
    val ctx = LocalContext.current
    var current by remember { mutableStateOf(AppIcon.current(ctx)) }
    // Premium ended: go back to the standard icon.
    LaunchedEffect(isPremium) {
        if (!isPremium && current.premium) { AppIcon.set(ctx, AppIcon.STANDARD); current = AppIcon.STANDARD }
    }
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Tətbiqin ikonu", style = MaterialTheme.typography.headlineSmall)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            AppIcon.entries.forEach { icon ->
                val locked = icon.premium && !isPremium
                val selected = icon == current
                Column(
                    Modifier.clip(RoundedCornerShape(16.dp))
                        .clickable(enabled = !locked && !selected) { AppIcon.set(ctx, icon); current = icon }
                        .padding(6.dp)
                        .semantics { contentDescription = icon.label + if (locked) ", Premium ilə" else if (selected) ", seçilib" else "" },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        // Adaptive icons are not plain vectors, so draw them once into a small bitmap.
                        val bmp = remember(icon) {
                            androidx.core.content.ContextCompat.getDrawable(ctx, icon.preview)!!
                                .toBitmap(160, 160).asImageBitmap()
                        }
                        Image(
                            bmp, null,
                            Modifier.size(64.dp).alpha(if (locked) 0.4f else 1f)
                                .border(if (selected) 3.dp else 0.dp, if (selected) cs.primary else cs.background, RoundedCornerShape(50)),
                        )
                        if (locked) Text("🔒", fontSize = 20.sp)
                    }
                    Text(icon.label, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Text(
            if (isPremium) "Ana ekranda ikon bir neçə saniyəyə dəyişir." else "Əlavə ikonlar Premium ilə açılır.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
