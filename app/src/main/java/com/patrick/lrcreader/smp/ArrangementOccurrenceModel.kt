package com.patrick.lrcreader.smp

internal data class ArrangementOccurrenceProjection(
    val entries: List<ArrangementEntryData>,
    val segments: List<ArrangementSegmentData>,
    val structureSegmentIds: List<String>,
    val preservedLegacySegments: List<ArrangementSegmentData>
)

internal data class PreparedArrangementOccurrence(
    val entryIndex: Int,
    val repeatIndex: Int,
    val repeatCount: Int,
    val arrangementStartMs: Long,
    val durationMs: Long,
    val segment: ArrangementSegmentData
)

internal data class PreparedArrangementPlayhead(
    val entryIndex: Int,
    val repeatIndex: Int,
    val repeatCount: Int,
    val segmentProgressFraction: Float,
    val arrangementPositionMs: Long,
    val arrangementDurationMs: Long
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

internal fun prepareArrangementOccurrences(
    segments: List<ArrangementSegmentData>,
    structureSegmentIds: List<String>,
    entries: List<ArrangementEntryData>,
    useOccurrenceModel: Boolean
): List<PreparedArrangementOccurrence> {
    val segmentsById = segments.associateBy { segment -> segment.id }
    val entriesById = entries.associateBy { entry -> entry.entryId }
    return buildList {
        var arrangementStartMs = 0L
        structureSegmentIds.forEachIndexed { entryIndex, segmentId ->
            val segment = segmentsById[segmentId] ?: return@forEachIndexed
            val entry = entriesById[segmentId]
            if (useOccurrenceModel && entry?.muted == true) return@forEachIndexed
            val segmentStartMs = minOf(segment.startMs, segment.endMs)
                .coerceIn(0L, Long.MAX_VALUE - 1L)
            val segmentEndMs = maxOf(segment.startMs, segment.endMs)
                .coerceAtLeast(segmentStartMs + 1L)
            val segmentDurationMs = segmentEndMs - segmentStartMs
            val repeatCount = if (useOccurrenceModel) {
                entry?.repeatCount?.coerceAtLeast(1) ?: 1
            } else {
                1
            }
            repeat(repeatCount) { repeatIndex ->
                add(
                    PreparedArrangementOccurrence(
                        entryIndex = entryIndex,
                        repeatIndex = repeatIndex,
                        repeatCount = repeatCount,
                        arrangementStartMs = arrangementStartMs,
                        durationMs = segmentDurationMs,
                        segment = segment
                    )
                )
                arrangementStartMs = saturatedArrangementTimeAdd(
                    arrangementStartMs,
                    segmentDurationMs
                )
            }
        }
    }
}

internal fun resolvePreparedArrangementPlayheadFromSource(
    occurrences: List<PreparedArrangementOccurrence>,
    playbackIndex: Int,
    sourcePositionMs: Long
): PreparedArrangementPlayhead? {
    val occurrence = occurrences.getOrNull(playbackIndex) ?: return null
    val segmentStartMs = minOf(
        occurrence.segment.startMs,
        occurrence.segment.endMs
    ).coerceAtLeast(0L)
    val progressMs = (sourcePositionMs - segmentStartMs)
        .coerceIn(0L, occurrence.durationMs)
    return occurrence.toPlayhead(progressMs, occurrences.arrangementDurationMs())
}

internal fun resolvePreparedArrangementPlayheadFromTimeline(
    occurrences: List<PreparedArrangementOccurrence>,
    arrangementPositionMs: Long
): PreparedArrangementPlayhead? {
    val arrangementDurationMs = occurrences.arrangementDurationMs()
    if (occurrences.isEmpty() || arrangementDurationMs <= 0L) return null
    val safePositionMs = arrangementPositionMs.coerceIn(0L, arrangementDurationMs)
    val occurrence = if (safePositionMs >= arrangementDurationMs) {
        occurrences.last()
    } else {
        occurrences.firstOrNull { candidate ->
            safePositionMs < saturatedArrangementTimeAdd(
                candidate.arrangementStartMs,
                candidate.durationMs
            )
        } ?: occurrences.last()
    }
    val progressMs = (safePositionMs - occurrence.arrangementStartMs)
        .coerceIn(0L, occurrence.durationMs)
    return occurrence.toPlayhead(progressMs, arrangementDurationMs)
}

private fun PreparedArrangementOccurrence.toPlayhead(
    progressMs: Long,
    arrangementDurationMs: Long
): PreparedArrangementPlayhead = PreparedArrangementPlayhead(
    entryIndex = entryIndex,
    repeatIndex = repeatIndex,
    repeatCount = repeatCount,
    segmentProgressFraction = (progressMs.toDouble() / durationMs.toDouble())
        .coerceIn(0.0, 1.0)
        .toFloat(),
    arrangementPositionMs = saturatedArrangementTimeAdd(arrangementStartMs, progressMs),
    arrangementDurationMs = arrangementDurationMs
)

private fun List<PreparedArrangementOccurrence>.arrangementDurationMs(): Long {
    val lastOccurrence = lastOrNull() ?: return 0L
    return saturatedArrangementTimeAdd(
        lastOccurrence.arrangementStartMs,
        lastOccurrence.durationMs
    )
}

private fun saturatedArrangementTimeAdd(left: Long, right: Long): Long =
    if (right > Long.MAX_VALUE - left) Long.MAX_VALUE else left + right
