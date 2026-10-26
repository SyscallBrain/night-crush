package pt.tiago.nightcrush.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * The live profile: one [StateFlow], written to disk after every change.
 *
 * Android independent on purpose; [profileRepository] is the only place that
 * knows about a [Context]. Load order is main, then backup, then a fresh
 * profile, and anything unreadable is kept on disk under a timestamped name
 * instead of being overwritten silently.
 *
 * The clock and the id generator are injected so every recovery, clamp and
 * export is deterministic under test.
 */
class ProfileRepository(
    private val store: ProfileStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newProfileId: () -> String = { UUID.randomUUID().toString() },
) {
    private val lock = ReentrantLock()
    private val loaded: LoadOutcome = load()
    private val state = MutableStateFlow(loaded.profile)

    /** The current profile. Never null; observers see every persisted change. */
    val profile: StateFlow<Profile> = state.asStateFlow()

    /**
     * What went wrong during loading, or null when the stored profile was read
     * normally. Non-null means unreadable bytes were preserved on disk and the
     * profile in memory may be older (the backup) or brand new.
     */
    fun recoveryNote(): String? = loaded.note

    /** Applies [transform] and persists the result; nothing is lost on a process kill. */
    fun update(transform: (Profile) -> Profile): Unit = lock.withLock {
        val current = state.value
        val changed = transform(current)
        if (changed == current) return@withLock
        persist(current, changed)
        state.value = changed
    }

    /**
     * Rewrites both files from the current in-memory profile. Used after a
     * recovery, so the damaged file is replaced only once its content is safe
     * under a kept name.
     */
    fun flush(): Unit = lock.withLock {
        val current = state.value
        val encoded = ProfileSerializer.encode(current)
        store.writeBackup(encoded)
        store.writeMain(encoded)
    }

    /** The payload for the system file picker: profile plus checksum. */
    fun exportPayload(): String = ProfileSerializer.encodeForExport(state.value)

    /**
     * Replaces the profile with [text]. The current state stays as the backup,
     * so a bad import can never destroy the save that was already there.
     */
    fun importPayload(text: String): Result<Unit> {
        val imported = ProfileSerializer.decodeFromImport(text)
            ?: return Result.failure(IllegalArgumentException("The selected file is not a valid Night Crush profile"))
        // Exports from older builds walk the same migration chain as the stored file.
        val migrated = if (Migrations.isSupported(imported)) Migrations.migrate(imported) else null
        val problems = if (migrated == null) ProfileValidator.problems(imported) else emptyList()
        if (migrated == null) {
            return Result.failure(IllegalArgumentException("Invalid profile: " + problems.joinToString("; ")))
        }
        return lock.withLock {
            val previous = state.value
            try {
                persist(previous, migrated)
                state.value = migrated
                Result.success(Unit)
            } catch (error: Exception) {
                Result.failure(error)
            }
        }
    }

    /** Backup first, then the new main file: an interrupted write leaves the previous state recoverable. */
    private fun persist(previous: Profile, next: Profile) {
        store.writeBackup(ProfileSerializer.encode(previous))
        store.writeMain(ProfileSerializer.encode(next))
    }

    // -------------------------------------------------------------------- load

    private fun load(): LoadOutcome {
        val now = clock()
        val main = store.readMain()
        val mainProfile = main?.let { Migrations.migrate(it) }
        if (main != null && mainProfile != null) return LoadOutcome(normalize(mainProfile, now), null)

        val backup = store.readBackup()
        val backupProfile = backup?.let { Migrations.migrate(it) }
        // Anything unreadable is kept before a later write can replace it.
        val kept = ArrayList<String>()
        if (main != null && mainProfile == null) {
            store.setAside(main, FileProfileStore.unreadableName(now, fromBackup = false))
            kept += "profile.json"
        }
        if (backup != null && backupProfile == null) {
            store.setAside(backup, FileProfileStore.unreadableName(now, fromBackup = true))
            kept += "profile.bak"
        }
        return when {
            backupProfile != null -> LoadOutcome(
                normalize(backupProfile, now),
                "The stored profile was unreadable and was kept as " +
                    FileProfileStore.unreadableName(now, fromBackup = false) + "; the backup was restored.",
            )
            kept.isNotEmpty() -> LoadOutcome(
                Profile.fresh(now, newProfileId()),
                "The stored profile was unreadable (" + kept.joinToString(" and ") +
                    ") and was kept as " + FileProfileStore.UNREADABLE_PREFIX +
                    "<timestamp>" + FileProfileStore.UNREADABLE_SUFFIX + "; a new profile was started.",
            )
            else -> LoadOutcome(Profile.fresh(now, newProfileId()), null)
        }
    }

    /**
     * Clock rollback protection: a timestamp from the future cannot be trusted
     * (the device clock moved back), so it is clamped to now. An infinite
     * lives bonus that already ran out is cleared instead of being kept
     * forever.
     */
    private fun normalize(profile: Profile, now: Long): Profile {
        val clampedSeen = if (profile.lastSeenEpochMs > now) now else profile.lastSeenEpochMs
        val clearedBonus = if (profile.infiniteLivesUntilEpochMs < now) 0L else profile.infiniteLivesUntilEpochMs
        return if (clampedSeen == profile.lastSeenEpochMs && clearedBonus == profile.infiniteLivesUntilEpochMs) {
            profile
        } else {
            profile.copy(lastSeenEpochMs = clampedSeen, infiniteLivesUntilEpochMs = clearedBonus)
        }
    }

    private data class LoadOutcome(val profile: Profile, val note: String?)
}

/**
 * The single place that knows where profiles live on a device. Everything else
 * works against [ProfileStore], so the repository is testable on the JVM.
 */
fun profileRepository(context: Context): ProfileRepository {
    val application = context.applicationContext
    return ProfileRepository(FileProfileStore(File(application.filesDir, PROFILE_DIRECTORY)))
}

/** Kept out of the cloud backup rules: the profile is device local. */
const val PROFILE_DIRECTORY = "profile"
