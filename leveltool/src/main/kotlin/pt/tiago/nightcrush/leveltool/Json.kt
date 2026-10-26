package pt.tiago.nightcrush.leveltool

import kotlinx.serialization.json.Json
import pt.tiago.nightcrush.core.model.LevelFile
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.exists

/** One shared JSON configuration so written files are byte-stable. */
val levelJson = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
    encodeDefaults = true
    ignoreUnknownKeys = true
    explicitNulls = false
}

val levelJsonCompact = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    explicitNulls = false
}

fun sha256(text: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    return digest.digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

fun sha256File(path: Path): String {
    val digest = MessageDigest.getInstance("SHA-256")
    return digest.digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
}

fun readLevel(path: Path): LevelFile {
    require(path.exists()) { "Level not found: $path" }
    return levelJson.decodeFromString(LevelFile.serializer(), Files.readString(path))
}

fun writeText(path: Path, text: String) {
    path.parent?.let { Files.createDirectories(it) }
    Files.writeString(path, text)
}

fun round100(value: Int): Int = (value / 100) * 100
