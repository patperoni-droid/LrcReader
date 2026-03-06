package com.patrick.lrcreader.core

data class ChordsWindow(
    val previous: LrcLine?,
    val current: LrcLine?,
    val next: List<LrcLine>
)

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
