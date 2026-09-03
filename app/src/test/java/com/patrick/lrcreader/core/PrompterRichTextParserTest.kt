package com.patrick.lrcreader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrompterRichTextParserTest {

    @Test
    fun simpleText_isPreservedWithoutFormatting() {
        val source = "Bonjour Patrick"

        val document = parsePrompterRichText(source)

        assertEquals(source, document.source)
        assertEquals(source, document.lines.single().plainText)
        assertEquals(PrompterRichTextBlockKind.BODY, document.lines.single().blockKind)
        assertTrue(document.lines.single().spans.isEmpty())
        assertFalse(document.hasFormatting)
    }

    @Test
    fun emptyLine_isPreserved() {
        val line = parsePrompterRichText("").lines.single()

        assertEquals("", line.source)
        assertEquals("", line.plainText)
        assertEquals(listOf(0), line.inputOffsetToPlainOffset)
    }

    @Test
    fun titleMarkerAtLineStart_createsTitleBlock() {
        val line = parsePrompterRichText("# Ma chanson").lines.single()

        assertEquals(PrompterRichTextBlockKind.TITLE, line.blockKind)
        assertEquals("Ma chanson", line.plainText)
    }

    @Test
    fun sectionMarkerAtLineStart_createsSectionBlock() {
        val line = parsePrompterRichText("## Refrain").lines.single()

        assertEquals(PrompterRichTextBlockKind.SECTION, line.blockKind)
        assertEquals("Refrain", line.plainText)
    }

    @Test
    fun unsupportedThirdLevelHeading_remainsLiteralBodyText() {
        val source = "### Refrain"
        val line = parsePrompterRichText(source).lines.single()

        assertEquals(PrompterRichTextBlockKind.BODY, line.blockKind)
        assertEquals(source, line.plainText)
    }

    @Test
    fun leadingSpacePreventsTitleRecognition() {
        val source = " ## Ma chanson"
        val line = parsePrompterRichText(source).lines.single()

        assertEquals(PrompterRichTextBlockKind.BODY, line.blockKind)
        assertEquals(source, line.plainText)
    }

    @Test
    fun missingSpacePreventsTitleRecognition() {
        val source = "#Ma chanson"
        val line = parsePrompterRichText(source).lines.single()

        assertEquals(PrompterRichTextBlockKind.BODY, line.blockKind)
        assertEquals(source, line.plainText)
    }

    @Test
    fun exactDivider_createsEmptyDividerBlock() {
        val line = parsePrompterRichText("---").lines.single()

        assertEquals(PrompterRichTextBlockKind.DIVIDER, line.blockKind)
        assertEquals("", line.plainText)
        assertTrue(line.spans.isEmpty())
    }

    @Test
    fun dividerWithSurroundingSpaces_isRecognized() {
        val line = parsePrompterRichText("   ---   ").lines.single()

        assertEquals(PrompterRichTextBlockKind.DIVIDER, line.blockKind)
        assertEquals("", line.plainText)
    }

    @Test
    fun dividerFollowedByText_remainsLiteralBodyText() {
        val source = "--- texte"
        val line = parsePrompterRichText(source).lines.single()

        assertEquals(PrompterRichTextBlockKind.BODY, line.blockKind)
        assertEquals(source, line.plainText)
    }

    @Test
    fun boldPair_removesMarkersAndCreatesSpan() {
        val line = parsePrompterRichText("Bonjour **Patrick**").lines.single()

        assertEquals("Bonjour Patrick", line.plainText)
        assertEquals(
            listOf(PrompterRichTextSpan(8, 15, PrompterRichTextStyle.BOLD)),
            line.spans
        )
    }

    @Test
    fun fullLineBold_coversAllPlainText() {
        val line = parsePrompterRichText("**Bonjour**").lines.single()

        assertEquals("Bonjour", line.plainText)
        assertEquals(
            listOf(PrompterRichTextSpan(0, 7, PrompterRichTextStyle.BOLD)),
            line.spans
        )
    }

    @Test
    fun italicPair_removesMarkersAndCreatesSpan() {
        val line = parsePrompterRichText("Bonjour *Patrick*").lines.single()

        assertEquals("Bonjour Patrick", line.plainText)
        assertEquals(
            listOf(PrompterRichTextSpan(8, 15, PrompterRichTextStyle.ITALIC)),
            line.spans
        )
    }

    @Test
    fun separatedBoldAndItalicSpans_keepTheirOrder() {
        val line = parsePrompterRichText("**Bonjour** et *bonsoir*").lines.single()

        assertEquals("Bonjour et bonsoir", line.plainText)
        assertEquals(
            listOf(
                PrompterRichTextSpan(0, 7, PrompterRichTextStyle.BOLD),
                PrompterRichTextSpan(11, 18, PrompterRichTextStyle.ITALIC)
            ),
            line.spans
        )
    }

    @Test
    fun incompleteOpeningBoldMarker_remainsLiteral() {
        val source = "**bonjour"
        val line = parsePrompterRichText(source).lines.single()

        assertEquals(source, line.plainText)
        assertTrue(line.spans.isEmpty())
    }

    @Test
    fun incompleteClosingBoldMarker_remainsLiteral() {
        val source = "bonjour**"
        val line = parsePrompterRichText(source).lines.single()

        assertEquals(source, line.plainText)
        assertTrue(line.spans.isEmpty())
    }

    @Test
    fun incompleteItalicMarker_remainsLiteral() {
        val source = "bonjour *Patrick"
        val line = parsePrompterRichText(source).lines.single()

        assertEquals(source, line.plainText)
        assertTrue(line.spans.isEmpty())
    }

    @Test
    fun singleAsterisk_remainsLiteral() {
        val line = parsePrompterRichText("*").lines.single()

        assertEquals("*", line.plainText)
        assertTrue(line.spans.isEmpty())
    }

    @Test
    fun emptyBoldMarker_remainsLiteral() {
        val line = parsePrompterRichText("****").lines.single()

        assertEquals("****", line.plainText)
        assertTrue(line.spans.isEmpty())
    }

    @Test
    fun nestedItalicInsideBold_remainsEntirelyLiteral() {
        val source = "**gras *italique* gras**"
        val line = parsePrompterRichText(source).lines.single()

        assertEquals(source, line.plainText)
        assertTrue(line.spans.isEmpty())
    }

    @Test
    fun nestedBoldInsideItalic_remainsEntirelyLiteral() {
        val source = "*italique **gras** italique*"
        val line = parsePrompterRichText(source).lines.single()

        assertEquals(source, line.plainText)
        assertTrue(line.spans.isEmpty())
    }

    @Test
    fun chordProChord_isOrdinaryTextForThisParser() {
        val source = "[Am]"
        val line = parsePrompterRichText(source).lines.single()

        assertEquals(source, line.plainText)
        assertTrue(line.spans.isEmpty())
    }

    @Test
    fun chordProSectionLabel_isOrdinaryTextForThisParser() {
        val source = "[Refrain]"
        val line = parsePrompterRichText(source).lines.single()

        assertEquals(source, line.plainText)
        assertEquals(PrompterRichTextBlockKind.BODY, line.blockKind)
    }

    @Test
    fun chordProInsideLyrics_isPreservedExactly() {
        val source = "Je [Am]voulais"
        val line = parsePrompterRichText(source).lines.single()

        assertEquals(source, line.plainText)
        assertEquals((0..source.length).toList(), line.inputOffsetToPlainOffset)
    }

    @Test
    fun accentsArePreservedInsideStyledText() {
        val line = parsePrompterRichText("**Été à Noël**").lines.single()

        assertEquals("Été à Noël", line.plainText)
        assertEquals(PrompterRichTextSpan(0, 10, PrompterRichTextStyle.BOLD), line.spans.single())
    }

    @Test
    fun apostrophesArePreservedInsideStyledText() {
        val line = parsePrompterRichText("*C'est l'été*").lines.single()

        assertEquals("C'est l'été", line.plainText)
        assertEquals(
            PrompterRichTextSpan(0, "C'est l'été".length, PrompterRichTextStyle.ITALIC),
            line.spans.single()
        )
    }

    @Test
    fun emojiIsPreservedAsUtf16Text() {
        val line = parsePrompterRichText("**🎸 musique**").lines.single()

        assertEquals("🎸 musique", line.plainText)
        assertEquals("🎸 musique".length, line.spans.single().endExclusive)
    }

    @Test
    fun crlfSeparatorsCreateDistinctLinesAndPreserveSource() {
        val source = "Un\r\nDeux\r\n"
        val document = parsePrompterRichText(source)

        assertEquals(source, document.source)
        assertEquals(listOf("Un", "Deux", ""), document.lines.map { it.source })
    }

    @Test
    fun crSeparatorsCreateDistinctLinesAndPreserveSource() {
        val source = "Un\rDeux\r"
        val document = parsePrompterRichText(source)

        assertEquals(source, document.source)
        assertEquals(listOf("Un", "Deux", ""), document.lines.map { it.source })
    }

    @Test
    fun lfPreservesInitialIntermediateAndFinalEmptyLines() {
        val source = "\nUn\n\nDeux\n"
        val document = parsePrompterRichText(source)

        assertEquals(source, document.source)
        assertEquals(listOf("", "Un", "", "Deux", ""), document.lines.map { it.source })
    }

    @Test
    fun simpleTextOffsetMapping_isIdentity() {
        val line = parsePrompterRichText("abc").lines.single()

        assertEquals(listOf(0, 1, 2, 3), line.inputOffsetToPlainOffset)
    }

    @Test
    fun boldOffsetMapping_collapsesBothMarkerPairs() {
        val line = parsePrompterRichText("**ab**").lines.single()

        assertEquals(listOf(0, 0, 0, 1, 2, 2, 2), line.inputOffsetToPlainOffset)
    }

    @Test
    fun italicOffsetMapping_collapsesBothMarkers() {
        val line = parsePrompterRichText("*ab*").lines.single()

        assertEquals(listOf(0, 0, 1, 2, 2), line.inputOffsetToPlainOffset)
    }

    @Test
    fun titleOffsetMapping_collapsesLinePrefix() {
        val line = parsePrompterRichText("# Hi").lines.single()

        assertEquals(listOf(0, 0, 0, 1, 2), line.inputOffsetToPlainOffset)
    }

    @Test
    fun sectionOffsetMapping_collapsesLinePrefix() {
        val line = parsePrompterRichText("## Hi").lines.single()

        assertEquals(listOf(0, 0, 0, 0, 1, 2), line.inputOffsetToPlainOffset)
    }

    @Test
    fun dividerOffsetMapping_collapsesWholeLine() {
        val line = parsePrompterRichText(" --- ").lines.single()

        assertEquals(List(6) { 0 }, line.inputOffsetToPlainOffset)
    }

    @Test
    fun emojiOffsetMapping_usesUtf16CodeUnits() {
        val line = parsePrompterRichText("**🎸a**").lines.single()

        assertEquals("🎸a", line.plainText)
        assertEquals(listOf(0, 0, 0, 1, 2, 3, 3, 3), line.inputOffsetToPlainOffset)
        assertEquals(0, line.plainOffsetForInputOffset(2))
        assertEquals(2, line.plainOffsetForInputOffset(4))
        assertEquals(3, line.plainOffsetForInputOffset(7))
    }
}
