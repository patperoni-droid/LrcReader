package com.patrick.lrcreader.core.light

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.patrick.lrcreader.smp.SmpLightCueBridge
import kotlinx.coroutines.flow.StateFlow

object LightCueDispatcher {

    private const val TAG = "LightCueDispatcher"
    private const val TRACE_TAG = "LIGHT_CUE_TRACE"

    private val runtime = LightCueRuntime()
    private val outputs: List<LightOutput> = listOf(SimulatorLightOutput)

    private var appContext: Context? = null

    val sceneState: StateFlow<LightSceneState> = SimulatorLightOutput.sceneState

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun warmTrack(trackUri: String?): Boolean {
        val key = trackUri?.takeIf { it.isNotBlank() } ?: return false
        val context = appContext ?: return false
        return SmpLightCueBridge.getRuntimeCues(context, key).orEmpty().isNotEmpty()
    }

    fun advance(trackUri: String?, positionMs: Long, isPlaying: Boolean) {
        val key = trackUri?.takeIf { it.isNotBlank() } ?: return
        val context = appContext ?: return
        val cues = SmpLightCueBridge.getRuntimeCues(context, key).orEmpty()
        if (cues.isEmpty()) {
            return
        }

        val nowMs = SystemClock.elapsedRealtime()
        val nextScene = runtime.advance(
            trackUri = key,
            cues = cues,
            positionMs = positionMs.coerceAtLeast(0L),
            isPlaying = isPlaying,
            realtimeMs = nowMs
        )
        publish(nextScene)
        Log.d(
            TRACE_TAG,
            "ADVANCE track=$key positionMs=$positionMs isPlaying=$isPlaying cuePositionMs=${nextScene.cuePositionMs} hasCue=${nextScene.hasCue}"
        )
    }

    fun syncToPosition(trackUri: String?, positionMs: Long) {
        val key = trackUri?.takeIf { it.isNotBlank() } ?: return
        val context = appContext ?: return
        val cues = SmpLightCueBridge.getRuntimeCues(context, key).orEmpty()
        if (cues.isEmpty()) {
            val resetScene = runtime.reset(trackUri = key, realtimeMs = SystemClock.elapsedRealtime())
            publish(resetScene)
            return
        }

        val nowMs = SystemClock.elapsedRealtime()
        val nextScene = runtime.syncToPosition(
            trackUri = key,
            cues = cues,
            positionMs = positionMs.coerceAtLeast(0L),
            realtimeMs = nowMs
        )
        publish(nextScene)
        Log.d(
            TRACE_TAG,
            "SYNC track=$key positionMs=$positionMs cuePositionMs=${nextScene.cuePositionMs} hasCue=${nextScene.hasCue}"
        )
    }

    fun reset(trackUri: String?) {
        val key = trackUri?.takeIf { it.isNotBlank() } ?: return
        publish(runtime.reset(trackUri = key, realtimeMs = SystemClock.elapsedRealtime()))
        Log.d(TAG, "reset: $key")
        Log.d(TRACE_TAG, "RESET track=$key")
    }

    fun resetGlobal() {
        publish(runtime.resetGlobal(realtimeMs = SystemClock.elapsedRealtime()))
        Log.d(TAG, "resetGlobal")
        Log.d(TRACE_TAG, "RESET_GLOBAL")
    }

    private fun publish(sceneState: LightSceneState) {
        outputs.forEach { output -> output.apply(sceneState) }
    }
}

internal class LightCueRuntime {

    private data class PlaybackObservation(
        val positionMs: Long,
        val realtimeMs: Long,
        val playbackRate: Float
    )

    private val lastObservationByTrack: MutableMap<String, PlaybackObservation> = mutableMapOf()
    private var currentScene: LightSceneState = LightSceneState.off()

    fun advance(
        trackUri: String,
        cues: List<LightCue>,
        positionMs: Long,
        isPlaying: Boolean,
        realtimeMs: Long
    ): LightSceneState {
        val safePositionMs = positionMs.coerceAtLeast(0L)
        val previousObservation = lastObservationByTrack[trackUri]
        val previousRate = previousObservation?.playbackRate ?: currentScene.playbackRate
        val playbackRate = resolvePlaybackRate(
            previousObservation = previousObservation,
            currentPositionMs = safePositionMs,
            currentRealtimeMs = realtimeMs,
            isPlaying = isPlaying,
            fallbackRate = previousRate
        )

        if (currentScene.trackUri != null && currentScene.trackUri != trackUri) {
            currentScene = LightSceneState.off(
                trackUri = trackUri,
                anchorPositionMs = safePositionMs,
                anchorRealtimeMs = realtimeMs,
                isPlaying = isPlaying,
                playbackRate = playbackRate
            )
        }

        val nextScene = when {
            previousObservation == null -> syncToPosition(
                trackUri = trackUri,
                cues = cues,
                positionMs = safePositionMs,
                realtimeMs = realtimeMs,
                isPlaying = isPlaying,
                playbackRate = playbackRate
            )

            safePositionMs < previousObservation.positionMs -> syncToPosition(
                trackUri = trackUri,
                cues = cues,
                positionMs = safePositionMs,
                realtimeMs = realtimeMs,
                isPlaying = isPlaying,
                playbackRate = playbackRate
            )

            !isPlaying -> updateAnchor(
                sceneState = currentScene.withTrackUri(trackUri),
                positionMs = safePositionMs,
                realtimeMs = realtimeMs,
                isPlaying = false,
                playbackRate = playbackRate
            )

            else -> {
                val dueCues = cuesBetween(
                    cues = cues,
                    fromExclusiveMs = previousObservation.positionMs,
                    toInclusiveMs = safePositionMs
                )
                val appliedScene = dueCues.fold(currentScene.withTrackUri(trackUri)) { scene, cue ->
                    applyCue(
                        sceneState = scene,
                        cue = cue,
                        currentPositionMs = safePositionMs,
                        realtimeMs = realtimeMs,
                        isPlaying = isPlaying,
                        playbackRate = playbackRate
                    )
                }
                updateAnchor(
                    sceneState = appliedScene,
                    positionMs = safePositionMs,
                    realtimeMs = realtimeMs,
                    isPlaying = isPlaying,
                    playbackRate = playbackRate
                )
            }
        }

        currentScene = nextScene
        lastObservationByTrack[trackUri] = PlaybackObservation(
            positionMs = safePositionMs,
            realtimeMs = realtimeMs,
            playbackRate = playbackRate
        )
        return currentScene
    }

    fun syncToPosition(
        trackUri: String,
        cues: List<LightCue>,
        positionMs: Long,
        realtimeMs: Long
    ): LightSceneState {
        val playbackRate = lastObservationByTrack[trackUri]?.playbackRate ?: currentScene.playbackRate
        return syncToPosition(
            trackUri = trackUri,
            cues = cues,
            positionMs = positionMs,
            realtimeMs = realtimeMs,
            isPlaying = false,
            playbackRate = playbackRate
        ).also { scene ->
            currentScene = scene
            lastObservationByTrack[trackUri] = PlaybackObservation(
                positionMs = positionMs.coerceAtLeast(0L),
                realtimeMs = realtimeMs,
                playbackRate = playbackRate
            )
        }
    }

    fun reset(trackUri: String, realtimeMs: Long): LightSceneState {
        lastObservationByTrack.remove(trackUri)
        if (currentScene.trackUri == trackUri) {
            currentScene = LightSceneState.off(anchorRealtimeMs = realtimeMs)
        }
        return currentScene
    }

    fun resetGlobal(realtimeMs: Long): LightSceneState {
        lastObservationByTrack.clear()
        currentScene = LightSceneState.off(anchorRealtimeMs = realtimeMs)
        return currentScene
    }

    private fun syncToPosition(
        trackUri: String,
        cues: List<LightCue>,
        positionMs: Long,
        realtimeMs: Long,
        isPlaying: Boolean,
        playbackRate: Float
    ): LightSceneState {
        val safePositionMs = positionMs.coerceAtLeast(0L)
        val baseScene = LightSceneState.off(
            trackUri = trackUri,
            anchorPositionMs = safePositionMs,
            anchorRealtimeMs = realtimeMs,
            isPlaying = isPlaying,
            playbackRate = playbackRate
        )
        val synced = cues
            .asSequence()
            .filter { cue -> cue.timeMs <= safePositionMs }
            .fold(baseScene) { scene, cue ->
                applyCue(
                    sceneState = scene,
                    cue = cue,
                    currentPositionMs = safePositionMs,
                    realtimeMs = realtimeMs,
                    isPlaying = isPlaying,
                    playbackRate = playbackRate
                )
            }

        return updateAnchor(
            sceneState = synced,
            positionMs = safePositionMs,
            realtimeMs = realtimeMs,
            isPlaying = isPlaying,
            playbackRate = playbackRate
        )
    }

    private fun applyCue(
        sceneState: LightSceneState,
        cue: LightCue,
        currentPositionMs: Long,
        realtimeMs: Long,
        isPlaying: Boolean,
        playbackRate: Float
    ): LightSceneState {
        val frameAtCue = sceneState.renderAtPosition(cue.timeMs)
        val nextTargetColor = when (val action = cue.action) {
            is LightAction.Color -> action.argb
            LightAction.Blackout -> LIGHT_OFF_COLOR_ARGB
            is LightAction.Strobe -> frameAtCue.colorArgb
        }
        val nextTargetIntensity = when (cue.action) {
            LightAction.Blackout -> 0f
            else -> cue.intensity.coerceIn(0f, 1f)
        }
        val nextStrobeHz = (cue.action as? LightAction.Strobe)?.hz?.coerceAtLeast(0.1f)

        return LightSceneState(
            trackUri = sceneState.trackUri,
            previousColorArgb = frameAtCue.colorArgb,
            previousIntensity = frameAtCue.intensity.coerceIn(0f, 1f),
            targetColorArgb = nextTargetColor.coerceIn(0L, 0xFFFFFFFFL),
            targetIntensity = nextTargetIntensity,
            strobeHz = nextStrobeHz,
            cuePositionMs = cue.timeMs.coerceAtLeast(0L),
            fadeMs = cue.fadeMs.coerceAtLeast(0L),
            anchorPositionMs = currentPositionMs.coerceAtLeast(0L),
            anchorRealtimeMs = realtimeMs.coerceAtLeast(0L),
            isPlaying = isPlaying,
            playbackRate = playbackRate.coerceAtLeast(0f),
            hasCue = true
        )
    }

    private fun updateAnchor(
        sceneState: LightSceneState,
        positionMs: Long,
        realtimeMs: Long,
        isPlaying: Boolean,
        playbackRate: Float
    ): LightSceneState {
        return sceneState.copy(
            anchorPositionMs = positionMs.coerceAtLeast(0L),
            anchorRealtimeMs = realtimeMs.coerceAtLeast(0L),
            isPlaying = isPlaying,
            playbackRate = playbackRate.coerceAtLeast(0f)
        )
    }

    private fun cuesBetween(
        cues: List<LightCue>,
        fromExclusiveMs: Long,
        toInclusiveMs: Long
    ): List<LightCue> {
        if (cues.isEmpty() || toInclusiveMs < fromExclusiveMs) {
            return emptyList()
        }

        return cues.filter { cue ->
            cue.timeMs > fromExclusiveMs && cue.timeMs <= toInclusiveMs
        }
    }

    private fun resolvePlaybackRate(
        previousObservation: PlaybackObservation?,
        currentPositionMs: Long,
        currentRealtimeMs: Long,
        isPlaying: Boolean,
        fallbackRate: Float
    ): Float {
        if (!isPlaying || previousObservation == null) {
            return fallbackRate.coerceAtLeast(0f).takeIf { it > 0f } ?: 1f
        }

        val deltaRealtimeMs = (currentRealtimeMs - previousObservation.realtimeMs).coerceAtLeast(0L)
        val deltaPositionMs = (currentPositionMs - previousObservation.positionMs).coerceAtLeast(0L)
        if (deltaRealtimeMs <= 0L || deltaPositionMs <= 0L) {
            return fallbackRate.coerceAtLeast(0f).takeIf { it > 0f } ?: 1f
        }

        return (deltaPositionMs.toFloat() / deltaRealtimeMs.toFloat()).coerceIn(0f, 4f)
    }

    private fun LightSceneState.withTrackUri(trackUri: String): LightSceneState {
        return if (this.trackUri == trackUri) this else copy(trackUri = trackUri)
    }
}
