package com.aaideas.krossvord.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.repeatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.border
import androidx.compose.ui.draw.drawWithContent
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
import androidx.compose.ui.platform.LocalDensity
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
    // Short screens: smaller tiles and tighter spacing, so the grid cells get bigger.
    val compact = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < COMPACT_HEIGHT_DP
    val context = androidx.compose.ui.platform.LocalContext.current
    val lowEnd = remember { isLowEndPhone(context) }
    androidx.compose.runtime.CompositionLocalProvider(LocalTile provides if (compact) 40.dp else 46.dp, LocalLowEnd provides lowEnd) {
        PuzzleContent(vm, isPremium, compact)
    }
}

@Composable
private fun PuzzleContent(vm: GameViewModel, isPremium: Boolean, compact: Boolean) {
    // Count play time only while the puzzle is on screen and the app is in front.
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, ev ->
            if (ev == androidx.lifecycle.Lifecycle.Event.ON_START) vm.puzzleShown()
            if (ev == androidx.lifecycle.Lifecycle.Event.ON_STOP) vm.puzzleHidden()
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs); vm.puzzleHidden() }
    }
    val game = vm.game ?: return
    val columns = vm.inputMode == InputMode.COLUMNS
    val letters = if (columns) vm.columnLetters() else emptyList()
    val draft = if (columns) vm.draft() else emptyList()
    // Letters being built in the columns mode are previewed in the grid before the check.
    val preview: Map<Cell, Char> = game.activeEntry?.let { e ->
        game.openCells(e).zip(draft.mapNotNull { letters.getOrNull(it) }).toMap()
    }.orEmpty()
    Column(Modifier.fillMaxSize().padding(horizontal = if (compact) 4.dp else 8.dp, vertical = if (compact) 2.dp else 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = vm::back) { Text("← Səviyyələr") }
            var confirmRestart by remember { mutableStateOf(false) }
            TextButton(onClick = { confirmRestart = true }, Modifier.semantics { contentDescription = "Səviyyəni yenidən başla" }) { Text("↺") }
            if (confirmRestart) AlertDialog(
                onDismissRequest = { confirmRestart = false },
                title = { Text("Səviyyəni yenidən başla?") },
                text = { Text("Bu səviyyədəki hərflər, canlar və açılmış ipucları silinəcək.") },
                confirmButton = { Button(onClick = { confirmRestart = false; vm.restartLevel() }) { Text("Yenidən başla") } },
                dismissButton = { TextButton(onClick = { confirmRestart = false }) { Text("İmtina") } },
            )
            Spacer(Modifier.weight(1f))
            LivesBadge(vm, isPremium, Modifier.padding(end = 10.dp))
            Text(
                "${vm.currentLevelTitle} · ${game.solvedCount}/${game.board.level.entries.size}",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        if (game.keyword != null) KeywordStrip(game.keywordLetters, game.isKeywordKnown, vm::openKeywordDialog)
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val cell = min(maxWidth / game.board.cols, maxHeight / game.board.rows)
            Grid(
                game, cell, preview, vm.feedback, vm::isClueShown, vm::tapCell, vm::tapClueOf,
                tutorialEntry = if (vm.tutorialStep == TUTORIAL_TAP) game.activeEntry else null,
            )
        }
        val hintText = when {
            isPremium -> "Hərf aç"
            vm.freeHints > 0 -> "Hərf aç (${vm.freeHints} pulsuz)"
            else -> "Hərf aç ▶ reklam"
        }
        // Small phones, letter columns: shuffle and hint move between the columns, so the grid gets that row.
        val actionsInColumns = compact && columns
        if (!actionsInColumns) Row(Modifier.fillMaxWidth().padding(vertical = if (compact) 2.dp else 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        // Clues live in the grid cells now; below the grid only the tutorial, or a song line too long for a cell.
        // Big text: switched on in settings, or on its own when the phone uses a large font.
        val active = game.activeEntry
        val bigText = vm.bigClues || LocalDensity.current.fontScale >= BIG_FONT_SCALE
        when {
            vm.tutorialStep != TUTORIAL_DONE -> TutorialCallout(vm.tutorialStep, vm::finishTutorial)
            active != null && vm.isClueShown(active) && (bigText || game.clueText(active).length > CELL_CLUE_MAX) ->
                LongClueLine(active, game.clueText(active), big = bigText)
        }
        val entry = game.activeEntry
        when {
            entry == null || vm.inputMode == InputMode.KEYBOARD -> AzKeyboard(onLetter = vm::type, onBackspace = vm::backspace)
            vm.inputMode == InputMode.TILES ->
                TileInput(LetterTiles.tilesFor(game, entry, vm.tileShuffle), vm::type, vm::backspace, vm::shuffleTiles)
            else -> ColumnsInput(
                letters = letters,
                draft = draft,
                feedback = vm.feedback,
                onPick = vm::pickLetter,
                onUnpick = vm::unpickLetter,
                onUndo = vm::unpickLast,
                onClear = vm::clearDraft,
                extra = if (actionsInColumns) ColumnExtras(vm::shuffleTiles, vm::hint, if (isPremium || vm.freeHints > 0) "Hərf aç" else "Hərf ▶") else null,
            )
        }
    }
    if (vm.showKeywordDialog) KeywordDialog(vm)
    vm.clueCard?.takeIf { it in game.board.level.entries }?.let { e ->
        ClueCard(game, e, needsAd = vm.easeNeedsAd(), onEase = vm::easeActiveClue, onClose = vm::closeClueCard)
    }
    // Lives come back with time: keep the countdown moving while some are missing.
    val missing = vm.secondsToNextLife != null
    LaunchedEffect(missing) {
        while (missing) { kotlinx.coroutines.delay(1000); vm.tickLives() }
    }
    if (vm.outOfLives && !vm.showCompletion && vm.ads.pending == null) {
        val dailyOpen = !vm.daily.guess.finished
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Canlar bitdi") },
            text = {
                Text(
                    "Növbəti can ${clock(vm.secondsToNextLife ?: 0)} sonra özü gələcək." +
                        if (dailyOpen) "\nGünün sözünü tapsan, canlar dərhal dolacaq." else ""
                )
            },
            confirmButton = {
                Button(onClick = vm::refillLives) { Text(if (isPremium) "Canları doldur" else "Canları doldur ▶ reklam") }
            },
            dismissButton = {
                if (dailyOpen) TextButton(onClick = { vm.open(Screen.WORD_OF_DAY) }) { Text("Günün sözü") }
                else TextButton(onClick = vm::back) { Text("Gözləyəcəm") }
            },
        )
    }
    if (vm.showCompletion) {
        AlertDialog(
            onDismissRequest = vm::dismissCompletion,
            title = { Text("Təbriklər!") },
            text = {
                Text(
                    "${vm.currentLevelTitle} həll olundu." +
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
    onClueTap: (Entry) -> Unit,
    tutorialEntry: Entry?,
) {
    val colors = LocalGridColors.current
    val active = game.activeEntry
    val activeCells = active?.cells?.toSet().orEmpty()
    // Where each word starts, and which way it runs: drawn as a clear arrow in that cell.
    val starts = remember(game.board) {
        game.board.level.entries.groupBy({ it.cells.first() }, { it.direction })
    }
    // When a word is picked, a light runs along it from the clue, showing which way to write.
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(active?.id) {
        val n = active?.cells?.size ?: return@LaunchedEffect
        sweep.snapTo(0f)
        sweep.animateTo(n + 1f, tween(durationMillis = 130 * (n + 1), easing = androidx.compose.animation.core.LinearEasing))
    }
    val arrowColor = MaterialTheme.colorScheme.primary
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
                            mod.clickable { onTap(cell) }
                                .wordArrows(starts[cell].orEmpty(), arrowColor)
                                .then(
                                    if (active == null || cell !in activeCells) Modifier
                                    else Modifier.sweepGlow(active.cells.indexOf(cell), active.direction, arrowColor) { sweep.value },
                                ),
                        )
                    } else {
                        val clues = game.board.cluesAt[cell]
                        if (clues == null) Box(mod.background(colors.emptyCell))
                        else ClueCell(clues, active, game, clueShown, onClueTap, tutorialEntry, mod.background(colors.clueCell))
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
                    preview != null -> colors.letter
                    cell in game.revealed -> colors.revealed
                    else -> colors.letter
                },
            )
        }
    }
}

/** A filled triangle on the edge a word enters from, pointing the way the word runs. */
private fun Modifier.wordArrows(dirs: List<Direction>, color: androidx.compose.ui.graphics.Color) =
    if (dirs.isEmpty()) this else drawWithContent {
        drawContent()
        val s = size.minDimension * 0.17f
        dirs.forEach { d ->
            val path = androidx.compose.ui.graphics.Path().apply {
                if (d == Direction.ACROSS) {
                    moveTo(0f, size.height / 2 - s); lineTo(s * 1.2f, size.height / 2); lineTo(0f, size.height / 2 + s)
                } else {
                    moveTo(size.width / 2 - s, 0f); lineTo(size.width / 2, s * 1.2f); lineTo(size.width / 2 + s, 0f)
                }
                close()
            }
            drawPath(path, color)
        }
    }

/**
 * The light that runs along a picked word: [index] is the cell's place in the word, [progress]
 * goes from 0 to the word length. Read only while drawing, so the grid doesn't recompose per frame.
 */
private fun Modifier.sweepGlow(index: Int, dir: Direction, color: androidx.compose.ui.graphics.Color, progress: () -> Float) =
    drawWithContent {
        drawContent()
        val glow = (1f - kotlin.math.abs(progress() - (index + 0.5f))).coerceIn(0f, 1f)
        if (glow <= 0f) return@drawWithContent
        drawRect(color.copy(alpha = 0.35f * glow))
        // A chevron in the lit cell, pointing the way the word runs.
        val s = size.minDimension * 0.22f
        val cx = size.width / 2; val cy = size.height / 2
        val path = androidx.compose.ui.graphics.Path().apply {
            if (dir == Direction.ACROSS) { moveTo(cx - s / 2, cy - s); lineTo(cx + s / 2, cy); lineTo(cx - s / 2, cy + s) }
            else { moveTo(cx - s, cy - s / 2); lineTo(cx, cy + s / 2); lineTo(cx + s, cy - s / 2) }
        }
        drawPath(path, color.copy(alpha = glow), style = androidx.compose.ui.graphics.drawscope.Stroke(width = s * 0.35f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
    }

/** Longest clue that still fits in a grid cell; longer ones (song lines) show ♪ there and in full below. */
const val CELL_CLUE_MAX = 16

fun cellClue(text: String): String = if (text.length > CELL_CLUE_MAX) "♪ …" else text

@Composable
private fun ClueCell(
    clues: List<Entry>,
    active: Entry?,
    game: GameState,
    shown: (Entry) -> Boolean,
    onClueTap: (Entry) -> Unit,
    tutorialEntry: Entry?,
    modifier: Modifier,
) {
    val colors = LocalGridColors.current
    Column(modifier.padding(1.dp)) {
        // Across clue on top, down clue below: the usual Scandinavian order. Each half is its own button.
        clues.sortedBy { it.direction }.forEach { e ->
            val isActive = e == active
            val text = (if (shown(e)) game.clueText(e) else "?")
            // A finite pulse (an endless one would also keep UI tests from ever going idle).
            val pulse = if (e == tutorialEntry) {
                val a = remember { Animatable(0.35f) }
                LaunchedEffect(e) { a.animateTo(1f, repeatable(7, tween(600), RepeatMode.Reverse)) }
                a.value
            } else 0f
            Box(
                Modifier.fillMaxWidth().weight(1f)
                    .background(if (isActive) colors.activeWord else colors.clueCell)
                    .then(if (e == tutorialEntry) Modifier.border(3.dp, MaterialTheme.colorScheme.primary.copy(alpha = pulse)) else Modifier)
                    .clickable { onClueTap(e) }
                    .semantics { contentDescription = "İpucu: $text" },
                contentAlignment = Alignment.Center,
            ) {
                val showDots = isActive && shown(e) && e.clueLevels.size > 1
                FitClueText(
                    cellClue(text) + if (e.direction == Direction.ACROSS) " →" else " ↓",
                    colors.clueText,
                    // The tutorial's 3dp frame is drawn over the cell, so keep the text inside it.
                    Modifier.fillMaxSize()
                        .padding(if (e == tutorialEntry) 3.dp else 0.dp)
                        .padding(start = 1.dp, end = 1.dp, bottom = if (showDots) 5.dp else 0.dp),
                )
                // Which clue level is showing; only on the active word, where a tap eases it.
                if (showDots) {
                    val level = game.clueTier(e)
                    BasicText(
                        "●".repeat(level + 1) + "○".repeat(e.clueLevels.lastIndex - level),
                        style = TextStyle(color = colors.clueText, fontSize = 5.sp, lineHeight = 5.sp),
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = if (e == tutorialEntry) 3.dp else 0.dp),
                    )
                }
            }
        }
    }
}

/** First-run hint under the grid: tap the highlighted clue cell, then what happened. */
@Composable
private fun TutorialCallout(step: Int, onDone: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)) {
            Text(
                if (step == TUTORIAL_TAP) "☝ Haşiyəli ipucu xanasına toxun: ipucu böyük açılacaq."
                else "Əla! Kartda «Daha asan ipucu» var: ikincisi pulsuzdur, üçüncüsü reklamdan sonra. Açılmış ipucuları sürüşdürüb vərəqlə.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDone) { Text(if (step == TUTORIAL_TAP) "Keç" else "Aydındır") }
        }
    }
}

/** Phone font scale from which the clue also shows in large text under the grid. */
const val BIG_FONT_SCALE = 1.3f

/** The selected clue in full under the grid: song lines that don't fit a cell, or big-text mode. */
@Composable
private fun LongClueLine(entry: Entry, text: String, big: Boolean) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
    ) {
        Text(
            (if (text.length > CELL_CLUE_MAX) "♪ " else if (entry.direction == Direction.ACROSS) "→ " else "↓ ") +
                "$text  (${entry.letters.size} hərf)",
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = if (big) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleSmall,
        )
    }
}

/** Azerbaijani on-screen keyboard: all 32 letters, QÜERTY-like order. */
private val KEY_ROWS = listOf("QÜERTYUİOPÖ", "ASDFGHJKLIƏ", "ZXCVBNMÇŞĞ")

@Composable
internal fun AzKeyboard(onLetter: (Char) -> Unit, onBackspace: () -> Unit) {
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
        modifier = modifier.height(LocalTile.current + 2.dp).clickable(onClick = onClick),
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

/** Letter tile size; smaller on short screens so the grid keeps more room. */
private val LocalTile = androidx.compose.runtime.staticCompositionLocalOf { 46.dp }

/** True on weak phones: tiles drop their shadows, which are costly to draw many times per frame. */
private val LocalLowEnd = androidx.compose.runtime.staticCompositionLocalOf { false }

/** Weak phone: the system says low RAM, or 4 GB or less of memory (e.g. a Redmi 8). */
fun isLowEndPhone(context: android.content.Context): Boolean {
    val am = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as? android.app.ActivityManager ?: return false
    val info = android.app.ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
    return am.isLowRamDevice || info.totalMem <= 4_500_000_000L
}

/** Screens shorter than this (small HD+ phones such as a Redmi 8) get the compact layout. */
const val COMPACT_HEIGHT_DP = 740

@Composable
private fun LetterTile(tile: Tile, hideWhenUsed: Boolean = false, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (tile.used) (if (hideWhenUsed) 0.4f else 0.8f) else 1f, tween(180), label = "tileScale")
    val alpha by animateFloatAsState(if (tile.used) (if (hideWhenUsed) 0f else 0.25f) else 1f, tween(180), label = "tileAlpha")
    Surface(
        modifier = Modifier.size(LocalTile.current).graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
            .clickable(enabled = !tile.used, onClick = onClick)
            .semantics { contentDescription = "Hərf ${tile.letter}" },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = if (tile.used || LocalLowEnd.current) 0.dp else 3.dp,
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
 * Letter columns: two columns of letters on the left, two on the right. Tapping a letter puts it
 * straight into the next free cell of the word in the grid; the tile lights up and shows its order
 * (1, 2, 3...). Tapping a lit tile takes that letter back. A full word is checked at once.
 */
@Composable
private fun ColumnsInput(
    letters: List<Char?>,
    draft: List<Int>,
    feedback: WordFeedback?,
    onPick: (Int) -> Unit,
    onUnpick: (Int) -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    extra: ColumnExtras? = null,
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
        Modifier.fillMaxWidth().height(LocalTile.current * 4 + 28.dp).padding(top = 4.dp).graphicsLayer { translationX = shake.value },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (c in 0..1) LetterColumn(WordBuilder.column(letters, c), draft, onPick, onUnpick)
        Column(
            Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (feedback != null) {
                Text(
                    if (feedback == WordFeedback.WRONG) "✗" else "✓",
                    fontSize = if (extra != null) 20.sp else 28.sp,
                    lineHeight = if (extra != null) 20.sp else 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (feedback == WordFeedback.WRONG) MaterialTheme.colorScheme.error else LocalGridColors.current.keyword,
                )
            }
            if (extra == null) {
                SmallAction("⌫", "Son hərfi sil", draft.isNotEmpty(), onUndo)
                SmallAction("✕", "Təmizlə", draft.isNotEmpty(), onClear)
            } else {
                // 2 x 2 buttons with captions, so they are clear without the row above.
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LabeledAction("⌫", "Sil", draft.isNotEmpty(), onUndo)
                    LabeledAction("✕", "Təmizlə", draft.isNotEmpty(), onClear)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LabeledAction("⟳", "Qarışdır", true, extra.onShuffle)
                    LabeledAction("💡", extra.hintLabel, true, extra.onHint, highlight = true)
                }
            }
        }
        for (c in 2..3) LetterColumn(WordBuilder.column(letters, c), draft, onPick, onUnpick)
    }
}

@Composable
private fun LetterColumn(
    items: List<IndexedValue<Char?>>,
    draft: List<Int>,
    onPick: (Int) -> Unit,
    onUnpick: (Int) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Column(
        Modifier.padding(horizontal = 3.dp).fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(if (LocalTile.current < 46.dp) 4.dp else 6.dp, Alignment.CenterVertically),
    ) {
        items.forEach { (index, ch) ->
            if (ch == null) { Spacer(Modifier.size(LocalTile.current)); return@forEach }
            val order = draft.indexOf(index)
            key(index, ch) {
                PickTile(ch, if (order >= 0) order + 1 else null) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (order >= 0) onUnpick(index) else onPick(index)
                }
            }
        }
    }
}

/** A letter tile that lights up when picked and shows its pick order in the corner. */
@Composable
private fun PickTile(letter: Char, order: Int?, onClick: () -> Unit) {
    val picked = order != null
    val scale by animateFloatAsState(if (picked) 0.92f else 1f, tween(150), label = "pickScale")
    Surface(
        modifier = Modifier.size(LocalTile.current).graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Hərf $letter" + if (picked) ", seçilib $order" else "" },
        shape = RoundedCornerShape(12.dp),
        color = if (picked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = if (picked || LocalLowEnd.current) 0.dp else 3.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                letter.toString(),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = if (picked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (order != null) {
                Text(
                    "$order",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 5.dp),
                )
            }
        }
    }
}

/** Shuffle and hint, shown between the letter columns on small phones. */
class ColumnExtras(val onShuffle: () -> Unit, val onHint: () -> Unit, val hintLabel: String)

@Composable
private fun LabeledAction(icon: String, label: String, enabled: Boolean, onClick: () -> Unit, highlight: Boolean = false) {
    Column(
        Modifier.width(64.dp).clickable(enabled = enabled, onClick = onClick).semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(20.dp),
            color = (if (highlight) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                .copy(alpha = if (enabled) 1f else 0.4f),
        ) { Box(contentAlignment = Alignment.Center) { Text(icon, fontSize = 18.sp) } }
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
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
        Modifier.fillMaxWidth().clickable(enabled = !known, onClick = onGuess).padding(vertical = 2.dp, horizontal = 8.dp),
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

/** 125 -> "2:05". */
fun clock(seconds: Long): String = "${seconds / 60}:${"%02d".format(seconds % 60)}"

/** Hearts for the shared lives, extra ones as "+N", and the countdown to the next life. */
@Composable
private fun LivesBadge(vm: GameViewModel, isPremium: Boolean, modifier: Modifier = Modifier) {
    val n = vm.lives
    val hearts = when {
        isPremium -> "♥ ∞"
        n > com.aaideas.krossvord.model.Lives.CAP -> "♥".repeat(com.aaideas.krossvord.model.Lives.CAP) + "+${n - com.aaideas.krossvord.model.Lives.CAP}"
        else -> "♥".repeat(n) + "♡".repeat(com.aaideas.krossvord.model.Lives.CAP - n)
    }
    Column(modifier.semantics { contentDescription = "Can: ${if (isPremium) "limitsiz" else n}" }, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(hearts, color = MaterialTheme.colorScheme.error, fontSize = 18.sp, lineHeight = 18.sp)
        val next = vm.secondsToNextLife
        if (!isPremium && next != null) {
            Text("+1 ${clock(next)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
