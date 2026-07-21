package com.patrick.lrcreader.smp

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID

object ArrangementVariantStore {
    private const val TRACKS_DIR_NAME = "tracks"
    private const val CONFIG_FILE_NAME = "config.json"
    private const val ARRANGEMENT_FILE_NAME = "arrangement.json"

    suspend fun create(
        context: Context,
        title: String,
        sourceSongId: String,
        arrangement: ArrangementData
    ): Result<SongUnit> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanTitle = title.trim().takeIf(String::isNotEmpty)
                ?: throw IllegalArgumentException("Arrangement variant title is empty")
            val cleanSourceSongId = sourceSongId.trim().takeIf(String::isNotEmpty)
                ?: throw IllegalArgumentException("Arrangement variant sourceSongId is empty")
            require(arrangement.entries.isNotEmpty() || arrangement.structureSegmentIds.isNotEmpty()) {
                "Arrangement variant structure is empty"
            }

            val tracksRoot = File(context.filesDir, TRACKS_DIR_NAME).apply { mkdirs() }
            require(tracksRoot.isDirectory) { "Runtime tracks folder is unavailable" }
            val sourceSong = SmpLibraryScanner(context).findSongById(cleanSourceSongId)
                ?: throw IOException("Arrangement variant source song is missing")
            require(!sourceSong.audioPath.isNullOrBlank()) {
                "Arrangement variant source audio is missing"
            }

            val variantId = "arrangement_${UUID.randomUUID()}"
            val targetDir = File(tracksRoot, variantId)
            val tempDir = File(tracksRoot, ".$variantId.tmp")
            if (!tempDir.mkdirs()) {
                throw IOException("Unable to create Arrangement variant temporary folder")
            }

            try {
                val variantData = arrangement.copy(
                    name = cleanTitle,
                    sourceSongId = cleanSourceSongId,
                    updatedAt = System.currentTimeMillis()
                )
                File(tempDir, CONFIG_FILE_NAME).writeText(
                    JSONObject()
                        .put("version", 1)
                        .put("id", variantId)
                        .put("title", cleanTitle)
                        .put(
                            "arrangementVariant",
                            JSONObject()
                                .put("sourceSongId", cleanSourceSongId)
                        )
                        .toString(2),
                    Charsets.UTF_8
                )
                File(tempDir, ARRANGEMENT_FILE_NAME).writeText(
                    ArrangementJsonCodec.encode(variantData).toString(2),
                    Charsets.UTF_8
                )
                if (!tempDir.renameTo(targetDir)) {
                    throw IOException("Unable to publish Arrangement variant")
                }
            } catch (error: Throwable) {
                tempDir.deleteRecursively()
                throw error
            }

            SmpLibraryScanner(context).findSongById(variantId)
                ?: throw IOException("Arrangement variant is not visible in the Library")
        }
    }
}
