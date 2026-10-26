package pt.tiago.nightcrush.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.security.MessageDigest

/**
 * The single JSON configuration used for the stored profile, the export
 * payload and everything a player hands back to the app.
 *
 * Pure: no Android types and no file system, so the same code runs on the
 * JVM in the unit tests. Decoding never throws into the caller: a damaged
 * file yields null and the repository decides what to do with it.
 */
object ProfileSerializer {
    private val json = Json {
        // Defaults are written out: a profile on disk is complete, so a future
        // default change cannot rewrite history silently.
        encodeDefaults = true
        // Older or newer builds may carry fields this one does not know yet.
        ignoreUnknownKeys = true
        prettyPrint = true
        prettyPrintIndent = "  "
        isLenient = false
    }

    /** Store encoding of a validated profile. */
    fun encode(profile: Profile): String = json.encodeToString(Profile.serializer(), profile)

    /** Store decoding. Returns null for anything unreadable; the caller recovers. */
    fun decode(text: String): Profile? = try {
        json.decodeFromString(Profile.serializer(), text)
    } catch (_: Exception) {
        null
    }

    /** The schema version stored in [text], or null when the file is not a JSON object with one. */
    fun readSchemaVersion(text: String): Int? = try {
        val root = json.parseToJsonElement(text) as? JsonObject
        (root?.get("schemaVersion") as? JsonPrimitive)?.content?.toIntOrNull()
    } catch (_: Exception) {
        null
    }

    /**
     * Export payload: the profile inside a small envelope carrying a SHA-256
     * of its canonical content, so a half written or hand edited file is
     * rejected on import instead of silently replacing progress.
     */
    fun encodeForExport(profile: Profile): String {
        val envelope = ProfileExportEnvelope(
            schemaVersion = profile.schemaVersion,
            profile = profile,
            sha256 = contentHash(profile),
        )
        return json.encodeToString(ProfileExportEnvelope.serializer(), envelope)
    }

    /**
     * Import counterpart of [encodeForExport]. Accepts both an exported
     * envelope (checksum verified) and a bare profile file, as long as the
     * result migrates and validates. Returns null otherwise.
     */
    fun decodeFromImport(text: String): Profile? {
        val envelope = try {
            json.decodeFromString(ProfileExportEnvelope.serializer(), text)
        } catch (_: Exception) {
            null
        }
        if (envelope == null) return Migrations.migrate(text)
        if (!HEX_64.matches(envelope.sha256)) return null
        if (envelope.schemaVersion != envelope.profile.schemaVersion) return null
        if (!constantTimeEquals(envelope.sha256, contentHash(envelope.profile))) return null
        return Migrations.migrate(envelope.profile)
    }

    /** SHA-256 over the canonical (key sorted) encoding, independent of key order in the file. */
    private fun contentHash(profile: Profile): String =
        sha256(canonicalText(json.parseToJsonElement(encode(profile))).toByteArray(Charsets.UTF_8))

    private fun canonicalText(element: JsonElement): String = when (element) {
        is JsonObject -> JsonObject(element.toSortedMap().mapValues { (_, value) -> canonicalElement(value) })
            .toString()
        else -> canonicalElement(element).toString()
    }

    private fun canonicalElement(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(element.toSortedMap().mapValues { (_, value) -> canonicalElement(value) })
        is JsonArray -> JsonArray(element.map(::canonicalElement))
        else -> element
    }

    private fun constantTimeEquals(expected: String, actual: String): Boolean =
        MessageDigest.isEqual(expected.toByteArray(Charsets.US_ASCII), actual.toByteArray(Charsets.US_ASCII))

    private val HEX_64 = Regex("[0-9a-f]{64}")

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte) }
}

/** The file written by the system file picker. Its [profile] is the whole game state. */
@Serializable
private data class ProfileExportEnvelope(
    val schemaVersion: Int,
    val profile: Profile,
    val sha256: String,
    val checksumVersion: Int = CHECKSUM_VERSION,
) {
    companion object {
        const val CHECKSUM_VERSION = 1
    }
}
