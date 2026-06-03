package com.patrick.lrcreader.smp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SmpConfigLufsTest {

    @Test
    fun playbackJsonRoundTrip_preservesLufsPreparationSettings() {
        val config = SmpConfig(
            title = "Fiesta",
            id = "song_001",
            playback = SmpConfig.PlaybackConfig.fromStoredValues(
                startMs = null,
                endMs = null,
                volumeDb = 6,
                volumeSource = SmpConfig.PlaybackConfig.VOLUME_SOURCE_LUFS,
                lufsMeasured = -18.2f,
                lufsTarget = -14f,
                lufsAutoDb = 4.2f,
                lufsManualDb = 2
            )
        )

        val restored = SmpConfig.fromJsonOrNull(config.toJsonString())

        assertNotNull(restored)
        val playback = restored!!.playback
        assertNotNull(playback)
        assertEquals(6, playback!!.volumeDb)
        assertEquals(SmpConfig.PlaybackConfig.VOLUME_SOURCE_LUFS, playback.volumeSource)
        assertEquals(-18.2f, playback.lufsMeasured ?: 0f, 0.0001f)
        assertEquals(-14f, playback.lufsTarget ?: 0f, 0.0001f)
        assertEquals(4.2f, playback.lufsAutoDb ?: 0f, 0.0001f)
        assertEquals(2, playback.lufsManualDb)
    }
}
