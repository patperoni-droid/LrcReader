package com.patrick.lrcreader.core.sync

import org.json.JSONArray
import org.json.JSONObject

const val SMP_SYNC_MANIFEST_SCHEMA_VERSION = 1

data class SmpSyncManifest(
    val schemaVersion: Int = SMP_SYNC_MANIFEST_SCHEMA_VERSION,
    val appVersion: String,
    val deviceId: String? = null,
    val generatedAt: Long,
    val libraryVersion: Long? = null,
    val songs: List<SmpSyncSongEntry> = emptyList(),
    val playlists: List<SmpSyncPlaylistEntry> = emptyList(),
    val families: List<SmpSyncFamilyEntry> = emptyList(),
    val globalState: SmpSyncGlobalStateEntry? = null
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("schemaVersion", schemaVersion)
            put("appVersion", appVersion)
            putNullable("deviceId", deviceId)
            put("generatedAt", generatedAt)
            putNullable("libraryVersion", libraryVersion)
            put("songs", songs.toJsonArray { it.toJson() })
            put("playlists", playlists.toJsonArray { it.toJson() })
            put("families", families.toJsonArray { it.toJson() })
            put("globalState", globalState?.toJson() ?: JSONObject.NULL)
        }
    }

    fun toJsonString(indentSpaces: Int = 2): String {
        return toJson().toString(indentSpaces.coerceAtLeast(0))
    }

    companion object {
        fun fromJson(rawJson: String): SmpSyncManifest {
            val json = JSONObject(rawJson)
            return SmpSyncManifest(
                schemaVersion = json.getInt("schemaVersion"),
                appVersion = json.getString("appVersion"),
                deviceId = json.optStringOrNull("deviceId"),
                generatedAt = json.getLong("generatedAt"),
                libraryVersion = json.optLongOrNull("libraryVersion"),
                songs = json.optJSONArray("songs").toObjects { SmpSyncSongEntry.fromJson(it) },
                playlists = json.optJSONArray("playlists").toObjects {
                    SmpSyncPlaylistEntry.fromJson(it)
                },
                families = json.optJSONArray("families").toObjects {
                    SmpSyncFamilyEntry.fromJson(it)
                },
                globalState = json.optJSONObject("globalState")?.let {
                    SmpSyncGlobalStateEntry.fromJson(it)
                }
            )
        }

        fun fromJsonOrNull(rawJson: String?): SmpSyncManifest? {
            if (rawJson.isNullOrBlank()) return null
            return runCatching { fromJson(rawJson) }.getOrNull()
        }
    }
}

data class SmpSyncSongEntry(
    val songId: String,
    val title: String,
    val updatedAt: Long? = null,
    val audioHash: String? = null,
    val lyricsHash: String? = null,
    val chordsHash: String? = null,
    val notesHash: String? = null,
    val prompterHash: String? = null,
    val timelineHash: String? = null,
    val midiHash: String? = null,
    val dmxHash: String? = null,
    val settingsHash: String? = null,
    val arrangementHash: String? = null,
    val gridHash: String? = null,
    val fullSongHash: String
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("songId", songId)
            put("title", title)
            putNullable("updatedAt", updatedAt)
            putNullable("audioHash", audioHash)
            putNullable("lyricsHash", lyricsHash)
            putNullable("chordsHash", chordsHash)
            putNullable("notesHash", notesHash)
            putNullable("prompterHash", prompterHash)
            putNullable("timelineHash", timelineHash)
            putNullable("midiHash", midiHash)
            putNullable("dmxHash", dmxHash)
            putNullable("settingsHash", settingsHash)
            putNullable("arrangementHash", arrangementHash)
            putNullable("gridHash", gridHash)
            put("fullSongHash", fullSongHash)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): SmpSyncSongEntry {
            return SmpSyncSongEntry(
                songId = json.getString("songId"),
                title = json.getString("title"),
                updatedAt = json.optLongOrNull("updatedAt"),
                audioHash = json.optStringOrNull("audioHash"),
                lyricsHash = json.optStringOrNull("lyricsHash"),
                chordsHash = json.optStringOrNull("chordsHash"),
                notesHash = json.optStringOrNull("notesHash"),
                prompterHash = json.optStringOrNull("prompterHash"),
                timelineHash = json.optStringOrNull("timelineHash"),
                midiHash = json.optStringOrNull("midiHash"),
                dmxHash = json.optStringOrNull("dmxHash"),
                settingsHash = json.optStringOrNull("settingsHash"),
                arrangementHash = json.optStringOrNull("arrangementHash"),
                gridHash = json.optStringOrNull("gridHash"),
                fullSongHash = json.getString("fullSongHash")
            )
        }
    }
}

data class SmpSyncPlaylistEntry(
    val playlistId: String? = null,
    val playlistName: String,
    val itemsHash: String,
    val groupsHash: String? = null,
    val colorsHash: String? = null,
    val fullPlaylistHash: String
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            putNullable("playlistId", playlistId)
            put("playlistName", playlistName)
            put("itemsHash", itemsHash)
            putNullable("groupsHash", groupsHash)
            putNullable("colorsHash", colorsHash)
            put("fullPlaylistHash", fullPlaylistHash)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): SmpSyncPlaylistEntry {
            return SmpSyncPlaylistEntry(
                playlistId = json.optStringOrNull("playlistId"),
                playlistName = json.getString("playlistName"),
                itemsHash = json.getString("itemsHash"),
                groupsHash = json.optStringOrNull("groupsHash"),
                colorsHash = json.optStringOrNull("colorsHash"),
                fullPlaylistHash = json.getString("fullPlaylistHash")
            )
        }
    }
}

data class SmpSyncFamilyEntry(
    val familyId: String,
    val title: String,
    val songIds: List<String>,
    val parentSongId: String? = null,
    val activeSongId: String? = null,
    val hash: String
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("familyId", familyId)
            put("title", title)
            put("songIds", songIds.toJsonArray { it })
            putNullable("parentSongId", parentSongId)
            putNullable("activeSongId", activeSongId)
            put("hash", hash)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): SmpSyncFamilyEntry {
            return SmpSyncFamilyEntry(
                familyId = json.getString("familyId"),
                title = json.getString("title"),
                songIds = json.optJSONArray("songIds").toStrings(),
                parentSongId = json.optStringOrNull("parentSongId"),
                activeSongId = json.optStringOrNull("activeSongId"),
                hash = json.getString("hash")
            )
        }
    }
}

data class SmpSyncGlobalStateEntry(
    val stateHash: String,
    val updatedAt: Long? = null
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("stateHash", stateHash)
            putNullable("updatedAt", updatedAt)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): SmpSyncGlobalStateEntry {
            return SmpSyncGlobalStateEntry(
                stateHash = json.getString("stateHash"),
                updatedAt = json.optLongOrNull("updatedAt")
            )
        }
    }
}

enum class SyncEntityType {
    SONG,
    PLAYLIST,
    FAMILY,
    GLOBAL_STATE
}

enum class SyncDiffStatus {
    ABSENT_ON_B,
    IDENTICAL,
    MODIFIED_ON_A,
    MODIFIED_ON_B,
    POSSIBLE_CONFLICT,
    PLAYLIST_DIFFERENT,
    FAMILY_DIFFERENT,
    BROKEN_REFERENCE
}

data class SyncDiff(
    val entityType: SyncEntityType,
    val entityId: String,
    val status: SyncDiffStatus,
    val title: String? = null,
    val aHash: String? = null,
    val bHash: String? = null,
    val brokenReferenceIds: List<String> = emptyList()
)

enum class SyncPlanAction {
    COPY_TO_B,
    KEEP,
    REVIEW_CONFLICT,
    UPDATE_PLAYLIST_ON_B,
    UPDATE_FAMILY_ON_B,
    REVIEW_BROKEN_REFERENCE
}

data class SyncPlanItem(
    val action: SyncPlanAction,
    val diff: SyncDiff
)

data class SyncPlan(
    val items: List<SyncPlanItem> = emptyList()
) {
    val additions: List<SyncPlanItem>
        get() = items.filter { it.diff.status == SyncDiffStatus.ABSENT_ON_B }

    val modifications: List<SyncPlanItem>
        get() = items.filter {
            it.diff.status == SyncDiffStatus.MODIFIED_ON_A ||
                it.diff.status == SyncDiffStatus.PLAYLIST_DIFFERENT ||
                it.diff.status == SyncDiffStatus.FAMILY_DIFFERENT
        }

    val conflicts: List<SyncPlanItem>
        get() = items.filter { it.diff.status == SyncDiffStatus.POSSIBLE_CONFLICT }

    val brokenReferences: List<SyncPlanItem>
        get() = items.filter { it.diff.status == SyncDiffStatus.BROKEN_REFERENCE }

    val hasConflicts: Boolean
        get() = conflicts.isNotEmpty()
}

private fun JSONObject.optStringOrNull(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return getString(key)
}

private fun JSONObject.optLongOrNull(key: String): Long? {
    if (!has(key) || isNull(key)) return null
    return getLong(key)
}

private fun JSONObject.putNullable(key: String, value: String?) {
    put(key, value ?: JSONObject.NULL)
}

private fun JSONObject.putNullable(key: String, value: Long?) {
    put(key, value ?: JSONObject.NULL)
}

private fun <T> List<T>.toJsonArray(toJsonValue: (T) -> Any): JSONArray {
    return JSONArray().apply {
        this@toJsonArray.forEach { put(toJsonValue(it)) }
    }
}

private fun JSONArray?.toStrings(): List<String> {
    if (this == null) return emptyList()
    return List(length()) { index -> getString(index) }
}

private fun <T> JSONArray?.toObjects(fromJson: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    return List(length()) { index -> fromJson(getJSONObject(index)) }
}
