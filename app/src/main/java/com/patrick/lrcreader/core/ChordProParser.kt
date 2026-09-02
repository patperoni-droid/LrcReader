package com.patrick.lrcreader.core

data class ChordProDocument(
    val source: String,
    val lines: List<ChordProLine>,
    val hasChords: Boolean
)

data class ChordProLine(
    val lyricText: String,
    val anchors: List<ChordAnchor>
)

data class ChordAnchor(
    val lyricOffset: Int,
    val sourceRange: IntRange,
    val symbol: ChordSymbol
)

data class ChordSymbol(
    val raw: String,
    val root: Char,
    val accidental: Char?,
    val suffix: String,
    val bassRoot: Char?,
    val bassAccidental: Char?
)

private data class ChordProSourceLine(
    val text: String,
    val sourceOffset: Int
)

private val CHORD_SUFFIX_TOKENS = listOf(
    "omit",
    "maj",
    "min",
    "dim",
    "aug",
    "sus",
    "add",
    "dom",
    "alt",
    "no",
    "m",
    "M"
)

fun parseChordPro(source: String): ChordProDocument {
    val lines = splitChordProSourceLines(source).map(::parseChordProLine)
    return ChordProDocument(
        source = source,
        lines = lines,
        hasChords = lines.any { it.anchors.isNotEmpty() }
    )
}

private fun parseChordProLine(sourceLine: ChordProSourceLine): ChordProLine {
    val lyricText = StringBuilder(sourceLine.text.length)
    val anchors = mutableListOf<ChordAnchor>()
    var cursor = 0

    while (cursor < sourceLine.text.length) {
        if (sourceLine.text[cursor] != '[') {
            lyricText.append(sourceLine.text[cursor])
            cursor += 1
            continue
        }

        val closingBracket = sourceLine.text.indexOf(']', startIndex = cursor + 1)
        if (closingBracket < 0) {
            lyricText.append(sourceLine.text, cursor, sourceLine.text.length)
            break
        }

        val rawSymbol = sourceLine.text.substring(cursor + 1, closingBracket)
        val symbol = parseChordSymbol(rawSymbol)
        if (symbol == null) {
            lyricText.append(sourceLine.text, cursor, closingBracket + 1)
        } else {
            anchors += ChordAnchor(
                lyricOffset = lyricText.length,
                sourceRange = (sourceLine.sourceOffset + cursor)..(sourceLine.sourceOffset + closingBracket),
                symbol = symbol
            )
        }
        cursor = closingBracket + 1
    }

    return ChordProLine(
        lyricText = lyricText.toString(),
        anchors = anchors
    )
}

private fun parseChordSymbol(raw: String): ChordSymbol? {
    if (raw.isEmpty() || raw.any(Char::isWhitespace)) return null

    val slashIndex = raw.indexOf('/')
    if (slashIndex != raw.lastIndexOf('/')) return null

    val main = if (slashIndex >= 0) raw.substring(0, slashIndex) else raw
    val bass = if (slashIndex >= 0) raw.substring(slashIndex + 1) else null
    if (main.isEmpty() || (slashIndex >= 0 && bass.isNullOrEmpty())) return null

    val root = main[0]
    if (root !in 'A'..'G') return null

    val accidental = main.getOrNull(1)?.takeIf { it == '#' || it == 'b' }
    val suffixStart = if (accidental == null) 1 else 2
    val suffix = main.substring(suffixStart)
    if (!isRecognizedChordSuffix(suffix)) return null

    val bassRoot = bass?.getOrNull(0)
    if (bass != null) {
        if (bassRoot !in 'A'..'G') return null
        if (bass.length > 2) return null
        if (bass.length == 2 && bass[1] != '#' && bass[1] != 'b') return null
    }

    return ChordSymbol(
        raw = raw,
        root = root,
        accidental = accidental,
        suffix = suffix,
        bassRoot = bassRoot,
        bassAccidental = bass?.getOrNull(1)
    )
}

private fun isRecognizedChordSuffix(suffix: String): Boolean {
    if (suffix.isEmpty()) return true

    var cursor = 0
    var parenthesisDepth = 0
    while (cursor < suffix.length) {
        val char = suffix[cursor]
        when {
            char.isDigit() || char == '+' || char == '-' || char == '°' || char == 'ø' || char == 'Δ' -> {
                cursor += 1
            }

            char == '(' -> {
                parenthesisDepth += 1
                cursor += 1
            }

            char == ')' -> {
                if (parenthesisDepth == 0) return false
                parenthesisDepth -= 1
                cursor += 1
            }

            char == '#' || char == 'b' -> {
                if (suffix.getOrNull(cursor + 1)?.isDigit() != true) return false
                cursor += 1
            }

            else -> {
                val token = CHORD_SUFFIX_TOKENS.firstOrNull { suffix.startsWith(it, cursor) }
                    ?: return false
                cursor += token.length
            }
        }
    }

    return parenthesisDepth == 0
}

private fun splitChordProSourceLines(source: String): List<ChordProSourceLine> {
    val lines = mutableListOf<ChordProSourceLine>()
    var lineStart = 0
    var cursor = 0

    while (cursor < source.length) {
        val separatorLength = when (source[cursor]) {
            '\r' -> if (source.getOrNull(cursor + 1) == '\n') 2 else 1
            '\n' -> 1
            else -> 0
        }
        if (separatorLength == 0) {
            cursor += 1
            continue
        }

        lines += ChordProSourceLine(
            text = source.substring(lineStart, cursor),
            sourceOffset = lineStart
        )
        cursor += separatorLength
        lineStart = cursor
    }

    lines += ChordProSourceLine(
        text = source.substring(lineStart),
        sourceOffset = lineStart
    )
    return lines
}
