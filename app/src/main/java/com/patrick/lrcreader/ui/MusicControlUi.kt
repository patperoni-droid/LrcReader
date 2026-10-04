package com.patrick.lrcreader.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val MusicControlAccent = Color(0xFFFFC247)
internal val MusicControlBorder = Color.White.copy(alpha = 0.09f)

/** Visual slots only: Material retains gestures, steps, keyboard and accessibility semantics. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThinMusicSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    accent: Color = MusicControlAccent,
    thumbSize: Dp = 16.dp
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val controlColor = if (enabled) accent else accent.copy(alpha = 0.40f)
    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        modifier = modifier.height(48.dp),
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        thumb = {
            Box(
                Modifier.size(thumbSize)
                    .background(controlColor, CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
            )
        },
        track = { state ->
            Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                val fraction = ((state.value - valueRange.start) /
                    (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
                val y = size.height / 2f
                drawLine(Color(0xFF424548), Offset(0f, y), Offset(size.width, y),
                    strokeWidth = size.height, cap = StrokeCap.Round)
                if (fraction > 0f) {
                    drawLine(controlColor, Offset(if (isRtl) size.width else 0f, y),
                        Offset(size.width * (if (isRtl) 1f - fraction else fraction), y),
                        strokeWidth = size.height, cap = StrokeCap.Round)
                }
                if (steps > 0) {
                    for (index in 1..steps) {
                        drawCircle(controlColor.copy(alpha = 0.70f), radius = 1.dp.toPx(),
                            center = Offset(size.width * index / (steps + 1), y))
                    }
                }
            }
        }
    )
}
