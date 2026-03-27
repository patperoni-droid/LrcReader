package com.patrick.lrcreader.ui

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.patrick.lrcreader.core.light.LightSceneState
import com.patrick.lrcreader.core.light.RenderedLightState

private const val SIMULATOR_STROBE_HALF_PERIOD_MS = 250L
private const val SIMULATOR_STROBE_FLASH_COLOR_ARGB = 0xFFFFFFFFL

@Composable
fun LightSimulatorPreview(
    sceneState: LightSceneState,
    enabled: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val renderedState by produceState(
        initialValue = renderSimulatorState(sceneState, SystemClock.elapsedRealtime()),
        key1 = sceneState
    ) {
        while (true) {
            val nowMs = SystemClock.elapsedRealtime()
            value = renderSimulatorState(sceneState, nowMs)

            val effectivePositionMs = sceneState.estimatedPositionAt(nowMs)
            val fadeActive = sceneState.isPlaying && sceneState.hasAnimatedTransitionAt(effectivePositionMs)
            val strobeActive = sceneState.isPlaying && sceneState.hasActiveStrobeAt(effectivePositionMs)
            if (!fadeActive && !strobeActive) {
                break
            }
            withFrameNanos { }
        }
    }

    Box(
        modifier = modifier
            .size(58.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF111111))
            .clickable(enabled = enabled && onClick != null) {
                onClick?.invoke()
            }
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.18f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(6.dp)
    ) {
        SimulatedLightSurface(renderedState)
    }
}

private fun renderSimulatorState(
    sceneState: LightSceneState,
    realtimeMs: Long
): RenderedLightState {
    val baseState = sceneState.copy(strobeHz = null).renderAtRealtime(realtimeMs)
    val effectivePositionMs = sceneState.estimatedPositionAt(realtimeMs)
    if (!sceneState.isPlaying || !sceneState.hasActiveStrobeAt(effectivePositionMs)) {
        return baseState
    }

    val elapsedSinceCueMs = (effectivePositionMs - sceneState.cuePositionMs).coerceAtLeast(0L)
    val blinkOn = ((elapsedSinceCueMs / SIMULATOR_STROBE_HALF_PERIOD_MS) % 2L) == 0L
    return if (blinkOn) {
        RenderedLightState(
            colorArgb = SIMULATOR_STROBE_FLASH_COLOR_ARGB,
            intensity = 1f
        )
    } else {
        RenderedLightState(
            colorArgb = baseState.colorArgb,
            intensity = 1f
        )
    }
}

@Composable
private fun BoxScope.SimulatedLightSurface(renderedState: RenderedLightState) {
    val displayColor = scaleLightColor(
        colorArgb = renderedState.colorArgb,
        intensity = renderedState.intensity
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .align(Alignment.Center)
            .clip(RoundedCornerShape(8.dp))
            .background(displayColor)
    )
}

private fun scaleLightColor(
    colorArgb: Long,
    intensity: Float
): Color {
    val safeIntensity = intensity.coerceIn(0f, 1f)
    val base = Color(colorArgb)
    return Color(
        red = base.red * safeIntensity,
        green = base.green * safeIntensity,
        blue = base.blue * safeIntensity,
        alpha = 1f
    )
}
