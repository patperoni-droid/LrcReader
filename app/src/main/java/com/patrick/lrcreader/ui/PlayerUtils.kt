/**
 * Utilitaires Player & Prompteur
 *
 * Ce fichier sert à deux petites choses importantes :
 *
 * 1) formatMs(ms)
 *    → Transforme une durée en millisecondes en un format lisible pour nous (comme "01:20").
 *      En gros, ça évite d’afficher des chiffres bizarres. Très utile dans tout le lecteur.
 *
 * 2) buildPrompterText(...)
 *    → Décide quel texte doit être affiché dans le mode prompteur.
 *      - Si on a des paroles synchronisées : il récupère juste le texte propre.
 *      - Sinon : il prend les paroles brutes écrites par l’utilisateur.
 *      - S’il n’y a rien : il renvoie un texte vide.
 *
 * En résumé :
 * 👉 Ce fichier ne fait rien de visible à l’écran,
 *    mais il prépare le texte et les durées pour que le lecteur et le prompteur
 *    fonctionnent proprement.
 */
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