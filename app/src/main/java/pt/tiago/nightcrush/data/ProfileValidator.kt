package pt.tiago.nightcrush.data

/**
 * Semantic checks on a decoded profile. Pure: no Android, no clock, no files.
 *
 * The rules exist to reject a damaged, truncated or hostile file before it
 * becomes the live state, and to keep the numbers the economy assumes in
 * range. They must never reject a profile this build itself produced, so
 * every limit here is at least as large as the content it describes.
 */
object ProfileValidator {
    /** The catalog has 12 worlds of 50 levels and 3 difficulties. */
    const val MAX_LEVEL_ORDINAL = 600
    /** Every level in every difficulty, so progress can never exceed the catalog. */
    const val MAX_PROGRESS_ENTRIES = MAX_LEVEL_ORDINAL * 3
    const val MAX_DAILY_HISTORY = 270
    const val MAX_SAVED_MOVES = 500
    val MOVE_KINDS = setOf("swap", "harpoon", "freeSwap", "shuffle")
    const val MAX_STICKERS = 12 * 9
    const val MAX_PUZZLE_PACKS = 4

    private const val MIN_SPEED = 0.25f
    private const val MAX_SPEED = 4f

    private val DIFFICULTIES = setOf("easy", "medium", "hard")
    private val HINTS = setOf("always", "delay", "buttonOnly", "off")
    private val PALETTES = setOf("normal", "deuteranopia", "protanopia", "tritanopia")
    private val THEMES = setOf("BIO", "GEM", "CANDY", "NEON")
    private val PROFILE_ID = Regex("[A-Za-z0-9._:-]{1,96}")
    /** Ids and counter keys: achievements, boosters, stickers, combo names such as "shark+ray". */
    private val TOKEN = Regex("[A-Za-z0-9._:+*-]{1,64}")
    private val LEVEL_KEY = Regex("([0-9]{1,4})-(easy|medium|hard)")
    private val WORLD_KEY = Regex("world-[0-9]{2}(-[A-Za-z0-9]+)?")

    /** Every problem found, empty when the profile is usable. */
    fun problems(profile: Profile): List<String> = buildList {
        addAll(headerProblems(profile))
        addAll(boosterProblems(profile))
        addAll(levelProblems(profile))
        addAll(boosterProgressProblems(profile))
        addAll(missionProblems(profile))
        addAll(collectionProblems(profile))
        addAll(statsProblems(profile))
        addAll(freePlayProblems(profile))
        addAll(eventProblems(profile))
        addAll(savedMatchProblems(profile))
        addAll(settingsProblems(profile))
    }

    fun isValid(profile: Profile): Boolean = problems(profile).isEmpty()

    /** Returns [profile] or throws with every problem; used at the edges that must not fail soft. */
    fun requireValid(profile: Profile): Profile {
        val problems = problems(profile)
        require(problems.isEmpty()) { "Invalid profile: " + problems.joinToString("; ") }
        return profile
    }

    // ------------------------------------------------------------------ header

    private fun headerProblems(profile: Profile): List<String> = buildList {
        if (profile.schemaVersion != Profile.SCHEMA_VERSION) add("unsupported schemaVersion: ${profile.schemaVersion}")
        if (!PROFILE_ID.matches(profile.profileId)) add("invalid profileId: ${profile.profileId}")
        if (profile.createdAtEpochMs < 0) add("negative createdAtEpochMs")
        if (profile.lastSeenEpochMs < 0) add("negative lastSeenEpochMs")
        if (profile.pearls < 0) add("negative pearls")
        if (profile.lives !in 0..Profile.MAX_LIVES) add("lives out of range: ${profile.lives}")
        if (profile.lastLifeAnchorEpochMs < 0) add("negative lastLifeAnchorEpochMs")
        if (profile.infiniteLivesUntilEpochMs < 0) add("negative infiniteLivesUntilEpochMs")
        if (profile.streak < 0) add("negative streak")
        if (profile.maxStreak < 0) add("negative maxStreak")
        if (profile.starChestsOpened < 0) add("negative starChestsOpened")
        if (profile.unopenedStarChests < 0) add("negative unopenedStarChests")
        if (profile.dailyRewardDay !in 0..7) add("dailyRewardDay out of range: ${profile.dailyRewardDay}")
        if (profile.lastDailyClaimEpochDay < -1) add("invalid lastDailyClaimEpochDay")
        addAll(keyProblems("worldChestsClaimed", profile.worldChestsClaimed, WORLD_KEY))
        addAll(keyProblems("achievements", profile.achievements, TOKEN))
        addAll(keyProblems("tutorialSeen", profile.tutorialSeen, TOKEN))
    }

    // ---------------------------------------------------------------- boosters

    private fun boosterProblems(profile: Profile): List<String> = buildList {
        val boosters = profile.boosters
        addNegative("boosters.rayStart", boosters.rayStart)
        addNegative("boosters.jellyStart", boosters.jellyStart)
        addNegative("boosters.pearlStart", boosters.pearlStart)
        addNegative("boosters.harpoon", boosters.harpoon)
        addNegative("boosters.freeSwap", boosters.freeSwap)
        addNegative("boosters.shuffle", boosters.shuffle)
    }

    // ------------------------------------------------------------------ levels

    private fun levelProblems(profile: Profile): List<String> = buildList {
        if (profile.levels.size > MAX_PROGRESS_ENTRIES) add("too many level entries: ${profile.levels.size}")
        profile.levels.forEach { (key, progress) ->
            val match = LEVEL_KEY.matchEntire(key)
            if (match == null) {
                add("invalid level key: $key")
            } else if (match.groupValues[1].toInt() !in 1..MAX_LEVEL_ORDINAL) {
                add("level ordinal out of range in key: $key")
            }
            addAll(progressProblems(key, progress))
        }
    }

    private fun progressProblems(key: String, progress: LevelProgress): List<String> = buildList {
        if (progress.stars !in 0..3) add("stars out of range for $key: ${progress.stars}")
        addNegative("bestScore for $key", progress.bestScore)
        addNegative("wins for $key", progress.wins)
        addNegative("attempts for $key", progress.attempts)
        if (progress.wins > progress.attempts) add("more wins than attempts for $key")
        if (progress.stars > 0 && progress.wins == 0) add("stars without a win for $key")
    }

    private fun boosterProgressProblems(profile: Profile): List<String> = buildList {
        if (profile.starChestsOpened > MAX_PROGRESS_ENTRIES) add("too many star chests: ${profile.starChestsOpened}")
        if (profile.worldChestsClaimed.size > MAX_LEVEL_ORDINAL) add("too many world chests: ${profile.worldChestsClaimed.size}")
    }

    // ---------------------------------------------------------------- missions

    private fun missionProblems(profile: Profile): List<String> = buildList {
        val missions = profile.missions
        if (missions.epochDay < -1) add("invalid missions.epochDay")
        if (missions.ids.size != missions.progress.size || missions.ids.size != missions.claimed.size) {
            add("mission lists have different sizes")
        }
        if (missions.ids.size > 8) add("too many daily missions: ${missions.ids.size}")
        missions.ids.forEachIndexed { index, id ->
            if (!TOKEN.matches(id)) add("invalid mission id: $id")
            val progress = missions.progress.getOrNull(index)
            if (progress == null || progress < 0) add("invalid mission progress at $index")
            missions.claimed.getOrNull(index)?.let { claimed ->
                if (claimed && progress != null && progress == 0) add("mission $id claimed without progress")
            }
        }
    }

    // ------------------------------------------------------------- collection

    private fun collectionProblems(profile: Profile): List<String> = buildList {
        val collection = profile.collection
        if (collection.stickers.size > MAX_STICKERS) add("too many stickers: ${collection.stickers.size}")
        collection.stickers.forEach { (id, count) ->
            if (!TOKEN.matches(id)) add("invalid sticker id: $id")
            if (count < 0) add("negative sticker count for $id")
        }
        addAll(keyProblems("collection.panoramaUnlocked", collection.panoramaUnlocked, WORLD_KEY))
    }

    // ------------------------------------------------------------------ stats

    private fun statsProblems(profile: Profile): List<String> = buildList {
        val stats = profile.stats
        addCounterProblems("stats.wins", stats.wins, DIFFICULTIES)
        addCounterProblems("stats.plays", stats.plays, DIFFICULTIES)
        addCounterProblems("stats.stars", stats.stars, DIFFICULTIES)
        addCounterProblems("stats.bestScore", stats.bestScore, DIFFICULTIES)
        addTokenProblems("stats.specialsCreated", stats.specialsCreated, TOKEN)
        addTokenProblems("stats.combos", stats.combos, TOKEN)
        addNegative("stats.maxCascade", stats.maxCascade)
        addNegative("stats.timePlayedMillis", stats.timePlayedMillis)
        addNegative("stats.maxStreak", stats.maxStreak)
        addNegative("stats.dailyRewardsClaimed", stats.dailyRewardsClaimed)
    }

    // -------------------------------------------------------------- free play

    private fun freePlayProblems(profile: Profile): List<String> = buildList {
        val freePlay = profile.freePlay
        addCounterProblems("freePlay.endlessLevels", freePlay.endlessLevels, DIFFICULTIES)
        addCounterProblems("freePlay.endlessScore", freePlay.endlessScore, DIFFICULTIES)
        addCounterProblems("freePlay.timeAttackScore", freePlay.timeAttackScore, DIFFICULTIES)
        freePlay.puzzlesSolved.forEach { ordinal ->
            if (ordinal !in 1..MAX_PUZZLE_PACKS) add("puzzle pack out of range: $ordinal")
        }
        if (profile.dailyHistory.size > MAX_DAILY_HISTORY) add("too many daily results: ${profile.dailyHistory.size}")
        profile.dailyHistory.forEach { result ->
            if (result.epochDay < 0) add("daily result with negative epochDay")
            if (result.difficulty !in DIFFICULTIES) add("daily result with invalid difficulty: ${result.difficulty}")
            if (result.stars !in 0..3) add("daily result with stars out of range: ${result.stars}")
            if (result.score < 0) add("negative daily result score")
        }
    }

    // ------------------------------------------------------------------ event

    private fun eventProblems(profile: Profile): List<String> = buildList {
        val event = profile.weeklyEvent
        if (event.isoWeek < -1) add("invalid weeklyEvent.isoWeek")
        if (event.progress < 0) add("negative weeklyEvent.progress")
        if (event.pearlsEarned < 0) add("negative weeklyEvent.pearlsEarned")
        if (event.claimedTiers.size != event.claimedTiers.distinct().size) add("duplicate weekly event tier")
        event.claimedTiers.forEach { tier -> if (tier < 0) add("negative weekly event tier") }
    }

    // ------------------------------------------------------------ saved match

    private fun savedMatchProblems(profile: Profile): List<String> = buildList {
        val saved = profile.savedMatch ?: return@buildList
        if (!TOKEN.matches(saved.levelId)) add("invalid saved match levelId: ${saved.levelId}")
        if (saved.difficulty !in DIFFICULTIES) add("saved match with invalid difficulty: ${saved.difficulty}")
        if (saved.startedAtEpochMs < 0) add("negative saved match start time")
        if (saved.moves.size > MAX_SAVED_MOVES) add("saved match has too many moves: ${saved.moves.size}")
        saved.boostersUsed.forEach { booster -> if (!TOKEN.matches(booster)) add("invalid saved match booster: $booster") }
        if (saved.kinds.isNotEmpty() && saved.kinds.size != saved.moves.size) add("saved match kinds do not match its moves")
        saved.kinds.forEach { kind -> if (kind !in MOVE_KINDS) add("unknown saved match move kind: $kind") }
        saved.moves.forEach { move ->
            if (move.a.size != 2 || move.b.size != 2) add("saved match move with wrong arity")
            if (move.a.any { it < 0 } || move.b.any { it < 0 }) add("saved match move with negative coordinate")
        }
    }

    // --------------------------------------------------------------- settings

    private fun settingsProblems(profile: Profile): List<String> = buildList {
        val settings = profile.settings
        val speed = settings.animationSpeed
        if (speed.isNaN()) add("animationSpeed is NaN")
        else if (speed.isInfinite() || speed !in MIN_SPEED..MAX_SPEED) add("animationSpeed out of range: $speed")
        if (settings.hints !in HINTS) add("unknown hints mode: ${settings.hints}")
        if (settings.colorPalette !in PALETTES) add("unknown colorPalette: ${settings.colorPalette}")
        if (settings.boardTheme !in THEMES) add("unknown boardTheme: ${settings.boardTheme}")
        for ((name, volume) in listOf("sfxVolume" to settings.sfxVolume, "musicVolume" to settings.musicVolume)) {
            if (volume.isNaN() || volume !in 0f..1f) add("$name out of range: $volume")
        }
    }

    // ---------------------------------------------------------------- helpers

    private fun MutableList<String>.addNegative(field: String, value: Int) {
        if (value < 0) add("negative $field: $value")
    }

    private fun MutableList<String>.addNegative(field: String, value: Long) {
        if (value < 0) add("negative $field: $value")
    }

    private fun MutableList<String>.addCounterProblems(field: String, counters: Map<String, Int>, allowed: Set<String>) {
        counters.forEach { (key, value) ->
            if (key !in allowed) add("unknown key in $field: $key")
            if (value < 0) add("negative $field[$key]: $value")
        }
    }

    /** Same as [addCounterProblems] for free form keys such as special or combo names. */
    private fun MutableList<String>.addTokenProblems(field: String, counters: Map<String, Int>, pattern: Regex) {
        counters.forEach { (key, value) ->
            if (!pattern.matches(key)) add("invalid key in $field: $key")
            if (value < 0) add("negative $field[$key]: $value")
        }
    }

    private fun keyProblems(field: String, keys: Set<String>, pattern: Regex): List<String> =
        keys.filterNot { pattern.matches(it) }.map { key -> "invalid key in $field: $key" }
}
