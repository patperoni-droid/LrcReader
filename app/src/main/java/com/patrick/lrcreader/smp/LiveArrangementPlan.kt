package com.patrick.lrcreader.smp

data class LiveArrangementPlan(
    val occurrences: List<LiveArrangementOccurrence>
) {
    val durationMs: Long = occurrences.fold(0L) { total, occurrence ->
        if (occurrence.durationMs > Long.MAX_VALUE - total) {
            Long.MAX_VALUE
        } else {
            total + occurrence.durationMs
        }
    }
}

data class LiveArrangementOccurrence(
    val key: String,
    val label: String,
    val durationMs: Long
)
