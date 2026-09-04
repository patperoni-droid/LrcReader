package com.patrick.lrcreader.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.patrick.lrcreader.core.PrompterTextColor
import com.patrick.lrcreader.core.preparePrompterText
import org.junit.Assert.*
import org.junit.Test

class PrompterEditingErgonomicsTest {
    @Test fun defaultRemovesColorAndPreservesCaretInsideWord() {
        val result = removePrompterColor(TextFieldValue("a<c=red>hello</c>b", TextRange(10)))
        assertEquals("ahellob", result.text)
        assertEquals(TextRange(3), result.selection)
        assertFalse(preparePrompterText(result.text).hasFormatting)
    }
    @Test fun defaultRemovesSeveralColorsAndPreservesReversedSelection() {
        val source = "<c=red>un</c> <c=blue>deux</c>"
        val result = removePrompterColor(TextFieldValue(source, TextRange(source.length, 0)))
        assertEquals("un deux", result.text)
        assertEquals(TextRange(7, 0), result.selection)
    }
    @Test fun defaultOnUncoloredTextDoesNotInsertAnything() {
        val value = TextFieldValue("[Am]bonjour", TextRange(6))
        assertEquals(value, removePrompterColor(value))
    }
    @Test fun defaultPreservesOtherFormattingAndChords() {
        val source = "<c=white>**[Am]bonjour**</c>"
        val result = removePrompterColor(TextFieldValue(source, TextRange(15)))
        assertEquals("**[Am]bonjour**", result.text)
        assertEquals("Am", preparePrompterText(result.text).lines.single().chords.single().symbol.raw)
    }
    @Test fun recolorKeepsCaretAtSameVisiblePosition() {
        val value = TextFieldValue("<c=red>hello</c>", TextRange(9))
        val result = applyPrompterColor(value, PrompterTextColor.ORANGE, "Texte")
        assertEquals("<c=orange>hello</c>", result.text)
        assertEquals(TextRange(12), result.selection)
    }
    @Test fun recolorKeepsReversedSelection() {
        val result = applyPrompterColor(TextFieldValue("<c=red>hello</c>", TextRange(11, 8)), PrompterTextColor.BLUE, "Texte")
        assertEquals(TextRange(12, 9), result.selection)
    }
    @Test fun chordInsideCaretIsReplacedWithoutChangingLyrics() {
        val result = editOrInsertPrompterChord(TextFieldValue("Je [Am]chante", TextRange(5)), "F#m7")
        assertEquals("Je [F#m7]chante", result.text)
        assertEquals(TextRange(9), result.selection)
    }
    @Test fun selectedChordCanBeReplacedButSelectedLyricsArePreserved() {
        assertEquals("[G]mot", editOrInsertPrompterChord(TextFieldValue("[Am]mot", TextRange(4, 0)), "G").text)
        assertEquals("[Am][G]mot", editOrInsertPrompterChord(TextFieldValue("[Am]mot", TextRange(4, 7)), "G").text)
    }
    @Test fun emphasisCanBeAppliedThenRemovedWithoutLosingSelection() {
        for (command in listOf(PrompterMarkupCommand.BOLD, PrompterMarkupCommand.ITALIC)) {
            val original = TextFieldValue("bonjour", TextRange(0, 7))
            val formatted = insertPrompterMarkup(original, command, "Texte")
            val restored = insertPrompterMarkup(formatted, command, "Texte")
            assertEquals(original, restored)
        }
    }
    @Test fun emphasisRemovalPreservesCaretInExistingWord() {
        val result = insertPrompterMarkup(TextFieldValue("**bonjour**", TextRange(5)), PrompterMarkupCommand.BOLD, "Texte")
        assertEquals("bonjour", result.text)
        assertEquals(TextRange(3), result.selection)
    }
    @Test fun mixedAndPartialEmphasisRemainIntact() {
        for (value in listOf(TextFieldValue("**bonjour**", TextRange(4)),
            TextFieldValue("**bonjour** fin", TextRange(5, 14)), TextFieldValue("**bonjour**", TextRange(1, 5)))) {
            assertEquals(value, insertPrompterMarkup(value, PrompterMarkupCommand.ITALIC, "Texte"))
        }
    }
    @Test fun partialColorTagSelectionRemainsIntact() {
        val value = TextFieldValue("<c=red>bonjour</c>", TextRange(2, 10))
        assertEquals(value, applyPrompterColor(value, PrompterTextColor.BLUE, "Texte"))
    }
    @Test fun formattingCannotSplitAnExistingChordToken() {
        val value = TextFieldValue("[Am]bonjour", TextRange(2))
        assertEquals(value, applyPrompterColor(value, PrompterTextColor.BLUE, "Texte"))
        assertEquals(value, insertPrompterMarkup(value, PrompterMarkupCommand.BOLD, "Texte"))
    }
    @Test fun malformedNestedColorsRemainIntact() {
        val source = "<c=red>a<c=blue>b</c>c</c>"
        val value = TextFieldValue(source, TextRange(0, source.length))
        assertEquals(value, applyPrompterColor(value, PrompterTextColor.GREEN, "Texte"))
    }
}
