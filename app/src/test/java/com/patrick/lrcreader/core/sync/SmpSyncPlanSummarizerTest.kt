package com.patrick.lrcreader.core.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmpSyncPlanSummarizerTest {

    private val summarizer = SmpSyncPlanSummarizer()

    @Test
    fun emptyPlan_keepsNoAutomaticDeletionReminder() {
        val summary = summarizer.summarize(SyncPlan())

        assertEquals(0, summary.songsIdentical)
        assertEquals(0, summary.songsAbsentOnB)
        assertEquals(0, summary.automaticDeletionCount)
        assertEquals(SmpSyncDeletionPolicy.NO_AUTOMATIC_DELETION, summary.deletionPolicy)
        assertFalse(summary.hasWork)
        assertEquals(
            listOf(SmpSyncPlanSummaryLineKind.NO_AUTOMATIC_DELETION),
            summary.lines.map { it.kind }
        )
    }

    @Test
    fun songCounters_areSummarized() {
        val summary = summarizer.summarize(
            SyncPlan(
                items = listOf(
                    item(SyncEntityType.SONG, "song_ok", SyncDiffStatus.IDENTICAL),
                    item(SyncEntityType.SONG, "song_new", SyncDiffStatus.ABSENT_ON_B),
                    item(SyncEntityType.SONG, "song_a", SyncDiffStatus.MODIFIED_ON_A),
                    item(SyncEntityType.SONG, "song_b", SyncDiffStatus.MODIFIED_ON_B)
                )
            )
        )

        assertEquals(1, summary.songsIdentical)
        assertEquals(1, summary.songsAbsentOnB)
        assertEquals(1, summary.songsModifiedOnA)
        assertEquals(1, summary.songsModifiedOnB)
        assertTrue(summary.hasWork)
        assertTrue(summary.lines.any { it.kind == SmpSyncPlanSummaryLineKind.SONGS_ABSENT_ON_B })
    }

    @Test
    fun conflicts_areSummarizedAsWarnings() {
        val summary = summarizer.summarize(
            SyncPlan(
                items = listOf(
                    item(SyncEntityType.SONG, "song_conflict", SyncDiffStatus.POSSIBLE_CONFLICT)
                )
            )
        )

        assertEquals(1, summary.possibleConflicts)
        assertEquals(
            SmpSyncPlanSummarySeverity.WARNING,
            summary.lines.first { it.kind == SmpSyncPlanSummaryLineKind.POSSIBLE_CONFLICTS }.severity
        )
    }

    @Test
    fun playlistAndFamilyDifferences_areSummarized() {
        val summary = summarizer.summarize(
            SyncPlan(
                items = listOf(
                    item(SyncEntityType.PLAYLIST, "Set Fiesta", SyncDiffStatus.PLAYLIST_DIFFERENT),
                    item(SyncEntityType.PLAYLIST, "Set New", SyncDiffStatus.ABSENT_ON_B),
                    item(SyncEntityType.FAMILY, "family_001", SyncDiffStatus.FAMILY_DIFFERENT),
                    item(SyncEntityType.FAMILY, "family_002", SyncDiffStatus.ABSENT_ON_B)
                )
            )
        )

        assertEquals(2, summary.playlistsDifferent)
        assertEquals(2, summary.familiesDifferent)
    }

    @Test
    fun brokenReferences_areSummarized() {
        val summary = summarizer.summarize(
            SyncPlan(
                items = listOf(
                    item(SyncEntityType.PLAYLIST, "Set Fiesta", SyncDiffStatus.BROKEN_REFERENCE),
                    item(SyncEntityType.FAMILY, "family_001", SyncDiffStatus.BROKEN_REFERENCE)
                )
            )
        )

        assertEquals(2, summary.brokenReferences)
        assertEquals(
            SmpSyncPlanSummarySeverity.WARNING,
            summary.lines.first { it.kind == SmpSyncPlanSummaryLineKind.BROKEN_REFERENCES }.severity
        )
    }

    @Test
    fun absentOnA_isNotCountedAsAutomaticDeletion() {
        val summary = summarizer.summarize(
            SyncPlan(
                items = listOf(
                    item(SyncEntityType.SONG, "song_only_on_b", SyncDiffStatus.ABSENT_ON_A)
                )
            )
        )

        assertEquals(1, summary.songsAbsentOnA)
        assertEquals(0, summary.automaticDeletionCount)
        assertEquals(SmpSyncDeletionPolicy.NO_AUTOMATIC_DELETION, summary.deletionPolicy)
        assertTrue(summary.lines.any { it.kind == SmpSyncPlanSummaryLineKind.NO_AUTOMATIC_DELETION })
    }

    private fun item(
        entityType: SyncEntityType,
        entityId: String,
        status: SyncDiffStatus
    ): SyncPlanItem {
        return SyncPlanItem(
            action = when (status) {
                SyncDiffStatus.POSSIBLE_CONFLICT -> SyncPlanAction.REVIEW_CONFLICT
                SyncDiffStatus.BROKEN_REFERENCE -> SyncPlanAction.REVIEW_BROKEN_REFERENCE
                SyncDiffStatus.ABSENT_ON_B -> SyncPlanAction.COPY_TO_B
                else -> SyncPlanAction.KEEP
            },
            diff = SyncDiff(
                entityType = entityType,
                entityId = entityId,
                status = status
            )
        )
    }
}
