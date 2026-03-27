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
    val durationMs: Long? = null,
    val fadeOutMs: Long? = null,
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
        if (isCueExpiredAt(safePositionMs)) {
            return RenderedLightState(
                colorArgb = LIGHT_OFF_COLOR_ARGB,
                intensity = 0f
            )
        }

        val fadeOutStartMs = fadeOutStartPositionMs()
        if (fadeOutStartMs != null && safePositionMs >= fadeOutStartMs) {
            val fadeOutDuration = fadeOutMs?.coerceAtLeast(0L) ?: 0L
            if (fadeOutDuration <= 0L) {
                return RenderedLightState(
                    colorArgb = LIGHT_OFF_COLOR_ARGB,
                    intensity = 0f
                )
            }
            val startState = renderBeforeAutoOffAt(fadeOutStartMs)
            val fadeOutProgress = ((safePositionMs - fadeOutStartMs).toFloat() / fadeOutDuration.toFloat())
                .coerceIn(0f, 1f)
            return RenderedLightState(
                colorArgb = lerpArgb(startState.colorArgb, LIGHT_OFF_COLOR_ARGB, fadeOutProgress),
                intensity = lerp(startState.intensity, 0f, fadeOutProgress).coerceIn(0f, 1f)
            )
        }

        return renderBeforeAutoOffAt(safePositionMs)
    }

    fun hasActiveStrobeAt(positionMs: Long): Boolean {
        if (!hasCue || strobeHz == null) {
            return false
        }
        val safePositionMs = positionMs.coerceAtLeast(0L)
        if (isCueExpiredAt(safePositionMs)) {
            return false
        }
        val fadeOutStartMs = fadeOutStartPositionMs()
        return fadeOutStartMs == null || safePositionMs < fadeOutStartMs
    }

    fun hasAnimatedTransitionAt(positionMs: Long): Boolean {
        if (!hasCue) {
            return false
        }
        val safePositionMs = positionMs.coerceAtLeast(0L)
        val fadeOutStartMs = fadeOutStartPositionMs()
        if (fadeOutStartMs != null && safePositionMs >= fadeOutStartMs) {
            val fadeOutEndMs = fadeOutEndPositionMs()
            return fadeOutEndMs != null &&
                (fadeOutMs?.coerceAtLeast(0L) ?: 0L) > 0L &&
                safePositionMs < fadeOutEndMs
        }
        val fadeInActive = fadeMs > 0L && safePositionMs < cuePositionMs + fadeMs
        val fadeOutEndMs = fadeOutEndPositionMs()
        val fadeOutActive = fadeOutStartMs != null && fadeOutEndMs != null && safePositionMs < fadeOutEndMs
        return fadeInActive || fadeOutActive
    }

    private fun renderBeforeAutoOffAt(positionMs: Long): RenderedLightState {
        val progress = if (fadeMs <= 0L) {
            1f
        } else {
            ((positionMs.coerceAtLeast(0L) - cuePositionMs).coerceAtLeast(0L).toFloat() / fadeMs.toFloat())
                .coerceIn(0f, 1f)
        }

        val baseColor = lerpArgb(previousColorArgb, targetColorArgb, progress)
        val baseIntensity = lerp(previousIntensity, targetIntensity, progress)
        val finalIntensity = strobeHz
            ?.takeIf { it > 0f }
            ?.let { hz ->
                val elapsedSinceCueMs = (positionMs.coerceAtLeast(0L) - cuePositionMs).coerceAtLeast(0L)
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

    private fun fadeOutStartPositionMs(): Long? {
        return durationMs?.coerceAtLeast(0L)?.let { safeDuration ->
            cuePositionMs.coerceAtLeast(0L) + safeDuration
        }
    }

    private fun fadeOutEndPositionMs(): Long? {
        val start = fadeOutStartPositionMs() ?: return null
        val safeFadeOut = fadeOutMs?.coerceAtLeast(0L) ?: 0L
        return start + safeFadeOut
    }

    private fun isCueExpiredAt(positionMs: Long): Boolean {
        val fadeOutStartMs = fadeOutStartPositionMs() ?: return false
        val fadeOutDuration = fadeOutMs?.coerceAtLeast(0L) ?: 0L
        return if (fadeOutDuration <= 0L) {
            positionMs >= fadeOutStartMs
        } else {
            positionMs >= (fadeOutStartMs + fadeOutDuration)
        }
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
