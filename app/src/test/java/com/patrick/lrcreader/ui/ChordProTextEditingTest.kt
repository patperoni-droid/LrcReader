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
    fun automaticPaletteKeepsUniqueChordsInFirstAppearanceOrder() {
        assertEquals(
            listOf("Am", "G", "F"),
            extractPrompterChordPaletteFromText("[Am] texte [G]\n[Am] autre ligne\n[F] puis [G]")
        )
    }

    @Test
    fun automaticPaletteUpdatesFromCurrentSourceTextWithoutTransposition() {
        val initial = extractPrompterChordPaletteFromText("[Am] [G] [Am]")
        val updated = extractPrompterChordPaletteFromText("[Am] [G] [Am] [Dm]")

        assertEquals(listOf("Am", "G"), initial)
        assertEquals(listOf("Am", "G", "Dm"), updated)
    }

    @Test
    fun exactChordReplacementChangesEveryMatchingAnchorOnly() {
        val result = replaceExactPrompterChordOccurrences(
            value = TextFieldValue(
                "[G] [Gm] [G7] [Gmaj7] [G/B] [G#] [G]",
                selection = TextRange(43)
            ),
            oldChord = "G",
            newChord = "G7"
        )

        assertEquals("[G7] [Gm] [G7] [Gmaj7] [G/B] [G#] [G7]", result.text)
    }

    @Test
    fun exactChordReplacementSupportsMinorAlteredAndSlashChords() {
        val source = TextFieldValue("[Am] [Am7] [C#] [C#/G#] [C] [Cmaj7]")

        assertEquals(
            "[Am7] [Am7] [C#] [C#/G#] [C] [Cmaj7]",
            replaceExactPrompterChordOccurrences(source, "Am", "Am7").text
        )
        assertEquals(
            "[Am] [Am7] [Db] [C#/G#] [C] [Cmaj7]",
            replaceExactPrompterChordOccurrences(source, "C#", "Db").text
        )
        assertEquals(
            "[Am] [Am7] [C#] [C#/A] [C] [Cmaj7]",
            replaceExactPrompterChordOccurrences(source, "C#/G#", "C#/A").text
        )
        assertEquals(
            "[Am] [Am7] [C#] [C#/G#] [Cmaj7] [Cmaj7]",
            replaceExactPrompterChordOccurrences(source, "C", "Cmaj7").text
        )
    }

    @Test
    fun targetAlreadyInPaletteRemainsUniqueAfterReplacement() {
        val result = replaceExactPrompterChordOccurrences(
            TextFieldValue("[G] texte [G7] puis [G]"),
            oldChord = "G",
            newChord = "G7"
        )

        assertEquals(listOf("G7"), extractPrompterChordPaletteFromText(result.text))
    }

    @Test
    fun replacesEveryOccurrenceWhenChordIsUsedManyTimes() {
        val source = List(30) { "[G]ligne $it" }.joinToString("\n")
        val result = replaceExactPrompterChordOccurrences(
            TextFieldValue(source),
            oldChord = "G",
            newChord = "G7"
        )

        assertEquals(30, Regex("\\[G7]").findAll(result.text).count())
        assertEquals(listOf("G7"), extractPrompterChordPaletteFromText(result.text))
    }

    @Test
    fun sameOrInvalidReplacementLeavesTextUnchanged() {
        val source = TextFieldValue("[G] texte", TextRange(3))

        assertEquals(source, replaceExactPrompterChordOccurrences(source, "G", "G"))
        assertEquals(source, replaceExactPrompterChordOccurrences(source, "G", "invalid chord"))
    }

    @Test
    fun emptyChordButtonInsertsBracketsAndPlacesCaretInside() {
        val result = insertEmptyChordProAtSelection(
            TextFieldValue("Je pars ce soir", selection = TextRange(8))
        )

        assertEquals("Je pars []ce soir", result.text)
        assertEquals(TextRange(9), result.selection)
    }

    @Test
    fun emptyChordButtonPreservesSelectedLyrics() {
        val result = insertEmptyChordProAtSelection(
            TextFieldValue("Je pars ce soir", selection = TextRange(10, 3))
        )

        assertEquals("Je []pars ce soir", result.text)
        assertEquals(TextRange(4), result.selection)
    }
}
