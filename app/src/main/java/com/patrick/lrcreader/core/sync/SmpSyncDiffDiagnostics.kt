package com.patrick.lrcreader.core.sync

import android.util.Log

private const val SYNC_DIFF_DIAG_TAG = "SMP_SYNC_DIFF_DIAG"

data class SmpSyncPlanDiagnostics(
    val fullSongCount: Int,
    val fullSongReasonCounts: Map<String, Int>,
    val modifiedSongs: List<SmpSyncSongDiffDiagnostic>,
    val modifiedPlaylists: List<SmpSyncPlaylistDiffDiagnostic>,
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
    val sameTitleDifferentSongId: String?,
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

data class SmpSyncPlaylistDiffDiagnostic(
    val playlistName: String,
    val status: SyncDiffStatus,
    val primaryReason: String,
    val differentComponents: List<String>,
    val sourceSongIds: List<String>,
    val targetSongIds: List<String>,
    val sourceItemsHash: String?,
    val targetItemsHash: String?,
    val sourceGroupsHash: String?,
    val targetGroupsHash: String?,
    val sourceColorsHash: String?,
    val targetColorsHash: String?,
    val sourceFullPlaylistHash: String?,
    val targetFullPlaylistHash: String?
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
        val sourcePlaylistsById = source.playlists.associateBy { it.identityKey() }
        val targetPlaylistsById = target.playlists.associateBy { it.identityKey() }
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
                val sameTitleMatches = sameTitleIdsBySourceId[item.diff.entityId].orEmpty()
                val sameTitleDifferentSongId = sameTitleMatches.firstOrNull()?.targetSongId
                val primaryReason = primaryReason(
                    item = item,
                    sourceSong = sourceSong,
                    targetSong = targetSong,
                    differentComponents = differentComponents,
                    sameTitleDifferentIds = sameTitleMatches
                )

                SmpSyncSongDiffDiagnostic(
                    title = item.diff.title
                        ?: sourceSong?.title
                        ?: targetSong?.title
                        ?: item.diff.entityId,
                    sourceSongId = item.diff.entityId,
                    targetSongId = targetSong?.songId ?: sameTitleDifferentSongId,
                    sameTitleDifferentSongId = sameTitleDifferentSongId,
                    status = item.diff.status,
                    packageKind = packageItem?.kind ?: item.inferredPackageKind(),
                    primaryReason = primaryReason,
                    differentComponents = differentComponents
                )
            }
            .sortedWith(compareBy({ it.title.lowercase() }, { it.sourceSongId }))

        val playlistDiagnostics = plan.items
            .filter { item -> item.diff.entityType == SyncEntityType.PLAYLIST }
            .map { item ->
                val sourcePlaylist = sourcePlaylistsById[item.diff.entityId]
                val targetPlaylist = targetPlaylistsById[item.diff.entityId]
                val differentComponents = if (sourcePlaylist != null && targetPlaylist != null) {
                    playlistComponentDifferences(sourcePlaylist, targetPlaylist)
                } else {
                    emptyList()
                }
                SmpSyncPlaylistDiffDiagnostic(
                    playlistName = item.diff.title
                        ?: sourcePlaylist?.playlistName
                        ?: targetPlaylist?.playlistName
                        ?: item.diff.entityId,
                    status = item.diff.status,
                    primaryReason = playlistPrimaryReason(
                        item = item,
                        sourcePlaylist = sourcePlaylist,
                        targetPlaylist = targetPlaylist,
                        differentComponents = differentComponents
                    ),
                    differentComponents = differentComponents,
                    sourceSongIds = sourcePlaylist?.songIds.orEmpty(),
                    targetSongIds = targetPlaylist?.songIds.orEmpty(),
                    sourceItemsHash = sourcePlaylist?.itemsHash,
                    targetItemsHash = targetPlaylist?.itemsHash,
                    sourceGroupsHash = sourcePlaylist?.groupsHash,
                    targetGroupsHash = targetPlaylist?.groupsHash,
                    sourceColorsHash = sourcePlaylist?.colorsHash,
                    targetColorsHash = targetPlaylist?.colorsHash,
                    sourceFullPlaylistHash = sourcePlaylist?.fullPlaylistHash,
                    targetFullPlaylistHash = targetPlaylist?.fullPlaylistHash
                )
            }
            .sortedWith(compareBy({ it.playlistName.lowercase() }))

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
            modifiedPlaylists = playlistDiagnostics,
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

    private fun playlistComponentDifferences(
        source: SmpSyncPlaylistEntry,
        target: SmpSyncPlaylistEntry
    ): List<String> {
        return buildList {
            if (source.itemsHash != target.itemsHash) add("itemsHash")
            if (source.groupsHash != target.groupsHash) add("groupsHash")
            if (source.colorsHash != target.colorsHash) add("colorsHash")
            if (source.songIds != target.songIds) add("songIds")
            if (source.fullPlaylistHash != target.fullPlaylistHash) add("fullPlaylistHash")
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

    private fun playlistPrimaryReason(
        item: SyncPlanItem,
        sourcePlaylist: SmpSyncPlaylistEntry?,
        targetPlaylist: SmpSyncPlaylistEntry?,
        differentComponents: List<String>
    ): String {
        if (item.diff.status == SyncDiffStatus.ABSENT_ON_B) return "playlist absente sur téléphone secours"
        if (targetPlaylist == null) return "playlist absente sur téléphone secours"
        if (sourcePlaylist == null) return "playlist absente sur téléphone principal"
        return differentComponents
            .firstOrNull { it != "fullPlaylistHash" }
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
                "diff:song title=${song.title} sourceSongId=${song.sourceSongId} targetSongId=${song.targetSongId ?: "null"} sameTitleDifferentSongId=${song.sameTitleDifferentSongId ?: "null"} status=${song.status} packageKind=${song.packageKind ?: "none"} reason=${song.primaryReason} components=${song.differentComponents.joinToString()}"
            )
        }
        diagnostics.modifiedPlaylists.take(LOG_LIMIT).forEach { playlist ->
            logInfo(
                "diff:playlist name=${playlist.playlistName} status=${playlist.status} reason=${playlist.primaryReason} components=${playlist.differentComponents.joinToString()} sourceItems=${playlist.sourceItemsHash ?: "null"} targetItems=${playlist.targetItemsHash ?: "null"} sourceGroups=${playlist.sourceGroupsHash ?: "null"} targetGroups=${playlist.targetGroupsHash ?: "null"} sourceColors=${playlist.sourceColorsHash ?: "null"} targetColors=${playlist.targetColorsHash ?: "null"} sourceFull=${playlist.sourceFullPlaylistHash ?: "null"} targetFull=${playlist.targetFullPlaylistHash ?: "null"} sourceSongIds=${playlist.sourceSongIds.joinToString(prefix = "[", postfix = "]")} targetSongIds=${playlist.targetSongIds.joinToString(prefix = "[", postfix = "]")}"
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

    private fun SmpSyncPlaylistEntry.identityKey(): String {
        return playlistId?.trim()?.takeIf { it.isNotEmpty() } ?: playlistName
    }

    private companion object {
        const val LOG_LIMIT = 120
    }
}
