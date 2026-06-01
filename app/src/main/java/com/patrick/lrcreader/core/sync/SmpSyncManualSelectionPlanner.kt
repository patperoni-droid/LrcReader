package com.patrick.lrcreader.core.sync

class SmpSyncManualSelectionPlanner {

    fun buildPlan(
        sourceManifest: SmpSyncManifest,
        selectedSongIds: Set<String>,
        selectedPlaylistIds: Set<String>
    ): SyncPlan {
        val songItems = sourceManifest.songs
            .filter { song -> song.songId in selectedSongIds }
            .map { song ->
                SyncPlanItem(
                    action = SyncPlanAction.COPY_TO_B,
                    diff = SyncDiff(
                        entityType = SyncEntityType.SONG,
                        entityId = song.songId,
                        status = SyncDiffStatus.MODIFIED_ON_A,
                        title = song.title,
                        aHash = song.fullSongHash
                    )
                )
            }

        val playlistItems = sourceManifest.playlists
            .filter { playlist -> playlist.identityKey() in selectedPlaylistIds }
            .map { playlist ->
                SyncPlanItem(
                    action = SyncPlanAction.UPDATE_PLAYLIST_ON_B,
                    diff = SyncDiff(
                        entityType = SyncEntityType.PLAYLIST,
                        entityId = playlist.identityKey(),
                        status = SyncDiffStatus.PLAYLIST_DIFFERENT,
                        title = playlist.playlistName,
                        aHash = playlist.fullPlaylistHash
                    )
                )
            }

        return SyncPlan(items = songItems + playlistItems)
    }

    private fun SmpSyncPlaylistEntry.identityKey(): String {
        return playlistId?.trim()?.takeIf { it.isNotEmpty() } ?: playlistName
    }
}
