package com.patrick.lrcreader.ui

import com.patrick.lrcreader.core.parseChordPro
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordProPrompterLayoutTest {

    @Test
    fun textWithoutChords_usesHistoricalFastPath() {
        val document = parseChordPro("Une ligne longue sans aucun accord")

        assertNull(buildChordProPrompterLines(document))
    }

    @Test
    fun chordDocument_keepsLyricsAndExposesChordAboveAnchoredWord() {
        val lines = render("Je [Am]voulais te [F]dire")

        assertEquals("Je voulais te dire", lines.single().lyricText)
        assertEquals(listOf("Am", "F"), lines.single().allChords())
        assertEquals("Je voulais te dire", lines.single().renderedLyrics())
    }

    @Test
    fun mixedDocument_keepsLineWithoutChordCompact() {
        val lines = render("[C]Avec accord\nSans accord")

        assertTrue(lines[0].hasChords)
        assertFalse(lines[1].hasChords)
        assertTrue(lines[1].words.isEmpty())
        assertEquals("Sans accord", lines[1].lyricText)
    }

    @Test
    fun adjacentChords_shareTheSameRunWithoutLoss() {
        val line = render("[C][G]Bonjour").single()

        assertEquals(listOf("C", "G"), line.words.single().runs.single().chords)
        assertEquals("Bonjour", line.renderedLyrics())
    }

    @Test
    fun chordInsideWord_keepsTheWholeWordInOneWrappingUnit() {
        val line = render("Bon[Am]jour Patrick").single()

        assertEquals("Bonjour ", line.words.first().lyricText)
        assertEquals(listOf("", "Am"), line.words.first().runs.map { it.chords.firstOrNull().orEmpty() })
        assertEquals("Bonjour Patrick", line.renderedLyrics())
    }

    @Test
    fun multipleChordsInOneLine_keepOrderAndAllText() {
        val line = render("[C]Un [G]deux [Am]trois").single()

        assertEquals(listOf("C", "G", "Am"), line.allChords())
        assertEquals("Un deux trois", line.renderedLyrics())
    }

    @Test
    fun chordAtEnd_createsCompactTerminalAnchor() {
        val line = render("Fin[C]").single()
        val terminalWord = line.words.last()

        assertEquals("Fin", line.renderedLyrics())
        assertEquals("", terminalWord.lyricText)
        assertEquals(listOf("C"), terminalWord.runs.single().chords)
    }

    @Test
    fun longLine_isSplitIntoWrappableWordsWithoutLosingCharacters() {
        val source = "[C]Voici une ligne volontairement longue avec plusieurs mots qui doit pouvoir revenir à la ligne [G]proprement"
        val line = render(source).single()

        assertTrue(line.words.size > 10)
        assertEquals(parseChordPro(source).lines.single().lyricText, line.renderedLyrics())
        assertEquals(listOf("C", "G"), line.allChords())
    }

    @Test
    fun everyRenderedLine_preservesAllVisibleTextIncludingEmptyLines() {
        val document = parseChordPro("\n[C]Bonjour\n\nTexte [Am]final\n")
        val lines = buildChordProPrompterLines(document)!!

        assertEquals(document.lines.map { it.lyricText }, lines.map { it.lyricText })
        assertEquals(
            document.lines.map { it.lyricText },
            lines.map { line -> if (line.hasChords) line.renderedLyrics() else line.lyricText }
        )
    }

    private fun render(source: String): List<PrompterChordRenderLine> =
        buildChordProPrompterLines(parseChordPro(source))!!

    private fun PrompterChordRenderLine.renderedLyrics(): String =
        words.joinToString(separator = "") { it.lyricText }

    private fun PrompterChordRenderLine.allChords(): List<String> =
        words.flatMap { word -> word.runs.flatMap { it.chords } }
}
