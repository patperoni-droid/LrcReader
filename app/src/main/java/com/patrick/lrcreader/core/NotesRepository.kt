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
        val updatedAt: Long,
        // id du titre texte (TextSongRepository) associé pour le prompteur
        // null = pas encore lié à un prompteur
        val prompterId: String? = null
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

                val id = o.getLong("id")
                val title = o.optString("title", "")
                val content = o.optString("content", "")
                val updatedAt = o.optLong("updatedAt", 0L)

                // champ optionnel pour compat rétro
                val prompterId: String? = if (o.has("prompterId") && !o.isNull("prompterId")) {
                    o.optString("prompterId", null)
                } else {
                    null
                }

                list.add(
                    Note(
                        id = id,
                        title = title,
                        content = content,
                        updatedAt = updatedAt,
                        prompterId = prompterId
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
                // on ne sérialise prompterId que s'il existe, pour garder un JSON propre
                if (n.prompterId != null) {
                    put("prompterId", n.prompterId)
                }
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

        // si la note existait déjà, on garde son éventuel prompterId
        val existingPrompterId = current.firstOrNull { it.id == noteId }?.prompterId

        val note = Note(
            id = noteId,
            title = title.trim(),
            content = content.trim(),
            updatedAt = now,
            prompterId = existingPrompterId
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
     * Met à jour uniquement le prompterId d'une note (lien avec le titre prompteur).
     * @return la note mise à jour, ou null si id introuvable.
     */
    fun setPrompterId(context: Context, id: Long, prompterId: String?): Note? {
        val current = getAll(context).toMutableList()
        val idx = current.indexOfFirst { it.id == id }
        if (idx < 0) return null

        val old = current[idx]
        val updated = old.copy(
            prompterId = prompterId,
            updatedAt = System.currentTimeMillis()
        )
        current[idx] = updated
        persist(context, current)
        return updated
    }
}