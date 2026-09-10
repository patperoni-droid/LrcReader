package com.patrick.lrcreader.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioLyricsChordProToolbarTest {

    @Test
    fun timingControlKeepsPreviousToggleBehavior() {
        assertTrue(toggleAudioLyricsTiming(current = false, hasTimedLines = true))
        assertFalse(toggleAudioLyricsTiming(current = true, hasTimedLines = true))
        assertFalse(toggleAudioLyricsTiming(current = false, hasTimedLines = false))
    }

    @Test
    fun automaticChordPaletteIsAvailableWheneverLyricsAreEditable() {
        assertTrue(shouldShowAudioLyricsChordPalette(false, listOf("Am")))
        assertFalse(shouldShowAudioLyricsChordPalette(false, emptyList()))
        assertFalse(shouldShowAudioLyricsChordPalette(true, listOf("Am")))
    }

    @Test
    fun essentialPhoneControlsFitTargetWidthsOnOneRow() {
        assertEquals(26, audioLyricsToolbarRemainingWidthDp(360))
        assertEquals(11, audioLyricsToolbarRemainingWidthDp(393))
        assertEquals(30, audioLyricsToolbarRemainingWidthDp(412))

        assertTrue(audioLyricsToolbarLayout(360).compactFormatting)
        assertFalse(audioLyricsToolbarLayout(393).compactFormatting)
        assertFalse(audioLyricsToolbarLayout(412).compactFormatting)
        listOf(360, 393, 412).forEach { width ->
            assertTrue(audioLyricsToolbarLayout(width).paletteDirectlyVisible)
            assertEquals(1, audioLyricsToolbarLayout(width).rowCount)
        }
    }

    @Test
    fun timingIconAvailabilityStillDependsOnTimedLyrics() {
        assertTrue(audioLyricsChordProToolbarVisibility(true).showTimingControl)
        assertFalse(audioLyricsChordProToolbarVisibility(false).showTimingControl)
    }

    @Test
    fun paletteUsesOnlyRecognizedChordProTags() {
        assertEquals(
            listOf("Am", "F", "G7"),
            extractPrompterChordPaletteFromText(
                "[Couplet] Je [Am]pars [F]ce soir\n[G7]Encore [Am]"
            )
        )
    }

    @Test
    fun paletteKeepsFirstAppearanceOrderAndUpdatesFromCurrentText() {
        val initial = extractPrompterChordPaletteFromText("[Am] [G] [Am] [F] [G]")
        val updated = extractPrompterChordPaletteFromText("[Am] [G] [Am] [F] [G] [Dm]")

        assertEquals(listOf("Am", "G", "F"), initial)
        assertEquals(listOf("Am", "G", "F", "Dm"), updated)
    }

    @Test
    fun ordinaryBracketsAreNotOfferedAsChords() {
        assertTrue(extractPrompterChordPaletteFromText("[Couplet] texte").isEmpty())
        assertNull(selectedPrompterChord(TextFieldValue("[Couplet]", TextRange(3))))
    }

    @Test
    fun boldAndItalicReusePrompterEditingAndPreserveSelection() {
        val source = TextFieldValue("Je pars ce soir", TextRange(3, 7))
        val bold = insertPrompterMarkup(source, PrompterMarkupCommand.BOLD, "Gras")
        val italic = insertPrompterMarkup(source, PrompterMarkupCommand.ITALIC, "Italique")

        assertEquals("Je **pars** ce soir", bold.text)
        assertEquals(TextRange(3, 11), bold.selection)
        assertEquals("Je *pars* ce soir", italic.text)
        assertEquals(TextRange(3, 9), italic.selection)
    }

    @Test
    fun chordActionInsertsAndThenEditsRecognizedChord() {
        val inserted = editOrInsertPrompterChord(
            TextFieldValue("Je pars", TextRange(3)),
            "Am"
        )
        val edited = editOrInsertPrompterChord(
            inserted.copy(selection = TextRange(5)),
            "G"
        )

        assertEquals("Je [Am]pars", inserted.text)
        assertEquals("Je [G]pars", edited.text)
        assertTrue(isValidPrompterChordInput("C/E"))
        assertFalse(isValidPrompterChordInput("Couplet"))
    }

    @Test
    fun paletteChordFillsExistingEmptyBrackets() {
        val result = editOrInsertPrompterChord(
            TextFieldValue("Je [] pars", TextRange(4)),
            "Am"
        )

        assertEquals("Je [Am] pars", result.text)
        assertEquals(TextRange(7), result.selection)
    }

    @Test
    fun transpositionStepsAcrossZeroAndStopsAtBounds() {
        assertEquals(1, stepPrompterTransposition(0, 1))
        assertEquals(-1, stepPrompterTransposition(0, -1))
        assertEquals(11, stepPrompterTransposition(11, 1))
        assertEquals(-11, stepPrompterTransposition(-11, -1))
    }
}
