package pt.tiago.nightcrush.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.tiago.nightcrush.core.model.MoveSpec

class ProfileValidatorTest {
    private val now = 1_700_000_000_000L

    @Test fun freshProfileIsValid() {
        assertTrue(ProfileValidator.problems(Profile.fresh(now, "profile-1")).isEmpty())
    }

    @Test fun unsupportedSchemaVersionIsRejected() {
        val problems = ProfileValidator.problems(Profile.fresh(now, "profile-1").copy(schemaVersion = 99))
        assertTrue(problems.toString(), problems.any { problem -> problem.contains("schemaVersion") })
    }

    @Test fun negativeEconomyValuesAreRejected() {
        val problems = ProfileValidator.problems(
            Profile.fresh(now, "profile-1").copy(pearls = -1, lives = -1, streak = -2),
        )
        assertTrue(problems.toString(), problems.any { problem -> problem.contains("pearls") })
        assertTrue(problems.toString(), problems.any { problem -> problem.contains("lives") })
        assertTrue(problems.toString(), problems.any { problem -> problem.contains("streak") })
    }

    @Test fun livesAboveTheMaximumAreRejected() {
        val problems = ProfileValidator.problems(Profile.fresh(now, "profile-1").copy(lives = Profile.MAX_LIVES + 1))
        assertTrue(problems.toString(), problems.any { problem -> problem.contains("lives") })
    }

    @Test fun negativeBoosterCountsAreRejected() {
        val problems = ProfileValidator.problems(
            Profile.fresh(now, "profile-1").copy(boosters = BoosterInventory(harpoon = -1)),
        )
        assertTrue(problems.toString(), problems.any { problem -> problem.contains("harpoon") })
    }

    @Test fun starsOutsideZeroToThreeAreRejected() {
        val tooMany = ProfileValidator.problems(
            Profile.fresh(now, "profile-1").copy(
                levels = mapOf("0001-easy" to LevelProgress(stars = 4, wins = 1, attempts = 1)),
            ),
        )
        assertTrue(tooMany.toString(), tooMany.any { problem -> problem.contains("stars") })
        val negative = ProfileValidator.problems(
            Profile.fresh(now, "profile-1").copy(
                levels = mapOf("0001-easy" to LevelProgress(stars = -1, wins = 1, attempts = 1)),
            ),
        )
        assertTrue(negative.toString(), negative.any { problem -> problem.contains("stars") })
    }

    @Test fun validLevelProgressIsAccepted() {
        val profile = Profile.fresh(now, "profile-1").copy(
            levels = mapOf(
                "0001-easy" to LevelProgress(stars = 3, bestScore = 10_000, wins = 2, attempts = 5),
                "0600-hard" to LevelProgress(stars = 1, bestScore = 1_000, wins = 1, attempts = 1),
            ),
        )
        assertTrue(ProfileValidator.problems(profile).toString(), ProfileValidator.isValid(profile))
    }

    @Test fun moreWinsThanAttemptsIsRejected() {
        val problems = ProfileValidator.problems(
            Profile.fresh(now, "profile-1").copy(
                levels = mapOf("0001-easy" to LevelProgress(stars = 1, wins = 3, attempts = 1)),
            ),
        )
        assertTrue(problems.toString(), problems.any { problem -> problem.contains("wins than attempts") })
    }

    @Test fun brokenMapKeysAreRejected() {
        val badLevel = ProfileValidator.problems(
            Profile.fresh(now, "profile-1").copy(levels = mapOf("level one" to LevelProgress())),
        )
        assertTrue(badLevel.toString(), badLevel.any { problem -> problem.contains("level key") })

        val badChest = ProfileValidator.problems(
            Profile.fresh(now, "profile-1").copy(worldChestsClaimed = setOf("chest")),
        )
        assertTrue(badChest.toString(), badChest.any { problem -> problem.contains("worldChestsClaimed") })

        val badSticker = ProfileValidator.problems(
            Profile.fresh(now, "profile-1").copy(collection = CollectionState(stickers = mapOf("world-01-1" to -1))),
        )
        assertTrue(badSticker.toString(), badSticker.any { problem -> problem.contains("sticker") })

        val badDifficulty = ProfileValidator.problems(
            Profile.fresh(now, "profile-1").copy(stats = GameStats(wins = mapOf("impossible" to 1))),
        )
        assertTrue(badDifficulty.toString(), badDifficulty.any { problem -> problem.contains("stats.wins") })
    }

    @Test fun notANumberIsRejectedWhereANumberIsExpected() {
        val problems = ProfileValidator.problems(
            Profile.fresh(now, "profile-1").copy(settings = GameSettings(animationSpeed = Float.NaN)),
        )
        assertTrue(problems.toString(), problems.any { problem -> problem.contains("NaN") })
        val infinite = ProfileValidator.problems(
            Profile.fresh(now, "profile-1").copy(settings = GameSettings(animationSpeed = Float.POSITIVE_INFINITY)),
        )
        assertTrue(infinite.toString(), infinite.any { problem -> problem.contains("animationSpeed") })
    }

    @Test fun unknownSettingsValuesAreRejected() {
        val hints = ProfileValidator.problems(
            Profile.fresh(now, "profile-1").copy(settings = GameSettings(hints = "sometimes")),
        )
        assertTrue(hints.toString(), hints.any { problem -> problem.contains("hints") })
        val theme = ProfileValidator.problems(
            Profile.fresh(now, "profile-1").copy(settings = GameSettings(boardTheme = "NEON2")),
        )
        assertTrue(theme.toString(), theme.any { problem -> problem.contains("boardTheme") })
        val palette = ProfileValidator.problems(
            Profile.fresh(now, "profile-1").copy(settings = GameSettings(colorPalette = "rainbow")),
        )
        assertTrue(palette.toString(), palette.any { problem -> problem.contains("colorPalette") })
    }

    @Test fun missionListsMustAgree() {
        val problems = ProfileValidator.problems(
            Profile.fresh(now, "profile-1").copy(
                missions = DailyMissions(ids = listOf("a", "b"), progress = listOf(1), claimed = listOf(false, false)),
            ),
        )
        assertTrue(problems.toString(), problems.any { problem -> problem.contains("mission lists") })
    }

    @Test fun dailyHistoryIsBoundedAndChecked() {
        val tooLong = Profile.fresh(now, "profile-1").copy(
            dailyHistory = List(ProfileValidator.MAX_DAILY_HISTORY + 1) {
                DailyChallengeResult(epochDay = it.toLong(), difficulty = "easy", stars = 1, score = 100)
            },
        )
        assertTrue(ProfileValidator.problems(tooLong).any { problem -> problem.contains("too many daily results") })
        val badStars = Profile.fresh(now, "profile-1").copy(
            dailyHistory = listOf(DailyChallengeResult(epochDay = 1, difficulty = "easy", stars = 5, score = 100)),
        )
        assertTrue(ProfileValidator.problems(badStars).any { problem -> problem.contains("daily result") })
        val badDifficulty = Profile.fresh(now, "profile-1").copy(
            dailyHistory = listOf(DailyChallengeResult(epochDay = 1, difficulty = "nightmare", stars = 1, score = 100)),
        )
        assertTrue(ProfileValidator.problems(badDifficulty).any { problem -> problem.contains("difficulty") })
    }

    @Test fun savedMatchIsChecked() {
        val good = Profile.fresh(now, "profile-1").copy(
            savedMatch = SavedMatch(
                levelId = "level-0128",
                difficulty = "medium",
                moves = listOf(MoveSpec(listOf(0, 1), listOf(1, 1))),
                startedAtEpochMs = now,
            ),
        )
        assertTrue(ProfileValidator.problems(good).isEmpty())
        val badDifficulty = Profile.fresh(now, "profile-1").copy(
            savedMatch = SavedMatch(levelId = "level-0128", difficulty = "impossible"),
        )
        assertTrue(ProfileValidator.problems(badDifficulty).any { problem -> problem.contains("saved match") })
        val badMove = Profile.fresh(now, "profile-1").copy(
            savedMatch = SavedMatch(
                levelId = "level-0128",
                difficulty = "easy",
                moves = listOf(MoveSpec(listOf(0), listOf(1))),
            ),
        )
        assertTrue(ProfileValidator.problems(badMove).any { problem -> problem.contains("arity") })
    }

    @Test fun requireValidReportsEveryProblemAtOnce() {
        val broken = Profile.fresh(now, "profile-1").copy(pearls = -1, lives = 99)
        val error = runCatching { ProfileValidator.requireValid(broken) }.exceptionOrNull()
        assertFalse(error == null)
        assertTrue(error!!.message!!.contains("pearls"))
        assertTrue(error.message!!.contains("lives"))
    }

    @Test fun requireValidReturnsTheSameProfileWhenItIsGood() {
        val profile = Profile.fresh(now, "profile-1")
        assertEquals(profile, ProfileValidator.requireValid(profile))
    }
}
