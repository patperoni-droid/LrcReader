package com.patrick.lrcreader.core.light

import kotlin.math.roundToLong

const val LIGHT_OFF_COLOR_ARGB = 0xFF000000L

data class LightSceneState(
    val trackUri: String? = null,
    val previousColorArgb: Long = LIGHT_OFF_COLOR_ARGB,
    val previousIntensity: Float = 0f,
    val targetColorArgb: Long = LIGHT_OFF_COLOR_ARGB,
    val targetIntensity: Float = 0f,
    val strobeHz: Float? = null,
    val cuePositionMs: Long = 0L,
    val fadeMs: Long = 0L,
    val anchorPositionMs: Long = 0L,
    val anchorRealtimeMs: Long = 0L,
    val isPlaying: Boolean = false,
    val playbackRate: Float = 1f,
    val hasCue: Boolean = false
) {
    fun estimatedPositionAt(realtimeMs: Long): Long {
        if (!isPlaying) {
            return anchorPositionMs.coerceAtLeast(0L)
        }

        val deltaRealtimeMs = (realtimeMs - anchorRealtimeMs).coerceAtLeast(0L)
        val projectedDelta = (deltaRealtimeMs * playbackRate.coerceAtLeast(0f)).roundToLong()
        return (anchorPositionMs + projectedDelta).coerceAtLeast(0L)
    }

    fun renderAtRealtime(realtimeMs: Long): RenderedLightState {
        return renderAtPosition(estimatedPositionAt(realtimeMs))
    }

    fun renderAtPosition(positionMs: Long): RenderedLightState {
        if (!hasCue) {
            return RenderedLightState(
                colorArgb = LIGHT_OFF_COLOR_ARGB,
                intensity = 0f
            )
        }

        val safePositionMs = positionMs.coerceAtLeast(0L)
        val progress = if (fadeMs <= 0L) {
            1f
        } else {
            ((safePositionMs - cuePositionMs).coerceAtLeast(0L).toFloat() / fadeMs.toFloat())
                .coerceIn(0f, 1f)
        }

        val baseColor = lerpArgb(previousColorArgb, targetColorArgb, progress)
        val baseIntensity = lerp(previousIntensity, targetIntensity, progress)
        val finalIntensity = strobeHz
            ?.takeIf { it > 0f }
            ?.let { hz ->
                val elapsedSinceCueMs = (safePositionMs - cuePositionMs).coerceAtLeast(0L)
                val periodMs = (1_000f / hz).coerceAtLeast(1f)
                val phase = (elapsedSinceCueMs / periodMs).toLong()
                if (phase % 2L == 0L) baseIntensity else 0f
            }
            ?: baseIntensity

        return RenderedLightState(
            colorArgb = baseColor,
            intensity = finalIntensity.coerceIn(0f, 1f)
        )
    }

    companion object {
        fun off(
            trackUri: String? = null,
            anchorPositionMs: Long = 0L,
            anchorRealtimeMs: Long = 0L,
            isPlaying: Boolean = false,
            playbackRate: Float = 1f
        ): LightSceneState {
            return LightSceneState(
                trackUri = trackUri?.takeIf { it.isNotBlank() },
                anchorPositionMs = anchorPositionMs.coerceAtLeast(0L),
                anchorRealtimeMs = anchorRealtimeMs.coerceAtLeast(0L),
                isPlaying = isPlaying,
                playbackRate = playbackRate.coerceAtLeast(0f),
                hasCue = false
            )
        }
    }
}

data class RenderedLightState(
    val colorArgb: Long,
    val intensity: Float
)

private fun lerp(start: Float, end: Float, progress: Float): Float {
    return start + ((end - start) * progress.coerceIn(0f, 1f))
}

private fun lerpArgb(start: Long, end: Long, progress: Float): Long {
    val p = progress.coerceIn(0f, 1f)
    val startA = (start shr 24 and 0xFF).toInt()
    val startR = (start shr 16 and 0xFF).toInt()
    val startG = (start shr 8 and 0xFF).toInt()
    val startB = (start and 0xFF).toInt()
    val endA = (end shr 24 and 0xFF).toInt()
    val endR = (end shr 16 and 0xFF).toInt()
    val endG = (end shr 8 and 0xFF).toInt()
    val endB = (end and 0xFF).toInt()

    val a = (startA + ((endA - startA) * p)).roundToLong()
    val r = (startR + ((endR - startR) * p)).roundToLong()
    val g = (startG + ((endG - startG) * p)).roundToLong()
    val b = (startB + ((endB - startB) * p)).roundToLong()

    return ((a and 0xFF) shl 24) or
        ((r and 0xFF) shl 16) or
        ((g and 0xFF) shl 8) or
        (b and 0xFF)
}
