package com.patrick.lrcreader.ui

import com.patrick.lrcreader.core.LrcLine
import com.patrick.lrcreader.core.findActiveLrcIndex
import com.patrick.lrcreader.core.parseChordPro
import com.patrick.lrcreader.core.transposeChord

internal fun displayedChordTransposition(manual: Int, pitch: Int, syncPitchToChords: Boolean): Int =
    manual + if (syncPitchToChords) pitch else 0

internal data class AudioLyricsChordGrid(
    val lines: List<LrcLine>,
    val usesDerivedLyrics: Boolean
)

internal fun deriveAudioLyricsChordGrid(
    lyricsLines: List<LrcLine>,
    transposeSemitones: Int
): List<LrcLine> = lyricsLines.mapNotNull { sourceLine ->
    val chords = parseChordPro(sourceLine.text)
        .lines
        .flatMap { it.anchors }
        .map { anchor -> transposeChord(anchor.symbol, transposeSemitones) }
    chords.takeIf { it.isNotEmpty() }?.let {
        LrcLine(
            timeMs = sourceLine.timeMs,
            text = it.joinToString(separator = "   ")
        )
    }
}

internal fun resolveAudioLyricsChordGrid(
    derivedLines: List<LrcLine>,
    legacyLines: List<LrcLine>
): AudioLyricsChordGrid = if (derivedLines.isNotEmpty()) {
    AudioLyricsChordGrid(lines = derivedLines, usesDerivedLyrics = true)
} else {
    AudioLyricsChordGrid(lines = legacyLines, usesDerivedLyrics = false)
}

internal fun findActiveAudioLyricsChordGridIndex(
    lines: List<LrcLine>,
    positionMs: Long
): Int {
    if (lines.none { it.timeMs > 0L }) return -1
    return findActiveLrcIndex(lines, positionMs)
}
