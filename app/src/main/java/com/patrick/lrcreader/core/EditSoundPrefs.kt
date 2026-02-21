package com.patrick.lrcreader.core

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import org.json.JSONObject

/**
 * Stocke, pour chaque fichier audio, les points d'entrée/sortie choisis
 * dans l'écran d’édition.
 *
 * On garde ça dans un seul SharedPreferences sous forme de JSON.
 */
object EditSoundPrefs {

    private const val PREFS_NAME = "edit_sound_prefs"
    private const val KEY_JSON = "edits_json"

    data class EditInfo(
        val startMs: Int,
        val endMs: Int
    )

    data class ResolvedEdit(
        val key: String,
        val info: EditInfo
    )

    fun trimKeyForUri(uri: Uri): String {
        return runCatching {
            DocumentsContract.getDocumentId(uri)
        }.getOrElse {
            uri.toString()
        }
    }

    /**
     * Sauvegarde un réglage pour un fichier.
     */
    fun save(context: Context, uri: Uri, startMs: Int, endMs: Int) {
        val map = getAll(context).toMutableMap()
        val key = trimKeyForUri(uri)
        map[key] = EditInfo(startMs, endMs)
        val legacyKey = uri.toString()
        if (legacyKey != key) {
            map.remove(legacyKey)
        }
        persist(context, map)
    }

    /**
     * Récupère le réglage d’un fichier (ou null s’il n’y en a pas).
     */
    fun get(context: Context, uri: Uri): EditInfo? {
        return resolve(context, uri)?.info
    }

    fun resolve(context: Context, uri: Uri): ResolvedEdit? {
        val all = getAll(context)
        val key = trimKeyForUri(uri)
        all[key]?.let { return ResolvedEdit(key = key, info = it) }

        val legacyKey = uri.toString()
        if (legacyKey != key) {
            all[legacyKey]?.let { return ResolvedEdit(key = legacyKey, info = it) }
        }
        return null
    }

    /**
     * Supprime seulement l’édition de ce fichier.
     */
    fun clearOne(context: Context, uri: Uri) {
        val map = getAll(context).toMutableMap()
        var removed = false
        val key = trimKeyForUri(uri)
        if (map.remove(key) != null) removed = true
        val legacyKey = uri.toString()
        if (legacyKey != key && map.remove(legacyKey) != null) removed = true
        if (removed) {
            persist(context, map)
        }
    }

    /**
     * Renvoie toutes les éditions sous forme Map<String, EditInfo>
     * (pratique pour la sauvegarde globale).
     */
    fun getAll(context: Context): Map<String, EditInfo> {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = sp.getString(KEY_JSON, null) ?: return emptyMap()
        return try {
            val root = JSONObject(raw)
            val result = mutableMapOf<String, EditInfo>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val obj = root.getJSONObject(k)
                val start = obj.optInt("startMs", 0)
                val end = obj.optInt("endMs", 0)
                result[k] = EditInfo(start, end)
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    // ───────────────── interne ─────────────────

    private fun persist(context: Context, map: Map<String, EditInfo>) {
        val root = JSONObject()
        map.forEach { (uriStr, info) ->
            val o = JSONObject()
            o.put("startMs", info.startMs)
            o.put("endMs", info.endMs)
            root.put(uriStr, o)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_JSON, root.toString())
            .apply()
    }
}
