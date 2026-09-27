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

    @Test fun singleLetterChordsAreRecognizedButDoNotMakeALineConvertibleAlone() {
        listOf("C", "D", "G").forEach { chord ->
            assertTrue(parseChordSymbol(chord) != null)
            val source = "$chord\nparoles"
            assertEquals(source, convert(source))
            assertEquals(0, analyzeChordLineImport(source).convertiblePairCount)
        }
    }

    @Test fun isolatedLettersInOrdinaryFrenchTextAreRefused() {
        listOf(
            "A partir de quand ?\nparoles",
            "A mon avis\nparoles",
            "G comme guitare\nparoles",
            "C'est à dire\nparoles",
            "D'accord\nparoles"
        ).forEach { source -> assertEquals(source, convert(source)) }
    }

    @Test fun convertsRecognizedTokensAndIgnoresOrdinaryUnknownTokens() {
        assertEquals(
            "[A]ab[D]cd[Bm7]ef",
            convert("A D Bm7\nabcdef")
        )
        assertEquals(
            "[C]abcdefg[D]h",
            convert("C Suis D\nabcdefgh")
        )
        assertEquals(
            "[G]abcdefgh[F]i",
            convert("G texte F\nabcdefghi")
        )
        assertEquals(
            "[Bm7/E]abcdefghijkl[F#sus4]m",
            convert("Bm7/E texte F#sus4\nabcdefghijklm")
        )
        assertEquals(
            "[C]abcdefghijkl[D]m",
            convert("C Mot Autre D\nabcdefghijklm")
        )
        assertEquals(
            "[C]abcd[D]ef[E]g",
            convert("C X D E\nabcdefg")
        )
    }

    @Test fun convertsTwoChordLinesIntoOneLyricLine() {
        val source = "C D\nE F\nabcdefgh"
        val analysis = analyzeChordLineImport(source)

        assertEquals("[C][E]ab[D][F]cdefgh", applyChordLineImport(analysis))
        assertEquals(1, analysis.convertiblePairCount)
        assertEquals(0, analysis.ambiguousLineCount)
    }

    @Test fun convertsRealMixedAndPureChordBlockInLineThenTokenOrder() {
        val source = "C   Suis   D   Bm7\nBm7/E E7\nN'oublie pas : dis-lui..."

        val converted = convert(source)

        assertEquals(
            "[C][Bm7/E]N'oubl[E7]ie pa[D]s : [Bm7]dis-lui...",
            converted
        )
        assertTrue("Suis" !in converted)
    }

    @Test fun convertsThreeChordLinesIntoOneLyricLine() {
        assertEquals(
            "[C][E][G]ab[D][F][A]cdefgh",
            convert("C D\nE F\nG A\nabcdefgh")
        )
    }

    @Test fun fourSuccessiveChordLinesAreRefusedAsOneBlock() {
        val source = "C D\nE F\nG A\nBm Em\nabcdefgh"
        val analysis = analyzeChordLineImport(source)

        assertEquals(source, applyChordLineImport(analysis))
        assertEquals(0, analysis.convertiblePairCount)
        assertEquals(1, analysis.ambiguousLineCount)
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

    @Test fun partiallyConvertsChordsAndDropsTheValidatedNoiseLine() {
        val source = "C   Suis   D\nabcdefghijkl"
        val analysis = analyzeChordLineImport(source)
        val converted = applyChordLineImport(analysis)

        assertEquals("[C]abcdefghijk[D]l", converted)
        assertTrue("Suis" !in converted)
        assertTrue('\n' !in converted)
        assertEquals(1, analysis.convertiblePairCount)
        assertEquals(0, analysis.ambiguousLineCount)
    }

    @Test fun partialConversionPreservesLfCrLfAndCrOutsideTheConvertedPair() {
        listOf("\n", "\r\n", "\r").forEach { separator ->
            val source = "intro${separator}C   Suis   D${separator}abcdefghijkl${separator}outro"
            assertEquals(
                "intro${separator}[C]abcdefghijk[D]l${separator}outro",
                convert(source)
            )
        }
    }

    @Test fun partialConversionAcceptsTabSeparators() {
        assertEquals(
            "[C]abcdefghijkl[D]",
            convert("C\tSuis\tD\nabcdefghijkl")
        )
    }

    @Test fun partiallyConvertsUnknownTokensAtTheBeginningOrEnd() {
        assertEquals(
            "abcdefg[C]hijk[D]l",
            convert("Suis   C   D\nabcdefghijkl")
        )
        assertEquals(
            "[C]abcd[D]efghijkl",
            convert("C   D   Suis\nabcdefghijkl")
        )
    }

    @Test fun partiallyConvertsThreeOfFourTokens() {
        assertEquals(
            "[C]abcdefgh[D]ijkl[E]m",
            convert("C   X   D   E\nabcdefghijklm")
        )
    }

    @Test fun oneRecognizedChordStillDoesNotMakeANoisyLineConvertible() {
        listOf(
            "C Suis\nparoles",
            "Bm7 texte\nparoles"
        ).forEach { source -> assertEquals(source, convert(source)) }
    }

    @Test fun dropsNearlyValidAndTextTokensWhileSupportingEnrichedChords() {
        assertEquals(
            "[C]abcdefghi[D]jkl",
            convert("C   H7   D\nabcdefghijkl")
        )
        assertEquals(
            "[Bm7/E]abcdefghijklmnop[F#sus4]",
            convert("Bm7/E   texte   F#sus4\nabcdefghijklmnop")
        )
    }

    @Test fun unsafePartialProjectionIsRefused() {
        val source = "C   Suis          D\nabc"

        assertEquals(source, convert(source))
        assertEquals(1, analyzeChordLineImport(source).ambiguousLineCount)
    }

    @Test fun rejectsMarkupLikeUnknownTokens() {
        val source = "C   [Chœur]   D\nabcdefghijkl"
        assertEquals(source, convert(source))
    }

    @Test fun sectionsBlankLinesAndMissingLyricsAreBlockBarriers() {
        listOf(
            "C D\n[Chœur]",
            "C D\n[Outro]",
            "C D",
            "C D\n\nparoles"
        ).forEach { source ->
            assertEquals(source, convert(source))
            assertEquals(0, analyzeChordLineImport(source).convertiblePairCount)
        }
    }

    @Test fun sectionAndBlankLinePreventIncorrectBlockFusion() {
        assertEquals(
            "C D\n[Chœur]\n[E]pa[F]roles",
            convert("C D\n[Chœur]\nE F\nparoles")
        )
        assertEquals(
            "C D\n\n[E]pa[F]roles",
            convert("C D\n\nE F\nparoles")
        )
    }

    @Test fun existingChordProInEitherLineIsRefused() {
        listOf(
            "[C] [D]\nparoles",
            "C D\nparoles [Em]ici",
            "C D\n[E] [F]\nparoles"
        ).forEach { source -> assertEquals(source, convert(source)) }
    }

    @Test fun unsafeProjectionInOneChordLineRejectsTheWholeBlock() {
        val source = "C D\nE          F\nabc"
        val analysis = analyzeChordLineImport(source)

        assertEquals(source, applyChordLineImport(analysis))
        assertEquals(0, analysis.convertiblePairCount)
        assertEquals(1, analysis.ambiguousLineCount)
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

    @Test fun chordBlocksPreserveLfCrLfAndCrOutsideTheReplacement() {
        listOf("\n", "\r\n", "\r").forEach { separator ->
            val source = "intro${separator}C D${separator}E F${separator}abcd${separator}outro"
            assertEquals(
                "intro${separator}[C][E]ab[D][F]cd${separator}outro",
                convert(source)
            )
        }
    }

    @Test fun convertsMultipleSafePairsAndPreservesBlankSeparator() {
        val source = "C D\nabcd\n\nAm7 Am7/D\nefgh"
        assertEquals("[C]ab[D]cd\n\n[Am7]efgh[Am7/D]", convert(source))
    }

    @Test fun conversionIsIdempotent() {
        val converted = convert("C D\nabcd\n\nAm7 Am7/D\nefgh")
        assertEquals(converted, convert(converted))
        assertTrue(analyzeChordLineImport(converted).replacements.isEmpty())

        val partial = convert("C   Suis   D\nabcdefghijkl")
        assertEquals(partial, convert(partial))
        assertTrue(analyzeChordLineImport(partial).replacements.isEmpty())

        val block = convert("C D\nE F\nG A\nabcdefgh")
        assertEquals(block, convert(block))
        assertTrue(analyzeChordLineImport(block).replacements.isEmpty())
    }

    @Test fun explicitBoldChordNormalizerRemainsIndependent() {
        val source = "C D\nparoles **Em**"
        assertEquals("C D\nparoles [Em]", applyChordProImport(analyzeChordProImport(source)))
        assertEquals("[C]pa[D]roles **Em**", convert(source))
    }
}
