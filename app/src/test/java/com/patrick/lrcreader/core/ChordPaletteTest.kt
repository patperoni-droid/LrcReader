package com.patrick.lrcreader.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ChordPaletteTest {

    @Test
    fun parseChordPaletteInput_parsesAndDeduplicates() {
        val palette = parseChordPaletteInput("Am, D, G, F, D")
        assertEquals(listOf("Am", "D", "G", "F"), palette)
    }

    @Test
    fun inferChordPaletteFromText_extractsUniqueChordTokens() {
        val inferred = inferChordPaletteFromText("Am D G\nF C/G D")
        assertEquals(listOf("Am", "D", "G", "F", "C/G"), inferred)
    }

    @Test
    fun insertChordAtCursor_insertsAtSelection() {
        val result = insertChordAtCursor(
            text = "Am  G",
            selectionStart = 3,
            selectionEnd = 3,
            chord = "D"
        )
        assertEquals("Am D G", result.text)
        assertEquals(4, result.cursor)
    }

    @Test
    fun sortChordPaletteByUsage_sortsByDescendingFrequency() {
        val sorted = sortChordPaletteByUsage(
            palette = listOf("Am", "D", "G", "F"),
            rawText = "G Am G D G F"
        )
        assertEquals(listOf("G", "Am", "D", "F"), sorted)
    }

    @Test
    fun sortChordPaletteByUsage_keepsStableOrderOnTie() {
        val sorted = sortChordPaletteByUsage(
            palette = listOf("Am", "D", "G"),
            rawText = "D Am"
        )
        assertEquals(listOf("Am", "D", "G"), sorted)
    }

    @Test
    fun sortChordPaletteByUsage_keepsInitialOrderWhenNoMatch() {
        val sorted = sortChordPaletteByUsage(
            palette = listOf("Am", "D", "G"),
            rawText = "C F Bb"
        )
        assertEquals(listOf("Am", "D", "G"), sorted)
    }

    @Test
    fun sortChordPaletteByUsage_handlesEmptyPalette() {
        val sorted = sortChordPaletteByUsage(
            palette = emptyList(),
            rawText = "Am D G"
        )
        assertEquals(emptyList<String>(), sorted)
    }
}
