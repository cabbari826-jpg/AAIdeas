package com.aaideas.krossvord.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aaideas.krossvord.model.WordBuilder
import com.aaideas.krossvord.monetization.AdKind
import com.aaideas.krossvord.monetization.PlaceholderAdService

@Composable
fun HomeScreen(vm: GameViewModel) {
    var confirmReset by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Krossvord", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Text("Azərbaycanca skandinav krossvordu", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        Text("Həll olunub: ${vm.completed.size} / ${vm.levels.size}", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(32.dp))
        Button(onClick = { vm.startLevel(vm.continueIndex()) }, Modifier.fillMaxWidth().height(56.dp)) { Text(vm.continueLabel()) }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { vm.open(Screen.LEVELS) }, Modifier.fillMaxWidth().height(56.dp)) { Text("Səviyyə seç") }
        Spacer(Modifier.height(12.dp))
        if (vm.completed.isNotEmpty()) {
            OutlinedButton(onClick = { confirmReset = true }, Modifier.fillMaxWidth().height(56.dp)) { Text("Yenidən başla") }
            Spacer(Modifier.height(12.dp))
        }
        TextButton(onClick = { vm.open(Screen.SETTINGS) }) { Text("Ayarlar") }
    }
    if (confirmReset) ResetAllDialog(onConfirm = { confirmReset = false; vm.resetAllProgress(); vm.startLevel(0) }, onDismiss = { confirmReset = false })
}

/** Asks before wiping all progress. */
@Composable
fun ResetAllDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hər şeyi sıfırla?") },
        text = { Text("Bütün həll olunmuş səviyyələr, canlar və pulsuz ipucları silinəcək. Ayarlar və premium qalır.") },
        confirmButton = { Button(onClick = onConfirm) { Text("Sıfırla") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("İmtina") } },
    )
}

@Composable
fun LevelsScreen(vm: GameViewModel) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TextButton(onClick = vm::back) { Text("← Əsas səhifə") }
        vm.packs.forEach { Text(it.title, style = MaterialTheme.typography.headlineSmall) }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(vm.levels) { i, level ->
                val done = level.id in vm.completed
                Card(Modifier.fillMaxWidth().clickable { vm.startLevel(i) }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Səviyyə ${i + 1}", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.weight(1f))
                        Text(if (done) "✓ həll olunub · yenidən oyna" else "${level.entries.size} söz")
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: GameViewModel, isPremium: Boolean) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = vm::back) { Text("← Əsas səhifə") }
        Text("Hərflərin daxil edilməsi", style = MaterialTheme.typography.headlineSmall)
        listOf(
            InputMode.COLUMNS to "Anaqram: sözü sütunlardakı hərflərdən yığ",
            InputMode.TILES to "Kartlar: hərfləri bir-bir xanaya qoy",
            InputMode.KEYBOARD to "Çətin rejim: tam klaviatura",
        ).forEach { (mode, label) ->
            Row(
                Modifier.fillMaxWidth().clickable { vm.setInput(mode) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = vm.inputMode == mode, onClick = { vm.setInput(mode) })
                Text(label)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Artıq hərflər")
                Text("Sütunlara sözdə olmayan ${WordBuilder.EXTRA_LETTERS} hərf əlavə olunur", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = vm.extraLetters, onCheckedChange = vm::changeExtraLetters)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Bir ipucu ilə başla")
                Text("Qalan ipucları istəyəndə açılır", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = vm.hideClues, onCheckedChange = vm::changeHideClues)
        }
        Spacer(Modifier.height(8.dp))
        Text("İrəliləyiş", style = MaterialTheme.typography.headlineSmall)
        Text("Həll olunub: ${vm.completed.size} / ${vm.levels.size}. Həll olunmuş səviyyəni siyahıdan yenidən oynamaq olar.")
        var confirmReset by remember { mutableStateOf(false) }
        OutlinedButton(onClick = { confirmReset = true }) { Text("Bütün irəliləyişi sıfırla") }
        if (confirmReset) ResetAllDialog(onConfirm = { confirmReset = false; vm.resetAllProgress() }, onDismiss = { confirmReset = false })
        Spacer(Modifier.height(8.dp))
        Text("Premium", style = MaterialTheme.typography.headlineSmall)
        Text(
            if (isPremium) "Premium aktivdir: reklam yoxdur, ipucları pulsuzdur."
            else "Premium: səviyyələr arasında reklam olmur, ipucları reklamsız açılır."
        )
        if (isPremium) OutlinedButton(onClick = vm.premium::resetForTesting) { Text("Premiumu söndür (test)") }
        else Button(onClick = vm.premium::purchase) { Text("Premium al (test, ödənişsiz)") }
        Text("Prototip: alış hələ Google Play Billing ilə bağlı deyil.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        Text("Mənbələr", style = MaterialTheme.typography.headlineSmall)
        Text("Bu paketdəki sözlər və ipucları qaralamadır, redaktə gözləyir.", style = MaterialTheme.typography.bodyMedium)
    }
}

/** Fake ad so the flow can be tried before AdMob is added. */
@Composable
fun AdPlaceholderHost(ads: PlaceholderAdService) {
    val req = ads.pending ?: return
    AlertDialog(
        onDismissRequest = { ads.finish(watched = false) },
        title = { Text(if (req.kind == AdKind.REWARDED) "Reklam (mükafatlı)" else "Reklam") },
        text = {
            Text(
                if (req.kind == AdKind.REWARDED) "Burada mükafatlı reklam olacaq. Sonunda: ${req.reason}."
                else "Burada səviyyələr arası reklam olacaq."
            )
        },
        confirmButton = {
            Button(onClick = { ads.finish(watched = true) }) {
                Text(if (req.kind == AdKind.REWARDED) "Baxdım, mükafatı al" else "Bağla")
            }
        },
        dismissButton = if (req.kind == AdKind.REWARDED) {
            { TextButton(onClick = { ads.finish(watched = false) }) { Text("İmtina") } }
        } else null,
    )
}
