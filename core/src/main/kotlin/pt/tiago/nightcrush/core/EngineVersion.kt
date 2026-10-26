package pt.tiago.nightcrush.core

/**
 * Version of the observable game engine behaviour.
 *
 * Any change that alters the outcome of a move for the same level and the same
 * seed must bump this value: certificates are only valid for the engine version
 * that produced them.
 *
 * 1: first frozen engine (M2). 2: fixed gravity so falling pieces never cross
 * columns (row-major index arithmetic bug in the first certification runs).
 * 3: one lantern per spawner column per move, however many refills a cascade needs.
 * 4: gravity settles before cascades that start without a match (forced swaps,
 * boosters, conveyors, finale), so those moves no longer leave holes; armed
 * jellyfish explode before new matches; a due lantern waits for the next refill
 * of its column instead of being skipped.
 *
 * Still 4: puzzle boards (VariantSpec.puzzle) lose when they have no valid
 * move instead of shuffling. No campaign level sets the flag, so no certified
 * outcome changes.
 */
const val ENGINE_VERSION: Int = 4
