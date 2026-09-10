package com.patrick.lrcreader.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.patrick.lrcreader.core.PrompterTextColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioLyricsRichColorEditingTest {

    @Test
    fun colorWrapsOnlySelectedWordOrPartialSelection() {
        val word = applyPrompterColor(
            TextFieldValue("Je pars ce soir", TextRange(11, 15)),
            PrompterTextColor.RED,
            ""
        )
        val partial = applyPrompterColor(
            TextFieldValue("Bonsoir", TextRange(3, 7)),
            PrompterTextColor.BLUE,
            ""
        )

        assertEquals("Je pars ce <c=red>soir</c>", word.text)
        assertEquals("Bon<c=blue>soir</c>", partial.text)
    }

    @Test
    fun fullLineSelectionCanBeColored() {
        val source = "Je pars ce soir"
        val result = applyPrompterColor(
            TextFieldValue(source, TextRange(0, source.length)),
            PrompterTextColor.GREEN,
            ""
        )

        assertEquals("<c=green>Je pars ce soir</c>", result.text)
    }

    @Test
    fun existingColorCanBeChangedThenRemovedWithDefault() {
        val source = "Je <c=red>pars</c> ce soir"
        val recolored = applyPrompterColor(
            TextFieldValue(source, TextRange(12)),
            PrompterTextColor.BLUE,
            ""
        )
        val restored = removePrompterColor(recolored.copy(selection = TextRange(13)))

        assertEquals("Je <c=blue>pars</c> ce soir", recolored.text)
        assertEquals("Je pars ce soir", restored.text)
    }

    @Test
    fun defaultRemovesOnlyTheSelectedColorRegion() {
        val source = "<c=red>un</c> et <c=green>deux</c>"
        val result = removePrompterColor(
            TextFieldValue(source, TextRange(8))
        )

        assertEquals("un et <c=green>deux</c>", result.text)
    }

    @Test
    fun colorCoexistsWithBoldItalicAndChordPro() {
        val bold = applyPrompterColor(
            TextFieldValue("**soir**", TextRange(2, 6)),
            PrompterTextColor.YELLOW,
            ""
        )
        val italic = applyPrompterColor(
            TextFieldValue("*soir*", TextRange(1, 5)),
            PrompterTextColor.ORANGE,
            ""
        )
        val chord = applyPrompterColor(
            TextFieldValue("[Am]pars", TextRange(4, 8)),
            PrompterTextColor.GREEN,
            ""
        )

        assertEquals("**<c=yellow>soir</c>**", bold.text)
        assertEquals("*<c=orange>soir</c>*", italic.text)
        assertEquals("[Am]<c=green>pars</c>", chord.text)
    }

    @Test
    fun paletteRequiresSelectionUnlessCaretIsInsideExistingColor() {
        assertFalse(canEditPrompterColor(TextFieldValue("texte", TextRange(2))))
        assertTrue(canEditPrompterColor(TextFieldValue("texte", TextRange(0, 5))))
        assertTrue(
            canEditPrompterColor(
                TextFieldValue("<c=red>texte</c>", TextRange(10))
            )
        )
    }

}
