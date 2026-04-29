package com.patrick.lrcreader.smp

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

data class GridSetupData(
    val tempoBpm: Double?,
    val syncPointMs: Long?,
    val inMs: Long? = null,
    val outMs: Long? = null,
    val timeSignatureNumerator: Int = 4,
    val timeSignatureDenominator: Int = 4
)

object GridSetupStore {

    private const val TAG = "GridSetupStore"
    private const val TRACKS_DIR_NAME = "tracks"
    private const val FILE_NAME = "grid.json"

    suspend fun load(context: Context, songId: String): GridSetupData? = withContext(Dispatchers.IO) {
        val songDir = resolveSongDir(context, songId) ?: return@withContext null
        val gridFile = File(songDir, FILE_NAME)
        if (!gridFile.isFile) {
            return@withContext null
        }

        runCatching {
            val rawJson = gridFile.readText(Charsets.UTF_8)
            fromJson(JSONObject(rawJson))
        }.getOrElse { error ->
            Log.w(TAG, "load failed songId=${songId.trim()} path=${gridFile.absolutePath}", error)
            null
        }
    }

    suspend fun save(context: Context, songId: String, data: GridSetupData): Boolean = withContext(Dispatchers.IO) {
        val songDir = resolveSongDir(context, songId) ?: return@withContext false
        if (!songDir.exists() && !songDir.mkdirs()) {
            Log.w(TAG, "save mkdir failed songId=${songId.trim()} path=${songDir.absolutePath}")
            return@withContext false
        }

        val targetFile = File(songDir, FILE_NAME)
        val tmpFile = File(songDir, "$FILE_NAME.tmp")
        val backupFile = File(songDir, "$FILE_NAME.bak")
        val rawJson = toJson(data).toString(2)

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

    private fun fromJson(json: JSONObject): GridSetupData {
        val numerator = json.optInt("timeSignatureNumerator", 4).coerceAtLeast(1)
        val denominator = json.optInt("timeSignatureDenominator", 4).coerceAtLeast(1)
        val tempoBpm = json.takeIf { it.has("tempoBpm") && !it.isNull("tempoBpm") }
            ?.optDouble("tempoBpm")
            ?.takeIf { it.isFinite() && it > 0.0 }
        val syncPointMs = json.takeIf { it.has("syncPointMs") && !it.isNull("syncPointMs") }
            ?.optLong("syncPointMs")
            ?.takeIf { it >= 0L }
        val inMs = json.takeIf { it.has("inMs") && !it.isNull("inMs") }
            ?.optLong("inMs")
            ?.takeIf { it >= 0L }
        val outMs = json.takeIf { it.has("outMs") && !it.isNull("outMs") }
            ?.optLong("outMs")
            ?.takeIf { it >= 0L }

        return GridSetupData(
            tempoBpm = tempoBpm,
            syncPointMs = syncPointMs,
            inMs = inMs,
            outMs = outMs,
            timeSignatureNumerator = numerator,
            timeSignatureDenominator = denominator
        )
    }

    private fun toJson(data: GridSetupData): JSONObject {
        return JSONObject().apply {
            if (data.tempoBpm != null) {
                put("tempoBpm", data.tempoBpm)
            } else {
                put("tempoBpm", JSONObject.NULL)
            }
            if (data.syncPointMs != null) {
                put("syncPointMs", data.syncPointMs)
            } else {
                put("syncPointMs", JSONObject.NULL)
            }
            if (data.inMs != null) {
                put("inMs", data.inMs)
            } else {
                put("inMs", JSONObject.NULL)
            }
            if (data.outMs != null) {
                put("outMs", data.outMs)
            } else {
                put("outMs", JSONObject.NULL)
            }
            put("timeSignatureNumerator", data.timeSignatureNumerator.coerceAtLeast(1))
            put("timeSignatureDenominator", data.timeSignatureDenominator.coerceAtLeast(1))
        }
    }
}
