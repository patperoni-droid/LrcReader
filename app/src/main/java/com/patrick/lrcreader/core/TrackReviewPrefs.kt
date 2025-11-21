package com.patrick.lrcreader.core

import android.content.Context
import androidx.core.content.edit

/**
 * Stocke les morceaux marqués "À revoir".
 * Clé = un identifiant de morceau (souvent l'URI ou le path).
 */
object TrackReviewPrefs {

    private const val PREFS_NAME = "track_review_prefs"
    private const val KEY_PREFIX = "needs_review_"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Retourne true si ce morceau est marqué "À revoir". */
    fun isMarked(context: Context, trackKey: String): Boolean {
        return prefs(context).getBoolean(KEY_PREFIX + trackKey, false)
    }

    /** Marque ou dé-marque un morceau. */
    fun setMarked(context: Context, trackKey: String, value: Boolean) {
        prefs(context).edit {
            if (value) {
                putBoolean(KEY_PREFIX + trackKey, true)
            } else {
                remove(KEY_PREFIX + trackKey)
            }
        }
    }

    /** Efface tout (si un jour tu veux un bouton pour tout remettre à zéro). */
    fun clearAll(context: Context) {
        prefs(context).edit { clear() }
    }
}