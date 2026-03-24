package com.patrick.lrcreader.core.light

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object LightPreviewTestController {

    private const val TEST_RED_ARGB = 0xFFFF0000L
    private const val TEST_GREEN_ARGB = 0xFF00FF00L
    private const val TEST_BLUE_ARGB = 0xFF0000FFL
    private const val TEST_WHITE_ARGB = 0xFFFFFFFFL
    private const val TEST_STROBE_HZ = 2f

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var quickTestJob: Job? = null

    fun prime(trackUri: String?, sceneState: LightSceneState) {
        val key = trackUri?.takeIf { it.isNotBlank() } ?: return
        cancelQuickTest()
        SimulatorLightOutput.showPreviewOverride(
            sceneState.copy(
                trackUri = key,
                anchorRealtimeMs = SystemClock.elapsedRealtime()
            )
        )
    }

    fun showRed(trackUri: String?) {
        showColor(trackUri, TEST_RED_ARGB)
    }

    fun showBlue(trackUri: String?) {
        showColor(trackUri, TEST_BLUE_ARGB)
    }

    fun showGreen(trackUri: String?) {
        showColor(trackUri, TEST_GREEN_ARGB)
    }

    fun showWhite(trackUri: String?) {
        showColor(trackUri, TEST_WHITE_ARGB)
    }

    fun showStrobe(trackUri: String?) {
        val key = trackUri?.takeIf { it.isNotBlank() } ?: return
        cancelQuickTest()
        SimulatorLightOutput.showPreviewOverride(
            buildScene(
                trackUri = key,
                colorArgb = TEST_BLUE_ARGB,
                intensity = 1f,
                strobeHz = TEST_STROBE_HZ,
                isPlaying = true
            )
        )
    }

    fun showBlackout(trackUri: String?) {
        val key = trackUri?.takeIf { it.isNotBlank() } ?: return
        cancelQuickTest()
        SimulatorLightOutput.showPreviewOverride(
            LightSceneState(
                trackUri = key,
                previousColorArgb = LIGHT_OFF_COLOR_ARGB,
                previousIntensity = 0f,
                targetColorArgb = LIGHT_OFF_COLOR_ARGB,
                targetIntensity = 0f,
                cuePositionMs = 0L,
                fadeMs = 0L,
                anchorPositionMs = 0L,
                anchorRealtimeMs = SystemClock.elapsedRealtime(),
                isPlaying = false,
                playbackRate = 1f,
                hasCue = true
            )
        )
    }

    fun showOff(trackUri: String?) {
        val key = trackUri?.takeIf { it.isNotBlank() } ?: return
        cancelQuickTest()
        SimulatorLightOutput.showPreviewOverride(
            LightSceneState.off(
                trackUri = key,
                anchorRealtimeMs = SystemClock.elapsedRealtime()
            )
        )
    }

    fun runQuickTest(trackUri: String?) {
        val key = trackUri?.takeIf { it.isNotBlank() } ?: return
        cancelQuickTest()
        quickTestJob = scope.launch {
            SimulatorLightOutput.showPreviewOverride(buildScene(trackUri = key, colorArgb = TEST_RED_ARGB))
            delay(1_000L)
            SimulatorLightOutput.showPreviewOverride(buildScene(trackUri = key, colorArgb = TEST_BLUE_ARGB))
            delay(1_000L)
            SimulatorLightOutput.showPreviewOverride(
                buildScene(
                    trackUri = key,
                    colorArgb = TEST_BLUE_ARGB,
                    strobeHz = TEST_STROBE_HZ,
                    isPlaying = true
                )
            )
            delay(2_000L)
            SimulatorLightOutput.showPreviewOverride(
                LightSceneState(
                    trackUri = key,
                    previousColorArgb = LIGHT_OFF_COLOR_ARGB,
                    previousIntensity = 0f,
                    targetColorArgb = LIGHT_OFF_COLOR_ARGB,
                    targetIntensity = 0f,
                    cuePositionMs = 0L,
                    fadeMs = 0L,
                    anchorPositionMs = 0L,
                    anchorRealtimeMs = SystemClock.elapsedRealtime(),
                    isPlaying = false,
                    playbackRate = 1f,
                    hasCue = true
                )
            )
            delay(1_000L)
            SimulatorLightOutput.showPreviewOverride(
                LightSceneState.off(
                    trackUri = key,
                    anchorRealtimeMs = SystemClock.elapsedRealtime()
                )
            )
            quickTestJob = null
        }
    }

    fun stop() {
        cancelQuickTest()
        SimulatorLightOutput.clearPreviewOverride()
    }

    private fun showColor(trackUri: String?, colorArgb: Long) {
        val key = trackUri?.takeIf { it.isNotBlank() } ?: return
        cancelQuickTest()
        SimulatorLightOutput.showPreviewOverride(
            buildScene(trackUri = key, colorArgb = colorArgb)
        )
    }

    private fun buildScene(
        trackUri: String,
        colorArgb: Long,
        intensity: Float = 1f,
        strobeHz: Float? = null,
        isPlaying: Boolean = false
    ): LightSceneState {
        return LightSceneState(
            trackUri = trackUri,
            previousColorArgb = colorArgb,
            previousIntensity = intensity.coerceIn(0f, 1f),
            targetColorArgb = colorArgb,
            targetIntensity = intensity.coerceIn(0f, 1f),
            strobeHz = strobeHz,
            cuePositionMs = 0L,
            fadeMs = 0L,
            anchorPositionMs = 0L,
            anchorRealtimeMs = SystemClock.elapsedRealtime(),
            isPlaying = isPlaying,
            playbackRate = 1f,
            hasCue = true
        )
    }

    private fun cancelQuickTest() {
        quickTestJob?.cancel()
        quickTestJob = null
    }
}
