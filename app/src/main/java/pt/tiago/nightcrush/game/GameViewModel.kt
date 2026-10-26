package pt.tiago.nightcrush.game

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pt.tiago.nightcrush.R
import pt.tiago.nightcrush.core.model.Blockers
import pt.tiago.nightcrush.core.model.Cell
import pt.tiago.nightcrush.core.model.Difficulty
import pt.tiago.nightcrush.core.model.GameState
import pt.tiago.nightcrush.core.model.LevelBuilder
import pt.tiago.nightcrush.core.model.LevelEntry
import pt.tiago.nightcrush.core.model.LevelFile
import pt.tiago.nightcrush.core.model.LimitType
import pt.tiago.nightcrush.core.model.Move
import pt.tiago.nightcrush.core.model.MoveSpec
import pt.tiago.nightcrush.core.model.Outcome
import pt.tiago.nightcrush.core.model.Pieces
import pt.tiago.nightcrush.core.model.PreLevelBooster
import pt.tiago.nightcrush.core.model.PuzzleEntry
import pt.tiago.nightcrush.core.model.SpecialKind
import pt.tiago.nightcrush.core.model.WorldEntry
import pt.tiago.nightcrush.core.random.SplitMix64
import pt.tiago.nightcrush.core.rules.GameEngine
import pt.tiago.nightcrush.core.rules.GameEvent
import pt.tiago.nightcrush.core.rules.PuzzleSolver
import pt.tiago.nightcrush.data.BoosterInventory
import pt.tiago.nightcrush.data.CatalogRepository
import pt.tiago.nightcrush.data.Profile
import pt.tiago.nightcrush.data.ProfileRepository
import pt.tiago.nightcrush.data.SavedMatch

/** Which mode the current session belongs to. */
enum class GameMode { CAMPAIGN, DAILY, ZEN, TIME_ATTACK, ENDLESS, PUZZLE }

enum class UiScreen { HOME, MAP, INTRO, GAME, RESULT, SETTINGS, PROFILE, COLLECTION, FREE_PLAY, DAILY, ABOUT, MISSIONS, EVENT, ACHIEVEMENTS, PUZZLES }

/** In-level boosters that are tapped on the board. */
enum class ActiveBooster { HARPOON, FREE_SWAP, SHUFFLE }

/** Localised text for the view model; the app passes resources, tests pass a stub. */
fun interface Texts {
    fun get(@StringRes id: Int, vararg args: Any): String
}

data class ResultState(
    val won: Boolean,
    val stars: Int,
    val score: Int,
    val pearls: Int,
    val streak: Int,
    val reason: Outcome.Lost?,
    val mode: GameMode,
    val lifeLost: Boolean,
)

/** What came out of a chest, for the opening dialog. */
data class ChestOpening(
    val world: Boolean,
    /** 0-based world of a world chest. */
    val worldIndex: Int = -1,
    val pearls: Int,
    val boosters: BoosterInventory,
    /** Sticker id to "new" (false for a duplicate turned into pearls). */
    val stickers: List<Pair<String, Boolean>>,
    val infiniteMinutes: Int,
)

data class UiState(
    val screen: UiScreen = UiScreen.HOME,
    val profile: Profile,
    val worlds: List<WorldEntry> = emptyList(),
    val levels: List<LevelEntry> = emptyList(),
    /** Ordinals that are packaged and playable; gaps in the catalog are skipped. */
    val available: Set<Int> = emptySet(),
    val dailyEntry: LevelFile? = null,
    val worldIndex: Int = 0,
    val difficulty: Difficulty = Difficulty.EASY,
    val level: LevelFile? = null,
    val mode: GameMode = GameMode.CAMPAIGN,
    val board: GameState? = null,
    val timeline: AnimationTimeline? = null,
    val selected: Cell? = null,
    val hint: Pair<Cell, Cell>? = null,
    val animating: Boolean = false,
    val paused: Boolean = false,
    /** Tutorial card shown over a paused board until the player dismisses it. */
    val tutorialKey: String? = null,
    /** Settings opened from the pause menu return to the game. */
    val settingsFromGame: Boolean = false,
    val activeBooster: ActiveBooster? = null,
    val result: ResultState? = null,
    val pendingBoosters: List<PreLevelBooster> = emptyList(),
    val endlessRun: Int = 0,
    val endlessMovesCarried: Int = 0,
    val timeLeftSec: Int = 0,
    val message: String? = null,
    val chest: ChestOpening? = null,
    /** Shuffle asks first when "confirm boosters" is on, because it is spent at once. */
    val confirmShuffle: Boolean = false,
    /** The campaign was blocked for lack of lives: show the wait and the free modes. */
    val noLives: Boolean = false,
    /** The puzzle packs, loaded the first time the puzzle screen opens. */
    val puzzles: List<PuzzleEntry> = emptyList(),
    val puzzlePack: Int = 1,
    val puzzle: PuzzleEntry? = null,
    /** Hints used in the current puzzle; they cost stars. */
    val puzzleHints: Int = 0,
    /** Spoken by TalkBack after each move ("Ray created, 12 pieces cleared, cascade 3"). */
    val announcement: String? = null,
    val dailyStep: Int? = null,
    val recoveryNote: String? = null,
    val catalogReady: Boolean = false,
    /** Wall clock for countdowns on screen, refreshed every second. */
    val nowEpochMs: Long = 0,
    val today: Long = 0,
) {
    fun isWon(ordinal: Int, difficulty: Difficulty = this.difficulty): Boolean =
        (profile.levels[ProfileReducer.key(ordinal, difficulty)]?.wins ?: 0) > 0

    /** A level opens once the previous playable level is won; missing levels never block. */
    fun isUnlocked(ordinal: Int): Boolean {
        if (ordinal !in available) return false
        val previous = available.filter { it < ordinal }.maxOrNull() ?: return true
        return isWon(previous)
    }

    /** The first playable level not yet won at the current difficulty. */
    fun nextOrdinal(): Int {
        val ordered = available.sorted()
        return ordered.firstOrNull { !isWon(it) } ?: ordered.lastOrNull() ?: 1
    }
}

/**
 * Owns the session and the screens. The engine resolves every move in one pure
 * step; this class only schedules the animation and persists progress.
 */
class GameViewModel(
    private val profiles: ProfileRepository,
    private val catalog: CatalogRepository,
    private val audio: AudioController,
    private val haptics: Haptics,
    private val texts: Texts,
    private val clock: () -> Long = System::currentTimeMillis,
    /** True when the system has animations turned off; reduced motion follows it until the player chooses. */
    private val systemReducedMotion: () -> Boolean = { false },
) : ViewModel() {

    private val _ui = MutableStateFlow(
        UiState(
            profile = profiles.profile.value,
            recoveryNote = profiles.recoveryNote(),
        ),
    )
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    /** The moves of the match in progress, for saved games, and their kinds. */
    private val currentMoves = ArrayList<MoveSpec>()
    private val currentKinds = ArrayList<String>()
    private var currentMode = GameMode.CAMPAIGN
    private var currentLevel: LevelFile? = null
    private var endlessScore = 0
    /** Everything placed before the first move (picked boosters plus the streak bonus), for saved games. */
    private var startBoosters: List<PreLevelBooster> = emptyList()

    /** Play time not yet written to the profile; flushed when a level ends or the app leaves. */
    private var unsavedPlayMillis = 0L

    /** Specials made this game, by [SpecialKind] ordinal; counted here because the engine's counters feed the certificate hashes. */
    private val specialsThisGame = IntArray(SpecialKind.entries.size)

    private var timeJob: Job? = null
    private var animationJob: Job? = null
    private var hintJob: Job? = null
    private var timeLeftMs = 0L
    private var timeBonusMs = 0L

    init {
        val catalogFile = runCatching { catalog.catalog() }.getOrNull()
        val worlds = catalogFile?.worlds.orEmpty()
        val levels = catalogFile?.levels.orEmpty().sortedBy { it.ordinal }
        val today = Missions.epochDay(clock())
        _ui.value = _ui.value.copy(
            worlds = worlds,
            levels = levels,
            available = levels.mapTo(HashSet()) { it.ordinal },
            dailyEntry = DailyPicker.pick(catalog, levels, today, _ui.value.difficulty),
            catalogReady = worlds.isNotEmpty(),
            nowEpochMs = clock(),
            today = today,
        )
        val settingsNow = profiles.profile.value.settings
        if (!settingsNow.reducedMotionChosen && settingsNow.reducedMotion != systemReducedMotion()) {
            profiles.update { it.copy(settings = it.settings.copy(reducedMotion = systemReducedMotion())) }
        }
        // The screens always show the live profile; every change may unlock achievements.
        val achievementCatalog = Achievements.Catalog(
            worlds = worlds.map { it.from..it.to },
            fullMoons = levels.filter { it.isFullMoon }.mapTo(HashSet()) { it.ordinal },
            available = levels.mapTo(HashSet()) { it.ordinal },
        )
        viewModelScope.launch {
            profiles.profile.collect { profile ->
                _ui.update { it.copy(profile = profile) }
                if (worlds.isNotEmpty()) awardAchievements(profile, achievementCatalog)
            }
        }
        // Countdowns (next life, infinite lives) and the daily turnover.
        viewModelScope.launch {
            var lastRefill = 0L
            while (isActive) {
                val now = clock()
                _ui.update { it.copy(nowEpochMs = now, today = Missions.epochDay(now)) }
                val ui = _ui.value
                if (ui.screen == UiScreen.GAME && !ui.paused && ui.result == null) unsavedPlayMillis += 1_000L
                if (now - lastRefill >= 30_000L) {
                    lastRefill = now
                    profiles.update { LifeRules.refill(it, now) }
                }
                delay(1_000L)
            }
        }
    }

    private fun awardAchievements(profile: Profile, catalog: Achievements.Catalog) {
        val fresh = Achievements.newlyUnlocked(profile, catalog)
        if (fresh.isEmpty()) return
        profiles.update { it.copy(achievements = it.achievements + fresh) }
        audio.onSticker()
        val names = fresh.joinToString(", ") { texts.get(Achievements.titleRes(it)) }
        _ui.update { it.copy(message = texts.get(R.string.ach_unlocked, names)) }
    }

    private fun flushPlayTime() {
        val millis = unsavedPlayMillis
        if (millis <= 0L) return
        unsavedPlayMillis = 0L
        profiles.update { it.copy(stats = it.stats.copy(timePlayedMillis = it.stats.timePlayedMillis + millis)) }
    }

    private fun message(@StringRes id: Int, vararg args: Any) {
        _ui.update { it.copy(message = texts.get(id, *args)) }
    }

    // ------------------------------------------------------------------ timer

    /** Counts down only while the board is idle on screen: animations and pause stop the clock. */
    private fun startTimerFor(state: GameState) {
        timeJob?.cancel()
        timeJob = null
        if (state.limitType != LimitType.TIME) return
        timeLeftMs = state.timeLeftSec * 1000L
        timeBonusMs = 0L
        _ui.update { it.copy(timeLeftSec = state.timeLeftSec) }
        timeJob = viewModelScope.launch {
            var last = clock()
            while (isActive) {
                delay(100L)
                val now = clock()
                val elapsed = (now - last).coerceIn(0L, 1_000L)
                last = now
                val ui = _ui.value
                if (ui.screen != UiScreen.GAME || ui.paused || ui.animating || ui.result != null) continue
                timeLeftMs -= elapsed
                if (timeLeftMs <= 0L) {
                    timeLeftMs = 0L
                    _ui.update { it.copy(timeLeftSec = 0, selected = null, activeBooster = null) }
                    playMove(Move.TimeUp)
                    break
                }
                val seconds = ((timeLeftMs + 999L) / 1000L).toInt()
                if (seconds != ui.timeLeftSec) _ui.update { it.copy(timeLeftSec = seconds) }
            }
        }
    }

    private fun stopTimer() {
        timeJob?.cancel()
        timeJob = null
    }

    /** Leaves whatever match is on screen without touching lives or progress. */
    private fun dropMatch() {
        stopTimer()
        animationJob?.cancel()
        hintJob?.cancel()
        _ui.update {
            it.copy(
                board = null, timeline = null, animating = false, paused = false,
                selected = null, hint = null, activeBooster = null, settingsFromGame = false,
            )
        }
    }

    // --------------------------------------------------------------- navigation

    private fun goTo(screen: UiScreen) {
        _ui.update { it.copy(screen = screen, message = null) }
    }

    fun onHome() {
        if (_ui.value.screen == UiScreen.RESULT) dropMatch()
        goTo(UiScreen.HOME)
    }

    fun onOpenMap() {
        if (_ui.value.screen == UiScreen.RESULT) dropMatch()
        val ui = _ui.value
        // Open the map on the world of the next level to play.
        val world = ((ui.nextOrdinal() - 1) / 50).coerceIn(0, (ui.worlds.size - 1).coerceAtLeast(0))
        _ui.update { it.copy(screen = UiScreen.MAP, worldIndex = if (ui.screen == UiScreen.HOME) world else it.worldIndex) }
    }

    fun onOpenSettings() {
        val fromGame = _ui.value.screen == UiScreen.GAME
        _ui.update { it.copy(screen = UiScreen.SETTINGS, settingsFromGame = fromGame, paused = it.paused || fromGame) }
    }

    fun onOpenProfile() = goTo(UiScreen.PROFILE)
    fun onOpenCollection() = goTo(UiScreen.COLLECTION)
    fun onOpenFreePlay() {
        if (_ui.value.screen == UiScreen.RESULT) dropMatch()
        goTo(UiScreen.FREE_PLAY)
    }
    fun onOpenAbout() = goTo(UiScreen.ABOUT)
    fun onOpenMissions() = goTo(UiScreen.MISSIONS)

    fun onOpenDaily() {
        val today = Missions.epochDay(clock())
        _ui.update {
            it.copy(
                screen = UiScreen.DAILY,
                today = today,
                dailyEntry = DailyPicker.pick(catalog, it.levels, today, it.difficulty),
            )
        }
    }

    fun onSelectDailyLevel(ordinal: Int, mode: GameMode) {
        onSelectLevel(ordinal, mode)
    }

    fun exportPayload(): String = profiles.exportPayload()

    fun onExported() = message(R.string.msg_saved)

    fun importPayload(text: String): Boolean {
        val ok = profiles.importPayload(text).isSuccess
        message(if (ok) R.string.msg_imported else R.string.msg_import_failed)
        return ok
    }

    fun onClaimMission(index: Int) {
        profiles.update { Missions.claim(it, index) }
        audio.onSticker()
    }

    /** Opens one star chest and shows what came out. */
    fun onOpenChest() {
        val profile = profiles.profile.value
        if (profile.unopenedStarChests <= 0) {
            message(R.string.msg_chest_empty, 20 - ProfileReducer.totalStars(profile) % 20)
            return
        }
        openChest(world = false, chestIndex = profile.starChestsOpened, worldIndex = -1, worldKey = null)
    }

    private fun openChest(world: Boolean, chestIndex: Int, worldIndex: Int, worldKey: String?) {
        val day = Missions.epochDay(clock())
        val bonus = WeeklyEvents.kindFor(day) == WeeklyEvents.Kind.STICKERS
        val before = profiles.profile.value
        val reward = ChestRules.reward(before, chestIndex, worldChest = world, bonusSticker = bonus)
        val pagesBefore = StickerBook.completePages(before.collection)
        val owned = before.collection.stickers.filterValues { it > 0 }.keys.toMutableSet()
        val stickers = reward.stickers.map { id -> id to owned.add(id) }
        profiles.update { current ->
            var opened = ChestRules.open(current, reward, worldChest = world, worldKey = worldKey)
            val pagePearls = StickerBook.pearlsForPageCompletion(opened.collection, pagesBefore)
            if (pagePearls > 0) opened = opened.copy(pearls = opened.pearls + pagePearls)
            if (bonus) opened = WeeklyEvents.afterBonusSticker(opened, day)
            opened
        }
        audio.onChest()
        val gained = profiles.profile.value.pearls - before.pearls
        _ui.update {
            it.copy(
                chest = ChestOpening(
                    world = world,
                    worldIndex = worldIndex,
                    pearls = gained,
                    boosters = reward.boosters,
                    stickers = stickers,
                    infiniteMinutes = if (world) 30 else 0,
                ),
            )
        }
    }

    /** Hands out the world chest once every level of the world is won on this difficulty. */
    private fun checkWorldChest(level: LevelFile?, difficulty: Difficulty) {
        level ?: return
        val ui = _ui.value
        val worldIndex = ui.worlds.indexOfFirst { level.ordinal in it.from..it.to }
        if (worldIndex < 0) return
        val world = ui.worlds[worldIndex]
        val key = ChestRules.worldKey(worldIndex, difficulty)
        val profile = profiles.profile.value
        if (key in profile.worldChestsClaimed) return
        val complete = (world.from..world.to).filter { it in ui.available }
            .all { (profile.levels[ProfileReducer.key(it, difficulty)]?.wins ?: 0) > 0 }
        if (!complete) return
        openChest(world = true, chestIndex = worldIndex * 3 + difficulty.ordinal, worldIndex = worldIndex, worldKey = key)
    }

    fun onDismissChest() {
        _ui.update { it.copy(chest = null) }
    }

    fun onClaimEvent() {
        val before = profiles.profile.value.pearls
        profiles.update { WeeklyEvents.claim(it, Missions.epochDay(clock())) }
        val gained = profiles.profile.value.pearls - before
        if (gained > 0) {
            audio.onChest()
            message(R.string.event_claimed, gained)
        }
    }

    fun onOpenEvent() = goTo(UiScreen.EVENT)

    fun onOpenAchievements() = goTo(UiScreen.ACHIEVEMENTS)

    fun onClaimDailyReward() {
        val day = Missions.epochDay(clock())
        val next = profiles.profile.value.dailyRewardDay % DailyReward.steps.size + 1
        val claimed = DailyReward.claim(profiles.profile.value, day, SplitMix64(day))
        if (claimed != null) {
            profiles.update {
                val updated = claimed.first
                updated.copy(stats = updated.stats.copy(dailyRewardsClaimed = updated.stats.dailyRewardsClaimed + 1))
            }
            audio.onChest()
        }
        _ui.update { it.copy(dailyStep = null, message = texts.get(R.string.msg_daily_claimed, next)) }
    }

    private fun refreshDailyReward() {
        val profile = profiles.profile.value
        val day = Missions.epochDay(clock())
        if (!DailyReward.isClaimedToday(profile, day)) {
            _ui.update { it.copy(dailyStep = profile.dailyRewardDay % DailyReward.steps.size + 1) }
        }
    }

    fun onWorldChanged(index: Int) {
        _ui.update { it.copy(worldIndex = index.coerceIn(0, (it.worlds.size - 1).coerceAtLeast(0))) }
    }

    fun onDifficultyChanged(difficulty: Difficulty) {
        _ui.update { it.copy(difficulty = difficulty) }
    }

    fun onSelectLevel(ordinal: Int, mode: GameMode = GameMode.CAMPAIGN) {
        val level = catalog.level(ordinal)
        if (level == null) {
            message(R.string.msg_level_missing, ordinal)
            return
        }
        dropMatch()
        currentMode = mode
        currentLevel = level
        _ui.update {
            it.copy(
                screen = UiScreen.INTRO,
                level = level,
                mode = mode,
                pendingBoosters = emptyList(),
                result = null,
                message = null,
            )
        }
    }

    fun onToggleBooster(booster: PreLevelBooster) {
        _ui.update {
            val current = it.pendingBoosters
            val next = when {
                current.contains(booster) -> current - booster
                inventoryOf(booster) > 0 -> current + booster
                else -> current
            }
            it.copy(pendingBoosters = next)
        }
    }

    fun onStartLevel() {
        val level = _ui.value.level ?: return
        val difficulty = _ui.value.difficulty
        val boosters = _ui.value.pendingBoosters
        if (currentMode == GameMode.CAMPAIGN && !hasLife(profiles.profile.value)) {
            _ui.update { it.copy(noLives = true) }
            return
        }
        // A campaign win streak adds free specials on top of the ones the player picked.
        val streakBonus = if (currentMode == GameMode.CAMPAIGN) StreakRules.bonus(profiles.profile.value.streak) else emptyList()
        val placed = boosters + streakBonus
        val state = runCatching { LevelBuilder.start(level, difficulty, placed) }.getOrNull()
        if (state == null) {
            message(R.string.msg_level_broken)
            return
        }
        startBoosters = placed
        consumeBoosters(boosters)
        currentMoves.clear()
        currentKinds.clear()
        showBoard(state, level)
        playWorldMusic(level.ordinal)
        saveMatch()
    }

    private fun showBoard(state: GameState, level: LevelFile?) {
        animationJob?.cancel()
        specialsThisGame.fill(0)
        hintJob?.cancel()
        val tutorial = level?.tutorial?.steps?.firstOrNull()?.textKey
            ?.takeIf { it !in profiles.profile.value.tutorialSeen }
        _ui.update {
            it.copy(
                screen = UiScreen.GAME,
                level = level,
                mode = currentMode,
                board = state,
                timeline = null,
                selected = null,
                hint = null,
                animating = false,
                paused = tutorial != null,
                tutorialKey = tutorial,
                settingsFromGame = false,
                activeBooster = null,
                result = null,
                message = null,
                timeLeftSec = if (state.limitType == LimitType.TIME) state.timeLeftSec else 0,
            )
        }
        startTimerFor(state)
        scheduleHintIfIdle()
    }

    private fun playWorldMusic(ordinal: Int) {
        val world = ((ordinal - 1) / 50).coerceIn(0, 11)
        audio.playMusic("music_${world / 2 + 1}")
    }

    // -------------------------------------------------------------- free modes

    /** Zen: an open board with no goals and no limits. */
    fun onStartZen() {
        val level = FreeBoards.zen(seed = clock())
        currentMode = GameMode.ZEN
        currentLevel = level
        currentMoves.clear()
        currentKinds.clear()
        showBoard(LevelBuilder.start(level, Difficulty.EASY), level)
        audio.playMusic("music_zen")
    }

    fun onStartTimeAttack(difficulty: Difficulty) {
        val level = FreeBoards.timeAttack(seed = clock(), difficulty = difficulty)
        currentMode = GameMode.TIME_ATTACK
        currentLevel = level
        currentMoves.clear()
        currentKinds.clear()
        _ui.update { it.copy(difficulty = difficulty) }
        showBoard(LevelBuilder.start(level, difficulty), level)
        audio.playMusic("music_zen")
    }

    fun onStartEndless(difficulty: Difficulty) {
        currentMode = GameMode.ENDLESS
        currentLevel = null
        endlessScore = 0
        _ui.update { it.copy(difficulty = difficulty, endlessRun = 0, endlessMovesCarried = 0, result = null) }
        startEndlessStep()
    }

    // ------------------------------------------------------------------ puzzles

    fun onOpenPuzzles() {
        if (_ui.value.screen == UiScreen.RESULT) dropMatch()
        if (_ui.value.puzzles.isEmpty()) {
            viewModelScope.launch {
                val loaded = withContext(Dispatchers.IO) { catalog.puzzles() }
                _ui.update { it.copy(puzzles = loaded) }
            }
        }
        goTo(UiScreen.PUZZLES)
    }

    fun onPuzzlePack(pack: Int) {
        _ui.update { it.copy(puzzlePack = pack) }
    }

    fun onStartPuzzle(number: Int) {
        val entry = _ui.value.puzzles.firstOrNull { it.number == number } ?: return
        if (!PuzzleRules.isUnlocked(profiles.profile.value, number)) return
        dropMatch()
        currentMode = GameMode.PUZZLE
        currentLevel = entry.level
        currentMoves.clear()
        currentKinds.clear()
        _ui.update { it.copy(puzzle = entry, puzzlePack = entry.pack, puzzleHints = 0, result = null) }
        showBoard(LevelBuilder.start(entry.level, Difficulty.EASY), entry.level)
        audio.playMusic("music_zen")
    }

    /** Puzzle hints come from the solver, so they always lead to a solution from where the board is now. */
    private fun puzzleHint(board: GameState) {
        hintJob?.cancel()
        hintJob = viewModelScope.launch {
            val line = withContext(Dispatchers.Default) { PuzzleSolver.solve(board.copyState(), board.movesLeft) }
            val ui = _ui.value
            if (ui.board !== board || !canPlay(ui)) return@launch
            val first = line?.firstOrNull()
            if (first == null) {
                message(R.string.puzzle_no_way)
                return@launch
            }
            _ui.update { it.copy(hint = first.a to first.b, puzzleHints = it.puzzleHints + 1) }
            haptics.tap()
        }
    }

    /** Endless picks normal move-limited levels in a fixed order per difficulty. */
    private fun startEndlessStep() {
        val ui = _ui.value
        val pool = ui.levels.map { it.ordinal }.sorted()
        if (pool.isEmpty()) {
            message(R.string.msg_not_ready)
            return
        }
        val first = (ui.endlessRun * 7 + ui.difficulty.ordinal * 13) % pool.size
        var level: LevelFile? = null
        for (step in pool.indices) {
            val candidate = catalog.level(pool[(first + step) % pool.size])
            val variant = candidate?.variants?.get(ui.difficulty.name.lowercase())
            if (candidate != null && candidate.tutorial == null && variant?.limit?.type == "moves") {
                level = candidate
                break
            }
        }
        val chosen = level ?: run {
            message(R.string.msg_not_ready)
            return
        }
        currentLevel = chosen
        val state = runCatching { LevelBuilder.start(chosen, ui.difficulty) }.getOrNull() ?: run {
            message(R.string.msg_level_broken)
            return
        }
        if (ui.endlessMovesCarried > 0) state.movesLeft += ui.endlessMovesCarried
        currentMoves.clear()
        currentKinds.clear()
        showBoard(state, chosen)
        playWorldMusic(chosen.ordinal)
    }

    // ---------------------------------------------------------------- gameplay

    private fun canPlay(ui: UiState): Boolean =
        ui.screen == UiScreen.GAME && !ui.animating && !ui.paused && ui.result == null &&
            ui.board?.outcome == Outcome.Playing

    fun onCellTap(cell: Cell) {
        val ui = _ui.value
        val board = ui.board ?: return
        if (!canPlay(ui)) return
        when (ui.activeBooster) {
            ActiveBooster.HARPOON -> {
                useHarpoon(board, cell)
                return
            }
            ActiveBooster.FREE_SWAP -> {
                val selected = ui.selected
                if (selected != null && adjacent(selected, cell)) {
                    if (consumeInLevelBooster(ActiveBooster.FREE_SWAP)) {
                        _ui.update { it.copy(activeBooster = null, selected = null) }
                        playMove(Move.FreeSwap(selected, cell))
                    }
                } else if (movable(board, cell)) {
                    _ui.update { it.copy(selected = cell) }
                }
                return
            }
            else -> Unit
        }
        val selected = ui.selected
        when {
            selected == null -> if (movable(board, cell)) _ui.update { it.copy(selected = cell) }
            selected == cell -> _ui.update { it.copy(selected = null) }
            adjacent(selected, cell) -> {
                _ui.update { it.copy(selected = null) }
                playMove(Move.Swap(selected, cell))
            }
            movable(board, cell) -> _ui.update { it.copy(selected = cell) }
            else -> _ui.update { it.copy(selected = null) }
        }
    }

    fun onSwap(a: Cell, b: Cell) {
        val ui = _ui.value
        val board = ui.board ?: return
        if (!canPlay(ui)) return
        when (ui.activeBooster) {
            ActiveBooster.HARPOON -> useHarpoon(board, a)
            ActiveBooster.FREE_SWAP -> if (consumeInLevelBooster(ActiveBooster.FREE_SWAP)) {
                _ui.update { it.copy(activeBooster = null, selected = null) }
                playMove(Move.FreeSwap(a, b))
            }
            else -> {
                _ui.update { it.copy(selected = null) }
                playMove(Move.Swap(a, b))
            }
        }
    }

    private fun useHarpoon(board: GameState, cell: Cell) {
        val i = board.idx(cell.r, cell.c)
        val target = board.playable[i] &&
            (board.pieces[i] != Pieces.EMPTY || board.blockers[i] != Blockers.NONE || board.silt[i] > 0)
        if (!target) return
        if (!consumeInLevelBooster(ActiveBooster.HARPOON)) return
        _ui.update { it.copy(activeBooster = null, selected = null) }
        playMove(Move.Harpoon(cell))
    }

    private fun adjacent(a: Cell, b: Cell): Boolean = kotlin.math.abs(a.r - b.r) + kotlin.math.abs(a.c - b.c) == 1

    private fun movable(board: GameState, cell: Cell): Boolean {
        if (!board.inBounds(cell.r, cell.c)) return false
        val i = board.idx(cell.r, cell.c)
        return board.playable[i] && board.pieces[i] != Pieces.EMPTY && board.blockers[i] == Blockers.NONE
    }

    fun onUseBooster(booster: ActiveBooster) {
        val ui = _ui.value
        if (!canPlay(ui)) return
        when (booster) {
            ActiveBooster.SHUFFLE -> when {
                profiles.profile.value.boosters.shuffle <= 0 -> Unit
                profiles.profile.value.settings.confirmBoosters -> _ui.update { it.copy(confirmShuffle = true) }
                consumeInLevelBooster(ActiveBooster.SHUFFLE) -> playMove(Move.Shuffle)
            }
            else -> _ui.update {
                it.copy(activeBooster = if (it.activeBooster == booster) null else booster, selected = null)
            }
        }
    }

    fun onHint() {
        val ui = _ui.value
        val board = ui.board ?: return
        if (!canPlay(ui)) return
        if (ui.mode == GameMode.PUZZLE) puzzleHint(board) else computeHint(board, manual = true)
    }

    /** The best swap by immediate score and objective progress, computed off the main thread. */
    private fun computeHint(board: GameState, manual: Boolean) {
        hintJob?.cancel()
        hintJob = viewModelScope.launch {
            val best = withContext(Dispatchers.Default) {
                val probe = board.copyState()
                GameEngine.validSwaps(probe, limit = 64).maxByOrNull { swap ->
                    val result = GameEngine.applyMove(probe, swap, recordEvents = false)
                    result.state.score - probe.score + progressDelta(probe, result.state)
                }
            } ?: return@launch
            val ui = _ui.value
            if (ui.board === board && canPlay(ui)) {
                _ui.update { it.copy(hint = best.a to best.b) }
                if (manual) haptics.tap()
            }
        }
    }

    private fun progressDelta(before: GameState, after: GameState): Int {
        var delta = 0
        for (i in before.progress.indices) {
            val b = before.progress[i]
            val a = after.progress[i]
            for (k in b.indices) delta += (a[k] - b[k]) * 100
        }
        return delta
    }

    private fun playMove(move: Move) {
        val ui = _ui.value
        val board = ui.board ?: return
        if (ui.animating || board.outcome != Outcome.Playing) return
        if (move != Move.TimeUp && (ui.paused || ui.result != null)) return
        hintJob?.cancel()

        // The finale rain in timed levels counts the seconds actually left.
        val input = if (board.limitType == LimitType.TIME) {
            board.copyState().also { it.timeLeftSec = (timeLeftMs / 1000L).toInt() }
        } else {
            board
        }
        val result = GameEngine.applyMove(input, move)
        val settings = profiles.profile.value.settings
        val timeline = AnimationTimeline.build(
            pre = board,
            result = result,
            speed = settings.animationSpeed,
            words = comboWords(),
            formatPoints = { "+$it" },
            reducedMotion = settings.reducedMotion,
        )
        audio.onMove(result.events)
        haptics.onMove(result.events)
        for (event in result.events) if (event is GameEvent.SpecialCreated) specialsThisGame[event.kind.ordinal]++

        if (result.events.any { it is GameEvent.SwapRejected }) {
            haptics.reject()
            _ui.update {
                it.copy(timeline = timeline, animating = true, selected = null, hint = null, announcement = texts.get(R.string.a11y_move_rejected))
            }
            finishAnimation(timeline, settled = false)
            return
        }

        recordMove(move)
        recordEventsToProfile(result.events)
        if (result.state.limitType == LimitType.TIME && timeBonusMs < MAX_TIME_BONUS_MS) {
            // Each cascade step from the second one buys a second, capped at +20 s per game.
            val steps = result.events.count { it is GameEvent.CascadeStep && it.index >= 2 }
            val bonus = (steps * 1000L).coerceAtMost(MAX_TIME_BONUS_MS - timeBonusMs)
            timeBonusMs += bonus
            timeLeftMs += bonus
        }
        _ui.update {
            it.copy(
                board = result.state,
                timeline = timeline,
                animating = true,
                selected = null,
                hint = null,
                announcement = announce(result.events),
            )
        }
        finishAnimation(timeline, settled = true)
    }

    /** The spoken summary of a move for TalkBack. */
    private fun announce(events: List<GameEvent>): String {
        val parts = ArrayList<String>()
        for (kind in events.filterIsInstance<GameEvent.SpecialCreated>().map { it.kind }.distinct()) {
            val name = when (kind) {
                SpecialKind.RAY -> R.string.special_ray
                SpecialKind.JELLY -> R.string.special_jelly
                SpecialKind.LANTERN_FISH -> R.string.special_fish
                else -> R.string.special_pearl
            }
            parts.add(texts.get(R.string.a11y_move_created, texts.get(name)))
        }
        val cleared = events.count { it is GameEvent.PieceCleared }
        if (cleared > 0) parts.add(texts.get(R.string.a11y_move_cleared, cleared))
        val cascade = events.filterIsInstance<GameEvent.CascadeStep>().maxOfOrNull { it.index } ?: 0
        if (cascade >= 2) parts.add(texts.get(R.string.a11y_move_cascade, cascade))
        if (events.any { it is GameEvent.Shuffled }) parts.add(texts.get(R.string.a11y_move_shuffled))
        return parts.joinToString(", ")
    }

    private fun recordMove(move: Move) {
        val (spec, kind) = when (move) {
            is Move.Swap -> MoveSpec.of(move) to "swap"
            is Move.FreeSwap -> MoveSpec(listOf(move.a.r, move.a.c), listOf(move.b.r, move.b.c)) to "freeSwap"
            is Move.Harpoon -> MoveSpec(listOf(move.target.r, move.target.c), listOf(move.target.r, move.target.c)) to "harpoon"
            Move.Shuffle -> MoveSpec(listOf(0, 0), listOf(0, 0)) to "shuffle"
            Move.TimeUp -> return
        }
        currentMoves.add(spec)
        currentKinds.add(kind)
    }

    private fun moveOf(spec: MoveSpec, kind: String): Move = when (kind) {
        "freeSwap" -> Move.FreeSwap(Cell(spec.a[0], spec.a[1]), Cell(spec.b[0], spec.b[1]))
        "harpoon" -> Move.Harpoon(Cell(spec.a[0], spec.a[1]))
        "shuffle" -> Move.Shuffle
        else -> spec.toMove()
    }

    /** Input stays blocked for the animation; the board state is already final. */
    private fun finishAnimation(timeline: AnimationTimeline, settled: Boolean) {
        animationJob?.cancel()
        animationJob = viewModelScope.launch {
            delay(timeline.totalMillis)
            _ui.update { it.copy(animating = false, timeline = null) }
            if (settled) afterMoveSettled() else scheduleHintIfIdle()
        }
    }

    private fun afterMoveSettled() {
        val board = _ui.value.board ?: return
        saveMatch()
        when (val outcome = board.outcome) {
            is Outcome.Won -> onLevelFinished(true, outcome)
            is Outcome.Lost -> onLevelFinished(false, outcome)
            else -> scheduleHintIfIdle()
        }
    }

    private fun scheduleHintIfIdle() {
        hintJob?.cancel()
        val ui = _ui.value
        if (ui.mode == GameMode.ZEN || ui.mode == GameMode.PUZZLE) return
        val delayMillis = when (profiles.profile.value.settings.hints) {
            "always", "delay" -> when (ui.difficulty) {
                Difficulty.EASY -> 3_000L
                Difficulty.MEDIUM -> 6_000L
                Difficulty.HARD -> return
            }
            else -> return
        }
        val board = ui.board ?: return
        hintJob = viewModelScope.launch {
            delay(delayMillis)
            val now = _ui.value
            if (now.board === board && canPlay(now) && now.hint == null && now.activeBooster == null) {
                computeHint(board, manual = false)
            }
        }
    }

    private fun onLevelFinished(won: Boolean, outcome: Outcome) {
        stopTimer()
        flushPlayTime()
        if (currentMode == GameMode.PUZZLE) {
            finishPuzzle(won, outcome)
            return
        }
        val board = _ui.value.board ?: return
        val level = currentLevel
        val difficulty = _ui.value.difficulty
        val mode = currentMode
        // Time attack has no goals: running out of time simply ends the run.
        val completed = won || (mode == GameMode.TIME_ATTACK && outcome == Outcome.Lost.OutOfTime)
        val stars = if (won) starsFor(board, level, difficulty) else 0
        val pearls = if (won && mode == GameMode.CAMPAIGN) {
            ProfileReducer.pearlsForWin(profiles.profile.value, level, difficulty, stars, mode)
        } else {
            0
        }
        val lifeLost = mode == GameMode.CAMPAIGN && !won
        val now = clock()
        profiles.update { profile ->
            var updated = ProfileReducer.applyResult(
                profile = profile,
                level = level,
                difficulty = difficulty,
                won = completed,
                stars = stars,
                score = board.score,
                usedBoosters = _ui.value.pendingBoosters.isNotEmpty(),
                mode = mode,
                specialsCreated = specialsThisGame,
                combosMade = board.combosMade,
                maxCascade = board.maxCascade,
                nowEpochMs = now,
            )
            updated = Missions.afterWin(
                profile = updated,
                won = won,
                difficulty = difficulty.name.lowercase(),
                fullMoon = level?.isFullMoon == true,
                mode = mode,
                nowEpochMs = now,
            )
            if (lifeLost) updated = LifeRules.spend(updated, now)
            if (mode == GameMode.ENDLESS) {
                endlessScore += board.score
                val levels = _ui.value.endlessRun + if (won) 1 else 0
                updated = ProfileReducer.endlessRecord(updated, difficulty, levels, endlessScore)
            }
            updated
        }
        if (won && mode == GameMode.CAMPAIGN) checkWorldChest(level, difficulty)
        val moveCarry = if (mode == GameMode.ENDLESS && won) board.movesLeft.coerceIn(0, 5) else 0
        _ui.update {
            it.copy(
                screen = UiScreen.RESULT,
                paused = false,
                activeBooster = null,
                result = ResultState(
                    won = completed,
                    stars = stars,
                    score = board.score,
                    pearls = pearls,
                    streak = profiles.profile.value.streak,
                    reason = if (completed) null else outcome as? Outcome.Lost,
                    mode = mode,
                    lifeLost = lifeLost,
                ),
                endlessMovesCarried = moveCarry,
            )
        }
        currentMoves.clear()
        currentKinds.clear()
        saveMatch()
    }

    /** Puzzles keep their own records and never touch lives, streaks or level stats. */
    private fun finishPuzzle(won: Boolean, outcome: Outcome) {
        val ui = _ui.value
        val entry = ui.puzzle ?: return
        val stars = if (won) PuzzleRules.stars(ui.puzzleHints) else 0
        if (won) profiles.update { PuzzleRules.record(it, entry.number, stars) }
        _ui.update {
            it.copy(
                screen = UiScreen.RESULT,
                paused = false,
                activeBooster = null,
                result = ResultState(
                    won = won,
                    stars = stars,
                    score = ui.board?.score ?: 0,
                    pearls = 0,
                    streak = 0,
                    reason = if (won) null else outcome as? Outcome.Lost,
                    mode = GameMode.PUZZLE,
                    lifeLost = false,
                ),
            )
        }
        currentMoves.clear()
        currentKinds.clear()
    }

    private fun starsFor(board: GameState, level: LevelFile?, difficulty: Difficulty): Int {
        val variant = level?.variants?.get(difficulty.name.lowercase()) ?: return 1
        return when {
            board.score >= variant.stars.three -> 3
            board.score >= variant.stars.two -> 2
            else -> 1
        }
    }

    fun onNextLevel() {
        val result = _ui.value.result ?: return
        when {
            currentMode == GameMode.ENDLESS && result.won -> {
                _ui.update { it.copy(endlessRun = it.endlessRun + 1, result = null) }
                startEndlessStep()
            }
            currentMode == GameMode.PUZZLE && result.won -> {
                val next = (_ui.value.puzzle?.number ?: 0) + 1
                if (_ui.value.puzzles.any { it.number == next } && PuzzleRules.isUnlocked(profiles.profile.value, next)) onStartPuzzle(next) else onOpenPuzzles()
            }
            currentMode == GameMode.CAMPAIGN -> {
                val ordinal = currentLevel?.ordinal ?: return
                val next = _ui.value.available.filter { it > ordinal }.minOrNull()
                if (next == null) onHome() else onSelectLevel(next, GameMode.CAMPAIGN)
            }
            else -> onHome()
        }
    }

    /** Try again from the result screen, or restart from the pause menu. */
    fun onRetryLevel() {
        when (currentMode) {
            GameMode.ZEN -> onStartZen()
            GameMode.TIME_ATTACK -> onStartTimeAttack(_ui.value.difficulty)
            GameMode.ENDLESS -> onStartEndless(_ui.value.difficulty)
            GameMode.PUZZLE -> _ui.value.puzzle?.let { onStartPuzzle(it.number) }
            else -> {
                abandonInProgress()
                val level = currentLevel ?: return
                onSelectLevel(level.ordinal, currentMode)
            }
        }
    }

    /** Quitting a campaign level after the first move costs a life and the streak. */
    private fun abandonInProgress() {
        val ui = _ui.value
        val board = ui.board ?: return
        if (ui.result != null || board.outcome != Outcome.Playing) return
        if (currentMode == GameMode.CAMPAIGN && board.movesUsed > 0) {
            profiles.update { LifeRules.spend(it, clock()).copy(streak = 0) }
        }
    }

    fun onLeaveLevel() {
        flushPlayTime()
        val mode = currentMode
        abandonInProgress()
        currentMoves.clear()
        currentKinds.clear()
        saveMatch()
        dropMatch()
        when (mode) {
            GameMode.CAMPAIGN -> onOpenMap()
            GameMode.DAILY -> onOpenDaily()
            GameMode.PUZZLE -> goTo(UiScreen.PUZZLES)
            else -> goTo(UiScreen.FREE_PLAY)
        }
    }

    fun onPause() {
        if (_ui.value.screen != UiScreen.GAME || _ui.value.result != null) return
        _ui.update { it.copy(paused = true, selected = null, activeBooster = null) }
    }

    fun onResumePlay() {
        _ui.update { it.copy(paused = false) }
        scheduleHintIfIdle()
    }

    /** Closes the tutorial card; it is not shown again for this lesson. */
    fun onDismissTutorial() {
        val key = _ui.value.tutorialKey ?: return
        profiles.update { it.copy(tutorialSeen = it.tutorialSeen + key) }
        _ui.update { it.copy(tutorialKey = null, paused = false) }
        scheduleHintIfIdle()
    }

    fun onBack() {
        val ui = _ui.value
        when (ui.screen) {
            UiScreen.GAME -> when {
                ui.tutorialKey != null -> onDismissTutorial()
                ui.paused -> onResumePlay()
                else -> onPause()
            }
            UiScreen.SETTINGS -> if (ui.settingsFromGame && ui.board != null) {
                _ui.update { it.copy(screen = UiScreen.GAME, settingsFromGame = false) }
            } else {
                onHome()
            }
            UiScreen.RESULT -> when (ui.result?.mode) {
                GameMode.CAMPAIGN -> onOpenMap()
                GameMode.DAILY -> {
                    dropMatch()
                    onOpenDaily()
                }
                GameMode.PUZZLE -> onOpenPuzzles()
                else -> onOpenFreePlay()
            }
            UiScreen.INTRO -> if (currentMode == GameMode.DAILY) onOpenDaily() else onOpenMap()
            UiScreen.DAILY, UiScreen.PUZZLES -> goTo(UiScreen.FREE_PLAY)
            UiScreen.ABOUT -> goTo(UiScreen.SETTINGS)
            UiScreen.ACHIEVEMENTS -> goTo(UiScreen.PROFILE)
            UiScreen.MAP, UiScreen.PROFILE, UiScreen.COLLECTION,
            UiScreen.FREE_PLAY, UiScreen.MISSIONS, UiScreen.EVENT,
            -> onHome()
            UiScreen.HOME -> Unit
        }
    }

    /** Rebuilds a saved match by replaying its moves through the engine. */
    fun onResumeSavedMatch() {
        val saved = profiles.profile.value.savedMatch ?: return
        val ordinal = saved.levelId.removePrefix("level-").toIntOrNull()
        val level = ordinal?.let { catalog.level(it) }
        val difficulty = Difficulty.entries.firstOrNull { it.name.lowercase() == saved.difficulty }
        if (level == null || difficulty == null) {
            profiles.update { it.copy(savedMatch = null) }
            return
        }
        val boosters = saved.boostersUsed.mapNotNull { name -> PreLevelBooster.entries.firstOrNull { it.name == name } }
        var state = runCatching { LevelBuilder.start(level, difficulty, boosters) }.getOrNull() ?: run {
            profiles.update { it.copy(savedMatch = null) }
            return
        }
        for ((index, spec) in saved.moves.withIndex()) {
            val kind = saved.kinds.getOrElse(index) { "swap" }
            val result = GameEngine.applyMove(state, moveOf(spec, kind), recordEvents = false)
            if (result.state.outcome !is Outcome.Playing) {
                profiles.update { it.copy(savedMatch = null) }
                return
            }
            state = result.state
        }
        currentMode = GameMode.CAMPAIGN
        currentLevel = level
        currentMoves.clear()
        currentMoves.addAll(saved.moves)
        currentKinds.clear()
        currentKinds.addAll(saved.moves.indices.map { saved.kinds.getOrElse(it) { "swap" } })
        startBoosters = boosters
        _ui.update { it.copy(difficulty = difficulty, pendingBoosters = boosters.filter { b -> b != PreLevelBooster.LANTERN_FISH }) }
        showBoard(state, level)
        playWorldMusic(level.ordinal)
    }

    fun onDiscardSavedMatch() {
        profiles.update { it.copy(savedMatch = null) }
    }

    // ----------------------------------------------------------------- profile

    private fun hasLife(profile: Profile): Boolean {
        val now = clock()
        if (profile.infiniteLivesUntilEpochMs > now) return true
        return LifeRules.refill(profile, now).lives > 0
    }

    fun onToggleSetting(key: String) {
        profiles.update { profile ->
            val s = profile.settings
            val updated = when (key) {
                "sound" -> s.copy(soundOn = !s.soundOn)
                "music" -> s.copy(musicOn = !s.musicOn)
                "vibration" -> s.copy(vibrationOn = !s.vibrationOn)
                "reducedMotion" -> s.copy(reducedMotion = !s.reducedMotion, reducedMotionChosen = true)
                "contrastSymbols" -> s.copy(contrastSymbols = !s.contrastSymbols)
                "confirmBoosters" -> s.copy(confirmBoosters = !s.confirmBoosters)
                "leftHanded" -> s.copy(leftHanded = !s.leftHanded)
                "largerText" -> s.copy(largerText = !s.largerText)
                "distinctSymbols" -> s.copy(distinctSymbols = !s.distinctSymbols)
                else -> s
            }
            profile.copy(settings = updated)
        }
        audio.applySettings(profiles.profile.value.settings)
    }

    fun onVolume(music: Boolean, value: Float) {
        val v = value.coerceIn(0f, 1f)
        profiles.update { it.copy(settings = if (music) it.settings.copy(musicVolume = v) else it.settings.copy(sfxVolume = v)) }
        audio.applySettings(profiles.profile.value.settings)
    }

    fun onConfirmShuffle(confirmed: Boolean) {
        _ui.update { it.copy(confirmShuffle = false) }
        if (confirmed && canPlay(_ui.value) && consumeInLevelBooster(ActiveBooster.SHUFFLE)) playMove(Move.Shuffle)
    }

    fun onDismissNoLives() {
        _ui.update { it.copy(noLives = false) }
    }

    /** Starts over with a fresh profile; settings stay. The screen asks for confirmation first. */
    fun onResetProgress() {
        dropMatch()
        val now = clock()
        profiles.update { Profile.fresh(now, java.util.UUID.randomUUID().toString()).copy(settings = it.settings) }
        _ui.update { it.copy(screen = UiScreen.HOME, result = null, chest = null) }
        message(R.string.msg_progress_reset)
    }

    /** A plain-text report for bug hunting: versions, device and a profile summary, no personal data. */
    fun diagnosticPayload(appVersion: String, device: String): String {
        val p = profiles.profile.value
        val ui = _ui.value
        return buildString {
            appendLine("NightCrush diagnostic")
            appendLine("app=$appVersion engine=${pt.tiago.nightcrush.core.ENGINE_VERSION} schema=${p.schemaVersion}")
            appendLine("device=$device")
            appendLine("catalog: worlds=${ui.worlds.size} levels=${ui.levels.size}")
            appendLine("levels won=${p.levels.count { it.value.wins > 0 }} stars=${ProfileReducer.totalStars(p)} pearls=${p.pearls}")
            appendLine("lives=${p.lives} streak=${p.streak} maxStreak=${p.maxStreak}")
            appendLine("stats=${p.stats}")
            appendLine("settings=${p.settings}")
            appendLine("savedMatch=${p.savedMatch?.let { it.levelId + " " + it.difficulty + " moves=" + it.moves.size }}")
            appendLine("achievements=${p.achievements.size} stickers=${p.collection.stickers.size}")
            profiles.recoveryNote()?.let { appendLine("recovery=$it") }
        }
    }

    fun onHintsMode(mode: String) {
        profiles.update { it.copy(settings = it.settings.copy(hints = mode)) }
    }

    fun onAnimationSpeed(speed: Float) {
        profiles.update { it.copy(settings = it.settings.copy(animationSpeed = speed)) }
    }

    fun onPalette(name: String) {
        profiles.update { it.copy(settings = it.settings.copy(colorPalette = name)) }
    }

    fun onTheme(name: String) {
        profiles.update { it.copy(settings = it.settings.copy(boardTheme = name)) }
    }

    fun onDismissMessage() {
        _ui.update { it.copy(message = null) }
    }

    // ----------------------------------------------------------------- helpers

    private fun inventoryOf(booster: PreLevelBooster): Int {
        val b = profiles.profile.value.boosters
        return when (booster) {
            PreLevelBooster.RAY -> b.rayStart
            PreLevelBooster.JELLY -> b.jellyStart
            PreLevelBooster.ABYSS_PEARL -> b.pearlStart
            PreLevelBooster.LANTERN_FISH -> 0
        }
    }

    private fun consumeBoosters(boosters: List<PreLevelBooster>) {
        if (boosters.isEmpty()) return
        profiles.update { profile ->
            var b = profile.boosters
            for (booster in boosters) {
                b = when (booster) {
                    PreLevelBooster.RAY -> b.copy(rayStart = (b.rayStart - 1).coerceAtLeast(0))
                    PreLevelBooster.JELLY -> b.copy(jellyStart = (b.jellyStart - 1).coerceAtLeast(0))
                    PreLevelBooster.ABYSS_PEARL -> b.copy(pearlStart = (b.pearlStart - 1).coerceAtLeast(0))
                    PreLevelBooster.LANTERN_FISH -> b
                }
            }
            profile.copy(boosters = b)
        }
    }

    private fun consumeInLevelBooster(booster: ActiveBooster): Boolean {
        val b = profiles.profile.value.boosters
        val count = when (booster) {
            ActiveBooster.HARPOON -> b.harpoon
            ActiveBooster.FREE_SWAP -> b.freeSwap
            ActiveBooster.SHUFFLE -> b.shuffle
        }
        if (count <= 0) {
            _ui.update { it.copy(activeBooster = null) }
            return false
        }
        profiles.update { p ->
            val inv = p.boosters
            p.copy(
                boosters = when (booster) {
                    ActiveBooster.HARPOON -> inv.copy(harpoon = inv.harpoon - 1)
                    ActiveBooster.FREE_SWAP -> inv.copy(freeSwap = inv.freeSwap - 1)
                    ActiveBooster.SHUFFLE -> inv.copy(shuffle = inv.shuffle - 1)
                },
            )
        }
        return true
    }

    /** Saves the campaign match in progress after every move; clears it otherwise. */
    private fun saveMatch() {
        val ui = _ui.value
        val level = currentLevel
        val board = ui.board
        val inProgress = currentMode == GameMode.CAMPAIGN && level != null && board != null &&
            board.outcome == Outcome.Playing && ui.result == null && currentMoves.isNotEmpty()
        if (!inProgress) {
            if (profiles.profile.value.savedMatch != null) profiles.update { it.copy(savedMatch = null) }
            return
        }
        profiles.update {
            it.copy(
                savedMatch = SavedMatch(
                    levelId = level!!.id,
                    difficulty = ui.difficulty.name.lowercase(),
                    boostersUsed = startBoosters.map { b -> b.name },
                    moves = currentMoves.toList(),
                    startedAtEpochMs = clock(),
                    livesSpent = false,
                    kinds = currentKinds.toList(),
                ),
            )
        }
    }

    fun onForeground() {
        audio.applySettings(profiles.profile.value.settings)
        profiles.update { LifeRules.refill(it, clock()) }
        refreshDailyReward()
    }

    fun onBackground() {
        audio.onBackground()
        flushPlayTime()
        // Never let the clock run while the app is away.
        if (_ui.value.screen == UiScreen.GAME) onPause()
    }

    private fun recordEventsToProfile(events: List<GameEvent>) {
        if (currentMode == GameMode.ZEN) return
        var rays = 0
        var lanterns = 0
        var combos = 0
        for (event in events) {
            when (event) {
                is GameEvent.SpecialCreated -> if (event.kind == SpecialKind.RAY) rays++
                is GameEvent.LanternDelivered -> lanterns++
                is GameEvent.ComboActivated -> combos++
                else -> Unit
            }
        }
        val now = clock()
        profiles.update { profile ->
            var updated = Missions.apply(profile, events, now)
            updated = WeeklyEvents.afterEvents(updated, Missions.epochDay(now), rays, lanterns, combos)
            updated
        }
    }

    private fun comboWords(): List<String> = listOf(
        texts.get(R.string.combo_shimmering),
        texts.get(R.string.combo_luminous),
        texts.get(R.string.combo_radiant),
        texts.get(R.string.combo_spring_tide),
        texts.get(R.string.combo_ray_shower),
    )

    private companion object {
        const val MAX_TIME_BONUS_MS = 20_000L
    }
}
