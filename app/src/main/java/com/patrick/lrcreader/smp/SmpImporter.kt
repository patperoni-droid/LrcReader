package com.patrick.lrcreader.smp

import android.content.Context
import android.net.Uri
import android.os.Looper
import android.provider.OpenableColumns
import android.util.Log
import com.patrick.lrcreader.core.EditSoundPrefs
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipInputStream

class SmpImporter(private val context: Context) {

    @Volatile
    var lastFailureReason: String? = null
        private set

    companion object {
        private const val TAG = "SmpImporter"
        private const val TRACKS_DIR_NAME = "tracks"
        private const val CONFIG_FILE_NAME = "config.json"
        private const val WAVEFORM_FILE_NAME = "waveform.json"
    }

    private data class PreservedSongTextFile(
        val fileName: String,
        val bytes: ByteArray
    )

    fun importSmp(uri: Uri): SongUnit? {
        lastFailureReason = null

        if (Looper.myLooper() == Looper.getMainLooper()) {
            lastFailureReason = "appel sur le thread principal"
            Log.w(TAG, "importSmp refusé sur le thread principal uri=$uri")
            return null
        }

        val displayName = resolveDisplayName(uri)
        val tracksRoot = File(context.filesDir, TRACKS_DIR_NAME)
        if (!tracksRoot.exists() && !tracksRoot.mkdirs()) {
            lastFailureReason = "création du dossier tracks impossible"
            Log.e(TAG, "Impossible de créer le dossier tracks: ${tracksRoot.absolutePath}")
            return null
        }

        val stagingDir = File(
            tracksRoot,
            ".import_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
        )
        if (!stagingDir.mkdirs()) {
            lastFailureReason = "création du dossier temporaire impossible"
            Log.e(TAG, "Impossible de créer le dossier temporaire: ${stagingDir.absolutePath}")
            return null
        }

        var importedDir: File? = null

        try {
            val extracted = extractArchive(uri = uri, stagingDir = stagingDir) ?: return null

            val config = extracted.config
            val title = config.title ?: displayName.removeSmpSuffix().ifBlank { "Untitled" }
            val rawConfigId = config.id
            val stableConfigId = sanitizeSongId(rawConfigId)
            val songId = stableConfigId ?: "song_${UUID.randomUUID()}"
            val destinationDir = File(tracksRoot, songId)
            val preservedLyrics = capturePreservedLyrics(
                destinationDir = destinationDir
            )

            if (stableConfigId != null && destinationDir.exists()) {
                Log.i(
                    TAG,
                    "Doublon SMP détecté: remplacement du morceau existant configId=$stableConfigId songId=$songId title=$title dir=${destinationDir.absolutePath}"
                )
            } else if (stableConfigId == null) {
                val reason = if (rawConfigId.isNullOrBlank()) "config.id absent" else "config.id invalide"
                Log.i(
                    TAG,
                    "Import SMP traité comme nouveau morceau: $reason rawConfigId=${rawConfigId ?: "null"} generatedSongId=$songId title=$title"
                )
            }

            if (destinationDir.exists() && !deleteRecursivelyIfExists(destinationDir)) {
                lastFailureReason = "écrasement du dossier existant impossible"
                Log.e(TAG, "Impossible d'écraser le dossier existant: ${destinationDir.absolutePath}")
                return null
            }

            if (!moveStagingToDestination(stagingDir = stagingDir, destinationDir = destinationDir)) {
                lastFailureReason = "finalisation de l'import impossible"
                Log.e(TAG, "Impossible de finaliser l'import vers ${destinationDir.absolutePath}")
                deleteRecursivelyIfExists(destinationDir)
                return null
            }

            importedDir = destinationDir
            restorePreservedLyrics(
                destinationDir = destinationDir,
                preservedLyrics = preservedLyrics
            )
            val audioPath = extracted.audioFileName?.let { File(destinationDir, it).absolutePath }

            restoreWaveformTrims(
                playback = config.playback,
                audioPath = audioPath,
                songId = songId,
                title = title
            )

            val songUnit = SongUnit(
                id = songId,
                title = title,
                storageFolder = destinationDir.absolutePath,
                audioPath = audioPath,
                lyricsPath = extracted.lyricsFileName?.let { File(destinationDir, it).absolutePath },
                chordsPath = extracted.chordsFileName?.let { File(destinationDir, it).absolutePath },
                waveformPath = extracted.waveformFileName?.let { File(destinationDir, it).absolutePath },
                annotationsPath = extracted.annotationsFileName?.let { File(destinationDir, it).absolutePath },
                midiPath = extracted.midiFileName?.let { File(destinationDir, it).absolutePath },
                dmxPath = extracted.dmxFileName?.let { File(destinationDir, it).absolutePath },
                prompterPath = extracted.prompterFileName?.let { File(destinationDir, it).absolutePath }
            )
            if (!SmpMetaStore.write(songUnit)) {
                Log.w(TAG, "Ecriture meta.json impossible après import songId=$songId dir=${destinationDir.absolutePath}")
            }

            Log.d(
                TAG,
                "Import .smp terminé: name=$displayName songId=$songId dir=${destinationDir.absolutePath}"
            )
            return songUnit
        } catch (e: Exception) {
            lastFailureReason = "exception pendant l'import"
            Log.e(TAG, "Erreur pendant l'import du .smp name=$displayName uri=$uri", e)
            return null
        } finally {
            if (importedDir == null) {
                deleteRecursivelyIfExists(stagingDir)
            }
        }
    }

    private fun extractArchive(uri: Uri, stagingDir: File): ExtractedArchive? {
        val extractedFiles = ExtractedFiles()
        val writtenFileNames = mutableSetOf<String>()
        var rawConfig: String? = null

        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zipInputStream ->
                    var entry = zipInputStream.nextEntry

                    while (entry != null) {
                        try {
                            if (!entry.isDirectory) {
                                val canonicalName = canonicalNameFor(entry.name)

                                when {
                                    canonicalName == null -> {
                                        Log.d(TAG, "Entrée SMP ignorée: ${entry.name}")
                                    }

                                    !writtenFileNames.add(canonicalName) -> {
                                        Log.w(TAG, "Entrée SMP dupliquée ignorée: ${entry.name} -> $canonicalName")
                                    }

                                    else -> {
                                        val destination = File(stagingDir, canonicalName)
                                        if (canonicalName == CONFIG_FILE_NAME) {
                                            val bytes = readEntryBytes(zipInputStream)
                                            rawConfig = String(bytes, Charsets.UTF_8)
                                            writeBytes(destination, bytes)
                                        } else {
                                            writeEntry(zipInputStream, destination)
                                        }
                                        extractedFiles.accept(canonicalName)
                                    }
                                }
                            }
                        } finally {
                            runCatching { zipInputStream.closeEntry() }
                        }

                        entry = zipInputStream.nextEntry
                    }
                }
            } ?: run {
                lastFailureReason = "uri inaccessible"
                Log.e(TAG, "Uri inaccessible pour l'import .smp uri=$uri")
                return null
            }
        } catch (e: Exception) {
            lastFailureReason = "zip corrompu ou illisible"
            Log.e(TAG, "Zip .smp corrompu ou illisible uri=$uri", e)
            return null
        }

        if (!extractedFiles.hasConfig) {
            lastFailureReason = "config.json absent"
            Log.e(TAG, "Import .smp impossible: config.json absent")
            return null
        }

        val config = SmpConfig.fromJsonOrNull(rawConfig)
        if (config == null) {
            lastFailureReason = "config.json invalide"
            Log.e(TAG, "Import .smp impossible: config.json invalide")
            return null
        }

        if (!extractedFiles.hasImportableContent()) {
            lastFailureReason = "aucune ressource utile trouvée"
            Log.e(TAG, "Import .smp impossible: aucune ressource utile trouvée")
            return null
        }

        return ExtractedArchive(
            config = config,
            audioFileName = extractedFiles.audioFileName,
            lyricsFileName = extractedFiles.lyricsFileName,
            chordsFileName = extractedFiles.chordsFileName,
            waveformFileName = extractedFiles.waveformFileName,
            annotationsFileName = extractedFiles.annotationsFileName,
            midiFileName = extractedFiles.midiFileName,
            dmxFileName = extractedFiles.dmxFileName,
            prompterFileName = extractedFiles.prompterFileName
        )
    }

    private fun canonicalNameFor(entryName: String): String? {
        val normalizedEntryName = entryName.replace('\\', '/')
        val fileName = normalizedEntryName.substringAfterLast('/').lowercase(Locale.ROOT)

        return when {
            fileName == CONFIG_FILE_NAME -> CONFIG_FILE_NAME
            isAudioFile(fileName) -> fileName
            fileName == "lyrics.lrc" -> fileName
            fileName == "chords.lrc" -> fileName
            fileName == WAVEFORM_FILE_NAME -> fileName
            fileName == "annotations.json" -> fileName
            fileName == "midi_cues.json" -> fileName
            fileName == "dmx_cues.json" -> fileName
            fileName == "settings.json" -> fileName
            isPrompterFile(fileName) -> fileName
            else -> null
        }
    }

    private fun isAudioFile(fileName: String): Boolean {
        return fileName == "audio.mp3" ||
            fileName == "audio.wav" ||
            fileName == "audio.flac" ||
            fileName == "audio.m4a" ||
            fileName == "audio.aac" ||
            fileName == "audio.ogg"
    }

    private fun isPrompterFile(fileName: String): Boolean {
        return fileName == "prompteur.txt" ||
            fileName == "prompteur.json" ||
            fileName == "prompter.txt" ||
            fileName == "prompter.json"
    }

    private fun sanitizeSongId(rawId: String?): String? {
        val safeId = rawId
            ?.trim()
            ?.replace(Regex("[^A-Za-z0-9._-]+"), "_")
            ?.trim('_', '.', '-')
            ?.ifBlank { null }

        if (rawId != null && safeId == null) {
            Log.w(TAG, "config.id invalide, génération d'un id local")
        }

        return safeId
    }

    private fun readEntryBytes(zipInputStream: ZipInputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

        while (true) {
            val read = zipInputStream.read(buffer)
            if (read <= 0) break
            output.write(buffer, 0, read)
        }

        return output.toByteArray()
    }

    private fun writeEntry(zipInputStream: ZipInputStream, destination: File) {
        FileOutputStream(destination).use { output ->
            zipInputStream.copyTo(output)
        }
    }

    private fun writeBytes(destination: File, bytes: ByteArray) {
        FileOutputStream(destination).use { output ->
            output.write(bytes)
            output.flush()
        }
    }

    private fun moveStagingToDestination(stagingDir: File, destinationDir: File): Boolean {
        if (stagingDir.renameTo(destinationDir)) {
            return true
        }

        if (!destinationDir.exists() && !destinationDir.mkdirs()) {
            Log.e(TAG, "Impossible de créer le dossier cible: ${destinationDir.absolutePath}")
            return false
        }

        val files = stagingDir.listFiles().orEmpty()
        for (file in files) {
            val destination = File(destinationDir, file.name)
            try {
                file.inputStream().use { input ->
                    FileOutputStream(destination).use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Impossible de copier ${file.absolutePath} vers ${destination.absolutePath}", e)
                return false
            }
        }

        return deleteRecursivelyIfExists(stagingDir)
    }

    private fun deleteRecursivelyIfExists(target: File): Boolean {
        return !target.exists() || target.deleteRecursively()
    }

    private fun restoreWaveformTrims(
        playback: SmpConfig.PlaybackConfig?,
        audioPath: String?,
        songId: String,
        title: String
    ) {
        if (playback == null) {
            return
        }

        if (audioPath.isNullOrBlank()) {
            Log.i(
                TAG,
                "Trims SMP ignorés: aucun audio extrait songId=$songId title=$title"
            )
            return
        }

        val startMs = playback.trimStartMs ?: 0L
        val endMs = playback.trimEndMs ?: 0L
        if (startMs > Int.MAX_VALUE.toLong() || endMs > Int.MAX_VALUE.toLong()) {
            Log.w(
                TAG,
                "Trims SMP ignorés: valeurs hors plage songId=$songId title=$title startMs=$startMs endMs=$endMs"
            )
            return
        }

        val audioUri = Uri.fromFile(File(audioPath))
        runCatching {
            EditSoundPrefs.save(
                context = context,
                uri = audioUri,
                startMs = startMs.toInt(),
                endMs = endMs.toInt()
            )
        }.onSuccess {
            Log.i(
                TAG,
                "Trims SMP restaurés: songId=$songId title=$title audioUri=$audioUri startMs=$startMs endMs=$endMs"
            )
        }.onFailure { error ->
            Log.e(
                TAG,
                "Impossible de restaurer les trims SMP: songId=$songId title=$title audioUri=$audioUri",
                error
            )
        }
    }

    private fun capturePreservedLyrics(destinationDir: File): PreservedSongTextFile? {
        if (!destinationDir.isDirectory) {
            return null
        }

        val existingLyrics = File(destinationDir, "lyrics.lrc")
        if (!existingLyrics.isFile) {
            return null
        }

        return runCatching {
            PreservedSongTextFile(
                fileName = existingLyrics.name,
                bytes = existingLyrics.readBytes()
            )
        }.onFailure { error ->
            Log.e(
                TAG,
                "Impossible de préserver les paroles utilisateur avant remplacement: ${existingLyrics.absolutePath}",
                error
            )
        }.getOrNull()
    }

    private fun restorePreservedLyrics(
        destinationDir: File,
        preservedLyrics: PreservedSongTextFile?
    ) {
        if (preservedLyrics == null) {
            return
        }

        val targetFile = File(destinationDir, preservedLyrics.fileName)
        runCatching {
            targetFile.parentFile?.mkdirs()
            FileOutputStream(targetFile).use { output ->
                output.write(preservedLyrics.bytes)
                output.flush()
            }
        }.onSuccess {
            Log.i(
                TAG,
                "Paroles utilisateur préservées après réimport: ${targetFile.absolutePath}"
            )
        }.onFailure { error ->
            Log.e(
                TAG,
                "Impossible de restaurer les paroles utilisateur après réimport: ${targetFile.absolutePath}",
                error
            )
        }
    }

    private fun resolveDisplayName(uri: Uri): String {
        var name = uri.lastPathSegment ?: "unknown.smp"

        try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) {
                    name = cursor.getString(index)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Impossible de lire le nom du fichier uri=$uri", e)
        }

        return name
    }

    private fun String.removeSmpSuffix(): String {
        return if (endsWith(".smp", ignoreCase = true)) {
            dropLast(4)
        } else {
            this
        }
    }

    private data class ExtractedArchive(
        val config: SmpConfig,
        val audioFileName: String?,
        val lyricsFileName: String?,
        val chordsFileName: String?,
        val waveformFileName: String?,
        val annotationsFileName: String?,
        val midiFileName: String?,
        val dmxFileName: String?,
        val prompterFileName: String?
    )

    private inner class ExtractedFiles {
        var hasConfig: Boolean = false
            private set
        var audioFileName: String? = null
            private set
        var lyricsFileName: String? = null
            private set
        var chordsFileName: String? = null
            private set
        var waveformFileName: String? = null
            private set
        var annotationsFileName: String? = null
            private set
        var midiFileName: String? = null
            private set
        var dmxFileName: String? = null
            private set
        var prompterFileName: String? = null
            private set

        fun accept(fileName: String) {
            when {
                fileName == CONFIG_FILE_NAME -> hasConfig = true
                isAudioFile(fileName) -> audioFileName = fileName
                fileName == "lyrics.lrc" -> lyricsFileName = fileName
                fileName == "chords.lrc" -> chordsFileName = fileName
                fileName == WAVEFORM_FILE_NAME -> waveformFileName = fileName
                fileName == "annotations.json" -> annotationsFileName = fileName
                fileName == "midi_cues.json" -> midiFileName = fileName
                fileName == "dmx_cues.json" -> dmxFileName = fileName
                isPrompterFile(fileName) -> prompterFileName = fileName
            }
        }

        fun hasImportableContent(): Boolean {
            return audioFileName != null ||
                lyricsFileName != null ||
                chordsFileName != null ||
                waveformFileName != null ||
                annotationsFileName != null ||
                midiFileName != null ||
                dmxFileName != null ||
                prompterFileName != null
        }
    }
}
