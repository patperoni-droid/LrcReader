package com.patrick.lrcreader.core

data class PrompterPreparedDocument(
    val source: String,
    val lines: List<PrompterPreparedLine>,
    val hasChords: Boolean,
    val hasFormatting: Boolean
)

data class PrompterPreparedLine(
    val chordProLine: ChordProLine,
    val richTextLine: PrompterRichTextLine,
    val chords: List<PrompterPreparedChord>
) {
    val intermediateText: String
        get() = chordProLine.lyricText

    val plainText: String
        get() = richTextLine.plainText

    val blockKind: PrompterRichTextBlockKind
        get() = richTextLine.blockKind

    val spans: List<PrompterRichTextSpan>
        get() = richTextLine.spans

    val hasChords: Boolean
        get() = chords.isNotEmpty()

    val hasFormatting: Boolean
        get() = blockKind != PrompterRichTextBlockKind.BODY || spans.isNotEmpty() || chords.any { it.color != null }
}

data class PrompterPreparedChord(
    val anchor: ChordAnchor,
    val plainTextOffset: Int,
    val color: PrompterTextColor? = null
) {
    val symbol: ChordSymbol
        get() = anchor.symbol
}

fun preparePrompterText(source: String): PrompterPreparedDocument {
    val chordProDocument = parseChordPro(source)
    // Keep source coordinates for chords, which are removed before lyric formatting.
    val sourceColorLines = if ("<c=" in source) parsePrompterRichText(source).lines else null
    var sourceOffset = 0
    val lines = chordProDocument.lines.mapIndexed { index, chordProLine ->
        val sourceColorLine = sourceColorLines?.get(index)
        val chordColors = chordProLine.anchors.map { anchor ->
            sourceColorLine?.let { line ->
                val offset = line.plainOffsetForInputOffset(anchor.sourceRange.first - sourceOffset)
                line.spans.firstOrNull {
                    it.style is PrompterRichTextStyle.ForegroundColor &&
                        offset >= it.start && offset < it.endExclusive
                }?.let { (it.style as PrompterRichTextStyle.ForegroundColor).color }
            }
        }
        // Only hide empty tags produced by removing a coloured chord, never literal empty tags.
        val emptyColorOffsets = chordProLine.anchors.mapIndexedNotNull { chordIndex, anchor ->
            chordColors[chordIndex]?.let { color ->
                val opening = "<c=${color.syntaxName}>"
                val start = anchor.lyricOffset - opening.length
                start.takeIf {
                    it >= 0 && chordProLine.lyricText.startsWith(opening + "</c>", it)
                }
            }
        }.toSet()
        val richTextLine = parsePrompterRichText(chordProLine.lyricText, emptyColorOffsets).lines.single()
        val chords = chordProLine.anchors.mapIndexed { chordIndex, anchor ->
            PrompterPreparedChord(
                anchor = anchor,
                plainTextOffset = richTextLine.plainOffsetForInputOffset(anchor.lyricOffset),
                color = chordColors[chordIndex]
            )
        }
        if (sourceColorLine != null) {
            sourceOffset += sourceColorLine.source.length
            if (source.getOrNull(sourceOffset) == '\r') sourceOffset++
            if (source.getOrNull(sourceOffset) == '\n') sourceOffset++
        }
        PrompterPreparedLine(
            chordProLine = chordProLine,
            richTextLine = richTextLine,
            chords = chords
        )
    }

    return PrompterPreparedDocument(
        source = source,
        lines = lines,
        hasChords = chordProDocument.hasChords,
        hasFormatting = lines.any(PrompterPreparedLine::hasFormatting)
    )
}
