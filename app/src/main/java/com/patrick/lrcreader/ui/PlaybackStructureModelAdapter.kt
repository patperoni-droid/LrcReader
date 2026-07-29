package com.patrick.lrcreader.ui

import com.patrick.lrcreader.smp.LiveArrangementPlan

object PlaybackStructureModelAdapter {
    fun from(plan: LiveArrangementPlan): PlaybackStructureModel? {
        val totalDurationMs = plan.durationMs
        if (totalDurationMs <= 0L || plan.occurrences.isEmpty()) return null

        return PlaybackStructureModel(
            segments = plan.occurrences.map { occurrence ->
                PlaybackStructureSegment(
                    key = occurrence.key,
                    label = occurrence.label,
                    fraction = (
                        occurrence.durationMs.toDouble() /
                            totalDurationMs.toDouble()
                        ).toFloat(),
                    color = arrangementTrackOccurrenceColor(occurrence.color)
                )
            }
        )
    }
}
