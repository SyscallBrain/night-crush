package pt.tiago.nightcrush.data

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Where the profile bytes live. The app writes them to [FileProfileStore]; the
 * unit tests use an in-memory double.
 *
 * The contract every implementation must honour:
 *  - [readMain] and [readBackup] never throw and never return half written text;
 *  - a write either replaces the whole file or leaves the old one in place;
 *  - [setAside] keeps an unreadable profile instead of deleting it.
 */
interface ProfileStore {
    /** The current profile, or null when there is none or it cannot be read. */
    fun readMain(): String?

    /** The last known good profile, or null. */
    fun readBackup(): String?

    fun writeMain(text: String)

    fun writeBackup(text: String)

    /** Keeps [text] under [name] so the user can recover it by hand. */
    fun setAside(text: String, name: String)
}

/**
 * Files inside [directory]: `profile.json` (current), `profile.bak` (the state
 * before the last change) and `profile-unreadable-<timestamp>.json` (kept,
 * never deleted).
 *
 * Every write goes to a temporary file that is flushed to the storage and then
 * renamed over the target, so a process kill mid-write can never leave a
 * truncated profile behind: the reader sees either the old file or the new one.
 */
class FileProfileStore(private val directory: File) : ProfileStore {
    private val lock = ReentrantLock()

    override fun readMain(): String? = read(File(directory, MAIN_NAME))

    override fun readBackup(): String? = read(File(directory, BACKUP_NAME))

    override fun writeMain(text: String): Unit = writeAtomically(File(directory, MAIN_NAME), text)

    override fun writeBackup(text: String): Unit = writeAtomically(File(directory, BACKUP_NAME), text)

    override fun setAside(text: String, name: String): Unit = lock.withLock {
        writeAtomically(freeName(File(directory, sanitize(name))), text)
    }

    private fun read(file: File): String? = lock.withLock {
        if (!file.isFile) return@withLock null
        try {
            file.readText(Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    private fun writeAtomically(target: File, text: String): Unit = lock.withLock {
        if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
            throw IOException("Cannot create profile directory ${directory.path}")
        }
        val temporary = File(directory, target.name + TEMP_SUFFIX)
        val bytes = text.toByteArray(Charsets.UTF_8)
        try {
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.flush()
                output.fd.sync()
            }
            // renameTo replaces the target on the same filesystem, which is the atomic case.
            if (!temporary.renameTo(target)) {
                // Fall back for the filesystems that refuse to replace an existing file.
                if (!target.delete() || !temporary.renameTo(target)) {
                    throw IOException("Could not replace ${target.name}")
                }
            }
        } catch (error: Exception) {
            temporary.delete()
            throw error
        }
    }

    /** Never overwrites an existing kept file: a second failure in the same millisecond gets its own name. */
    private fun freeName(preferred: File): File {
        if (!preferred.exists()) return preferred
        for (suffix in 1..99) {
            val candidate = File(preferred.parentFile, "${preferred.nameWithoutExtension}-$suffix.${preferred.extension}")
            if (!candidate.exists()) return candidate
        }
        return File(preferred.parentFile, "${preferred.nameWithoutExtension}-${System.nanoTime()}.${preferred.extension}")
    }

    private fun sanitize(name: String): String {
        val cleaned = buildString(name.length) {
            name.forEach { character ->
                append(if (character.isLetterOrDigit() || character == '.' || character == '-' || character == '_') character else '_')
            }
        }
        return if (cleaned.isEmpty()) FALLBACK_NAME else cleaned
    }

    companion object {
        const val MAIN_NAME = "profile.json"
        const val BACKUP_NAME = "profile.bak"
        const val UNREADABLE_PREFIX = "profile-unreadable-"
        const val UNREADABLE_SUFFIX = ".json"
        private const val TEMP_SUFFIX = ".tmp"
        private const val FALLBACK_NAME = "profile-unreadable.json"

        /** The file name used to keep an unreadable profile found at [timestamp]. */
        fun unreadableName(timestamp: Long, fromBackup: Boolean): String =
            UNREADABLE_PREFIX + timestamp + if (fromBackup) "-backup" else "" + UNREADABLE_SUFFIX
    }
}
