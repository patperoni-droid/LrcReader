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
    "red" -> Color(0xFFA75F65)
    "blue" -> Color(0xFF4E7FA8)
    "green" -> Color(0xFF52866D)
    "violet" -> Color(0xFF856BA3)
    "orange", "amber" -> Color(0xFFA87349)
    "yellow" -> Color(0xFF968847)
    "gray" -> Color(0xFF6C7B86)
    else -> Color(0xFF364957)
}

internal fun arrangementTrackOccurrenceContainerColor(
    color: Color,
    isMuted: Boolean,
    isActive: Boolean,
    isQueued: Boolean
): Color = when {
    isMuted -> color.copy(alpha = 0.24f)
    isActive -> color.copy(alpha = 1f)
    isQueued -> color.copy(alpha = 0.90f)
    else -> color.copy(alpha = 0.80f)
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
