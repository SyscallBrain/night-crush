package pt.tiago.nightcrush.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import pt.tiago.nightcrush.ui.components.expandArcFlags

class ExpandArcFlagsTest {
    @Test
    fun splitsCompactArcFlags() {
        assertEquals("M12 4 a4 4 0 1 1 0 8 4 4 0 0 1 0 -8 z", expandArcFlags("M12 4a4 4 0 110 8 4 4 0 010-8z"))
    }

    @Test
    fun splitsChainedDecimals() {
        assertEquals("a1 1 0 0 0 1.5 .9", expandArcFlags("a1 1 0 001.5.9"))
    }

    @Test
    fun leavesOtherCommandsAlone() {
        assertEquals("M3 11 l9 -7 9 7 v9 H3 z", expandArcFlags("M3 11l9-7 9 7v9H3z"))
    }
}
