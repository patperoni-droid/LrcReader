package com.patrick.lrcreader.ui

import com.patrick.lrcreader.core.PrompterRichTextStyle
import com.patrick.lrcreader.core.preparePrompterText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SinglePrompterRenderLineTest {

    @Test
    fun textWithoutChordRemainsVisibleAndUsesNoChordRuns() {
        val line = renderLine("Une ligne sans accord")

        assertEquals("Une ligne sans accord", line.lyricText)
        assertFalse(line.hasChords)
        assertTrue(line.words.isEmpty())
    }

    @Test
    fun oneChordIsAnchoredWithoutChangingVisibleLyrics() {
        val line = renderLine("Je [Am]pars")

        assertEquals("Je pars", line.renderedLyrics())
        assertEquals(listOf(3 to listOf("Am")), line.chordsWithOffsets())
    }

    @Test
    fun severalChordsKeepTheirOrderAndPositions() {
        val line = renderLine("Je [Am]pars ce [F]soir")

        assertEquals("Je pars ce soir", line.renderedLyrics())
        assertEquals(
            listOf(3 to listOf("Am"), 11 to listOf("F")),
            line.chordsWithOffsets()
        )
    }

    @Test
    fun transpositionChangesRenderedChordsOnly() {
        val source = "Je [Am]pars ce [F]soir"
        val line = renderLine(source, transposeSemitones = 2)

        assertEquals("Je pars ce soir", line.renderedLyrics())
        assertEquals(listOf("Bm", "G"), line.allChords())
        assertEquals(source, preparePrompterText(source).source)
    }

    @Test
    fun ordinaryBracketsRemainLiteralText() {
        val line = renderLine("[Refrain] Je pars")

        assertEquals("[Refrain] Je pars", line.lyricText)
        assertFalse(line.hasChords)
        assertTrue(line.words.isEmpty())
    }

    @Test
    fun chordsAtStartMiddleAndEndKeepExactVisibleOffsets() {
        val line = renderLine("[C]Début mi[G]lieu fin[Am]")

        assertEquals("Début milieu fin", line.renderedLyrics())
        assertEquals(
            listOf(
                0 to listOf("C"),
                8 to listOf("G"),
                16 to listOf("Am")
            ),
            line.chordsWithOffsets()
        )
    }

    @Test
    fun richFormattingIsPreservedInTheSharedLineModel() {
        val line = renderLine("**Je [Am]pars**")

        assertEquals("Je pars", line.renderedLyrics())
        assertEquals(listOf("Am"), line.allChords())
        assertTrue(
            line.words.flatMap { it.runs }.all { run ->
                run.spans.singleOrNull()?.style == PrompterRichTextStyle.Bold
            }
        )
    }

    private fun renderLine(
        source: String,
        transposeSemitones: Int = 0
    ): PrompterChordRenderLine = buildSinglePrompterRenderLine(
        line = preparePrompterText(source).lines.single(),
        transposeSemitones = transposeSemitones
    )

    private fun PrompterChordRenderLine.renderedLyrics(): String =
        if (hasChords) words.joinToString(separator = "") { it.lyricText } else lyricText

    private fun PrompterChordRenderLine.allChords(): List<String> =
        words.flatMap { word -> word.runs.flatMap { it.chords } }

    private fun PrompterChordRenderLine.chordsWithOffsets(): List<Pair<Int, List<String>>> {
        var offset = 0
        return buildList {
            words.forEach { word ->
                word.runs.forEach { run ->
                    if (run.chords.isNotEmpty()) add(offset to run.chords)
                    offset += run.lyricText.length
                }
            }
        }
    }
}
