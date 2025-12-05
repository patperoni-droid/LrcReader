package com.patrick.lrcreader.core

import android.util.Log   // ⬅️ ICI
// même regex qu'avant : [mm:ss.xx] ou [m:s]


/**
 * Parse un texte LRC en listant TOUTES les lignes.
 *
 * - lignes AVEC timestamp -> OK
 * - lignes SANS timestamp -> on les accroche au dernier timestamp rencontré
 *   (ou 0 ms s'il n'y en a pas encore)
 *
 * On ne redéclare PAS LrcLine ici, on utilise celui de LrcLine.kt
 */


/**
 * Parser LRC simple, compatible avec ce que LrcStorage.saveForTrack écrit :
 * [mm:ss.xx]Texte de la ligne
 */
fun parseLrc(raw: String): List<LrcLine> {
    val result = mutableListOf<LrcLine>()

    // Chaque ligne du fichier .lrc
    val lines = raw.lines()

    // Regex pour trouver les timestamps du style [01:23.45]
    val tagRegex = Regex("""\[(\d{1,2}):(\d{2})(?:\.(\d{1,2}))?]""")

    for (line in lines) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) continue

        val matches = tagRegex.findAll(trimmed).toList()

        // Texte = la ligne sans les [mm:ss.xx]
        val textPart = trimmed.replace(tagRegex, "").trim()

        if (matches.isEmpty()) {
            // AUCUN TAG : on garde la ligne mais avec timeMs = 0
            if (textPart.isNotEmpty()) {
                result.add(
                    LrcLine(
                        timeMs = 0L,
                        text = textPart
                    )
                )
            }
            Log.d("LrcDebug", "PARSE no-tag: text='$textPart'")
        } else {
            // UNE OU PLUSIEURS BALISES [mm:ss.xx]
            for (m in matches) {
                val min = m.groupValues[1].toLongOrNull() ?: 0L
                val sec = m.groupValues[2].toLongOrNull() ?: 0L
                val hundredthsRaw = m.groupValues.getOrNull(3) ?: "0"
                val hundredths = hundredthsRaw.padEnd(2, '0').take(2).toLongOrNull() ?: 0L

                val ms = (min * 60_000L) + (sec * 1_000L) + (hundredths * 10L)

                result.add(
                    LrcLine(
                        timeMs = ms,
                        text = textPart
                    )
                )

                Log.d(
                    "LrcDebug",
                    "PARSE tag: [$min:$sec.$hundredthsRaw] → $ms ms, text='$textPart'"
                )
            }
        }
    }

    // On trie par temps pour être sûr que l'affichage soit propre
    return result
}