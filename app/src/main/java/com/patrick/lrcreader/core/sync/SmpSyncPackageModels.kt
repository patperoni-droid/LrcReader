package com.patrick.lrcreader.core.sync

import org.json.JSONArray
import org.json.JSONObject

const val SMP_SYNC_PACKAGE_SCHEMA_VERSION = 1

data class SmpSyncPackage(
    val schemaVersion: Int = SMP_SYNC_PACKAGE_SCHEMA_VERSION,
    val generatedAt: Long,
    val sourceDeviceId: String? = null,
    val items: List<SmpSyncPackageItem> = emptyList()
) {
    val itemCount: Int
        get() = items.size

    val estimatedBytes: Long?
        get() {
            var total = 0L
            items.forEach { item ->
                val size = item.estimatedBytes ?: return null
                total += size
            }
            return total
        }

    val knownEstimatedBytes: Long
        get() = items.sumOf { it.estimatedBytes ?: 0L }

    val hasCompleteSizeEstimate: Boolean
        get() = items.all { it.estimatedBytes != null }

    val fullSongCount: Int
        get() = countKind(SmpSyncPackageKind.SONG_FULL)

    val songComponentCount: Int
        get() = countKind(SmpSyncPackageKind.SONG_COMPONENT)

    val playlistStateCount: Int
        get() = countKind(SmpSyncPackageKind.PLAYLIST_STATE)

    val familyStateCount: Int
        get() = countKind(SmpSyncPackageKind.FAMILY_STATE)

    val globalStateCount: Int
        get() = countKind(SmpSyncPackageKind.GLOBAL_STATE)

    private fun countKind(kind: SmpSyncPackageKind): Int {
        return items.count { it.kind == kind }
    }

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("schemaVersion", schemaVersion)
            put("generatedAt", generatedAt)
            put("sourceDeviceId", sourceDeviceId ?: JSONObject.NULL)
            put(
                "items",
                JSONArray().apply {
                    items.forEach { put(it.toJson()) }
                }
            )
        }
    }

    fun toJsonString(indentSpaces: Int = 2): String {
        return toJson().toString(indentSpaces.coerceAtLeast(0))
    }

    companion object {
        fun fromJson(rawJson: String): SmpSyncPackage {
            val json = JSONObject(rawJson)
            val itemsJson = json.optJSONArray("items") ?: JSONArray()
            val items = buildList {
                for (index in 0 until itemsJson.length()) {
                    itemsJson.optJSONObject(index)?.let { itemJson ->
                        add(SmpSyncPackageItem.fromJson(itemJson))
                    }
                }
            }
            return SmpSyncPackage(
                schemaVersion = json.optInt("schemaVersion", SMP_SYNC_PACKAGE_SCHEMA_VERSION),
                generatedAt = json.optLong("generatedAt", 0L),
                sourceDeviceId = json.optStringOrNull("sourceDeviceId"),
                items = items
            )
        }

        fun fromJsonOrNull(rawJson: String?): SmpSyncPackage? {
            if (rawJson.isNullOrBlank()) return null
            return runCatching { fromJson(rawJson) }.getOrNull()
        }
    }
}

data class SmpSyncPackageItem(
    val kind: SmpSyncPackageKind,
    val entityId: String,
    val title: String? = null,
    val sourceHash: String? = null,
    val estimatedBytes: Long? = null,
    val componentName: String? = null,
    val contentEntry: String? = null,
    val diffStatus: SyncDiffStatus? = null
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("kind", kind.name)
            put("entityId", entityId)
            put("title", title ?: JSONObject.NULL)
            put("sourceHash", sourceHash ?: JSONObject.NULL)
            put("estimatedBytes", estimatedBytes ?: JSONObject.NULL)
            put("componentName", componentName ?: JSONObject.NULL)
            put("contentEntry", contentEntry ?: JSONObject.NULL)
            put("diffStatus", diffStatus?.name ?: JSONObject.NULL)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): SmpSyncPackageItem {
            return SmpSyncPackageItem(
                kind = runCatching {
                    SmpSyncPackageKind.valueOf(json.getString("kind"))
                }.getOrDefault(SmpSyncPackageKind.SONG_FULL),
                entityId = json.optString("entityId").trim(),
                title = json.optStringOrNull("title"),
                sourceHash = json.optStringOrNull("sourceHash"),
                estimatedBytes = json.optLongOrNull("estimatedBytes"),
                componentName = json.optStringOrNull("componentName"),
                contentEntry = json.optStringOrNull("contentEntry"),
                diffStatus = json.optStringOrNull("diffStatus")?.let { raw ->
                    runCatching { SyncDiffStatus.valueOf(raw) }.getOrNull()
                }
            )
        }
    }
}

enum class SmpSyncPackageKind {
    SONG_FULL,
    SONG_COMPONENT,
    PLAYLIST_STATE,
    FAMILY_STATE,
    GLOBAL_STATE
}

private fun JSONObject.optStringOrNull(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return optString(key).trim().takeIf { it.isNotEmpty() }
}

private fun JSONObject.optLongOrNull(key: String): Long? {
    if (!has(key) || isNull(key)) return null
    return runCatching { getLong(key) }.getOrNull()
}
