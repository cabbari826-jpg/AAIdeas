package com.aaideas.krossvord.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** "3 saat 05 dəq", "12 dəq", "0 dəq". */
fun playTimeText(seconds: Long): String {
    val min = seconds / 60
    return if (min >= 60) "${min / 60} saat ${"%02d".format(min % 60)} dəq" else "$min dəq"
}

/** Player statistics: levels solved (overall and per pack), words found, word-of-the-day streaks, time played. */
@Composable
fun StatsScreen(vm: GameViewModel) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Statistika", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Həll olunub", "${vm.completed.size} / ${vm.levels.size}", Modifier.weight(1f))
            StatTile("Tapılmış söz", "${vm.wordsFound}", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Günün sözü: seriya", "🔥 ${vm.daily.streak}", Modifier.weight(1f))
            StatTile("Ən uzun seriya", "${vm.daily.bestStreak} gün", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Oyun vaxtı", playTimeText(vm.playSeconds), Modifier.weight(1f))
            StatTile("Günün sözü tapılıb", "${vm.daily.totalSolved}", Modifier.weight(1f))
        }
        Text("Paketlər", style = MaterialTheme.typography.titleLarge)
        vm.packs.forEachIndexed { p, pack ->
            val levels = vm.packLevels(p)
            val done = levels.count { (_, l) -> l.id in vm.completed }
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth()) {
                    Text(pack.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text("$done / ${levels.size}", style = MaterialTheme.typography.bodyLarge)
                }
                LinearProgressIndicator(
                    progress = { if (levels.isEmpty()) 0f else done / levels.size.toFloat() },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.padding(14.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
