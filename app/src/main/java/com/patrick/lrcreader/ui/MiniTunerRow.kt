package com.patrick.lrcreader.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun MiniTunerRow(
    noteString: String,
    centsOffset: Float,
    isInTune: Boolean,
    hasSignal: Boolean,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    onEnable: (() -> Unit)? = null
) {
    val clamped = centsOffset.coerceIn(-50f, 50f)
    val showCents = hasSignal && isActive
    val centerLocked = showCents && abs(clamped) < MINI_TUNER_CENTER_LOCK_TOLERANCE_CENTS
    val displayCents = if (centerLocked) 0f else clamped
    val visualInTune = isInTune || centerLocked
    val target = if (showCents) displayCents / 50f else 0f
    val animatedPos by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 140),
        label = "miniTunerNeedle"
    )

    val noteColor = when {
        !isActive -> Color(0xFF90A4AE)
        visualInTune -> Color(0xFF66BB6A)
        else -> Color(0xFFFFECB3)
    }
    val barColor = if (visualInTune && showCents) Color(0xFF66BB6A) else Color(0xFF78909C)
    val compactNote = compactNoteName(noteString)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(30.dp)
            .background(Color(0xFF111111), RoundedCornerShape(8.dp))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = if (showCents) compactNote else "—",
            color = noteColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(24.dp)
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .height(14.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val midX = w / 2f
                val midY = h / 2f
                val usable = w * 0.47f

                drawLine(
                    color = barColor.copy(alpha = 0.28f),
                    start = Offset(0f, midY),
                    end = Offset(w, midY),
                    strokeWidth = 4f
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.45f),
                    start = Offset(midX, 1f),
                    end = Offset(midX, h - 1f),
                    strokeWidth = 3f
                )

                val x = midX + (animatedPos * usable)
                drawCircle(
                    color = if (showCents) barColor else Color(0xFF546E7A),
                    radius = 5f,
                    center = Offset(x, midY)
                )
            }
        }

        if (showCents) {
            val sign = if (displayCents > 0f) "+" else ""
            Text(
                text = "$sign${displayCents.roundToInt()}c",
                color = if (visualInTune) Color(0xFF66BB6A) else Color(0xFFB0BEC5),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        } else if (isActive) {
            Text(
                text = "…",
                color = Color(0xFF90A4AE),
                fontSize = 11.sp
            )
        } else if (onEnable != null) {
            Text(
                text = "OFF",
                color = Color(0xFF90A4AE),
                fontSize = 10.sp
            )
            TextButton(
                onClick = onEnable,
                modifier = Modifier.height(22.dp)
            ) {
                Text(
                    text = "ON",
                    color = Color(0xFF80DEEA),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        } else {
            Spacer(Modifier.width(4.dp))
        }
    }
}

private fun compactNoteName(note: String): String {
    if (note.isBlank()) return "—"
    val withoutOctave = note.takeWhile { !it.isDigit() }
    return if (withoutOctave.isBlank() || withoutOctave == "—") "—" else withoutOctave
}

fun isTunerInTune(centsOffset: Float?, toleranceCents: Float = 5f): Boolean {
    val cents = centsOffset ?: return false
    return abs(cents) < toleranceCents
}

private const val MINI_TUNER_CENTER_LOCK_TOLERANCE_CENTS = 5f
