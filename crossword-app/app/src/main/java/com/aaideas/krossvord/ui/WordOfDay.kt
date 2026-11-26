package com.aaideas.krossvord.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aaideas.krossvord.model.DailyWords

/** Home-screen card for the word of the day; the design's home can place it anywhere. */
@Composable
fun WordOfDayCard(daily: DailyWordGame, onOpen: () -> Unit) {
    val g = daily.guess
    Card(
        onClick = onOpen,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = Modifier.fillMaxWidth().glassy(CardDefaults.shape),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Günün sözü", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                if (daily.streak > 0) Text("🔥 ${daily.streak}", style = MaterialTheme.typography.titleSmall)
            }
            Text(
                when {
                    g.solved -> "✓ Tapdın: ${g.word.word}. Sabah yeni söz."
                    g.failed -> "Bu gün alınmadı: ${g.word.word}. Sabah yeni söz."
                    else -> "${g.letters.size} hərf · ${DailyWords.MAX_ATTEMPTS - g.attempts} cəhd. Təsvirdən tap!"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
fun WordOfDayScreen(daily: DailyWordGame, isPremium: Boolean, onBack: () -> Unit) {
    val g = daily.guess
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        ScreenHeader("Günün sözü", onBack) {
            Text(
                "♥".repeat(DailyWords.MAX_ATTEMPTS - g.attempts) + "♡".repeat(g.attempts),
                color = MaterialTheme.colorScheme.error,
                fontSize = 18.sp,
                modifier = Modifier.padding(end = 4.dp).semantics { contentDescription = "Cəhd: ${DailyWords.MAX_ATTEMPTS - g.attempts}" },
            )
            if (daily.streak > 0) Text("🔥 ${daily.streak}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 6.dp))
        }
        Spacer(Modifier.height(12.dp))
        Spacer(Modifier.height(8.dp))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Text(
                g.word.clue,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        // Letter boxes: open (hint) letters in the accent colour, a wrong try flashes red.
        val boxes = if (g.finished) g.letters.map { it } else g.boxes()
        val colors = LocalGridColors.current
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        ) {
            boxes.forEachIndexed { i, ch ->
                val bg = when {
                    g.solved -> colors.solved
                    g.failed -> colors.wrong
                    daily.wrongFlash -> colors.wrong
                    i in g.revealed -> colors.activeWord
                    else -> colors.letterCell
                }
                Box(
                    Modifier.weight(1f).widthIn(max = 48.dp).aspectRatio(1f)
                        .background(bg, MaterialTheme.shapes.small)
                        .border(1.dp, colors.line, MaterialTheme.shapes.small),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(ch?.toString() ?: "", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = colors.letter)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            when {
                g.solved -> "Əla, tapdın! Sabah yeni söz olacaq."
                g.failed -> "Cəhdlər bitdi. Söz: ${g.word.word}. Sabah yenidən yoxla."
                daily.wrongFlash -> "Səhvdir. Yenə yoxla."
                else -> "${g.letters.size} hərf. Hər səhv bir cəhd aparır."
            },
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        val reward = daily.reward
        if (g.solved && reward != null) {
            Text(
                reward,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        } else if (!g.finished) {
            Text(
                "Tapsan: canlar dolur, ya da +1 ehtiyat can.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
        // The keyboard sits a little above the bottom edge, with big keys.
        Spacer(Modifier.weight(1f))
        if (!g.finished) {
            Button(onClick = daily::hint, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                Text(if (isPremium) "Hərf aç" else "Hərf aç ▶ reklam")
            }
            AzKeyboard(onLetter = daily::type, onBackspace = daily::backspace, keyHeight = 58.dp)
            Spacer(Modifier.height(16.dp))
        }
    }
}
