package com.aaideas.krossvord.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.aaideas.krossvord.data.PackRepository
import com.aaideas.krossvord.data.ProgressStore
import com.aaideas.krossvord.model.Board
import com.aaideas.krossvord.model.Cell
import com.aaideas.krossvord.model.Entry
import com.aaideas.krossvord.model.GameState
import com.aaideas.krossvord.model.Level
import com.aaideas.krossvord.model.Pack
import com.aaideas.krossvord.model.WordBuilder
import com.aaideas.krossvord.monetization.AdPolicy
import com.aaideas.krossvord.monetization.LocalPremiumManager
import com.aaideas.krossvord.monetization.PlaceholderAdService
import com.aaideas.krossvord.monetization.PremiumManager

enum class Screen { HOME, LEVELS, PUZZLE, SETTINGS }

/** How letters are entered. COLUMNS is the default; the others stay as options in settings. */
enum class InputMode { COLUMNS, TILES, KEYBOARD }

enum class WordFeedback { RIGHT, WRONG }

/** Free hints for guessing the key word before its letters are all in the grid. */
const val KEYWORD_BONUS_HINTS = 2

class GameViewModel(app: Application) : AndroidViewModel(app) {
    val packs: List<Pack> = PackRepository(app).bundledPacks()
    val levels: List<Level> = packs.flatMap { it.levels }
    private val progress = ProgressStore(app)
    val premium: PremiumManager = LocalPremiumManager(app)
    val ads = PlaceholderAdService()

    var screen by mutableStateOf(Screen.HOME)
        private set
    var completed by mutableStateOf(progress.completedLevels())
        private set
    var game by mutableStateOf<GameState?>(null)
        private set
    var showCompletion by mutableStateOf(false)
        private set

    var inputMode by mutableStateOf(
        runCatching { InputMode.valueOf(progress.inputMode) }.getOrDefault(InputMode.COLUMNS)
    )
        private set
    var hideClues by mutableStateOf(progress.hideClues)
        private set
    var extraLetters by mutableStateOf(progress.extraLetters)
        private set

    fun changeExtraLetters(on: Boolean) {
        progress.extraLetters = on
        extraLetters = on
    }

    /** Letter-columns input: indexes of the picked letters, for the word in [draftKey]. */
    private var draftPicks by mutableStateOf(emptyList<Int>())
    private var draftKey: Pair<Int, Int>? = null
    var feedback by mutableStateOf<WordFeedback?>(null)
        private set
    var mistakes by mutableStateOf(0)
        private set
    var freeHints by mutableStateOf(progress.freeHints)
        private set
    var showKeywordDialog by mutableStateOf(false)
        private set
    var keywordWrong by mutableStateOf(false)
        private set

    fun openKeywordDialog() {
        val s = game ?: return
        if (s.keyword != null && !s.isKeywordKnown) { keywordWrong = false; showKeywordDialog = true }
    }

    fun closeKeywordDialog() { showKeywordDialog = false }

    fun guessKeyword(text: String) {
        val s = game ?: return
        val (next, ok) = s.guessKeyword(text)
        if (ok) {
            freeHints = freeHints + KEYWORD_BONUS_HINTS
            progress.freeHints = freeHints
            progress.saveKeywordGuessed(s.board.level.id)
            game = next
            showKeywordDialog = false
        } else keywordWrong = true
    }
    /** Bumped by the shuffle button to reorder the tiles. */
    var tileShuffle by mutableStateOf(0)
        private set

    private var levelIndex = 0

    fun setInput(mode: InputMode) {
        progress.inputMode = mode.name
        inputMode = mode
    }

    fun changeHideClues(on: Boolean) {
        progress.hideClues = on
        hideClues = on
    }

    fun isClueShown(e: Entry): Boolean = !hideClues || (game?.isClueOpen(e) ?: true)

    fun openActiveClue() = update { s -> s.activeEntry?.let { s.openClue(it) } ?: s }

    // ---- Letter columns ----

    private fun keyOf(s: GameState, e: Entry) = e.id to s.openCells(e).size

    fun columnLetters(): List<Char?> {
        val s = game ?: return emptyList()
        val e = s.activeEntry ?: return emptyList()
        return WordBuilder.letters(s, e, tileShuffle, extraLetters)
    }

    /** Picked letter indexes for the active word (empty when the word or its open cells changed). */
    fun draft(): List<Int> {
        val s = game ?: return emptyList()
        val e = s.activeEntry ?: return emptyList()
        return if (draftKey == keyOf(s, e)) draftPicks else emptyList()
    }

    fun pickLetter(index: Int) {
        val s = game ?: return
        val e = s.activeEntry ?: return
        if (feedback == WordFeedback.WRONG) feedback = null
        val picks = draft()
        val open = s.openCells(e)
        if (index in picks || picks.size >= open.size || columnLetters().getOrNull(index) == null) return
        draftKey = keyOf(s, e)
        draftPicks = picks + index
        if (draftPicks.size == open.size) checkDraft(s, e)
    }

    fun unpickLast() {
        feedback = null
        draftPicks = draft().dropLast(1)
    }

    fun clearDraft() {
        feedback = null
        draftPicks = emptyList()
    }

    private fun checkDraft(s: GameState, e: Entry) {
        val letters = columnLetters()
        val word = draftPicks.map { letters[it]!! }
        val (_, ok) = s.submitWord(e, word)
        if (ok) {
            draftPicks = emptyList()
            feedback = WordFeedback.RIGHT
            update { it.openClue(e).submitWord(e, word).first }
        } else {
            mistakes++
            feedback = WordFeedback.WRONG
            viewModelScope.launch {
                delay(700)
                if (feedback == WordFeedback.WRONG) { draftPicks = emptyList(); feedback = null }
            }
        }
    }

    fun shuffleTiles() { tileShuffle++ }

    val currentLevelNumber: Int get() = levelIndex + 1

    fun open(s: Screen) { screen = s }

    fun back() {
        screen = when (screen) {
            Screen.PUZZLE -> Screen.LEVELS
            else -> Screen.HOME
        }
    }

    /** First unfinished level, or the last one when everything is done. */
    fun continueIndex(): Int = levels.indexOfFirst { it.id !in completed }.let { if (it < 0) levels.lastIndex else it }

    fun startLevel(index: Int) {
        levelIndex = index
        val level = levels[index]
        val board = Board(level)
        game = GameState.start(board).copy(
            typed = progress.loadTyped(level.id),
            revealed = progress.loadRevealed(level.id),
        ).let { s -> progress.loadOpenedClues(level.id)?.let { s.copy(openedClues = it) } ?: s }
            .copy(keywordGuessed = progress.loadKeywordGuessed(level.id)).let { s -> s.selectEntry(level.entries.firstOrNull { !s.isEntrySolved(it) } ?: level.entries.first()) }
        showCompletion = false
        feedback = null
        draftPicks = emptyList()
        mistakes = 0
        screen = Screen.PUZZLE
    }

    fun restartLevel() {
        progress.resetLevel(levels[levelIndex].id)
        completed = progress.completedLevels()
        startLevel(levelIndex)
    }

    fun tapCell(c: Cell) = updateSelection { if (it.board.isLetter(c)) it.tapCell(c) else it.tapClue(c) }
    fun selectEntry(e: Entry) = updateSelection { it.selectEntry(e) }

    private fun updateSelection(f: (GameState) -> GameState) {
        if (feedback == WordFeedback.RIGHT) feedback = null
        update(f)
    }
    fun type(ch: Char) = update { it.type(ch) }
    fun backspace() = update { it.backspace() }
    fun clearWord() = update { it.clearActiveEntry() }

    /** Hint: free for premium, otherwise after a rewarded (placeholder) ad. */
    fun hint() {
        if (premium.isPremium.value) update { it.revealLetter() }
        else if (freeHints > 0) {
            freeHints--
            progress.freeHints = freeHints
            update { it.revealLetter() }
        }
        else ads.showRewarded("Hərf aç") { update { it.revealLetter() } }
    }

    fun nextLevel() {
        showCompletion = false
        val go = {
            if (levelIndex + 1 < levels.size) startLevel(levelIndex + 1) else screen = Screen.LEVELS
        }
        val since = progress.levelsSinceInterstitial
        if (AdPolicy.shouldShowInterstitial(premium.isPremium.value, completed.size, since)) {
            progress.levelsSinceInterstitial = 0
            ads.showInterstitial(go)
        } else go()
    }

    fun dismissCompletion() { showCompletion = false }

    private fun update(f: (GameState) -> GameState) {
        val old = game ?: return
        var s = f(old)
        // After a word is solved, jump to the next unsolved word so the player keeps flowing.
        val active = s.activeEntry
        if (active != null && s.isEntrySolved(active) && !old.isEntrySolved(active) && !s.isComplete) {
            val next = s.board.level.entries.let { all ->
                val i = all.indexOf(active)
                (all.drop(i + 1) + all.take(i)).firstOrNull { !s.isEntrySolved(it) }
            }
            if (next != null) s = s.selectEntry(next)
        }
        game = s
        val id = s.board.level.id
        if (s.isComplete && !old.isComplete) {
            progress.markCompleted(id)
            progress.levelsSinceInterstitial = progress.levelsSinceInterstitial + 1
            completed = progress.completedLevels()
            showCompletion = true
        } else if (!s.isComplete) {
            progress.saveTyped(id, s.typed, s.revealed)
        }
        if (s.openedClues != old.openedClues) progress.saveOpenedClues(id, s.openedClues)
    }
}
