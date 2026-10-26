package pt.tiago.nightcrush.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.tiago.nightcrush.core.model.MoveSpec

class ProfileSerializerTest {
    private val now = 1_700_000_000_000L

    @Test fun encodeThenDecodeKeepsEveryField() {
        val original = sample()
        val decoded = ProfileSerializer.decode(ProfileSerializer.encode(original))
        assertEquals(original, decoded)
    }

    @Test fun encodedTextIsCompleteAndReadable() {
        val text = ProfileSerializer.encode(sample())
        assertTrue(text.contains("\"pearls\": 120"))
        assertTrue(text.contains("\"schemaVersion\": ${Profile.SCHEMA_VERSION}"))
        // Defaults are written out: the file on disk is the whole profile.
        assertTrue(text.contains("\"wonWithoutBoosters\""))
        assertTrue(text.contains("\n"))
    }

    @Test fun unknownKeysFromANewerBuildAreIgnored() {
        val text = ProfileSerializer.encode(sample()).replaceFirst("{", "{\"futureField\": 42,")
        assertEquals(sample(), ProfileSerializer.decode(text))
    }

    @Test fun decodeFailsSoftOnDamagedText() {
        assertNull(ProfileSerializer.decode(""))
        assertNull(ProfileSerializer.decode("not json at all"))
        assertNull(ProfileSerializer.decode("{\"schemaVersion\": "))
        assertNull(ProfileSerializer.decode("[]"))
        assertNull(ProfileSerializer.decode("{\"pearls\": 5}"))
    }

    @Test fun readSchemaVersionOnlyAnswersForAJsonObject() {
        assertEquals(Profile.SCHEMA_VERSION, ProfileSerializer.readSchemaVersion(ProfileSerializer.encode(sample())))
        assertEquals(7, ProfileSerializer.readSchemaVersion("""{"schemaVersion": 7}"""))
        assertNull(ProfileSerializer.readSchemaVersion("""{"pearls": 1}"""))
        assertNull(ProfileSerializer.readSchemaVersion("garbage"))
    }

    @Test fun exportedPayloadRoundTripsThroughImport() {
        val original = sample()
        val imported = ProfileSerializer.decodeFromImport(ProfileSerializer.encodeForExport(original))
        assertEquals(original, imported)
    }

    @Test fun tamperedExportIsRejected() {
        val exported = ProfileSerializer.encodeForExport(sample())
        val tampered = exported.replace("\"pearls\": 120", "\"pearls\": 999999")
        assertNull(ProfileSerializer.decodeFromImport(tampered))
    }

    @Test fun exportWithABrokenChecksumFieldIsRejected() {
        val exported = ProfileSerializer.encodeForExport(sample())
        val broken = exported.replace(Regex("\"sha256\": \"[0-9a-f]{64}\""), "\"sha256\": \"nope\"")
        assertNull(ProfileSerializer.decodeFromImport(broken))
    }

    @Test fun importRejectsGarbageAndAcceptsABareProfileFile() {
        assertNull(ProfileSerializer.decodeFromImport("garbage"))
        assertEquals(sample(), ProfileSerializer.decodeFromImport(ProfileSerializer.encode(sample())))
    }

    @Test fun importRejectsAnInvalidProfile() {
        val invalid = ProfileSerializer.encode(sample().copy(pearls = -3))
        assertNull(ProfileSerializer.decodeFromImport(invalid))
    }

    @Test fun savedMatchMovesSurviveTheRoundTrip() {
        val original = sample().copy(
            savedMatch = SavedMatch(
                levelId = "level-0128",
                difficulty = "hard",
                boostersUsed = listOf("harpoon", "shuffle"),
                moves = listOf(
                    MoveSpec(listOf(1, 2), listOf(2, 3)),
                    MoveSpec(listOf(0, 0), listOf(1, 1)),
                ),
                startedAtEpochMs = now,
                livesSpent = true,
            ),
        )
        val text = ProfileSerializer.encode(original)
        assertEquals(original, ProfileSerializer.decode(text))
    }

    private fun sample(): Profile = Profile.fresh(now, "profile-1").copy(
        pearls = 120,
        lives = 3,
        streak = 4,
        maxStreak = 9,
        boosters = BoosterInventory(rayStart = 2, jellyStart = 1, pearlStart = 0, harpoon = 5, freeSwap = 4, shuffle = 3),
        levels = mapOf(
            "0001-easy" to LevelProgress(stars = 3, bestScore = 24_000, wins = 3, attempts = 4),
            "0128-medium" to LevelProgress(stars = 1, bestScore = 9_000, wins = 1, attempts = 2),
        ),
        worldChestsClaimed = setOf("world-01-easy"),
        achievements = setOf("first_win"),
        tutorialSeen = setOf("intro"),
        dailyRewardDay = 3,
        lastDailyClaimEpochDay = 19_700,
        missions = DailyMissions(
            epochDay = 19_700,
            ids = listOf("play_30", "win_3", "stars_10"),
            progress = listOf(30, 3, 12),
            claimed = listOf(true, false, false),
            bonusClaimed = false,
        ),
        collection = CollectionState(stickers = mapOf("world-01-1" to 3), panoramaUnlocked = setOf("world-01")),
        stats = GameStats(
            wins = mapOf("easy" to 10),
            plays = mapOf("easy" to 12),
            stars = mapOf("easy" to 21),
            bestScore = mapOf("easy" to 30_100),
            specialsCreated = mapOf("shark" to 8),
            combos = mapOf("shark+ray" to 2),
            maxCascade = 7,
            timePlayedMillis = 3_600_000,
            maxStreak = 9,
        ),
        freePlay = FreePlayRecords(
            endlessLevels = mapOf("hard" to 14),
            endlessScore = mapOf("hard" to 88_000),
            timeAttackScore = mapOf("easy" to 4_200),
            puzzlesSolved = setOf(1, 2),
        ),
        dailyHistory = listOf(DailyChallengeResult(epochDay = 19_700, difficulty = "easy", stars = 3, score = 21_000)),
        weeklyEvent = WeeklyEventState(isoWeek = 2_891, progress = 4, claimedTiers = listOf(1, 2), pearlsEarned = 60),
        settings = GameSettings(
            soundOn = false,
            musicOn = true,
            vibrationOn = false,
            animationSpeed = 1.5f,
            hints = "buttonOnly",
            confirmBoosters = false,
            colorPalette = "deuteranopia",
            distinctSymbols = true,
            reducedMotion = true,
            leftHanded = true,
            largerText = true,
            boardTheme = "NEON",
        ),
    )
}
