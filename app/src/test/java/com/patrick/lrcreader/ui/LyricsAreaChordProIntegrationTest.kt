package com.patrick.lrcreader.ui

import com.patrick.lrcreader.core.LrcLine
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

        val item = prepareLyricsRenderItems(listOf(source)).single()

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
            LrcLine(timeMs = 3_000L, text = "Même ligne")
        )

        val items = prepareLyricsRenderItems(lines)

        assertEquals(lines.size, items.size)
        items.forEachIndexed { index, item ->
            assertSame(lines[index], item.line)
            assertEquals(lines[index].timeMs, item.line.timeMs)
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

        val items = prepareLyricsRenderItems(lines)

        assertEquals(3, items.size)
        assertNull(items[0].chordProLine)
        assertTrue(items[1].chordProLine?.hasChords == true)
        assertTrue(items[2].chordProLine?.hasChords == true)
        assertEquals(lines.map { it.timeMs }, items.map { it.line.timeMs })
    }

    private fun renderItem(text: String): LyricsRenderItem =
        prepareLyricsRenderItems(listOf(LrcLine(timeMs = 1_000L, text = text))).single()

    private fun PrompterChordRenderLine.visibleLyrics(): String =
        words.joinToString(separator = "") { it.lyricText }

    private fun PrompterChordRenderLine.allChords(): List<String> =
        words.flatMap { word -> word.runs.flatMap { it.chords } }
}
