package com.patrick.lrcreader.core.sync

data class SmpSyncPlanSummary(
    val songsIdentical: Int = 0,
    val songsAbsentOnB: Int = 0,
    val songsModifiedOnA: Int = 0,
    val songsModifiedOnB: Int = 0,
    val possibleConflicts: Int = 0,
    val playlistsDifferent: Int = 0,
    val familiesDifferent: Int = 0,
    val brokenReferences: Int = 0,
    val songsAbsentOnA: Int = 0,
    val automaticDeletionCount: Int = 0,
    val deletionPolicy: SmpSyncDeletionPolicy = SmpSyncDeletionPolicy.NO_AUTOMATIC_DELETION,
    val lines: List<SmpSyncPlanSummaryLine> = emptyList()
) {
    val hasWork: Boolean
        get() = lines.any { it.kind != SmpSyncPlanSummaryLineKind.NO_AUTOMATIC_DELETION }
}

data class SmpSyncPlanSummaryLine(
    val kind: SmpSyncPlanSummaryLineKind,
    val count: Int,
    val severity: SmpSyncPlanSummarySeverity = SmpSyncPlanSummarySeverity.INFO
)

enum class SmpSyncPlanSummaryLineKind {
    SONGS_IDENTICAL,
    SONGS_ABSENT_ON_B,
    SONGS_MODIFIED_ON_A,
    SONGS_MODIFIED_ON_B,
    POSSIBLE_CONFLICTS,
    PLAYLISTS_DIFFERENT,
    FAMILIES_DIFFERENT,
    BROKEN_REFERENCES,
    SONGS_ABSENT_ON_A,
    NO_AUTOMATIC_DELETION
}

enum class SmpSyncPlanSummarySeverity {
    INFO,
    ACTION,
    WARNING
}

enum class SmpSyncDeletionPolicy {
    NO_AUTOMATIC_DELETION
}
