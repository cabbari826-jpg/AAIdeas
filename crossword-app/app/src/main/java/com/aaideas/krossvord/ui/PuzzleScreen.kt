package com.aaideas.krossvord.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.aaideas.krossvord.model.AzText
import com.aaideas.krossvord.model.Cell
import com.aaideas.krossvord.model.Direction
import com.aaideas.krossvord.model.Entry
import com.aaideas.krossvord.model.GameState
import com.aaideas.krossvord.model.LetterTiles
import com.aaideas.krossvord.model.Tile

@Composable
fun PuzzleScreen(vm: GameViewModel, isPremium: Boolean) {
    val game = vm.game ?: return
    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = vm::back) { Text("← Səviyyələr") }
            Spacer(Modifier.weight(1f))
            Text(
                "Səviyyə ${vm.currentLevelNumber} · ${game.solvedCount}/${game.board.level.entries.size}",
                style = MaterialTheme.typography.titleSmall,
            )
        }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val cell = min(maxWidth / game.board.cols, maxHeight / game.board.rows)
            Grid(game, cell, vm::tapCell)
        }
        ClueBar(game.activeEntry, game)
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = vm::clearWord, modifier = Modifier.weight(1f)) { Text("Sözü sil") }
            Button(onClick = vm::hint, modifier = Modifier.weight(1f)) {
                Text(if (isPremium) "Hərf aç" else "Hərf aç ▶ reklam")
            }
        }
        val entry = game.activeEntry
        if (vm.useKeyboard || entry == null) AzKeyboard(onLetter = vm::type, onBackspace = vm::backspace)
        else TileInput(LetterTiles.tilesFor(game, entry, vm.tileShuffle), vm::type, vm::backspace, vm::shuffleTiles)
    }
    if (vm.showCompletion) {
        AlertDialog(
            onDismissRequest = vm::dismissCompletion,
            title = { Text("Təbriklər!") },
            text = { Text("Səviyyə ${vm.currentLevelNumber} həll olundu.") },
            confirmButton = { Button(onClick = vm::nextLevel) { Text("Növbəti") } },
            dismissButton = { TextButton(onClick = vm::dismissCompletion) { Text("Bağla") } },
        )
    }
}

@Composable
private fun Grid(game: GameState, cellSize: Dp, onTap: (Cell) -> Unit) {
    val colors = LocalGridColors.current
    val active = game.activeEntry
    val activeCells = active?.cells?.toSet().orEmpty()
    Column(Modifier.border(1.dp, colors.line)) {
        for (r in 0 until game.board.rows) {
            Row {
                for (c in 0 until game.board.cols) {
                    val cell = Cell(r, c)
                    val mod = Modifier.size(cellSize).border(0.5.dp, colors.line)
                    if (game.board.isLetter(cell)) {
                        LetterCell(game, cell, cell == game.selectedCell, cell in activeCells, cellSize, mod.clickable { onTap(cell) })
                    } else {
                        val clues = game.board.cluesAt[cell]
                        if (clues == null) Box(mod.background(colors.emptyCell))
                        else ClueCell(clues, active, mod.background(colors.clueCell).clickable { onTap(cell) })
                    }
                }
            }
        }
    }
}

@Composable
private fun LetterCell(game: GameState, cell: Cell, selected: Boolean, inActive: Boolean, size: Dp, modifier: Modifier) {
    val colors = LocalGridColors.current
    val entries = game.board.entriesAt[cell].orEmpty()
    val bg = when {
        selected -> colors.activeCell
        inActive -> colors.activeWord
        entries.any { game.isEntryWrong(it) } -> colors.wrong
        entries.any { game.isEntrySolved(it) } -> colors.solved
        else -> colors.letterCell
    }
    val ch = game.typed[cell]
    Box(
        modifier.background(bg).semantics { contentDescription = "Xana ${cell.row + 1}, ${cell.col + 1}: ${ch ?: "boş"}" },
        contentAlignment = Alignment.Center,
    ) {
        if (ch != null) {
            Text(
                ch.toString(),
                fontSize = (size.value * 0.55f).sp,
                fontWeight = FontWeight.SemiBold,
                color = if (cell in game.revealed) colors.revealed else colors.letter,
            )
        }
    }
}

@Composable
private fun ClueCell(clues: List<Entry>, active: Entry?, modifier: Modifier) {
    val colors = LocalGridColors.current
    Column(modifier.padding(1.dp)) {
        // Across clue on top, down clue below: the usual Scandinavian order.
        clues.sortedBy { it.direction }.forEach { e ->
            val isActive = e == active
            Box(
                Modifier.fillMaxWidth().weight(1f)
                    .background(if (isActive) colors.activeWord else colors.clueCell),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = e.clue + if (e.direction == Direction.ACROSS) " →" else " ↓",
                    style = TextStyle(color = colors.clueText, textAlign = TextAlign.Center, lineHeight = 1.05.em),
                    autoSize = TextAutoSize.StepBased(minFontSize = 5.sp, maxFontSize = 11.sp, stepSize = 0.5.sp),
                    modifier = Modifier.padding(horizontal = 1.dp),
                )
            }
        }
    }
}

@Composable
private fun ClueBar(entry: Entry?, game: GameState) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
    ) {
        val text = if (entry == null) "Sözü seçin" else {
            val arrow = if (entry.direction == Direction.ACROSS) "→" else "↓"
            "$arrow ${entry.clue}  (${entry.letters.size} hərf)" + if (game.isEntryWrong(entry)) "  · səhvdir" else ""
        }
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 10.dp), style = MaterialTheme.typography.titleMedium)
    }
}

/** Azerbaijani on-screen keyboard: all 32 letters, QÜERTY-like order. */
private val KEY_ROWS = listOf("QÜERTYUİOPÖ", "ASDFGHJKLIƏ", "ZXCVBNMÇŞĞ")

@Composable
private fun AzKeyboard(onLetter: (Char) -> Unit, onBackspace: () -> Unit) {
    check(KEY_ROWS.joinToString("").toSet() == AzText.ALPHABET.toSet())
    val haptic = LocalHapticFeedback.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        KEY_ROWS.forEachIndexed { i, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                row.forEach { ch ->
                    Key(ch.toString(), Modifier.weight(1f)) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onLetter(ch)
                    }
                }
                if (i == KEY_ROWS.lastIndex) Key("⌫", Modifier.weight(1f), onBackspace)
            }
        }
    }
}

@Composable
private fun Key(label: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.height(48.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Box(contentAlignment = Alignment.Center) { Text(label, fontSize = 18.sp, fontWeight = FontWeight.Medium) }
    }
}

/**
 * Tile input: the word's letters, shuffled, plus a few decoys. Tapping a tile puts its letter
 * into the selected cell; used tiles fade out. Same height as the keyboard so the grid doesn't jump.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TileInput(tiles: List<Tile>, onLetter: (Char) -> Unit, onBackspace: () -> Unit, onShuffle: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Column(
        Modifier.fillMaxWidth().height(156.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FlowRow(
            Modifier.fillMaxWidth().weight(1f).padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (tiles.isEmpty()) {
                Text("Söz tapıldı ✓", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
            }
            tiles.forEachIndexed { i, tile ->
                key(i, tile.letter) {
                    LetterTile(tile) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onLetter(tile.letter)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onShuffle, modifier = Modifier.weight(1f)) { Text("⟳ Qarışdır") }
            OutlinedButton(onClick = onBackspace, modifier = Modifier.weight(1f)) { Text("⌫ Sil") }
        }
    }
}

@Composable
private fun LetterTile(tile: Tile, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (tile.used) 0.8f else 1f, tween(180), label = "tileScale")
    val alpha by animateFloatAsState(if (tile.used) 0.25f else 1f, tween(180), label = "tileAlpha")
    Surface(
        modifier = Modifier.size(52.dp).graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
            .clickable(enabled = !tile.used, onClick = onClick)
            .semantics { contentDescription = "Hərf ${tile.letter}" },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = if (tile.used) 0.dp else 3.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                tile.letter.toString(),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
