package com.aaideas.krossvord.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aaideas.krossvord.data.RemotePack
import com.aaideas.krossvord.model.Weekly

/**
 * "Yeni paketlər": level packs to download once and then play offline. Big buttons, one per
 * pack, no gestures. The list comes from the release "packs" (Açar Söz Studio publishes there);
 * without internet it shows the list as last seen.
 */
@Composable
fun PacksScreen(vm: GameViewModel) {
    LaunchedEffect(Unit) { vm.refreshRemote() }
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader("Yeni paketlər", vm::back)
        Text(
            "Paketi bir dəfə yükləyin, sonra internetsiz oynayın. Həftənin sürprizləri özü yüklənir.",
            style = MaterialTheme.typography.bodyLarge,
        )
        when (vm.online) {
            GameViewModel.Online.LOADING -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 3.dp)
                Spacer(Modifier.width(10.dp))
                Text("Siyahı yoxlanılır…", style = MaterialTheme.typography.bodyLarge)
            }
            GameViewModel.Online.OFFLINE -> Surface(shape = MaterialTheme.shapes.large, color = cs.surfaceVariant) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("📶 İnternet yoxdur", style = MaterialTheme.typography.titleMedium)
                    Text("Yüklənmiş paketlər internetsiz işləyir. Yeni paket üçün internetə qoşulun.", style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = vm::refreshRemote) { Text("Yenidən yoxla") }
                }
            }
            else -> {}
        }
        vm.downloadNote?.let { note ->
            Surface(shape = MaterialTheme.shapes.large, color = cs.tertiaryContainer, contentColor = cs.onTertiaryContainer) {
                Text(note, Modifier.fillMaxWidth().padding(14.dp), style = MaterialTheme.typography.bodyLarge)
            }
        }
        val list = vm.remotePacks
        if (list.isEmpty() && vm.online == GameViewModel.Online.OK) {
            Text("Hələlik yeni paket yoxdur. Tezliklə olacaq!", style = MaterialTheme.typography.bodyLarge)
        }
        list.forEach { r -> RemotePackRow(vm, r) }
        Spacer(Modifier.height(BottomMenuHeight))
    }
}

@Composable
private fun RemotePackRow(vm: GameViewModel, r: RemotePack) {
    val cs = MaterialTheme.colorScheme
    val have = vm.haveVersion(r.packId)
    val busy = vm.downloading[r.packId] == true
    val weekly = Weekly.isWeekly(r.packId)
    Surface(shape = MaterialTheme.shapes.large, color = cs.surfaceContainerHigh, modifier = Modifier.fillMaxWidth().glassy(MaterialTheme.shapes.large)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text((if (weekly) "🎁 " else "📦 ") + r.title, style = MaterialTheme.typography.titleLarge)
            Text(
                "${r.levels} səviyyə · ${kb(r.bytes)}" + if (weekly) " · həftənin sürprizi" else "",
                style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
            )
            when {
                busy -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Yüklənir…", style = MaterialTheme.typography.bodyLarge)
                }
                have == null -> Button(onClick = { vm.downloadPack(r) }, Modifier.fillMaxWidth().height(56.dp)) { Text("⬇ Yüklə", style = MaterialTheme.typography.titleMedium) }
                have < r.contentVersion -> Button(onClick = { vm.downloadPack(r) }, Modifier.fillMaxWidth().height(56.dp)) { Text("⟳ Yenilə", style = MaterialTheme.typography.titleMedium) }
                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("✓ Yüklənib", style = MaterialTheme.typography.titleMedium, color = cs.primary, modifier = Modifier.weight(1f))
                    val p = vm.packs.indexOfFirst { it.packId == r.packId }
                    if (p >= 0) Button(onClick = { vm.selectedPack = p; vm.open(Screen.HOME) }) { Text("Oyna ▶") }
                }
            }
        }
    }
}

private fun kb(bytes: Long): String = if (bytes <= 0) "kiçik" else "${(bytes + 1023) / 1024} KB"
