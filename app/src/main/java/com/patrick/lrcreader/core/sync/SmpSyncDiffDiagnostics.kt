package com.patrick.lrcreader.core.sync

import android.util.Log

private const val SYNC_DIFF_DIAG_TAG = "SMP_SYNC_DIFF_DIAG"

data class SmpSyncPlanDiagnostics(
    val fullSongCount: Int,
    val fullSongReasonCounts: Map<String, Int>,
    val modifiedSongs: List<SmpSyncSongDiffDiagnostic>,
    val sameTitleDifferentSongIds: List<SmpSyncSameTitleDifferentIdDiagnostic>
) {
    val hasLargeFullSongTransfer: Boolean
        get() = fullSongCount > LARGE_FULL_SONG_TRANSFER_THRESHOLD

    companion object {
        const val LARGE_FULL_SONG_TRANSFER_THRESHOLD = 10
    }
}

data class SmpSyncSongDiffDiagnostic(
    val title: String,
    val sourceSongId: String,
    val targetSongId: String?,
    val status: SyncDiffStatus,
    val packageKind: SmpSyncPackageKind?,
    val primaryReason: String,
    val differentComponents: List<String>
)

data class SmpSyncSameTitleDifferentIdDiagnostic(
    val title: String,
    val sourceSongId: String,
    val targetSongId: String
)

class SmpSyncDiffDiagnosticsBuilder {

    fun build(
        source: SmpSyncManifest,
        target: SmpSyncManifest,
        plan: SyncPlan,
        syncPackage: SmpSyncPackage? = null
    ): SmpSyncPlanDiagnostics {
        val sourceById = source.songs.associateBy { it.songId }
        val targetById = target.songs.associateBy { it.songId }
        val targetByTitle = target.songs.groupBy { it.title.normalizedTitleKey() }
        val packageByEntityId = syncPackage
            ?.items
            .orEmpty()
            .associateBy { it.entityId }

        val sameTitleDifferentIds = source.songs
            .flatMap { sourceSong ->
                targetByTitle[sourceSong.title.normalizedTitleKey()]
                    .orEmpty()
                    .filter { targetSong -> targetSong.songId != sourceSong.songId }
                    .map { targetSong ->
                        SmpSyncSameTitleDifferentIdDiagnostic(
                            title = sourceSong.title,
                            sourceSongId = sourceSong.songId,
                            targetSongId = targetSong.songId
                        )
                    }
            }
            .distinctBy { "${it.title}|${it.sourceSongId}|${it.targetSongId}" }
            .sortedWith(compareBy({ it.title.lowercase() }, { it.sourceSongId }, { it.targetSongId }))

        val sameTitleIdsBySourceId = sameTitleDifferentIds.groupBy { it.sourceSongId }

        val songDiagnostics = plan.items
            .filter { item -> item.diff.entityType == SyncEntityType.SONG }
            .mapNotNull { item ->
                val sourceSong = sourceById[item.diff.entityId]
                val targetSong = targetById[item.diff.entityId]
                val differentComponents = if (sourceSong != null && targetSong != null) {
                    componentDifferences(sourceSong, targetSong)
                } else {
                    emptyList()
                }
                val packageItem = packageByEntityId[item.diff.entityId]
                val primaryReason = primaryReason(
                    item = item,
                    sourceSong = sourceSong,
                    targetSong = targetSong,
                    differentComponents = differentComponents,
                    sameTitleDifferentIds = sameTitleIdsBySourceId[item.diff.entityId].orEmpty()
                )

                SmpSyncSongDiffDiagnostic(
                    title = item.diff.title
                        ?: sourceSong?.title
                        ?: targetSong?.title
                        ?: item.diff.entityId,
                    sourceSongId = item.diff.entityId,
                    targetSongId = targetSong?.songId,
                    status = item.diff.status,
                    packageKind = packageItem?.kind ?: item.inferredPackageKind(),
                    primaryReason = primaryReason,
                    differentComponents = differentComponents
                )
            }
            .sortedWith(compareBy({ it.title.lowercase() }, { it.sourceSongId }))

        val fullSongDiagnostics = songDiagnostics.filter { diagnostic ->
            diagnostic.packageKind == SmpSyncPackageKind.SONG_FULL ||
                diagnostic.status == SyncDiffStatus.ABSENT_ON_B ||
                diagnostic.status == SyncDiffStatus.MODIFIED_ON_A
        }
        val reasonCounts = fullSongDiagnostics
            .groupingBy { it.primaryReason }
            .eachCount()
            .toList()
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
            .toMap()

        val diagnostics = SmpSyncPlanDiagnostics(
            fullSongCount = fullSongDiagnostics.size,
            fullSongReasonCounts = reasonCounts,
            modifiedSongs = songDiagnostics,
            sameTitleDifferentSongIds = sameTitleDifferentIds
        )
        logDiagnostics(diagnostics)
        return diagnostics
    }

    private fun componentDifferences(
        source: SmpSyncSongEntry,
        target: SmpSyncSongEntry
    ): List<String> {
        return buildList {
            if (source.title != target.title) add("title")
            if (source.audioHash != target.audioHash) add("audioHash")
            if (source.lyricsHash != target.lyricsHash) add("lyricsHash")
            if (source.chordsHash != target.chordsHash) add("chordsHash")
            if (source.notesHash != target.notesHash) add("notesHash")
            if (source.prompterHash != target.prompterHash) add("prompterHash")
            if (source.timelineHash != target.timelineHash) add("timelineHash")
            if (source.midiHash != target.midiHash) add("midiHash")
            if (source.dmxHash != target.dmxHash) add("dmxHash")
            if (source.settingsHash != target.settingsHash) add("settingsHash")
            if (source.arrangementHash != target.arrangementHash) add("arrangementHash")
            if (source.gridHash != target.gridHash) add("gridHash")
            if (source.fullSongHash != target.fullSongHash) add("fullSongHash")
        }
    }

    private fun primaryReason(
        item: SyncPlanItem,
        sourceSong: SmpSyncSongEntry?,
        targetSong: SmpSyncSongEntry?,
        differentComponents: List<String>,
        sameTitleDifferentIds: List<SmpSyncSameTitleDifferentIdDiagnostic>
    ): String {
        if (item.diff.status == SyncDiffStatus.ABSENT_ON_B) {
            return if (sameTitleDifferentIds.isNotEmpty()) {
                "songId différent"
            } else {
                "absent sur téléphone secours"
            }
        }
        if (targetSong == null) return "absent sur téléphone secours"
        if (sourceSong == null) return "absent sur téléphone principal"
        return differentComponents
            .firstOrNull { it != "fullSongHash" }
            ?: differentComponents.firstOrNull()
            ?: item.diff.status.name
    }

    private fun SyncPlanItem.inferredPackageKind(): SmpSyncPackageKind? {
        if (action != SyncPlanAction.COPY_TO_B) return null
        return when (diff.status) {
            SyncDiffStatus.ABSENT_ON_B,
            SyncDiffStatus.MODIFIED_ON_A -> SmpSyncPackageKind.SONG_FULL
            else -> null
        }
    }

    private fun logDiagnostics(diagnostics: SmpSyncPlanDiagnostics) {
        logInfo("diff:fullSongs=${diagnostics.fullSongCount} reasons=${diagnostics.fullSongReasonCounts}")
        diagnostics.sameTitleDifferentSongIds.take(LOG_LIMIT).forEach { item ->
            logWarn(
                "diff:same_title_different_songId title=${item.title} sourceSongId=${item.sourceSongId} targetSongId=${item.targetSongId}"
            )
        }
        diagnostics.modifiedSongs.take(LOG_LIMIT).forEach { song ->
            logInfo(
                "diff:song title=${song.title} sourceSongId=${song.sourceSongId} targetSongId=${song.targetSongId ?: "null"} status=${song.status} packageKind=${song.packageKind ?: "none"} reason=${song.primaryReason} components=${song.differentComponents.joinToString()}"
            )
        }
    }

    private fun logInfo(message: String) {
        runCatching { Log.i(SYNC_DIFF_DIAG_TAG, message) }
    }

    private fun logWarn(message: String) {
        runCatching { Log.w(SYNC_DIFF_DIAG_TAG, message) }
    }

    private fun String.normalizedTitleKey(): String {
        return trim()
            .lowercase()
            .replace(Regex("\\s+"), " ")
    }

    private companion object {
        const val LOG_LIMIT = 120
    }
}
