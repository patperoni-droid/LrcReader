package com.patrick.lrcreader.core

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

object DjFolderPrefs {
    private const val PREF = "dj_folder_prefs"

    // ancien champ (on le garde pour compat)
    private const val KEY_URI = "dj_folder_uri"

    // nouveaux champs
    private const val KEY_URIS = "dj_folder_uris"       // JSON array de strings
    private const val KEY_CURRENT = "dj_folder_current" // string

    /**
     * Ajoute (ou remplace) un dossier DJ et le met courant.
     * Compatible avec ton ancien code qui faisait juste save(context, uri)
     */
    fun save(context: Context, uri: Uri) {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val all = getAllInternal(prefs).toMutableList()

        // on évite les doublons
        val asString = uri.toString()
        if (all.none { it == asString }) {
            all.add(asString)
        }

        prefs.edit()
            // on garde l’ancien champ pour ne rien casser
            .putString(KEY_URI, asString)
            // nouveaux champs
            .putString(KEY_URIS, toJsonArray(all).toString())
            .putString(KEY_CURRENT, asString)
            .apply()
    }

    /**
     * Ancienne méthode : renvoie le dossier courant si on en a un.
     * Ça évite de casser le code existant.
     */
    fun get(context: Context): Uri? {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

        // priorité au nouveau champ courant
        val current = prefs.getString(KEY_CURRENT, null)
        if (current != null) return Uri.parse(current)

        // sinon on retombe sur l’ancien champ
        val old = prefs.getString(KEY_URI, null)
        return old?.let { Uri.parse(it) }
    }

    /**
     * Renvoie tous les dossiers DJ déjà autorisés.
     */
    fun getAll(context: Context): List<Uri> {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        return getAllInternal(prefs).map { Uri.parse(it) }
    }

    /**
     * Change juste le dossier courant parmi ceux déjà enregistrés.
     */
    fun setCurrent(context: Context, uri: Uri) {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_CURRENT, uri.toString())
            .apply()
    }

    /**
     * Oublie tout.
     */
    fun clear(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_URI)
            .remove(KEY_URIS)
            .remove(KEY_CURRENT)
            .apply()
    }

    // ----------------- helpers privés -----------------

    private fun getAllInternal(prefs: android.content.SharedPreferences): List<String> {
        val json = prefs.getString(KEY_URIS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val s = arr.optString(i, null)
                    if (!s.isNullOrEmpty()) add(s)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun toJsonArray(list: List<String>): JSONArray {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        return arr
    }
}