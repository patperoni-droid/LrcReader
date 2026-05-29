package com.patrick.lrcreader.core.sync

class SmpSyncManifestComparator {

    fun compare(
        source: SmpSyncManifest,
        target: SmpSyncManifest,
        base: SmpSyncManifest? = null
    ): SyncPlan {
        val sourceSongIds = source.songs.map { it.songId }.toSet()
        val targetSongIds = target.songs.map { it.songId }.toSet()
        val availableSongIds = sourceSongIds + targetSongIds

        val items = buildList {
            addAll(compareSongs(source, target, base))
            addAll(comparePlaylists(source, target, base))
            addAll(compareFamilies(source, target, base))
            addAll(findBrokenPlaylistReferences(source, availableSongIds))
            addAll(findBrokenFamilyReferences(source, availableSongIds))
        }

        return SyncPlan(items = items.filterNot {
            it.diff.status == SyncDiffStatus.IDENTICAL
        })
    }

    private fun compareSongs(
        source: SmpSyncManifest,
        target: SmpSyncManifest,
        base: SmpSyncManifest?
    ): List<SyncPlanItem> {
        val sourceById = source.songs.associateBy { it.songId }
        val targetById = target.songs.associateBy { it.songId }
        val baseById = base?.songs.orEmpty().associateBy { it.songId }

        return compareEntityMaps(
            sourceById = sourceById,
            targetById = targetById,
            baseById = baseById,
            entityType = SyncEntityType.SONG,
            idOf = { it.songId },
            titleOf = { it.title },
            hashOf = { it.fullSongHash },
            differentStatus = SyncDiffStatus.MODIFIED_ON_A,
            actionForDifference = SyncPlanAction.COPY_TO_B
        )
    }

    private fun comparePlaylists(
        source: SmpSyncManifest,
        target: SmpSyncManifest,
        base: SmpSyncManifest?
    ): List<SyncPlanItem> {
        val sourceById = source.playlists.associateBy { it.identityKey() }
        val targetById = target.playlists.associateBy { it.identityKey() }
        val baseById = base?.playlists.orEmpty().associateBy { it.identityKey() }

        return compareEntityMaps(
            sourceById = sourceById,
            targetById = targetById,
            baseById = baseById,
            entityType = SyncEntityType.PLAYLIST,
            idOf = { it.identityKey() },
            titleOf = { it.playlistName },
            hashOf = { it.fullPlaylistHash },
            differentStatus = SyncDiffStatus.PLAYLIST_DIFFERENT,
            actionForDifference = SyncPlanAction.UPDATE_PLAYLIST_ON_B
        )
    }

    private fun compareFamilies(
        source: SmpSyncManifest,
        target: SmpSyncManifest,
        base: SmpSyncManifest?
    ): List<SyncPlanItem> {
        val sourceById = source.families.associateBy { it.familyId }
        val targetById = target.families.associateBy { it.familyId }
        val baseById = base?.families.orEmpty().associateBy { it.familyId }

        return compareEntityMaps(
            sourceById = sourceById,
            targetById = targetById,
            baseById = baseById,
            entityType = SyncEntityType.FAMILY,
            idOf = { it.familyId },
            titleOf = { it.title },
            hashOf = { it.hash },
            differentStatus = SyncDiffStatus.FAMILY_DIFFERENT,
            actionForDifference = SyncPlanAction.UPDATE_FAMILY_ON_B
        )
    }

    private fun <T> compareEntityMaps(
        sourceById: Map<String, T>,
        targetById: Map<String, T>,
        baseById: Map<String, T>,
        entityType: SyncEntityType,
        idOf: (T) -> String,
        titleOf: (T) -> String?,
        hashOf: (T) -> String,
        differentStatus: SyncDiffStatus,
        actionForDifference: SyncPlanAction
    ): List<SyncPlanItem> {
        val ids = (sourceById.keys + targetById.keys).sorted()
        return ids.map { id ->
            val source = sourceById[id]
            val target = targetById[id]
            val base = baseById[id]

            when {
                source == null && target != null -> SyncPlanItem(
                    action = SyncPlanAction.KEEP,
                    diff = SyncDiff(
                        entityType = entityType,
                        entityId = idOf(target),
                        status = SyncDiffStatus.ABSENT_ON_A,
                        title = titleOf(target),
                        bHash = hashOf(target)
                    )
                )

                source != null && target == null -> SyncPlanItem(
                    action = actionForMissingTarget(entityType),
                    diff = SyncDiff(
                        entityType = entityType,
                        entityId = idOf(source),
                        status = SyncDiffStatus.ABSENT_ON_B,
                        title = titleOf(source),
                        aHash = hashOf(source)
                    )
                )

                source != null && target != null -> compareExistingEntity(
                    source = source,
                    target = target,
                    base = base,
                    entityType = entityType,
                    idOf = idOf,
                    titleOf = titleOf,
                    hashOf = hashOf,
                    differentStatus = differentStatus,
                    actionForDifference = actionForDifference
                )

                else -> null
            }
        }.filterNotNull()
    }

    private fun <T> compareExistingEntity(
        source: T,
        target: T,
        base: T?,
        entityType: SyncEntityType,
        idOf: (T) -> String,
        titleOf: (T) -> String?,
        hashOf: (T) -> String,
        differentStatus: SyncDiffStatus,
        actionForDifference: SyncPlanAction
    ): SyncPlanItem {
        val sourceHash = hashOf(source)
        val targetHash = hashOf(target)
        val baseHash = base?.let(hashOf)

        if (sourceHash == targetHash) {
            return SyncPlanItem(
                action = SyncPlanAction.KEEP,
                diff = SyncDiff(
                    entityType = entityType,
                    entityId = idOf(source),
                    status = SyncDiffStatus.IDENTICAL,
                    title = titleOf(source),
                    aHash = sourceHash,
                    bHash = targetHash
                )
            )
        }

        val status = when {
            baseHash != null && sourceHash != baseHash && targetHash != baseHash -> {
                SyncDiffStatus.POSSIBLE_CONFLICT
            }
            baseHash != null && sourceHash == baseHash && targetHash != baseHash -> {
                SyncDiffStatus.MODIFIED_ON_B
            }
            else -> differentStatus
        }
        val action = when (status) {
            SyncDiffStatus.POSSIBLE_CONFLICT -> SyncPlanAction.REVIEW_CONFLICT
            SyncDiffStatus.MODIFIED_ON_B -> SyncPlanAction.KEEP
            else -> actionForDifference
        }

        return SyncPlanItem(
            action = action,
            diff = SyncDiff(
                entityType = entityType,
                entityId = idOf(source),
                status = status,
                title = titleOf(source),
                aHash = sourceHash,
                bHash = targetHash
            )
        )
    }

    private fun actionForMissingTarget(entityType: SyncEntityType): SyncPlanAction {
        return when (entityType) {
            SyncEntityType.PLAYLIST -> SyncPlanAction.UPDATE_PLAYLIST_ON_B
            SyncEntityType.FAMILY -> SyncPlanAction.UPDATE_FAMILY_ON_B
            else -> SyncPlanAction.COPY_TO_B
        }
    }

    private fun findBrokenPlaylistReferences(
        source: SmpSyncManifest,
        availableSongIds: Set<String>
    ): List<SyncPlanItem> {
        return source.playlists.mapNotNull { playlist ->
            val missing = playlist.songIds
                .filterNot { it in availableSongIds }
                .distinct()
                .sorted()
            if (missing.isEmpty()) return@mapNotNull null

            SyncPlanItem(
                action = SyncPlanAction.REVIEW_BROKEN_REFERENCE,
                diff = SyncDiff(
                    entityType = SyncEntityType.PLAYLIST,
                    entityId = playlist.identityKey(),
                    status = SyncDiffStatus.BROKEN_REFERENCE,
                    title = playlist.playlistName,
                    aHash = playlist.fullPlaylistHash,
                    brokenReferenceIds = missing
                )
            )
        }
    }

    private fun findBrokenFamilyReferences(
        source: SmpSyncManifest,
        availableSongIds: Set<String>
    ): List<SyncPlanItem> {
        return source.families.mapNotNull { family ->
            val refs = family.songIds + listOfNotNull(family.parentSongId, family.activeSongId)
            val missing = refs
                .filterNot { it in availableSongIds }
                .distinct()
                .sorted()
            if (missing.isEmpty()) return@mapNotNull null

            SyncPlanItem(
                action = SyncPlanAction.REVIEW_BROKEN_REFERENCE,
                diff = SyncDiff(
                    entityType = SyncEntityType.FAMILY,
                    entityId = family.familyId,
                    status = SyncDiffStatus.BROKEN_REFERENCE,
                    title = family.title,
                    aHash = family.hash,
                    brokenReferenceIds = missing
                )
            )
        }
    }

    private fun SmpSyncPlaylistEntry.identityKey(): String {
        return playlistId?.trim()?.takeIf { it.isNotEmpty() } ?: playlistName
    }
}
