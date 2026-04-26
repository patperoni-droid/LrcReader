package com.patrick.lrcreader.smp

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class ArrangementSegmentData(
    val id: String,
    val name: String,
    val startMs: Long,
    val endMs: Long
)

data class ArrangementData(
    val version: Int = 1,
    val name: String = "Arrangement 1",
    val sourceSongId: String,
    val updatedAt: Long = System.currentTimeMillis(),
    val segments: List<ArrangementSegmentData>,
    val structureSegmentIds: List<String>
)

object ArrangementStore {

    private const val TAG = "ArrangementStore"
    private const val TRACKS_DIR_NAME = "tracks"
    private const val FILE_NAME = "arrangement.json"

    suspend fun load(context: Context, songId: String): ArrangementData? = withContext(Dispatchers.IO) {
        val songDir = resolveSongDir(context, songId) ?: return@withContext null
        val arrangementFile = File(songDir, FILE_NAME)
        if (!arrangementFile.isFile) {
            return@withContext null
        }

        runCatching {
            fromJson(JSONObject(arrangementFile.readText(Charsets.UTF_8)))
        }.getOrElse { error ->
            Log.w(TAG, "load failed songId=${songId.trim()} path=${arrangementFile.absolutePath}", error)
            null
        }
    }

    suspend fun save(context: Context, songId: String, data: ArrangementData): Boolean = withContext(Dispatchers.IO) {
        val songDir = resolveSongDir(context, songId) ?: return@withContext false
        if (!songDir.exists() && !songDir.mkdirs()) {
            Log.w(TAG, "save mkdir failed songId=${songId.trim()} path=${songDir.absolutePath}")
            return@withContext false
        }

        val targetFile = File(songDir, FILE_NAME)
        val tmpFile = File(songDir, "$FILE_NAME.tmp")
        val backupFile = File(songDir, "$FILE_NAME.bak")
        val rawJson = toJson(data.copy(updatedAt = System.currentTimeMillis())).toString(2)

        runCatching {
            tmpFile.writeText(rawJson, Charsets.UTF_8)

            if (backupFile.exists() && !backupFile.delete()) {
                Log.w(TAG, "save delete stale backup failed path=${backupFile.absolutePath}")
            }

            if (targetFile.exists() && !targetFile.renameTo(backupFile)) {
                tmpFile.delete()
                Log.w(TAG, "save backup rename failed path=${targetFile.absolutePath}")
                return@runCatching false
            }

            val renamed = tmpFile.renameTo(targetFile)
            if (renamed) {
                if (backupFile.exists() && !backupFile.delete()) {
                    Log.w(TAG, "save delete backup failed path=${backupFile.absolutePath}")
                }
                return@runCatching true
            }

            Log.w(TAG, "save tmp rename failed path=${targetFile.absolutePath}")
            tmpFile.delete()
            if (backupFile.exists() && !backupFile.renameTo(targetFile)) {
                Log.w(TAG, "save rollback failed path=${targetFile.absolutePath}")
            }
            false
        }.getOrElse { error ->
            Log.w(TAG, "save failed songId=${songId.trim()} path=${targetFile.absolutePath}", error)
            runCatching { tmpFile.delete() }
            false
        }
    }

    private fun resolveSongDir(context: Context, songId: String): File? {
        val cleanSongId = songId.trim().takeIf { it.isNotEmpty() } ?: return null
        return File(File(context.filesDir, TRACKS_DIR_NAME), cleanSongId)
    }

    private fun fromJson(json: JSONObject): ArrangementData {
        val sourceSongId = json.optString("sourceSongId").trim().ifBlank {
            throw IllegalArgumentException("Missing sourceSongId")
        }
        val segments = json.optJSONArray("segments")
            ?.let(::segmentsFromJson)
            .orEmpty()
        val validSegmentIds = segments.map { it.id }.toSet()
        val structureSegmentIds = json.optJSONArray("structureSegmentIds")
            ?.let(::structureFromJson)
            .orEmpty()
            .filter { it in validSegmentIds }

        return ArrangementData(
            version = json.optInt("version", 1).coerceAtLeast(1),
            name = json.optString("name").trim().ifBlank { "Arrangement 1" },
            sourceSongId = sourceSongId,
            updatedAt = json.optLong("updatedAt", System.currentTimeMillis()),
            segments = segments,
            structureSegmentIds = structureSegmentIds
        )
    }

    private fun segmentsFromJson(array: JSONArray): List<ArrangementSegmentData> {
        val out = ArrayList<ArrangementSegmentData>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val id = item.optString("id").trim()
            val name = item.optString("name").trim()
            val startMs = item.optLong("startMs", -1L)
            val endMs = item.optLong("endMs", -1L)
            if (id.isBlank() || name.isBlank() || startMs < 0L || endMs <= startMs) continue
            out += ArrangementSegmentData(
                id = id,
                name = name,
                startMs = startMs,
                endMs = endMs
            )
        }
        return out
    }

    private fun structureFromJson(array: JSONArray): List<String> {
        val out = ArrayList<String>(array.length())
        for (index in 0 until array.length()) {
            val value = array.optString(index).trim()
            if (value.isNotEmpty()) {
                out += value
            }
        }
        return out
    }

    private fun toJson(data: ArrangementData): JSONObject {
        return JSONObject().apply {
            put("version", data.version.coerceAtLeast(1))
            put("name", data.name.ifBlank { "Arrangement 1" })
            put("sourceSongId", data.sourceSongId)
            put("updatedAt", data.updatedAt)
            put(
                "segments",
                JSONArray().apply {
                    data.segments.forEach { segment ->
                        put(
                            JSONObject().apply {
                                put("id", segment.id)
                                put("name", segment.name)
                                put("startMs", segment.startMs.coerceAtLeast(0L))
                                put("endMs", segment.endMs.coerceAtLeast(segment.startMs + 1L))
                            }
                        )
                    }
                }
            )
            put(
                "structureSegmentIds",
                JSONArray().apply {
                    data.structureSegmentIds.forEach { segmentId ->
                        put(segmentId)
                    }
                }
            )
        }
    }
}
