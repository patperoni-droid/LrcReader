package com.patrick.lrcreader.core.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Lightweight PCM16 boost stage that stays in the Player audio chain.
 *
 * Neutral and negative gain continue to use ExoPlayer volume. Only the part
 * above unity is applied here, without rebuilding the active Player.
 */
@UnstableApi
internal class TrackBoostAudioProcessor : AudioProcessor {

    @Volatile
    private var boostLinear = 1f

    private var configured = false
    private var inputEnded = false
    private var reusableBuffer: ByteBuffer = EMPTY_BUFFER
    private var pendingOutput: ByteBuffer = EMPTY_BUFFER

    fun setBoostLinear(value: Float) {
        boostLinear = value.coerceIn(1f, 2f)
    }

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        configured = true
        return inputAudioFormat
    }

    override fun isActive(): Boolean = configured

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        inputEnded = false

        val gain = boostLinear
        if (gain <= 1.0005f) {
            val input = inputBuffer.slice()
            val output = replaceOutputBuffer(input.remaining())
            output.put(input)
            output.flip()
            pendingOutput = output
            inputBuffer.position(inputBuffer.limit())
            return
        }

        val input = inputBuffer.slice().order(ByteOrder.LITTLE_ENDIAN)
        val outputSize = input.remaining()
        val output = replaceOutputBuffer(outputSize).order(ByteOrder.LITTLE_ENDIAN)

        while (input.remaining() >= Short.SIZE_BYTES) {
            val sample = input.short.toInt()
            val amplified = (sample * gain)
                .roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            output.putShort(amplified.toShort())
        }
        while (input.hasRemaining()) {
            output.put(input.get())
        }

        output.flip()
        pendingOutput = output
        inputBuffer.position(inputBuffer.limit())
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun getOutput(): ByteBuffer {
        val output = pendingOutput
        pendingOutput = EMPTY_BUFFER
        return output
    }

    override fun isEnded(): Boolean = inputEnded && !pendingOutput.hasRemaining()

    override fun flush() {
        pendingOutput = EMPTY_BUFFER
        inputEnded = false
    }

    override fun reset() {
        flush()
        configured = false
    }

    private fun replaceOutputBuffer(requiredCapacity: Int): ByteBuffer {
        reusableBuffer = if (reusableBuffer.capacity() < requiredCapacity) {
            ByteBuffer.allocateDirect(requiredCapacity).order(ByteOrder.nativeOrder())
        } else {
            reusableBuffer.clear()
            reusableBuffer
        }
        return reusableBuffer
    }

    private companion object {
        val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
    }
}
