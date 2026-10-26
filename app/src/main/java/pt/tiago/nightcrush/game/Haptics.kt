package pt.tiago.nightcrush.game

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import pt.tiago.nightcrush.core.rules.GameEvent
import pt.tiago.nightcrush.data.GameSettings

/** Haptics: short on a swap, medium on specials, double on combos, long on a win. */
class Haptics(private val context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private var settings = GameSettings()

    fun applySettings(settings: GameSettings) {
        this.settings = settings
    }

    private fun silent(): Boolean {
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        return manager.ringerMode == AudioManager.RINGER_MODE_SILENT
    }

    fun tap() = vibrate(20)

    fun reject() = vibrate(40)

    fun onMove(events: List<GameEvent>) {
        for (event in events) {
            when (event) {
                is GameEvent.ComboActivated -> double()
                is GameEvent.SpecialCreated -> vibrate(35)
                is GameEvent.SpecialActivated -> vibrate(35)
                is GameEvent.SwapStarted -> tap()
                else -> Unit
            }
        }
    }

    fun win() = vibrate(220)
    fun lose() = vibrate(120)

    private fun double() {
        if (!settings.vibrationOn || silent()) return
        vibrate(25)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ vibrate(25) }, 90)
    }

    private fun vibrate(millis: Long) {
        if (!settings.vibrationOn || silent()) return
        val v = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(millis, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(millis)
        }
    }
}
