package com.patrick.lrcreader.ui

import androidx.compose.ui.graphics.Color

internal val ArrangementTrackBackgroundColor = Color(0xFF0B1014)

internal fun arrangementTrackOccurrenceColor(color: String?): Color = when (color) {
    "red" -> Color(0xFF6D2A2A)
    "blue" -> Color(0xFF244A73)
    "green" -> Color(0xFF285E3A)
    "violet" -> Color(0xFF56336F)
    "orange", "amber" -> Color(0xFF74471F)
    "yellow" -> Color(0xFF665B1F)
    "gray" -> Color(0xFF455A64)
    else -> Color(0xFF1C2933)
}

internal fun arrangementTrackOccurrenceContainerColor(
    color: Color,
    isMuted: Boolean,
    isActive: Boolean,
    isQueued: Boolean
): Color = when {
    isMuted -> color.copy(alpha = 0.24f)
    isActive -> color.copy(alpha = 0.82f)
    isQueued -> color.copy(alpha = 0.68f)
    else -> color.copy(alpha = 0.58f)
}
