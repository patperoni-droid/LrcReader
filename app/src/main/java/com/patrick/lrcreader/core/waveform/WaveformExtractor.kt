package com.patrick.lrcreader.core.waveform

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max

object WaveformExtractor {
    private const val DEQUEUE_TIMEOUT_US = 10_000L
    private const val FALLBACK_WINDOW_FRAMES = 2_048
    private const val MIN_POINTS = 64
    private const val MAX_POINTS = 20_000

    suspend fun extractNormalizedPeaks(
        context: Context,
        uri: Uri,
        targetPoints: Int = MAX_POINTS
    ): List<Float> = withContext(Dispatchers.IO) {
        val safeTargetPoints = targetPoints.coerceIn(MIN_POINTS, MAX_POINTS)
        extractInternal(context, uri, safeTargetPoints)
    }

    private fun extractInternal(
        context: Context,
        uri: Uri,
        targetPoints: Int
    ): List<Float> {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null

        try {
            extractor.setDataSource(context, uri, null)
            val audioTrackIndex = findAudioTrackIndex(extractor)
                ?: throw IllegalArgumentException("No audio track found")

            extractor.selectTrack(audioTrackIndex)
            val trackFormat = extractor.getTrackFormat(audioTrackIndex)
            val mime = trackFormat.getString(MediaFormat.KEY_MIME)
                ?: throw IllegalArgumentException("Missing audio mime")

            val sampleRate = trackFormat.getIntegerOrNull(MediaFormat.KEY_SAMPLE_RATE)
            val durationUs = trackFormat.getLongOrNull(MediaFormat.KEY_DURATION)
            val estimatedWindowFrames = estimateWindowFrames(
                durationUs = durationUs,
                sampleRate = sampleRate,
                targetPoints = targetPoints
            )

            val aggregator = PeakAggregator(windowFrames = estimatedWindowFrames)
            codec = MediaCodec.createDecoderByType(mime).also {
                it.configure(trackFormat, null, null, 0)
                it.start()
            }

            val bufferInfo = MediaCodec.BufferInfo()
            var inputEnded = false
            var outputEnded = false
            var channelCount = trackFormat.getIntegerOrNull(MediaFormat.KEY_CHANNEL_COUNT) ?: 1
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT

            while (!outputEnded) {
                if (!inputEnded) {
                    val inputIndex = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                        if (inputBuffer != null) {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    0,
                                    0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputEnded = true
                            } else {
                                val presentationUs = extractor.sampleTime.coerceAtLeast(0L)
                                codec.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    sampleSize,
                                    presentationUs,
                                    0
                                )
                                extractor.advance()
                            }
                        }
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit

                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outputFormat = codec.outputFormat
                        channelCount = outputFormat.getIntegerOrNull(MediaFormat.KEY_CHANNEL_COUNT)
                            ?: channelCount
                        pcmEncoding = outputFormat.getIntegerOrNull(MediaFormat.KEY_PCM_ENCODING)
                            ?: AudioFormat.ENCODING_PCM_16BIT
                    }

                    else -> {
                        if (outputIndex >= 0) {
                            if (bufferInfo.size > 0) {
                                codec.getOutputBuffer(outputIndex)?.let { outputBuffer ->
                                    val chunk = outputBuffer.duplicate().apply {
                                        position(bufferInfo.offset)
                                        limit(bufferInfo.offset + bufferInfo.size)
                                    }
                                    aggregator.consume(
                                        buffer = chunk,
                                        channelCount = channelCount.coerceAtLeast(1),
                                        pcmEncoding = pcmEncoding
                                    )
                                }
                            }
                            codec.releaseOutputBuffer(outputIndex, false)
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                outputEnded = true
                            }
                        }
                    }
                }
            }

            val rawPeaks = aggregator.buildPeaks()
            val reduced = downsampleMax(rawPeaks, targetPoints)
            return normalize(reduced)
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun findAudioTrackIndex(extractor: MediaExtractor): Int? {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith("audio/")) return i
        }
        return null
    }

    private fun estimateWindowFrames(
        durationUs: Long?,
        sampleRate: Int?,
        targetPoints: Int
    ): Int {
        val duration = durationUs ?: return FALLBACK_WINDOW_FRAMES
        val rate = sampleRate ?: return FALLBACK_WINDOW_FRAMES
        if (duration <= 0L || rate <= 0) return FALLBACK_WINDOW_FRAMES

        val totalFrames = ((duration / 1_000_000.0) * rate).toLong().coerceAtLeast(1L)
        return (totalFrames / targetPoints).toInt().coerceAtLeast(1)
    }

    private fun normalize(peaks: List<Float>): List<Float> {
        if (peaks.isEmpty()) return emptyList()
        val maxPeak = peaks.maxOrNull() ?: 0f
        if (maxPeak <= 1e-6f) return List(peaks.size) { 0f }
        return peaks.map { (it / maxPeak).coerceIn(0f, 1f) }
    }

    private fun downsampleMax(peaks: List<Float>, maxPoints: Int): List<Float> {
        if (peaks.size <= maxPoints) return peaks
        val out = ArrayList<Float>(maxPoints)
        val step = peaks.size.toDouble() / maxPoints.toDouble()

        for (i in 0 until maxPoints) {
            val start = (i * step).toInt().coerceAtMost(peaks.lastIndex)
            val endExclusive = (((i + 1) * step).toInt()).coerceIn(start + 1, peaks.size)
            var blockMax = 0f
            var idx = start
            while (idx < endExclusive) {
                blockMax = max(blockMax, peaks[idx])
                idx++
            }
            out += blockMax
        }
        return out
    }

    private class PeakAggregator(
        private val windowFrames: Int
    ) {
        private val peaks = ArrayList<Float>()
        private var framesInWindow = 0
        private var currentWindowMax = 0f

        fun consume(
            buffer: ByteBuffer,
            channelCount: Int,
            pcmEncoding: Int
        ) {
            val littleEndian = buffer.order(ByteOrder.LITTLE_ENDIAN)
            when (pcmEncoding) {
                AudioFormat.ENCODING_PCM_FLOAT -> consumePcmFloat(littleEndian, channelCount)
                AudioFormat.ENCODING_PCM_8BIT -> consumePcm8(littleEndian, channelCount)
                AudioFormat.ENCODING_PCM_24BIT_PACKED -> consumePcm24(littleEndian, channelCount)
                AudioFormat.ENCODING_PCM_32BIT -> consumePcm32(littleEndian, channelCount)
                else -> consumePcm16(littleEndian, channelCount)
            }
        }

        fun buildPeaks(): List<Float> {
            if (framesInWindow > 0) {
                peaks += currentWindowMax
                framesInWindow = 0
                currentWindowMax = 0f
            }
            return peaks
        }

        private fun consumePcm8(buffer: ByteBuffer, channels: Int) {
            while (buffer.remaining() >= channels) {
                var frameMax = 0f
                repeat(channels) {
                    val unsigned = buffer.get().toInt() and 0xFF
                    val sample = ((unsigned - 128) / 128f).coerceIn(-1f, 1f)
                    frameMax = max(frameMax, abs(sample))
                }
                pushFrame(frameMax)
            }
        }

        private fun consumePcm16(buffer: ByteBuffer, channels: Int) {
            val bytesPerFrame = 2 * channels
            while (buffer.remaining() >= bytesPerFrame) {
                var frameMax = 0f
                repeat(channels) {
                    val sample = (buffer.short.toInt() / 32768f).coerceIn(-1f, 1f)
                    frameMax = max(frameMax, abs(sample))
                }
                pushFrame(frameMax)
            }
        }

        private fun consumePcm24(buffer: ByteBuffer, channels: Int) {
            val bytesPerFrame = 3 * channels
            while (buffer.remaining() >= bytesPerFrame) {
                var frameMax = 0f
                repeat(channels) {
                    val b0 = buffer.get().toInt() and 0xFF
                    val b1 = buffer.get().toInt() and 0xFF
                    val b2 = buffer.get().toInt()
                    val sampleInt = (b0 or (b1 shl 8) or (b2 shl 16))
                    val sample = (sampleInt / 8_388_608f).coerceIn(-1f, 1f)
                    frameMax = max(frameMax, abs(sample))
                }
                pushFrame(frameMax)
            }
        }

        private fun consumePcm32(buffer: ByteBuffer, channels: Int) {
            val bytesPerFrame = 4 * channels
            while (buffer.remaining() >= bytesPerFrame) {
                var frameMax = 0f
                repeat(channels) {
                    val sample = (buffer.int / 2_147_483_648f).coerceIn(-1f, 1f)
                    frameMax = max(frameMax, abs(sample))
                }
                pushFrame(frameMax)
            }
        }

        private fun consumePcmFloat(buffer: ByteBuffer, channels: Int) {
            val bytesPerFrame = 4 * channels
            while (buffer.remaining() >= bytesPerFrame) {
                var frameMax = 0f
                repeat(channels) {
                    val sample = buffer.float.coerceIn(-1f, 1f)
                    frameMax = max(frameMax, abs(sample))
                }
                pushFrame(frameMax)
            }
        }

        private fun pushFrame(framePeak: Float) {
            currentWindowMax = max(currentWindowMax, framePeak)
            framesInWindow++
            if (framesInWindow >= windowFrames) {
                peaks += currentWindowMax
                framesInWindow = 0
                currentWindowMax = 0f
            }
        }
    }

    private fun MediaFormat.getIntegerOrNull(key: String): Int? =
        if (containsKey(key)) getInteger(key) else null

    private fun MediaFormat.getLongOrNull(key: String): Long? =
        if (containsKey(key)) getLong(key) else null
}
