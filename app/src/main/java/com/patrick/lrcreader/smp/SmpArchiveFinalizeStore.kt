package com.patrick.lrcreader.smp

import android.content.Context
import android.net.Uri
import android.util.Log
import com.patrick.lrcreader.core.StorageModePrefs
import com.patrick.lrcreader.core.WorkspaceResolver
import org.json.JSONObject

enum class SmpArchivePersistState {
    PENDING,
    SUCCESS,
    FAILED
}

data class SmpArchiveFinalizeRecord(
    val songId: String,
    val requestId: String,
    val state: SmpArchivePersistState,
    val mode: StorageModePrefs.Mode,
    val workspaceRootUri: String,
    val setupTreeUri: String?,
    val requestedAtMs: Long,
    val updatedAtMs: Long,
    val archiveUri: String? = null,
    val lastError: String? = null
) {
    fun toSnapshot(): WorkspaceResolver.Snapshot? {
        val rootUri = runCatching { Uri.parse(workspaceRootUri) }.getOrNull() ?: return null
        val setupTree = setupTreeUri?.let { raw -> runCatching { Uri.parse(raw) }.getOrNull() }
        val status = when (mode) {
            StorageModePrefs.Mode.SAF -> WorkspaceResolver.Status.READY
            StorageModePrefs.Mode.INTERNAL -> WorkspaceResolver.Status.INTERNAL_LEGACY
        }
        return WorkspaceResolver.Snapshot(
            mode = mode,
            setupTreeUri = setupTree,
            workspaceRootUri = rootUri,
            status = status,
            detail = "captured_for_smp_archive_finalize"
        )
    }
}

object SmpArchiveFinalizeStore {

    private const val TAG = "SMP_ARCHIVE_FINALIZE"
    private const val PREFS_NAME = "smp_archive_finalize_store"
    private const val KEY_SONG_IDS = "song_ids"

    fun savePending(context: Context, record: SmpArchiveFinalizeRecord) {
        saveRecord(
            context = context,
            record = record.copy(
                state = SmpArchivePersistState.PENDING,
                updatedAtMs = System.currentTimeMillis()
            )
        )
    }

    fun get(context: Context, songId: String): SmpArchiveFinalizeRecord? {
        val cleanSongId = songId.trim()
        if (cleanSongId.isEmpty()) return null
        val raw = prefs(context).getString(songKey(cleanSongId), null) ?: return null
        return decode(raw)
    }

    fun listPending(context: Context): List<SmpArchiveFinalizeRecord> {
        return songIds(context).mapNotNull { songId ->
            get(context, songId)
        }.filter { record ->
            record.state == SmpArchivePersistState.PENDING
        }.sortedBy { record ->
            record.requestedAtMs
        }
    }

    fun clear(context: Context, songId: String) {
        val cleanSongId = songId.trim()
        if (cleanSongId.isEmpty()) return
        val updatedIds = songIds(context).toMutableSet().apply { remove(cleanSongId) }
        prefs(context).edit()
            .putStringSet(KEY_SONG_IDS, updatedIds)
            .remove(songKey(cleanSongId))
            .apply()
        Log.i(TAG, "step=store_clear songId=$cleanSongId")
    }

    fun markSuccess(
        context: Context,
        songId: String,
        requestId: String,
        archiveUri: String
    ) {
        val current = get(context, songId) ?: return
        if (current.requestId != requestId) return
        saveRecord(
            context = context,
            record = current.copy(
                state = SmpArchivePersistState.SUCCESS,
                updatedAtMs = System.currentTimeMillis(),
                archiveUri = archiveUri,
                lastError = null
            )
        )
    }

    fun markFailed(
        context: Context,
        songId: String,
        requestId: String,
        reason: String?
    ) {
        val current = get(context, songId) ?: return
        if (current.requestId != requestId) return
        saveRecord(
            context = context,
            record = current.copy(
                state = SmpArchivePersistState.FAILED,
                updatedAtMs = System.currentTimeMillis(),
                lastError = reason
            )
        )
    }

    private fun saveRecord(context: Context, record: SmpArchiveFinalizeRecord) {
        val cleanSongId = record.songId.trim()
        if (cleanSongId.isEmpty()) return
        val updatedIds = songIds(context).toMutableSet().apply { add(cleanSongId) }
        prefs(context).edit()
            .putStringSet(KEY_SONG_IDS, updatedIds)
            .putString(songKey(cleanSongId), encode(record.copy(songId = cleanSongId)))
            .apply()
        Log.i(
            TAG,
            "step=store_save songId=$cleanSongId requestId=${record.requestId} state=${record.state} archiveUri=${record.archiveUri} error=${record.lastError}"
        )
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun songIds(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_SONG_IDS, emptySet()).orEmpty()

    private fun songKey(songId: String): String = "record::$songId"

    private fun encode(record: SmpArchiveFinalizeRecord): String {
        return JSONObject()
            .put("songId", record.songId)
            .put("requestId", record.requestId)
            .put("state", record.state.name)
            .put("mode", record.mode.name)
            .put("workspaceRootUri", record.workspaceRootUri)
            .put("setupTreeUri", record.setupTreeUri)
            .put("requestedAtMs", record.requestedAtMs)
            .put("updatedAtMs", record.updatedAtMs)
            .put("archiveUri", record.archiveUri)
            .put("lastError", record.lastError)
            .toString()
    }

    private fun decode(raw: String): SmpArchiveFinalizeRecord? {
        return runCatching {
            val json = JSONObject(raw)
            SmpArchiveFinalizeRecord(
                songId = json.getString("songId"),
                requestId = json.getString("requestId"),
                state = SmpArchivePersistState.valueOf(json.getString("state")),
                mode = StorageModePrefs.Mode.valueOf(json.getString("mode")),
                workspaceRootUri = json.getString("workspaceRootUri"),
                setupTreeUri = json.optString("setupTreeUri").ifBlank { null },
                requestedAtMs = json.getLong("requestedAtMs"),
                updatedAtMs = json.getLong("updatedAtMs"),
                archiveUri = json.optString("archiveUri").ifBlank { null },
                lastError = json.optString("lastError").ifBlank { null }
            )
        }.onFailure { error ->
            Log.e(TAG, "step=store_decode_failed", error)
        }.getOrNull()
    }
}
