package com.patrick.lrcreader.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.patrick.lrcreader.core.analyzeChordProImport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollingTextChordProImportTest {
    @Test fun textWithoutCandidateHasNoSuggestion() {
        assertNull(chordProImportSuggestion("Em C G D", ignoredSource = null))
    }

    @Test fun explicitChordHasSuggestionAndConvertsDraftOnly() {
        val value = TextFieldValue("Avant **Em** après", TextRange(8))
        val analysis = chordProImportSuggestion(value.text, ignoredSource = null)!!

        val converted = applyChordProImportSuggestion(value, analysis)!!

        assertEquals("Avant [Em] après", converted.text)
        assertEquals("Avant **Em** après", value.text)
    }

    @Test fun ignoredExactSourceIsNotSuggestedAgain() {
        val source = "**Em**"
        assertNull(chordProImportSuggestion(source, ignoredSource = source))
    }

    @Test fun aNewSessionOffersTheSameSourceAgain() {
        val source = "**Em**"
        val ignoredInFirstSession = source

        assertNull(chordProImportSuggestion(source, ignoredInFirstSession))
        assertEquals(1, chordProImportSuggestion(source, ignoredSource = null)!!.candidateCount)
    }

    @Test fun convertedTextIsNotImmediatelySuggestedAgain() {
        val source = "**Em**"
        val analysis = analyzeChordProImport(source)
        val converted = applyChordProImportSuggestion(TextFieldValue(source), analysis)!!

        assertEquals("[Em]", converted.text)
        assertNull(chordProImportSuggestion(converted.text, ignoredSource = source))
    }

    @Test fun libraryAndPlaylistStartFromTheSameCleanSuggestionState() {
        listOf("library", "playlist").forEach { route ->
            val suggestion = chordProImportSuggestion("**Em**", ignoredSource = null)

            assertEquals(route, 1, suggestion?.candidateCount)
        }
    }

    @Test fun editingAfterIgnoreAllowsANewSuggestion() {
        val ignored = "**Em**"
        val changed = "**Em** puis **G**"

        assertEquals(2, chordProImportSuggestion(changed, ignoredSource = ignored)!!.candidateCount)
    }

    @Test fun staleAnalysisIsNeverApplied() {
        val analysis = analyzeChordProImport("**Em**")
        val changed = TextFieldValue("X **Em**", TextRange(1))

        assertNull(applyChordProImportSuggestion(changed, analysis))
        assertEquals("X **Em**", changed.text)
    }

    @Test fun cursorBeforeReplacementKeepsItsPosition() {
        val value = TextFieldValue("Avant **Em** après", TextRange(3))

        val converted = applyChordProImportSuggestion(value, analyzeChordProImport(value.text))!!

        assertEquals(TextRange(3), converted.selection)
    }

    @Test fun cursorAfterReplacementAccountsForShorterMarkup() {
        val source = "Avant **Em** après"
        val value = TextFieldValue(source, TextRange(source.length))

        val converted = applyChordProImportSuggestion(value, analyzeChordProImport(source))!!

        assertEquals(TextRange(converted.text.length), converted.selection)
        assertEquals(source.length - 2, converted.selection.start)
    }

    @Test fun selectionCoveringReplacementIsRemapped() {
        val source = "Avant **Em** après"
        val value = TextFieldValue(source, TextRange(6, 12))

        val converted = applyChordProImportSuggestion(value, analyzeChordProImport(source))!!

        assertEquals(TextRange(6, 10), converted.selection)
    }

    @Test fun reversedSelectionKeepsItsDirection() {
        val source = "Avant **Em** après"
        val value = TextFieldValue(source, TextRange(source.length, 6))

        val converted = applyChordProImportSuggestion(value, analyzeChordProImport(source))!!

        assertEquals(TextRange(converted.text.length, 6), converted.selection)
        assertTrue(converted.selection.reversed)
    }

    @Test fun multipleReplacementsRemapBothSelectionOffsets() {
        val source = "x **Em** milieu **Am/F#** fin"
        val value = TextFieldValue(source, TextRange(2, source.length))

        val converted = applyChordProImportSuggestion(value, analyzeChordProImport(source))!!

        assertEquals("x [Em] milieu [Am/F#] fin", converted.text)
        assertEquals(TextRange(2, converted.text.length), converted.selection)
    }

    @Test fun compositionIsRemappedWithTheSameRules() {
        val source = "**Em** texte"
        val value = TextFieldValue(source, TextRange(source.length), TextRange(7, source.length))

        val converted = applyChordProImportSuggestion(value, analyzeChordProImport(source))!!

        assertEquals(TextRange(5, converted.text.length), converted.composition)
    }

    @Test fun chordLinesAreSuggestedOnlyWhenAPairIsSafe() {
        assertNull(
            scrollingTextChordProImportSuggestion(
                source = "A partir de quand ?\nparoles",
                ignoredExplicitChordSource = null,
                ignoredChordLineSource = null
            )
        )

        val partialSuggestion = scrollingTextChordProImportSuggestion(
            source = "C Suis D\nabcdefgh",
            ignoredExplicitChordSource = null,
            ignoredChordLineSource = null
        )
        assertTrue(partialSuggestion is ScrollingTextChordProImportSuggestion.ChordLines)

        val suggestion = scrollingTextChordProImportSuggestion(
            source = "C D\nabcd",
            ignoredExplicitChordSource = null,
            ignoredChordLineSource = null
        )

        assertTrue(suggestion is ScrollingTextChordProImportSuggestion.ChordLines)
        assertEquals(1, suggestion?.candidateCount)
    }

    @Test fun chordLineSuggestionConvertsOnlyTheDraft() {
        val source = "C D\nabcd"
        val value = TextFieldValue(source, TextRange(source.length))
        val suggestion = scrollingTextChordProImportSuggestion(source, null, null)!!

        val converted = applyScrollingTextChordProImportSuggestion(value, suggestion)!!

        assertEquals("[C]ab[D]cd", converted.text)
        assertEquals(converted.text.length, converted.selection.start)
        assertEquals(source, value.text)
    }

    @Test fun safeMixedChordLineDropsValidatedNoiseWhenApplied() {
        val source = "C   Suis   D\nabcdefghijkl"
        val suggestion = scrollingTextChordProImportSuggestion(source, null, null)!!

        val converted = applyScrollingTextChordProImportSuggestion(
            TextFieldValue(source),
            suggestion
        )!!

        assertEquals("[C]abcdefghijk[D]l", converted.text)
        assertEquals(source, TextFieldValue(source).text)
    }

    @Test fun chordBlockStillUsesTheExistingSuggestionAndApplyFlow() {
        val source = "C   Suis   D   Bm7\nBm7/E E7\nN'oublie pas : dis-lui..."
        val suggestion = scrollingTextChordProImportSuggestion(source, null, null)!!

        val converted = applyScrollingTextChordProImportSuggestion(
            TextFieldValue(source),
            suggestion
        )!!

        assertEquals(1, suggestion.candidateCount)
        assertEquals(
            "[C][Bm7/E]N'oubl[E7]ie pa[D]s : [Bm7]dis-lui...",
            converted.text
        )
    }

    @Test fun ignoredChordLineSourceIsUnchangedAndNotSuggestedAgain() {
        val source = "C D\nabcd"
        val value = TextFieldValue(source)

        assertNull(scrollingTextChordProImportSuggestion(source, null, source))
        assertEquals(source, value.text)
    }

    @Test fun editingAfterIgnoringChordLinesAllowsANewSuggestion() {
        val ignored = "C D\nabcd"
        val changed = "$ignored\n\nE F\nwxyz"

        val suggestion = scrollingTextChordProImportSuggestion(changed, null, ignored)

        assertTrue(suggestion is ScrollingTextChordProImportSuggestion.ChordLines)
        assertEquals(2, suggestion?.candidateCount)
    }

    @Test fun staleChordLineSuggestionIsNeverApplied() {
        val source = "C D\nabcd"
        val suggestion = scrollingTextChordProImportSuggestion(source, null, null)!!
        val changed = TextFieldValue("X\n$source", TextRange(1))

        assertNull(applyScrollingTextChordProImportSuggestion(changed, suggestion))
        assertEquals("X\n$source", changed.text)
    }

    @Test fun safeAndProtectedChordLinesConvertOnlyTheSafePair() {
        val source = "C D\nabcd\n\nC [Chœur] D\nparoles"
        val suggestion = scrollingTextChordProImportSuggestion(source, null, null)!!

        val converted = applyScrollingTextChordProImportSuggestion(
            TextFieldValue(source),
            suggestion
        )!!

        assertEquals("[C]ab[D]cd\n\nC [Chœur] D\nparoles", converted.text)
    }

    @Test fun explicitMarkupHasPriorityThenChordLinesRemainAvailable() {
        val source = "**Em**\nC D\nabcd"

        val first = scrollingTextChordProImportSuggestion(source, null, null)
        assertTrue(first is ScrollingTextChordProImportSuggestion.ExplicitChordMarkup)

        val afterIgnoringExplicit = scrollingTextChordProImportSuggestion(
            source = source,
            ignoredExplicitChordSource = source,
            ignoredChordLineSource = null
        )
        assertTrue(afterIgnoringExplicit is ScrollingTextChordProImportSuggestion.ChordLines)

        val convertedExplicit = applyScrollingTextChordProImportSuggestion(
            TextFieldValue(source),
            first!!
        )!!
        val afterConvertingExplicit = scrollingTextChordProImportSuggestion(
            source = convertedExplicit.text,
            ignoredExplicitChordSource = source,
            ignoredChordLineSource = null
        )
        assertTrue(afterConvertingExplicit is ScrollingTextChordProImportSuggestion.ChordLines)
    }

    @Test fun chordLineConversionRemapsSelectionAcrossTheReplacement() {
        val source = "préfixe\nC D\nabcd\nsuffixe"
        val value = TextFieldValue(source, TextRange(2, source.length))
        val suggestion = scrollingTextChordProImportSuggestion(source, null, null)!!

        val converted = applyScrollingTextChordProImportSuggestion(value, suggestion)!!

        assertEquals("préfixe\n[C]ab[D]cd\nsuffixe", converted.text)
        assertEquals(TextRange(2, converted.text.length), converted.selection)
    }
}
