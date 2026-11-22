package com.aaideas.krossvord.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.aaideas.krossvord.data.PackRepository
import com.aaideas.krossvord.data.ProgressStore
import com.aaideas.krossvord.model.Board
import com.aaideas.krossvord.model.Cell
import com.aaideas.krossvord.model.Entry
import com.aaideas.krossvord.model.GameState
import com.aaideas.krossvord.model.Level
import com.aaideas.krossvord.model.Pack
import com.aaideas.krossvord.monetization.AdPolicy
import com.aaideas.krossvord.monetization.LocalPremiumManager
import com.aaideas.krossvord.monetization.PlaceholderAdService
import com.aaideas.krossvord.monetization.PremiumManager

enum class Screen { HOME, LEVELS, PUZZLE, SETTINGS }

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

    var useKeyboard by mutableStateOf(progress.useKeyboard)
        private set
    /** Bumped by the shuffle button to reorder the tiles. */
    var tileShuffle by mutableStateOf(0)
        private set

    private var levelIndex = 0

    fun setKeyboardMode(on: Boolean) {
        progress.useKeyboard = on
        useKeyboard = on
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
        ).let { s -> s.selectEntry(level.entries.firstOrNull { !s.isEntrySolved(it) } ?: level.entries.first()) }
        showCompletion = false
        screen = Screen.PUZZLE
    }

    fun restartLevel() {
        progress.resetLevel(levels[levelIndex].id)
        completed = progress.completedLevels()
        startLevel(levelIndex)
    }

    fun tapCell(c: Cell) = update { if (it.board.isLetter(c)) it.tapCell(c) else it.tapClue(c) }
    fun selectEntry(e: Entry) = update { it.selectEntry(e) }
    fun type(ch: Char) = update { it.type(ch) }
    fun backspace() = update { it.backspace() }
    fun clearWord() = update { it.clearActiveEntry() }

    /** Hint: free for premium, otherwise after a rewarded (placeholder) ad. */
    fun hint() {
        if (premium.isPremium.value) update { it.revealLetter() }
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
    }
}
