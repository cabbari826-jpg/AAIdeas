package com.aaideas.krossvord.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aaideas.krossvord.model.AzText

/** One found word: the word, where it came from, and its clues as an explanation. */
private data class Found(val word: String, val group: String, val explanation: String)

/**
 * The player's word collection: every word found in the levels and every solved word of the
 * day, with its clues as a short explanation. A lazy list, so it stays smooth on weak phones.
 */
@Composable
fun CollectionScreen(vm: GameViewModel) {
    var query by remember { mutableStateOf("") }
    // "Mənim sözlərim": the words the player got wrong, to go over again.
    var mine by remember { mutableStateOf(vm.openMistakes) }
    val all = remember(vm.collection) {
        val fromLevels = vm.allEntries.filter { (_, e) -> e.answer in vm.collection }.map { (p, e) ->
            Found(e.answer, p.title, e.clueLevels.distinct().joinToString(" · "))
        }
        val fromDaily = vm.daily.words.filter { it.word in vm.collection }.map { Found(it.word, "Günün sözü", it.clue) }
        (fromDaily + fromLevels).distinctBy { it.word }
    }
    val total = vm.allEntries.size
    val missed = remember(vm.mistakeWords) {
        vm.allEntries.filter { (_, e) -> e.answer in vm.mistakeWords }.map { (p, e) ->
            Found(e.answer, p.title, e.clueLevels.distinct().joinToString(" · "))
        }.distinctBy { it.word }
    }
    val shown = remember(all, missed, mine, query) {
        val q = AzText.normalize(query.trim())
        val base = if (mine) missed else all
        if (q.isEmpty()) base else base.filter { it.word.contains(q) }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        ScreenHeader("Söz kolleksiyası", { vm.open(Screen.STATS) }, Modifier.padding(top = 16.dp, bottom = 12.dp))
        Text("Tapılmış sözlər: ${all.size}. Səviyyələrdə cəmi $total söz var.", style = MaterialTheme.typography.bodyMedium)
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.FilterChip(selected = !mine, onClick = { mine = false }, label = { Text("Hamısı") })
            androidx.compose.material3.FilterChip(selected = mine, onClick = { mine = true }, label = { Text("📖 Mənim sözlərim (${missed.size})") })
        }
        if (mine) Text(
            if (missed.isEmpty()) "Səhv etdiyiniz sözlər burada toplanır ki, təkrar edəsiniz. Hələ heç biri yoxdur. Əla!"
            else "Səhv etdiyiniz sözlər. Yadda qalanda «Öyrəndim» deyin.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            query, { query = it },
            label = { Text("Söz axtar") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
        if (all.isEmpty() && !mine) Text("Hələ söz yoxdur. Tapdığın hər söz burada izahı ilə görünəcək.", style = MaterialTheme.typography.bodyLarge)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = BottomMenuHeight)) {
            items(shown, key = { it.word }) { f ->
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(f.word, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(f.explanation, style = MaterialTheme.typography.bodyLarge)
                    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(f.group, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                        if (mine) androidx.compose.material3.OutlinedButton(onClick = { vm.markLearned(f.word) }) { Text("✓ Öyrəndim") }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
