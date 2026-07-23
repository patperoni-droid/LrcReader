package com.patrick.lrcreader.core.waveform

import android.media.AudioFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class WaveformPeakAggregatorTest {

    @Test
    fun pcm16Stereo_isAggregatedByFrameWindow() {
        val pcm = shortArrayOf(
            0, 16_384,
            Short.MIN_VALUE, 0,
            8_192, 0,
            0, -16_384
        )
        val buffer = ByteBuffer
            .allocate(pcm.size * Short.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
        pcm.forEach(buffer::putShort)
        buffer.flip()

        val aggregator = WaveformExtractor.PeakAggregator(windowFrames = 2)
        aggregator.consume(
            buffer = buffer,
            channelCount = 2,
            pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
        )

        assertArrayEquals(
            floatArrayOf(1f, 0.5f),
            aggregator.buildPeaks().toFloatArray(),
            0.0001f
        )
    }
}
