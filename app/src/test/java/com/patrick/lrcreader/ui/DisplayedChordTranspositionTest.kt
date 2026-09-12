package com.patrick.lrcreader.ui

import com.patrick.lrcreader.core.LrcLine
import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayedChordTranspositionTest {
    @Test
    fun `chord actions stay independent with sync off`() {
        val change = planLiveChordPitchChange(0, 2, 0, 1, false, false)
        assertEquals(1, change.manual)
        assertEquals(2, change.pitch)
        assertEquals(1, displayedChordTransposition(change.manual, change.pitch, false, change.compensation))
    }

    @Test
    fun `one chord click changes pitch and displayed chords exactly once`() {
        val up = planLiveChordPitchChange(0, 0, 0, 1, false, true)
        assertEquals(LiveChordPitchChange(1, 1, 1), up)
        assertEquals(1, displayedChordTransposition(up.manual, up.pitch, true, up.compensation))
        val down = planLiveChordPitchChange(up.manual, up.pitch, up.compensation, 0, false, true)
        assertEquals(LiveChordPitchChange(0, 0, 0), down)
        assertEquals(0, displayedChordTransposition(down.manual, down.pitch, true, down.compensation))
    }

    @Test
    fun `reset clears only manual with sync off and both with sync on`() {
        assertEquals(LiveChordPitchChange(0, 3, 2), planLiveChordPitchChange(2, 3, 2, 0, true, false))
        assertEquals(LiveChordPitchChange(0, 0, 0), planLiveChordPitchChange(2, 3, 2, 0, true, true))
    }

    @Test
    fun `pitch actions still move chords only while sync is on`() {
        assertEquals(2, displayedChordTransposition(1, 3, true, 2))
        assertEquals(1, displayedChordTransposition(1, 3, false, 2))
    }

    @Test
    fun `pitch limit never exceeds six and chord display still advances once`() {
        val change = planLiveChordPitchChange(0, 6, 0, 1, false, true)
        assertEquals(LiveChordPitchChange(1, 6, 0), change)
        assertEquals(7, displayedChordTransposition(change.manual, change.pitch, true, change.compensation))
    }

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
