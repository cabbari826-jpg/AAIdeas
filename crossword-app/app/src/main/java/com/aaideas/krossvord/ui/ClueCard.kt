package com.aaideas.krossvord.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.aaideas.krossvord.model.Direction
import com.aaideas.krossvord.model.Entry
import com.aaideas.krossvord.model.GameState
import kotlinx.coroutines.launch

private val TIER_NAMES = listOf("Çətin ipucu", "Orta ipucu", "Asan ipucu")

/**
 * A clue in large text over the puzzle. Opened clues of the word are pages: swipe, or use the
 * arrows, to go back to earlier ones. The bottom button opens the next, easier clue (the second
 * is free, the third needs a rewarded ad unless premium). Closes with ✕ or a tap outside.
 */
@Composable
fun ClueCard(
    game: GameState,
    entry: Entry,
    needsAd: Boolean,
    onEase: () -> Unit,
    onClose: () -> Unit,
) {
    val tier = game.clueTier(entry)
    val pager = rememberPagerState(initialPage = tier) { tier + 1 }
    val scope = rememberCoroutineScope()
    // A newly opened clue slides in.
    LaunchedEffect(tier) { if (pager.currentPage != tier) pager.animateScrollToPage(tier) }
    Dialog(onDismissRequest = onClose) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        (if (entry.direction == Direction.ACROSS) "→ Üfüqi" else "↓ Şaquli") + " · ${entry.letters.size} hərf",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onClose, modifier = Modifier.semantics { contentDescription = "Bağla" }) {
                        Text("✕", style = MaterialTheme.typography.titleLarge)
                    }
                }
                HorizontalPager(pager, Modifier.fillMaxWidth().heightIn(min = 150.dp)) { page ->
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            TIER_NAMES.getOrElse(page) { "İpucu ${page + 1}" },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            entry.clueLevels[page],
                            style = MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 12.dp).semantics { contentDescription = "Böyük ipucu: ${entry.clueLevels[page]}" },
                        )
                    }
                }
                // Page dots with arrows: filled = opened clue, ring = still closed.
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    TextButton(
                        onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                        enabled = pager.currentPage > 0,
                    ) { Text("‹ Əvvəlki") }
                    entry.clueLevels.indices.forEach { i ->
                        val color = when {
                            i == pager.currentPage -> MaterialTheme.colorScheme.primary
                            i <= tier -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                            else -> MaterialTheme.colorScheme.outlineVariant
                        }
                        Box(Modifier.padding(3.dp).size(9.dp).background(color, CircleShape))
                    }
                    TextButton(
                        onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                        enabled = pager.currentPage < tier,
                    ) { Text("Növbəti ›") }
                }
                if (game.canEaseClue(entry)) {
                    Button(onClick = onEase, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        Text(if (needsAd) "Daha asan ipucu ▶ reklam" else "Daha asan ipucu (pulsuz)")
                    }
                } else {
                    Text(
                        if (game.isEntrySolved(entry)) "Söz tapılıb ✓" else "Bu, ən asan ipucudur.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }
        }
    }
}
