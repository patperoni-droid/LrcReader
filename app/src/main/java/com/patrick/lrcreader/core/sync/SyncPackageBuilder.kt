package com.patrick.lrcreader.core.sync

class SyncPackageBuilder(
    private val estimateFullSongBytes: (SmpSyncSongEntry) -> Long? = { null }
) {

    fun build(
        sourceManifest: SmpSyncManifest,
        plan: SyncPlan,
        generatedAt: Long = System.currentTimeMillis()
    ): SmpSyncPackage {
        val sourceSongs = sourceManifest.songs.associateBy { it.songId }
        val sourcePlaylists = sourceManifest.playlists.associateBy { it.identityKey() }
        val sourceFamilies = sourceManifest.families.associateBy { it.familyId }

        val items = plan.items.mapNotNull { planItem ->
            when (planItem.diff.entityType) {
                SyncEntityType.SONG -> buildSongItem(planItem, sourceSongs)
                SyncEntityType.PLAYLIST -> buildPlaylistItem(planItem, sourcePlaylists)
                SyncEntityType.FAMILY -> buildFamilyItem(planItem, sourceFamilies)
                SyncEntityType.GLOBAL_STATE -> buildGlobalStateItem(planItem, sourceManifest)
            }
        }

        return SmpSyncPackage(
            generatedAt = generatedAt,
            sourceDeviceId = sourceManifest.deviceId,
            items = items
        )
    }

    private fun buildSongItem(
        planItem: SyncPlanItem,
        sourceSongs: Map<String, SmpSyncSongEntry>
    ): SmpSyncPackageItem? {
        if (planItem.action != SyncPlanAction.COPY_TO_B) return null
        if (planItem.diff.status != SyncDiffStatus.ABSENT_ON_B &&
            planItem.diff.status != SyncDiffStatus.MODIFIED_ON_A
        ) {
            return null
        }

        val song = sourceSongs[planItem.diff.entityId] ?: return null
        return SmpSyncPackageItem(
            kind = SmpSyncPackageKind.SONG_FULL,
            entityId = song.songId,
            title = song.title,
            sourceHash = song.fullSongHash,
            estimatedBytes = estimateFullSongBytes(song)
        )
    }

    private fun buildPlaylistItem(
        planItem: SyncPlanItem,
        sourcePlaylists: Map<String, SmpSyncPlaylistEntry>
    ): SmpSyncPackageItem? {
        if (planItem.action != SyncPlanAction.UPDATE_PLAYLIST_ON_B) return null
        if (planItem.diff.status != SyncDiffStatus.ABSENT_ON_B &&
            planItem.diff.status != SyncDiffStatus.PLAYLIST_DIFFERENT
        ) {
            return null
        }

        val playlist = sourcePlaylists[planItem.diff.entityId] ?: return null
        return SmpSyncPackageItem(
            kind = SmpSyncPackageKind.PLAYLIST_STATE,
            entityId = playlist.identityKey(),
            title = playlist.playlistName,
            sourceHash = playlist.fullPlaylistHash
        )
    }

    private fun buildFamilyItem(
        planItem: SyncPlanItem,
        sourceFamilies: Map<String, SmpSyncFamilyEntry>
    ): SmpSyncPackageItem? {
        if (planItem.action != SyncPlanAction.UPDATE_FAMILY_ON_B) return null
        if (planItem.diff.status != SyncDiffStatus.ABSENT_ON_B &&
            planItem.diff.status != SyncDiffStatus.FAMILY_DIFFERENT
        ) {
            return null
        }

        val family = sourceFamilies[planItem.diff.entityId] ?: return null
        return SmpSyncPackageItem(
            kind = SmpSyncPackageKind.FAMILY_STATE,
            entityId = family.familyId,
            title = family.title,
            sourceHash = family.hash
        )
    }

    private fun buildGlobalStateItem(
        planItem: SyncPlanItem,
        sourceManifest: SmpSyncManifest
    ): SmpSyncPackageItem? {
        if (planItem.action != SyncPlanAction.COPY_TO_B) return null
        if (planItem.diff.status != SyncDiffStatus.ABSENT_ON_B &&
            planItem.diff.status != SyncDiffStatus.MODIFIED_ON_A
        ) {
            return null
        }

        val globalState = sourceManifest.globalState ?: return null
        return SmpSyncPackageItem(
            kind = SmpSyncPackageKind.GLOBAL_STATE,
            entityId = planItem.diff.entityId,
            sourceHash = globalState.stateHash
        )
    }

    private fun SmpSyncPlaylistEntry.identityKey(): String {
        return playlistId?.trim()?.takeIf { it.isNotEmpty() } ?: playlistName
    }
}
