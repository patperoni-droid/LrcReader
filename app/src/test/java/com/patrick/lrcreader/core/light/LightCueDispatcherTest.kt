package com.patrick.lrcreader.core.light

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LightCueDispatcherTest {

    @Test
    fun advance_repeatedTicks_doNotReapplySameCue() {
        val runtime = LightCueRuntime()
        val cues = listOf(
            LightCue(
                timeMs = 1_000L,
                action = LightAction.Color(argb = 0xFFFF0000L),
                intensity = 1f
            )
        )

        runtime.advance(
            trackUri = "file:///track",
            cues = cues,
            positionMs = 900L,
            isPlaying = true,
            realtimeMs = 1_000L
        )

        val firstTrigger = runtime.advance(
            trackUri = "file:///track",
            cues = cues,
            positionMs = 1_050L,
            isPlaying = true,
            realtimeMs = 1_150L
        )
        val repeatedTick = runtime.advance(
            trackUri = "file:///track",
            cues = cues,
            positionMs = 1_100L,
            isPlaying = true,
            realtimeMs = 1_200L
        )

        assertEquals(1_000L, firstTrigger.cuePositionMs)
        assertEquals(1_000L, repeatedTick.cuePositionMs)
        assertEquals(firstTrigger.previousColorArgb, repeatedTick.previousColorArgb)
        assertEquals(firstTrigger.targetColorArgb, repeatedTick.targetColorArgb)
        assertEquals(firstTrigger.targetIntensity, repeatedTick.targetIntensity, 0.0001f)
    }

    @Test
    fun syncToPosition_backwardSeek_rebuildsEarlierScene() {
        val runtime = LightCueRuntime()
        val cues = listOf(
            LightCue(
                timeMs = 1_000L,
                action = LightAction.Color(argb = 0xFFFF0000L),
                intensity = 1f
            ),
            LightCue(
                timeMs = 2_000L,
                action = LightAction.Blackout,
                fadeMs = 0L
            ),
            LightCue(
                timeMs = 3_000L,
                action = LightAction.Color(argb = 0xFF0000FFL),
                intensity = 0.8f
            )
        )

        runtime.advance(
            trackUri = "file:///track",
            cues = cues,
            positionMs = 3_200L,
            isPlaying = true,
            realtimeMs = 3_200L
        )

        val rewound = runtime.syncToPosition(
            trackUri = "file:///track",
            cues = cues,
            positionMs = 1_500L,
            realtimeMs = 3_500L
        )
        val rendered = rewound.renderAtPosition(1_500L)

        assertEquals(1_000L, rewound.cuePositionMs)
        assertEquals(0xFFFF0000L, rendered.colorArgb)
        assertEquals(1f, rendered.intensity, 0.0001f)
        assertNull(rewound.strobeHz)
    }

    @Test
    fun syncToPosition_trimEntry_keepsFadeProgress() {
        val runtime = LightCueRuntime()
        val cues = listOf(
            LightCue(
                timeMs = 1_000L,
                action = LightAction.Color(argb = 0xFFFF0000L),
                intensity = 1f,
                fadeMs = 1_000L
            )
        )

        val scene = runtime.syncToPosition(
            trackUri = "file:///track",
            cues = cues,
            positionMs = 1_500L,
            realtimeMs = 5_000L
        )
        val rendered = scene.renderAtPosition(1_500L)

        assertEquals(1_000L, scene.cuePositionMs)
        assertEquals(0.5f, rendered.intensity, 0.0001f)
    }

    @Test
    fun syncToPosition_pauseState_freezesRenderOverRealtime() {
        val runtime = LightCueRuntime()
        val cues = listOf(
            LightCue(
                timeMs = 0L,
                action = LightAction.Color(argb = 0xFFFF0000L),
                intensity = 1f
            ),
            LightCue(
                timeMs = 1_000L,
                action = LightAction.Strobe(hz = 8f),
                intensity = 1f
            )
        )

        val pausedScene = runtime.syncToPosition(
            trackUri = "file:///track",
            cues = cues,
            positionMs = 1_500L,
            realtimeMs = 10_000L
        )

        assertEquals(
            pausedScene.renderAtRealtime(10_000L),
            pausedScene.renderAtRealtime(11_000L)
        )
    }
}
