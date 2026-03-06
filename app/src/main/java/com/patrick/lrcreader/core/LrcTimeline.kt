package com.patrick.lrcreader.core

enum class LyricsViewMode {
    LYRICS,
    CHORDS
}

data class LyricsChordsUiState(
    val showToggle: Boolean,
    val showMissingChordsMessage: Boolean
)

data class ChordsWindow(
    val previous: LrcLine?,
    val current: LrcLine?,
    val next: List<LrcLine>
)

fun resolveLyricsViewMode(
    current: LyricsViewMode,
    hasLyrics: Boolean,
    hasChords: Boolean
): LyricsViewMode {
    return when {
        current == LyricsViewMode.CHORDS && hasChords -> LyricsViewMode.CHORDS
        hasLyrics -> LyricsViewMode.LYRICS
        hasChords -> LyricsViewMode.CHORDS
        else -> LyricsViewMode.LYRICS
    }
}

fun computeLyricsChordsUiState(hasLyrics: Boolean, hasChords: Boolean): LyricsChordsUiState {
    return LyricsChordsUiState(
        showToggle = hasLyrics && hasChords,
        showMissingChordsMessage = hasLyrics && !hasChords
    )
}

fun resolveChordsLookupFileName(exactLyricsFileName: String?, fallbackBaseName: String): String {
    val exact = exactLyricsFileName?.trim().orEmpty()
    if (exact.isNotBlank()) return exact
    val fallback = fallbackBaseName.trim()
    if (fallback.isBlank()) return ""
    return "$fallback.lrc"
}

fun findActiveLrcIndex(lines: List<LrcLine>, positionMs: Long): Int {
    if (lines.isEmpty()) return -1
    val taggedIndices = lines.withIndex()
        .filter { it.value.timeMs > 0L }
        .map { it.index }

    if (taggedIndices.isEmpty()) return 0

    return taggedIndices.lastOrNull { idx ->
        lines[idx].timeMs <= positionMs
    } ?: -1
}

fun buildChordsWindow(lines: List<LrcLine>, activeIndex: Int, nextCount: Int = 3): ChordsWindow {
    if (lines.isEmpty()) return ChordsWindow(previous = null, current = null, next = emptyList())

    val safeIndex = activeIndex.coerceIn(0, lines.lastIndex)
    val previous = lines.getOrNull(safeIndex - 1)
    val current = lines[safeIndex]
    val next = buildList {
        var idx = safeIndex + 1
        while (idx <= lines.lastIndex && size < nextCount) {
            add(lines[idx])
            idx += 1
        }
    }

    return ChordsWindow(
        previous = previous,
        current = current,
        next = next
    )
}
