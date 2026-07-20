package com.patrick.lrcreader.smp

internal data class ArrangementOccurrenceProjection(
    val entries: List<ArrangementEntryData>,
    val segments: List<ArrangementSegmentData>,
    val structureSegmentIds: List<String>,
    val preservedLegacySegments: List<ArrangementSegmentData>
)

internal fun ArrangementData.toOccurrenceProjection(): ArrangementOccurrenceProjection {
    val occurrenceSegments = entries.map { entry -> entry.toSegmentData() }
    val occurrenceIds = occurrenceSegments.mapTo(linkedSetOf()) { segment -> segment.id }
    return ArrangementOccurrenceProjection(
        entries = entries,
        segments = occurrenceSegments,
        structureSegmentIds = entries.map { entry -> entry.entryId },
        preservedLegacySegments = segments.filterNot { segment -> segment.id in occurrenceIds }
    )
}

internal fun reconcileArrangementEntries(
    segments: List<ArrangementSegmentData>,
    structureSegmentIds: List<String>,
    existingEntries: List<ArrangementEntryData>
): List<ArrangementEntryData> {
    val segmentsById = segments.associateBy { segment -> segment.id }
    val existingEntriesById = existingEntries.associateBy { entry -> entry.entryId }
    return structureSegmentIds.mapNotNull { entryId ->
        val segment = segmentsById[entryId] ?: return@mapNotNull null
        val existingEntry = existingEntriesById[entryId]
        ArrangementEntryData(
            entryId = entryId,
            name = segment.name,
            startMs = segment.startMs,
            endMs = segment.endMs,
            repeatCount = existingEntry?.repeatCount ?: 1,
            muted = existingEntry?.muted ?: false,
            color = existingEntry?.color
        )
    }
}

internal fun buildArrangementDataForPersistence(
    useOccurrenceModel: Boolean,
    name: String,
    sourceSongId: String,
    segments: List<ArrangementSegmentData>,
    structureSegmentIds: List<String>,
    existingEntries: List<ArrangementEntryData>,
    preservedLegacySegments: List<ArrangementSegmentData>
): ArrangementData {
    if (!useOccurrenceModel) {
        return ArrangementData(
            name = name,
            sourceSongId = sourceSongId,
            segments = segments,
            structureSegmentIds = structureSegmentIds
        )
    }

    val entries = reconcileArrangementEntries(
        segments = segments,
        structureSegmentIds = structureSegmentIds,
        existingEntries = existingEntries
    )
    val entrySegments = entries.map { entry -> entry.toSegmentData() }
    val entryIds = entrySegments.mapTo(linkedSetOf()) { segment -> segment.id }
    val compatibleSegments = entrySegments + preservedLegacySegments.filterNot { segment ->
        segment.id in entryIds
    }
    return ArrangementData(
        version = 2,
        name = name,
        sourceSongId = sourceSongId,
        segments = compatibleSegments,
        structureSegmentIds = entries.map { entry -> entry.entryId },
        entries = entries
    )
}

internal fun ArrangementEntryData.toSegmentData(): ArrangementSegmentData =
    ArrangementSegmentData(
        id = entryId,
        name = name,
        startMs = startMs,
        endMs = endMs
    )
