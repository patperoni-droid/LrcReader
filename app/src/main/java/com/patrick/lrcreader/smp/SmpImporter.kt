package com.patrick.lrcreader.smp

import android.content.Context
import android.net.Uri
import android.os.Looper
import android.os.SystemClock
import android.provider.OpenableColumns
import android.util.Log
import com.patrick.lrcreader.core.EditSoundPrefs
import org.json.JSONObject
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
        private const val TRACE_TAG = "SMP_TRACE"
        private const val IMPORT_TRACE_TAG = "IMPORT_TRACE"
        private const val TRACKS_DIR_NAME = "tracks"
        private const val CONFIG_FILE_NAME = "config.json"
        private const val WAVEFORM_FILE_NAME = "waveform.json"
        private const val GRID_FILE_NAME = "grid.json"
        private const val ARRANGEMENT_FILE_NAME = "arrangement.json"
        private const val MIDI_TRACE_TAG = "SMP_MIDI_TRACE"
    }

    data class ArrangementVariantsRestoreOutcome(
        val manifestFound: Boolean,
        val success: Boolean,
        val selectedVariantId: String? = null
    )

    private data class PreservedSongTextFile(
        val fileName: String,
        val bytes: ByteArray
    )

    fun restoreArrangementVariantsOnly(
        uri: Uri,
        sourceSong: SongUnit,
        replaceExisting: Boolean = false,
        selectedVariantOnly: Boolean = false
    ): ArrangementVariantsRestoreOutcome {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Log.w(TAG, "Restauration des variantes refusée sur le thread principal uri=$uri")
            return ArrangementVariantsRestoreOutcome(manifestFound = false, success = false)
        }

        val archive = runCatching {
            readArrangementVariantsArchive(uri)
        }.getOrElse { error ->
            Log.e(TAG, "Lecture des variantes Arrangement impossible uri=$uri", error)
            return ArrangementVariantsRestoreOutcome(manifestFound = true, success = false)
        } ?: return ArrangementVariantsRestoreOutcome(manifestFound = false, success = true)

        if (archive.sourceSongId != sourceSong.id) {
            Log.e(
                TAG,
                "Restauration des variantes refusée: archiveParent=${archive.sourceSongId} runtimeParent=${sourceSong.id}"
            )
            return ArrangementVariantsRestoreOutcome(manifestFound = true, success = false)
        }
        val selectedVariantId = archive.selectedVariantId
        if (selectedVariantOnly && selectedVariantId == null) {
            return ArrangementVariantsRestoreOutcome(
                manifestFound = true,
                success = true
            )
        }
        val archiveToRestore = if (selectedVariantOnly) {
            archive.copy(
                variants = archive.variants.filter { variant ->
                    variant.id == selectedVariantId
                }
            )
        } else {
            archive
        }

        val restored = ArrangementVariantStore.restoreFromArchive(
            context = context,
            sourceSong = sourceSong,
            archive = archiveToRestore,
            replaceExisting = replaceExisting
        )
        if (restored.isFailure) {
            Log.e(
                TAG,
                "Restauration des variantes Arrangement impossible pour ${sourceSong.id}",
                restored.exceptionOrNull()
            )
        }
        return ArrangementVariantsRestoreOutcome(
            manifestFound = true,
            success = restored.isSuccess,
            selectedVariantId = selectedVariantId.takeIf { selectedVariantOnly }
        )
    }

    fun importSmp(
        uri: Uri,
        preserveExistingLyricsOnReplace: Boolean = true
    ): SongUnit? {
        val importStartMs = SystemClock.elapsedRealtime()
        lastFailureReason = null
        Log.i(
            IMPORT_TRACE_TAG,
            "elapsedMs=$importStartMs step=runtime_import_start uri=$uri"
        )

        if (Looper.myLooper() == Looper.getMainLooper()) {
            lastFailureReason = "appel sur le thread principal"
            Log.w(TAG, "importSmp refusé sur le thread principal uri=$uri")
            Log.w(TRACE_TAG, "step=import_failed uri=$uri reason=main_thread")
            Log.i(
                IMPORT_TRACE_TAG,
                "elapsedMs=${SystemClock.elapsedRealtime()} step=runtime_import_end durationMs=${SystemClock.elapsedRealtime() - importStartMs} uri=$uri result=main_thread"
            )
            return null
        }

        val displayName = resolveDisplayName(uri)
        val tracksRoot = File(context.filesDir, TRACKS_DIR_NAME)
        Log.i(
            TRACE_TAG,
            "step=import_start uri=$uri displayName=$displayName tracksRoot=${tracksRoot.absolutePath}"
        )
        if (!tracksRoot.exists() && !tracksRoot.mkdirs()) {
            lastFailureReason = "création du dossier tracks impossible"
            Log.e(TAG, "Impossible de créer le dossier tracks: ${tracksRoot.absolutePath}")
            Log.e(TRACE_TAG, "step=import_failed uri=$uri reason=tracks_root_create_failed path=${tracksRoot.absolutePath}")
            Log.i(
                IMPORT_TRACE_TAG,
                "elapsedMs=${SystemClock.elapsedRealtime()} step=runtime_import_end durationMs=${SystemClock.elapsedRealtime() - importStartMs} uri=$uri result=tracks_root_create_failed"
            )
            return null
        }

        val stagingDir = File(
            tracksRoot,
            ".import_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
        )
        if (!stagingDir.mkdirs()) {
            lastFailureReason = "création du dossier temporaire impossible"
            Log.e(TAG, "Impossible de créer le dossier temporaire: ${stagingDir.absolutePath}")
            Log.e(TRACE_TAG, "step=import_failed uri=$uri reason=staging_create_failed path=${stagingDir.absolutePath}")
            Log.i(
                IMPORT_TRACE_TAG,
                "elapsedMs=${SystemClock.elapsedRealtime()} step=runtime_import_end durationMs=${SystemClock.elapsedRealtime() - importStartMs} uri=$uri result=staging_create_failed"
            )
            return null
        }

        var importedDir: File? = null

        try {
            val extractStartMs = SystemClock.elapsedRealtime()
            val extracted = extractArchive(uri = uri, stagingDir = stagingDir) ?: run {
                Log.i(
                    IMPORT_TRACE_TAG,
                    "elapsedMs=${SystemClock.elapsedRealtime()} step=runtime_import_end durationMs=${SystemClock.elapsedRealtime() - importStartMs} uri=$uri result=extract_failed"
                )
                return null
            }
            Log.i(
                IMPORT_TRACE_TAG,
                "elapsedMs=${SystemClock.elapsedRealtime()} step=runtime_import_extract_done durationMs=${SystemClock.elapsedRealtime() - extractStartMs} uri=$uri"
            )

            val config = extracted.config
            val title = firstNonBlankTitle(
                config.title,
                displayName.removeSmpSuffix()
            )
            val rawConfigId = config.id
            val stableConfigId = sanitizeSongId(rawConfigId)
            val songId = stableConfigId ?: "song_${UUID.randomUUID()}"
            val destinationDir = File(tracksRoot, songId)
            val destinationExistedBeforeImport = destinationDir.exists()
            Log.i(
                TRACE_TAG,
                "step=import_resolved uri=$uri displayName=$displayName rawConfigId=${rawConfigId ?: "null"} stableSongId=${stableConfigId ?: "invalid_or_absent"} finalSongId=$songId destinationDir=${destinationDir.absolutePath}"
            )
            val existingMidiFile = File(destinationDir, SmpMidiCuesStore.MIDI_CUES_FILE_NAME)
            val preservedLyrics = if (preserveExistingLyricsOnReplace) {
                capturePreservedLyrics(destinationDir = destinationDir)
            } else {
                null
            }

            if (stableConfigId != null && destinationDir.exists()) {
                Log.i(
                    TAG,
                    "Doublon SMP détecté: remplacement du morceau existant configId=$stableConfigId songId=$songId title=$title dir=${destinationDir.absolutePath}"
                )
                Log.d(
                    MIDI_TRACE_TAG,
                    "${if (existingMidiFile.isFile) "OVERWRITE" else "REIMPORT"} songId=$songId path=${existingMidiFile.absolutePath} exists=${existingMidiFile.isFile} incomingMidi=${extracted.midiFileName != null}"
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
                Log.e(TRACE_TAG, "step=import_failed uri=$uri reason=destination_delete_failed destinationDir=${destinationDir.absolutePath}")
                Log.i(
                    IMPORT_TRACE_TAG,
                    "elapsedMs=${SystemClock.elapsedRealtime()} step=runtime_import_end durationMs=${SystemClock.elapsedRealtime() - importStartMs} uri=$uri result=destination_delete_failed songId=$songId"
                )
                return null
            }

            val finalizeStartMs = SystemClock.elapsedRealtime()
            if (!moveStagingToDestination(stagingDir = stagingDir, destinationDir = destinationDir)) {
                lastFailureReason = "finalisation de l'import impossible"
                Log.e(TAG, "Impossible de finaliser l'import vers ${destinationDir.absolutePath}")
                Log.e(TRACE_TAG, "step=import_failed uri=$uri reason=move_to_destination_failed destinationDir=${destinationDir.absolutePath}")
                deleteRecursivelyIfExists(destinationDir)
                Log.i(
                    IMPORT_TRACE_TAG,
                    "elapsedMs=${SystemClock.elapsedRealtime()} step=runtime_import_end durationMs=${SystemClock.elapsedRealtime() - importStartMs} uri=$uri result=move_to_destination_failed songId=$songId"
                )
                return null
            }
            Log.i(
                IMPORT_TRACE_TAG,
                "elapsedMs=${SystemClock.elapsedRealtime()} step=runtime_import_finalize_done durationMs=${SystemClock.elapsedRealtime() - finalizeStartMs} uri=$uri songId=$songId"
            )

            importedDir = destinationDir
            if (preserveExistingLyricsOnReplace) {
                restorePreservedLyrics(
                    destinationDir = destinationDir,
                    preservedLyrics = preservedLyrics
                )
            }
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
                timelinePath = extracted.timelineFileName?.let { File(destinationDir, it).absolutePath },
                waveformPath = extracted.waveformFileName?.let { File(destinationDir, it).absolutePath },
                annotationsPath = extracted.annotationsFileName?.let { File(destinationDir, it).absolutePath },
                midiPath = extracted.midiFileName?.let { File(destinationDir, it).absolutePath },
                dmxPath = extracted.dmxFileName?.let { File(destinationDir, it).absolutePath },
                prompterPath = extracted.prompterFileName?.let { File(destinationDir, it).absolutePath }
            )
            normalizeImportedArrangementSource(
                destinationDir = destinationDir,
                sourceSongId = songId
            )
            Log.d(
                "RESTORE_DIAG",
                "importedSongId=$songId importedTitle=$title importedDisplayTitle=$title importedAudioPath=${audioPath ?: "null"}"
            )
            Log.d(
                "RESTORE_DIAG",
                "configExists=${File(destinationDir, CONFIG_FILE_NAME).isFile} configTitle=${config.title ?: "null"} metadataTitle=${songUnit.title}"
            )
            if (!SmpMetaStore.write(songUnit)) {
                Log.w(TAG, "Ecriture meta.json impossible après import songId=$songId dir=${destinationDir.absolutePath}")
            }
            extracted.arrangementVariants?.let { variantsArchive ->
                val restoreResult = ArrangementVariantStore.restoreFromArchive(
                    context = context,
                    sourceSong = songUnit,
                    archive = variantsArchive,
                    replaceExisting = true
                )
                if (restoreResult.isFailure) {
                    lastFailureReason = "restauration des variantes Arrangement impossible"
                    Log.e(
                        TAG,
                        "Import du parent terminé mais restauration de ses variantes impossible songId=$songId",
                        restoreResult.exceptionOrNull()
                    )
                    if (!destinationExistedBeforeImport) {
                        val rollbackSucceeded = deleteRecursivelyIfExists(destinationDir)
                        if (rollbackSucceeded) {
                            importedDir = null
                        } else {
                            Log.e(
                                TAG,
                                "Rollback du parent importé impossible après échec des variantes songId=$songId"
                            )
                        }
                    }
                    return null
                }
            }

            Log.d(
                TAG,
                "Import .smp terminé: name=$displayName songId=$songId dir=${destinationDir.absolutePath}"
            )
            Log.i(
                TRACE_TAG,
                "step=import_success uri=$uri songId=$songId title=$title destinationDir=${destinationDir.absolutePath}"
            )
            Log.i(
                IMPORT_TRACE_TAG,
                "elapsedMs=${SystemClock.elapsedRealtime()} step=runtime_import_end durationMs=${SystemClock.elapsedRealtime() - importStartMs} uri=$uri result=success songId=$songId"
            )
            return songUnit
        } catch (e: Exception) {
            lastFailureReason = "exception pendant l'import"
            Log.e(TAG, "Erreur pendant l'import du .smp name=$displayName uri=$uri", e)
            Log.e(TRACE_TAG, "step=import_failed uri=$uri reason=exception", e)
            Log.i(
                IMPORT_TRACE_TAG,
                "elapsedMs=${SystemClock.elapsedRealtime()} step=runtime_import_end durationMs=${SystemClock.elapsedRealtime() - importStartMs} uri=$uri result=exception"
            )
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
        var rawArrangementVariants: String? = null

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
                                        if (canonicalName == ArrangementVariantsArchiveCodec.FILE_NAME) {
                                            throw IOException("Duplicate Arrangement variants manifest")
                                        }
                                        Log.w(TAG, "Entrée SMP dupliquée ignorée: ${entry.name} -> $canonicalName")
                                    }

                                    else -> {
                                        val destination = File(stagingDir, canonicalName)
                                        if (canonicalName == GRID_FILE_NAME || canonicalName == WAVEFORM_FILE_NAME) {
                                            Log.i(
                                                TAG,
                                                "Import extracted asset: file=$canonicalName path=${destination.absolutePath}"
                                            )
                                        }
                                        if (canonicalName == CONFIG_FILE_NAME) {
                                            val bytes = readEntryBytes(zipInputStream)
                                            rawConfig = String(bytes, Charsets.UTF_8)
                                            writeBytes(destination, bytes)
                                        } else if (canonicalName == ArrangementVariantsArchiveCodec.FILE_NAME) {
                                            val bytes = readEntryBytes(
                                                zipInputStream = zipInputStream,
                                                maximumBytes = ArrangementVariantsArchiveCodec.MAX_ARCHIVE_BYTES
                                            )
                                            rawArrangementVariants = String(bytes, Charsets.UTF_8)
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
                Log.e(TRACE_TAG, "step=import_extract_failed uri=$uri reason=uri_inaccessible")
                return null
            }
        } catch (e: Exception) {
            lastFailureReason = "zip corrompu ou illisible"
            Log.e(TAG, "Zip .smp corrompu ou illisible uri=$uri", e)
            Log.e(TRACE_TAG, "step=import_extract_failed uri=$uri reason=zip_invalid", e)
            return null
        }

        if (!extractedFiles.hasConfig) {
            lastFailureReason = "config.json absent"
            Log.e(TAG, "Import .smp impossible: config.json absent")
            Log.e(TRACE_TAG, "step=import_extract_failed uri=$uri reason=config_missing")
            return null
        }

        val config = SmpConfig.fromJsonOrNull(rawConfig)
        if (config == null) {
            lastFailureReason = "config.json invalide"
            Log.e(TAG, "Import .smp impossible: config.json invalide")
            Log.e(TRACE_TAG, "step=import_extract_failed uri=$uri reason=config_invalid")
            return null
        }

        if (!extractedFiles.hasImportableContent()) {
            lastFailureReason = "aucune ressource utile trouvée"
            Log.e(TAG, "Import .smp impossible: aucune ressource utile trouvée")
            return null
        }

        val arrangementVariants = rawArrangementVariants?.let { rawManifest ->
            val decoded = runCatching {
                ArrangementVariantsArchiveCodec.decode(JSONObject(rawManifest))
            }.getOrElse { error ->
                lastFailureReason = "variantes Arrangement invalides"
                Log.e(TAG, "Import .smp impossible: manifeste de variantes Arrangement invalide", error)
                return null
            }
            val packagedSongId = config.id?.trim()
            if (packagedSongId.isNullOrEmpty() || decoded.sourceSongId != packagedSongId) {
                lastFailureReason = "parent des variantes Arrangement incohérent"
                Log.e(
                    TAG,
                    "Import .smp impossible: parent Arrangement=${decoded.sourceSongId} configId=${packagedSongId ?: "null"}"
                )
                return null
            }
            decoded
        }

        return ExtractedArchive(
            config = config,
            audioFileName = extractedFiles.audioFileName,
            lyricsFileName = extractedFiles.lyricsFileName,
            chordsFileName = extractedFiles.chordsFileName,
            timelineFileName = extractedFiles.timelineFileName,
            waveformFileName = extractedFiles.waveformFileName,
            annotationsFileName = extractedFiles.annotationsFileName,
            midiFileName = extractedFiles.midiFileName,
            dmxFileName = extractedFiles.dmxFileName,
            prompterFileName = extractedFiles.prompterFileName,
            arrangementFileName = extractedFiles.arrangementFileName,
            arrangementVariants = arrangementVariants
        )
    }

    private fun readArrangementVariantsArchive(uri: Uri): ArrangementVariantsArchive? {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            ZipInputStream(inputStream).use { zipInputStream ->
                var entry = zipInputStream.nextEntry
                var found: ArrangementVariantsArchive? = null
                while (entry != null) {
                    try {
                        if (
                            !entry.isDirectory &&
                            canonicalNameFor(entry.name) == ArrangementVariantsArchiveCodec.FILE_NAME
                        ) {
                            require(found == null) { "Duplicate Arrangement variants manifest" }
                            val rawJson = String(
                                readEntryBytes(
                                    zipInputStream = zipInputStream,
                                    maximumBytes = ArrangementVariantsArchiveCodec.MAX_ARCHIVE_BYTES
                                ),
                                Charsets.UTF_8
                            )
                            found = ArrangementVariantsArchiveCodec.decode(JSONObject(rawJson))
                        }
                    } finally {
                        runCatching { zipInputStream.closeEntry() }
                    }
                    entry = zipInputStream.nextEntry
                }
                return found
            }
        } ?: throw IOException("SMP uri is inaccessible")
    }

    private fun canonicalNameFor(entryName: String): String? {
        val normalizedEntryName = entryName.replace('\\', '/')
        val fileName = normalizedEntryName.substringAfterLast('/').lowercase(Locale.ROOT)

        return when {
            fileName == CONFIG_FILE_NAME -> CONFIG_FILE_NAME
            isAudioFile(fileName) -> fileName
            fileName == "lyrics.lrc" -> fileName
            fileName == "chords.lrc" -> fileName
            fileName == SmpTimelineStore.TIMELINE_FILE_NAME -> fileName
            fileName == WAVEFORM_FILE_NAME -> fileName
            fileName == "annotations.json" -> fileName
            fileName == "midi_cues.json" -> fileName
            fileName == "dmx_cues.json" -> fileName
            fileName == GRID_FILE_NAME -> fileName
            fileName == ARRANGEMENT_FILE_NAME -> fileName
            fileName == ArrangementVariantsArchiveCodec.FILE_NAME -> fileName
            fileName == "settings.json" -> fileName
            isPrompterFile(fileName) -> fileName
            else -> null
        }
    }

    private fun isAudioFile(fileName: String): Boolean {
        return fileName == "audio.mp3" ||
            fileName == "audio.wav" ||
            fileName == "audio.wave" ||
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

    private fun readEntryBytes(
        zipInputStream: ZipInputStream,
        maximumBytes: Int
    ): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

        while (true) {
            val read = zipInputStream.read(buffer)
            if (read <= 0) break
            if (output.size() + read > maximumBytes) {
                throw IOException("SMP entry is too large")
            }
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

    private fun normalizeImportedArrangementSource(
        destinationDir: File,
        sourceSongId: String
    ) {
        val arrangementFile = File(destinationDir, ARRANGEMENT_FILE_NAME)
        if (!arrangementFile.isFile) return

        runCatching {
            val arrangement = ArrangementJsonCodec.decode(
                JSONObject(arrangementFile.readText(Charsets.UTF_8))
            )
            if (arrangement.sourceSongId == sourceSongId) return
            arrangementFile.writeText(
                ArrangementJsonCodec.encode(
                    arrangement.copy(sourceSongId = sourceSongId)
                ).toString(2),
                Charsets.UTF_8
            )
        }.onFailure { error ->
            Log.e(
                TAG,
                "Normalisation du projet Arrangement impossible songId=$sourceSongId",
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

    private fun firstNonBlankTitle(vararg candidates: String?): String {
        return candidates
            .asSequence()
            .mapNotNull { it?.trim() }
            .firstOrNull { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
            ?: "Titre sans nom"
    }

    private data class ExtractedArchive(
        val config: SmpConfig,
        val audioFileName: String?,
        val lyricsFileName: String?,
        val chordsFileName: String?,
        val timelineFileName: String?,
        val waveformFileName: String?,
        val annotationsFileName: String?,
        val midiFileName: String?,
        val dmxFileName: String?,
        val prompterFileName: String?,
        val arrangementFileName: String?,
        val arrangementVariants: ArrangementVariantsArchive?
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
        var timelineFileName: String? = null
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
        var arrangementFileName: String? = null
            private set

        fun accept(fileName: String) {
            when {
                fileName == CONFIG_FILE_NAME -> hasConfig = true
                isAudioFile(fileName) -> audioFileName = fileName
                fileName == "lyrics.lrc" -> lyricsFileName = fileName
                fileName == "chords.lrc" -> chordsFileName = fileName
                fileName == SmpTimelineStore.TIMELINE_FILE_NAME -> timelineFileName = fileName
                fileName == WAVEFORM_FILE_NAME -> waveformFileName = fileName
                fileName == "annotations.json" -> annotationsFileName = fileName
                fileName == "midi_cues.json" -> midiFileName = fileName
                fileName == "dmx_cues.json" -> dmxFileName = fileName
                fileName == ARRANGEMENT_FILE_NAME -> arrangementFileName = fileName
                isPrompterFile(fileName) -> prompterFileName = fileName
            }
        }

        fun hasImportableContent(): Boolean {
            return audioFileName != null ||
                lyricsFileName != null ||
                chordsFileName != null ||
                timelineFileName != null ||
                waveformFileName != null ||
                annotationsFileName != null ||
                midiFileName != null ||
                dmxFileName != null ||
                prompterFileName != null ||
                arrangementFileName != null
        }
    }
}
