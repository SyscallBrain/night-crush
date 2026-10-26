package pt.tiago.nightcrush

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import pt.tiago.nightcrush.data.CatalogRepository
import pt.tiago.nightcrush.data.FileProfileStore
import pt.tiago.nightcrush.data.ProfileRepository
import pt.tiago.nightcrush.game.AudioController
import pt.tiago.nightcrush.game.GameViewModel
import pt.tiago.nightcrush.game.Haptics
import pt.tiago.nightcrush.game.Texts
import java.io.File

/** One object graph for the whole app; no DI framework needed. */
class AppContainer(context: Context) {
    private val app = context.applicationContext

    val profileRepository: ProfileRepository = ProfileRepository(
        store = FileProfileStore(File(app.filesDir, "profile")),
    )
    val catalogRepository: CatalogRepository = CatalogRepository(app)
    val audio: AudioController = AudioController(app)
    val haptics: Haptics = Haptics(app)
    val texts: Texts = Texts { id, args -> app.getString(id, *args) }

    /** "Remove animations" in the system settings sets the animator scale to 0. */
    fun systemReducedMotion(): Boolean =
        android.provider.Settings.Global.getFloat(app.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}

class GameViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = GameViewModel(
        profiles = container.profileRepository,
        catalog = container.catalogRepository,
        audio = container.audio,
        haptics = container.haptics,
        texts = container.texts,
        systemReducedMotion = container::systemReducedMotion,
    ) as T
}
