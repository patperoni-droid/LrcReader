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
        get() = blockKind != PrompterRichTextBlockKind.BODY || spans.isNotEmpty()
}

data class PrompterPreparedChord(
    val anchor: ChordAnchor,
    val plainTextOffset: Int
) {
    val symbol: ChordSymbol
        get() = anchor.symbol
}

fun preparePrompterText(source: String): PrompterPreparedDocument {
    val chordProDocument = parseChordPro(source)
    val lines = chordProDocument.lines.map { chordProLine ->
        val richTextLine = parsePrompterRichText(chordProLine.lyricText).lines.single()
        val chords = chordProLine.anchors.map { anchor ->
            PrompterPreparedChord(
                anchor = anchor,
                plainTextOffset = richTextLine.plainOffsetForInputOffset(anchor.lyricOffset)
            )
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
