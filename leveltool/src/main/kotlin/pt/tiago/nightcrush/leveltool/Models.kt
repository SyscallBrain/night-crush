package pt.tiago.nightcrush.leveltool

import kotlinx.serialization.Serializable
import pt.tiago.nightcrush.core.model.Cell
import pt.tiago.nightcrush.core.model.Move

/** A certificate proves one variant can be won with at least three stars. */
@Serializable
data class CertificateFile(
    val schemaVersion: Int = 1,
    val engineVersion: Int,
    val levelId: String,
    val difficulty: String,
    val revision: Int,
    val seedHex: String,
    val moves: List<MoveSpec>,
    val afterStateHash: List<String>,
    val finalScore: Int,
    val movesUsed: Int,
    val maxCascade: Int,
    val twoStars: Int,
    val threeStars: Int,
)

@Serializable
data class MoveSpec(val a: List<Int>, val b: List<Int>)

fun MoveSpec.toMove(): Move.Swap = Move.Swap(Cell(a[0], a[1]), Cell(b[0], b[1]))

fun Move.Swap.toSpec(): MoveSpec = MoveSpec(listOf(a.r, a.c), listOf(b.r, b.c))

// The catalog and asset manifest shapes live in :core so the app reads the same
// schema it is generated from.
typealias CatalogFile = pt.tiago.nightcrush.core.model.CatalogFile
typealias WorldEntry = pt.tiago.nightcrush.core.model.WorldEntry
typealias LevelEntry = pt.tiago.nightcrush.core.model.LevelEntry
typealias AssetManifest = pt.tiago.nightcrush.core.model.AssetManifest
typealias AssetFile = pt.tiago.nightcrush.core.model.AssetFile
