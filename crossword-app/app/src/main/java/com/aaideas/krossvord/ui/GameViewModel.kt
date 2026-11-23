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

enum class Screen { HOME, LEVELS, PUZZLE, SETTINGS, WORD_OF_DAY, STATS, COLLECTION, DAILY, PACKS, MAP }

/** How letters are entered. COLUMNS is the default; the others stay as options in settings. */
/** Pack shown alone in kids mode. */
const val KIDS_PACK = "az-usaq"

enum class InputMode { COLUMNS, TILES, KEYBOARD }

enum class WordFeedback { RIGHT, WRONG }

/** "Bir az dincəlin" after this much play in one sitting, and again every time after it. */
const val REST_EVERY_SECONDS = 60L * 60
/** Away this long and the next play is a new sitting. */
const val REST_GAP_MS = 15L * 60 * 1000

/** Text sizes on the settings slider: normal, a bit bigger, large, very large. */
val TEXT_SCALES = listOf(1f, 1.15f, 1.3f, 1.5f)
val TEXT_SCALE_NAMES = listOf("Adi", "Bir az iri", "İri", "Çox iri")

/** The phone's font at this size or bigger turns large mode on by itself (until the player decides). */
const val AUTO_LARGE_FONT_SCALE = 1.15f

/** Seconds without progress on a level before the gentle "stuck?" hint shows. No timer is ever shown. */
const val STUCK_SECONDS = 75L

/** How the "stuck?" help looks: A pill under the grid, B owl helper, C golden glow, D key helper (Açar = key), E tall key with big eyes and brows (an office-assistant feel, our own drawing). */
enum class StuckStyle { PILL, OWL, GLOW, KEY, KEY_TALL }

/** Mascots the player can pick in Settings (in this order). */
val MASCOTS = listOf(StuckStyle.KEY_TALL, StuckStyle.KEY, StuckStyle.OWL)

/** Short praise for a found word (shown big for a moment, said aloud if there is a voice). */
val SHORT_PRAISE = listOf("Bravo!", "Əla!", "Afərin!", "Super!", "Düzdür!")

/** Free hints for guessing the key word before its letters are all in the grid. */
const val KEYWORD_BONUS_HINTS = 2

/** Wrong words allowed per level before the player must watch an ad or restart. */

/** Clue steps that are free; later steps need a rewarded ad. */
const val FREE_CLUE_STEPS = 1
/** First-run tutorial: ask whether to explain, then coach marks one by one, then done. */
const val TUTORIAL_ASK = 0
const val TUTORIAL_COACH = 1
const val TUTORIAL_DONE = 2

/** Parts of the puzzle screen the coach marks point at. */
enum class TutorialSpot { CLUE, WORD, LETTERS, KEYWORD, HINT, LIVES, RESTART, BACK }

val COACH_STEPS: List<Pair<TutorialSpot, String>> = listOf(
    TutorialSpot.CLUE to "Bu xanada ipucu yazılıb. Ox sözün hansı tərəfə yazıldığını göstərir: → sağa, ↓ aşağı. Toxunsanız, ipucu böyük açılar.",
    TutorialSpot.WORD to "Sözün hərfləri bu xanalara yazılır. Seçilmiş söz rənglə işıqlanır, işıq sözün istiqamətində qaçır.",
    TutorialSpot.LETTERS to "Hərfləri buradan seçin. Söz düzdürsə, yaşıl olur. Səhvdirsə, bir can gedir.",
    TutorialSpot.KEYWORD to "Dairəli xanalardakı hərflər açar sözü yaradır. Tez tapsanız, bonus alırsınız.",
    TutorialSpot.HINT to "Çətin olsa, bir hərf açın.",
    TutorialSpot.LIVES to "Canlarınız. Bitəndə vaxt keçdikcə özü bərpa olunur.",
    TutorialSpot.RESTART to "Səviyyəni yenidən başlamaq üçün.",
    TutorialSpot.BACK to "Əsas səhifəyə qayıtmaq üçün.",
)

class GameViewModel(app: Application) : AndroidViewModel(app) {
    /** Bundled packs plus downloaded ones; replaced after a download (see [reloadPacks]). */
    var packs: List<Pack> by mutableStateOf(com.aaideas.krossvord.data.PackCache.get(app))
        private set
    var levels: List<Level> by mutableStateOf(packs.flatMap { it.levels })
        private set
    private val progress = ProgressStore(app)
    private val dailyStore = com.aaideas.krossvord.data.DailyStore(app)

    /** Today, for holidays and daily things; tests can set it. */
    var today: () -> java.time.LocalDate = { java.time.LocalDate.now() }
    private fun dayNum() = today().toEpochDay()
    val premium: PremiumManager = LocalPremiumManager(app)
    val ads = PlaceholderAdService()
    val daily = DailyWordGame(app, ads, premium).also { d ->
        d.onSolved = {
            sounds.wordFound()
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
        sounds.sound = on
    }

    var vibrateOn by mutableStateOf(progress.vibrateOn)
        private set

    fun changeVibrate(on: Boolean) {
        vibrateOn = on
        progress.vibrateOn = on
        sounds.vibrate = on
    }

    init { sounds.sound = soundOn; sounds.vibrate = vibrateOn }

    /** Say the praise aloud for a found word (only with an Azerbaijani voice on the phone). */
    var praiseVoice by mutableStateOf(progress.praiseVoice)
        private set

    fun changePraiseVoice(on: Boolean) {
        praiseVoice = on
        progress.praiseVoice = on
        if (on) voice.warmUp()
    }

    /** "Bravo!" shown big for a moment after a found word; [praiseTick] restarts the animation. */
    var praise by mutableStateOf<String?>(null)
        private set
    var praiseTick by mutableStateOf(0)
        private set

    private fun praiseWord() {
        val w = SHORT_PRAISE.random()
        praise = w
        praiseTick++
        if (praiseVoice) voice.speak(w)
    }

    // ---- Gentle hint when stuck: bumped on every bit of progress; the screen waits STUCK_SECONDS after it ----
    var progressTick by mutableStateOf(0)
        private set
    private fun progressed() { progressTick++ }

    // ---- "Mənim sözlərim": words got wrong at least once ----
    var mistakeWords by mutableStateOf(progress.mistakeWords)
        private set

    private fun addMistake(word: String) {
        if (word in mistakeWords) return
        mistakeWords = mistakeWords + word
        progress.mistakeWords = mistakeWords
    }

    /** The collection screen opens on "Mənim sözlərim" (set by the button that opens it). */
    var openMistakes = false

    /** The player marks a word as learned: it leaves the list. */
    fun markLearned(word: String) {
        mistakeWords = mistakeWords - word
        progress.mistakeWords = mistakeWords
    }

    /** Time on the puzzle screen: started when it shows, added up when it hides. */
    private var playStart = 0L
    fun puzzleShown() {
        val now = android.os.SystemClock.elapsedRealtime()
        // A real pause (15 minutes away) starts a new sitting for the rest reminder.
        if (lastHidden != 0L && now - lastHidden > REST_GAP_MS) { sittingSeconds = 0; nextRestAt = REST_EVERY_SECONDS }
        playStart = now
    }
    fun puzzleHidden() {
        flushTime()
        playStart = 0L
        lastHidden = android.os.SystemClock.elapsedRealtime()
    }

    // ---- "Bir az dincəlin": after an hour of play in one sitting, a gentle window before the next level, then every hour ----
    private var lastHidden = 0L
    /** Seconds played on levels in this sitting. */
    private var sittingSeconds = 0L
    private var nextRestAt = REST_EVERY_SECONDS
    var restDue by mutableStateOf(false)
        private set
    fun closeRest(goHome: Boolean) { restDue = false; if (goHome) { flushTime(); screen = Screen.HOME } }
    /** Tests: pretend this much was played. */
    fun addSittingForTest(sec: Long) { sittingSeconds += sec }
    private fun checkRest() {
        flushTime()
        if (sittingSeconds >= nextRestAt) {
            restDue = true
            nextRestAt = sittingSeconds + REST_EVERY_SECONDS
        }
    }

    /** Adds the time since [playStart] to the totals and to the current level; keeps counting. */
    private fun flushTime() {
        if (playStart == 0L) return
        val now = android.os.SystemClock.elapsedRealtime()
        val sec = (now - playStart) / 1000
        if (sec in 1..6 * 3600) {
            progress.playSeconds = progress.playSeconds + sec
            sittingSeconds += sec
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

    val tasks: List<DailyTask> get() = DailyTasks.forDay(dayNum(), hasMini = miniLevels.isNotEmpty())
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
        if (Streak.hintDue(now, day, dailyStore.lastStreakHint)) {
            dailyStore.lastStreakHint = day
            freeHints = freeHints + 1
            progress.freeHints = freeHints
            dailyNote = "💡 ${Streak.run(now, day)} gün ardıcıl! Hədiyyə: 1 pulsuz «Hərf aç»"
        }
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
        if (w == proverb.answer) sounds.wordFound()
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
    val allEntries: List<Pair<Pack, Entry>> get() = entriesOf(packs)
    private var entriesCache: Pair<List<Pack>, List<Pair<Pack, Entry>>>? = null
    private fun entriesOf(ps: List<Pack>): List<Pair<Pack, Entry>> =
        entriesCache?.takeIf { it.first === ps }?.second
            ?: ps.flatMap { p -> p.levels.flatMap { l -> l.entries.map { p to it } } }.also { entriesCache = ps to it }

    var medals: List<Medal> by mutableStateOf(Medals.all(packs))
        private set
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
        progressed()
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
    }

    /** The word whose clue card is open, or null. */
    var clueCard by mutableStateOf<Entry?>(null)
        private set

    fun closeClueCard() { clueCard = null }

    var tutorialStep by mutableStateOf(if (progress.tutorialDone) TUTORIAL_DONE else TUTORIAL_ASK)
        private set
    /** Which coach mark is showing; spots that are not on screen are skipped. */
    var coachIndex by mutableStateOf(0)
        private set
    /** Where the coach-marked parts are on screen, reported by the puzzle screen while coaching. */
    val tutorialRects = androidx.compose.runtime.mutableStateMapOf<TutorialSpot, androidx.compose.ui.geometry.Rect>()

    fun startCoach() { coachIndex = 0; tutorialStep = TUTORIAL_COACH }
    fun nextCoach() { if (coachIndex + 1 < COACH_STEPS.size) coachIndex++ else finishTutorial() }
    /** From settings: show the explanation again next time a level opens. */
    fun replayTutorial() { progress.tutorialDone = false; tutorialStep = TUTORIAL_ASK }

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

    // ---- Asking for the daily reminder: never on the first launch; on the second launch or
    // after the first solved level, once, and only if it is off. ----
    var askReminder by mutableStateOf(false)
        private set
    private var reminderDue = false
    /** On, and Android lets us notify (Android 13+ needs the permission even though the reminder is on by default). */
    private fun reminderOn() = com.aaideas.krossvord.remind.DailyReminder.let { it.isOn(getApplication()) && it.canNotify(getApplication()) }

    private fun maybeAskReminder() {
        if (!reminderDue || progress.reminderAsked || reminderOn() || welcome != null || showWhatsNew || whatsNewDue) return
        reminderDue = false
        progress.reminderAsked = true
        askReminder = true
    }

    fun closeReminderAsk() { askReminder = false }

    // ---- "What's new": once after an update (not on a fresh install), and from settings ----
    var showWhatsNew by mutableStateOf(false)
        private set
    private var whatsNewDue = false
    fun openWhatsNew() { showWhatsNew = true }
    fun closeWhatsNew() {
        showWhatsNew = false
        progress.whatsNewSeen = com.aaideas.krossvord.BuildConfig.VERSION_CODE
        maybeAskReminder()
    }
    private fun maybeShowWhatsNew() {
        if (whatsNewDue && welcome == null) { whatsNewDue = false; showWhatsNew = true }
    }

    /** Called once when the app opens. Gives the birthday life (once a year). */
    fun onAppOpen() {
        // Players from before this existed have progress but no record: they get it too.
        val existing = progress.launches > 0 || completed.isNotEmpty()
        val seen = progress.whatsNewSeen
        if (existing && seen < com.aaideas.krossvord.BuildConfig.VERSION_CODE) whatsNewDue = true
        else if (seen == 0) progress.whatsNewSeen = com.aaideas.krossvord.BuildConfig.VERSION_CODE
        progress.launches = progress.launches + 1
        if (progress.launches >= 2) reminderDue = true
        openWelcome()
        rollMonth()
        autoUpdatePacks()
        maybeShowWhatsNew()
        maybeAskReminder()
    }

    private fun openWelcome() {
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

    /** The app came back to the front: a new day may have started while it slept. */
    fun onResumeDay() {
        rollMonth()
        daily.refresh()
        dailyTick++
        tickLives()
        if (progress.welcomeDay != today().toEpochDay() && welcome == null) openWelcome()
        autoUpdatePacks()
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
        if (Greeting.isBirthday(today(), md) && progress.birthdayGiftYear != today().year) openWelcome()
        maybeAskReminder()
    }

    fun dismissWelcome() {
        if (welcome == Welcome.PROFILE) progress.profileAsked = true
        welcome = null
        maybeShowWhatsNew()
        maybeAskReminder()
    }

    fun hello(): String = Greeting.hello(java.time.LocalTime.now().hour, playerName)

    var largeMode by mutableStateOf(progress.largeMode)
        private set
    /** Until the player sets the switch, large mode follows the phone's own font size. */
    var largeModeChosen by mutableStateOf(progress.largeModeChosen)
        private set

    /** Text size picked on the slider (one of TEXT_SCALES), or 0 when never picked. */
    var textScale by mutableStateOf(progress.textScale)
        private set

    /**
     * Font scale for the whole app, given the phone's own (Configuration.fontScale). Never smaller
     * than the phone's. Until the player picks a size, a big phone font turns large mode on.
     */
    fun appFontScale(systemFontScale: Float): Float = when {
        textScale > 0f -> maxOf(systemFontScale, textScale)
        largeModeChosen -> if (largeMode) maxOf(systemFontScale, LARGE_FONT_SCALE) else systemFontScale
        systemFontScale >= AUTO_LARGE_FONT_SCALE -> maxOf(systemFontScale, LARGE_FONT_SCALE)
        else -> systemFontScale
    }

    /** Is the app's text bigger than the phone's own? */
    fun largeOn(systemFontScale: Float): Boolean = appFontScale(systemFontScale) > systemFontScale

    /** The slider's position: the picked size, or the size in use now. */
    fun shownTextScale(systemFontScale: Float): Float =
        if (textScale > 0f) textScale else TEXT_SCALES.minBy { kotlin.math.abs(it - appFontScale(systemFontScale)) }

    fun changeTextScale(scale: Float) {
        textScale = scale
        progress.textScale = scale
        largeMode = scale > 1f; largeModeChosen = true; progress.largeMode = largeMode
    }

    fun changeLargeMode(on: Boolean) = changeTextScale(if (on) LARGE_FONT_SCALE else 1f)

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
            addMistake(e.answer)
            feedback = WordFeedback.WRONG
            viewModelScope.launch {
                delay(700)
                if (feedback == WordFeedback.WRONG) { draftPicks = emptyList(); feedback = null }
            }
        }
    }

    fun shuffleTiles() { tileShuffle++ }

    /** Pack index and 1-based number inside its pack for a global level index. */
    private var packStart: List<Int> = packs.runningFold(0) { acc, p -> acc + p.levels.size }

    /** Puts new packs in (after a download) and keeps the current level and chosen pack. */
    fun reloadPacks(newPacks: List<Pack>) {
        val levelId = levels.getOrNull(levelIndex)?.id
        val packId = packs.getOrNull(selectedPack)?.packId
        packs = newPacks
        levels = newPacks.flatMap { it.levels }
        packStart = newPacks.runningFold(0) { acc, p -> acc + p.levels.size }
        medals = Medals.all(newPacks)
        levelIndex = levels.indexOfFirst { it.id == levelId }.coerceAtLeast(0)
        selectedPack = packId?.let { id -> newPacks.indexOfFirst { it.packId == id } } ?: -1
    }

    // ---- Downloadable packs (screen "Yeni paketlər") ----
    private val downloads = com.aaideas.krossvord.data.DownloadedPacks(app)
    /** Packs offered online, as last seen (kept for offline). */
    var remotePacks by mutableStateOf(downloads.cachedIndex()?.packs.orEmpty())
        private set
    enum class Online { IDLE, LOADING, OK, OFFLINE }
    var online by mutableStateOf(Online.IDLE)
        private set
    /** Pack ids being downloaded now. */
    val downloading = androidx.compose.runtime.mutableStateMapOf<String, Boolean>()
    var downloadNote by mutableStateOf<String?>(null)

    /** Version of a pack the game has (bundled or downloaded), or null. */
    fun haveVersion(packId: String): Int? = packs.firstOrNull { it.packId == packId }?.contentVersion
    fun isBundled(packId: String): Boolean = packs.any { it.packId == packId } && !downloads.installedVersions().containsKey(packId)

    fun refreshRemote() {
        if (online == Online.LOADING || !networkOn) return
        online = Online.LOADING
        viewModelScope.launch {
            val list = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { downloads.fetchIndex().packs }.getOrNull() }
            if (list != null) { remotePacks = list; online = Online.OK } else online = Online.OFFLINE
        }
    }

    fun downloadPack(r: com.aaideas.krossvord.data.RemotePack) {
        if (downloading[r.packId] == true) return
        downloading[r.packId] = true
        downloadNote = null
        viewModelScope.launch {
            val app = getApplication<Application>()
            val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { downloads.download(r); com.aaideas.krossvord.data.PackCache.reload(app) }.getOrNull()
            }
            downloading.remove(r.packId)
            if (ok != null) {
                reloadPacks(ok)
                downloadNote = "✓ «${r.title}» yükləndi: ${r.levels} səviyyə"
            } else downloadNote = "Yükləmək alınmadı. İnterneti yoxlayıb yenidən cəhd edin."
        }
    }

    fun removePack(packId: String) {
        downloads.remove(packId)
        reloadPacks(com.aaideas.krossvord.data.PackCache.reload(getApplication()))
    }

    /**
     * Once a day, quietly: fetch the list, download new weekly surprises and newer versions of
     * downloaded packs, so they are ready offline. Never blocks; no internet means nothing happens.
     */
    private fun autoUpdatePacks() {
        if (!networkOn || downloads.lastCheckDay == dayNum()) return
        viewModelScope.launch {
            val app = getApplication<Application>()
            val day = dayNum()
            val fresh = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val index = downloads.fetchIndex()
                    downloads.lastCheckDay = day
                    val have = downloads.installedVersions()
                    val bundled = packs.map { it.packId }.toSet() - have.keys
                    val want = index.packs.filter { r ->
                        r.packId !in bundled && (have[r.packId]?.let { it < r.contentVersion } ?: com.aaideas.krossvord.model.Weekly.isWeekly(r.packId))
                    }
                    want.forEach { runCatching { downloads.download(it) } }
                    index.packs to (if (want.isEmpty()) null else com.aaideas.krossvord.data.PackCache.reload(app))
                }.getOrNull()
            } ?: return@launch
            remotePacks = fresh.first
            fresh.second?.let { reloadPacks(it) }
        }
    }

    /** Off in tests (Robolectric): no list check, no downloads. */
    var networkOn = android.os.Build.FINGERPRINT != "robolectric"

    /** Seconds before the "stuck?" pill; tests shorten it. */
    var stuckSeconds = STUCK_SECONDS

    /**
     * How the stuck help shows. Default: the quiet golden glow. A mascot (Settings → Köməkçi)
     * replaces it with a little helper and a speech bubble. PILL is kept as an option in code.
     */
    var stuckStyle by mutableStateOf(runCatching { StuckStyle.valueOf(progress.helper) }.getOrDefault(StuckStyle.GLOW))

    /** Settings: the mascot, or null for none (glow). */
    val helper: StuckStyle? get() = stuckStyle.takeIf { it in MASCOTS }

    fun changeHelper(h: StuckStyle?) {
        stuckStyle = h ?: StuckStyle.GLOW
        progress.helper = h?.name.orEmpty()
    }
    fun packIndexOf(index: Int): Int = (packs.indices.lastOrNull { packStart[it] <= index } ?: 0)
    fun numberInPack(index: Int): Int = index - packStart[packIndexOf(index)] + 1
    fun levelTitle(index: Int): String {
        val p = packIndexOf(index)
        return (if (p == 0) "" else packs[p].title + " · ") + "Səviyyə ${numberInPack(index)}"
    }
    fun packLevels(p: Int): List<Pair<Int, Level>> = packs[p].levels.mapIndexed { i, l -> packStart[p] + i to l }

    /** Pack shown on the level list; follows the level being played. */
    var selectedPack by mutableStateOf(-1)

    /** Pack whose levels the home screen shows: the one picked, else the one being played. */
    val homePack: Int get() = selectedPack.takeIf { it in packs.indices && packOpen(it) } ?: packIndexOf(continueIndex())

    val currentLevelNumber: Int get() = numberInPack(levelIndex)
    /** "7 / 16": the level's place in its pack; null for the daily mini crossword. */
    val currentLevelCount: String? get() = if (playingMini) null else "$currentLevelNumber / ${packs[packIndexOf(levelIndex)].levels.size}"
    val currentLevelTitle: String get() = if (playingMini) "Mini krossvord" else levelTitle(levelIndex)

    // ---- Daily mini-crossword: one small grid a day from assets/mini, no timer ----
    val miniLevels: List<Level> by lazy {
        runCatching {
            getApplication<Application>().assets.open("mini/az-mini.json").bufferedReader(Charsets.UTF_8).use { Pack.parse(it.readText()).levels }
        }.getOrDefault(emptyList())
    }
    val miniToday: Level? get() = miniLevels.takeIf { it.isNotEmpty() }?.let {
        val order = it.indices.shuffled(java.util.Random(1828L))
        it[order[Math.floorMod(dayNum(), it.size.toLong()).toInt()]]
    }
    val miniSolvedToday: Boolean get() { dailyTick; return dailyStore.miniSolvedDay == dayNum() }
    var playingMini by mutableStateOf(false)
        private set

    fun startMini() {
        val level = miniToday ?: return
        playingMini = true
        game = GameState.start(Board(level)).copy(
            typed = progress.loadTyped(level.id),
            revealed = progress.loadRevealed(level.id),
            clueTiers = startTiers(level),
        ).let { s -> s.selectEntry(level.entries.firstOrNull { !s.isEntrySolved(it) } ?: level.entries.first()) }
        showCompletion = false
        feedback = null
        draftPicks = emptyList()
        mistakes = 0
        hintsUsed = 0
        progressed()
        if (praiseVoice) voice.warmUp()
        tickLives()
        screen = Screen.PUZZLE
    }

    fun open(s: Screen) {
        if (s == Screen.HOME && completed.isNotEmpty()) { reminderDue = true; maybeAskReminder() }
        if (s == Screen.WORD_OF_DAY) daily.refresh()
        screen = s
    }

    fun back() {
        screen = when (screen) {
            Screen.PUZZLE -> if (playingMini) Screen.DAILY.also { flushTime(); playingMini = false } else Screen.HOME.also { if (completed.isNotEmpty()) { reminderDue = true; maybeAskReminder() } }
            else -> Screen.HOME
        }
    }

    /** First unfinished level, or the last one when everything is done. */
    fun continueIndex(): Int = levels.indices.firstOrNull { levels[it].id !in completed && levelOpen(it) } ?: levels.lastIndex


    /** Holiday packs are open only around the holiday (or once the player has started them). */
    fun packOpen(p: Int): Boolean {
        val pack = packs[p]
        if (kidsMode && kidsPack >= 0) return p == kidsPack
        if (com.aaideas.krossvord.model.Weekly.isWeekly(pack.packId)) {
            val position = packs.subList(0, p).count { com.aaideas.krossvord.model.Weekly.isWeekly(it.packId) }
            return com.aaideas.krossvord.model.Weekly.isOpen(position, today()) || pack.levels.any { it.id in completed || progress.hasProgress(it.id) }
        }
        return com.aaideas.krossvord.model.Holidays.packOpen(pack.packId, today()) ||
            pack.levels.any { it.id in completed || progress.hasProgress(it.id) }
    }
    fun levelOpen(index: Int) = packOpen(packIndexOf(index))

    // ---- "Ayın yolu": a monthly road trip, moved by levels solved this month ----
    private fun monthKeyOf(d: java.time.LocalDate) = d.year * 100 + d.monthValue
    /** Levels solved for the first time this month (resets when the month changes). */
    var monthLevels by mutableStateOf(0)
        private set
    var monthFinishDay by mutableStateOf(0)
        private set
    val bestFinishDay: Int get() { monthLevels; return progress.bestFinishDay }
    val route: com.aaideas.krossvord.model.Route get() = com.aaideas.krossvord.model.Challenge.routeFor(java.time.YearMonth.from(today()))

    private fun rollMonth() {
        val key = monthKeyOf(today())
        if (progress.monthKey != key) {
            progress.monthKey = key; progress.monthLevels = 0; progress.monthFinishDay = 0
        }
        monthLevels = progress.monthLevels
        monthFinishDay = progress.monthFinishDay
    }

    /** A level solved for the first time: the car moves; at the end, free hints. Returns a line for the result card. */
    private fun advanceTrip(): String? {
        rollMonth()
        val c = com.aaideas.krossvord.model.Challenge
        if (monthLevels >= c.GOAL) return null
        val before = c.position(route, monthLevels).toInt()
        monthLevels++; progress.monthLevels = monthLevels
        if (monthLevels >= c.GOAL) {
            val day = today().dayOfMonth
            monthFinishDay = day; progress.monthFinishDay = day
            if (progress.bestFinishDay == 0 || day < progress.bestFinishDay) progress.bestFinishDay = day
            freeHints += c.REWARD_HINTS; progress.freeHints = freeHints
            return "🏁 ${route.places.last().name}! Ayın yolu ${day}-də başa çatdı. Hədiyyə: ${c.REWARD_HINTS} pulsuz ipucu"
        }
        val after = c.position(route, monthLevels).toInt()
        return if (after > before) "🚗 Ayın yolu: ${route.places[after].name} çatdınız!" else null
    }

    init { rollMonth() }

    /** The newest open weekly surprise with levels left, for the home card; null when none. */
    val weeklyPack: Int? get() = packs.indices.lastOrNull { com.aaideas.krossvord.model.Weekly.isWeekly(packs[it].packId) && packOpen(it) }
        ?.takeIf { p -> packs[p].levels.any { it.id !in completed } }

    fun startWeekly() {
        val p = weeklyPack ?: return
        val i = packLevels(p).firstOrNull { (_, l) -> l.id !in completed }?.first ?: return
        startLevel(i)
    }

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
        checkRest()
        playingMini = false
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
        progressed()
        if (praiseVoice) voice.warmUp()
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
        if (playingMini) { miniToday?.let { progress.resetLevel(it.id) }; startMini(); return }
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
        progressed()
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
        if (playingMini) { playingMini = false; game = null; screen = Screen.DAILY; return }
        val go = {
            val next = (levelIndex + 1 until levels.size).firstOrNull { levelOpen(it) }
            if (next != null) startLevel(next) else screen = Screen.HOME
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
        if (found > 0) {
            progressed()
            if (s.isComplete && !old.isComplete) sounds.levelSolved() else { sounds.wordFound(); praiseWord() }
        }
        s.board.level.entries.filter { s.isEntryWrong(it) && !old.isEntryWrong(it) }.forEach { addMistake(it.answer) }
        if (s.isComplete && !old.isComplete) {
            val medalsBefore = earnedMedals()
            if (mistakes == 0) {
                progress.perfectLevels = progress.perfectLevels + 1
                progress.perfectRun = progress.perfectRun + 1
                progress.bestPerfectRun = maxOf(progress.bestPerfectRun, progress.perfectRun)
            } else progress.perfectRun = 0
            if (playingMini) {
                dailyStore.miniSolvedDay = dayNum()
                newMedals = emptyList()
            } else {
                progress.markCompleted(id)
                newMedals = medals.filter { it.id in earnedMedalsAfterMark(id) - medalsBefore }
            }
            flushTime()
            val stars = LevelResult.stars(mistakes, hintsUsed)
            val rankUp = addXp(com.aaideas.krossvord.model.Ranks.levelXp(stars))
            val trip = if (!playingMini && id !in completed) advanceTrip() else null
            val before = com.aaideas.krossvord.model.Journey.opened(completed.size)
            val after = com.aaideas.krossvord.model.Journey.opened(progress.completedLevels().size)
            result = LevelResult(
                trip = trip,
                city = if (after > before && !playingMini) com.aaideas.krossvord.model.Journey.CITIES[after - 1] else null,
                rankUp = rankUp,
                xp = com.aaideas.krossvord.model.Ranks.levelXp(stars),
                stars = stars,
                seconds = progress.levelSeconds(id),
                praise = LevelResult.PRAISE.random(),
                keyword = s.keyword?.word,
                medals = newMedals,
                fact = nextFact(),
            )
            if (playingMini) track(TaskKind.MINI) else track(TaskKind.LEVELS)
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
data class LevelResult(val trip: String? = null, val city: com.aaideas.krossvord.model.City? = null, val rankUp: com.aaideas.krossvord.model.Rank? = null, val xp: Int = 0, val stars: Int, val seconds: Long, val praise: String, val keyword: String?, val medals: List<Medal>, val fact: String? = null) {
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
