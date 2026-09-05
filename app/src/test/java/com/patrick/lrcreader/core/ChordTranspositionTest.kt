package com.patrick.lrcreader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ChordTranspositionTest {

    @Test
    fun naturalRootsAndCommonSuffixesTransposeUp() {
        assertEquals("D", transpose("C", 2))
        assertEquals("Bm", transpose("Am", 2))
        assertEquals("D7", transpose("C7", 2))
        assertEquals("Dmaj7", transpose("Cmaj7", 2))
        assertEquals("Dm7", transpose("Cm7", 2))
        assertEquals("Dsus4", transpose("Csus4", 2))
        assertEquals("Dadd9", transpose("Cadd9", 2))
        assertEquals("Ddim", transpose("Cdim", 2))
        assertEquals("Daug", transpose("Caug", 2))
    }

    @Test
    fun explicitSharpsKeepSharpSpelling() {
        assertEquals("G#m7", transpose("F#m7", 2))
        assertEquals("D#", transpose("C#", 2))
    }

    @Test
    fun explicitFlatsKeepFlatSpelling() {
        assertEquals("Eb", transpose("Db", 2))
        assertEquals("C7", transpose("Bb7", 2))
        assertEquals("Ab", transpose("Gb", 2))
    }

    @Test
    fun slashChordTransposesRootAndBass() {
        assertEquals("D/F#", transpose("C/E", 2))
        assertEquals("C/E", transpose("D/F#", -2))
        assertEquals("Eb/Bb", transpose("Db/Ab", 2))
    }

    @Test
    fun negativeAndLargeOffsetsAreNormalizedModuloTwelve() {
        assertEquals("E", transpose("F#", -2))
        assertEquals("D", transpose("C", 14))
        assertEquals("B", transpose("C", -13))
        assertEquals("Dbmaj7", transpose("Dbmaj7", 24))
        assertEquals("G", transpose("C", Int.MAX_VALUE))
        assertEquals("E", transpose("C", Int.MIN_VALUE))
    }

    @Test
    fun zeroKeepsTheExactOriginalSpelling() {
        assertEquals("C", transpose("C", 0))
        assertEquals("Db7", transpose("Db7", 0))
        assertEquals("B#add9/Fb", transpose("B#add9/Fb", 0))
    }

    @Test
    fun recognizedSuffixIsPreservedVerbatim() {
        assertEquals("Dalt(b9)", transpose("Calt(b9)", 2))
        assertEquals("Edom7add9", transpose("Ddom7add9", 2))
    }

    @Test
    fun parserKeepsSourceAndExcludesNonChordContentFromTransposition() {
        val source = "{title: Demo}\n[Refrain] [C]Bonjour [H]texte"
        val document = parseChordPro(source)

        assertEquals(source, document.source)
        assertEquals(listOf("D"), document.lines.flatMap { line ->
            line.anchors.map { anchor -> transposeChord(anchor.symbol, 2) }
        })
        assertEquals("{title: Demo}", document.lines[0].lyricText)
        assertEquals("[Refrain] Bonjour [H]texte", document.lines[1].lyricText)
        assertFalse(parseChordPro("[Refrain]").hasChords)
    }

    private fun transpose(raw: String, semitones: Int): String {
        val document = parseChordPro("[$raw]")
        val symbol = document.lines.single().anchors.single().symbol
        return transposeChord(symbol, semitones)
    }
}
