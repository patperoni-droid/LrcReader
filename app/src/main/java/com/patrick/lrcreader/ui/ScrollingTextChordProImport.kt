package com.patrick.lrcreader.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.patrick.lrcreader.core.ChordLineImportAnalysis
import com.patrick.lrcreader.core.ChordProImportAnalysis
import com.patrick.lrcreader.core.ChordProImportReplacement
import com.patrick.lrcreader.core.analyzeChordLineImport
import com.patrick.lrcreader.core.analyzeChordProImport
import com.patrick.lrcreader.core.applyChordLineImport
import com.patrick.lrcreader.core.applyChordProImport

internal sealed interface ScrollingTextChordProImportSuggestion {
    val source: String
    val candidateCount: Int

    data class ExplicitChordMarkup(
        val analysis: ChordProImportAnalysis
    ) : ScrollingTextChordProImportSuggestion {
        override val source: String get() = analysis.source
        override val candidateCount: Int get() = analysis.candidateCount
    }

    data class ChordLines(
        val analysis: ChordLineImportAnalysis
    ) : ScrollingTextChordProImportSuggestion {
        override val source: String get() = analysis.source
        override val candidateCount: Int get() = analysis.convertiblePairCount
    }
}

internal fun chordProImportSuggestion(
    source: String,
    ignoredSource: String?
): ChordProImportAnalysis? {
    if (source == ignoredSource) return null
    return analyzeChordProImport(source).takeIf { it.candidateCount > 0 }
}

internal fun scrollingTextChordProImportSuggestion(
    source: String,
    ignoredExplicitChordSource: String?,
    ignoredChordLineSource: String?
): ScrollingTextChordProImportSuggestion? {
    chordProImportSuggestion(source, ignoredExplicitChordSource)?.let {
        return ScrollingTextChordProImportSuggestion.ExplicitChordMarkup(it)
    }
    if (source == ignoredChordLineSource) return null
    return analyzeChordLineImport(source)
        .takeIf { it.convertiblePairCount > 0 }
        ?.let { ScrollingTextChordProImportSuggestion.ChordLines(it) }
}

/** Returns null rather than applying offsets computed for an older editor value. */
internal fun applyChordProImportSuggestion(
    value: TextFieldValue,
    analysis: ChordProImportAnalysis
): TextFieldValue? = remapChordProImportValue(
    value = value,
    source = analysis.source,
    replacements = analysis.replacements,
    buildUpdatedText = { applyChordProImport(analysis) }
)

internal fun applyScrollingTextChordProImportSuggestion(
    value: TextFieldValue,
    suggestion: ScrollingTextChordProImportSuggestion
): TextFieldValue? = when (suggestion) {
    is ScrollingTextChordProImportSuggestion.ExplicitChordMarkup ->
        applyChordProImportSuggestion(value, suggestion.analysis)

    is ScrollingTextChordProImportSuggestion.ChordLines -> remapChordProImportValue(
        value = value,
        source = suggestion.analysis.source,
        replacements = suggestion.analysis.replacements,
        buildUpdatedText = { applyChordLineImport(suggestion.analysis) }
    )
}

private fun remapChordProImportValue(
    value: TextFieldValue,
    source: String,
    replacements: List<ChordProImportReplacement>,
    buildUpdatedText: () -> String
): TextFieldValue? {
    if (value.text != source) return null
    val updatedText = buildUpdatedText()

    fun mapOffset(offset: Int): Int {
        var delta = 0
        replacements.forEach { edit ->
            val start = edit.sourceRange.first
            val endExclusive = edit.sourceRange.last + 1
            if (offset <= start) return offset + delta
            if (offset < endExclusive) return start + delta + edit.replacement.length
            delta += edit.replacement.length - (endExclusive - start)
        }
        return offset + delta
    }

    fun mapRange(range: TextRange): TextRange = TextRange(
        start = mapOffset(range.start).coerceIn(0, updatedText.length),
        end = mapOffset(range.end).coerceIn(0, updatedText.length)
    )

    return TextFieldValue(
        text = updatedText,
        selection = mapRange(value.selection),
        composition = value.composition?.let(::mapRange)
    )
}
