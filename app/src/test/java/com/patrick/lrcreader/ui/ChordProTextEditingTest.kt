package com.patrick.lrcreader.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class ChordProTextEditingTest {

    @Test
    fun insertsChordAtBeginning() {
        val result = insertChordProAtSelection(
            TextFieldValue("Bonjour", selection = TextRange(0)),
            "Am"
        )

        assertEquals("[Am]Bonjour", result.text)
        assertEquals(TextRange(4), result.selection)
    }

    @Test
    fun insertsChordInMiddleWithoutAddingSpaces() {
        val result = insertChordProAtSelection(
            TextFieldValue("Je voulais", selection = TextRange(3)),
            "Am"
        )

        assertEquals("Je [Am]voulais", result.text)
        assertEquals(TextRange(7), result.selection)
    }

    @Test
    fun insertsChordAtEnd() {
        val result = insertChordProAtSelection(
            TextFieldValue("Bonjour", selection = TextRange(7)),
            "C/E"
        )

        assertEquals("Bonjour[C/E]", result.text)
        assertEquals(TextRange(12), result.selection)
    }

    @Test
    fun insertsAtSelectionStartWithoutDeletingSelection() {
        val result = insertChordProAtSelection(
            TextFieldValue("Je voulais te dire", selection = TextRange(3, 10)),
            "F#m7"
        )

        assertEquals("Je [F#m7]voulais te dire", result.text)
        assertEquals(TextRange(9), result.selection)
    }

    @Test
    fun insertsAtStartOfReversedSelection() {
        val result = insertChordProAtSelection(
            TextFieldValue("Je voulais te dire", selection = TextRange(10, 3)),
            "Am"
        )

        assertEquals("Je [Am]voulais te dire", result.text)
        assertEquals(TextRange(7), result.selection)
    }

    @Test
    fun preservesUnicodeAndEmojiBeforeInsertion() {
        val source = "Été 🎸musical"
        val offset = "Été 🎸".length

        val result = insertChordProAtSelection(
            TextFieldValue(source, selection = TextRange(offset)),
            "Bb"
        )

        assertEquals("Été 🎸[Bb]musical", result.text)
        assertEquals(TextRange(offset + 4), result.selection)
    }

    @Test
    fun supportsSuccessiveInsertionsAtUpdatedCursor() {
        var value = TextFieldValue("Bonjour", selection = TextRange(0))

        value = insertChordProAtSelection(value, "Am")
        value = insertChordProAtSelection(value, "F")
        value = insertChordProAtSelection(value, "G")

        assertEquals("[Am][F][G]Bonjour", value.text)
        assertEquals(TextRange(10), value.selection)
    }

    @Test
    fun blankChordLeavesValueUnchanged() {
        val source = TextFieldValue("Bonjour", selection = TextRange(2, 5))

        assertEquals(source, insertChordProAtSelection(source, "   "))
    }

    @Test
    fun paletteInputAcceptsSpacesAndPreservesComplexChordOrder() {
        assertEquals(
            listOf("Am", "F#m7", "C/E", "G", "Bb"),
            parseTextPrompterChordPaletteInput(" Am F#m7 C/E; G\nAm, Bb ")
        )
    }
}
