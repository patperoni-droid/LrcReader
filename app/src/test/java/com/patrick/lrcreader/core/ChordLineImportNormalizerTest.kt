package com.patrick.lrcreader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordLineImportNormalizerTest {
    private fun convert(source: String): String = applyChordLineImport(analyzeChordLineImport(source))

    @Test fun convertsTwoChordsUsingTheirLogicalColumns() {
        val source = "    C        D\nN'oublie pas dis-lui"
        assertEquals("N'ou[C]blie pas [D]dis-lui", convert(source))
    }

    @Test fun convertsSeveralChordsIncludingSlashChord() {
        val source = "C   D   Bm7/E\nBonjour le monde"
        assertEquals("[C]Bonj[D]our [Bm7/E]le monde", convert(source))
    }

    @Test fun multipleChordsAtTheSameMappedOffsetKeepSourceOrder() {
        assertEquals("[C]ab[D][E]", convert("C D E\nab"))
    }

    @Test fun chordInsideAWordStaysAtItsColumnAndWhitespaceSnapsForward() {
        assertEquals("ab[C]cd [D]ef", convert("  C D\nabcd ef"))
    }

    @Test fun slightOverrunAnchorsAtEndButLargeOverrunRejectsWholePair() {
        assertEquals("[C]abc[D]", convert("C   D\nabc"))
        val unsafe = "C          D\nabc"
        assertEquals(unsafe, convert(unsafe))
        assertEquals(1, analyzeChordLineImport(unsafe).ambiguousLineCount)
    }

    @Test fun nonChordTokenMakesTheWholeLineAmbiguous() {
        val source = "C   Suis   D   Bm7\nN'oublie pas"
        val analysis = analyzeChordLineImport(source)
        assertEquals(source, applyChordLineImport(analysis))
        assertEquals(0, analysis.convertiblePairCount)
        assertEquals(1, analysis.ambiguousLineCount)
    }

    @Test fun sectionsSuccessiveChordLinesAndMissingLyricsAreRefused() {
        listOf(
            "C D\n[Chœur]",
            "C D\n[Outro]",
            "C D\nE F\nparoles",
            "C D",
            "C D\n\nparoles"
        ).forEach { source ->
            assertEquals(source, convert(source))
            assertEquals(0, analyzeChordLineImport(source).convertiblePairCount)
        }
    }

    @Test fun existingChordProInEitherLineIsRefused() {
        listOf(
            "[C] [D]\nparoles",
            "C D\nparoles [Em]ici"
        ).forEach { source -> assertEquals(source, convert(source)) }
    }

    @Test fun tabsUseFourColumnStopsAndNonBreakingSpacesSeparateTokens() {
        assertEquals("[C]abcd [D]ef", convert("C\tD\nabcd ef"))
        assertEquals("[C]abc[D]d", convert("C\u00A0\u00A0D\nabcd"))
    }

    @Test fun preservesAccentsApostrophesEmojiAndCombinedCharacters() {
        assertEquals("L'[C]été d'[D]accord", convert("  C     D\nL'été d'accord"))
        assertEquals("🎸a[C]bc [D]def", convert("  C D\n🎸abc def"))
        assertEquals("e\u0301[C]abc [D]def", convert(" C   D\ne\u0301abc def"))
    }

    @Test fun preservesLfCrLfAndCrBetweenConvertedLyricLines() {
        val source = "C D\r\nabcd\rE F\rwxyz\nG A\nmnop"
        assertEquals("[C]ab[D]cd\r[E]wx[F]yz\n[G]mn[A]op", convert(source))
        assertEquals(3, analyzeChordLineImport(source).convertiblePairCount)
    }

    @Test fun convertsMultipleSafePairsAndPreservesBlankSeparator() {
        val source = "C D\nabcd\n\nAm7 Am7/D\nefgh"
        assertEquals("[C]ab[D]cd\n\n[Am7]efgh[Am7/D]", convert(source))
    }

    @Test fun conversionIsIdempotent() {
        val converted = convert("C D\nabcd\n\nAm7 Am7/D\nefgh")
        assertEquals(converted, convert(converted))
        assertTrue(analyzeChordLineImport(converted).replacements.isEmpty())
    }

    @Test fun explicitBoldChordNormalizerRemainsIndependent() {
        val source = "C D\nparoles **Em**"
        assertEquals("C D\nparoles [Em]", applyChordProImport(analyzeChordProImport(source)))
        assertEquals("[C]pa[D]roles **Em**", convert(source))
    }
}
