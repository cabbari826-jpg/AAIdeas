package com.aaideas.launcher.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.aaideas.launcher.BuildConfig
import com.aaideas.launcher.ui.GlassButton
import com.aaideas.launcher.ui.Glyphs
import com.aaideas.launcher.ui.LocalInk
import com.aaideas.launcher.ui.MenuRow
import com.aaideas.launcher.ui.Sep
import com.aaideas.launcher.ui.glass
import kotlinx.coroutines.launch

private sealed interface UpdateUi {
    data object Hidden : UpdateUi
    data class Offer(val release: PrototypeRelease) : UpdateUi
    data class NeedsPermission(val release: PrototypeRelease) : UpdateUi
    data class Downloading(val release: PrototypeRelease, val percent: Int) : UpdateUi
    data class Failed(val release: PrototypeRelease) : UpdateUi
}

private object UpdateState {
    var ui by mutableStateOf<UpdateUi>(UpdateUi.Hidden)
}

/** Settings row: version and "Проверить обновления". */
@Composable
fun UpdateSettingsRow() {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    val ink = LocalInk.current
    MenuRow(Glyphs.Refresh, "Проверить обновления", ink.text, {
        status = "Проверяю…"
        scope.launch {
            val latest = PrototypeUpdates.fetchLatest(BuildConfig.UPDATE_REPO)
            status = when {
                latest == null -> "Нет связи"
                latest.build > BuildConfig.VERSION_CODE -> {
                    if (UpdateState.ui !is UpdateUi.Downloading) UpdateState.ui = UpdateUi.Offer(latest)
                    "Есть ${latest.versionName}"
                }
                else -> "Последняя ✓"
            }
        }
    }, trailing = status ?: BuildConfig.VERSION_NAME)
    Sep()
    val context = androidx.compose.ui.platform.LocalContext.current
    var exporting by remember { mutableStateOf(false) }
    MenuRow(Glyphs.Image, "Отправить значки для проверки", ink.text, {
        if (!exporting) { exporting = true; scope.launch { IconExport.share(context); exporting = false } }
    }, trailing = if (exporting) "Собираю…" else null)
}

/** Prototype-only updater: on every return to the launcher, offers a newer build if there is one. */
@Composable
fun UpdateBanner() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ui by UpdateState::ui
    val ink = LocalInk.current

    LifecycleResumeEffect(Unit) {
        val job = scope.launch {
            val latest = PrototypeUpdates.fetchLatest(BuildConfig.UPDATE_REPO) ?: return@launch
            val current = ui
            if (latest.build > BuildConfig.VERSION_CODE && current !is UpdateUi.Downloading) {
                ui = if (current is UpdateUi.NeedsPermission) UpdateUi.NeedsPermission(latest) else UpdateUi.Offer(latest)
            }
        }
        onPauseOrDispose { job.cancel() }
    }

    fun start(offered: PrototypeRelease) {
        if (!canInstall(context)) {
            ui = UpdateUi.NeedsPermission(offered)
            openInstallPermissionSettings(context)
            return
        }
        scope.launch {
            ui = UpdateUi.Downloading(offered, 0)
            val newest = PrototypeUpdates.fetchLatest(BuildConfig.UPDATE_REPO)
            val release = if (newest != null && newest.build > offered.build) newest else offered
            val apk = PrototypeUpdates.download(context, release) { ui = UpdateUi.Downloading(release, it) }
            if (apk == null) ui = UpdateUi.Failed(release) else { ui = UpdateUi.Offer(release); launchInstaller(context, apk) }
        }
    }

    val state = ui
    if (state == UpdateUi.Hidden) return
    Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp)
                .glass(RoundedCornerShape(28.dp), extraWhite = 0.3f).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (state) {
                is UpdateUi.Offer, is UpdateUi.NeedsPermission, is UpdateUi.Failed -> {
                    val r = when (state) { is UpdateUi.Offer -> state.release; is UpdateUi.NeedsPermission -> state.release; is UpdateUi.Failed -> state.release; else -> error("") }
                    val title = when (state) {
                        is UpdateUi.NeedsPermission -> "Нужно разрешение на установку"
                        is UpdateUi.Failed -> "Не удалось скачать"
                        else -> "Новая версия ${r.versionName}"
                    }
                    val sub = when (state) {
                        is UpdateUi.NeedsPermission -> "В открывшемся окне разрешите установку, вернитесь и нажмите ещё раз."
                        is UpdateUi.Failed -> "Проверьте интернет."
                        else -> "Сейчас: ${BuildConfig.VERSION_NAME}"
                    }
                    Text(title, color = ink.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(sub, color = ink.text, fontSize = 14.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassButton("Позже", { ui = UpdateUi.Hidden }, Modifier.weight(1f))
                        GlassButton("Обновить", { start(r) }, Modifier.weight(1f), strong = true)
                    }
                }
                is UpdateUi.Downloading -> {
                    Text("Загрузка… ${state.percent}%", color = ink.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    LinearProgressIndicator(progress = { state.percent / 100f }, modifier = Modifier.fillMaxWidth(), color = ink.text, trackColor = ink.text.copy(alpha = 0.2f))
                }
                UpdateUi.Hidden -> Unit
            }
        }
    }
}

private fun canInstall(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

private fun openInstallPermissionSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

private fun launchInstaller(context: Context, apk: java.io.File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, "application/vnd.android.package-archive")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
