package com.patrick.lrcreader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordProParserTest {

    @Test
    fun textWithoutChords_isPreservedAndHasNoChords() {
        val source = "Je voulais te dire"

        val document = parseChordPro(source)

        assertEquals(source, document.source)
        assertEquals(listOf(source), document.lines.map { it.lyricText })
        assertTrue(document.lines.single().anchors.isEmpty())
        assertFalse(document.hasChords)
    }

    @Test
    fun chordAtStart_isRemovedAndAnchoredAtZero() {
        val line = parseChordPro("[C]Bonjour").lines.single()

        assertEquals("Bonjour", line.lyricText)
        assertEquals(0, line.anchors.single().lyricOffset)
        assertEquals(0..2, line.anchors.single().sourceRange)
        assertEquals("C", line.anchors.single().symbol.raw)
    }

    @Test
    fun chordsInMiddle_haveOffsetsInVisibleLyrics() {
        val line = parseChordPro("Je [Am]voulais te [F]dire").lines.single()

        assertEquals("Je voulais te dire", line.lyricText)
        assertEquals(listOf(3, 14), line.anchors.map { it.lyricOffset })
        assertEquals(listOf(3..6, 18..20), line.anchors.map { it.sourceRange })
        assertEquals(listOf("Am", "F"), line.anchors.map { it.symbol.raw })
    }

    @Test
    fun chordAtEnd_isRepresentedAtLyricsLength() {
        val line = parseChordPro("Fin[C]").lines.single()

        assertEquals("Fin", line.lyricText)
        assertEquals(3, line.anchors.single().lyricOffset)
        assertEquals(3..5, line.anchors.single().sourceRange)
    }

    @Test
    fun adjacentChords_shareTheSameOffset() {
        val line = parseChordPro("[C][G]Bonjour").lines.single()

        assertEquals("Bonjour", line.lyricText)
        assertEquals(listOf(0, 0), line.anchors.map { it.lyricOffset })
        assertEquals(listOf("C", "G"), line.anchors.map { it.symbol.raw })
    }

    @Test
    fun multipleChords_keepTheirOrder() {
        val line = parseChordPro("[C]Un [G]deux [Am]trois").lines.single()

        assertEquals("Un deux trois", line.lyricText)
        assertEquals(listOf("C", "G", "Am"), line.anchors.map { it.symbol.raw })
        assertEquals(listOf(0, 3, 8), line.anchors.map { it.lyricOffset })
    }

    @Test
    fun commonComplexChords_areRecognizedAndStructured() {
        val documents = listOf("F#m7", "Bb", "C#m7", "G7sus4", "C/E", "F#/A#")
            .map { raw -> parseChordPro("[$raw]Texte").lines.single().anchors.single().symbol }

        assertEquals("F#m7", documents[0].raw)
        assertEquals('F', documents[0].root)
        assertEquals('#', documents[0].accidental)
        assertEquals("m7", documents[0].suffix)
        assertNull(documents[0].bassRoot)

        assertEquals('B', documents[1].root)
        assertEquals('b', documents[1].accidental)
        assertEquals("", documents[1].suffix)

        assertEquals('C', documents[2].root)
        assertEquals('#', documents[2].accidental)
        assertEquals("m7", documents[2].suffix)

        assertEquals('G', documents[3].root)
        assertNull(documents[3].accidental)
        assertEquals("7sus4", documents[3].suffix)

        assertEquals('C', documents[4].root)
        assertEquals('E', documents[4].bassRoot)
        assertNull(documents[4].bassAccidental)

        assertEquals('F', documents[5].root)
        assertEquals('#', documents[5].accidental)
        assertEquals('A', documents[5].bassRoot)
        assertEquals('#', documents[5].bassAccidental)
    }

    @Test
    fun supportedSuffixFamilies_areRecognized() {
        val source = "[Am][Cmaj7][G7sus4][Ddim][Eaug][Fadd9]Texte"

        val line = parseChordPro(source).lines.single()

        assertEquals(listOf("Am", "Cmaj7", "G7sus4", "Ddim", "Eaug", "Fadd9"), line.anchors.map { it.symbol.raw })
        assertTrue(line.anchors.all { it.lyricOffset == 0 })
    }

    @Test
    fun sectionLabelsAndLowercaseWords_remainVisible() {
        val source = "[Refrain] [Couplet] [bonjour]"

        val document = parseChordPro(source)

        assertEquals(source, document.lines.single().lyricText)
        assertFalse(document.hasChords)
    }

    @Test
    fun unclosedBracket_remainsVisible() {
        val source = "Bonjour [Am"

        val document = parseChordPro(source)

        assertEquals(source, document.lines.single().lyricText)
        assertFalse(document.hasChords)
    }

    @Test
    fun emptyTag_remainsVisible() {
        val source = "Bonjour []"

        val document = parseChordPro(source)

        assertEquals(source, document.lines.single().lyricText)
        assertFalse(document.hasChords)
    }

    @Test
    fun invalidTagsRemainVisibleInFull() {
        val source = "[H7] [Coucou] [C foo] [C/] [C/H]"

        val document = parseChordPro(source)

        assertEquals(source, document.lines.single().lyricText)
        assertFalse(document.hasChords)
    }

    @Test
    fun invalidOuterTagIsNotPartiallyParsed() {
        val source = "[C[Am]"

        val document = parseChordPro(source)

        assertEquals(source, document.lines.single().lyricText)
        assertFalse(document.hasChords)
    }

    @Test
    fun multipleLinesAndEmptyLinesArePreserved() {
        val source = "[C]Bonjour\n\n[Am]Deuxième ligne\n"

        val document = parseChordPro(source)

        assertEquals(source, document.source)
        assertEquals(4, document.lines.size)
        assertEquals(listOf("Bonjour", "", "Deuxième ligne", ""), document.lines.map { it.lyricText })
        assertEquals(listOf("C"), document.lines[0].anchors.map { it.symbol.raw })
        assertTrue(document.lines[1].anchors.isEmpty())
        assertEquals(listOf("Am"), document.lines[2].anchors.map { it.symbol.raw })
        assertTrue(document.lines[3].anchors.isEmpty())
    }

    @Test
    fun initialAndFinalEmptyLinesArePreserved() {
        val source = "\n[C]Bonjour\n\n"

        val document = parseChordPro(source)

        assertEquals(listOf("", "Bonjour", "", ""), document.lines.map { it.lyricText })
        assertEquals(source, document.source)
    }

    @Test
    fun crlfLinesKeepGlobalSourceRanges() {
        val source = "[C]Un\r\n[Am]Deux"

        val document = parseChordPro(source)

        assertEquals(listOf("Un", "Deux"), document.lines.map { it.lyricText })
        assertEquals(0..2, document.lines[0].anchors.single().sourceRange)
        assertEquals(7..10, document.lines[1].anchors.single().sourceRange)
        assertEquals(source, document.source)
    }

    @Test
    fun accentsApostrophesAndUnicodeArePreserved() {
        val source = "Été d’accord 🎸 [Am]à bientôt"

        val line = parseChordPro(source).lines.single()

        assertEquals("Été d’accord 🎸 à bientôt", line.lyricText)
        assertEquals("Été d’accord 🎸 ".length, line.anchors.single().lyricOffset)
    }

    @Test
    fun offsetsUseKotlinStringUtf16Indices() {
        val source = "🎸 [C]été"

        val line = parseChordPro(source).lines.single()

        assertEquals("🎸 été", line.lyricText)
        assertEquals(3, line.anchors.single().lyricOffset)
    }

    @Test
    fun sourceIsPreservedExactlyWithoutTrimming() {
        val source = "  \n[C] Bonjour  \n"

        val document = parseChordPro(source)

        assertEquals(source, document.source)
        assertEquals(listOf("  ", " Bonjour  ", ""), document.lines.map { it.lyricText })
        assertTrue(document.hasChords)
    }

    @Test
    fun arbitraryMalformedInputDoesNotThrowOrLoseText() {
        val source = "]][[[Coucou]]\u0000\n[C7(b9)]ok"

        val document = parseChordPro(source)

        assertEquals(source, document.source)
        assertEquals("]][[[Coucou]]\u0000", document.lines[0].lyricText)
        assertEquals("ok", document.lines[1].lyricText)
        assertEquals("C7(b9)", document.lines[1].anchors.single().symbol.raw)
    }
}
