package com.patrick.lrcreader.ui

import com.patrick.lrcreader.core.LrcLine

/* ─────────────────────────────
   FONCTIONS UTILITAIRES PLAYER
   ───────────────────────────── */

/** Format mm:ss ou h:mm:ss */
fun formatMs(ms: Int): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val s = totalSeconds % 60
    val m = (totalSeconds / 60) % 60
    val h = totalSeconds / 3600
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

/** Construit le texte brut pour le prompteur. */
fun buildPrompterText(
    parsedLines: List<LrcLine>,
    rawLyrics: String
): String {
    return when {
        parsedLines.isNotEmpty() ->
            parsedLines.joinToString("\n") { it.text }
        rawLyrics.isNotBlank() ->
            rawLyrics
        else -> ""
    }
}