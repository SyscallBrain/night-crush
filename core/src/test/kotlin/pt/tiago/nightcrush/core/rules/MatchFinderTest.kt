package pt.tiago.nightcrush.core.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.tiago.nightcrush.core.testutil.boardOf

class MatchFinderTest {

    @Test
    fun `horizontal three is one plain group`() {
        val s = boardOf(
            "BYB",
            "BBB",
            "YBY",
        )
        val groups = MatchFinder.find(s)
        assertEquals(1, groups.size)
        assertEquals(MatchKind.THREE, groups[0].kind)
        assertEquals(listOf(3, 4, 5), groups[0].cells.toList())
    }

    @Test
    fun `vertical three is detected`() {
        val s = boardOf(
            "BYG",
            "BGY",
            "BGY",
        )
        val groups = MatchFinder.find(s)
        assertEquals(1, groups.size)
        assertEquals(MatchKind.THREE, groups[0].kind)
        assertEquals(listOf(0, 3, 6), groups[0].cells.toList())
        assertEquals(false, groups[0].runs[0].horizontal)
    }

    @Test
    fun `four in a row is classified as four`() {
        val s = boardOf(
            "BYGB",
            "BBBB",
            "YGBY",
        )
        val groups = MatchFinder.find(s)
        assertEquals(1, groups.size)
        assertEquals(MatchKind.FOUR, groups[0].kind)
        assertEquals(4, groups[0].cells.size)
    }

    @Test
    fun `five in a row is classified as five`() {
        val s = boardOf(
            "BYGBY",
            "BBBBB",
            "YGBYG",
        )
        val groups = MatchFinder.find(s)
        assertEquals(1, groups.size)
        assertEquals(MatchKind.FIVE, groups[0].kind)
    }

    @Test
    fun `an L shape shares a cell and is one LT group`() {
        val s = boardOf(
            "BYG",
            "BGY",
            "BBB",
        )
        val groups = MatchFinder.find(s)
        assertEquals(1, groups.size)
        assertEquals(MatchKind.LT, groups[0].kind)
        assertEquals(5, groups[0].cells.size)
    }

    @Test
    fun `a T shape shares a cell and is one LT group`() {
        val s = boardOf(
            "GYG",
            "BBB",
            "GBY",
            "GBY",
        )
        val groups = MatchFinder.find(s)
        assertEquals(1, groups.size)
        assertEquals(MatchKind.LT, groups[0].kind)
        assertEquals(5, groups[0].cells.size)
    }

    @Test
    fun `a 2x2 square is its own group`() {
        val s = boardOf(
            "BBY",
            "BBG",
            "YGY",
        )
        val groups = MatchFinder.find(s)
        assertEquals(1, groups.size)
        assertEquals(MatchKind.SQUARE, groups[0].kind)
        assertEquals(4, groups[0].cells.size)
    }

    @Test
    fun `five wins over an L when a row of five crosses a column`() {
        val s = boardOf(
            "..B..",
            "BBBBB",
            "..B..",
        )
        val groups = MatchFinder.find(s)
        assertEquals(1, groups.size)
        assertEquals(MatchKind.FIVE, groups[0].kind)
    }

    @Test
    fun `a cross of two runs is a single group`() {
        val s = boardOf(
            ".B.",
            "BBB",
            ".B.",
        )
        val groups = MatchFinder.find(s)
        assertEquals(1, groups.size)
        assertEquals(MatchKind.LT, groups[0].kind)
        assertEquals(5, groups[0].cells.size)
    }

    @Test
    fun `a stable board has no groups`() {
        val s = boardOf(
            "BYG",
            "YGB",
            "GBY",
        )
        assertTrue(MatchFinder.find(s).isEmpty())
    }

    @Test
    fun `two colours never merge into one group`() {
        val s = boardOf(
            "BBB",
            "YYY",
            "GBG",
        )
        val groups = MatchFinder.find(s)
        assertEquals(2, groups.size)
        assertEquals(setOf(0, 1), groups.map { it.color }.toSet())
    }

    @Test
    fun `holes break runs`() {
        val s = boardOf(
            "BYB",
            "Y Y",
            "BYB",
        )
        // Column 1 has a hole between the two Y pieces, so no run exists.
        assertTrue(MatchFinder.find(s).isEmpty())
    }
}
