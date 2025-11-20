package com.patrick.lrcreader.core

/**
 * Nettoie / parse un bloc de paroles LRC brut et renvoie une liste de LrcLine.
 *
 * - Si le texte contient des timecodes [mm:ss.xx] ou [mm:ss],
 *   on les lit et on remplit timeMs.
 * - Sinon, on retourne une liste vide (ce qui permettra au caller
 *   de faire un fallback en "texte simple", comme dans importLyricsFromAudio).
 */
object LrcCleaner {

    private val TAG_REGEX =
        Regex("""\[(\d{1,2}):(\d{1,2})(?:\.(\d{1,3}))?]""") // [mm:ss] ou [mm:ss.xxx]

    fun clean(raw: String): List<LrcLine> {
        // On enlève un éventuel BOM et on trim
        val text = raw.replace("\uFEFF", "").trim()
        if (text.isBlank()) return emptyList()

        val result = mutableListOf<LrcLine>()

        for (line in text.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            val matches = TAG_REGEX.findAll(trimmed).toList()
            if (matches.isEmpty()) {
                // Pas de tag temps sur cette ligne -> on ignore ici.
                // (importLyricsFromAudio fera le fallback "une ligne = une phrase"
                // si clean(...) renvoie une liste vide)
                continue
            }

            // On enlève tous les [mm:ss.xx] du texte pour ne garder que la phrase
            val content = TAG_REGEX.replace(trimmed, "").trim()
            if (content.isEmpty()) continue

            // Pour chaque tag de la ligne, on crée une LrcLine
            for (m in matches) {
                val minutes = m.groupValues[1].toLongOrNull() ?: 0L
                val seconds = m.groupValues[2].toLongOrNull() ?: 0L
                val fracStr = m.groupValues.getOrNull(3).orEmpty()

                // On convertit la partie fractionnaire en millisecondes
                val fracMs = when (fracStr.length) {
                    3 -> fracStr.toLongOrNull() ?: 0L
                    2 -> (fracStr.toLongOrNull() ?: 0L) * 10L
                    1 -> (fracStr.toLongOrNull() ?: 0L) * 100L
                    else -> 0L
                }

                val timeMs = minutes * 60_000L + seconds * 1_000L + fracMs

                result += LrcLine(
                    timeMs = timeMs,
                    text = content
                )
            }
        }

        // On trie par temps croissant
        return result.sortedBy { it.timeMs }
    }
}
