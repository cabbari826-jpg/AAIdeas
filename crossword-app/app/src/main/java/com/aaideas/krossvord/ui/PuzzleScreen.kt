package com.aaideas.krossvord.ui

import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import com.aaideas.krossvord.model.WordBuilder

@Composable
fun PuzzleScreen(vm: GameViewModel, isPremium: Boolean) {
    val game = vm.game ?: return
    val columns = vm.inputMode == InputMode.COLUMNS
    val letters = if (columns) vm.columnLetters() else emptyList()
    val draft = if (columns) vm.draft() else emptyList()
    // Letters being built in the columns mode are previewed in the grid before the check.
    val preview: Map<Cell, Char> = game.activeEntry?.let { e ->
        game.openCells(e).zip(draft.mapNotNull { letters.getOrNull(it) }).toMap()
    }.orEmpty()
    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = vm::back) { Text("← Səviyyələr") }
            Spacer(Modifier.weight(1f))
            Text(
                "♥".repeat(vm.lives) + "♡".repeat(MAX_LIVES - vm.lives),
                color = MaterialTheme.colorScheme.error,
                fontSize = 18.sp,
                modifier = Modifier.padding(end = 10.dp).semantics { contentDescription = "Can: ${vm.lives}" },
            )
            Text(
                "Səviyyə ${vm.currentLevelNumber} · ${game.solvedCount}/${game.board.level.entries.size}",
                style = MaterialTheme.typography.titleSmall,
            )
        }
        if (game.keyword != null) KeywordStrip(game.keywordLetters, game.isKeywordKnown, vm::openKeywordDialog)
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val cell = min(maxWidth / game.board.cols, maxHeight / game.board.rows)
            Grid(game, cell, preview, vm.feedback, vm::isClueShown, vm::tapCell)
        }
        ClueBar(
            game.activeEntry, game, game.activeEntry?.let(vm::isClueShown) ?: true, vm::openActiveClue,
            onEase = vm::easeActiveClue, easeNeedsAd = vm.easeNeedsAd(),
        )
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (columns) OutlinedButton(onClick = vm::shuffleTiles, modifier = Modifier.weight(1f)) { Text("⟳ Qarışdır") }
            else OutlinedButton(onClick = vm::clearWord, modifier = Modifier.weight(1f)) { Text("Sözü sil") }
            Button(onClick = vm::hint, modifier = Modifier.weight(1f)) {
                Text(
                    when {
                        isPremium -> "Hərf aç"
                        vm.freeHints > 0 -> "Hərf aç (${vm.freeHints} pulsuz)"
                        else -> "Hərf aç ▶ reklam"
                    }
                )
            }
        }
        val entry = game.activeEntry
        when {
            entry == null || vm.inputMode == InputMode.KEYBOARD -> AzKeyboard(onLetter = vm::type, onBackspace = vm::backspace)
            vm.inputMode == InputMode.TILES ->
                TileInput(LetterTiles.tilesFor(game, entry, vm.tileShuffle), vm::type, vm::backspace, vm::shuffleTiles)
            else -> ColumnsInput(
                letters = letters,
                draft = draft,
                slots = game.openCells(entry).size,
                word = run {
                    // Whole word: letters already settled in the grid are shown fixed, picks fill the gaps.
                    val picked = draft.mapNotNull { letters.getOrNull(it) }.iterator()
                    entry.cells.map { c ->
                        if (game.isLocked(c)) game.board.solution[c] to true
                        else (if (picked.hasNext()) picked.next() else null) to false
                    }
                },
                feedback = vm.feedback,
                onPick = vm::pickLetter,
                onUndo = vm::unpickLast,
                onClear = vm::clearDraft,
            )
        }
    }
    if (vm.showKeywordDialog) KeywordDialog(vm)
    if (vm.outOfLives && !vm.showCompletion && vm.ads.pending == null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Canlar bitdi") },
            text = { Text("Üç səhv söz. Reklama bax və bir can al, ya da səviyyəni yenidən başla.") },
            confirmButton = {
                Button(onClick = vm::extraLife) { Text(if (isPremium) "+1 can" else "+1 can ▶ reklam") }
            },
            dismissButton = { TextButton(onClick = vm::restartLevel) { Text("Yenidən başla") } },
        )
    }
    if (vm.showCompletion) {
        AlertDialog(
            onDismissRequest = vm::dismissCompletion,
            title = { Text("Təbriklər!") },
            text = {
                Text(
                    "Səviyyə ${vm.currentLevelNumber} həll olundu." +
                        (game.keyword?.let { "\nAçar söz: ${it.word}" } ?: "")
                )
            },
            confirmButton = { Button(onClick = vm::nextLevel) { Text("Növbəti") } },
            dismissButton = { TextButton(onClick = vm::dismissCompletion) { Text("Bağla") } },
        )
    }
}

@Composable
private fun Grid(
    game: GameState,
    cellSize: Dp,
    preview: Map<Cell, Char>,
    feedback: WordFeedback?,
    clueShown: (Entry) -> Boolean,
    onTap: (Cell) -> Unit,
) {
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
                        LetterCell(
                            game, cell, cell == game.selectedCell, cell in activeCells, cellSize,
                            preview[cell], feedback == WordFeedback.WRONG && cell in activeCells,
                            mod.clickable { onTap(cell) },
                        )
                    } else {
                        val clues = game.board.cluesAt[cell]
                        if (clues == null) Box(mod.background(colors.emptyCell))
                        else ClueCell(clues, active, clueShown, game::clueText, mod.background(colors.clueCell).clickable { onTap(cell) })
                    }
                }
            }
        }
    }
}

@Composable
private fun LetterCell(
    game: GameState,
    cell: Cell,
    selected: Boolean,
    inActive: Boolean,
    size: Dp,
    preview: Char?,
    wrongFlash: Boolean,
    modifier: Modifier,
) {
    val colors = LocalGridColors.current
    val entries = game.board.entriesAt[cell].orEmpty()
    val bg = when {
        wrongFlash -> colors.wrong
        selected && preview == null -> colors.activeCell
        inActive -> colors.activeWord
        entries.any { game.isEntryWrong(it) } -> colors.wrong
        entries.any { game.isEntrySolved(it) } -> colors.solved
        else -> colors.letterCell
    }
    val ch = preview ?: game.typed[cell]
    val keywordIndex = game.keyword?.cellList?.indexOf(cell) ?: -1
    Box(
        modifier.background(bg).semantics { contentDescription = "Xana ${cell.row + 1}, ${cell.col + 1}: ${ch ?: "boş"}" },
        contentAlignment = Alignment.Center,
    ) {
        if (keywordIndex >= 0) {
            // Ring sits inset; the number sits in the corner on the cell colour so it never covers the letter.
            Box(Modifier.fillMaxSize().padding(size * 0.14f).border(2.dp, colors.keyword, RoundedCornerShape(50)))
            Text(
                "${keywordIndex + 1}",
                fontSize = (size.value * 0.22f).sp,
                lineHeight = (size.value * 0.22f).sp,
                color = colors.keyword,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopStart).background(bg).padding(start = 2.dp, end = 1.dp),
            )
        }
        if (ch != null) {
            Text(
                ch.toString(),
                fontSize = (size.value * 0.55f).sp,
                fontWeight = FontWeight.SemiBold,
                color = when {
                    preview != null -> colors.letter.copy(alpha = 0.6f)
                    cell in game.revealed -> colors.revealed
                    else -> colors.letter
                },
            )
        }
    }
}

@Composable
private fun ClueCell(
    clues: List<Entry>,
    active: Entry?,
    shown: (Entry) -> Boolean,
    text: (Entry) -> String,
    modifier: Modifier,
) {
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
                    text = (if (shown(e)) text(e) else "?") + if (e.direction == Direction.ACROSS) " →" else " ↓",
                    style = TextStyle(color = colors.clueText, textAlign = TextAlign.Center, lineHeight = 1.05.em),
                    autoSize = TextAutoSize.StepBased(minFontSize = 5.sp, maxFontSize = 11.sp, stepSize = 0.5.sp),
                    modifier = Modifier.padding(horizontal = 1.dp),
                )
            }
        }
    }
}

@Composable
private fun ClueBar(
    entry: Entry?,
    game: GameState,
    shown: Boolean,
    onOpen: () -> Unit,
    onEase: () -> Unit,
    easeNeedsAd: Boolean,
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val text = if (entry == null) "Sözü seçin" else {
                val arrow = if (entry.direction == Direction.ACROSS) "→" else "↓"
                val clue = if (shown) game.clueText(entry) else "İpucu gizlidir"
                "$arrow $clue  (${entry.letters.size} hərf)" + if (game.isEntryWrong(entry)) "  · səhvdir" else ""
            }
            Text(
                text,
                Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 10.dp),
                style = MaterialTheme.typography.titleMedium,
            )
            if (entry != null && !shown) {
                TextButton(onClick = onOpen) { Text("İpucunu aç") }
            } else if (entry != null && game.canEaseClue(entry)) {
                // Clue levels: dots show how easy the clue is now; the button opens the next, easier one.
                val level = game.clueTier(entry)
                Text(
                    "●".repeat(level + 1) + "○".repeat(entry.clueLevels.lastIndex - level),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onEase) { Text(if (easeNeedsAd) "Asan ▶" else "Asan") }
            }
        }
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

private val TILE = 46.dp

@Composable
private fun LetterTile(tile: Tile, hideWhenUsed: Boolean = false, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (tile.used) (if (hideWhenUsed) 0.4f else 0.8f) else 1f, tween(180), label = "tileScale")
    val alpha by animateFloatAsState(if (tile.used) (if (hideWhenUsed) 0f else 0.25f) else 1f, tween(180), label = "tileAlpha")
    Surface(
        modifier = Modifier.size(TILE).graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
            .clickable(enabled = !tile.used, onClick = onClick)
            .semantics { contentDescription = "Hərf ${tile.letter}" },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = if (tile.used) 0.dp else 3.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                tile.letter.toString(),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

/**
 * Letter columns: two columns of scattered letters on the left, two on the right,
 * the word being built in the middle. When every slot is filled the word is checked.
 */
@Composable
private fun ColumnsInput(
    letters: List<Char?>,
    draft: List<Int>,
    slots: Int,
    word: List<Pair<Char?, Boolean>>,
    feedback: WordFeedback?,
    onPick: (Int) -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(feedback) {
        if (feedback == WordFeedback.WRONG) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    val shake = remember { Animatable(0f) }
    LaunchedEffect(feedback) {
        if (feedback == WordFeedback.WRONG) {
            for (x in listOf(14f, -12f, 9f, -6f, 3f, 0f)) shake.animateTo(x, tween(45))
        }
    }
    Row(
        Modifier.fillMaxWidth().height(212.dp).padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (c in 0..1) LetterColumn(WordBuilder.column(letters, c), draft, onPick)
        Column(
            Modifier.weight(1f).fillMaxHeight().padding(horizontal = 6.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val status = when {
                slots == 0 -> "Söz tapıldı ✓"
                feedback == WordFeedback.WRONG -> "Yox, yenidən cəhd et"
                feedback == WordFeedback.RIGHT -> "Düzdür! ✓"
                else -> "Hərfləri növbə ilə seç"
            }
            Text(
                status,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                color = when (feedback) {
                    WordFeedback.WRONG -> MaterialTheme.colorScheme.error
                    WordFeedback.RIGHT -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val n = word.size
                val slot = if (n == 0) 0.dp else min(34.dp, (maxWidth - 3.dp * (n - 1)) / n)
                Row(
                    Modifier.graphicsLayer { translationX = shake.value },
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    word.forEach { (ch, fixed) ->
                        Box(
                            Modifier.size(slot, slot * 1.25f)
                                .background(
                                    when {
                                        fixed -> LocalGridColors.current.solved
                                        feedback == WordFeedback.WRONG -> MaterialTheme.colorScheme.errorContainer
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    },
                                    RoundedCornerShape(6.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                ch?.toString() ?: "",
                                fontSize = (slot.value * 0.6f).sp,
                                fontWeight = FontWeight.Bold,
                                color = if (fixed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallAction("⌫", "Son hərfi sil", draft.isNotEmpty(), onUndo)
                SmallAction("✕", "Təmizlə", draft.isNotEmpty(), onClear)
            }
        }
        for (c in 2..3) LetterColumn(WordBuilder.column(letters, c), draft, onPick)
    }
}

@Composable
private fun LetterColumn(items: List<IndexedValue<Char?>>, draft: List<Int>, onPick: (Int) -> Unit) {
    val haptic = LocalHapticFeedback.current
    Column(
        Modifier.padding(horizontal = 3.dp).fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        items.forEach { (index, ch) ->
            if (ch == null) { Spacer(Modifier.size(TILE)); return@forEach }
            key(index, ch) {
                LetterTile(Tile(ch, used = index in draft), hideWhenUsed = true) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onPick(index)
                }
            }
        }
    }
}

@Composable
private fun SmallAction(label: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(48.dp).clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description },
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 1f else 0.4f),
    ) {
        Box(contentAlignment = Alignment.Center) { Text(label, fontSize = 20.sp) }
    }
}

/** The level's key word: slots fill in as its highlighted cells are solved; tap to guess early. */
@Composable
private fun KeywordStrip(letters: List<Char?>, known: Boolean, onGuess: () -> Unit) {
    val colors = LocalGridColors.current
    Row(
        Modifier.fillMaxWidth().clickable(enabled = !known, onClick = onGuess).padding(vertical = 4.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Açar söz", style = MaterialTheme.typography.labelLarge, color = colors.keyword)
        Spacer(Modifier.width(6.dp))
        letters.forEachIndexed { i, ch ->
            Box(
                Modifier.size(28.dp).border(2.dp, colors.keyword, RoundedCornerShape(50)),
                contentAlignment = Alignment.Center,
            ) {
                Text(ch?.toString() ?: "${i + 1}", fontSize = if (ch != null) 15.sp else 10.sp,
                    fontWeight = if (ch != null) FontWeight.Bold else FontWeight.Normal,
                    color = if (ch != null) MaterialTheme.colorScheme.onSurface else colors.keyword)
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            if (known) "✓" else "Təxmin et ›",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun KeywordDialog(vm: GameViewModel) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = vm::closeKeywordDialog,
        title = { Text("Açar sözü təxmin et") },
        text = {
            Column {
                Text("Düz cavab: +$KEYWORD_BONUS_HINTS pulsuz ipucu.")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    isError = vm.keywordWrong,
                    supportingText = { if (vm.keywordWrong) Text("Yox, başqa söz") },
                )
            }
        },
        confirmButton = { Button(onClick = { vm.guessKeyword(text) }, enabled = text.isNotBlank()) { Text("Yoxla") } },
        dismissButton = { TextButton(onClick = vm::closeKeywordDialog) { Text("Bağla") } },
    )
}
