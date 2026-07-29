package com.patrick.lrcreader.ui

import com.patrick.lrcreader.smp.LiveArrangementPlan
import com.patrick.lrcreader.smp.LiveArrangementOccurrence

object PlaybackStructureModelAdapter {
    fun from(plan: LiveArrangementPlan): PlaybackStructureModel? {
        val totalDurationMs = plan.durationMs
        if (totalDurationMs <= 0L || plan.occurrences.isEmpty()) return null

        return PlaybackStructureModel(
            segments = plan.occurrences.groupForDisplay().map { group ->
                PlaybackStructureSegment(
                    key = group.key,
                    label = if (group.repeatCount > 1) {
                        "${group.label} ×${group.repeatCount}"
                    } else {
                        group.label
                    },
                    fraction = (
                        group.durationMs.toDouble() /
                            totalDurationMs.toDouble()
                        ).toFloat(),
                    color = arrangementTrackOccurrenceColor(group.color)
                )
            }
        )
    }

    private data class DisplayGroup(
        val arrangementEntryKey: String?,
        val key: String,
        val label: String,
        val durationMs: Long,
        val color: String?,
        val repeatCount: Int
    )

    private fun List<LiveArrangementOccurrence>.groupForDisplay(): List<DisplayGroup> {
        val groups = mutableListOf<DisplayGroup>()

        forEach { occurrence ->
            val arrangementEntryKey = occurrence.key.arrangementEntryKey()
            val previous = groups.lastOrNull()
            if (
                arrangementEntryKey != null &&
                previous?.arrangementEntryKey == arrangementEntryKey &&
                previous.label == occurrence.label &&
                previous.color == occurrence.color
            ) {
                groups[groups.lastIndex] = previous.copy(
                    durationMs = previous.durationMs.saturatedPlus(occurrence.durationMs),
                    repeatCount = previous.repeatCount.saturatedIncrement()
                )
            } else {
                groups += DisplayGroup(
                    arrangementEntryKey = arrangementEntryKey,
                    key = occurrence.key,
                    label = occurrence.label,
                    durationMs = occurrence.durationMs,
                    color = occurrence.color,
                    repeatCount = 1
                )
            }
        }

        return groups
    }

    private fun String.arrangementEntryKey(): String? {
        val separatorIndex = lastIndexOf(':')
        if (separatorIndex <= 0 || separatorIndex == lastIndex) return null
        if (substring(separatorIndex + 1).toIntOrNull() == null) return null
        return substring(0, separatorIndex)
    }

    private fun Long.saturatedPlus(other: Long): Long =
        if (other > 0L && this > Long.MAX_VALUE - other) Long.MAX_VALUE else this + other

    private fun Int.saturatedIncrement(): Int =
        if (this == Int.MAX_VALUE) Int.MAX_VALUE else this + 1
}
