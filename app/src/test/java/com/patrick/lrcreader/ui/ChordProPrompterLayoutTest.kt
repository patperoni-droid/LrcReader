package com.patrick.lrcreader.ui

import com.patrick.lrcreader.core.parseChordPro
import com.patrick.lrcreader.core.preparePrompterText
import com.patrick.lrcreader.core.PrompterRichTextBlockKind
import com.patrick.lrcreader.core.PrompterRichTextStyle
import com.patrick.lrcreader.core.PrompterTextColor
import com.patrick.lrcreader.core.TextPrompterAlignment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChordProPrompterLayoutTest {

    @Test
    fun startAlignmentUsesHistoricalTextAndFlowArrangements() {
        assertEquals(TextAlign.Start, textAlignForPrompter(TextPrompterAlignment.START))
        assertEquals(Arrangement.Start, horizontalArrangementForPrompter(TextPrompterAlignment.START))
    }

    @Test
    fun centerAlignmentCentersPlainAndWholeChordLines() {
        assertEquals(TextAlign.Center, textAlignForPrompter(TextPrompterAlignment.CENTER))
        assertEquals(Arrangement.Center, horizontalArrangementForPrompter(TextPrompterAlignment.CENTER))
    }

    @Test
    fun centeredRichTitleAndSectionKeepChordWordUnitsAtomic() {
        val lines = renderRich("# [C]Ma chanson\n## [G]Refrain")

        assertEquals(Arrangement.Center, horizontalArrangementForPrompter(TextPrompterAlignment.CENTER))
        assertEquals(PrompterRichTextBlockKind.TITLE, lines[0].blockKind)
        assertEquals(PrompterRichTextBlockKind.SECTION, lines[1].blockKind)
        assertEquals(listOf("Ma ", "chanson"), lines[0].words.map { it.lyricText })
        assertEquals(listOf("Refrain"), lines[1].words.map { it.lyricText })
        assertEquals(listOf("C"), lines[0].allChords())
        assertEquals(listOf("G"), lines[1].allChords())
    }

    @Test
    fun dividerRenderModelRemainsIndependentFromAlignment() {
        val divider = renderRich("---").single()

        assertEquals(PrompterRichTextBlockKind.DIVIDER, divider.blockKind)
        assertEquals("", divider.lyricText)
        assertFalse(divider.hasChords)
    }

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
    fun zeroTranspositionKeepsExactChordSpellingAndSource() {
        val source = "[Dbmaj7]Bonjour [F#/A#]toi"
        val document = parseChordPro(source)
        val lines = buildChordProPrompterLines(document, transposeSemitones = 0)!!

        assertEquals(source, document.source)
        assertEquals(listOf("Dbmaj7", "F#/A#"), lines.single().allChords())
        assertEquals("Bonjour toi", lines.single().renderedLyrics())
    }

    @Test
    fun positiveTranspositionChangesOnlyRenderedChords() {
        val source = "[C]Je chante avec [G]toi [Am]ce soir [F]"
        val document = parseChordPro(source)
        val lines = buildChordProPrompterLines(document, transposeSemitones = 2)!!

        assertEquals(source, document.source)
        assertEquals(listOf("D", "A", "Bm", "G"), lines.single().allChords())
        assertEquals("Je chante avec toi ce soir ", lines.single().renderedLyrics())
    }

    @Test
    fun negativeTranspositionAndSlashChordUseParsedSymbols() {
        val source = "[D/F#]Début [C/E]fin [Refrain]"
        val document = parseChordPro(source)
        val lines = buildChordProPrompterLines(document, transposeSemitones = -2)!!

        assertEquals(source, document.source)
        assertEquals(listOf("C/E", "A#/D"), lines.single().allChords())
        assertEquals("Début fin [Refrain]", lines.single().renderedLyrics())
    }

    @Test
    fun richTextTranspositionKeepsLyricsAndChordColor() {
        val source = "Je <c=yellow>[C/E]chante</c>"
        val document = preparePrompterText(source)
        val line = buildRichTextPrompterLines(document, transposeSemitones = 2).single()
        val chordRun = line.words[1].runs.single()

        assertEquals(source, document.source)
        assertEquals("Je chante", line.renderedLyrics())
        assertEquals(listOf("D/F#"), chordRun.chords)
        assertEquals(listOf(PrompterTextColor.YELLOW), chordRun.chordColors)
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
        val source = "[C][G][Fm]Bonjour"
        val document = parseChordPro(source)
        val line = buildChordProPrompterLines(document)!!.single()

        assertEquals(source, document.source)
        assertEquals(listOf("C", "G", "Fm"), line.words.single().runs.single().chords)
        assertEquals("Bonjour", line.renderedLyrics())
    }

    @Test
    fun adjacentLongChords_remainDistinctAfterTransposition() {
        val source = "[Cmaj7][F#sus4][Am/F#]Bonjour"
        val document = parseChordPro(source)
        val line = buildChordProPrompterLines(document, transposeSemitones = 2)!!.single()

        assertEquals(source, document.source)
        assertEquals(
            listOf("Dmaj7", "G#sus4", "Bm/G#"),
            line.words.single().runs.single().chords
        )
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
    fun chordsOnlyLine_keepsEveryChordForReading() {
        val line = render("[Am] [F] [G]").single()

        assertEquals("  ", line.renderedLyrics())
        assertEquals(listOf("Am", "F", "G"), line.allChords())
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

    @Test
    fun renderMode_preservesBothHistoricalFastPaths() {
        assertEquals(
            PrompterRenderMode.PLAIN_TEXT,
            resolvePrompterRenderMode(preparePrompterText("Texte historique"))
        )
        assertEquals(
            PrompterRenderMode.CHORD_PRO,
            resolvePrompterRenderMode(preparePrompterText("Je [Am]vais"))
        )
        assertEquals(
            PrompterRenderMode.RICH_TEXT,
            resolvePrompterRenderMode(preparePrompterText("**Texte**"))
        )
    }

    @Test
    fun boldAroundChord_isSplitIntoLocalRunStylesWithoutLosingSpaces() {
        val line = renderRich("**Je [Am]voulais**").single()

        assertEquals("Je voulais", line.renderedLyrics())
        assertEquals(listOf("Je ", "voulais"), line.words.map { it.lyricText })
        assertEquals(listOf("Am"), line.allChords())
        assertEquals(
            listOf(PrompterRichTextStyle.Bold),
            line.words[0].runs.single().spans.map { it.style }
        )
        assertEquals(0, line.words[0].runs.single().spans.single().start)
        assertEquals(3, line.words[0].runs.single().spans.single().endExclusive)
        assertEquals(0, line.words[1].runs.single().spans.single().start)
        assertEquals(7, line.words[1].runs.single().spans.single().endExclusive)
    }

    @Test
    fun chordBeforeBoldWord_keepsOnlyThatWordBold() {
        val line = renderRich("Je [Am]**vais** bien").single()
        val chordRun = line.words[1].runs.single()

        assertEquals("Je vais bien", line.renderedLyrics())
        assertEquals(listOf("Am"), chordRun.chords)
        assertEquals("vais ", chordRun.lyricText)
        assertEquals(0, chordRun.spans.single().start)
        assertEquals(4, chordRun.spans.single().endExclusive)
        assertEquals(PrompterRichTextStyle.Bold, chordRun.spans.single().style)
        assertTrue(line.words[2].runs.single().spans.isEmpty())
    }

    @Test
    fun italicAroundChord_keepsChordAndAllStyledWords() {
        val line = renderRich("*Je [F]vais bien*").single()

        assertEquals("Je vais bien", line.renderedLyrics())
        assertEquals(listOf("F"), line.allChords())
        assertTrue(
            line.words.flatMap { it.runs }.all { run ->
                run.spans.singleOrNull()?.style == PrompterRichTextStyle.Italic
            }
        )
    }

    @Test
    fun separateBoldAndItalicSpans_remainAttachedToTheirRuns() {
        val line = renderRich("**Bonjour** et *bonsoir*").single()

        assertEquals("Bonjour et bonsoir", line.lyricText)
        assertEquals(PrompterRichTextStyle.Bold, line.spans[0].style)
        assertEquals(PrompterRichTextStyle.Italic, line.spans[1].style)
    }

    @Test
    fun titleAndSection_keepBlockKindsAndRemappedChords() {
        val lines = renderRich("# [C]Ma chanson\n## [G]Refrain")

        assertEquals(PrompterRichTextBlockKind.TITLE, lines[0].blockKind)
        assertEquals("Ma chanson", lines[0].renderedLyrics())
        assertEquals(listOf("C"), lines[0].allChords())
        assertEquals(PrompterRichTextBlockKind.SECTION, lines[1].blockKind)
        assertEquals("Refrain", lines[1].renderedLyrics())
        assertEquals(listOf("G"), lines[1].allChords())
    }

    @Test
    fun divider_isRepresentedWithoutLiteralDashes() {
        val line = renderRich("---").single()

        assertEquals(PrompterRichTextBlockKind.DIVIDER, line.blockKind)
        assertEquals("", line.lyricText)
        assertFalse(line.hasChords)
    }

    @Test
    fun chordBeforeDivider_isSafeAndRemainsInPreparedRenderModel() {
        val line = renderRich("[Am]---").single()

        assertEquals(PrompterRichTextBlockKind.DIVIDER, line.blockKind)
        assertEquals(listOf("Am"), line.allChords())
        assertEquals("", line.renderedLyrics())
    }

    @Test
    fun invalidMarkdownAndNonChordBrackets_remainLiteral() {
        val lines = renderRich("# Titre\n**Je [Am]voulais\n[Refrain]")

        assertEquals("**Je voulais", lines[1].renderedLyrics())
        assertEquals(listOf("Am"), lines[1].allChords())
        assertEquals("[Refrain]", lines[2].lyricText)
        assertFalse(lines[2].hasChords)
    }

    @Test
    fun longStyledChordLine_staysSplitIntoAtomicWords() {
        val source = "**[C]Voici une ligne volontairement longue avec plusieurs mots qui doit revenir [G]proprement**"
        val line = renderRich(source).single()

        assertTrue(line.words.size > 10)
        assertEquals("Voici une ligne volontairement longue avec plusieurs mots qui doit revenir proprement", line.renderedLyrics())
        assertEquals(listOf("C", "G"), line.allChords())
    }

    @Test
    fun recognizedColor_activatesRichTextModeAndReachesLocalChordRun() {
        val document = preparePrompterText("Je <c=yellow>[Am]voulais</c> bien")
        val line = buildRichTextPrompterLines(document).single()
        val chordRun = line.words[1].runs.single()

        assertEquals(PrompterRenderMode.RICH_TEXT, resolvePrompterRenderMode(document))
        assertEquals("voulais ", chordRun.lyricText)
        assertEquals(listOf("Am"), chordRun.chords)
        assertEquals(
            PrompterRichTextStyle.ForegroundColor(PrompterTextColor.YELLOW),
            chordRun.spans.single().style
        )
        assertEquals(0, chordRun.spans.single().start)
        assertEquals(7, chordRun.spans.single().endExclusive)
    }

    @Test
    fun stageColorsResolveToExpectedValuesAndWhiteUsesActiveTextColor() {
        assertEquals(Color(0xFFFFD54F), resolvePrompterTextColor(PrompterTextColor.YELLOW, Color.Black))
        assertEquals(Color(0xFFFFB74D), resolvePrompterTextColor(PrompterTextColor.ORANGE, Color.Black))
        assertEquals(Color(0xFFFF6B6B), resolvePrompterTextColor(PrompterTextColor.RED, Color.Black))
        assertEquals(Color(0xFF64B5F6), resolvePrompterTextColor(PrompterTextColor.BLUE, Color.Black))
        assertEquals(Color(0xFF81C784), resolvePrompterTextColor(PrompterTextColor.GREEN, Color.Black))

        val activeTextColor = Color(0xFFE0E0E0)
        assertEquals(
            activeTextColor,
            resolvePrompterTextColor(PrompterTextColor.WHITE, activeTextColor)
        )
    }

    private fun render(source: String): List<PrompterChordRenderLine> =
        buildChordProPrompterLines(parseChordPro(source))!!

    private fun renderRich(source: String): List<PrompterChordRenderLine> =
        buildRichTextPrompterLines(preparePrompterText(source))

    private fun PrompterChordRenderLine.renderedLyrics(): String =
        words.joinToString(separator = "") { it.lyricText }

    private fun PrompterChordRenderLine.allChords(): List<String> =
        words.flatMap { word -> word.runs.flatMap { it.chords } }
}
