package com.patrick.lrcreader.smp

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object SmpExporter {

    private const val TAG = "SMP_EXPORT"
    private const val EXPORT_DIR_NAME = "smp_exports"
    private const val CONFIG_ENTRY_NAME = "config.json"
    private const val GRID_ENTRY_NAME = "grid.json"
    private const val WAVEFORM_ENTRY_NAME = "waveform.json"
    private const val ARRANGEMENT_ENTRY_NAME = "arrangement.json"

    internal data class ExportRequest(
        val packageSong: SongUnit,
        val selectedVariantId: String?
    )

    fun exportSongUnitToSmp(context: Context, songUnit: SongUnit): File? {
        val exportsRoot = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (exportsRoot == null) {
            Log.e(TAG, "Export SMP impossible: dossier Downloads externe indisponible")
            return null
        }

        val exportDir = File(exportsRoot, EXPORT_DIR_NAME)
        return exportSongUnitToDirectory(context, songUnit, exportDir)
    }

    fun exportSongUnitToCacheSmp(context: Context, songUnit: SongUnit): File? {
        val exportDir = File(context.cacheDir, EXPORT_DIR_NAME)
        return exportSongUnitToDirectory(context, songUnit, exportDir)
    }

    private fun exportSongUnitToDirectory(
        context: Context,
        songUnit: SongUnit,
        exportDir: File
    ): File? {
        if (!exportDir.exists() && !exportDir.mkdirs()) {
            Log.e(TAG, "Export SMP impossible: création du dossier ${exportDir.absolutePath}")
            return null
        }

        val requestedSong = refreshSongUnitForExport(context, songUnit)
        val exportRequest = runCatching {
            resolveExportRequest(
                requestedSong = requestedSong,
                findSongById = SmpLibraryScanner(context)::findSongById
            )
        }.getOrElse { error ->
            Log.e(
                TAG,
                "Export SMP impossible: dépendance Arrangement introuvable pour ${requestedSong.id}",
                error
            )
            return null
        }
        val exportSong = refreshSongUnitForExport(context, exportRequest.packageSong)
        val config = SmpConfig.fromSongUnit(context, exportSong)
        val arrangementVariants = runCatching {
            resolveArrangementVariantsForExport(
                context = context,
                sourceSong = exportSong,
                selectedVariantId = exportRequest.selectedVariantId
            )
        }.getOrElse { error ->
            Log.e(
                TAG,
                "Export SMP refusé: variante Arrangement illisible pour le parent ${exportSong.id}",
                error
            )
            return null
        }
        val targetFile = resolveAvailableExportFile(exportDir, requestedSong)
        val partFile = File(exportDir, "${targetFile.name}.part")
        val ignoredFiles = mutableListOf<String>()
        var exportedFiles = 0

        logAnnotationsState(exportSong, config)

        if (partFile.exists() && !partFile.delete()) {
            Log.e(TAG, "Export SMP impossible: suppression du fichier temporaire ${partFile.absolutePath}")
            return null
        }

        try {
            ZipOutputStream(FileOutputStream(partFile).buffered()).use { zipOutput ->
                writeStringEntry(
                    zipOutput = zipOutput,
                    entryName = CONFIG_ENTRY_NAME,
                    contents = config.toJsonString()
                )
                exportedFiles += 1

                exportedFiles += writeAssetEntry(
                    zipOutput = zipOutput,
                    label = "audio",
                    sourcePath = exportSong.audioPath,
                    entryName = config.files?.audio,
                    ignoredFiles = ignoredFiles
                )
                exportedFiles += writeAssetEntry(
                    zipOutput = zipOutput,
                    label = "lyrics",
                    sourcePath = exportSong.lyricsPath,
                    entryName = config.files?.lyrics,
                    ignoredFiles = ignoredFiles
                )
                exportedFiles += writeAssetEntry(
                    zipOutput = zipOutput,
                    label = "chords",
                    sourcePath = exportSong.chordsPath,
                    entryName = config.files?.chords,
                    ignoredFiles = ignoredFiles
                )
                exportedFiles += writeAssetEntry(
                    zipOutput = zipOutput,
                    label = "timeline",
                    sourcePath = exportSong.timelinePath,
                    entryName = config.files?.timeline,
                    ignoredFiles = ignoredFiles
                )
                exportedFiles += writeAssetEntry(
                    zipOutput = zipOutput,
                    label = "waveform",
                    sourcePath = resolveWaveformPathForExport(exportSong),
                    entryName = WAVEFORM_ENTRY_NAME,
                    ignoredFiles = ignoredFiles
                )
                exportedFiles += writeAssetEntry(
                    zipOutput = zipOutput,
                    label = "annotations",
                    sourcePath = exportSong.annotationsPath,
                    entryName = config.files?.annotations,
                    ignoredFiles = ignoredFiles
                )
                exportedFiles += writeAssetEntry(
                    zipOutput = zipOutput,
                    label = "midiCues",
                    sourcePath = exportSong.midiPath,
                    entryName = config.files?.midiCues,
                    ignoredFiles = ignoredFiles
                )
                exportedFiles += writeAssetEntry(
                    zipOutput = zipOutput,
                    label = "dmxCues",
                    sourcePath = exportSong.dmxPath,
                    entryName = config.files?.dmxCues,
                    ignoredFiles = ignoredFiles
                )
                exportedFiles += writeAssetEntry(
                    zipOutput = zipOutput,
                    label = "prompter",
                    sourcePath = exportSong.prompterPath,
                    entryName = config.files?.prompter,
                    ignoredFiles = ignoredFiles
                )
                exportedFiles += writeAssetEntry(
                    zipOutput = zipOutput,
                    label = "grid",
                    sourcePath = resolveGridPathForExport(exportSong),
                    entryName = GRID_ENTRY_NAME,
                    ignoredFiles = ignoredFiles
                )
                exportedFiles += writeAssetEntry(
                    zipOutput = zipOutput,
                    label = "arrangement",
                    sourcePath = resolveArrangementPathForExport(exportSong),
                    entryName = ARRANGEMENT_ENTRY_NAME,
                    ignoredFiles = ignoredFiles
                )
                if (arrangementVariants.variants.isNotEmpty()) {
                    writeStringEntry(
                        zipOutput = zipOutput,
                        entryName = ArrangementVariantsArchiveCodec.FILE_NAME,
                        contents = ArrangementVariantsArchiveCodec.encode(arrangementVariants).toString(2)
                    )
                    exportedFiles += 1
                }
            }

            if (!partFile.renameTo(targetFile)) {
                Log.e(
                    TAG,
                    "Export SMP impossible: renommage ${partFile.absolutePath} -> ${targetFile.absolutePath}"
                )
                partFile.delete()
                return null
            }

            Log.i(
                TAG,
                "Export SMP terminé: path=${targetFile.absolutePath} exportedFiles=$exportedFiles"
            )
            if (ignoredFiles.isNotEmpty()) {
                Log.i(TAG, "Fichiers ignorés: ${ignoredFiles.joinToString()}")
            }
            return targetFile
        } catch (error: Exception) {
            Log.e(
                TAG,
                "Export SMP impossible: songId=${songUnit.id} title=${songUnit.title}",
                error
            )
            partFile.delete()
            return null
        }
    }

    private fun writeStringEntry(
        zipOutput: ZipOutputStream,
        entryName: String,
        contents: String
    ) {
        zipOutput.putNextEntry(ZipEntry(entryName))
        zipOutput.write(contents.toByteArray(Charsets.UTF_8))
        zipOutput.closeEntry()
    }

    private fun writeAssetEntry(
        zipOutput: ZipOutputStream,
        label: String,
        sourcePath: String?,
        entryName: String?,
        ignoredFiles: MutableList<String>
    ): Int {
        if (sourcePath.isNullOrBlank()) {
            return 0
        }

        if (entryName.isNullOrBlank()) {
            ignoredFiles += "$label non exportable"
            return 0
        }

        val sourceFile = File(sourcePath)
        if (!sourceFile.isFile) {
            ignoredFiles += "$label introuvable"
            return 0
        }

        zipOutput.putNextEntry(ZipEntry(entryName))
        sourceFile.inputStream().buffered().use { input ->
            input.copyTo(zipOutput)
        }
        zipOutput.closeEntry()
        return 1
    }

    private fun resolveAvailableExportFile(exportDir: File, songUnit: SongUnit): File {
        val baseName = sanitizeExportBaseName(
            songUnit.title.takeIf { it.isNotBlank() }
                ?: songUnit.id.takeIf { it.isNotBlank() }
                ?: "song_export"
        )

        var index = 0
        while (true) {
            val suffix = if (index == 0) "" else "_${index + 1}"
            val candidate = File(exportDir, "$baseName$suffix.smp")
            if (!candidate.exists()) {
                return candidate
            }
            index += 1
        }
    }

    private fun sanitizeExportBaseName(rawName: String): String {
        return rawName
            .trim()
            .replace(Regex("[^A-Za-z0-9._ -]+"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('.', '_', '-', ' ')
            .ifBlank { "song_export" }
    }

    private fun refreshSongUnitForExport(context: Context, songUnit: SongUnit): SongUnit {
        val songDir = songUnit.storageFolder
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.takeIf { it.isDirectory }
            ?: return songUnit

        resolveAnnotationsFileForExport(songDir)?.let { annotationsFile ->
            SmpAnnotationsStore.awaitIdle(annotationsFile)
        }
        SmpTimelineStore.awaitIdle(File(songDir, SmpTimelineStore.TIMELINE_FILE_NAME))

        return SmpLibraryScanner(context).findSongById(songUnit.id) ?: songUnit
    }

    private fun resolveAnnotationsFileForExport(songDir: File): File? {
        val metaName = SmpMetaStore.read(songDir)
            ?.annotationsFile
            ?.trim()
            .takeUnless { it.isNullOrBlank() }
        if (metaName != null) {
            return File(songDir, metaName)
        }

        val configName = runCatching {
            val configFile = File(songDir, CONFIG_ENTRY_NAME)
            if (!configFile.isFile) {
                null
            } else {
                SmpConfig.fromJsonOrNull(configFile.readText(Charsets.UTF_8))
                    ?.files
                    ?.annotations
                    ?.trim()
                    .takeUnless { it.isNullOrBlank() }
            }
        }.getOrNull()
        if (configName != null) {
            return File(songDir, configName)
        }

        return File(songDir, SmpAnnotationsStore.ANNOTATIONS_FILE_NAME)
    }

    private fun logAnnotationsState(songUnit: SongUnit, config: SmpConfig) {
        val annotationsPath = songUnit.annotationsPath
        val annotationsFile = annotationsPath?.let(::File)
        val exists = annotationsFile?.isFile == true
        Log.i(
            TAG,
            "Export annotations state: songId=${songUnit.id} path=${annotationsPath ?: "null"} exists=$exists entry=${config.files?.annotations ?: "null"}"
        )
    }

    private fun resolveGridPathForExport(songUnit: SongUnit): String? {
        val songDir = songUnit.storageFolder
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.takeIf { it.isDirectory }
            ?: return null
        val gridFile = File(songDir, GRID_ENTRY_NAME)
        Log.i(
            TAG,
            "Export grid state: songId=${songUnit.id} path=${gridFile.absolutePath} exists=${gridFile.isFile}"
        )
        return gridFile.takeIf { it.isFile }?.absolutePath
    }

    private fun resolveWaveformPathForExport(songUnit: SongUnit): String? {
        val songDir = songUnit.storageFolder
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.takeIf { it.isDirectory }
            ?: return null
        val waveformFile = File(songDir, WAVEFORM_ENTRY_NAME)
        Log.i(
            TAG,
            "Export waveform state: songId=${songUnit.id} path=${waveformFile.absolutePath} exists=${waveformFile.isFile}"
        )
        return waveformFile.takeIf { it.isFile }?.absolutePath
    }

    private fun resolveArrangementPathForExport(songUnit: SongUnit): String? {
        val songDir = songUnit.storageFolder
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.takeIf { it.isDirectory }
            ?: return null
        return File(songDir, ARRANGEMENT_ENTRY_NAME)
            .takeIf { it.isFile }
            ?.absolutePath
    }

    private fun resolveArrangementVariantsForExport(
        context: Context,
        sourceSong: SongUnit,
        selectedVariantId: String?
    ): ArrangementVariantsArchive {
        val variants = SmpLibraryScanner(context)
            .listSongs()
            .filter { candidate -> candidate.arrangementSourceSongId == sourceSong.id }
            .filter { candidate ->
                selectedVariantId == null || candidate.id == selectedVariantId
            }
            .map { variant ->
                val variantDir = variant.storageFolder
                    ?.takeIf { it.isNotBlank() }
                    ?.let(::File)
                    ?.takeIf { it.isDirectory }
                    ?: throw IllegalStateException("Arrangement variant folder is missing: ${variant.id}")
                val arrangementFile = File(variantDir, ARRANGEMENT_ENTRY_NAME)
                val arrangement = runCatching {
                    ArrangementJsonCodec.decode(
                        org.json.JSONObject(arrangementFile.readText(Charsets.UTF_8))
                    )
                }.getOrElse { error ->
                    throw IllegalStateException(
                        "Arrangement variant structure is unreadable: ${variant.id}",
                        error
                    )
                }
                ArrangementVariantArchiveEntry(
                    id = variant.id,
                    title = variant.title,
                    arrangement = arrangement,
                    lyrics = File(variantDir, "lyrics.lrc")
                        .takeIf(File::isFile)
                        ?.readText(Charsets.UTF_8),
                    chords = File(variantDir, "chords.lrc")
                        .takeIf(File::isFile)
                        ?.readText(Charsets.UTF_8),
                    lyricsLineColors = File(variantDir, CONFIG_ENTRY_NAME)
                        .takeIf(File::isFile)
                        ?.let { configFile ->
                            SmpConfig.fromJsonOrNull(configFile.readText(Charsets.UTF_8))
                                ?.lyricsLineColors
                        }
                )
            }
        if (selectedVariantId != null && variants.none { it.id == selectedVariantId }) {
            throw IllegalStateException(
                "Selected Arrangement variant is missing: $selectedVariantId"
            )
        }

        return ArrangementVariantsArchive(
            sourceSongId = sourceSong.id,
            variants = variants,
            selectedVariantId = selectedVariantId
        )
    }

    internal fun resolveExportRequest(
        requestedSong: SongUnit,
        findSongById: (String) -> SongUnit?
    ): ExportRequest {
        val sourceSongId = requestedSong.arrangementSourceSongId
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: return ExportRequest(
                packageSong = requestedSong,
                selectedVariantId = null
            )
        val sourceSong = findSongById(sourceSongId)
            ?: throw IllegalStateException(
                "Arrangement variant source song is missing: ${requestedSong.id}"
            )
        require(sourceSong.arrangementSourceSongId == null) {
            "Arrangement variant source cannot be another variant"
        }
        return ExportRequest(
            packageSong = sourceSong,
            selectedVariantId = requestedSong.id
        )
    }
}
