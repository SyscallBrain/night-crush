package pt.tiago.nightcrush.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import kotlinx.serialization.json.Json
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import pt.tiago.nightcrush.core.model.CatalogFile
import pt.tiago.nightcrush.core.model.Difficulty
import pt.tiago.nightcrush.core.model.LevelBuilder
import pt.tiago.nightcrush.core.model.LevelFile
import pt.tiago.nightcrush.core.model.PuzzleEntry
import pt.tiago.nightcrush.core.model.PuzzleFile
import pt.tiago.nightcrush.data.BoosterInventory
import pt.tiago.nightcrush.data.CollectionState
import pt.tiago.nightcrush.data.LevelProgress
import pt.tiago.nightcrush.data.Profile
import pt.tiago.nightcrush.data.WeeklyEventState
import pt.tiago.nightcrush.game.ChestOpening
import pt.tiago.nightcrush.game.GameMode
import pt.tiago.nightcrush.game.ProfileReducer
import pt.tiago.nightcrush.game.PuzzleRules
import pt.tiago.nightcrush.game.ResultState
import pt.tiago.nightcrush.game.UiScreen
import pt.tiago.nightcrush.game.UiState
import pt.tiago.nightcrush.game.WeeklyEvents
import pt.tiago.nightcrush.ui.theme.NightCrushTheme
import java.io.File
import java.lang.reflect.Proxy
import java.time.LocalDate

/**
 * Renders the main screens to PNG on the JVM so the layout can be reviewed
 * without an emulator. Opt-in, because it is slow:
 *
 *     ./gradlew :app:testDebugUnitTest -PncShots --tests '*ScreenshotTest'
 *
 * Images land in app/build/screenshots/. The phone of reference is 1080×2340 at
 * ~420 dpi, i.e. about 411×891 dp.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "pt-rPT-w411dp-h891dp-xxhdpi")
open class ScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val json = Json { ignoreUnknownKeys = true }
    private val content = File("../content")
    /** Portuguese shots in build/screenshots, the English run in build/screenshots/en. */
    private val out = File(if (RuntimeEnvironment.getQualifiers().startsWith("en")) "build/screenshots/en" else "build/screenshots").apply { mkdirs() }

    @Before
    fun optIn() = assumeTrue(System.getProperty("nc.shots") == "true")

    private fun level(ordinal: Int): LevelFile {
        val path = "levels/world-%02d/level-%04d.json".format((ordinal - 1) / 50 + 1, ordinal)
        return json.decodeFromString(LevelFile.serializer(), File(content, path).readText())
    }

    private fun baseState(): UiState {
        val catalog = json.decodeFromString(CatalogFile.serializer(), File(content, "catalog.json").readText())
        val won = (1..16).associate { ProfileReducer.key(it, Difficulty.EASY) to LevelProgress(stars = if (it == 6) 2 else 3, wins = 1, attempts = 1) }
        val now = 1_793_000_000_000L
        val profile = Profile(
            profileId = "shots",
            createdAtEpochMs = now,
            lastSeenEpochMs = now,
            pearls = 230,
            lives = 4,
            lastLifeAnchorEpochMs = now - 5 * 60_000,
            streak = 2,
            levels = won,
            collection = CollectionState(
                stickers = mapOf("w01-01" to 1, "w01-02" to 1, "w01-04" to 1, "w01-05" to 1, "w01-07" to 1, "w01-09" to 1, "w02-03" to 1, "w02-06" to 1, "w02-08" to 1),
            ),
            weeklyEvent = WeeklyEventState(isoWeek = WeeklyEvents.isoWeek(LANTERN_DAY), progress = 9, claimedTiers = listOf(0)),
        )
        return UiState(
            profile = profile,
            worlds = catalog.worlds,
            levels = catalog.levels,
            available = catalog.levels.map { it.ordinal }.toSet(),
            catalogReady = true,
            nowEpochMs = now,
            today = LANTERN_DAY,
        )
    }

    private val callbacks: UiCallbacks = Proxy.newProxyInstance(
        UiCallbacks::class.java.classLoader,
        arrayOf(UiCallbacks::class.java),
    ) { proxy, method, args ->
        // Compose compares callbacks on recomposition, so the object methods need real answers.
        when (method.name) {
            "equals" -> proxy === args?.firstOrNull()
            "hashCode" -> System.identityHashCode(proxy)
            "toString" -> "UiCallbacks(stub)"
            else -> null
        }
    } as UiCallbacks

    private fun shot(name: String, ui: UiState) {
        // The plankton and pulses never idle, so drive the clock by hand.
        compose.mainClock.autoAdvance = false
        compose.setContent { NightCrushTheme { NightCrushApp(ui, callbacks) } }
        compose.mainClock.advanceTimeBy(1_200)
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun home() = shot("home", baseState())

    @Test fun map() = shot("map", baseState().copy(screen = UiScreen.MAP))

    /** The end of a world: the path reaches the world chest at the top. */
    @Test fun mapTop() {
        val base = baseState()
        val won = (1..48).associate { ProfileReducer.key(it, Difficulty.EASY) to LevelProgress(stars = 3, wins = 1) }
        shot("map-top", base.copy(screen = UiScreen.MAP, profile = base.profile.copy(levels = won)))
    }

    @Test fun intro() = shot("intro", baseState().copy(screen = UiScreen.INTRO, level = level(17)))

    @Test fun game16() {
        val level = level(16)
        shot("game16", baseState().copy(screen = UiScreen.GAME, level = level, board = LevelBuilder.start(level, Difficulty.EASY)))
    }

    @Test fun game18() {
        val level = level(18)
        shot("game18", baseState().copy(screen = UiScreen.GAME, level = level, board = LevelBuilder.start(level, Difficulty.EASY)))
    }

    @Test fun result() = shot(
        "result",
        baseState().copy(
            screen = UiScreen.RESULT,
            result = ResultState(won = true, stars = 2, score = 53960, pearls = 20, streak = 3, reason = null, mode = GameMode.CAMPAIGN, lifeLost = false),
        ),
    )

    @Test fun lost() {
        val level = level(18)
        shot(
            "lost",
            baseState().copy(
                screen = UiScreen.RESULT, level = level, board = LevelBuilder.start(level, Difficulty.EASY),
                result = ResultState(won = false, stars = 0, score = 12040, pearls = 0, streak = 0, reason = null, mode = GameMode.CAMPAIGN, lifeLost = true),
            ),
        )
    }

    @Test fun settings() = shot("settings", baseState().copy(screen = UiScreen.SETTINGS))

    @Test fun about() = shot("about", baseState().copy(screen = UiScreen.ABOUT))

    @Test fun collection() = shot("collection", baseState().copy(screen = UiScreen.COLLECTION))

    /** All twelve coasts half lit, to review the shore of each world. */
    @Test fun panoramas() {
        val base = baseState()
        val lit = (1..600).filter { it % 50 < 25 }.associate { ProfileReducer.key(it, Difficulty.EASY) to LevelProgress(stars = if (it % 3 == 0) 3 else 1, wins = 1) }
        shot("panoramas", base.copy(screen = UiScreen.COLLECTION, profile = base.profile.copy(levels = lit)))
    }

    private fun withStats(base: UiState): UiState = base.copy(
        profile = base.profile.copy(
            achievements = setOf("first_ray", "first_jelly", "cascade_6", "streak_5"),
            stats = base.profile.stats.copy(
                wins = mapOf("easy" to 21, "medium" to 4, "hard" to 1),
                plays = mapOf("easy" to 21, "medium" to 5, "hard" to 3),
                specialsCreated = mapOf("0" to 64, "1" to 21, "2" to 12, "3" to 5),
                combos = mapOf("0" to 4, "10" to 1),
                maxCascade = 9,
                timePlayedMillis = 5_400_000,
            ),
            maxStreak = 6,
        ),
    )

    @Test fun profile() = shot("profile", withStats(baseState()).copy(screen = UiScreen.PROFILE))

    @Test fun achievements() = shot("achievements", withStats(baseState()).copy(screen = UiScreen.ACHIEVEMENTS))

    @Test fun paused() {
        val level = level(16)
        shot("paused", baseState().copy(screen = UiScreen.GAME, level = level, board = LevelBuilder.start(level, Difficulty.EASY), paused = true))
    }

    @Test fun symbols() {
        val level = level(18)
        val base = baseState()
        shot(
            "symbols",
            base.copy(
                screen = UiScreen.GAME, level = level, board = LevelBuilder.start(level, Difficulty.EASY),
                profile = base.profile.copy(settings = base.profile.settings.copy(contrastSymbols = true)),
            ),
        )
    }

    @Test fun noLives() = shot("nolives", baseState().copy(noLives = true, profile = baseState().profile.copy(lives = 0)))

    private fun puzzles(): List<PuzzleEntry> =
        json.decodeFromString(PuzzleFile.serializer(), File(content, "puzzles/puzzles.json").readText()).puzzles

    @Test fun puzzleList() {
        val base = baseState()
        var profile = base.profile
        for (n in 1..7) profile = PuzzleRules.record(profile, n, if (n % 3 == 0) 2 else 3)
        shot("puzzles", base.copy(screen = UiScreen.PUZZLES, puzzles = puzzles(), profile = profile))
    }

    @Test fun puzzleGame() {
        val entry = puzzles().first { it.number == 60 }
        shot(
            "puzzle",
            baseState().copy(
                screen = UiScreen.GAME, mode = GameMode.PUZZLE, puzzle = entry, level = entry.level,
                board = LevelBuilder.start(entry.level, Difficulty.EASY),
            ),
        )
    }

    @Test fun event() = shot("event", baseState().copy(screen = UiScreen.EVENT))

    @Test fun chest() = shot(
        "chest",
        baseState().copy(
            chest = ChestOpening(
                world = true,
                worldIndex = 0,
                pearls = 175,
                boosters = BoosterInventory(rayStart = 1, jellyStart = 1, pearlStart = 0, harpoon = 2, freeSwap = 0, shuffle = 1),
                stickers = listOf("w01-07" to true, "w03-02" to true, "w01-01" to false),
                infiniteMinutes = 30,
            ),
        ),
    )

    private companion object {
        /** A day of the lantern race, so the event screen shows its tier track. */
        val LANTERN_DAY: Long = (LocalDate.of(2026, 10, 25).toEpochDay()..LocalDate.of(2026, 12, 1).toEpochDay())
            .first { WeeklyEvents.kindFor(it) == WeeklyEvents.Kind.LANTERNS }
    }
}

/** The same screens in English, for the store listing. */
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h891dp-xxhdpi")
class ScreenshotTestEn : ScreenshotTest()
