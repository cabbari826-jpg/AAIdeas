package com.aaideas.krossvord.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.repeatable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
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
        Box(Modifier.fillMaxSize()) {
            PuzzleContent(vm, isPremium, compact)
            TutorialLayer(vm)
        }
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
        val glass = LocalGlass.current
        var confirmRestart by remember { mutableStateOf(false) }
        if (confirmRestart) AlertDialog(
            onDismissRequest = { confirmRestart = false },
            title = { Text("Səviyyəni yenidən başla?") },
            text = { Text("Bu səviyyədəki hərflər, canlar və açılmış ipucları silinəcək.") },
            confirmButton = { Button(onClick = { confirmRestart = false; vm.restartLevel() }) { Text("Yenidən başla") } },
            dismissButton = { TextButton(onClick = { confirmRestart = false }) { Text("İmtina") } },
        )
        if (glass) GlassTopBar(vm, isPremium) { confirmRestart = true }
        else Row(verticalAlignment = Alignment.CenterVertically) {
            SquareButton(Glyph.BACK, "Geri", vm::back, Modifier.tutorialAnchor(vm, TutorialSpot.BACK))
            Spacer(Modifier.width(8.dp))
            SquareButton(Glyph.RESTART, "Səviyyəni yenidən başla", { confirmRestart = true }, Modifier.tutorialAnchor(vm, TutorialSpot.RESTART))
            Spacer(Modifier.weight(1f))
            LivesBadge(vm, isPremium, Modifier.padding(end = 6.dp).tutorialAnchor(vm, TutorialSpot.LIVES))
        }
        if (game.keyword != null) KeywordStrip(game.keywordLetters, game.isKeywordKnown, vm::openKeywordDialog, Modifier.tutorialAnchor(vm, TutorialSpot.KEYWORD))
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            // Şüşə: the grid sits in a glass card with a small inner margin.
            val pad = if (glass) GLASS_GRID_PAD * 2 else 0.dp
            val cell = min((maxWidth - pad) / game.board.cols, (maxHeight - pad) / game.board.rows)
            Grid(
                game, cell, preview, vm.feedback, vm::isClueShown, vm::tapCell, vm::tapClueOf,
                tutorialEntry = if (vm.tutorialStep == TUTORIAL_COACH && COACH_STEPS[vm.coachIndex].first == TutorialSpot.CLUE) game.activeEntry else null,
                onSpot = if (vm.tutorialStep == TUTORIAL_COACH) { spot, r -> vm.tutorialRects[spot] = r } else null,
            )
        }
        val hintText = when {
            isPremium -> "Hərf aç"
            vm.freeHints > 0 -> "Hərf aç (${vm.freeHints} pulsuz)"
            else -> "Hərf aç ▶ reklam"
        }
        // Small phones, letter columns: shuffle and hint move between the columns, so the grid gets that row.
        // Şüşə keeps them in the row: its letter tiles are laid out in rows, not columns.
        val actionsInColumns = compact && columns && !glass
        if (!actionsInColumns) Row(Modifier.fillMaxWidth().padding(vertical = if (compact) 2.dp else 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // A fixed height makes the button's fill take the whole touch area, so it lines up with the glass rim.
            val glassBtn = if (glass) Modifier.height(48.dp).glassy(CircleShape) else Modifier
            val outlined = if (glass) ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer) else ButtonDefaults.outlinedButtonColors()
            val border = if (glass) null else ButtonDefaults.outlinedButtonBorder(true)
            if (columns) OutlinedButton(onClick = vm::shuffleTiles, modifier = Modifier.weight(1f).then(glassBtn), colors = outlined, border = border) { Text("⟳ Qarışdır") }
            else OutlinedButton(onClick = vm::clearWord, modifier = Modifier.weight(1f).then(glassBtn), colors = outlined, border = border) { Text("Sözü sil") }
            Button(onClick = vm::hint, modifier = Modifier.weight(1f).then(glassBtn).tutorialAnchor(vm, TutorialSpot.HINT)) {
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
            active != null && vm.isClueShown(active) && (glass || bigText || game.clueText(active).length > CELL_CLUE_MAX) ->
                LongClueLine(active, game.clueText(active), big = bigText)
        }
        val entry = game.activeEntry
        Box(Modifier.tutorialAnchor(vm, TutorialSpot.LETTERS)) {
        when {
            entry == null || vm.inputMode == InputMode.KEYBOARD -> Column {
                AzKeyboard(onLetter = vm::type, onBackspace = vm::backspace, keyHeight = if (compact) 52.dp else 60.dp)
                Spacer(Modifier.height(if (compact) 6.dp else 14.dp))
            }
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
    }
    if (vm.showKeywordDialog) KeywordDialog(vm)
    vm.clueCard?.takeIf { it in game.board.level.entries }?.let { e ->
        androidx.compose.runtime.LaunchedEffect(Unit) { vm.voice.warmUp() }
        ClueCard(
            game, e, needsAd = vm.easeNeedsAd(), onEase = vm::easeActiveClue, onClose = vm::closeClueCard,
            onSpeak = if (vm.voice.available) vm.voice::speak else null,
        )
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
    val result = vm.result
    if (vm.showCompletion && result != null) {
        LevelResultDialog(vm.currentLevelTitle, result, vm::nextLevel, vm::dismissCompletion)
    } else if (vm.showCompletion) {
        AlertDialog(
            onDismissRequest = vm::dismissCompletion,
            title = { Text("Təbriklər!") },
            text = { Text("${vm.currentLevelTitle} həll olundu.") },
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
    onSpot: ((TutorialSpot, androidx.compose.ui.geometry.Rect) -> Unit)? = null,
) {
    val colors = LocalGridColors.current
    val active = game.activeEntry
    val density = LocalDensity.current
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
    // While coaching: tell where the active word and its clue cell are on screen.
    val spots = if (onSpot == null || active == null) Modifier else Modifier.onGloballyPositioned { co ->
        val o = co.boundsInRoot().topLeft
        val px = with(density) { cellSize.toPx() }
        fun rectOf(cells: List<Cell>) = androidx.compose.ui.geometry.Rect(
            o.x + cells.minOf { it.col } * px, o.y + cells.minOf { it.row } * px,
            o.x + (cells.maxOf { it.col } + 1) * px, o.y + (cells.maxOf { it.row } + 1) * px,
        )
        onSpot(TutorialSpot.WORD, rectOf(active.cells))
        game.board.cluesAt.entries.firstOrNull { active in it.value }?.let { onSpot(TutorialSpot.CLUE, rectOf(listOf(it.key))) }
    }
    val glass = LocalGlass.current
    // Şüşə: the grid is a glass panel of separate rounded cells (as in the mockup) instead of ruled lines.
    val card = RoundedCornerShape(20.dp)
    Column(
        (if (glass) Modifier.clip(card).background(MaterialTheme.colorScheme.surfaceContainer).glassy(card).padding(GLASS_GRID_PAD)
        else Modifier.border(1.dp, colors.line)).then(spots),
    ) {
        for (r in 0 until game.board.rows) {
            Row {
                for (c in 0 until game.board.cols) {
                    val cell = Cell(r, c)
                    val mod = if (glass) Modifier.size(cellSize).padding(1.5.dp).clip(RoundedCornerShape(cellSize * 0.16f))
                        else Modifier.size(cellSize).border(0.5.dp, colors.line)
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
                        if (clues == null) Box(mod.blockCell(colors))
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
                // Şüşə cells are see-through: a second coat of the cell colour would show as a box.
                modifier = Modifier.align(Alignment.TopStart).background(if (LocalGlass.current) Color.Transparent else bg).padding(start = 2.dp, end = 1.dp),
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

/** Phone font scale from which the clue also shows in large text under the grid. */
const val BIG_FONT_SCALE = 1.3f

/** Text scale in large mode; at least [BIG_FONT_SCALE], so the big clue line shows too. */
const val LARGE_FONT_SCALE = 1.3f

/** The selected clue in full under the grid: song lines that don't fit a cell, or big-text mode. */
@Composable
private fun LongClueLine(entry: Entry, text: String, big: Boolean) {
    val glass = LocalGlass.current
    // Şüşə: a glass pill, "Süfrənin şahı · 5 hərf", as in the mockup.
    val shape = if (glass) CircleShape else RoundedCornerShape(10.dp)
    Surface(
        color = if (glass) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.secondaryContainer,
        shape = shape,
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = if (glass) 6.dp else 0.dp).glassy(shape),
    ) {
        Text(
            if (glass) (if (text.length > CELL_CLUE_MAX) "♪ " else "") + "$text · ${entry.letters.size} hərf"
            else (if (text.length > CELL_CLUE_MAX) "♪ " else if (entry.direction == Direction.ACROSS) "→ " else "↓ ") +
                "$text  (${entry.letters.size} hərf)",
            Modifier.padding(horizontal = if (glass) 18.dp else 12.dp, vertical = if (glass) 7.dp else 8.dp),
            style = if (big) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleSmall,
            fontWeight = if (glass) FontWeight.Bold else null,
        )
    }
}

/** Azerbaijani on-screen keyboard: all 32 letters, QÜERTY-like order. */
private val KEY_ROWS = listOf("QÜERTYUİOPÖ", "ASDFGHJKLIƏ", "ZXCVBNMÇŞĞ")

@Composable
internal fun AzKeyboard(onLetter: (Char) -> Unit, onBackspace: () -> Unit, keyHeight: Dp = LocalTile.current + 8.dp) {
    check(KEY_ROWS.joinToString("").toSet() == AzText.ALPHABET.toSet())
    val haptic = LocalHapticFeedback.current
    // Tall keys with big letters: easier to hit for older players.
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
        KEY_ROWS.forEachIndexed { i, row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                row.forEach { ch ->
                    Key(ch.toString(), Modifier.weight(1f), keyHeight) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onLetter(ch)
                    }
                }
                if (i == KEY_ROWS.lastIndex) Key("⌫", Modifier.weight(1.4f), keyHeight, onBackspace)
            }
        }
    }
}

@Composable
private fun Key(label: String, modifier: Modifier, height: Dp, onClick: () -> Unit) {
    val shape = RoundedCornerShape(if (LocalGlass.current) 12.dp else 8.dp)
    Surface(
        modifier = modifier.height(height).glassy(shape).clickable(onClick = onClick),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Box(contentAlignment = Alignment.Center) { Text(label, fontSize = 22.sp, fontWeight = FontWeight.SemiBold) }
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
            .glassy(RoundedCornerShape(12.dp))
            .clickable(enabled = !tile.used, onClick = onClick)
            .semantics { contentDescription = "Hərf ${tile.letter}" },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = if (tile.used || LocalLowEnd.current || LocalGlass.current) 0.dp else 3.dp,
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
    if (LocalGlass.current) {
        GlassLetterRows(letters, draft, onPick, onUnpick, onUndo, onClear, Modifier.graphicsLayer { translationX = shake.value })
        return
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
            .glassy(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Hərf $letter" + if (picked) ", seçilib $order" else "" },
        shape = RoundedCornerShape(12.dp),
        color = if (picked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
        shadowElevation = if (picked || LocalLowEnd.current || LocalGlass.current) 0.dp else 3.dp,
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
            modifier = Modifier.size(40.dp).glassy(RoundedCornerShape(20.dp)),
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
        modifier = Modifier.size(48.dp).glassy(RoundedCornerShape(24.dp)).clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description },
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 1f else 0.4f),
    ) {
        Box(contentAlignment = Alignment.Center) { Text(label, fontSize = 20.sp) }
    }
}

/** The level's key word: slots fill in as its highlighted cells are solved; tap to guess early. */
@Composable
private fun KeywordStrip(letters: List<Char?>, known: Boolean, onGuess: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalGridColors.current
    // Centred as one group: label, circles, guess mark.
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
    Row(
        modifier.clickable(enabled = !known, onClick = onGuess).padding(vertical = 2.dp, horizontal = 8.dp),
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
        Spacer(Modifier.width(6.dp))
        Text(
            if (known) "✓" else "›",
            style = MaterialTheme.typography.titleLarge,
            color = colors.keyword,
        )
    }
    }
}

enum class Glyph { BACK, RESTART }

/** A big, easy-to-hit square button for the top bar; the icon is drawn, so it sits exactly in the middle. */
@Composable
private fun SquareButton(glyph: Glyph, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tint = MaterialTheme.colorScheme.onSecondaryContainer
    androidx.compose.material3.Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier.size(48.dp).glassy(RoundedCornerShape(14.dp)).semantics { contentDescription = description },
    ) {
        androidx.compose.foundation.Canvas(Modifier.padding(13.dp)) {
            val w = size.width; val h = size.height
            val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
            when (glyph) {
                Glyph.BACK -> drawPath(androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.64f, h * 0.12f); lineTo(w * 0.3f, h * 0.5f); lineTo(w * 0.64f, h * 0.88f)
                }, tint, style = stroke)
                Glyph.RESTART -> {
                    // A circle running clockwise from the right round to the top, arrowhead at the top.
                    drawArc(tint, startAngle = -30f, sweepAngle = 300f, useCenter = false,
                        topLeft = androidx.compose.ui.geometry.Offset(w * 0.1f, h * 0.1f),
                        size = androidx.compose.ui.geometry.Size(w * 0.8f, h * 0.8f), style = stroke)
                    val x = w * 0.5f; val y = h * 0.1f
                    drawPath(androidx.compose.ui.graphics.Path().apply {
                        moveTo(x - w * 0.1f, y - h * 0.16f); lineTo(x + w * 0.1f, y); lineTo(x - w * 0.1f, y + h * 0.16f)
                    }, tint, style = stroke)
                }
            }
        }
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

/** One heart with the number of lives inside; empty red outline at zero; countdown to the next life below. */
@Composable
private fun LivesBadge(vm: GameViewModel, isPremium: Boolean, modifier: Modifier = Modifier) {
    val n = vm.lives
    val red = Color(0xFFE53935)
    Column(modifier.semantics { contentDescription = "Can: ${if (isPremium) "limitsiz" else n}" }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
            val empty = !isPremium && n <= 0
            androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
                val w = size.width; val h = size.height
                // A classic, round-shouldered heart, symmetric left and right.
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.5f, h * 0.9f)
                    cubicTo(w * 0.5f, h * 0.9f, w * 0.04f, h * 0.62f, w * 0.04f, h * 0.34f)
                    cubicTo(w * 0.04f, h * 0.17f, w * 0.17f, h * 0.06f, w * 0.3f, h * 0.06f)
                    cubicTo(w * 0.4f, h * 0.06f, w * 0.47f, h * 0.12f, w * 0.5f, h * 0.2f)
                    cubicTo(w * 0.53f, h * 0.12f, w * 0.6f, h * 0.06f, w * 0.7f, h * 0.06f)
                    cubicTo(w * 0.83f, h * 0.06f, w * 0.96f, h * 0.17f, w * 0.96f, h * 0.34f)
                    cubicTo(w * 0.96f, h * 0.62f, w * 0.5f, h * 0.9f, w * 0.5f, h * 0.9f)
                    close()
                }
                if (empty) drawPath(path, red, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx()))
                else drawPath(path, red)
            }
            if (!empty) Text(if (isPremium) "∞" else "$n", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 3.dp))
        }
        val next = vm.secondsToNextLife
        if (!isPremium && next != null) {
            Text("+1 ${clock(next)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---- Şüşə (glass) layout, as in the Liquid Glass mockup. Klassik and the other themes never reach this. ----

/** Inner margin of the glass card around the grid. */
private val GLASS_GRID_PAD = 6.dp

/** The heart outline used by the lives badge and the glass top bar. */
private fun heartPath(w: Float, h: Float) = androidx.compose.ui.graphics.Path().apply {
    moveTo(w * 0.5f, h * 0.9f)
    cubicTo(w * 0.5f, h * 0.9f, w * 0.04f, h * 0.62f, w * 0.04f, h * 0.34f)
    cubicTo(w * 0.04f, h * 0.17f, w * 0.17f, h * 0.06f, w * 0.3f, h * 0.06f)
    cubicTo(w * 0.4f, h * 0.06f, w * 0.47f, h * 0.12f, w * 0.5f, h * 0.2f)
    cubicTo(w * 0.53f, h * 0.12f, w * 0.6f, h * 0.06f, w * 0.7f, h * 0.06f)
    cubicTo(w * 0.83f, h * 0.06f, w * 0.96f, h * 0.17f, w * 0.96f, h * 0.34f)
    cubicTo(w * 0.96f, h * 0.62f, w * 0.5f, h * 0.9f, w * 0.5f, h * 0.9f)
    close()
}

/** A drawn back arrow or restart circle, centred in its box. */
@Composable
private fun GlyphIcon(glyph: Glyph, tint: Color, modifier: Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val w = size.width; val h = size.height
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.6.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        when (glyph) {
            Glyph.BACK -> drawPath(androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.62f, h * 0.14f); lineTo(w * 0.3f, h * 0.5f); lineTo(w * 0.62f, h * 0.86f)
            }, tint, style = stroke)
            Glyph.RESTART -> {
                drawArc(tint, startAngle = -30f, sweepAngle = 300f, useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(w * 0.12f, h * 0.12f),
                    size = androidx.compose.ui.geometry.Size(w * 0.76f, h * 0.76f), style = stroke)
                val x = w * 0.5f; val y = h * 0.12f
                drawPath(androidx.compose.ui.graphics.Path().apply {
                    moveTo(x - w * 0.1f, y - h * 0.15f); lineTo(x + w * 0.1f, y); lineTo(x - w * 0.1f, y + h * 0.15f)
                }, tint, style = stroke)
            }
        }
    }
}

/** One glass pill across the top: "‹ Səviyyələr", restart, hearts, and the level number in its pack. */
@Composable
private fun GlassTopBar(vm: GameViewModel, isPremium: Boolean, onRestart: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ink = cs.onSurface
    Row(
        Modifier.fillMaxWidth().padding(bottom = 6.dp).clip(CircleShape).background(cs.surfaceContainer).glassy(CircleShape)
            .height(52.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.clip(CircleShape).clickable(onClick = vm::back).fillMaxHeight().padding(start = 6.dp, end = 12.dp)
                .tutorialAnchor(vm, TutorialSpot.BACK).semantics { contentDescription = "Geri" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlyphIcon(Glyph.BACK, ink, Modifier.size(22.dp))
            Spacer(Modifier.width(4.dp))
            Text(if (vm.playingMini) "Geri" else "Səviyyələr", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = ink)
        }
        Box(
            Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onRestart)
                .tutorialAnchor(vm, TutorialSpot.RESTART).semantics { contentDescription = "Səviyyəni yenidən başla" },
            contentAlignment = Alignment.Center,
        ) { GlyphIcon(Glyph.RESTART, ink, Modifier.size(22.dp)) }
        Spacer(Modifier.weight(1f))
        val n = vm.lives
        val red = Color(0xFFE53935)
        Row(
            Modifier.tutorialAnchor(vm, TutorialSpot.LIVES).semantics { contentDescription = "Can: ${if (isPremium) "limitsiz" else n}" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            val hearts = if (isPremium) 1 else maxOf(n, 1)
            repeat(hearts) {
                androidx.compose.foundation.Canvas(Modifier.size(17.dp)) {
                    val path = heartPath(size.width, size.height)
                    if (!isPremium && n <= 0) drawPath(path, red, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
                    else drawPath(path, red)
                }
            }
            if (isPremium) Text("∞", fontWeight = FontWeight.Bold, color = ink, modifier = Modifier.padding(start = 2.dp))
            val next = vm.secondsToNextLife
            if (!isPremium && next != null) {
                Text(" +1 ${clock(next)}", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            }
        }
        val count = vm.currentLevelCount
        if (count != null) Text(count, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ink,
            modifier = Modifier.padding(start = 10.dp, end = 14.dp))
        else Spacer(Modifier.width(12.dp))
    }
}

/** Glass letter tiles in even rows of six, ⌫ and ✕ at the end, as in the mockup. Same picking as the columns. */
@Composable
private fun GlassLetterRows(
    letters: List<Char?>,
    draft: List<Int>,
    onPick: (Int) -> Unit,
    onUnpick: (Int) -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val cs = MaterialTheme.colorScheme
    // Each slot: a letter index, or -1 for ⌫ and -2 for ✕. Used-up spots stay as gaps, so tiles never jump.
    // At most three rows, so the grid above keeps its full width: long words get a 7th or 8th tile per row.
    val perRow = maxOf(6, (letters.size + 2 + 2) / 3)
    // ⌫ and ✕ are always the last two tiles of the bottom row, on the right; -3 is an empty gap before them.
    val rows = (letters.size + 2 + perRow - 1) / perRow
    val slots = letters.indices.toList() + List(rows * perRow - letters.size - 2) { -3 } + listOf(-1, -2)
    val gap = if (perRow > 6) 6.dp else 8.dp
    val h = LocalTile.current + 6.dp
    Column(modifier.fillMaxWidth().padding(top = 2.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        slots.chunked(perRow).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                row.forEach { i ->
                    val shape = RoundedCornerShape(16.dp)
                    val ch = letters.getOrNull(i)
                    when {
                        i == -3 || (i >= 0 && ch == null) -> Spacer(Modifier.weight(1f).height(h))
                        i >= 0 -> {
                            val order = draft.indexOf(i).takeIf { it >= 0 }?.plus(1)
                            val picked = order != null
                            Box(
                                Modifier.weight(1f).height(h).clip(shape)
                                    .background(if (picked) cs.primary else cs.primaryContainer)
                                    .glassy(shape)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        if (picked) onUnpick(i) else onPick(i)
                                    }
                                    .semantics { contentDescription = "Hərf $ch" + if (picked) ", seçilib $order" else "" },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(ch.toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = if (picked) cs.onPrimary else cs.onSurface)
                                if (order != null) Text("$order", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = cs.onPrimary,
                                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 3.dp, end = 6.dp))
                            }
                        }
                        else -> {
                            val enabled = draft.isNotEmpty()
                            Box(
                                Modifier.weight(1f).height(h).clip(shape)
                                    .background(cs.secondaryContainer)
                                    .glassy(shape)
                                    .clickable(enabled = enabled) { if (i == -1) onUndo() else onClear() }
                                    .semantics { contentDescription = if (i == -1) "Son hərfi sil" else "Təmizlə" },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(if (i == -1) "⌫" else "✕", fontSize = 22.sp, color = cs.onSurface.copy(alpha = if (enabled) 1f else 0.4f))
                            }
                        }
                    }
                }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
