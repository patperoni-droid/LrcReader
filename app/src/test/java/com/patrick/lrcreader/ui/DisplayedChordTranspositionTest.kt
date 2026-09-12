package com.patrick.lrcreader.ui

import com.patrick.lrcreader.core.LrcLine
import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayedChordTranspositionTest {
    @Test
    fun `sync off preserves manual transposition`() {
        assertEquals(-1, displayedChordTransposition(-1, 2, false))
    }

    @Test
    fun `sync on combines positive and negative pitch with manual transposition`() {
        assertEquals(2, displayedChordTransposition(0, 2, true))
        assertEquals(-2, displayedChordTransposition(0, -2, true))
        assertEquals(1, displayedChordTransposition(-1, 2, true))
        assertEquals(1, displayedChordTransposition(3, -2, true))
    }

    @Test
    fun `neutral pitch and disabling sync restore manual display without changing source`() {
        val source = listOf(LrcLine(timeMs = 0L, text = "[C] [Am] [F] [G]"))
        val shifted = deriveAudioLyricsChordGrid(source, displayedChordTransposition(0, 2, true))
        assertEquals("D   Bm   G   A", shifted.single().text)
        assertEquals(3, displayedChordTransposition(3, 0, true))
        val restored = deriveAudioLyricsChordGrid(source, displayedChordTransposition(0, 2, false))
        assertEquals("C   Am   F   G", restored.single().text)
        assertEquals("[C] [Am] [F] [G]", source.single().text)
    }

    @Test
    fun `combined offset uses existing pitch class wrapping`() {
        val source = listOf(LrcLine(timeMs = 0L, text = "[C]"))
        val rendered = deriveAudioLyricsChordGrid(source, displayedChordTransposition(11, 2, true))
        assertEquals("C#", rendered.single().text)
    }
}
