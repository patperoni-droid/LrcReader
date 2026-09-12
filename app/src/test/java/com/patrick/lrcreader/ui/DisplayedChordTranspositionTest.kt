package com.patrick.lrcreader.ui

import com.patrick.lrcreader.core.LrcLine
import com.patrick.lrcreader.core.DisplayPrefs
import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayedChordTranspositionTest {
    @Test
    fun `stored pitch is neutralized at runtime when sync is off`() {
        assertEquals(0, DisplayPrefs.runtimePitchSemi(4, false))
        assertEquals(4, DisplayPrefs.runtimePitchSemi(4, true))
    }
    @Test
    fun `sync off changes chords only and keeps audio neutral`() {
        assertEquals(LiveChordPitchChange(1, 0), planLiveChordPitchChange(0, 0, 1, false, false))
        assertEquals(LiveChordPitchChange(1, 0), planLiveChordPitchChange(0, 4, 1, false, false))
        assertEquals(LiveChordPitchChange(0, 0), planLiveChordPitchChange(1, 0, 0, true, false))
    }

    @Test
    fun `sync on advances chords and pitch once per click`() {
        val up = planLiveChordPitchChange(0, 0, 1, false, true)
        assertEquals(LiveChordPitchChange(1, 1), up)
        val down = planLiveChordPitchChange(up.manual, up.pitch, 0, false, true)
        assertEquals(LiveChordPitchChange(0, 0), down)
    }

    @Test
    fun `reset clears chords and audio when sync is on`() {
        assertEquals(LiveChordPitchChange(0, 0), planLiveChordPitchChange(3, 3, 0, true, true))
    }

    @Test
    fun `pitch remains within existing bounds while chords keep advancing`() {
        assertEquals(LiveChordPitchChange(7, 6), planLiveChordPitchChange(6, 6, 7, false, true))
        assertEquals(LiveChordPitchChange(-7, -6), planLiveChordPitchChange(-6, -6, -7, false, true))
    }

    @Test
    fun `source chords are unchanged by display transposition`() {
        val source = listOf(LrcLine(timeMs = 0L, text = "[C] [Am] [F] [G]"))
        val rendered = deriveAudioLyricsChordGrid(source, transposeSemitones = 2)
        assertEquals("D   Bm   G   A", rendered.single().text)
        assertEquals("[C] [Am] [F] [G]", source.single().text)
    }
}
