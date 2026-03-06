package com.patrick.lrcreader.core

import kotlin.math.max
import kotlin.math.min

data class TextInsertionResult(
    val text: String,
    val cursor: Int
)

private const val LIVE_CHORD_COMPENSATION_MS = 150L
private const val LIVE_CHORD_DOUBLE_CLICK_GUARD_MS = 80L

fun parseChordPaletteInput(raw: String): List<String> {
    return raw
        .split(Regex("""[,\n;]+"""))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
}

fun inferChordPaletteFromText(raw: String): List<String> {
    if (raw.isBlank()) return emptyList()
    val chordToken = Regex("""^[A-G](?:#|b)?(?:m|maj|min|sus|dim|aug|add)?\d*(?:/[A-G](?:#|b)?)?$""")
    val tokens = raw
        .split(Regex("""[\s,;|]+"""))
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    return buildList {
        tokens.forEach { token ->
            if (chordToken.matches(token) && token !in this) {
                add(token)
            }
        }
    }
}

fun sortChordPaletteByUsage(
    palette: List<String>,
    rawText: String
): List<String> {
    if (palette.isEmpty()) return emptyList()
    if (rawText.isBlank()) return palette

    val tokens = rawText
        .split(Regex("""[\s,;|]+"""))
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    if (tokens.isEmpty()) return palette

    val usageCountByToken = mutableMapOf<String, Int>()
    tokens.forEach { token ->
        val normalized = token.lowercase()
        usageCountByToken[normalized] = (usageCountByToken[normalized] ?: 0) + 1
    }

    return palette
        .mapIndexed { index, chord ->
            Triple(chord, usageCountByToken[chord.lowercase()] ?: 0, index)
        }
        .sortedWith(
            compareByDescending<Triple<String, Int, Int>> { it.second }
                .thenBy { it.third }
        )
        .map { it.first }
}

fun insertChordAtCursor(
    text: String,
    selectionStart: Int,
    selectionEnd: Int,
    chord: String
): TextInsertionResult {
    if (chord.isBlank()) return TextInsertionResult(text = text, cursor = selectionEnd.coerceIn(0, text.length))

    val safeStart = min(selectionStart, selectionEnd).coerceIn(0, text.length)
    val safeEnd = max(selectionStart, selectionEnd).coerceIn(0, text.length)
    val before = text.substring(0, safeStart)
    val after = text.substring(safeEnd)

    val prefix = if (before.isNotEmpty() && !before.last().isWhitespace()) " " else ""
    val suffix = if (after.isNotEmpty() && !after.first().isWhitespace()) " " else ""
    val inserted = "$prefix$chord$suffix"
    val nextText = before + inserted + after
    val nextCursor = (before + inserted).length
    return TextInsertionResult(text = nextText, cursor = nextCursor)
}

fun captureLiveChord(
    chord: String,
    playerPositionMs: Long,
    compensationMs: Long = LIVE_CHORD_COMPENSATION_MS
): LrcLine {
    val safeChord = chord.trim()
    val capturedTime = (playerPositionMs - compensationMs).coerceAtLeast(0L)
    return LrcLine(
        timeMs = capturedTime,
        text = safeChord
    )
}

fun formatLrcTimeTag(timeMs: Long): String {
    val safe = timeMs.coerceAtLeast(0L)
    val totalSeconds = safe / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val hundredths = (safe % 1000) / 10
    return "[%02d:%02d.%02d]".format(minutes, seconds, hundredths)
}

fun formatCapturedLiveChordLine(line: LrcLine): String {
    return "${formatLrcTimeTag(line.timeMs)} ${line.text.trim()}"
}

fun appendCapturedChordLineSorted(
    current: List<LrcLine>,
    newLine: LrcLine
): List<LrcLine> {
    return (current + newLine).sortedBy { it.timeMs }
}

fun isLiveCaptureAllowed(
    nowElapsedMs: Long,
    lastCaptureElapsedMs: Long?,
    minIntervalMs: Long = LIVE_CHORD_DOUBLE_CLICK_GUARD_MS
): Boolean {
    if (lastCaptureElapsedMs == null) return true
    return nowElapsedMs - lastCaptureElapsedMs >= minIntervalMs
}
