package com.patrick.lrcreader.core

// même regex qu'avant : [mm:ss.xx] ou [m:s]
private val timeRegex = Regex("""\[(\d{1,2}):(\d{1,2})(?:[.:](\d{1,2}))?]""")

/**
 * Parse un texte LRC en listant TOUTES les lignes.
 *
 * - lignes AVEC timestamp -> OK
 * - lignes SANS timestamp -> on les accroche au dernier timestamp rencontré
 *   (ou 0 ms s'il n'y en a pas encore)
 *
 * On ne redéclare PAS LrcLine ici, on utilise celui de LrcLine.kt
 */
fun parseLrc(lrcText: String): List<LrcLine> {
    val result = mutableListOf<LrcLine>()
    var lastTimeMs = 0L   // temps courant (mis à jour à chaque balise)

    for (rawLine in lrcText.lines()) {
        if (rawLine.isBlank()) continue

        val matches = timeRegex.findAll(rawLine).toList()
        val cleanText = rawLine.replace(timeRegex, "").trim()

        if (matches.isEmpty()) {
            // ligne SANS time → on l’ajoute quand même
            if (cleanText.isNotEmpty()) {
                result.add(LrcLine(timeMs = lastTimeMs, text = cleanText))
            }
        } else {
            // ligne AVEC un ou plusieurs timecodes
            for (m in matches) {
                val min = m.groupValues[1].toLong()
                val sec = m.groupValues[2].toLong()
                val cent = m.groupValues.getOrNull(3)?.toLongOrNull() ?: 0
                val timeMs = (min * 60_000) + (sec * 1_000) + (cent * 10)

                // on met à jour le “dernier temps connu”
                lastTimeMs = timeMs

                if (cleanText.isNotEmpty()) {
                    result.add(LrcLine(timeMs = timeMs, text = cleanText))
                }
            }
        }
    }

    return result.sortedBy { it.timeMs }
}