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

        val config = SmpConfig.fromSongUnit(context, songUnit)
        val targetFile = resolveAvailableExportFile(exportDir, songUnit)
        val partFile = File(exportDir, "${targetFile.name}.part")
        val ignoredFiles = mutableListOf<String>()
        var exportedFiles = 0

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
                    sourcePath = songUnit.audioPath,
                    entryName = config.files?.audio,
                    ignoredFiles = ignoredFiles
                )
                exportedFiles += writeAssetEntry(
                    zipOutput = zipOutput,
                    label = "lyrics",
                    sourcePath = songUnit.lyricsPath,
                    entryName = config.files?.lyrics,
                    ignoredFiles = ignoredFiles
                )
                exportedFiles += writeAssetEntry(
                    zipOutput = zipOutput,
                    label = "chords",
                    sourcePath = songUnit.chordsPath,
                    entryName = config.files?.chords,
                    ignoredFiles = ignoredFiles
                )
                exportedFiles += writeAssetEntry(
                    zipOutput = zipOutput,
                    label = "annotations",
                    sourcePath = songUnit.annotationsPath,
                    entryName = config.files?.annotations,
                    ignoredFiles = ignoredFiles
                )
                exportedFiles += writeAssetEntry(
                    zipOutput = zipOutput,
                    label = "midiCues",
                    sourcePath = songUnit.midiPath,
                    entryName = config.files?.midiCues,
                    ignoredFiles = ignoredFiles
                )
                exportedFiles += writeAssetEntry(
                    zipOutput = zipOutput,
                    label = "dmxCues",
                    sourcePath = songUnit.dmxPath,
                    entryName = config.files?.dmxCues,
                    ignoredFiles = ignoredFiles
                )
                exportedFiles += writeAssetEntry(
                    zipOutput = zipOutput,
                    label = "prompter",
                    sourcePath = songUnit.prompterPath,
                    entryName = config.files?.prompter,
                    ignoredFiles = ignoredFiles
                )
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
}
