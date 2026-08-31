package com.patrick.lrcreader.smp

data class ArrangementNavigationItem(
    val ownerSongId: String,
    val entryId: String,
    val name: String,
    val navigationPositionMs: Long,
    val repeatCount: Int
)

internal fun ArrangementData.toLinearNavigationItems(
    ownerSongId: String
): List<ArrangementNavigationItem> = entries
    .asSequence()
    .filterNot(ArrangementEntryData::muted)
    .map { entry ->
        ArrangementNavigationItem(
            ownerSongId = ownerSongId,
            entryId = entry.entryId,
            name = entry.name,
            navigationPositionMs = entry.startMs.coerceAtLeast(0L),
            repeatCount = entry.repeatCount.coerceAtLeast(1)
        )
    }
    .toList()

internal fun List<PreparedArrangementOccurrence>.toVirtualNavigationItems(
    ownerSongId: String
): List<ArrangementNavigationItem> = asSequence()
    .filter { occurrence -> occurrence.repeatIndex == 0 }
    .map { occurrence ->
        ArrangementNavigationItem(
            ownerSongId = ownerSongId,
            entryId = occurrence.segment.id,
            name = occurrence.segment.name,
            navigationPositionMs = occurrence.arrangementStartMs,
            repeatCount = occurrence.repeatCount.coerceAtLeast(1)
        )
    }
    .toList()
