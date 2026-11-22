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
import com.aaideas.krossvord.model.Greeting
import com.aaideas.krossvord.model.DailyTask
import com.aaideas.krossvord.model.DailyTasks
import com.aaideas.krossvord.model.Facts
import com.aaideas.krossvord.model.Proverb
import com.aaideas.krossvord.model.Proverbs
import com.aaideas.krossvord.model.Streak
import com.aaideas.krossvord.model.TaskKind
import com.aaideas.krossvord.model.Lives
import com.aaideas.krossvord.model.Medal
import com.aaideas.krossvord.model.MedalStats
import com.aaideas.krossvord.model.Medals
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

enum class Screen { HOME, LEVELS, PUZZLE, SETTINGS, WORD_OF_DAY, STATS, COLLECTION, DAILY }

/** How letters are entered. COLUMNS is the default; the others stay as options in settings. */
/** Pack shown alone in kids mode. */
const val KIDS_PACK = "az-usaq"

enum class InputMode { COLUMNS, TILES, KEYBOARD }

enum class WordFeedback { RIGHT, WRONG }

/** Free hints for guessing the key word before its letters are all in the grid. */
const val KEYWORD_BONUS_HINTS = 2

/** Wrong words allowed per level before the player must watch an ad or restart. */

/** Clue steps that are free; later steps need a rewarded ad. */
const val FREE_CLUE_STEPS = 1
const val TUTORIAL_TAP = 0
const val TUTORIAL_EXPLAIN = 1
const val TUTORIAL_DONE = 2

class GameViewModel(app: Application) : AndroidViewModel(app) {
    val packs: List<Pack> = com.aaideas.krossvord.data.PackCache.get(app)
    val levels: List<Level> = packs.flatMap { it.levels }
    private val progress = ProgressStore(app)
    private val dailyStore = com.aaideas.krossvord.data.DailyStore(app)

    /** Today, for holidays and daily things; tests can set it. */
    var today: () -> java.time.LocalDate = { java.time.LocalDate.now() }
    private fun dayNum() = today().toEpochDay()
    val premium: PremiumManager = LocalPremiumManager(app)
    val ads = PlaceholderAdService()
    val daily = DailyWordGame(app, ads, premium).also { d ->
        d.onSolved = {
            if (soundOn) sounds.wordFound()
            d.reward = dailyReward()
            addXp(com.aaideas.krossvord.model.Ranks.DAILY_XP)
            collect(listOf(d.guess.word.word))
            track(TaskKind.WORD_OF_DAY)
        }
    }
    private val sounds = GameSounds(app)
    /** Clue read aloud, only when the phone has an Azerbaijani voice. */
    val voice = ClueVoice(app)

    var soundOn by mutableStateOf(progress.soundOn)
        private set

    fun changeSound(on: Boolean) {
        soundOn = on
        progress.soundOn = on
    }

    /** Time on the puzzle screen: started when it shows, added up when it hides. */
    private var playStart = 0L
    fun puzzleShown() { playStart = android.os.SystemClock.elapsedRealtime() }
    fun puzzleHidden() {
        flushTime()
        playStart = 0L
    }

    /** Adds the time since [playStart] to the totals and to the current level; keeps counting. */
    private fun flushTime() {
        if (playStart == 0L) return
        val now = android.os.SystemClock.elapsedRealtime()
        val sec = (now - playStart) / 1000
        if (sec in 1..6 * 3600) {
            progress.playSeconds = progress.playSeconds + sec
            game?.let { progress.addLevelSeconds(it.board.level.id, sec) }
            playStart += sec * 1000
        }
    }

    /** Wrong words and letter hints in this level, for the stars. */
    private var hintsUsed = 0

    /** What the result card shows after a solved level. */
    var result by mutableStateOf<LevelResult?>(null)
        private set
    val playSeconds: Long get() = progress.playSeconds + if (playStart == 0L) 0 else (android.os.SystemClock.elapsedRealtime() - playStart) / 1000
    val wordsFound: Int get() = progress.wordsFound

    // ---- Daily tasks, streak calendar, proverb of the day, facts ----
    /** Bumped on every change so the daily screen recomposes. */
    var dailyTick by mutableStateOf(0)
        private set
    /** A short line about a reward just given (task, all tasks, 7 days); the screen shows and clears it. */
    var dailyNote by mutableStateOf<String?>(null)

    val tasks: List<DailyTask> get() = DailyTasks.forDay(dayNum(), hasMini = false)
    fun taskCount(t: DailyTask): Int { dailyTick; return dailyStore.count(dayNum(), t.kind).coerceAtMost(t.target) }
    fun taskDone(t: DailyTask) = taskCount(t) >= t.target
    val tasksDone: Int get() = tasks.count { taskDone(it) }

    val playedDays: Set<Long> get() { dailyTick; return dailyStore.playedDays }
    val streakRun: Int get() = Streak.run(playedDays, dayNum())
    fun todayNum() = dayNum()

    private fun track(kind: TaskKind, n: Int = 1) {
        val day = dayNum()
        dailyStore.add(day, kind, n)
        markPlayed()
        val list = tasks
        val paid = dailyStore.rewarded(day).toMutableSet()
        list.forEachIndexed { i, t ->
            if (i !in paid && dailyStore.count(day, t.kind) >= t.target) {
                paid += i
                addXp(DailyTasks.TASK_XP)
                dailyNote = "✓ ${t.title}: +${DailyTasks.TASK_XP} XP"
            }
        }
        dailyStore.setRewarded(day, paid)
        if (paid.size >= list.size && !dailyStore.allRewarded(day)) {
            dailyStore.setAllRewarded(day)
            setLives(livesState.gift(now()))
            dailyNote = "🎉 Bütün tapşırıqlar bitdi: +1 can ❤️"
        }
        dailyTick++
    }

    private fun markPlayed() {
        val day = dayNum()
        val days = dailyStore.playedDays
        if (day !in days) dailyStore.playedDays = days + day
        val now = dailyStore.playedDays
        if (Streak.giftDue(now, day, dailyStore.lastStreakGift)) {
            dailyStore.lastStreakGift = day
            setLives(livesState.gift(now(), 2))
            addXp(50)
            dailyNote = "🎁 ${Streak.run(now, day)} gün ardıcıl! Hədiyyə: +2 can və 50 XP"
        }
        dailyTick++
    }

    val proverb: Proverb get() = Proverbs.forDay(dayNum())
    val proverbOptions: List<String> get() = Proverbs.options(dayNum())
    val proverbPick: String? get() { dailyTick; return dailyStore.proverbPick(dayNum()) }

    fun pickProverb(w: String) {
        if (proverbPick != null) return
        dailyStore.setProverbPick(dayNum(), w)
        if (soundOn) { if (w == proverb.answer) sounds.wordFound() }
        if (w == proverb.answer) { addXp(5); track(TaskKind.PROVERB) } else markPlayed()
        dailyTick++
    }

    private fun nextFact(): String {
        val i = dailyStore.factIndex
        dailyStore.factIndex = i + 1
        return Facts.at(i)
    }

    // ---- Rank ----
    var xp by mutableStateOf(progress.xp)
        private set

    /** Adds XP; returns the new rank if this raised it. */
    private fun addXp(n: Int): com.aaideas.krossvord.model.Rank? {
        val before = com.aaideas.krossvord.model.Ranks.of(xp)
        xp += n
        progress.xp = xp
        val after = com.aaideas.krossvord.model.Ranks.of(xp)
        return if (after.index > before.index) after else null
    }

    // ---- Word collection and medals ----
    var collection by mutableStateOf(progress.collection)
        private set

    init {
        // Players from before the collection existed: their solved levels count.
        val done = progress.completedLevels()
        if (collection.isEmpty() && done.isNotEmpty()) {
            collect(levels.filter { it.id in done }.flatMap { l -> l.entries.map { it.answer } })
        }
    }

    private fun collect(words: List<String>) {
        if (words.all { it in collection }) return
        collection = collection + words
        progress.collection = collection
    }

    /** Every answer in the bundled packs, with its pack, for the collection screen. */
    val allEntries: List<Pair<Pack, Entry>> by lazy { packs.flatMap { p -> p.levels.flatMap { l -> l.entries.map { p to it } } } }

    val medals: List<Medal> = Medals.all(packs)
    fun medalStats(done: Set<String> = completed) = MedalStats(
        packsDone = packs.filter { p -> p.levels.isNotEmpty() && p.levels.all { it.id in done } }.map { it.packId }.toSet(),
        levelsDone = done.size,
        perfectLevels = progress.perfectLevels,
        bestPerfectRun = progress.bestPerfectRun,
        dailyBestStreak = daily.bestStreak,
        wordsFound = progress.wordsFound,
    )
    fun earnedMedals(): Set<String> = Medals.earnedIds(packs, medalStats())
    private fun earnedMedalsAfterMark(levelId: String) = Medals.earnedIds(packs, medalStats(completed + levelId))

    /** Medals won by the level just solved; shown in the completion dialog. */
    var newMedals by mutableStateOf<List<Medal>>(emptyList())
        private set

    override fun onCleared() { puzzleHidden(); sounds.release(); voice.shutdown() }

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

    /** Light / dark / follow the phone; kept across launches. */
    var themeMode by mutableStateOf(ThemeMode.fromName(progress.themeMode))
        private set

    fun changeTheme(mode: ThemeMode) {
        themeMode = mode
        progress.themeMode = mode.name
    }

    // ---- Themes (skins) ----
    var skin by mutableStateOf(Skin.fromId(progress.skin))
        private set
    var ownedSkins by mutableStateOf(Skin.free + progress.skinsOwned.map { Skin.fromId(it) })
        private set
    /** A gift theme just earned; the home screen shows it once. */
    var newSkin by mutableStateOf<Skin?>(null)

    /** Themes the player may use now: earned ones, plus the premium set while premium is active. */
    fun usableSkins(isPremium: Boolean): Set<Skin> = if (isPremium) ownedSkins + Skin.premium else ownedSkins

    /** The theme to draw; a premium theme falls back to Klassik if premium ends. */
    fun activeSkin(isPremium: Boolean): Skin = if (skin in usableSkins(isPremium)) skin else Skin.KLASSIK

    fun changeSkin(s: Skin) {
        if (s !in usableSkins(premium.isPremium.value)) return
        skin = s
        progress.skin = s.id
        if (newSkin == s) newSkin = null
    }

    /** Check gift conditions (rank, word-of-the-day streak, Novruz) and keep what was earned. */
    fun refreshSkins(rankThemes: Set<String> = emptySet()) {
        val now = Skin.earned(rankThemes, daily.bestStreak)
        val gained = now - ownedSkins
        if (gained.isEmpty()) return
        ownedSkins = ownedSkins + gained
        progress.skinsOwned = (ownedSkins - Skin.free).map { it.id }.toSet()
        newSkin = gained.first()
    }

    /** Prototype only: try every theme without earning it. */
    fun unlockAllSkinsForTesting() {
        ownedSkins = Skin.entries.toSet() - Skin.premium
        progress.skinsOwned = ownedSkins.map { it.id }.toSet()
    }

    var dynamicColor by mutableStateOf(progress.dynamicColor)
        private set

    fun changeDynamicColor(on: Boolean) {
        dynamicColor = on
        progress.dynamicColor = on
    }

    /** Has the player any progress (typed letters or lives used) in this level? */
    fun levelStarted(index: Int): Boolean = progress.hasProgress(levels[index].id)

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
    /** Shared lives (model/Lives): refreshed by [tickLives] while some are missing. */
    private var livesState by mutableStateOf(progress.lives.at(now()))
    private fun now() = System.currentTimeMillis()
    val lives: Int get() = livesState.count
    /** Seconds until the next life comes back, or null when full; updates every second. */
    val secondsToNextLife: Long? get() = livesState.msToNext(livesClock)?.let { (it + 999) / 1000 }
    private var livesClock by mutableStateOf(now())
    /** Premium players never run out. */
    val outOfLives: Boolean get() = lives <= 0 && !premium.isPremium.value && !kidsMode

    private fun setLives(l: Lives) { livesState = l; livesClock = now(); progress.lives = l }

    /** Called every second by the puzzle screen while lives are missing. */
    fun tickLives() {
        livesClock = now()
        val l = livesState.at(livesClock)
        if (l != livesState) setLives(l)
    }

    private fun loseLife() { if (!premium.isPremium.value && !kidsMode) setLives(livesState.lose(now())) }

    /** Out of lives: back to full after a rewarded ad (no ad for premium). */
    fun refillLives() {
        val give: () -> Unit = { setLives(livesState.refill(now())) }
        if (premium.isPremium.value) give() else ads.showRewarded("Canları doldur", give)
    }

    /** Word of the day solved: lives back to full, or one more in reserve. Returns the message. */
    private fun dailyReward(): String {
        val before = livesState.at(now())
        setLives(before.reward(now()))
        return if (before.count < Lives.CAP) "Mükafat: canlar doldu ♥" else "Mükafat: +1 ehtiyat can ♥ (${lives})"
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

    /** One switch for weak eyes: bigger text everywhere and the selected clue in large print. */
    // --- Welcome, name and birthday (all optional, kept on the phone) ---
    var playerName by mutableStateOf(progress.playerName)
        private set
    var birthday by mutableStateOf(Greeting.parse(progress.birthday))
        private set
    var birthYear by mutableStateOf(progress.birthYear.takeIf { it > 0 })
        private set

    /** What to show when the app opens: first the profile question, then once a day a greeting. */
    enum class Welcome { PROFILE, BIRTHDAY, DAILY }
    var welcome by mutableStateOf<Welcome?>(null)
        private set

    /** Called once when the app opens. Gives the birthday life (once a year). */
    fun onAppOpen() {
        val day = today()
        if (Greeting.isBirthday(day, birthday) && progress.birthdayGiftYear != day.year) {
            progress.birthdayGiftYear = day.year
            setLives(livesState.gift(now()))
            progress.welcomeDay = day.toEpochDay()
            welcome = Welcome.BIRTHDAY
            return
        }
        if (!progress.profileAsked) { welcome = Welcome.PROFILE; return }
        if (progress.welcomeDay != day.toEpochDay()) {
            progress.welcomeDay = day.toEpochDay()
            welcome = Welcome.DAILY
        }
    }

    fun editProfile() { welcome = Welcome.PROFILE }

    fun saveProfile(name: String, md: java.time.MonthDay?, year: Int? = null) {
        birthYear = year; progress.birthYear = year ?: 0
        playerName = name.trim().take(24); progress.playerName = playerName
        birthday = md; progress.birthday = md?.let(Greeting::format)
        progress.profileAsked = true
        progress.welcomeDay = today().toEpochDay()
        welcome = null
        // Entered on the birthday itself: the gift still comes today.
        if (Greeting.isBirthday(today(), md) && progress.birthdayGiftYear != today().year) onAppOpen()
    }

    fun dismissWelcome() {
        if (welcome == Welcome.PROFILE) progress.profileAsked = true
        welcome = null
    }

    fun hello(): String = Greeting.hello(java.time.LocalTime.now().hour, playerName)

    var largeMode by mutableStateOf(progress.largeMode)
        private set

    fun changeLargeMode(on: Boolean) {
        largeMode = on
        progress.largeMode = on
    }

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
    fun continueIndex(): Int = levels.indices.firstOrNull { levels[it].id !in completed && levelOpen(it) } ?: levels.lastIndex


    /** Holiday packs are open only around the holiday (or once the player has started them). */
    fun packOpen(p: Int): Boolean {
        val pack = packs[p]
        if (kidsMode && kidsPack >= 0) return p == kidsPack
        return com.aaideas.krossvord.model.Holidays.packOpen(pack.packId, today()) ||
            pack.levels.any { it.id in completed || progress.hasProgress(it.id) }
    }
    fun levelOpen(index: Int) = packOpen(packIndexOf(index))

    /** The holiday to greet on the home screen today, if any. */
    val holiday: com.aaideas.krossvord.model.Holiday? get() = com.aaideas.krossvord.model.Holidays.greeting(today())

    /** Saved clue levels; in kids mode clues not touched yet start at the easiest one. */
    private fun startTiers(level: Level): Map<Int, Int> {
        val saved = progress.loadClueTiers(level.id)
        if (!kidsMode) return saved
        return level.entries.associate { it.id to (saved[it.id] ?: it.clueLevels.lastIndex) }
    }

    // ---- Kids mode: easiest clues first, no lives lost, only the kids pack when there is one ----
    var kidsMode by mutableStateOf(progress.kidsMode)
        private set
    val kidsPack: Int get() = packs.indexOfFirst { it.packId == KIDS_PACK }

    fun changeKidsMode(on: Boolean) {
        kidsMode = on
        progress.kidsMode = on
        if (on && kidsPack >= 0) selectedPack = kidsPack
    }

    fun startLevel(index: Int) {
        levelIndex = index
        selectedPack = packIndexOf(index)
        val level = levels[index]
        val board = Board(level)
        game = GameState.start(board).copy(
            typed = progress.loadTyped(level.id),
            revealed = progress.loadRevealed(level.id),
        ).let { s -> progress.loadOpenedClues(level.id)?.let { s.copy(openedClues = it) } ?: s }
            .copy(keywordGuessed = progress.loadKeywordGuessed(level.id), clueTiers = startTiers(level)).let { s -> s.selectEntry(level.entries.firstOrNull { !s.isEntrySolved(it) } ?: level.entries.first()) }
        showCompletion = false
        feedback = null
        draftPicks = emptyList()
        mistakes = 0
        hintsUsed = 0
        tickLives()
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
        dailyStore.clear()
        dailyTick++
        completed = progress.completedLevels()
        freeHints = progress.freeHints
        setLives(progress.lives.at(now()))
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
        hintsUsed++
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
            val next = (levelIndex + 1 until levels.size).firstOrNull { levelOpen(it) }
            if (next != null) startLevel(next) else screen = Screen.LEVELS
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
        mistakes += newlyWrong
        val found = s.solvedCount - old.solvedCount
        if (found > 0 && s.board.level.id == old.board.level.id) {
            progress.wordsFound = progress.wordsFound + found
            track(TaskKind.WORDS, found)
            collect(s.board.level.entries.filter { s.isEntrySolved(it) && !old.isEntrySolved(it) }.map { it.answer })
        }
        if (soundOn && found > 0) { if (s.isComplete && !old.isComplete) sounds.levelSolved() else sounds.wordFound() }
        if (s.isComplete && !old.isComplete) {
            val medalsBefore = earnedMedals()
            if (mistakes == 0) {
                progress.perfectLevels = progress.perfectLevels + 1
                progress.perfectRun = progress.perfectRun + 1
                progress.bestPerfectRun = maxOf(progress.bestPerfectRun, progress.perfectRun)
            } else progress.perfectRun = 0
            progress.markCompleted(id)
            newMedals = medals.filter { it.id in earnedMedalsAfterMark(id) - medalsBefore }
            flushTime()
            val stars = LevelResult.stars(mistakes, hintsUsed)
            val rankUp = addXp(com.aaideas.krossvord.model.Ranks.levelXp(stars))
            result = LevelResult(
                rankUp = rankUp,
                xp = com.aaideas.krossvord.model.Ranks.levelXp(stars),
                stars = stars,
                seconds = progress.levelSeconds(id),
                praise = LevelResult.PRAISE.random(),
                keyword = s.keyword?.word,
                medals = newMedals,
                fact = nextFact(),
            )
            track(TaskKind.LEVELS)
            if (mistakes == 0) track(TaskKind.PERFECT)
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

/** The result card of a solved level: 1 to 3 stars, time taken (shown only here), praise, new medals. */
data class LevelResult(val rankUp: com.aaideas.krossvord.model.Rank? = null, val xp: Int = 0, val stars: Int, val seconds: Long, val praise: String, val keyword: String?, val medals: List<Medal>, val fact: String? = null) {
    companion object {
        val PRAISE = listOf("Əla!", "Bravo!", "Afərin!", "Belə davam et!", "Mükəmməl!", "Super!", "Çox gözəl!")
        /** Never zero: 3 without mistakes and letter hints, 2 with a few, 1 otherwise. */
        fun stars(mistakes: Int, hints: Int): Int = when {
            mistakes == 0 && hints == 0 -> 3
            mistakes + hints <= 2 -> 2
            else -> 1
        }
    }
}
