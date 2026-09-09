package com.patrick.lrcreader.ui

import com.patrick.lrcreader.core.LrcLine
import com.patrick.lrcreader.core.PrompterRichTextStyle
import com.patrick.lrcreader.core.PrompterTextColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsAreaChordProIntegrationTest {

    @Test
    fun normalLyricsKeepTheSimpleRenderPath() {
        val source = LrcLine(timeMs = 1_000L, text = "Je pars ce soir")

        val item = prepareLyricsRenderItems(listOf(source), transposeSemitones = 0).single()

        assertSame(source, item.line)
        assertNull(item.chordProLine)
    }

    @Test
    fun oneChordUsesTheSharedRenderModel() {
        val item = renderItem("Je [Am]pars")

        assertEquals("Je pars", item.chordProLine?.visibleLyrics())
        assertEquals(listOf("Am"), item.chordProLine?.allChords())
    }

    @Test
    fun severalChordsKeepTheirOrder() {
        val item = renderItem("Je [Am]pars ce [F]soir")

        assertEquals("Je pars ce soir", item.chordProLine?.visibleLyrics())
        assertEquals(listOf("Am", "F"), item.chordProLine?.allChords())
    }

    @Test
    fun supportedTranspositionRangeRebuildsOnlyRenderedChords() {
        val source = LrcLine(timeMs = 4_200L, text = "Je [C]pars")
        val expectedChords = listOf(
            0 to "C",
            1 to "C#",
            -1 to "B",
            11 to "B",
            -11 to "C#"
        )

        expectedChords.forEach { (transposeSemitones, expectedChord) ->
            val item = prepareLyricsRenderItems(
                lines = listOf(source),
                transposeSemitones = transposeSemitones
            ).single()

            assertSame(source, item.line)
            assertEquals("Je [C]pars", item.line.text)
            assertEquals(4_200L, item.line.timeMs)
            assertEquals(listOf(expectedChord), item.chordProLine?.allChords())
            assertEquals("Je pars", item.chordProLine?.visibleLyrics())
        }
    }

    @Test
    fun transpositionDoesNotMoveNormalLyricsToChordProRendering() {
        val source = LrcLine(timeMs = 2_500L, text = "Paroles normales")

        val item = prepareLyricsRenderItems(
            lines = listOf(source),
            transposeSemitones = 11
        ).single()

        assertSame(source, item.line)
        assertNull(item.chordProLine)
    }

    @Test
    fun chordsAtStartMiddleAndEndRemainInOneRenderItem() {
        listOf(
            "[C]Début" to listOf("C"),
            "Mi[G]lieu" to listOf("G"),
            "Fin[Am]" to listOf("Am")
        ).forEach { (text, expectedChords) ->
            val item = renderItem(text)

            assertEquals(expectedChords, item.chordProLine?.allChords())
        }
    }

    @Test
    fun ordinaryBracketsKeepTheSimpleRenderPath() {
        val item = renderItem("[Refrain] Je pars")

        assertNull(item.chordProLine)
        assertEquals("[Refrain] Je pars", item.line.text)
    }

    @Test
    fun richColorWithoutChordUsesSharedRenderingAndHidesMarkup() {
        val item = renderItem("Je pars <c=red>ce soir</c>")
        val rendered = item.chordProLine!!

        assertEquals("Je pars ce soir", rendered.lyricText)
        assertFalse(rendered.hasChords)
        assertTrue(item.richTextEnabled)
        assertTrue(
            rendered.spans.any {
                it.style == PrompterRichTextStyle.ForegroundColor(PrompterTextColor.RED) &&
                    rendered.lyricText.substring(it.start, it.endExclusive) == "ce soir"
            }
        )
        assertFalse(rendered.lyricText.contains("<c="))
        assertFalse(rendered.lyricText.contains("</c>"))
    }

    @Test
    fun boldAndItalicWithoutChordUseSharedRenderingAndHideMarkers() {
        val bold = renderItem("Je pars **ce soir**")
        val italic = renderItem("Je *pars* ce soir")

        assertEquals("Je pars ce soir", bold.chordProLine?.lyricText)
        assertEquals("Je pars ce soir", italic.chordProLine?.lyricText)
        assertTrue(bold.chordProLine?.spans?.any { it.style == PrompterRichTextStyle.Bold } == true)
        assertTrue(italic.chordProLine?.spans?.any { it.style == PrompterRichTextStyle.Italic } == true)
        assertFalse(bold.chordProLine?.lyricText.orEmpty().contains('*'))
        assertFalse(italic.chordProLine?.lyricText.orEmpty().contains('*'))
    }

    @Test
    fun richColorCombinesWithBoldAndItalicWithoutVisibleTags() {
        val bold = renderItem("Je <c=blue>**pars**</c>")
        val italic = renderItem("Je <c=green>*pars*</c>")

        listOf(bold, italic).forEach { item ->
            assertEquals("Je pars", item.chordProLine?.lyricText)
            assertFalse(item.chordProLine?.lyricText.orEmpty().contains('<'))
            assertFalse(item.chordProLine?.lyricText.orEmpty().contains('*'))
            assertTrue(
                item.chordProLine?.spans?.any {
                    it.style is PrompterRichTextStyle.ForegroundColor
                } == true
            )
        }
        assertTrue(bold.chordProLine?.spans?.any { it.style == PrompterRichTextStyle.Bold } == true)
        assertTrue(italic.chordProLine?.spans?.any { it.style == PrompterRichTextStyle.Italic } == true)
    }

    @Test
    fun chordProCombinesWithRichColorAndBold() {
        val colored = renderItem("Je [Am]<c=orange>pars</c>")
        val bold = renderItem("Je [Am]**pars**")

        assertEquals(listOf("Am"), colored.chordProLine?.allChords())
        assertEquals("Je pars", colored.chordProLine?.visibleLyrics())
        assertTrue(
            colored.chordProLine?.spans?.any {
                it.style == PrompterRichTextStyle.ForegroundColor(PrompterTextColor.ORANGE)
            } == true
        )
        assertEquals(listOf("Am"), bold.chordProLine?.allChords())
        assertEquals("Je pars", bold.chordProLine?.visibleLyrics())
        assertTrue(bold.chordProLine?.spans?.any { it.style == PrompterRichTextStyle.Bold } == true)
    }

    @Test
    fun activeNextAndInactiveStatesRemainIndexBased() {
        assertEquals(
            LyricsLineActivity(isActive = true, isNext = false),
            lyricsLineActivity(index = 4, currentLrcIndex = 4, readabilityModeEnabled = false)
        )
        assertEquals(
            LyricsLineActivity(isActive = false, isNext = true),
            lyricsLineActivity(index = 5, currentLrcIndex = 4, readabilityModeEnabled = false)
        )
        assertEquals(
            LyricsLineActivity(isActive = false, isNext = false),
            lyricsLineActivity(index = 6, currentLrcIndex = 4, readabilityModeEnabled = false)
        )
        assertFalse(
            lyricsLineActivity(index = 5, currentLrcIndex = 4, readabilityModeEnabled = true).isNext
        )
    }

    @Test
    fun clickAndSeekDataStayAttachedToTheOriginalLrcLine() {
        val lines = listOf(
            LrcLine(timeMs = 1_000L, text = "Même ligne"),
            LrcLine(timeMs = 2_000L, text = "[Am]Même ligne"),
            LrcLine(
                timeMs = 3_000L,
                text = "<c=red>Même ligne</c>",
                colorArgb = 0xFF64B5F6.toInt()
            )
        )

        val items = prepareLyricsRenderItems(lines, transposeSemitones = 0)

        assertEquals(lines.size, items.size)
        items.forEachIndexed { index, item ->
            assertSame(lines[index], item.line)
            assertEquals(lines[index].timeMs, item.line.timeMs)
            assertEquals(lines[index].colorArgb, item.line.colorArgb)
        }
    }

    @Test
    fun mixedVisualHeightsKeepExactlyOneItemPerLrcLine() {
        val lines = listOf(
            LrcLine(timeMs = 1_000L, text = "Courte"),
            LrcLine(
                timeMs = 2_000L,
                text = "[Am]Une ligne ChordPro volontairement longue qui peut revenir à la ligne [F]plus loin"
            ),
            LrcLine(timeMs = 3_000L, text = "Fin[C]")
        )

        val items = prepareLyricsRenderItems(lines, transposeSemitones = 0)

        assertEquals(3, items.size)
        assertNull(items[0].chordProLine)
        assertTrue(items[1].chordProLine?.hasChords == true)
        assertTrue(items[2].chordProLine?.hasChords == true)
        assertEquals(lines.map { it.timeMs }, items.map { it.line.timeMs })
    }

    private fun renderItem(text: String): LyricsRenderItem =
        prepareLyricsRenderItems(
            lines = listOf(LrcLine(timeMs = 1_000L, text = text)),
            transposeSemitones = 0
        ).single()

    private fun PrompterChordRenderLine.visibleLyrics(): String =
        words.joinToString(separator = "") { it.lyricText }

    private fun PrompterChordRenderLine.allChords(): List<String> =
        words.flatMap { word -> word.runs.flatMap { it.chords } }
}
