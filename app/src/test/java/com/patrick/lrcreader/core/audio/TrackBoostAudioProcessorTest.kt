package com.patrick.lrcreader.core.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class TrackBoostAudioProcessorTest {

    @Test
    fun neutralBoostPassesPcmThroughUnchanged() {
        val processor = configuredProcessor()
        processor.setBoostLinear(1f)

        processor.queueInput(pcm16Buffer(shortArrayOf(1_000, -1_000, 24_000)))

        assertArrayEquals(
            shortArrayOf(1_000, -1_000, 24_000),
            readPcm16(processor.getOutput())
        )
    }

    @Test
    fun positiveBoostAmplifiesAndClampsPcm16() {
        val processor = configuredProcessor()
        processor.setBoostLinear(2f)

        processor.queueInput(pcm16Buffer(shortArrayOf(1_000, -1_000, 20_000, -20_000)))

        assertArrayEquals(
            shortArrayOf(2_000, -2_000, Short.MAX_VALUE, Short.MIN_VALUE),
            readPcm16(processor.getOutput())
        )
    }

    @Test
    fun boostCanCrossUnityWithoutReconfiguringProcessor() {
        val processor = configuredProcessor()

        processor.setBoostLinear(1f)
        processor.queueInput(pcm16Buffer(shortArrayOf(10_000)))
        assertArrayEquals(shortArrayOf(10_000), readPcm16(processor.getOutput()))

        processor.setBoostLinear(1.1f)
        processor.queueInput(pcm16Buffer(shortArrayOf(10_000)))
        assertArrayEquals(shortArrayOf(11_000), readPcm16(processor.getOutput()))

        processor.setBoostLinear(1f)
        processor.queueInput(pcm16Buffer(shortArrayOf(10_000)))
        assertArrayEquals(shortArrayOf(10_000), readPcm16(processor.getOutput()))
    }

    private fun configuredProcessor(): TrackBoostAudioProcessor {
        return TrackBoostAudioProcessor().apply {
            configure(AudioProcessor.AudioFormat(44_100, 2, C.ENCODING_PCM_16BIT))
            flush()
        }
    }

    private fun pcm16Buffer(samples: ShortArray): ByteBuffer {
        return ByteBuffer.allocateDirect(samples.size * Short.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                samples.forEach(::putShort)
                flip()
            }
    }

    private fun readPcm16(buffer: ByteBuffer): ShortArray {
        val input = buffer.slice().order(ByteOrder.LITTLE_ENDIAN)
        return ShortArray(input.remaining() / Short.SIZE_BYTES) { input.short }
    }
}
