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

    internal data class RestoreResult(
        val restoredVariantIds: List<String>,
        val preservedVariantIds: List<String>
    )

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

    suspend fun update(
        context: Context,
        variantId: String,
        title: String,
        sourceSongId: String,
        arrangement: ArrangementData
    ): Result<SongUnit> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanVariantId = variantId.trim().takeIf(String::isNotEmpty)
                ?: throw IllegalArgumentException("Arrangement variant id is empty")
            val cleanTitle = title.trim().takeIf(String::isNotEmpty)
                ?: throw IllegalArgumentException("Arrangement variant title is empty")
            val cleanSourceSongId = sourceSongId.trim().takeIf(String::isNotEmpty)
                ?: throw IllegalArgumentException("Arrangement variant sourceSongId is empty")
            require(cleanVariantId != cleanSourceSongId) {
                "Arrangement variant id matches its source"
            }
            require(arrangement.entries.isNotEmpty() || arrangement.structureSegmentIds.isNotEmpty()) {
                "Arrangement variant structure is empty"
            }

            val scanner = SmpLibraryScanner(context)
            val existingVariant = scanner.findSongById(cleanVariantId)
                ?: throw IOException("Arrangement variant is missing")
            require(existingVariant.arrangementSourceSongId == cleanSourceSongId) {
                "Arrangement variant source does not match"
            }
            val sourceSong = scanner.findSongById(cleanSourceSongId)
                ?: throw IOException("Arrangement variant source song is missing")
            require(!sourceSong.audioPath.isNullOrBlank() && File(sourceSong.audioPath).isFile) {
                "Arrangement variant source audio is missing"
            }

            val tracksRoot = File(context.filesDir, TRACKS_DIR_NAME)
            val targetDir = File(tracksRoot, cleanVariantId)
            require(targetDir.isDirectory) { "Arrangement variant runtime folder is missing" }
            val tempDir = File(
                tracksRoot,
                ".update_${cleanVariantId}_${UUID.randomUUID().toString().take(8)}"
            )
            val backupDir = File(
                tracksRoot,
                ".backup_${cleanVariantId}_${UUID.randomUUID().toString().take(8)}"
            )
            require(tempDir.mkdirs()) { "Unable to prepare Arrangement variant update" }

            try {
                writeVariantFiles(
                    targetDir = tempDir,
                    variantId = cleanVariantId,
                    title = cleanTitle,
                    sourceSongId = cleanSourceSongId,
                    arrangement = arrangement.copy(
                        name = cleanTitle,
                        sourceSongId = cleanSourceSongId,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                if (!targetDir.renameTo(backupDir)) {
                    throw IOException("Unable to preserve Arrangement variant before update")
                }
                if (!tempDir.renameTo(targetDir)) {
                    backupDir.renameTo(targetDir)
                    throw IOException("Unable to publish Arrangement variant update")
                }
                backupDir.deleteRecursively()
            } catch (error: Throwable) {
                tempDir.deleteRecursively()
                if (!targetDir.exists() && backupDir.exists()) {
                    backupDir.renameTo(targetDir)
                }
                throw error
            }

            scanner.findSongById(cleanVariantId)
                ?: throw IOException("Updated Arrangement variant is not visible in the Library")
        }
    }

    internal fun restoreFromArchive(
        context: Context,
        sourceSong: SongUnit,
        archive: ArrangementVariantsArchive,
        replaceExisting: Boolean
    ): Result<RestoreResult> = runCatching {
        require(sourceSong.id.isNotBlank()) { "Arrangement variant parent id is empty" }
        require(archive.sourceSongId == sourceSong.id) {
            "Arrangement variants archive does not match its runtime parent"
        }
        require(!sourceSong.audioPath.isNullOrBlank() && File(sourceSong.audioPath).isFile) {
            "Arrangement variant parent audio is missing"
        }

        val tracksRoot = File(context.filesDir, TRACKS_DIR_NAME)
        require(tracksRoot.isDirectory || tracksRoot.mkdirs()) {
            "Runtime tracks folder is unavailable"
        }

        data class PendingVariant(
            val id: String,
            val targetDir: File,
            val tempDir: File,
            val backupDir: File
        )

        val pending = mutableListOf<PendingVariant>()
        val preserved = mutableListOf<String>()
        archive.variants.forEach { variant ->
            val targetDir = File(tracksRoot, variant.id)
            if (targetDir.exists() && !replaceExisting) {
                preserved += variant.id
                return@forEach
            }
            if (targetDir.exists()) {
                val existingArrangement = File(targetDir, ARRANGEMENT_FILE_NAME)
                    .takeIf(File::isFile)
                    ?.let { file ->
                        runCatching {
                            ArrangementJsonCodec.decode(JSONObject(file.readText(Charsets.UTF_8)))
                        }.getOrNull()
                    }
                require(
                    existingArrangement != null &&
                        existingArrangement.sourceSongId.trim() != variant.id
                ) {
                    "Arrangement variant id conflicts with another runtime song: ${variant.id}"
                }
            }

            val tempDir = File(tracksRoot, ".restore_${variant.id}_${UUID.randomUUID().toString().take(8)}")
            val backupDir = File(tracksRoot, ".backup_${variant.id}_${UUID.randomUUID().toString().take(8)}")
            require(tempDir.mkdirs()) { "Unable to prepare Arrangement variant restore" }
            try {
                val normalizedArrangement = variant.arrangement.copy(
                    name = variant.title,
                    sourceSongId = sourceSong.id
                )
                writeVariantFiles(
                    targetDir = tempDir,
                    variantId = variant.id,
                    title = variant.title,
                    sourceSongId = sourceSong.id,
                    arrangement = normalizedArrangement
                )
                pending += PendingVariant(
                    id = variant.id,
                    targetDir = targetDir,
                    tempDir = tempDir,
                    backupDir = backupDir
                )
            } catch (error: Throwable) {
                tempDir.deleteRecursively()
                throw error
            }
        }

        val published = mutableListOf<PendingVariant>()
        try {
            pending.forEach { item ->
                if (item.targetDir.exists() && !item.targetDir.renameTo(item.backupDir)) {
                    throw IOException("Unable to preserve existing Arrangement variant: ${item.id}")
                }
                if (!item.tempDir.renameTo(item.targetDir)) {
                    if (item.backupDir.exists()) {
                        item.backupDir.renameTo(item.targetDir)
                    }
                    throw IOException("Unable to publish restored Arrangement variant: ${item.id}")
                }
                published += item
            }
        } catch (error: Throwable) {
            published.asReversed().forEach { item ->
                item.targetDir.deleteRecursively()
                if (item.backupDir.exists()) {
                    item.backupDir.renameTo(item.targetDir)
                }
            }
            pending.forEach { item -> item.tempDir.deleteRecursively() }
            throw error
        }

        published.forEach { item -> item.backupDir.deleteRecursively() }
        RestoreResult(
            restoredVariantIds = published.map(PendingVariant::id),
            preservedVariantIds = preserved
        )
    }

    private fun writeVariantFiles(
        targetDir: File,
        variantId: String,
        title: String,
        sourceSongId: String,
        arrangement: ArrangementData
    ) {
        File(targetDir, CONFIG_FILE_NAME).writeText(
            JSONObject()
                .put("version", 1)
                .put("id", variantId)
                .put("title", title)
                .put(
                    "arrangementVariant",
                    JSONObject().put("sourceSongId", sourceSongId)
                )
                .toString(2),
            Charsets.UTF_8
        )
        File(targetDir, ARRANGEMENT_FILE_NAME).writeText(
            ArrangementJsonCodec.encode(arrangement).toString(2),
            Charsets.UTF_8
        )
    }
}
