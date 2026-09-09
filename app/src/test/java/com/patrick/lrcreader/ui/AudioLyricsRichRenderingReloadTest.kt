package com.patrick.lrcreader.ui

import com.patrick.lrcreader.core.LrcLine
import com.patrick.lrcreader.core.PrompterRichTextStyle
import com.patrick.lrcreader.core.PrompterTextColor
import com.patrick.lrcreader.core.findActiveLrcIndex
import com.patrick.lrcreader.core.parseLrc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioLyricsRichRenderingReloadTest {

    @Test
    fun initialRichRenderingKeepsSourceAndHidesColorMarkup() {
        val source = LrcLine(1_000L, "Je pars <c=red>ce soir</c>")

        val rendered = prepareLyricsRenderItems(listOf(source), 0).single()

        assertEquals(source, rendered.line)
        assertEquals("Je pars ce soir", rendered.chordProLine?.lyricText)
        assertFalse(rendered.chordProLine?.lyricText.orEmpty().contains("<c="))
        assertTrue(rendered.hasColor(PrompterTextColor.RED, "ce soir"))
    }

    @Test
    fun reloadKeepsUntimedRichLineInsideMixedSynchronizedLyrics() {
        val reloaded = parseLrc(
            """
            [00:09.33] Dis-lui
            <c=red>REFRAIN</c>
            [00:11.58] Fais ça pour moi, dis-lui
            """.trimIndent()
        )

        val normalized = normalizeLyricsLinesForRuntime(reloaded)
        val rendered = prepareLyricsRenderItems(normalized, 0)

        assertEquals(3, normalized.size)
        assertEquals(0L, normalized[1].timeMs)
        assertEquals("REFRAIN", rendered[1].chordProLine?.lyricText)
        assertTrue(rendered[1].hasColor(PrompterTextColor.RED, "REFRAIN"))
        assertEquals(0, findActiveLrcIndex(normalized, 10_000L))
        assertEquals(2, findActiveLrcIndex(normalized, 12_000L))
    }

    @Test
    fun rebuildingParsedLinesAndRenderModelKeepsIdenticalRichOutput() {
        val firstParsed = parseLrc("[00:04.20] Je <c=blue>pars</c> ce soir")
        val rebuiltParsed = parseLrc("[00:04.20] Je <c=blue>pars</c> ce soir")

        val first = prepareLyricsRenderItems(normalizeLyricsLinesForRuntime(firstParsed), 0).single()
        val rebuilt = prepareLyricsRenderItems(normalizeLyricsLinesForRuntime(rebuiltParsed), 0).single()

        assertEquals(first.line, rebuilt.line)
        assertEquals(first.chordProLine, rebuilt.chordProLine)
        assertTrue(rebuilt.hasColor(PrompterTextColor.BLUE, "pars"))
    }

    @Test
    fun logicalReplayDoesNotAlterColorChordBoldOrItalic() {
        val source = listOf(
            LrcLine(1_000L, "Je [Am]<c=orange>pars</c>"),
            LrcLine(2_000L, "Je **pars**"),
            LrcLine(3_000L, "Je *pars*")
        )

        val firstPlayback = prepareLyricsRenderItems(source, 0)
        val replay = prepareLyricsRenderItems(source.map { it.copy() }, 0)

        assertEquals(firstPlayback.map { it.chordProLine }, replay.map { it.chordProLine })
        assertTrue(replay[0].hasColor(PrompterTextColor.ORANGE, "pars"))
        assertEquals(listOf("Am"), replay[0].chordProLine?.words?.flatMap { word ->
            word.runs.flatMap { it.chords }
        })
        assertTrue(replay[1].hasStyle(PrompterRichTextStyle.Bold, "pars"))
        assertTrue(replay[2].hasStyle(PrompterRichTextStyle.Italic, "pars"))
        replay.forEach { item ->
            assertFalse(item.chordProLine?.lyricText.orEmpty().contains('<'))
            assertFalse(item.chordProLine?.lyricText.orEmpty().contains('*'))
        }
    }

    @Test
    fun normalizationOrdersTimedSubsequenceWithoutMovingUntimedRichLines() {
        val rich = LrcLine(0L, "<c=green>REFRAIN</c>")
        val later = LrcLine(3_000L, "Plus tard")
        val earlier = LrcLine(1_000L, "Plus tôt")

        val normalized = normalizeLyricsLinesForRuntime(listOf(later, rich, earlier))

        assertEquals(listOf(earlier, rich, later), normalized)
        assertEquals(listOf(1_000L, 0L, 3_000L), normalized.map { it.timeMs })
    }

    @Test
    fun entirelyUntimedLyricsKeepExistingRuntimeBehavior() {
        assertTrue(
            normalizeLyricsLinesForRuntime(
                listOf(LrcLine(0L, "<c=red>Sans synchronisation</c>"))
            ).isEmpty()
        )
    }

    private fun LyricsRenderItem.hasColor(color: PrompterTextColor, text: String): Boolean =
        chordProLine?.spans?.any { span ->
            span.style == PrompterRichTextStyle.ForegroundColor(color) &&
                chordProLine.lyricText.substring(span.start, span.endExclusive) == text
        } == true

    private fun LyricsRenderItem.hasStyle(style: PrompterRichTextStyle, text: String): Boolean =
        chordProLine?.spans?.any { span ->
            span.style == style &&
                chordProLine.lyricText.substring(span.start, span.endExclusive) == text
        } == true
}
