package com.patrick.lrcreader.smp

import android.content.Context
import com.patrick.lrcreader.core.LrcStorage
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
    private const val LYRICS_FILE_NAME = "lyrics.lrc"
    private const val CHORDS_FILE_NAME = "chords.lrc"
    private const val GRID_FILE_NAME = "grid.json"
    private val PROMPTER_FILE_NAMES = listOf(
        "prompteur.txt",
        "prompteur.json",
        "prompter.txt",
        "prompter.json"
    )

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
            val initialPlayback = SmpVariantPlayback.initialProfileFromParent(context, sourceSong)
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
                        .put("playback", SmpVariantPlayback.encode(initialPlayback))
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
                    ),
                    existingVariantDir = targetDir
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
            val backupDir: File,
            val customTitle: SmpConfig.CustomTitleContract?
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
                validateExistingVariantParent(
                    existingArrangement = existingArrangement,
                    variantId = variant.id,
                    expectedSourceSongId = sourceSong.id
                )
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
                    arrangement = normalizedArrangement,
                    existingVariantDir = targetDir.takeIf(File::isDirectory),
                    archivedLyrics = variant.lyrics,
                    archivedChords = variant.chords,
                    archivedLyricsLineColors = variant.lyricsLineColors,
                    archivedTimeline = variant.timeline,
                    archivedAnnotations = variant.annotations,
                    archivedMidiCues = variant.midiCues,
                    archivedDmxCues = variant.dmxCues,
                    archivedGrid = variant.grid,
                    archivedPrompter = variant.prompter,
                    archivedLyricsEditorRaw = variant.lyricsEditorRaw,
                    archivedPlayback = variant.playback
                )
                pending += PendingVariant(
                    id = variant.id,
                    targetDir = targetDir,
                    tempDir = tempDir,
                    backupDir = backupDir,
                    customTitle = variant.customTitle
                )
            } catch (error: Throwable) {
                tempDir.deleteRecursively()
                throw error
            }
        }

        val published = mutableListOf<PendingVariant>()
        val previousCustomTitles = mutableListOf<Pair<String, SmpConfig.CustomTitleContract>>()
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
            published.forEach { item ->
                val incomingCustomTitle = item.customTitle ?: return@forEach
                val previousCustomTitle = captureCustomTitleContract(context, item.id)
                if (!applyCustomTitleContract(context, item.id, incomingCustomTitle)) {
                    throw IOException("Unable to restore Arrangement variant custom title: ${item.id}")
                }
                previousCustomTitles += item.id to previousCustomTitle
            }
        } catch (error: Throwable) {
            previousCustomTitles.asReversed().forEach { (songId, previousCustomTitle) ->
                if (!applyCustomTitleContract(context, songId, previousCustomTitle)) {
                    error.addSuppressed(
                        IOException("Unable to rollback Arrangement variant custom title: $songId")
                    )
                }
            }
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

    internal fun validateExistingVariantParent(
        existingArrangement: ArrangementData?,
        variantId: String,
        expectedSourceSongId: String
    ) {
        val existingSourceSongId = existingArrangement
            ?.sourceSongId
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: throw IllegalArgumentException(
                "Arrangement variant id conflicts with another runtime song: $variantId"
            )
        require(existingSourceSongId == expectedSourceSongId) {
            "Arrangement variant parent conflict: variantId=$variantId " +
                "existingParent=$existingSourceSongId incomingParent=$expectedSourceSongId"
        }
    }

    internal fun writeVariantFiles(
        targetDir: File,
        variantId: String,
        title: String,
        sourceSongId: String,
        arrangement: ArrangementData,
        existingVariantDir: File? = null,
        archivedLyrics: String? = null,
        archivedChords: String? = null,
        archivedLyricsLineColors: Map<String, Int>? = null,
        archivedTimeline: String? = null,
        archivedAnnotations: String? = null,
        archivedMidiCues: String? = null,
        archivedDmxCues: String? = null,
        archivedGrid: String? = null,
        archivedPrompter: ArrangementVariantPrompterArchiveAsset? = null,
        archivedLyricsEditorRaw: String? = null,
        archivedPlayback: SmpConfig.PlaybackConfig? = null
    ) {
        existingVariantDir
            ?.takeIf(File::isDirectory)
            ?.listFiles()
            .orEmpty()
            .filterNot { child ->
                child.name == CONFIG_FILE_NAME || child.name == ARRANGEMENT_FILE_NAME
            }
            .forEach { child ->
                val copied = child.copyRecursively(
                    target = File(targetDir, child.name),
                    overwrite = true
                )
                if (!copied) {
                    throw IOException("Unable to preserve Arrangement variant asset: ${child.name}")
                }
            }

        val configJson = existingVariantDir
            ?.let { sourceDir -> File(sourceDir, CONFIG_FILE_NAME) }
            ?.takeIf(File::isFile)
            ?.let { configFile ->
                runCatching {
                    JSONObject(configFile.readText(Charsets.UTF_8))
                }.getOrNull()
            }
            ?: JSONObject()
        configJson
            .put("version", configJson.optInt("version", 1).coerceAtLeast(1))
            .put("id", variantId)
            .put("title", title)
            .put(
                "arrangementVariant",
                JSONObject().put("sourceSongId", sourceSongId)
            )
        archivedLyricsLineColors?.let { colors ->
            configJson.put(
                "lyricsLineColors",
                JSONObject().apply {
                    colors.keys.sorted().forEach { key ->
                        put(key, colors.getValue(key))
                    }
                }
            )
        }
        archivedPlayback?.let { playback ->
            configJson.put("playback", SmpVariantPlayback.encode(playback))
        }
        File(targetDir, CONFIG_FILE_NAME).writeText(
            configJson.toString(2),
            Charsets.UTF_8
        )
        File(targetDir, ARRANGEMENT_FILE_NAME).writeText(
            ArrangementJsonCodec.encode(arrangement).toString(2),
            Charsets.UTF_8
        )
        archivedLyrics?.let { lyrics ->
            File(targetDir, LYRICS_FILE_NAME).writeText(lyrics, Charsets.UTF_8)
        }
        when {
            archivedLyricsEditorRaw != null -> {
                File(targetDir, LrcStorage.LYRICS_EDITOR_RAW_FILE_NAME).writeText(
                    archivedLyricsEditorRaw,
                    Charsets.UTF_8
                )
            }

            archivedLyrics != null -> {
                val existingRaw = File(targetDir, LrcStorage.LYRICS_EDITOR_RAW_FILE_NAME)
                if (existingRaw.exists() && !existingRaw.delete()) {
                    throw IOException("Unable to clear stale Arrangement variant raw lyrics draft")
                }
            }
        }
        archivedChords?.let { chords ->
            File(targetDir, CHORDS_FILE_NAME).writeText(chords, Charsets.UTF_8)
        }
        archivedTimeline?.let { timeline ->
            File(targetDir, SmpTimelineStore.TIMELINE_FILE_NAME).writeText(
                timeline,
                Charsets.UTF_8
            )
        }
        archivedAnnotations?.let { annotations ->
            File(targetDir, SmpAnnotationsStore.ANNOTATIONS_FILE_NAME).writeText(
                annotations,
                Charsets.UTF_8
            )
        }
        archivedMidiCues?.let { midiCues ->
            File(targetDir, SmpMidiCuesStore.MIDI_CUES_FILE_NAME).writeText(
                midiCues,
                Charsets.UTF_8
            )
        }
        archivedDmxCues?.let { dmxCues ->
            File(targetDir, SmpLightCueStore.LIGHT_CUES_FILE_NAME).writeText(
                dmxCues,
                Charsets.UTF_8
            )
        }
        archivedGrid?.let { grid ->
            File(targetDir, GRID_FILE_NAME).writeText(grid, Charsets.UTF_8)
        }
        archivedPrompter?.let { prompter ->
            PROMPTER_FILE_NAMES.forEach { fileName ->
                val aliasFile = File(targetDir, fileName)
                if (aliasFile.exists() && !aliasFile.deleteRecursively()) {
                    throw IOException("Unable to replace Arrangement variant prompter: $fileName")
                }
            }
            File(targetDir, "prompter.${prompter.format}").writeText(
                prompter.content,
                Charsets.UTF_8
            )
        }
    }
}
