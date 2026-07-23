package com.patrick.lrcreader.core.waveform

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaCodecList
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max

object WaveformExtractor {
    private const val PERF_TAG = "WAVEFORM_PERF"
    private const val DEQUEUE_TIMEOUT_US = 10_000L
    private const val FALLBACK_WINDOW_FRAMES = 2_048
    private const val MIN_POINTS = 64
    private const val MAX_POINTS = 20_000
    private const val OVERVIEW_WINDOW_COUNT = 64
    private const val OVERVIEW_SAMPLE_DURATION_US = 180_000L
    private val preferredMp3AnalysisDecoders = listOf(
        "c2.android.mp3.decoder",
        "OMX.google.mp3.decoder"
    )

    suspend fun extractNormalizedPeaks(
        context: Context,
        uri: Uri,
        targetPoints: Int = MAX_POINTS
    ): List<Float> = withContext(Dispatchers.IO) {
        val safeTargetPoints = targetPoints.coerceIn(MIN_POINTS, MAX_POINTS)
        extractInternal(context, uri, safeTargetPoints)
    }

    suspend fun extractSampledOverview(
        context: Context,
        uri: Uri,
        targetPoints: Int
    ): List<Float> = withContext(Dispatchers.IO) {
        val safeTargetPoints = targetPoints.coerceIn(MIN_POINTS, MAX_POINTS)
        extractSampledOverviewInternal(context, uri, safeTargetPoints)
    }

    private fun extractSampledOverviewInternal(
        context: Context,
        uri: Uri,
        targetPoints: Int
    ): List<Float> {
        val extractionStartedNs = SystemClock.elapsedRealtimeNanos()
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
            val durationUs = trackFormat.getLongOrNull(MediaFormat.KEY_DURATION)
                ?.takeIf { it > 0L }
                ?: throw IllegalArgumentException("Missing audio duration")
            val sampleRate = trackFormat.getIntegerOrNull(MediaFormat.KEY_SAMPLE_RATE)
                ?.takeIf { it > 0 }
                ?: 44_100
            var channelCount = trackFormat.getIntegerOrNull(MediaFormat.KEY_CHANNEL_COUNT) ?: 1
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
            val windowCount = minOf(OVERVIEW_WINDOW_COUNT, targetPoints)
            val pointsPerWindow = (
                (targetPoints + windowCount - 1) / windowCount
                ).coerceAtLeast(1)
            val intervalUs = (durationUs / windowCount).coerceAtLeast(1L)
            val sampleDurationUs = minOf(OVERVIEW_SAMPLE_DURATION_US, intervalUs)
            val sampledPeaks = ArrayList<Float>(windowCount * pointsPerWindow)
            var decodedBufferCount = 0
            var decodedByteCount = 0L

            codec = createWaveformDecoder(mime).also {
                it.configure(trackFormat, null, null, 0)
                it.start()
            }
            val bufferInfo = MediaCodec.BufferInfo()

            repeat(windowCount) { windowIndex ->
                val intervalStartUs = windowIndex * intervalUs
                val centeredStartUs = intervalStartUs + (intervalUs - sampleDurationUs) / 2L
                val sampleStartUs = centeredStartUs.coerceIn(
                    0L,
                    (durationUs - sampleDurationUs).coerceAtLeast(0L)
                )
                val sampleEndUs = (sampleStartUs + sampleDurationUs).coerceAtMost(durationUs)
                val windowFrames = (
                    sampleDurationUs / 1_000_000.0 * sampleRate.toDouble() / pointsPerWindow
                    ).toInt().coerceAtLeast(1)
                val aggregator = PeakAggregator(windowFrames = windowFrames)

                codec.flush()
                extractor.seekTo(sampleStartUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                var inputEnded = false
                var outputEnded = false
                var guard = 0

                while (!outputEnded && guard < 4_096) {
                    guard++
                    if (!inputEnded) {
                        val inputIndex = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                        if (inputIndex >= 0) {
                            val inputBuffer = codec.getInputBuffer(inputIndex)
                            val sampleTimeUs = extractor.sampleTime
                            if (inputBuffer == null || sampleTimeUs < 0L || sampleTimeUs > sampleEndUs) {
                                codec.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    0,
                                    sampleEndUs,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputEnded = true
                            } else {
                                val sampleSize = extractor.readSampleData(inputBuffer, 0)
                                if (sampleSize < 0) {
                                    codec.queueInputBuffer(
                                        inputIndex,
                                        0,
                                        0,
                                        sampleEndUs,
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                    )
                                    inputEnded = true
                                } else {
                                    codec.queueInputBuffer(
                                        inputIndex,
                                        0,
                                        sampleSize,
                                        sampleTimeUs.coerceAtLeast(0L),
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
                                if (
                                    bufferInfo.size > 0 &&
                                    bufferInfo.presentationTimeUs in sampleStartUs..sampleEndUs
                                ) {
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
                                        decodedBufferCount++
                                        decodedByteCount += bufferInfo.size.toLong()
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

                val windowPeaks = fitPointCount(
                    peaks = aggregator.buildPeaks(),
                    targetPoints = pointsPerWindow
                )
                sampledPeaks.addAll(windowPeaks)
            }

            val result = normalize(fitPointCount(sampledPeaks, targetPoints))
            Log.i(
                PERF_TAG,
                "overview_done uri=$uri targetPoints=$targetPoints outputPoints=${result.size} " +
                    "windows=$windowCount sampleDurationUs=$sampleDurationUs " +
                    "buffers=$decodedBufferCount decodedBytes=$decodedByteCount " +
                    "totalMs=${(SystemClock.elapsedRealtimeNanos() - extractionStartedNs) / 1_000_000L}"
            )
            return result
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun extractInternal(
        context: Context,
        uri: Uri,
        targetPoints: Int
    ): List<Float> {
        val extractionStartedNs = SystemClock.elapsedRealtimeNanos()
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
            codec = createWaveformDecoder(mime).also {
                it.configure(trackFormat, null, null, 0)
                it.start()
            }

            val bufferInfo = MediaCodec.BufferInfo()
            var inputEnded = false
            var outputEnded = false
            var channelCount = trackFormat.getIntegerOrNull(MediaFormat.KEY_CHANNEL_COUNT) ?: 1
            var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
            var decodedBufferCount = 0
            var decodedByteCount = 0L
            var aggregationDurationNs = 0L

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
                                    val aggregationStartedNs = SystemClock.elapsedRealtimeNanos()
                                    aggregator.consume(
                                        buffer = chunk,
                                        channelCount = channelCount.coerceAtLeast(1),
                                        pcmEncoding = pcmEncoding
                                    )
                                    aggregationDurationNs +=
                                        SystemClock.elapsedRealtimeNanos() - aggregationStartedNs
                                    decodedBufferCount++
                                    decodedByteCount += bufferInfo.size.toLong()
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
            val normalized = normalize(reduced)
            val totalDurationNs = SystemClock.elapsedRealtimeNanos() - extractionStartedNs
            Log.i(
                PERF_TAG,
                "extract_done uri=$uri targetPoints=$targetPoints outputPoints=${normalized.size} " +
                    "buffers=$decodedBufferCount decodedBytes=$decodedByteCount " +
                    "aggregateMs=${aggregationDurationNs / 1_000_000L} " +
                    "totalMs=${totalDurationNs / 1_000_000L}"
            )
            return normalized
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

    private fun createWaveformDecoder(mime: String): MediaCodec {
        if (mime.equals("audio/mpeg", ignoreCase = true)) {
            val codecInfos = runCatching {
                MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            }.getOrDefault(emptyArray())

            preferredMp3AnalysisDecoders.forEach { preferredName ->
                val available = codecInfos.any { info ->
                    !info.isEncoder &&
                        info.name.equals(preferredName, ignoreCase = true) &&
                        info.supportedTypes.any { supportedMime ->
                            supportedMime.equals(mime, ignoreCase = true)
                        }
                }
                if (available) {
                    runCatching {
                        MediaCodec.createByCodecName(preferredName)
                    }.onSuccess { decoder ->
                        Log.i(
                            PERF_TAG,
                            "decoder_selected mime=$mime name=${decoder.name} mode=software_analysis"
                        )
                    }.getOrNull()?.let { return it }
                }
            }
        }

        return MediaCodec.createDecoderByType(mime).also { decoder ->
            Log.i(
                PERF_TAG,
                "decoder_selected mime=$mime name=${decoder.name} mode=platform_default"
            )
        }
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

    private fun fitPointCount(peaks: List<Float>, targetPoints: Int): List<Float> {
        if (targetPoints <= 0) return emptyList()
        if (peaks.isEmpty()) return List(targetPoints) { 0f }
        if (peaks.size == targetPoints) return peaks
        if (peaks.size > targetPoints) return downsampleMax(peaks, targetPoints)
        return List(targetPoints) { index ->
            val sourceIndex = (
                index.toLong() * peaks.size.toLong() / targetPoints.toLong()
                ).toInt().coerceIn(0, peaks.lastIndex)
            peaks[sourceIndex]
        }
    }

    internal class PeakAggregator(
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
            val safeChannels = channels.coerceAtLeast(1)
            val samples = buffer.asShortBuffer()
            while (samples.remaining() >= safeChannels) {
                val availableFrames = samples.remaining() / safeChannels
                val framesToConsume = minOf(
                    availableFrames,
                    (windowFrames - framesInWindow).coerceAtLeast(1)
                )
                val samplesToConsume = framesToConsume * safeChannels
                var maxMagnitude = 0
                var sampleIndex = 0
                while (sampleIndex < samplesToConsume) {
                    val sample = samples.get().toInt()
                    val magnitude = if (sample == Short.MIN_VALUE.toInt()) {
                        32_768
                    } else {
                        kotlin.math.abs(sample)
                    }
                    if (magnitude > maxMagnitude) {
                        maxMagnitude = magnitude
                    }
                    sampleIndex++
                }
                val blockPeak = maxMagnitude / 32768f
                if (blockPeak > currentWindowMax) {
                    currentWindowMax = blockPeak
                }
                framesInWindow += framesToConsume
                if (framesInWindow >= windowFrames) {
                    peaks += currentWindowMax
                    framesInWindow = 0
                    currentWindowMax = 0f
                }
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
