package com.patrick.lrcreader.smp

import android.content.Context
import android.util.Log
import java.io.File

object SmpMetaStore {

    const val META_FILE_NAME = "meta.json"
    private const val TRACKS_DIR_NAME = "tracks"
    private const val TAG = "SmpMetaStore"

    fun read(songDir: File): SmpMeta? {
        val metaFile = File(songDir, META_FILE_NAME)
        if (!metaFile.isFile) {
            return null
        }

        return runCatching {
            SmpMeta.fromJsonOrNull(metaFile.readText(Charsets.UTF_8))
        }.getOrElse { error ->
            Log.e(TAG, "Lecture meta.json impossible: ${metaFile.absolutePath}", error)
            null
        }
    }

    fun read(context: Context, songId: String): SmpMeta? {
        val tracksDir = File(context.filesDir, TRACKS_DIR_NAME)
        return read(File(tracksDir, songId))
    }

    fun write(songDir: File, meta: SmpMeta): Boolean {
        val metaFile = File(songDir, META_FILE_NAME)
        val tmpFile = File(songDir, "$META_FILE_NAME.tmp")
        val rawJson = meta.toJsonString()

        return runCatching {
            songDir.mkdirs()
            tmpFile.writeText(rawJson, Charsets.UTF_8)
            if (metaFile.exists() && !metaFile.delete()) {
                Log.w(TAG, "Suppression meta.json impossible: ${metaFile.absolutePath}")
            }
            if (!tmpFile.renameTo(metaFile)) {
                tmpFile.writeText(rawJson, Charsets.UTF_8)
                metaFile.writeText(rawJson, Charsets.UTF_8)
                tmpFile.delete()
            }
            true
        }.getOrElse { error ->
            Log.e(TAG, "Ecriture meta.json impossible: ${metaFile.absolutePath}", error)
            runCatching { tmpFile.delete() }
            false
        }
    }

    fun write(songUnit: SongUnit): Boolean {
        val storageFolder = songUnit.storageFolder ?: return false
        return write(
            songDir = File(storageFolder),
            meta = SmpMeta.fromSongUnit(songUnit)
        )
    }
}
