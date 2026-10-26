package pt.tiago.nightcrush

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pt.tiago.nightcrush.core.model.Cell
import pt.tiago.nightcrush.core.model.Difficulty
import pt.tiago.nightcrush.core.model.PreLevelBooster
import pt.tiago.nightcrush.game.ActiveBooster
import pt.tiago.nightcrush.game.GameMode
import pt.tiago.nightcrush.game.GameViewModel
import pt.tiago.nightcrush.game.UiScreen
import pt.tiago.nightcrush.ui.NightCrushApp
import pt.tiago.nightcrush.ui.UiCallbacks
import pt.tiago.nightcrush.ui.theme.NightCrushTheme

class MainActivity : ComponentActivity() {

    private val container by lazy { AppContainer(this) }
    private val model: GameViewModel by viewModels { GameViewModelFactory(container) }

    private val exportDocument = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            lifecycleScope.launch {
                val payload = withContext(Dispatchers.IO) { model.exportPayload() }
                if (runCatching { writeText(uri, payload) }.isSuccess) model.onExported()
            }
        }
    }

    private val diagnosticDocument = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri != null) {
            lifecycleScope.launch {
                val device = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})"
                val payload = model.diagnosticPayload(BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")", device)
                if (runCatching { withContext(Dispatchers.IO) { writeText(uri, payload) } }.isSuccess) model.onExported()
            }
        }
    }

    private val importDocument = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            lifecycleScope.launch {
                val text = runCatching {
                    withContext(Dispatchers.IO) {
                        contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                    }
                }.getOrDefault("")
                model.importPayload(text)
            }
        }
    }

    private val callbacks = object : UiCallbacks {
        override fun onHome() = model.onHome()
        override fun onOpenMap() = model.onOpenMap()
        override fun onOpenSettings() = model.onOpenSettings()
        override fun onOpenProfile() = model.onOpenProfile()
        override fun onOpenCollection() = model.onOpenCollection()
        override fun onOpenFreePlay() = model.onOpenFreePlay()
        override fun onOpenDaily() = model.onOpenDaily()
        override fun onOpenAbout() = model.onOpenAbout()
        override fun onOpenMissions() = model.onOpenMissions()
        override fun onClaimMission(index: Int) = model.onClaimMission(index)
        override fun onOpenChest() = model.onOpenChest()
        override fun onDismissChest() = model.onDismissChest()
        override fun onOpenEvent() = model.onOpenEvent()
        override fun onClaimEvent() = model.onClaimEvent()
        override fun onOpenAchievements() = model.onOpenAchievements()
        override fun onOpenPuzzles() = model.onOpenPuzzles()
        override fun onPuzzlePack(pack: Int) = model.onPuzzlePack(pack)
        override fun onStartPuzzle(number: Int) = model.onStartPuzzle(number)
        override fun onClaimDailyReward() = model.onClaimDailyReward()
        override fun onWorldChanged(index: Int) = model.onWorldChanged(index)
        override fun onDifficultyChanged(difficulty: Difficulty) = model.onDifficultyChanged(difficulty)
        override fun onSelectLevel(ordinal: Int) = model.onSelectLevel(ordinal)
        override fun onSelectDailyLevel(ordinal: Int, mode: GameMode) = model.onSelectDailyLevel(ordinal, mode)
        override fun onToggleBooster(booster: PreLevelBooster) = model.onToggleBooster(booster)
        override fun onStartLevel() = model.onStartLevel()
        override fun onStartZen() = model.onStartZen()
        override fun onStartTimeAttack(difficulty: Difficulty) = model.onStartTimeAttack(difficulty)
        override fun onStartEndless(difficulty: Difficulty) = model.onStartEndless(difficulty)
        override fun onCellTap(cell: Cell) = model.onCellTap(cell)
        override fun onSwap(a: Cell, b: Cell) = model.onSwap(a, b)
        override fun onHint() = model.onHint()
        override fun onUseBooster(booster: ActiveBooster) = model.onUseBooster(booster)
        override fun onNext() = model.onNextLevel()
        override fun onRetry() = model.onRetryLevel()
        override fun onLeaveLevel() = model.onLeaveLevel()
        override fun onPause() = model.onPause()
        override fun onResumePlay() = model.onResumePlay()
        override fun onDismissTutorial() = model.onDismissTutorial()
        override fun onResumeSavedMatch() = model.onResumeSavedMatch()
        override fun onDiscardSavedMatch() = model.onDiscardSavedMatch()
        override fun onHintsMode(mode: String) = model.onHintsMode(mode)
        override fun onToggleSetting(key: String) = model.onToggleSetting(key)
        override fun onAnimationSpeed(speed: Float) = model.onAnimationSpeed(speed)
        override fun onPalette(name: String) = model.onPalette(name)
        override fun onTheme(name: String) = model.onTheme(name)
        override fun onBack() = model.onBack()
        override fun onExportProgress() {
            exportDocument.launch("nightcrush-progress.json")
        }

        override fun onImportProgress() {
            importDocument.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
        }

        override fun onExportDiagnostic() {
            diagnosticDocument.launch("nightcrush-diagnostic.txt")
        }

        override fun onVolume(music: Boolean, value: Float) = model.onVolume(music, value)
        override fun onConfirmShuffle(confirmed: Boolean) = model.onConfirmShuffle(confirmed)
        override fun onDismissNoLives() = model.onDismissNoLives()
        override fun onResetProgress() = model.onResetProgress()
        override fun currentState() = model.ui.value
        override fun onDismissMessage() = model.onDismissMessage()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = false
        setContent {
            NightCrushTheme {
                val ui by model.ui.collectAsStateWithLifecycle()
                BackHandler(enabled = ui.screen != UiScreen.HOME) { model.onBack() }
                NightCrushApp(ui, callbacks)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        model.onForeground()
    }

    override fun onPause() {
        model.onBackground()
        super.onPause()
    }

    private fun writeText(uri: Uri, text: String) {
        contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
    }
}
