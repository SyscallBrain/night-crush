package pt.tiago.nightcrush.core.random

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** SplitMix64 must be stable forever: certificates pin the sequence by hex state. */
class SplitMix64Test {

    @Test
    fun `same seed produces the same sequence`() {
        val a = SplitMix64(0L)
        val b = SplitMix64(0L)
        repeat(64) { assertEquals(a.nextLong(), b.nextLong()) }
    }

    @Test
    fun `state round-trips through hex`() {
        val rng = SplitMix64(0x7F3A_11C4_9B2E_0001UL.toLong())
        repeat(10) { rng.nextLong() }
        val restored = SplitMix64.fromHex(rng.stateHex())
        assertEquals(rng.nextLong(), restored.nextLong())
    }

    @Test
    fun `nextInt stays inside the bound`() {
        val rng = SplitMix64(42L)
        repeat(1000) {
            val value = rng.nextInt(6)
            assertNotEquals(-1, value)
            assertEquals(true, value in 0 until 6)
        }
    }

    @Test
    fun `nextInt over a power of two bound samples every value`() {
        val rng = SplitMix64(7L)
        val seen = BooleanArray(8)
        repeat(500) { seen[rng.nextInt(8)] = true }
        assertEquals(true, seen.all { it })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `nextInt rejects a non-positive bound`() {
        SplitMix64(1L).nextInt(0)
    }

    @Test
    fun `fromHex rejects malformed states`() {
        val failure = runCatching { SplitMix64.fromHex("xyz") }
        assertEquals(true, failure.isFailure)
    }

    @Test
    fun `known sequence pins the algorithm`() {
        // Golden vector produced by SplitMix64 version 1. If this changes, engineVersion must too.
        val rng = SplitMix64(0L)
        val first = rng.nextLong()
        val second = rng.nextLong()
        assertEquals("e220a8397b1dcdaf", java.lang.Long.toUnsignedString(first, 16))
        assertEquals("6e789e6aa1b965f4", java.lang.Long.toUnsignedString(second, 16))
    }
}
