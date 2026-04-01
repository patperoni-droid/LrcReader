package com.patrick.lrcreader.core.config

import org.json.JSONArray
import org.json.JSONObject

internal data class NotesConfigEntry(
    val id: Long,
    val title: String,
    val content: String,
    val updatedAt: Long
)

internal data class NotesScopedEntry(
    val scopeKey: String,
    val note: NotesConfigEntry
)

internal data class NotesConfigState(
    val schemaVersion: Int = SCHEMA_VERSION,
    val notesByScope: Map<String, List<NotesConfigEntry>> = emptyMap()
) {
    fun toJson(): JSONObject {
        val root = JSONObject()
        root.put("schemaVersion", schemaVersion)

        val notesObj = JSONObject()
        notesByScope.keys.sorted().forEach { scopeKey ->
            val entries = notesByScope[scopeKey].orEmpty()
                .sortedByDescending { it.updatedAt }

            val arr = JSONArray()
            entries.forEach { entry ->
                arr.put(
                    JSONObject().apply {
                        put("id", entry.id)
                        put("title", entry.title)
                        put("content", entry.content)
                        put("updatedAt", entry.updatedAt)
                    }
                )
            }
            notesObj.put(scopeKey, arr)
        }

        root.put("notes", notesObj)
        return root
    }

    fun allScopedEntries(): List<NotesScopedEntry> {
        val out = mutableListOf<NotesScopedEntry>()
        notesByScope.forEach { (scopeKey, entries) ->
            entries.forEach { entry ->
                out.add(NotesScopedEntry(scopeKey = scopeKey, note = entry))
            }
        }
        return out
    }

    fun findById(id: Long): NotesScopedEntry? {
        notesByScope.forEach { (scopeKey, entries) ->
            val found = entries.firstOrNull { it.id == id }
            if (found != null) {
                return NotesScopedEntry(scopeKey = scopeKey, note = found)
            }
        }
        return null
    }

    fun withUpsert(scopeKey: String, entry: NotesConfigEntry): NotesConfigState {
        val nextMap = notesByScope.toMutableMap()

        val previousScope = findById(entry.id)?.scopeKey
        if (previousScope != null && previousScope != scopeKey) {
            val prevEntries = nextMap[previousScope].orEmpty()
                .filterNot { it.id == entry.id }
            if (prevEntries.isEmpty()) {
                nextMap.remove(previousScope)
            } else {
                nextMap[previousScope] = prevEntries
            }
        }

        val currentEntries = nextMap[scopeKey].orEmpty().toMutableList()
        val index = currentEntries.indexOfFirst { it.id == entry.id }
        if (index >= 0) {
            currentEntries[index] = entry
        } else {
            currentEntries.add(entry)
        }

        nextMap[scopeKey] = currentEntries
            .sortedByDescending { it.updatedAt }

        return copy(
            schemaVersion = SCHEMA_VERSION,
            notesByScope = nextMap
        )
    }

    fun withMirroredUpsert(scopeKeys: List<String>, entry: NotesConfigEntry): NotesConfigState {
        val cleanedScopeKeys = scopeKeys.distinct()
        val nextMap = linkedMapOf<String, MutableList<NotesConfigEntry>>()

        notesByScope.forEach { (scopeKey, entries) ->
            val filtered = entries.filterNot { it.id == entry.id }.toMutableList()
            if (filtered.isNotEmpty()) {
                nextMap[scopeKey] = filtered
            }
        }

        cleanedScopeKeys.forEach { scopeKey ->
            val updatedEntries = nextMap[scopeKey] ?: mutableListOf()
            updatedEntries.add(entry)
            nextMap[scopeKey] = updatedEntries
                .distinctBy { it.id }
                .sortedByDescending { it.updatedAt }
                .toMutableList()
        }

        return copy(
            schemaVersion = SCHEMA_VERSION,
            notesByScope = nextMap.mapValues { it.value.toList() }
        )
    }

    fun withoutId(id: Long): NotesConfigState {
        val nextMap = linkedMapOf<String, List<NotesConfigEntry>>()

        notesByScope.forEach { (scopeKey, entries) ->
            val filtered = entries.filterNot { it.id == id }
            if (filtered.isNotEmpty()) {
                nextMap[scopeKey] = filtered
            }
        }

        return copy(
            schemaVersion = SCHEMA_VERSION,
            notesByScope = nextMap
        )
    }

    companion object {
        const val SCHEMA_VERSION = 1

        fun empty(): NotesConfigState = NotesConfigState(
            schemaVersion = SCHEMA_VERSION,
            notesByScope = emptyMap()
        )

        fun fromJson(raw: String): NotesConfigState {
            val root = JSONObject(raw)
            val schemaVersion = root.optInt("schemaVersion", SCHEMA_VERSION)
            val notesObj = root.optJSONObject("notes") ?: JSONObject()

            val notes = linkedMapOf<String, List<NotesConfigEntry>>()
            val scopeKeys = notesObj.keys().asSequence().toList().sorted()

            scopeKeys.forEach { scopeKey ->
                val arr = when (val node = notesObj.opt(scopeKey)) {
                    is JSONArray -> node
                    is JSONObject -> node.optJSONArray("items") ?: JSONArray()
                    else -> JSONArray()
                }

                val entries = mutableListOf<NotesConfigEntry>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue

                    val hasId = obj.has("id") && !obj.isNull("id")
                    if (!hasId) continue

                    entries.add(
                        NotesConfigEntry(
                            id = obj.optLong("id", 0L),
                            title = obj.optString("title", ""),
                            content = obj.optString("content", ""),
                            updatedAt = obj.optLong("updatedAt", 0L)
                        )
                    )
                }

                if (entries.isNotEmpty()) {
                    notes[scopeKey] = entries.sortedByDescending { it.updatedAt }
                }
            }

            return NotesConfigState(
                schemaVersion = schemaVersion,
                notesByScope = notes
            )
        }
    }
}
