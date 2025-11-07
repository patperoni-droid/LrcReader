package com.patrick.lrcreader.core

import android.content.Context

/**
 * Stocke les réglages d’édition d’un fichier audio :
 * - startMs : point d’entrée
 * - endMs   : point de sortie
 *
 * On met ça dans des SharedPreferences.
 */
object EditPrefs {

    private const val PREFS_NAME = "edit_prefs"
    private const val PREFIX = "edit_"   // clé = edit_[uriString]

    data class EditData(
        val startMs: Long,
        val endMs: Long
    )

    /**
     * Sauvegarde un réglage pour un fichier audio.
     * @param uriString -> Uri.toString() du fichier
     */
    fun saveEdit(context: Context, uriString: String, data: EditData) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // on stocke "startMs|endMs"
        val value = "${data.startMs}|${data.endMs}"
        prefs.edit()
            .putString(PREFIX + uriString, value)
            .apply()
    }

    /**
     * Récupère le réglage pour un fichier donné, ou null si on n’a rien.
     */
    fun getEdit(context: Context, uriString: String): EditData? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(PREFIX + uriString, null) ?: return null
        val parts = raw.split("|")
        if (parts.size != 2) return null
        val start = parts[0].toLongOrNull() ?: return null
        val end = parts[1].toLongOrNull() ?: return null
        return EditData(start, end)
    }

    /**
     * Pour la sauvegarde complète : on renvoie TOUT ce qu’on connaît.
     * clé = uriString
     */
    fun getAllEdits(context: Context): Map<String, EditData> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val out = mutableMapOf<String, EditData>()
        for ((key, value) in prefs.all) {
            if (!key.startsWith(PREFIX)) continue
            val uriString = key.removePrefix(PREFIX)
            val str = value as? String ?: continue
            val parts = str.split("|")
            if (parts.size != 2) continue
            val start = parts[0].toLongOrNull() ?: continue
            val end = parts[1].toLongOrNull() ?: continue
            out[uriString] = EditData(start, end)
        }
        return out
    }

    /**
     * On efface tout (utile quand on importe un backup).
     */
    fun clearAll(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        // on supprime seulement nos clés à nous
        for (key in prefs.all.keys) {
            if (key.startsWith(PREFIX)) {
                editor.remove(key)
            }
        }
        editor.apply()
    }
}