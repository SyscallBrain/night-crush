package pt.tiago.nightcrush.game

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import pt.tiago.nightcrush.core.rules.ClearCause
import pt.tiago.nightcrush.core.rules.GameEvent
import pt.tiago.nightcrush.core.model.SpecialKind
import pt.tiago.nightcrush.data.GameSettings

/**
 * Sound effects through SoundPool and one looping music track at a time.
 * All samples are synthesized offline by tools/generate_sounds.py; a missing
 * resource is simply skipped, so the app never crashes on audio.
 */
class AudioController(private val context: Context) {

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(8)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val sounds = HashMap<String, Int>()
    private var music: MediaPlayer? = null
    private var currentMusicName: String? = null
    private var settings = GameSettings()
    private var cascadeStep = 0
    private var audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    init {
        for (name in SFX_NAMES) {
            val id = context.resources.getIdentifier(name, "raw", context.packageName)
            if (id != 0) sounds[name] = soundPool.load(context, id, 1)
        }
    }

    /** The track the game asked for last, so it can come back after the app returns. */
    private var wantedMusic: String? = null
    private var fading: android.animation.ValueAnimator? = null

    fun applySettings(settings: GameSettings) {
        this.settings = settings
        if (!settings.musicOn) {
            stopMusic()
        } else {
            music?.setVolume(settings.musicVolume, settings.musicVolume)
            if (music == null) wantedMusic?.let { playMusic(it) }
        }
    }

    fun play(name: String, volume: Float = 1f) {
        if (!settings.soundOn) return
        if (audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT) return
        val id = sounds[name] ?: return
        val level = volume * settings.sfxVolume
        if (level <= 0f) return
        soundPool.play(id, level, level, 1, 0, 1f)
    }

    /** Starts a loop; a different track crossfades in over 800 ms. */
    fun playMusic(name: String) {
        wantedMusic = name
        if (!settings.musicOn || currentMusicName == name) return
        val id = context.resources.getIdentifier(name, "raw", context.packageName)
        if (id == 0) return
        val next = MediaPlayer.create(context, id) ?: return
        next.isLooping = true
        next.setVolume(0f, 0f)
        next.start()
        val previous = music
        music = next
        currentMusicName = name
        fading?.cancel()
        val target = settings.musicVolume
        fading = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 800L
            addUpdateListener { animator ->
                val t = animator.animatedValue as Float
                runCatching { next.setVolume(target * t, target * t) }
                previous?.runCatching { setVolume(target * (1f - t), target * (1f - t)) }
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    previous?.runCatching { stop(); release() }
                }
                override fun onAnimationCancel(animation: android.animation.Animator) {
                    previous?.runCatching { stop(); release() }
                }
            })
            start()
        }
    }

    fun stopMusic() {
        fading?.cancel()
        fading = null
        music?.runCatching {
            stop()
            release()
        }
        music = null
        currentMusicName = null
    }

    /** Silences the music while the app is away; [applySettings] brings it back. */
    fun onBackground() {
        stopMusic()
    }

    /** Maps one move's events to sounds; the pentatonic ladder rises with cascades. */
    fun onMove(events: List<GameEvent>) {
        var lastCascade = 0
        for (event in events) {
            when (event) {
                is GameEvent.SwapStarted -> play("click", 0.6f)
                is GameEvent.SwapRejected -> play("reject")
                is GameEvent.CascadeStep -> {
                    lastCascade = event.index
                    play("match${((event.index - 1) % 10) + 1}")
                }
                is GameEvent.SpecialCreated -> when (event.kind) {
                    SpecialKind.RAY -> play("special_ray")
                    SpecialKind.JELLY -> play("special_jelly")
                    SpecialKind.LANTERN_FISH -> play("special_fish")
                    SpecialKind.ABYSS_PEARL -> play("special_pearl")
                }
                is GameEvent.ComboActivated -> play("combo")
                is GameEvent.LayerDamaged -> play("layer_break", 0.7f)
                is GameEvent.LanternDelivered -> play("lantern")
                is GameEvent.SeahorseFreed -> play("seahorse")
                is GameEvent.PufferTick -> if (event.remaining <= 2) play("puffer_tick")
                is GameEvent.OilSpread -> play("oil_spread")
                is GameEvent.PieceCleared -> if (event.cause == ClearCause.SPECIAL) play("match1", 0.4f)
                else -> Unit
            }
        }
    }

    fun onResult(won: Boolean) {
        play(if (won) "win" else "lose")
    }

    fun onStar(index: Int) {
        play("star${index.coerceIn(1, 3)}")
    }

    fun onChest() = play("chest")
    fun onSticker() = play("sticker")
    fun onClick() = play("click", 0.5f)

    companion object {
        private val SFX_NAMES = listOf(
            "click", "reject", "swap",
            "match1", "match2", "match3", "match4", "match5",
            "match6", "match7", "match8", "match9", "match10",
            "special_ray", "special_jelly", "special_fish", "special_pearl",
            "combo", "layer_break", "lantern", "seahorse",
            "puffer_tick", "puffer_boom", "oil_spread",
            "star1", "star2", "star3", "win", "lose", "chest", "sticker",
        )
    }
}
