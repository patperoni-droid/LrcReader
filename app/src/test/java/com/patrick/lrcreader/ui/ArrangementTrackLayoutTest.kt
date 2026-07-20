package com.patrick.lrcreader.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrangementTrackLayoutTest {

    @Test
    fun blockWidth_keepsShortSegmentsTouchable() {
        assertEquals(112f, arrangementTrackBlockWidthDp(null), 0f)
        assertEquals(112f, arrangementTrackBlockWidthDp(2_000L), 0f)
    }

    @Test
    fun blockWidth_reflectsSegmentDurationAtFixedScale() {
        val thirtySeconds = arrangementTrackBlockWidthDp(30_000L)
        val sixtySeconds = arrangementTrackBlockWidthDp(60_000L)

        assertEquals(150f, thirtySeconds, 0f)
        assertEquals(300f, sixtySeconds, 0f)
        assertTrue(sixtySeconds > thirtySeconds)
    }

    @Test
    fun blockWidth_capsPathologicalDurations() {
        assertEquals(600f, arrangementTrackBlockWidthDp(10 * 60_000L), 0f)
    }
}
