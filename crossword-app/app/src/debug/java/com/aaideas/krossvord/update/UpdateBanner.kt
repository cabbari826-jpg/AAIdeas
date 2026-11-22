package com.aaideas.krossvord.update

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.aaideas.krossvord.BuildConfig
import kotlinx.coroutines.launch

private sealed interface UpdateUi {
    data object Hidden : UpdateUi
    data class Offer(val release: PrototypeRelease) : UpdateUi
    data class NeedsPermission(val release: PrototypeRelease) : UpdateUi
    data class Downloading(val release: PrototypeRelease, val percent: Int) : UpdateUi
    data class Failed(val release: PrototypeRelease) : UpdateUi
}

/**
 * Prototype-only updater. On start it asks GitHub for the newest published
 * build; if it is newer than this one, a card offers to download it and opens
 * the system installer. Lives in src/debug, so the Google Play build has none
 * of it (no INTERNET or REQUEST_INSTALL_PACKAGES permission either).
 */
@Composable
fun UpdateBanner() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ui by remember { mutableStateOf<UpdateUi>(UpdateUi.Hidden) }

    LaunchedEffect(Unit) {
        val latest = PrototypeUpdates.fetchLatest(BuildConfig.UPDATE_REPO) ?: return@LaunchedEffect
        if (latest.build > BuildConfig.VERSION_CODE) ui = UpdateUi.Offer(latest)
    }

    fun start(release: PrototypeRelease) {
        if (!canInstall(context)) {
            ui = UpdateUi.NeedsPermission(release)
            openInstallPermissionSettings(context)
            return
        }
        scope.launch {
            ui = UpdateUi.Downloading(release, 0)
            val apk = PrototypeUpdates.download(context, release) { ui = UpdateUi.Downloading(release, it) }
            if (apk == null) {
                ui = UpdateUi.Failed(release)
            } else {
                ui = UpdateUi.Offer(release)
                launchInstaller(context, apk)
            }
        }
    }

    val state = ui
    if (state == UpdateUi.Hidden) return
    Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.BottomCenter) {
        Card(Modifier.fillMaxWidth().padding(12.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (state) {
                    is UpdateUi.Offer -> {
                        Text("Yeni versiya var: ${state.release.versionName}", style = MaterialTheme.typography.titleMedium)
                        Text("Hazırkı: ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
                        Buttons(onLater = { ui = UpdateUi.Hidden }, onUpdate = { start(state.release) }, label = "Yenilə")
                    }
                    is UpdateUi.NeedsPermission -> {
                        Text("Yeniləmə üçün icazə lazımdır", style = MaterialTheme.typography.titleMedium)
                        Text("Açılan pəncərədə \"Bu mənbədən icazə ver\" seçin, sonra geri qayıdıb yenidən basın.")
                        Buttons(onLater = { ui = UpdateUi.Hidden }, onUpdate = { start(state.release) }, label = "Yenilə")
                    }
                    is UpdateUi.Downloading -> {
                        Text("Yüklənir… ${state.percent}%", style = MaterialTheme.typography.titleMedium)
                        LinearProgressIndicator(progress = { state.percent / 100f }, modifier = Modifier.fillMaxWidth())
                    }
                    is UpdateUi.Failed -> {
                        Text("Yükləmək alınmadı", style = MaterialTheme.typography.titleMedium)
                        Text("İnternet bağlantısını yoxlayın.")
                        Buttons(onLater = { ui = UpdateUi.Hidden }, onUpdate = { start(state.release) }, label = "Yenidən")
                    }
                    UpdateUi.Hidden -> Unit
                }
            }
        }
    }
}

@Composable
private fun Buttons(onLater: () -> Unit, onUpdate: () -> Unit, label: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onLater) { Text("Sonra") }
        Button(onClick = onUpdate) { Text(label) }
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
