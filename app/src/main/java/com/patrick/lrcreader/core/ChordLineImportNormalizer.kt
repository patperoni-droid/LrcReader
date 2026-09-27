package com.patrick.lrcreader.core

import java.text.BreakIterator
import java.util.Locale

private const val CHORD_LINE_TAB_STOP = 4
private const val MAX_CHORD_COLUMN_OVERRUN = 2
private const val MAX_FORWARD_WORD_SNAP = 2
private const val MAX_CHORD_LINES_PER_BLOCK = 3

internal class ChordLineImportAnalysis internal constructor(
    val source: String,
    val replacements: List<ChordProImportReplacement>,
    val ambiguousLineCount: Int
) {
    val convertiblePairCount: Int get() = replacements.size
}

private data class SourceLine(
    val text: String,
    val sourceStart: Int,
    val sourceEndExclusive: Int
)

private data class LogicalUnit(
    val start: Int,
    val endExclusive: Int,
    val columnStart: Int,
    val columnEndExclusive: Int,
    val isWhitespace: Boolean
)

private data class ChordLineToken(
    val text: String,
    val column: Int,
    val isChord: Boolean
)

private data class LineClassification(
    val tokens: List<ChordLineToken>,
    val hasChordPro: Boolean
) {
    val validChordCount: Int = tokens.count(ChordLineToken::isChord)
    val looksLikeChordContent: Boolean = validChordCount >= 2
    val isConvertibleChordLine: Boolean = looksLikeChordContent &&
        tokens.filterNot(ChordLineToken::isChord).none { it.text.looksLikeMarkup() }
    val isAmbiguousChordLine: Boolean = looksLikeChordContent && !isConvertibleChordLine
}

/**
 * Finds conservative blocks of one to three chord lines followed immediately by a lyric line.
 * Tabs use deterministic four-column stops; this is necessarily an approximation of the
 * tab width used by the application from which the text was copied.
 */
internal fun analyzeChordLineImport(source: String): ChordLineImportAnalysis {
    val lines = splitSourceLines(source)
    val classifications = lines.map(::classifyLine)
    val replacements = mutableListOf<ChordProImportReplacement>()
    var ambiguousLineCount = 0
    var index = 0

    while (index < lines.size) {
        val classification = classifications[index]
        if (!classification.isConvertibleChordLine) {
            if (classification.isAmbiguousChordLine) ambiguousLineCount += 1
            index += 1
            continue
        }
        if (classification.hasChordPro) {
            ambiguousLineCount += 1
            index += 1
            continue
        }

        val previousLooksLikeChords = classifications.getOrNull(index - 1)?.looksLikeChordContent == true
        var lyricLineIndex = index
        while (
            lyricLineIndex < lines.size &&
            classifications[lyricLineIndex].isConvertibleChordLine &&
            !classifications[lyricLineIndex].hasChordPro
        ) {
            lyricLineIndex += 1
        }
        val chordLineCount = lyricLineIndex - index
        if (previousLooksLikeChords || chordLineCount > MAX_CHORD_LINES_PER_BLOCK) {
            ambiguousLineCount += 1
            index = lyricLineIndex
            continue
        }

        val lyricLine = lines.getOrNull(lyricLineIndex)
        val lyricClassification = classifications.getOrNull(lyricLineIndex)
        val cannotPair = lyricLine == null ||
            lyricLine.text.isAnalysisBlank() ||
            lyricLine.text.isSectionLine() ||
            lyricClassification?.hasChordPro == true ||
            lyricClassification?.looksLikeChordContent == true

        if (cannotPair) {
            ambiguousLineCount += 1
            index = lyricLineIndex
            continue
        }

        val chordTokens = (index until lyricLineIndex).flatMap { chordLineIndex ->
            classifications[chordLineIndex].tokens.filter(ChordLineToken::isChord)
        }
        val insertions = chordTokens.map { token ->
            val lyricOffset = lyricOffsetForColumn(lyricLine.text, token.column)
                ?: return@map null
            lyricOffset to "[${token.text}]"
        }
        if (insertions.any { it == null }) {
            ambiguousLineCount += 1
            index = lyricLineIndex
            continue
        }

        val convertedLyric = StringBuilder(lyricLine.text)
        insertions.filterNotNull()
            .groupBy(keySelector = { it.first }, valueTransform = { it.second })
            .toSortedMap(compareByDescending { it })
            .forEach { (offset, tags) -> convertedLyric.insert(offset, tags.joinToString("")) }

        replacements += ChordProImportReplacement(
            sourceRange = lines[index].sourceStart until lyricLine.sourceEndExclusive,
            replacement = convertedLyric.toString()
        )
        index = lyricLineIndex + 1
    }

    return ChordLineImportAnalysis(
        source = source,
        replacements = replacements.toList(),
        ambiguousLineCount = ambiguousLineCount
    )
}

/** Applies only replacements calculated for the exact source snapshot in [analysis]. */
internal fun applyChordLineImport(analysis: ChordLineImportAnalysis): String =
    applyChordProImport(ChordProImportAnalysis(analysis.source, analysis.replacements))

private fun classifyLine(line: SourceLine): LineClassification {
    val tokens = tokenizeChordLine(line)
    return LineClassification(
        tokens = tokens,
        hasChordPro = parseChordPro(line.text).hasChords
    )
}

private fun tokenizeChordLine(line: SourceLine): List<ChordLineToken> {
    val units = logicalUnits(line.text)
    val tokens = mutableListOf<ChordLineToken>()
    var unitIndex = 0
    while (unitIndex < units.size) {
        if (units[unitIndex].isWhitespace) {
            unitIndex += 1
            continue
        }
        val first = units[unitIndex]
        var last = first
        unitIndex += 1
        while (unitIndex < units.size && !units[unitIndex].isWhitespace) {
            last = units[unitIndex]
            unitIndex += 1
        }
        val text = line.text.substring(first.start, last.endExclusive)
        tokens += ChordLineToken(
            text = text,
            column = first.columnStart,
            isChord = parseChordSymbol(text) != null
        )
    }
    return tokens
}

private fun lyricOffsetForColumn(lyrics: String, targetColumn: Int): Int? {
    val units = logicalUnits(lyrics)
    val logicalEnd = units.lastOrNull()?.columnEndExclusive ?: 0
    if (targetColumn > logicalEnd) {
        return lyrics.length.takeIf { targetColumn - logicalEnd <= MAX_CHORD_COLUMN_OVERRUN }
    }
    if (targetColumn == logicalEnd) return lyrics.length

    val targetUnitIndex = units.indexOfFirst {
        targetColumn >= it.columnStart && targetColumn < it.columnEndExclusive
    }
    if (targetUnitIndex < 0) return null
    val targetUnit = units[targetUnitIndex]
    if (!targetUnit.isWhitespace) return targetUnit.start

    val nextWord = units.drop(targetUnitIndex).firstOrNull { !it.isWhitespace }
    if (nextWord != null && nextWord.columnStart - targetColumn <= MAX_FORWARD_WORD_SNAP) {
        return nextWord.start
    }

    val distanceToStart = targetColumn - targetUnit.columnStart
    val distanceToEnd = targetUnit.columnEndExclusive - targetColumn
    return if (distanceToStart <= distanceToEnd) targetUnit.start else targetUnit.endExclusive
}

private fun logicalUnits(text: String): List<LogicalUnit> {
    if (text.isEmpty()) return emptyList()
    val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
    iterator.setText(text)
    val units = mutableListOf<LogicalUnit>()
    var column = 0
    var start = iterator.first()
    var end = iterator.next()
    while (end != BreakIterator.DONE) {
        val value = text.substring(start, end)
        val nextColumn = if (value == "\t") {
            column + (CHORD_LINE_TAB_STOP - column % CHORD_LINE_TAB_STOP)
        } else {
            column + 1
        }
        units += LogicalUnit(
            start = start,
            endExclusive = end,
            columnStart = column,
            columnEndExclusive = nextColumn,
            isWhitespace = value.all(::isAnalysisWhitespace)
        )
        column = nextColumn
        start = end
        end = iterator.next()
    }
    return units
}

private fun splitSourceLines(source: String): List<SourceLine> {
    val lines = mutableListOf<SourceLine>()
    var lineStart = 0
    var cursor = 0
    while (cursor < source.length) {
        if (source[cursor] != '\r' && source[cursor] != '\n') {
            cursor += 1
            continue
        }
        lines += SourceLine(source.substring(lineStart, cursor), lineStart, cursor)
        if (source[cursor] == '\r' && source.getOrNull(cursor + 1) == '\n') cursor += 1
        cursor += 1
        lineStart = cursor
    }
    lines += SourceLine(source.substring(lineStart), lineStart, source.length)
    return lines
}

private fun String.isAnalysisBlank(): Boolean = isEmpty() || all(::isAnalysisWhitespace)

private fun String.isSectionLine(): Boolean {
    val trimmed = trimAnalysisWhitespace()
    return trimmed.startsWith("## ") ||
        (trimmed.length >= 2 && trimmed.first() == '[' && trimmed.last() == ']')
}

private fun String.trimAnalysisWhitespace(): String {
    var start = 0
    var end = length
    while (start < end && isAnalysisWhitespace(this[start])) start += 1
    while (end > start && isAnalysisWhitespace(this[end - 1])) end -= 1
    return substring(start, end)
}

private fun String.looksLikeMarkup(): Boolean =
    any { it == '[' || it == ']' || it == '<' || it == '>' || it == '{' || it == '}' || it == '*' } ||
        startsWith('#')

private fun isAnalysisWhitespace(char: Char): Boolean =
    char.isWhitespace() || Character.isSpaceChar(char)
