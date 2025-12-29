package com.patrick.lrcreader.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// ─────────────────────────────────────────────────────────────
//  1) VIEWPORT : texte avec marge top/bottom basée sur la hauteur écran
// ─────────────────────────────────────────────────────────────
@Composable
fun PrompterTextViewport(
    content: String,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    bgColor: Color = Color(0xFF050912),
    textColor: Color = Color.White,
    fontSize: Int = 26,
    lineHeight: Int = 32,
    startOffsetFraction: Float = 0.55f,
    bottomOffsetFraction: Float = 0.30f,
    horizontalPadding: Dp = 16.dp,
    verticalPadding: Dp = 8.dp
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        if (content.isBlank()) {
            Text(
                text = "Texte introuvable.",
                color = Color.Gray,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val topPad = maxHeight * startOffsetFraction
                val bottomPad = maxHeight * bottomOffsetFraction

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(horizontal = horizontalPadding, vertical = verticalPadding)
                ) {
                    Spacer(Modifier.height(topPad))

                    Text(
                        text = content,
                        color = textColor,
                        fontSize = fontSize.sp,
                        lineHeight = lineHeight.sp,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(bottomPad))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  2) CONTROLES : Play/Pause + slider vitesse
// ─────────────────────────────────────────────────────────────
@Composable
fun PrompterControls(
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    speed: Float,
    onSpeedChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    speedRange: ClosedFloatingPointRange<Float> = 0.3f..3f,
    labelColor: Color = Color(0xFFB0BEC5),
    iconColor: Color = Color.White
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onTogglePlay) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = "Play/Pause",
                    tint = iconColor
                )
            }
            Text(
                text = if (isPlaying) "Défilement" else "En pause",
                color = iconColor,
                fontSize = 14.sp
            )
        }

        Column(modifier = Modifier.width(200.dp)) {
            Text(
                text = "Vitesse (${String.format(java.util.Locale.US, "%.1fx", speed)})",
                color = labelColor,
                fontSize = 11.sp
            )
            Slider(
                value = speed,
                onValueChange = onSpeedChange,
                valueRange = speedRange
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  3) HEADER simple “retour + titre” (optionnel)
// ─────────────────────────────────────────────────────────────
@Composable
fun PrompterHeader(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Color.White
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Retour",
                tint = color
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(text = title, color = color, fontSize = 20.sp)
    }
}

// ─────────────────────────────────────────────────────────────
//  4) MOTEUR AUTO-SCROLL : anime jusqu’en bas selon vitesse
// ─────────────────────────────────────────────────────────────
@Composable
fun AutoScrollEffect(
    key1: Any?,
    isPlaying: Boolean,
    speed: Float,
    scrollState: ScrollState,
    baseDurationMs: Long = 60_000L,
    initialDelayMs: Long = 50L,
    extraDelayBeforeStartMs: Long = 0L
) {
    LaunchedEffect(key1, isPlaying, speed) {
        if (!isPlaying) return@LaunchedEffect

        delay(initialDelayMs)

        val max = scrollState.maxValue
        if (max <= 0) return@LaunchedEffect

        val clamped = speed.coerceIn(0.3f, 3f)
        val duration = (baseDurationMs / clamped).toInt().coerceAtLeast(500)

        if (extraDelayBeforeStartMs > 0) delay(extraDelayBeforeStartMs)

        scrollState.animateScrollTo(
            value = max,
            animationSpec = tween(durationMillis = duration, easing = LinearEasing)
        )
    }
}