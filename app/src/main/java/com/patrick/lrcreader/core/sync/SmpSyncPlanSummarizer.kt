package com.patrick.lrcreader.core.sync

class SmpSyncPlanSummarizer {

    fun summarize(plan: SyncPlan): SmpSyncPlanSummary {
        val items = plan.items
        val songsIdentical = items.countStatus(SyncEntityType.SONG, SyncDiffStatus.IDENTICAL)
        val songsAbsentOnB = items.countStatus(SyncEntityType.SONG, SyncDiffStatus.ABSENT_ON_B)
        val songsModifiedOnA = items.countStatus(SyncEntityType.SONG, SyncDiffStatus.MODIFIED_ON_A)
        val songsModifiedOnB = items.countStatus(SyncEntityType.SONG, SyncDiffStatus.MODIFIED_ON_B)
        val possibleConflicts = items.count { it.diff.status == SyncDiffStatus.POSSIBLE_CONFLICT }
        val playlistsDifferent = items.count {
            it.diff.entityType == SyncEntityType.PLAYLIST &&
                (
                    it.diff.status == SyncDiffStatus.PLAYLIST_DIFFERENT ||
                        it.diff.status == SyncDiffStatus.ABSENT_ON_B
                    )
        }
        val familiesDifferent = items.count {
            it.diff.entityType == SyncEntityType.FAMILY &&
                (
                    it.diff.status == SyncDiffStatus.FAMILY_DIFFERENT ||
                        it.diff.status == SyncDiffStatus.ABSENT_ON_B
                    )
        }
        val brokenReferences = items.count { it.diff.status == SyncDiffStatus.BROKEN_REFERENCE }
        val songsAbsentOnA = items.countStatus(SyncEntityType.SONG, SyncDiffStatus.ABSENT_ON_A)

        val summaryWithoutLines = SmpSyncPlanSummary(
            songsIdentical = songsIdentical,
            songsAbsentOnB = songsAbsentOnB,
            songsModifiedOnA = songsModifiedOnA,
            songsModifiedOnB = songsModifiedOnB,
            possibleConflicts = possibleConflicts,
            playlistsDifferent = playlistsDifferent,
            familiesDifferent = familiesDifferent,
            brokenReferences = brokenReferences,
            songsAbsentOnA = songsAbsentOnA,
            automaticDeletionCount = 0,
            deletionPolicy = SmpSyncDeletionPolicy.NO_AUTOMATIC_DELETION
        )

        return summaryWithoutLines.copy(
            lines = buildLines(summaryWithoutLines)
        )
    }

    private fun buildLines(summary: SmpSyncPlanSummary): List<SmpSyncPlanSummaryLine> {
        return buildList {
            addIfPositive(
                kind = SmpSyncPlanSummaryLineKind.SONGS_IDENTICAL,
                count = summary.songsIdentical,
                severity = SmpSyncPlanSummarySeverity.INFO
            )
            addIfPositive(
                kind = SmpSyncPlanSummaryLineKind.SONGS_ABSENT_ON_B,
                count = summary.songsAbsentOnB,
                severity = SmpSyncPlanSummarySeverity.ACTION
            )
            addIfPositive(
                kind = SmpSyncPlanSummaryLineKind.SONGS_MODIFIED_ON_A,
                count = summary.songsModifiedOnA,
                severity = SmpSyncPlanSummarySeverity.ACTION
            )
            addIfPositive(
                kind = SmpSyncPlanSummaryLineKind.SONGS_MODIFIED_ON_B,
                count = summary.songsModifiedOnB,
                severity = SmpSyncPlanSummarySeverity.INFO
            )
            addIfPositive(
                kind = SmpSyncPlanSummaryLineKind.POSSIBLE_CONFLICTS,
                count = summary.possibleConflicts,
                severity = SmpSyncPlanSummarySeverity.WARNING
            )
            addIfPositive(
                kind = SmpSyncPlanSummaryLineKind.PLAYLISTS_DIFFERENT,
                count = summary.playlistsDifferent,
                severity = SmpSyncPlanSummarySeverity.ACTION
            )
            addIfPositive(
                kind = SmpSyncPlanSummaryLineKind.FAMILIES_DIFFERENT,
                count = summary.familiesDifferent,
                severity = SmpSyncPlanSummarySeverity.ACTION
            )
            addIfPositive(
                kind = SmpSyncPlanSummaryLineKind.BROKEN_REFERENCES,
                count = summary.brokenReferences,
                severity = SmpSyncPlanSummarySeverity.WARNING
            )
            addIfPositive(
                kind = SmpSyncPlanSummaryLineKind.SONGS_ABSENT_ON_A,
                count = summary.songsAbsentOnA,
                severity = SmpSyncPlanSummarySeverity.INFO
            )
            add(
                SmpSyncPlanSummaryLine(
                    kind = SmpSyncPlanSummaryLineKind.NO_AUTOMATIC_DELETION,
                    count = summary.automaticDeletionCount,
                    severity = SmpSyncPlanSummarySeverity.INFO
                )
            )
        }
    }

    private fun MutableList<SmpSyncPlanSummaryLine>.addIfPositive(
        kind: SmpSyncPlanSummaryLineKind,
        count: Int,
        severity: SmpSyncPlanSummarySeverity
    ) {
        if (count <= 0) return
        add(
            SmpSyncPlanSummaryLine(
                kind = kind,
                count = count,
                severity = severity
            )
        )
    }

    private fun List<SyncPlanItem>.countStatus(
        entityType: SyncEntityType,
        status: SyncDiffStatus
    ): Int {
        return count { it.diff.entityType == entityType && it.diff.status == status }
    }
}
