package com.patrick.lrcreader.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val ArrangementTrackBackgroundColor = Color(0xFF0B1014)
internal val ArrangementTrackSegmentShape = RoundedCornerShape(12.dp)
internal val ArrangementTrackSegmentMinHeight = 48.dp
internal val ArrangementTrackSegmentVisualInset = 4.dp
internal val ArrangementTrackSegmentNormalBorderWidth = 1.dp
internal val ArrangementTrackSegmentStateBorderWidth = 2.dp
internal val ArrangementTrackSegmentTextColor = Color.White
internal val ArrangementTrackSegmentTextSize = 14.sp
internal val ArrangementTrackSegmentTextWeight = FontWeight.Medium
internal val ArrangementTrackQueuedBorderColor = Color(0xFFFFD54F)

internal data class ArrangementTrackSegmentBorder(
    val width: Dp,
    val color: Color
)

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

internal fun arrangementTrackSegmentBorder(
    color: Color,
    isQueued: Boolean,
    isLooped: Boolean
): ArrangementTrackSegmentBorder = when {
    isLooped -> ArrangementTrackSegmentBorder(
        width = ArrangementTrackSegmentStateBorderWidth,
        color = lerp(color, Color.White, 0.42f)
    )
    isQueued -> ArrangementTrackSegmentBorder(
        width = ArrangementTrackSegmentStateBorderWidth,
        color = ArrangementTrackQueuedBorderColor
    )
    else -> ArrangementTrackSegmentBorder(
        width = ArrangementTrackSegmentNormalBorderWidth,
        color = color.copy(alpha = 0.82f)
    )
}
