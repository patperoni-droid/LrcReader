package com.patrick.lrcreader.core

import android.content.Context
import android.util.Log
import com.patrick.lrcreader.core.config.NotesConfigEntry
import com.patrick.lrcreader.core.config.NotesConfigStore
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Stockage simple des notes en JSON dans SharedPreferences.
 */
object NotesRepository {

    private const val TAG = "NotesRepository"
    private const val PREFS_NAME = "notes_repo"
    private const val KEY_NOTES = "notes"
    private const val KEY_SCOPE_KEY = "scopeKey"
    private const val GLOBAL_SCOPE_KEY = NotesConfigStore.GLOBAL_SCOPE_KEY

    private val mutex = Mutex()

    data class Note(
        val id: Long,
        val title: String,
        val content: String,
        val updatedAt: Long
    )

    private data class LegacyRecord(
        val note: Note,
        val scopeKeyHint: String?
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAll(context: Context): List<Note> {
        return withLockBlocking {
            ensurePortableInitialized(context)

            val fromJson = runCatching { NotesConfigStore.getAll(context) }.getOrElse {
                Log.w(TAG, "getAll: JSON read failed, fallback prefs", it)
                emptyList()
            }

            val legacy = readLegacyRecords(context)
            val merged = LinkedHashMap<Long, Note>()

            fromJson.forEach { scoped ->
                merged[scoped.note.id] = scoped.note.toPublicNote()
            }

            legacy.forEach { record ->
                if (!merged.containsKey(record.note.id)) {
                    merged[record.note.id] = record.note
                }
            }

            merged.values.sortedByDescending { it.updatedAt }
        }
    }

    /**
     * Crée ou met à jour une note.
     * @return l'id de la note.
     */
    fun upsert(context: Context, id: Long?, title: String, content: String): Long {
        return withLockBlocking {
            ensurePortableInitialized(context)

            val now = System.currentTimeMillis()
            val noteId = id ?: now
            val next = Note(
                id = noteId,
                title = title.trim(),
                content = content.trim(),
                updatedAt = now
            )

            val legacy = readLegacyRecords(context).toMutableList()
            val existingLegacyIdx = legacy.indexOfFirst { it.note.id == noteId }
            val existingLegacy = legacy.getOrNull(existingLegacyIdx)

            val scopeHint = existingLegacy?.scopeKeyHint
            val updatedLegacyRecord = LegacyRecord(
                note = next,
                scopeKeyHint = scopeHint
            )

            if (existingLegacyIdx >= 0) {
                legacy[existingLegacyIdx] = updatedLegacyRecord
            } else {
                legacy.add(0, updatedLegacyRecord)
            }
            persistLegacyRecords(context, legacy)

            val existingJson = runCatching { NotesConfigStore.getById(context, noteId) }.getOrNull()
            val resolvedScope = resolveScopeForWrite(
                requestedId = id,
                existingLegacy = existingLegacy,
                existingJsonScope = existingJson?.scopeKey
            )

            if (resolvedScope != null) {
                val jsonOk = runCatching {
                    NotesConfigStore.upsert(context, resolvedScope, next.toConfigEntry())
                }.getOrDefault(false)
                if (!jsonOk) {
                    Log.w(TAG, "upsert: JSON write skipped/failed, prefs fallback only id=$noteId")
                }
            } else {
                Log.w(TAG, "upsert: JSON skipped, scope conversion failed id=$noteId")
            }

            noteId
        }
    }

    fun delete(context: Context, id: Long) {
        withLockBlocking {
            ensurePortableInitialized(context)

            val filtered = readLegacyRecords(context)
                .filterNot { it.note.id == id }
            persistLegacyRecords(context, filtered)

            val jsonOk = runCatching {
                NotesConfigStore.delete(context, id)
            }.getOrDefault(false)
            if (!jsonOk) {
                Log.w(TAG, "delete: JSON delete skipped/failed, prefs fallback only id=$id")
            }
        }
    }

    fun clearAll(context: Context) {
        withLockBlocking {
            ensurePortableInitialized(context)
            prefs(context).edit().remove(KEY_NOTES).apply()
            runCatching {
                NotesConfigStore.getAll(context).forEach { scoped ->
                    NotesConfigStore.delete(context, scoped.note.id)
                }
            }
                .onFailure { Log.w(TAG, "clearAll: JSON clear failed", it) }
        }
    }

    fun get(context: Context, id: Long): Note? =
        withLockBlocking {
            ensurePortableInitialized(context)

            val fromJson = runCatching {
                NotesConfigStore.getById(context, id)?.note?.toPublicNote()
            }.getOrNull()
            if (fromJson != null) return@withLockBlocking fromJson

            readLegacyRecords(context).firstOrNull { it.note.id == id }?.note
        }

    /**
     * S'assure qu'une note a un id valide (>0).
     * Si ce n'est pas le cas, on lui en recrée un, on persiste, et on renvoie la version fixée.
     */
    fun ensureValidId(context: Context, note: Note): Note {
        if (note.id > 0) return note

        return withLockBlocking {
            ensurePortableInitialized(context)

            val newId = System.currentTimeMillis()
            val fixed = Note(
                id = newId,
                title = note.title,
                content = note.content,
                updatedAt = System.currentTimeMillis()
            )

            val legacy = readLegacyRecords(context).toMutableList()
            val legacyIdx = legacy.indexOfFirst {
                it.note.id == 0L &&
                    it.note.title == note.title &&
                    it.note.content == note.content
            }

            val legacyScopeHint = legacy.getOrNull(legacyIdx)?.scopeKeyHint
            if (legacyIdx >= 0) {
                legacy[legacyIdx] = LegacyRecord(
                    note = fixed,
                    scopeKeyHint = legacyScopeHint
                )
            } else {
                legacy.add(0, LegacyRecord(fixed, null))
            }
            persistLegacyRecords(context, legacy)

            val oldJson = runCatching { NotesConfigStore.getById(context, 0L) }.getOrNull()
            if (oldJson != null) {
                runCatching { NotesConfigStore.delete(context, 0L) }
            }

            val resolvedScope = when {
                oldJson != null && NotesConfigStore.isValidScopeKey(oldJson.scopeKey) -> {
                    NotesConfigStore.normalizeScopeKey(oldJson.scopeKey)
                }

                legacyScopeHint != null -> NotesConfigStore.normalizeScopeKey(legacyScopeHint)
                else -> GLOBAL_SCOPE_KEY
            }

            if (resolvedScope != null) {
                val jsonOk = runCatching {
                    NotesConfigStore.upsert(context, resolvedScope, fixed.toConfigEntry())
                }.getOrDefault(false)
                if (!jsonOk) {
                    Log.w(TAG, "ensureValidId: JSON write skipped/failed, prefs fallback only id=${fixed.id}")
                }
            } else {
                Log.w(TAG, "ensureValidId: JSON skipped, scope conversion failed id=${fixed.id}")
            }

            fixed
        }
    }

    private fun ensurePortableInitialized(context: Context) {
        runCatching { NotesConfigStore.ensureInitialized(context) }
    }

    private fun readLegacyRecords(context: Context): List<LegacyRecord> {
        val raw = prefs(context).getString(KEY_NOTES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val out = mutableListOf<LegacyRecord>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                out.add(
                    LegacyRecord(
                        note = Note(
                            id = o.optLong("id", 0L),
                            title = o.optString("title", ""),
                            content = o.optString("content", ""),
                            updatedAt = o.optLong("updatedAt", 0L)
                        ),
                        scopeKeyHint = if (o.has(KEY_SCOPE_KEY) && !o.isNull(KEY_SCOPE_KEY)) {
                            o.optString(KEY_SCOPE_KEY, "")
                        } else {
                            null
                        }
                    )
                )
            }
            out.sortedByDescending { it.note.updatedAt }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun persistLegacyRecords(context: Context, records: List<LegacyRecord>) {
        val arr = JSONArray()
        records.forEach { record ->
            val o = JSONObject().apply {
                put("id", record.note.id)
                put("title", record.note.title)
                put("content", record.note.content)
                put("updatedAt", record.note.updatedAt)
                val normalizedScope = NotesConfigStore.normalizeScopeKey(record.scopeKeyHint)
                if (normalizedScope != null) {
                    put(KEY_SCOPE_KEY, normalizedScope)
                }
            }
            arr.put(o)
        }
        prefs(context).edit()
            .putString(KEY_NOTES, arr.toString())
            .apply()
    }

    private fun resolveScopeForWrite(
        requestedId: Long?,
        existingLegacy: LegacyRecord?,
        existingJsonScope: String?
    ): String? {
        val normalizedJsonScope = NotesConfigStore.normalizeScopeKey(existingJsonScope)
        if (normalizedJsonScope != null) return normalizedJsonScope

        if (existingLegacy != null) {
            if (existingLegacy.scopeKeyHint != null) {
                return NotesConfigStore.normalizeScopeKey(existingLegacy.scopeKeyHint)
            }
            return GLOBAL_SCOPE_KEY
        }

        return if (requestedId == null) {
            GLOBAL_SCOPE_KEY
        } else {
            null
        }
    }

    private fun Note.toConfigEntry(): NotesConfigEntry {
        return NotesConfigEntry(
            id = id,
            title = title,
            content = content,
            updatedAt = updatedAt
        )
    }

    private fun NotesConfigEntry.toPublicNote(): Note {
        return Note(
            id = id,
            title = title,
            content = content,
            updatedAt = updatedAt
        )
    }

    private fun <T> withLockBlocking(block: () -> T): T {
        return runBlocking {
            mutex.withLock {
                block()
            }
        }
    }
}
