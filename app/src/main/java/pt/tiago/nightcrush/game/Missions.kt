package pt.tiago.nightcrush.game

import pt.tiago.nightcrush.core.model.Pieces
import pt.tiago.nightcrush.core.rules.GameEvent
import pt.tiago.nightcrush.core.random.SplitMix64
import pt.tiago.nightcrush.data.DailyMissions
import pt.tiago.nightcrush.data.Profile
import pt.tiago.nightcrush.data.WeeklyEventState

/**
 * Daily missions and the weekly event track.
 *
 * Missions are chosen from a fixed pool by the local date, so every device sees
 * the same three missions on the same day. Progress counts in any mode except
 * Zen.
 */
object Missions {

    class Template(
        val id: String,
        val target: Int,
    )

    val pool = listOf(
        Template("create_rays", 5),
        Template("special_combos", 2),
        Template("win_full_moon", 1),
        Template("win_medium", 3),
        Template("collect_coral", 100),
        Template("deliver_lanterns", 4),
        Template("play_daily", 1),
        Template("cascade_4", 1),
        Template("win_easy", 2),
        Template("clear_silt", 20),
        Template("free_seahorses", 3),
        Template("create_fish", 3),
    )

    /** The local calendar day: daily content turns over at local midnight, not UTC. */
    fun epochDay(nowEpochMs: Long): Long =
        java.time.Instant.ofEpochMilli(nowEpochMs).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay()

    fun scheduleFor(epochDay: Long): List<String> {
        val stream = SplitMix64(epochDay * 1_000_003L)
        val indices = (0 until pool.size).toMutableList()
        val picked = ArrayList<String>(3)
        repeat(3) {
            val index = stream.nextInt(indices.size)
            picked.add(pool[indices.removeAt(index)].id)
        }
        return picked
    }

    /** Ensures today's missions exist before progress is applied. */
    fun ensureToday(profile: Profile, nowEpochMs: Long): Profile {
        val day = epochDay(nowEpochMs)
        if (profile.missions.epochDay == day) return profile
        val ids = scheduleFor(day)
        return profile.copy(missions = DailyMissions(epochDay = day, ids = ids, progress = List(ids.size) { 0 }, claimed = List(ids.size) { false }))
    }

    /** Applies one move's events to the daily missions. */
    fun apply(profile: Profile, events: List<GameEvent>, nowEpochMs: Long): Profile {
        var updated = ensureToday(profile, nowEpochMs)
        val missions = updated.missions
        if (missions.ids.isEmpty()) return updated
        var rays = 0
        var fish = 0
        var combos = 0
        var lanterns = 0
        var silt = 0
        var seahorses = 0
        var coralCleared = 0
        var maxCascade = 0
        for (event in events) {
            when (event) {
                is GameEvent.SpecialCreated -> when (event.kind) {
                    pt.tiago.nightcrush.core.model.SpecialKind.RAY -> rays++
                    pt.tiago.nightcrush.core.model.SpecialKind.LANTERN_FISH -> fish++
                    else -> Unit
                }
                is GameEvent.ComboActivated -> combos++
                is GameEvent.LanternDelivered -> lanterns++
                is GameEvent.SiltCleared -> silt++
                is GameEvent.SeahorseFreed -> seahorses++
                is GameEvent.PieceCleared -> {
                    if (event.color == pt.tiago.nightcrush.core.model.PieceColor.CORAL.ordinal) coralCleared++
                }
                is GameEvent.CascadeStep -> maxCascade = maxOf(maxCascade, event.index)
                else -> Unit
            }
        }
        val progress = missions.progress.toIntArray()
        missions.ids.forEachIndexed { index, id ->
            val template = pool.first { it.id == id }
            val delta = when (id) {
                "create_rays" -> rays
                "create_fish" -> fish
                "special_combos" -> combos
                "deliver_lanterns" -> lanterns
                "clear_silt" -> silt
                "free_seahorses" -> seahorses
                "collect_coral" -> coralCleared
                "cascade_4" -> if (maxCascade >= 4) 1 else 0
                else -> 0
            }
            if (delta > 0) progress[index] = (progress[index] + delta).coerceAtMost(template.target)
        }
        updated = updated.copy(missions = missions.copy(progress = progress.toList()))
        return updated
    }

    /** Win-based missions are applied after the level settles. */
    fun afterWin(profile: Profile, won: Boolean, difficulty: String, fullMoon: Boolean, mode: GameMode, nowEpochMs: Long): Profile {
        if (!won || mode == GameMode.ZEN) return profile
        var updated = ensureToday(profile, nowEpochMs)
        val missions = updated.missions
        val progress = missions.progress.toIntArray()
        missions.ids.forEachIndexed { index, id ->
            val delta = when (id) {
                "win_full_moon" -> if (fullMoon) 1 else 0
                "win_medium" -> if (difficulty == "medium") 1 else 0
                "win_easy" -> if (difficulty == "easy") 1 else 0
                "play_daily" -> if (mode == GameMode.DAILY) 1 else 0
                else -> 0
            }
            if (delta > 0) {
                val target = pool.first { it.id == id }.target
                progress[index] = (progress[index] + delta).coerceAtMost(target)
            }
        }
        updated = updated.copy(missions = missions.copy(progress = progress.toList()))
        return updated
    }

    fun allComplete(profile: Profile): Boolean {
        val missions = profile.missions
        if (missions.ids.isEmpty()) return false
        return missions.ids.withIndex().all { (index, id) ->
            val target = pool.first { it.id == id }.target
            (missions.progress.getOrElse(index) { 0 }) >= target
        }
    }

    fun claim(profile: Profile, index: Int): Profile {
        val missions = profile.missions
        if (index !in missions.ids.indices) return profile
        if (missions.claimed.getOrElse(index) { false }) return profile
        val target = pool.first { it.id == missions.ids[index] }.target
        if (missions.progress.getOrElse(index) { 0 } < target) return profile
        val claimed = missions.claimed.toMutableList()
        claimed[index] = true
        var updated = profile.copy(
            missions = missions.copy(claimed = claimed),
            pearls = profile.pearls + 30,
        )
        if (allComplete(updated) && !missions.bonusClaimed) {
            val sticker = StickerBook.randomSticker(SplitMix64(missions.epochDay + 7), null)
            updated = updated.copy(
                missions = updated.missions.copy(bonusClaimed = true),
                collection = if ((updated.collection.stickers[sticker] ?: 0) == 0) {
                    updated.collection.copy(stickers = updated.collection.stickers + (sticker to 1))
                } else {
                    updated.collection
                },
                pearls = updated.pearls + if ((profile.collection.stickers[sticker] ?: 0) > 0) 10 else 0,
            )
        }
        return updated
    }
}

/**
 * Weekly offline events rotating by ISO week (design 8.8):
 *  - Ray week: every ray created pays 2 pearls, up to 300 in the week;
 *  - Lantern race and Combo tide: a five-tier track to claim;
 *  - Sticker hunt: every chest gives one more sticker.
 */
object WeeklyEvents {

    enum class Kind { RAYS, LANTERNS, COMBOS, STICKERS }

    const val PEARLS_PER_RAY = 2
    const val RAY_PEARLS_CAP = 300

    fun kindFor(epochDay: Long): Kind = Kind.entries[(isoWeek(epochDay) % 4).toInt()]

    /** Weeks start on Monday; epoch day 0 was a Thursday. */
    fun isoWeek(epochDay: Long): Long = (epochDay + 3) / 7

    /** Days left in the week, today included (7 on Monday, 1 on Sunday). */
    fun daysLeft(epochDay: Long): Int = 7 - ((epochDay + 3) % 7).toInt()

    fun tiers(kind: Kind): List<Int> = when (kind) {
        Kind.LANTERNS -> listOf(3, 8, 15, 25, 40)
        Kind.COMBOS -> listOf(2, 5, 10, 18, 30)
        else -> emptyList()
    }

    /** Pearls for each tier of a track. */
    val tierPearls = listOf(40, 60, 80, 100, 150)

    /** This week's state; a stored state from another week reads as a fresh one. */
    fun current(profile: Profile, epochDay: Long): WeeklyEventState {
        val week = isoWeek(epochDay)
        val state = profile.weeklyEvent
        return if (state.isoWeek == week) state else WeeklyEventState(isoWeek = week)
    }

    /** Applies one move's counters. */
    fun afterEvents(profile: Profile, epochDay: Long, rays: Int, lanterns: Int, combos: Int): Profile {
        val state = current(profile, epochDay)
        return when (kindFor(epochDay)) {
            Kind.RAYS -> {
                val pearls = (rays * PEARLS_PER_RAY).coerceAtMost(RAY_PEARLS_CAP - state.pearlsEarned).coerceAtLeast(0)
                profile.copy(
                    pearls = profile.pearls + pearls,
                    weeklyEvent = state.copy(progress = state.progress + rays, pearlsEarned = state.pearlsEarned + pearls),
                )
            }
            Kind.LANTERNS -> profile.copy(weeklyEvent = state.copy(progress = state.progress + lanterns))
            Kind.COMBOS -> profile.copy(weeklyEvent = state.copy(progress = state.progress + combos))
            Kind.STICKERS -> profile.copy(weeklyEvent = state)
        }
    }

    /** Counts the bonus stickers handed out by chests in the sticker hunt. */
    fun afterBonusSticker(profile: Profile, epochDay: Long): Profile {
        val state = current(profile, epochDay)
        return profile.copy(weeklyEvent = state.copy(progress = state.progress + 1))
    }

    fun claimable(profile: Profile, epochDay: Long): List<Int> {
        val state = current(profile, epochDay)
        return tiers(kindFor(epochDay)).withIndex()
            .filter { (index, threshold) -> state.progress >= threshold && index !in state.claimedTiers }
            .map { it.index }
    }

    /** Claims every tier reached so far. */
    fun claim(profile: Profile, epochDay: Long): Profile {
        val ready = claimable(profile, epochDay)
        if (ready.isEmpty()) return profile
        val state = current(profile, epochDay)
        val pearls = ready.sumOf { tierPearls[it] }
        return profile.copy(
            pearls = profile.pearls + pearls,
            weeklyEvent = state.copy(claimedTiers = (state.claimedTiers + ready).sorted(), pearlsEarned = state.pearlsEarned + pearls),
        )
    }
}
