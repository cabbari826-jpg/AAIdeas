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

enum class Screen { HOME, LEVELS, PUZZLE, SETTINGS, WORD_OF_DAY }

/** How letters are entered. COLUMNS is the default; the others stay as options in settings. */
enum class InputMode { COLUMNS, TILES, KEYBOARD }

enum class WordFeedback { RIGHT, WRONG }

/** Free hints for guessing the key word before its letters are all in the grid. */
const val KEYWORD_BONUS_HINTS = 2

/** Wrong words allowed per level before the player must watch an ad or restart. */
const val MAX_LIVES = 3

/** Clue steps that are free; later steps need a rewarded ad. */
const val FREE_CLUE_STEPS = 1
const val TUTORIAL_TAP = 0
const val TUTORIAL_EXPLAIN = 1
const val TUTORIAL_DONE = 2

class GameViewModel(app: Application) : AndroidViewModel(app) {
    val packs: List<Pack> = PackRepository(app).bundledPacks()
    val levels: List<Level> = packs.flatMap { it.levels }
    private val progress = ProgressStore(app)
    val premium: PremiumManager = LocalPremiumManager(app)
    val ads = PlaceholderAdService()
    val daily = DailyWordGame(app, ads, premium)

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
    // Both are Compose state: draft() must subscribe to them, or picks never redraw (old bug).
    private var draftKey by mutableStateOf<Pair<Int, Int>?>(null)
    var feedback by mutableStateOf<WordFeedback?>(null)
        private set
    var mistakes by mutableStateOf(0)
        private set
    var freeHints by mutableStateOf(progress.freeHints)
        private set
    var lives by mutableStateOf(MAX_LIVES)
        private set
    val outOfLives: Boolean get() = lives <= 0

    private fun loseLife() {
        lives = (lives - 1).coerceAtLeast(0)
        game?.let { progress.saveLives(it.board.level.id, lives) }
    }

    /** Out of lives: one more life after a rewarded ad (free for premium). */
    fun extraLife() {
        val give: () -> Unit = {
            lives = 1
            game?.let { progress.saveLives(it.board.level.id, lives) }
        }
        if (premium.isPremium.value) give() else ads.showRewarded("+1 can", give)
    }
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

    /**
     * Make the active clue easier: hardest -> medium is free, medium -> easiest needs a
     * rewarded ad (free for premium).
     */
    fun easeActiveClue() {
        val s = game ?: return
        val e = s.activeEntry ?: return
        if (!s.canEaseClue(e)) return
        val needsAd = s.clueTier(e) >= FREE_CLUE_STEPS && !premium.isPremium.value
        if (needsAd) ads.showRewarded("Asan ipucu") { update { it.easeClue(e) } } else update { it.easeClue(e) }
    }

    /**
     * Tap on a clue cell: selects that word and opens its clue in a large card, where the
     * player can read it comfortably, ask for an easier clue and swipe back to earlier ones.
     */
    fun tapClueOf(e: Entry) {
        val s = game ?: return
        if (feedback == WordFeedback.RIGHT) feedback = null
        if (s.activeEntry != e) update { it.selectEntry(e) }
        update { it.openClue(e) }
        clueCard = e
        if (tutorialStep == TUTORIAL_TAP) tutorialStep = TUTORIAL_EXPLAIN
    }

    /** The word whose clue card is open, or null. */
    var clueCard by mutableStateOf<Entry?>(null)
        private set

    fun closeClueCard() { clueCard = null }

    /** First-run tutorial: 0 = "tap this clue", 1 = explain what happened, 2 = done. */
    var tutorialStep by mutableStateOf(if (progress.tutorialDone) TUTORIAL_DONE else TUTORIAL_TAP)
        private set

    var bigClues by mutableStateOf(progress.bigClues)
        private set

    fun changeBigClues(on: Boolean) {
        bigClues = on
        progress.bigClues = on
    }

    fun finishTutorial() {
        tutorialStep = TUTORIAL_DONE
        progress.tutorialDone = true
    }

    fun easeNeedsAd(): Boolean {
        val s = game ?: return false
        val e = s.activeEntry ?: return false
        return s.clueTier(e) >= FREE_CLUE_STEPS && !premium.isPremium.value
    }

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
        if (outOfLives) return
        if (feedback == WordFeedback.WRONG) feedback = null
        val picks = draft()
        val open = s.openCells(e)
        if (index in picks || picks.size >= open.size || columnLetters().getOrNull(index) == null) return
        draftKey = keyOf(s, e)
        draftPicks = picks + index
        if (draftPicks.size == open.size) checkDraft(s, e)
    }

    /** Take back one picked letter (tapping a lit tile); later picks keep their order. */
    fun unpickLetter(index: Int) {
        feedback = null
        draftPicks = draft() - index
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
            loseLife()
            feedback = WordFeedback.WRONG
            viewModelScope.launch {
                delay(700)
                if (feedback == WordFeedback.WRONG) { draftPicks = emptyList(); feedback = null }
            }
        }
    }

    fun shuffleTiles() { tileShuffle++ }

    /** Pack index and 1-based number inside its pack for a global level index. */
    private val packStart: List<Int> = packs.runningFold(0) { acc, p -> acc + p.levels.size }
    fun packIndexOf(index: Int): Int = (packs.indices.lastOrNull { packStart[it] <= index } ?: 0)
    fun numberInPack(index: Int): Int = index - packStart[packIndexOf(index)] + 1
    fun levelTitle(index: Int): String {
        val p = packIndexOf(index)
        return (if (p == 0) "" else packs[p].title + " · ") + "Səviyyə ${numberInPack(index)}"
    }
    fun packLevels(p: Int): List<Pair<Int, Level>> = packs[p].levels.mapIndexed { i, l -> packStart[p] + i to l }

    /** Pack shown on the level list; follows the level being played. */
    var selectedPack by mutableStateOf(0)

    val currentLevelNumber: Int get() = numberInPack(levelIndex)
    val currentLevelTitle: String get() = levelTitle(levelIndex)

    fun open(s: Screen) {
        if (s == Screen.WORD_OF_DAY) daily.refresh()
        screen = s
    }

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
        selectedPack = packIndexOf(index)
        val level = levels[index]
        val board = Board(level)
        game = GameState.start(board).copy(
            typed = progress.loadTyped(level.id),
            revealed = progress.loadRevealed(level.id),
        ).let { s -> progress.loadOpenedClues(level.id)?.let { s.copy(openedClues = it) } ?: s }
            .copy(keywordGuessed = progress.loadKeywordGuessed(level.id), clueTiers = progress.loadClueTiers(level.id)).let { s -> s.selectEntry(level.entries.firstOrNull { !s.isEntrySolved(it) } ?: level.entries.first()) }
        showCompletion = false
        feedback = null
        draftPicks = emptyList()
        mistakes = 0
        lives = progress.loadLives(level.id) ?: MAX_LIVES
        screen = Screen.PUZZLE
    }

    /** Label for the home screen: which level "continue" opens and whether it was started. */
    fun continueLabel(): String {
        val i = continueIndex()
        val all = completed.size == levels.size
        return when {
            all -> "Hamısı həll olunub · ${levelTitle(i)}"
            progress.hasProgress(levels[i].id) -> "Davam et · ${levelTitle(i)}"
            completed.isEmpty() -> "Oyuna başla"
            else -> "Davam et · ${levelTitle(i)}"
        }
    }

    /** Wipe all progress (levels, lives, hints) and start again from level 1. */
    fun resetAllProgress() {
        progress.resetAll()
        daily.reset()
        completed = progress.completedLevels()
        freeHints = progress.freeHints
        game = null
        screen = Screen.HOME
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
    fun type(ch: Char) { if (!outOfLives) update { it.type(ch) } }
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
        // Tiles/keyboard modes: a word that just became complete but wrong costs a life.
        val newlyWrong = s.board.level.entries.count { s.isEntryWrong(it) && !old.isEntryWrong(it) }
        repeat(newlyWrong) { loseLife() }
        if (s.isComplete && !old.isComplete) {
            progress.markCompleted(id)
            progress.levelsSinceInterstitial = progress.levelsSinceInterstitial + 1
            completed = progress.completedLevels()
            showCompletion = true
        } else if (!s.isComplete) {
            progress.saveTyped(id, s.typed, s.revealed)
        }
        if (s.openedClues != old.openedClues) progress.saveOpenedClues(id, s.openedClues)
        if (s.clueTiers != old.clueTiers) progress.saveClueTiers(id, s.clueTiers)
    }
}
