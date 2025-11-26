package com.patrick.lrcreader.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stockage simple des notes en JSON dans SharedPreferences.
 */
object NotesRepository {

    private const val PREFS_NAME = "notes_repo"
    private const val KEY_NOTES = "notes"

    data class Note(
        val id: Long,
        val title: String,
        val content: String,
        val updatedAt: Long
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAll(context: Context): List<Note> {
        val json = prefs(context).getString(KEY_NOTES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<Note>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    Note(
                        // ⚠️ optLong pour supporter les anciennes notes sans id
                        id = o.optLong("id", 0L),
                        title = o.optString("title", ""),
                        content = o.optString("content", ""),
                        updatedAt = o.optLong("updatedAt", 0L)
                    )
                )
            }
            // Les plus récentes en haut
            list.sortedByDescending { it.updatedAt }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun persist(context: Context, notes: List<Note>) {
        val arr = JSONArray()
        notes.forEach { n ->
            val o = JSONObject().apply {
                put("id", n.id)
                put("title", n.title)
                put("content", n.content)
                put("updatedAt", n.updatedAt)
            }
            arr.put(o)
        }
        prefs(context).edit()
            .putString(KEY_NOTES, arr.toString())
            .apply()
    }

    /**
     * Crée ou met à jour une note.
     * @return l'id de la note.
     */
    fun upsert(context: Context, id: Long?, title: String, content: String): Long {
        val now = System.currentTimeMillis()
        val current = getAll(context).toMutableList()

        val noteId = id ?: now
        val idx = current.indexOfFirst { it.id == noteId }

        val note = Note(
            id = noteId,
            title = title.trim(),
            content = content.trim(),
            updatedAt = now
        )

        if (idx >= 0) {
            current[idx] = note
        } else {
            current.add(0, note)
        }

        persist(context, current)
        return noteId
    }

    fun delete(context: Context, id: Long) {
        val filtered = getAll(context).filterNot { it.id == id }
        persist(context, filtered)
    }

    fun get(context: Context, id: Long): Note? =
        getAll(context).firstOrNull { it.id == id }

    /**
     * S'assure qu'une note a un id valide (>0).
     * Si ce n'est pas le cas, on lui en recrée un, on persiste, et on renvoie la version fixée.
     */
    fun ensureValidId(context: Context, note: Note): Note {
        if (note.id > 0) return note  // ID déjà OK

        val newId = System.currentTimeMillis()

        val fixed = Note(
            id = newId,
            title = note.title,
            content = note.content,
            updatedAt = System.currentTimeMillis()
        )

        val all = getAll(context).toMutableList()
        // On essaie de retrouver la note via son contenu (fallback pour les vieux enregistrements)
        val index = all.indexOfFirst {
            it.id == 0L &&
                    it.title == note.title &&
                    it.content == note.content
        }

        if (index >= 0) {
            all[index] = fixed
        } else {
            // au cas où, on l'ajoute
            all.add(0, fixed)
        }

        // On ré-écrit tout proprement
        val arr = JSONArray()
        all.forEach { n ->
            val o = JSONObject().apply {
                put("id", n.id)
                put("title", n.title)
                put("content", n.content)
                put("updatedAt", n.updatedAt)
            }
            arr.put(o)
        }
        prefs(context).edit().putString(KEY_NOTES, arr.toString()).apply()

        return fixed
    }
}