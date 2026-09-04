package com.patrick.lrcreader.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.patrick.lrcreader.core.*
import org.junit.Assert.*
import org.junit.Test

class PrompterMarkupPaletteTest {
    @Test fun blockCommandsInsertAtCursorAndSelectPlaceholder() {
        for (command in listOf(PrompterMarkupCommand.TITLE, PrompterMarkupCommand.SECTION,
            PrompterMarkupCommand.VERSE, PrompterMarkupCommand.CHORUS)) {
            val edited = insertPrompterMarkup(TextFieldValue("avant après", TextRange(6)), command, "Nom")
            assertEquals("avant \n${command.prefix}Nom\naprès", edited.text)
            assertEquals("Nom", edited.text.substring(edited.selection.min, edited.selection.max))
            val line = preparePrompterText(edited.text).lines[1]
            assertEquals("Nom", line.plainText)
            assertEquals(if (command == PrompterMarkupCommand.TITLE) PrompterRichTextBlockKind.TITLE
                else PrompterRichTextBlockKind.SECTION, line.blockKind)
        }
    }

    @Test fun inlineCommandsWrapReversedSelectionWithoutLosingText() {
        for (command in listOf(PrompterMarkupCommand.BOLD, PrompterMarkupCommand.ITALIC, PrompterMarkupCommand.COMMENT)) {
            val edited = insertPrompterMarkup(TextFieldValue("Été 🎸", TextRange(6, 0)), command, "Texte")
            assertEquals("Été 🎸", preparePrompterText(edited.text).lines.first().plainText)
            assertEquals(if (command == PrompterMarkupCommand.BOLD) PrompterRichTextStyle.Bold
                else PrompterRichTextStyle.Italic, preparePrompterText(edited.text).lines.first().spans.single().style)
        }
    }

    @Test fun multilineCommentUsesSupportedLineLocalSyntax() {
        val edited = insertPrompterMarkup(TextFieldValue("un\r\ndeux", TextRange(0, 8)), PrompterMarkupCommand.COMMENT, "Texte")
        assertEquals("*un*\r\n*deux*\n", edited.text)
        assertEquals(listOf("un", "deux", ""), preparePrompterText(edited.text).lines.map { it.plainText })
    }

    @Test fun dividerPreservesSelectionAndCreatesSeparateLine() {
        val edited = insertPrompterMarkup(TextFieldValue("texte", TextRange(0, 5)), PrompterMarkupCommand.DIVIDER, "")
        assertEquals("---\ntexte", edited.text)
        assertEquals(TextRange(4), edited.selection)
        assertEquals(PrompterRichTextBlockKind.DIVIDER, preparePrompterText(edited.text).lines.first().blockKind)
    }

    @Test fun dividerBeforeSelectedLineKeepsItsOwnLine() {
        val edited = insertPrompterMarkup(TextFieldValue("texte\nfin", TextRange(0, 5)), PrompterMarkupCommand.DIVIDER, "")
        assertEquals("---\ntexte\nfin", edited.text)
    }

    @Test fun emptySelectionInsertsEditableColorPlaceholderAtCursor() {
        val edited = applyPrompterColor(TextFieldValue("ab", TextRange(1)), PrompterTextColor.RED, "Texte")
        assertEquals("a<c=red>Texte</c>b", edited.text)
        assertEquals("Texte", edited.text.substring(edited.selection.min, edited.selection.max))
    }

    @Test fun colorsPreserveHeadingsAndLineEndings() {
        val source = "# Titre\r\n## Refrain\n[Am]Été"
        val edited = applyPrompterColor(TextFieldValue(source, TextRange(source.length, 0)), PrompterTextColor.BLUE, "Texte")
        assertEquals("# <c=blue>Titre</c>\r\n## <c=blue>Refrain</c>\n<c=blue>[Am]Été</c>", edited.text)
        val lines = preparePrompterText(edited.text).lines
        assertEquals(PrompterRichTextBlockKind.TITLE, lines[0].blockKind)
        assertEquals(PrompterRichTextBlockKind.SECTION, lines[1].blockKind)
        assertEquals(PrompterTextColor.BLUE, lines[2].chords.single().color)
    }

    @Test fun recoloringInsideExistingRegionDoesNotNestTags() {
        val value = TextFieldValue("avant <c=red>mot</c> après", TextRange(14))
        assertEquals("avant <c=green>mot</c> après", applyPrompterColor(value, PrompterTextColor.GREEN, "Texte").text)
    }

    @Test fun everyColorReachesActualTextSpanAndChordRenderModel() {
        for (color in PrompterTextColor.entries) {
            val source = "[Am]Bonjour"
            val edited = applyPrompterColor(TextFieldValue(source, TextRange(0, source.length)), color, "Texte")
            val prepared = preparePrompterText(edited.text)
            val line = buildRichTextPrompterLines(prepared).single()
            val annotated = line.lyricText.withPrompterStyles(line.spans, Color.White)
            assertEquals(resolvePrompterTextColor(color, Color.White), annotated.spanStyles.single().item.color)
            assertEquals(listOf(color), line.words.first().runs.first().chordColors)
        }
    }

    @Test fun chordOnlyColorDoesNotLeaveLiteralTagsOrColorAdjacentChord() {
        val line = preparePrompterText("<c=red>[Am]</c>[G]fin").lines.single()
        assertEquals("fin", line.plainText)
        assertEquals(listOf(PrompterTextColor.RED, null), line.chords.map { it.color })
        assertEquals(listOf(0, 0), line.chords.map { it.plainTextOffset })
        assertTrue(line.hasFormatting)
    }

    @Test fun historicalEmptyTagsRemainLiteralEvenBesideColoredChord() {
        val line = preparePrompterText("<c=red></c> <c=blue>[Am]</c>").lines.single()
        assertEquals("<c=red></c> ", line.plainText)
    }

    @Test fun existingUncoloredChordProKeepsModeAnchorsAndText() {
        val source = "Je [Am]vais [G]bien"
        val doc = preparePrompterText(source)
        assertEquals(source, doc.source)
        assertEquals(PrompterRenderMode.CHORD_PRO, resolvePrompterRenderMode(doc))
        assertEquals("Je vais bien", doc.lines.single().plainText)
        assertEquals(listOf(3, 8), doc.lines.single().chords.map { it.plainTextOffset })
        assertTrue(doc.lines.single().chords.all { it.color == null })
    }
}
