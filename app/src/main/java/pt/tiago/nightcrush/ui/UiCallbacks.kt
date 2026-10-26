package pt.tiago.nightcrush.ui

import androidx.compose.runtime.Composable
import pt.tiago.nightcrush.core.model.Cell
import pt.tiago.nightcrush.core.model.Difficulty
import pt.tiago.nightcrush.core.model.PreLevelBooster
import pt.tiago.nightcrush.game.ActiveBooster
import pt.tiago.nightcrush.game.GameMode
import pt.tiago.nightcrush.game.UiState

/** Everything the screens can ask the app to do. */
interface UiCallbacks {
    fun onHome()
    fun onOpenMap()
    fun onOpenSettings()
    fun onOpenProfile()
    fun onOpenCollection()
    fun onOpenFreePlay()
    fun onOpenDaily()
    fun onOpenAbout()
    fun onOpenMissions()
    fun onClaimMission(index: Int)
    fun onOpenChest()
    fun onDismissChest()
    fun onOpenEvent()
    fun onClaimEvent()
    fun onOpenAchievements()
    fun onOpenPuzzles()
    fun onPuzzlePack(pack: Int)
    fun onStartPuzzle(number: Int)
    fun onClaimDailyReward()
    fun onWorldChanged(index: Int)
    fun onDifficultyChanged(difficulty: Difficulty)
    fun onSelectLevel(ordinal: Int)
    fun onSelectDailyLevel(ordinal: Int, mode: GameMode)
    fun onToggleBooster(booster: PreLevelBooster)
    fun onStartLevel()
    fun onStartZen()
    fun onStartTimeAttack(difficulty: Difficulty)
    fun onStartEndless(difficulty: Difficulty)
    fun onCellTap(cell: Cell)
    fun onSwap(a: Cell, b: Cell)
    fun onHint()
    fun onUseBooster(booster: ActiveBooster)
    fun onNext()
    fun onRetry()
    fun onLeaveLevel()
    fun onPause()
    fun onResumePlay()
    fun onDismissTutorial()
    fun onResumeSavedMatch()
    fun onDiscardSavedMatch()
    fun onHintsMode(mode: String)
    fun onToggleSetting(key: String)
    fun onAnimationSpeed(speed: Float)
    fun onPalette(name: String)
    fun onTheme(name: String)
    fun onBack()
    fun onExportProgress()
    fun onImportProgress()
    fun onDismissMessage()
    fun onExportDiagnostic()
    fun onVolume(music: Boolean, value: Float)
    fun onConfirmShuffle(confirmed: Boolean)
    fun onDismissNoLives()
    fun onResetProgress()
    fun currentState(): UiState
}
