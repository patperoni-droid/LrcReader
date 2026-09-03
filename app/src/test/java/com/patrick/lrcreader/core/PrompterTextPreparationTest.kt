package com.patrick.lrcreader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrompterTextPreparationTest {

    @Test
    fun simpleText_keepsHistoricalFastPathFlags() {
        val source = "Je vais bien"

        val document = preparePrompterText(source)

        assertEquals(source, document.source)
        assertEquals(source, document.lines.single().plainText)
        assertFalse(document.hasChords)
        assertFalse(document.hasFormatting)
    }

    @Test
    fun simpleChord_isRemappedToPlainText() {
        val line = preparePrompterText("Je [Am]vais").lines.single()

        assertEquals("Je vais", line.plainText)
        assertChord(line, index = 0, symbol = "Am", offset = 3)
        assertTrue(line.hasChords)
        assertFalse(line.hasFormatting)
    }

    @Test
    fun boldOnly_exposesFormattingWithoutChords() {
        val document = preparePrompterText("**Bonjour**")
        val line = document.lines.single()

        assertEquals("Bonjour", line.plainText)
        assertEquals(
            listOf(PrompterRichTextSpan(0, 7, PrompterRichTextStyle.BOLD)),
            line.spans
        )
        assertFalse(document.hasChords)
        assertTrue(document.hasFormatting)
    }

    @Test
    fun italicOnly_exposesFormattingWithoutChords() {
        val line = preparePrompterText("*Bonsoir*").lines.single()

        assertEquals("Bonsoir", line.plainText)
        assertEquals(PrompterRichTextStyle.ITALIC, line.spans.single().style)
        assertFalse(line.hasChords)
        assertTrue(line.hasFormatting)
    }

    @Test
    fun title_isPreparedAsTitleBlock() {
        val line = preparePrompterText("# Ma chanson").lines.single()

        assertEquals(PrompterRichTextBlockKind.TITLE, line.blockKind)
        assertEquals("Ma chanson", line.plainText)
    }

    @Test
    fun section_isPreparedAsSectionBlock() {
        val line = preparePrompterText("## Refrain").lines.single()

        assertEquals(PrompterRichTextBlockKind.SECTION, line.blockKind)
        assertEquals("Refrain", line.plainText)
        assertFalse(line.hasChords)
    }

    @Test
    fun divider_isPreparedAsEmptyDividerWithoutChords() {
        val line = preparePrompterText("---").lines.single()

        assertEquals(PrompterRichTextBlockKind.DIVIDER, line.blockKind)
        assertEquals("", line.plainText)
        assertTrue(line.chords.isEmpty())
    }

    @Test
    fun criticalBoldCase_keepsChordBeforeExpectedWord() {
        val line = preparePrompterText("**Je [Am]voulais**").lines.single()

        assertEquals("**Je voulais**", line.intermediateText)
        assertEquals("Je voulais", line.plainText)
        assertChord(line, index = 0, symbol = "Am", offset = 3)
    }

    @Test
    fun chordBeforeBoldText_isRemappedToStart() {
        val line = preparePrompterText("[Am]**Bonjour**").lines.single()

        assertEquals("Bonjour", line.plainText)
        assertChord(line, index = 0, symbol = "Am", offset = 0)
    }

    @Test
    fun chordInsideBoldText_isRemappedBeforeFollowingWord() {
        val line = preparePrompterText("**Bonjour [Am]Patrick**").lines.single()

        assertEquals("Bonjour Patrick", line.plainText)
        assertChord(line, index = 0, symbol = "Am", offset = 8)
    }

    @Test
    fun chordInsideItalicText_isRemappedBeforeFollowingWord() {
        val line = preparePrompterText("*Je [F]vais bien*").lines.single()

        assertEquals("Je vais bien", line.plainText)
        assertChord(line, index = 0, symbol = "F", offset = 3)
    }

    @Test
    fun sectionChord_isRemappedAfterSectionPrefixRemoval() {
        val line = preparePrompterText("## [G]Refrain").lines.single()

        assertEquals(PrompterRichTextBlockKind.SECTION, line.blockKind)
        assertEquals("Refrain", line.plainText)
        assertChord(line, index = 0, symbol = "G", offset = 0)
    }

    @Test
    fun titleChord_isRemappedAfterTitlePrefixRemoval() {
        val line = preparePrompterText("# [C]Ma chanson").lines.single()

        assertEquals(PrompterRichTextBlockKind.TITLE, line.blockKind)
        assertEquals("Ma chanson", line.plainText)
        assertChord(line, index = 0, symbol = "C", offset = 0)
    }

    @Test
    fun multipleChordsAcrossBoldText_keepExactOffsets() {
        val line = preparePrompterText("Je [Am]**vais** [F]bien").lines.single()

        assertEquals("Je vais bien", line.plainText)
        assertChord(line, index = 0, symbol = "Am", offset = 3)
        assertChord(line, index = 1, symbol = "F", offset = 8)
    }

    @Test
    fun chordImmediatelyAfterBoldOpening_isRemappedToStyledTextStart() {
        val line = preparePrompterText("Je **[Am]vais** bien").lines.single()

        assertEquals("Je vais bien", line.plainText)
        assertChord(line, index = 0, symbol = "Am", offset = 3)
    }

    @Test
    fun slashChordImmediatelyAfterItalicOpening_isRemappedToStyledTextStart() {
        val line = preparePrompterText("Je *[C/E]vais* bien").lines.single()

        assertEquals("Je vais bien", line.plainText)
        assertChord(line, index = 0, symbol = "C/E", offset = 3)
    }

    @Test
    fun incompleteMarkdownRemainsVisibleAndChordUsesVisibleOffset() {
        val line = preparePrompterText("**Je [Am]voulais").lines.single()

        assertEquals("**Je voulais", line.plainText)
        assertTrue(line.spans.isEmpty())
        assertFalse(line.hasFormatting)
        assertChord(line, index = 0, symbol = "Am", offset = 5)
    }

    @Test
    fun chordLikeSectionLabel_remainsVisibleBodyText() {
        val document = preparePrompterText("[Refrain]")
        val line = document.lines.single()

        assertEquals("[Refrain]", line.plainText)
        assertEquals(PrompterRichTextBlockKind.BODY, line.blockKind)
        assertFalse(document.hasChords)
        assertFalse(document.hasFormatting)
    }

    @Test
    fun adjacentChords_keepOrderAndShareFinalOffset() {
        val line = preparePrompterText("[C][G]**Bonjour**").lines.single()

        assertEquals("Bonjour", line.plainText)
        assertEquals(listOf("C", "G"), line.chords.map { it.symbol.raw })
        assertEquals(listOf(0, 0), line.chords.map { it.plainTextOffset })
    }

    @Test
    fun terminalChord_isKeptAtPlainTextEnd() {
        val line = preparePrompterText("Bonjour[Am]").lines.single()

        assertEquals("Bonjour", line.plainText)
        assertChord(line, index = 0, symbol = "Am", offset = 7)
    }

    @Test
    fun terminalChordAfterBoldText_isKeptAtPlainTextEnd() {
        val line = preparePrompterText("**Bonjour**[Am]").lines.single()

        assertEquals("Bonjour", line.plainText)
        assertChord(line, index = 0, symbol = "Am", offset = 7)
    }

    @Test
    fun dividerChord_isRetainedAtOffsetZero() {
        val line = preparePrompterText("[Am]---").lines.single()

        assertEquals(PrompterRichTextBlockKind.DIVIDER, line.blockKind)
        assertEquals("", line.plainText)
        assertChord(line, index = 0, symbol = "Am", offset = 0)
    }

    @Test
    fun emojiBeforeChord_keepsUtf16Offset() {
        val line = preparePrompterText("**🎸Je [Am]vais**").lines.single()

        assertEquals("🎸Je vais", line.plainText)
        assertEquals("🎸Je ".length, line.chords.single().plainTextOffset)
        assertEquals(5, line.chords.single().plainTextOffset)
    }

    @Test
    fun accentsBeforeChord_arePreservedInOffset() {
        val line = preparePrompterText("**Été [Am]ici**").lines.single()

        assertEquals("Été ici", line.plainText)
        assertChord(line, index = 0, symbol = "Am", offset = 4)
    }

    @Test
    fun apostrophesBeforeChord_arePreservedInOffset() {
        val line = preparePrompterText("*J'aime [F]chanter*").lines.single()

        assertEquals("J'aime chanter", line.plainText)
        assertChord(line, index = 0, symbol = "F", offset = 7)
    }

    @Test
    fun lfKeepsFormattedPlainAndEmptyLinesAssociated() {
        val document = preparePrompterText("# Titre\n\n[Am]**Bonjour**\n")

        assertEquals(listOf("Titre", "", "Bonjour", ""), document.lines.map { it.plainText })
        assertEquals(PrompterRichTextBlockKind.TITLE, document.lines[0].blockKind)
        assertTrue(document.lines[1].chords.isEmpty())
        assertChord(document.lines[2], index = 0, symbol = "Am", offset = 0)
        assertTrue(document.lines[3].chords.isEmpty())
    }

    @Test
    fun crlfKeepsLineAssociationAndFinalEmptyLine() {
        val source = "# Titre\r\n[Am]**Bonjour**\r\n"
        val document = preparePrompterText(source)

        assertEquals(source, document.source)
        assertEquals(listOf("Titre", "Bonjour", ""), document.lines.map { it.plainText })
        assertChord(document.lines[1], index = 0, symbol = "Am", offset = 0)
    }

    @Test
    fun crKeepsLineAssociationAndFinalEmptyLine() {
        val source = "## Section\r*Je [F]vais*\r"
        val document = preparePrompterText(source)

        assertEquals(source, document.source)
        assertEquals(listOf("Section", "Je vais", ""), document.lines.map { it.plainText })
        assertEquals(PrompterRichTextBlockKind.SECTION, document.lines[0].blockKind)
        assertChord(document.lines[1], index = 0, symbol = "F", offset = 3)
    }

    @Test
    fun preparedChordRetainsOriginalAnchorInformation() {
        val chord = preparePrompterText("Je [C/E]vais").lines.single().chords.single()

        assertEquals(3, chord.anchor.lyricOffset)
        assertEquals(3..7, chord.anchor.sourceRange)
        assertEquals('C', chord.symbol.root)
        assertEquals('E', chord.symbol.bassRoot)
    }

    private fun assertChord(
        line: PrompterPreparedLine,
        index: Int,
        symbol: String,
        offset: Int
    ) {
        val chord = line.chords[index]
        assertEquals(symbol, chord.symbol.raw)
        assertEquals(offset, chord.plainTextOffset)
    }
}
