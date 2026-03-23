package com.patrick.lrcreader.smp

const val DEFAULT_TIMELINE_NOTE_DURATION_MS = 10_000L

enum class TimelineMarkerKind(
    val storageValue: String,
    val defaultLabel: String
) {
    TEXT(
        storageValue = "text",
        defaultLabel = ""
    ),
    MIDI(
        storageValue = "midi",
        defaultLabel = "MIDI"
    ),
    NOTE(
        storageValue = "note",
        defaultLabel = "Note"
    ),
    DMX(
        storageValue = "dmx",
        defaultLabel = "DMX"
    );

    companion object {
        fun fromStorageValue(raw: String?): TimelineMarkerKind {
            return values().firstOrNull { kind ->
                kind.storageValue.equals(raw?.trim(), ignoreCase = true)
            } ?: TEXT
        }
    }
}

data class TimelineMarker(
    val timeMs: Long,
    val label: String,
    val kind: TimelineMarkerKind = TimelineMarkerKind.TEXT,
    val durationMs: Long? = null
)
