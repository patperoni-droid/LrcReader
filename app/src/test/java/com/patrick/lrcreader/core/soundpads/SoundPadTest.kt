package com.patrick.lrcreader.core.soundpads

import org.junit.Assert.*
import org.junit.Test

class SoundPadTest {
    @Test fun emptyPadAndReservedAppearanceAreValidButEmptyPadCannotHaveTrim() {
        val pad = SoundPad("empty", "Empty", colorArgb = 0xFF336699L)
        assertEquals("", pad.audioPath)
        assertNull(pad.outMs)
        assertEquals(0xFF336699L, pad.colorArgb)
        assertThrows(IllegalArgumentException::class.java) { pad.copy(inMs = 1L) }
        assertThrows(IllegalArgumentException::class.java) { pad.copy(colorArgb = -1L) }
    }
    @Test fun bankIsNotLimitedToDefaultGridSizes() {
        val bank = (1..25).map { SoundPad("id-$it", "Pad $it", "/private/$it.mp3") }
        assertEquals(25, bank.size)
        assertEquals(0, bank.last().pitchSemitones)
    }
    @Test fun busAndIndividualMultiplyExactlyOnce() {
        assertEquals(0.0625f, padsEffectiveGain(0.5f, 0.5f), 0.00001f)
        assertEquals(0f, padsEffectiveGain(0.7f, 0f), 0f)
        assertEquals(0.7f, padsEffectiveGain(0.7f, 1f), 0f)
    }
    @Test fun invalidTrimIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { SoundPad("a", "A", "a.mp3", inMs = 100, outMs = 100) }
        assertThrows(IllegalArgumentException::class.java) { SoundPad("a", "A", "a.mp3", inMs = -1) }
    }
    @Test fun invalidGainAndUnimplementedPitchAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { SoundPad("a", "A", "a.mp3", volume = Float.NaN) }
        assertThrows(IllegalArgumentException::class.java) { SoundPad("a", "A", "a.mp3", pitchSemitones = 1) }
    }
}
