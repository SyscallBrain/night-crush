package pt.tiago.nightcrush.core.model

import kotlinx.serialization.Serializable

/** The published catalog: worlds plus one entry per level with integrity hash. */
@Serializable
data class CatalogFile(
    val schemaVersion: Int = 1,
    val engineVersion: Int,
    val worlds: List<WorldEntry>,
    val levels: List<LevelEntry>,
)

@Serializable
data class WorldEntry(
    val id: String,
    val namePt: String,
    val nameEn: String,
    val from: Int,
    val to: Int,
)

@Serializable
data class LevelEntry(
    val id: String,
    val ordinal: Int,
    val worldId: String,
    val path: String,
    val revision: Int,
    val sha256: String,
    val isFullMoon: Boolean,
    val titlePt: String,
    val titleEn: String,
)

/** The manifest written next to the exported Android assets. */
@Serializable
data class AssetManifest(
    val schemaVersion: Int = 1,
    val engineVersion: Int,
    val catalogSha256: String,
    val files: List<AssetFile>,
)

@Serializable
data class AssetFile(val path: String, val sha256: String)

/** Deterministic daily pool file. */
@Serializable
data class DailyPoolFile(
    val schemaVersion: Int = 1,
    val engineVersion: Int,
    val ids: List<String>,
)

/**
 * The puzzle packs (content/puzzles/puzzles.json): fixed boards without
 * spawners, to be cleared in exactly [PuzzleEntry.moves] moves.
 */
@Serializable
data class PuzzleFile(
    val schemaVersion: Int = 1,
    val engineVersion: Int,
    val puzzles: List<PuzzleEntry>,
)

@Serializable
data class PuzzleEntry(
    /** 1..100; packs of 25. */
    val number: Int,
    val pack: Int,
    val moves: Int,
    /** A one-variant level ("easy") with no spawners and a silt goal on the marked cells. */
    val level: LevelFile,
    /** One certified solution, replayed by the build. */
    val solution: List<MoveSpec>,
)
