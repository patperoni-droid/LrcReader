package com.patrick.lrcreader.smp

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.documentfile.provider.DocumentFile

class SmpBatchImportProcessor(
    private val context: Context,
    private val smpConverter: SmpConverter
) {

    companion object {
        private const val TAG = "SMP_BATCH"

        private val AUDIO_EXTENSIONS = listOf(
            ".mp3",
            ".wav",
            ".flac",
            ".m4a",
            ".aac",
            ".ogg"
        )

        private val BUNDLE_EXTENSIONS = listOf(
            ".splbackup",
            ".splbackup.zip"
        )
    }

    enum class SourceKind {
        SMP,
        AUDIO,
        UNSUPPORTED
    }

    enum class FailureStage {
        UNSUPPORTED,
        CONVERSION,
        IMPORT,
        PLAYLIST
    }

    enum class ProgressStage {
        CONVERTING,
        IMPORTING,
        ADDING_TO_PLAYLIST
    }

    data class PlannedItem(
        val sourceUri: Uri,
        val displayName: String,
        val sourceKind: SourceKind,
        val mimeType: String?
    )

    data class BatchPlan(
        val items: List<PlannedItem>
    ) {
        val totalCount: Int get() = items.size
        val hasAudioToPrepare: Boolean get() = items.any { it.sourceKind == SourceKind.AUDIO }
        val supportedCount: Int get() = items.count { it.sourceKind != SourceKind.UNSUPPORTED }
        val hasSupportedItems: Boolean get() = supportedCount > 0
    }

    data class Progress(
        val currentItemIndex: Int,
        val totalCount: Int,
        val displayName: String,
        val stage: ProgressStage
    )

    data class ItemSuccess(
        val item: PlannedItem,
        val importedSong: SongUnit,
        val generatedSmpUri: Uri?,
        val addedToPlaylist: Boolean
    )

    data class ItemFailure(
        val item: PlannedItem,
        val stage: FailureStage,
        val reason: String?
    )

    data class BatchResult(
        val successes: List<ItemSuccess>,
        val failures: List<ItemFailure>
    ) {
        val successCount: Int get() = successes.size
        val failureCount: Int get() = failures.size
        val conversionFailureCount: Int get() = failures.count { it.stage == FailureStage.CONVERSION }
        val importFailureCount: Int get() = failures.count { it.stage == FailureStage.IMPORT }
        val playlistFailureCount: Int get() = failures.count { it.stage == FailureStage.PLAYLIST }
        val unsupportedCount: Int get() = failures.count { it.stage == FailureStage.UNSUPPORTED }
    }

    fun buildPlan(pickedUris: List<Uri>): BatchPlan {
        return BatchPlan(
            items = pickedUris.map { uri ->
                val displayName = resolveDisplayName(uri)
                val mimeType = context.contentResolver.getType(uri)
                val kind = classifyUri(uri = uri, mimeType = mimeType)
                PlannedItem(
                    sourceUri = uri,
                    displayName = displayName,
                    sourceKind = kind,
                    mimeType = mimeType
                )
            }
        )
    }

    suspend fun process(
        plan: BatchPlan,
        playlistName: String? = null,
        importSmp: suspend (Uri) -> SongUnit?,
        importFailureReasonProvider: () -> String?,
        addImportedSongToPlaylist: suspend (String, SongUnit) -> Result<Unit> = { _, _ ->
            Result.success(Unit)
        },
        onProgress: (Progress) -> Unit = {}
    ): BatchResult {
        val successes = mutableListOf<ItemSuccess>()
        val failures = mutableListOf<ItemFailure>()
        val totalCount = plan.totalCount.coerceAtLeast(1)

        plan.items.forEachIndexed { index, item ->
            when (item.sourceKind) {
                SourceKind.UNSUPPORTED -> {
                    val reason = "Fichier non pris en charge"
                    Log.w(
                        TAG,
                        "step=unsupported name=${item.displayName} uri=${item.sourceUri} mime=${item.mimeType}"
                    )
                    failures += ItemFailure(
                        item = item,
                        stage = FailureStage.UNSUPPORTED,
                        reason = reason
                    )
                }

                SourceKind.SMP,
                SourceKind.AUDIO -> {
                    val smpUri = if (item.sourceKind == SourceKind.AUDIO) {
                        onProgress(
                            Progress(
                                currentItemIndex = index + 1,
                                totalCount = totalCount,
                                displayName = item.displayName,
                                stage = ProgressStage.CONVERTING
                            )
                        )
                        Log.i(TAG, "step=convert_start name=${item.displayName} uri=${item.sourceUri}")
                        val conversionResult = smpConverter.convertSingleToLibrarySmp(item.sourceUri)
                        val outputUri = conversionResult.getOrNull()
                        if (outputUri == null) {
                            val reason = conversionResult.exceptionOrNull()?.message
                                ?: conversionResult.exceptionOrNull()?.javaClass?.simpleName
                                ?: "conversion SMP impossible"
                            Log.e(
                                TAG,
                                "step=convert_failed name=${item.displayName} uri=${item.sourceUri} reason=$reason",
                                conversionResult.exceptionOrNull()
                            )
                            failures += ItemFailure(
                                item = item,
                                stage = FailureStage.CONVERSION,
                                reason = reason
                            )
                            return@forEachIndexed
                        }
                        Log.i(
                            TAG,
                            "step=convert_ok name=${item.displayName} sourceUri=${item.sourceUri} smpUri=$outputUri"
                        )
                        outputUri
                    } else {
                        item.sourceUri
                    }

                    onProgress(
                        Progress(
                            currentItemIndex = index + 1,
                            totalCount = totalCount,
                            displayName = item.displayName,
                            stage = ProgressStage.IMPORTING
                        )
                    )
                    Log.i(TAG, "step=import_start name=${item.displayName} smpUri=$smpUri")
                    val importedSong = importSmp(smpUri)
                    if (importedSong == null) {
                        val reason = importFailureReasonProvider() ?: "import SMP impossible"
                        Log.e(
                            TAG,
                            "step=import_failed name=${item.displayName} smpUri=$smpUri reason=$reason"
                        )
                        failures += ItemFailure(
                            item = item,
                            stage = FailureStage.IMPORT,
                            reason = reason
                        )
                        return@forEachIndexed
                    }
                    Log.i(
                        TAG,
                        "step=import_ok name=${item.displayName} songId=${importedSong.id} title=${importedSong.title}"
                    )

                    if (!playlistName.isNullOrBlank()) {
                        onProgress(
                            Progress(
                                currentItemIndex = index + 1,
                                totalCount = totalCount,
                                displayName = item.displayName,
                                stage = ProgressStage.ADDING_TO_PLAYLIST
                            )
                        )
                        val playlistResult = addImportedSongToPlaylist(playlistName, importedSong)
                        val playlistError = playlistResult.exceptionOrNull()
                        if (playlistError != null) {
                            Log.e(
                                TAG,
                                "step=playlist_failed playlist=$playlistName songId=${importedSong.id} title=${importedSong.title}",
                                playlistError
                            )
                            failures += ItemFailure(
                                item = item,
                                stage = FailureStage.PLAYLIST,
                                reason = playlistError.message ?: "ajout playlist impossible"
                            )
                            return@forEachIndexed
                        }
                        Log.i(
                            TAG,
                            "step=playlist_ok playlist=$playlistName songId=${importedSong.id} title=${importedSong.title}"
                        )
                    }

                    successes += ItemSuccess(
                        item = item,
                        importedSong = importedSong,
                        generatedSmpUri = smpUri.takeIf { item.sourceKind == SourceKind.AUDIO },
                        addedToPlaylist = !playlistName.isNullOrBlank()
                    )
                }
            }
        }

        return BatchResult(
            successes = successes,
            failures = failures
        )
    }

    private fun classifyUri(uri: Uri, mimeType: String?): SourceKind {
        val nameCandidates = displayNameCandidatesOf(uri)

        if (hasAnyExtension(nameCandidates, BUNDLE_EXTENSIONS)) {
            return SourceKind.UNSUPPORTED
        }
        if (hasAnyExtension(nameCandidates, listOf(".smp"))) {
            return SourceKind.SMP
        }
        if (looksAudioFile(mimeType, nameCandidates)) {
            return SourceKind.AUDIO
        }

        val cleanMime = mimeType?.trim()?.lowercase()
        return when (cleanMime) {
            "application/zip",
            "application/x-zip-compressed",
            "application/octet-stream" -> SourceKind.SMP

            else -> SourceKind.UNSUPPORTED
        }
    }

    private fun looksAudioFile(mimeType: String?, nameCandidates: List<String>): Boolean {
        return mimeType?.startsWith("audio/") == true || hasAnyExtension(nameCandidates, AUDIO_EXTENSIONS)
    }

    private fun hasAnyExtension(candidates: List<String>, extensions: List<String>): Boolean {
        return candidates.any { candidate ->
            extensions.any { ext -> candidate.endsWith(ext, ignoreCase = true) }
        }
    }

    private fun displayNameCandidatesOf(uri: Uri): List<String> {
        val candidates = linkedSetOf<String>()
        val displayName = resolveDisplayName(uri).trim()
        if (displayName.isNotEmpty()) candidates += displayName
        val documentName = runCatching {
            DocumentFile.fromSingleUri(context, uri)?.name?.trim()
        }.getOrNull()
        if (!documentName.isNullOrEmpty()) candidates += documentName
        val lastPath = uri.lastPathSegment?.trim()
        if (!lastPath.isNullOrEmpty()) candidates += lastPath
        val rawUri = uri.toString().trim()
        if (rawUri.isNotEmpty()) candidates += rawUri
        return candidates.toList()
    }

    private fun resolveDisplayName(uri: Uri): String {
        var name = uri.lastPathSegment ?: "unknown"
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
        return name
    }
}
