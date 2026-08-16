package com.patrick.lrcreader.exo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextTrackIndicatorPriorityTest {

    @Test
    fun `priority is define next then chain then prepared selection`() {
        assertEquals("define", selectNextTrackIndicatorTitle("define", null, null))
        assertEquals("chain", selectNextTrackIndicatorTitle(null, "chain", null))
        assertEquals("prepared", selectNextTrackIndicatorTitle(null, null, "prepared"))
        assertEquals("define", selectNextTrackIndicatorTitle("define", "chain", null))
        assertEquals("chain", selectNextTrackIndicatorTitle(null, "chain", "prepared"))
        assertEquals("define", selectNextTrackIndicatorTitle("define", null, "prepared"))
        assertEquals("define", selectNextTrackIndicatorTitle("define", "chain", "prepared"))
        assertNull(selectNextTrackIndicatorTitle(null, null, null))
    }

    @Test
    fun `blank and null-like titles do not hide lower priority titles`() {
        assertEquals("chain", selectNextTrackIndicatorTitle(" ", "chain", "prepared"))
        assertEquals("prepared", selectNextTrackIndicatorTitle("null", "", "prepared"))
    }
}
