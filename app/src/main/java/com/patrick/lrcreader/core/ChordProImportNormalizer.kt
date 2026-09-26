package com.patrick.lrcreader.core

internal data class ChordProImportReplacement(
    val sourceRange: IntRange,
    val replacement: String
)

/** Offsets are UTF-16 indices in this exact source snapshot, including line separators. */
internal class ChordProImportAnalysis internal constructor(
    val source: String,
    val replacements: List<ChordProImportReplacement>
) {
    val candidateCount: Int get() = replacements.size
}

/**
 * Proposes only exact, explicitly bold chord symbols. No whitespace is normalized.
 * A line with unmatched, escaped or non-double asterisks is conservatively ignored:
 * interpreting partial or nested Markdown is outside this importer's scope.
 */
internal fun analyzeChordProImport(source: String): ChordProImportAnalysis {
    val replacements = mutableListOf<ChordProImportReplacement>()
    var lineStart = 0
    while (lineStart < source.length) {
        var lineEnd = lineStart
        while (lineEnd < source.length && source[lineEnd] != '\r' && source[lineEnd] != '\n') {
            lineEnd++
        }
        analyzeExplicitChordLine(source, lineStart, lineEnd, replacements)
        lineStart = lineEnd + 1
        if (source.getOrNull(lineEnd) == '\r' && source.getOrNull(lineStart) == '\n') lineStart++
    }
    return ChordProImportAnalysis(source, replacements.toList())
}

/** Applies the proposal only to its original snapshot; does not read or mutate editor state. */
internal fun applyChordProImport(analysis: ChordProImportAnalysis): String {
    val result = StringBuilder(analysis.source)
    analysis.replacements.asReversed().forEach { edit ->
        result.replace(edit.sourceRange.first, edit.sourceRange.last + 1, edit.replacement)
    }
    return result.toString()
}

private fun analyzeExplicitChordLine(
    source: String,
    start: Int,
    end: Int,
    replacements: MutableList<ChordProImportReplacement>
) {
    val markers = mutableListOf<Pair<Int, Boolean>>()
    var bracketDepth = 0
    var cursor = start
    while (cursor < end) {
        when (source[cursor]) {
            '[' -> bracketDepth++
            ']' -> if (bracketDepth > 0) bracketDepth--
            '*' -> {
                val markerStart = cursor
                while (cursor < end && source[cursor] == '*') cursor++
                if (cursor - markerStart != 2) return
                var backslashStart = markerStart
                while (backslashStart > start && source[backslashStart - 1] == '\\') backslashStart--
                if ((markerStart - backslashStart) % 2 != 0) return
                markers += markerStart to (bracketDepth > 0)
                continue
            }
        }
        cursor++
    }
    if (markers.size % 2 != 0) return
    for (index in markers.indices step 2) {
        val (opening, openingInBrackets) = markers[index]
        val (closing, closingInBrackets) = markers[index + 1]
        if (openingInBrackets || closingInBrackets) continue
        val raw = source.substring(opening + 2, closing)
        if (parseChordSymbol(raw) == null) continue
        replacements += ChordProImportReplacement(opening..(closing + 1), "[$raw]")
    }
}
