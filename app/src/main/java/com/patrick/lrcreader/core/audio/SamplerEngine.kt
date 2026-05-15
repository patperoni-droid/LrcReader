package com.patrick.lrcreader.core.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

class SamplerEngine {

    private val stateLock = Any()
    private val stopRequested = AtomicBoolean(false)

    @Volatile
    private var segments: List<SampleSegment> = emptyList()

    @Volatile
    private var playbackThread: Thread? = null

    @Volatile
    private var audioTrack: AudioTrack? = null

    @Volatile
    private var currentIndex: Int? = null

    @Volatile
    private var queuedIndex: Int? = null

    @Volatile
    private var crossfadeDurationMs: Int = DEFAULT_CROSSFADE_DURATION_MS

    @Volatile
    private var antiClickFadeDurationMs: Int = DEFAULT_ANTI_CLICK_FADE_DURATION_MS

    @Volatile
    var autoAdvanceSequentially: Boolean = false

    @Volatile
    var onSegmentStart: ((Int) -> Unit)? = null

    @Volatile
    var onSegmentTransition: ((Int, Int) -> Unit)? = null

    @Volatile
    var onPlaybackEnded: (() -> Unit)? = null

    fun setCrossfadeDurationMs(durationMs: Int) {
        crossfadeDurationMs = durationMs.coerceAtLeast(0)
        Log.d(FLOW_TAG, "CROSSFADE_DURATION_SET durationMs=$crossfadeDurationMs")
    }

    fun setAntiClickFadeDurationMs(durationMs: Int) {
        antiClickFadeDurationMs = durationMs.coerceAtLeast(0)
        Log.d(FLOW_TAG, "ANTI_CLICK_FADE_SET durationMs=$antiClickFadeDurationMs")
    }

    fun loadSegments(nextSegments: List<SampleSegment>) {
        stop()
        validateSegments(nextSegments)
        segments = nextSegments.toList()
        currentIndex = null
        queuedIndex = null

        val estimatedRamBytes = nextSegments.sumOf { it.estimatedRamBytes.toLong() }
        Log.d(
            FLOW_TAG,
            "LOAD_SEGMENTS count=${nextSegments.size} sampleRateHz=${nextSegments.firstOrNull()?.sampleRateHz ?: 0} estimatedRamBytes=$estimatedRamBytes estimatedRamMb=${"%.2f".format(estimatedRamBytes / BYTES_PER_MB)} crossfadeDurationMs=$crossfadeDurationMs antiClickFadeDurationMs=$antiClickFadeDurationMs"
        )
    }

    fun play(index: Int) {
        val snapshot = segments
        require(index in snapshot.indices) { "Invalid segment index: $index" }

        stop()
        stopRequested.set(false)
        currentIndex = index
        queuedIndex = null

        val thread = Thread(
            {
                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
                playFromIndex(index)
            },
            "ArrangementSamplerEngine"
        )

        synchronized(stateLock) {
            playbackThread = thread
        }

        Log.d(FLOW_TAG, "PLAY_START index=$index name=${snapshot[index].name}")
        thread.start()
    }

    fun queueNext(index: Int) {
        val snapshot = segments
        require(index in snapshot.indices) { "Invalid queued segment index: $index" }
        queuedIndex = index
        Log.d(FLOW_TAG, "QUEUE_NEXT index=$index name=${snapshot[index].name}")
    }

    fun stop() {
        stopRequested.set(true)

        val threadToJoin: Thread?
        val trackToRelease: AudioTrack?
        synchronized(stateLock) {
            threadToJoin = playbackThread
            playbackThread = null
            trackToRelease = audioTrack
            audioTrack = null
        }

        if (trackToRelease != null) {
            Log.d(FLOW_TAG, "AUDIO_TRACK_STOP reason=stop")
        }
        runCatching { trackToRelease?.pause() }
        runCatching { trackToRelease?.flush() }
        runCatching { trackToRelease?.stop() }
        runCatching { trackToRelease?.release() }

        if (threadToJoin != null && threadToJoin !== Thread.currentThread()) {
            runCatching { threadToJoin.join(STOP_JOIN_TIMEOUT_MS) }
        }

        currentIndex = null
        queuedIndex = null
        Log.d(FLOW_TAG, "STOP")
    }

    fun release() {
        stop()
        segments = emptyList()
        Log.d(FLOW_TAG, "RELEASE")
    }

    private fun playFromIndex(startIndex: Int) {
        val sampleRateHz = segments.getOrNull(startIndex)?.sampleRateHz ?: return
        val track = createAudioTrack(sampleRateHz)
        synchronized(stateLock) {
            audioTrack = track
        }

        try {
            track.play()
            Log.d(FLOW_TAG, "AUDIO_TRACK_START sampleRateHz=$sampleRateHz")
            var index: Int? = startIndex
            var initialOffsetBytes = 0
            var initialFadeInBytes = 0
            while (!stopRequested.get() && index != null) {
                val segment = segments.getOrNull(index) ?: break
                currentIndex = index
                onSegmentStart?.invoke(index)
                Log.d(
                    FLOW_TAG,
                    "NEXT_SEGMENT_START index=$index name=${segment.name} queued=$queuedIndex bytes=${segment.estimatedRamBytes} initialOffsetBytes=$initialOffsetBytes"
                )
                val transition = writeSegment(track, segment, initialOffsetBytes, initialFadeInBytes)
                if (stopRequested.get()) break

                val nextIndex = transition.nextIndex
                initialOffsetBytes = transition.nextInitialOffsetBytes
                initialFadeInBytes = transition.nextInitialFadeInBytes
                if (nextIndex != null) {
                    onSegmentTransition?.invoke(index, nextIndex)
                }
                index = nextIndex
                Log.d(
                    FLOW_TAG,
                    "SEGMENT_END index=$currentIndex next=$index " +
                        "nextInitialOffsetBytes=$initialOffsetBytes nextInitialFadeInBytes=$initialFadeInBytes"
                )
            }
        } catch (error: Throwable) {
            Log.w(FLOW_TAG, "PLAYBACK_ERROR message=${error.message}", error)
        } finally {
            Log.d(FLOW_TAG, "AUDIO_TRACK_STOP reason=thread_finally")
            runCatching { track.pause() }
            runCatching { track.flush() }
            runCatching { track.stop() }
            runCatching { track.release() }
            synchronized(stateLock) {
                if (audioTrack === track) {
                    audioTrack = null
                }
                if (playbackThread === Thread.currentThread()) {
                    playbackThread = null
                }
            }
            currentIndex = null
            queuedIndex = null
            if (!stopRequested.get()) {
                onPlaybackEnded?.invoke()
            }
        }
    }

    private fun writeSegment(
        track: AudioTrack,
        segment: SampleSegment,
        initialOffsetBytes: Int,
        initialFadeInBytes: Int
    ): SegmentTransition {
        val pcm = segment.pcm16Stereo
        var nextInitialOffsetBytes = 0
        var nextInitialFadeInBytes = 0
        val safeInitialOffsetBytes = alignToFrame(initialOffsetBytes)
            .coerceIn(0, pcm.size)
        val safeInitialFadeInBytes = alignToFrame(initialFadeInBytes)
            .coerceIn(0, pcm.size - safeInitialOffsetBytes)
        val heldTailBytes = resolveHeldTailBytes(segment)
        val bodyEnd = if (heldTailBytes > 0 && pcm.size - safeInitialOffsetBytes > heldTailBytes) {
            pcm.size - heldTailBytes
        } else {
            pcm.size
        }

        val bodyStart = if (safeInitialFadeInBytes > 0) {
            val fadeInEnd = (safeInitialOffsetBytes + safeInitialFadeInBytes).coerceAtMost(bodyEnd)
            writePcmRange(
                track = track,
                pcm = buildFadePcm(
                    sourcePcm = pcm,
                    startOffset = safeInitialOffsetBytes,
                    byteCount = fadeInEnd - safeInitialOffsetBytes,
                    fadeIn = true
                ),
                startOffset = 0,
                endOffset = fadeInEnd - safeInitialOffsetBytes
            )
            fadeInEnd
        } else {
            safeInitialOffsetBytes
        }
        writePcmRange(track, pcm, bodyStart, bodyEnd)
        if (stopRequested.get()) return SegmentTransition(null, 0)

        val nextIndex = queuedIndex ?: if (autoAdvanceSequentially) {
            currentIndex
                ?.plus(1)
                ?.takeIf { it in segments.indices }
        } else {
            null
        }
        val nextSegment = nextIndex?.let { segments.getOrNull(it) }
        val transitionCrossfadeBytes = resolveCrossfadeBytes(segment, nextSegment)
        val antiClickFadeBytes = resolveAntiClickFadeBytes(segment, nextSegment)
        if (nextIndex != null && nextSegment != null) {
            val previousLastSamplesEnergy = calculatePcmEnergy(
                pcm = segment.pcm16Stereo,
                sampleRateHz = segment.sampleRateHz,
                fromEnd = true
            )
            val nextFirstSamplesEnergy = calculatePcmEnergy(
                pcm = nextSegment.pcm16Stereo,
                sampleRateHz = nextSegment.sampleRateHz,
                fromEnd = false
            )
            Log.d(
                FLOW_TAG,
                "CUT_ANALYSIS previousSegmentId=${segment.id} nextSegmentId=${nextSegment.id} " +
                    "previousEndMs=${segment.endMs} nextStartMs=${nextSegment.startMs} " +
                    "previousLastSamplesEnergy=$previousLastSamplesEnergy " +
                    "nextFirstSamplesEnergy=$nextFirstSamplesEnergy " +
                    "energyDiff=${kotlin.math.abs(previousLastSamplesEnergy - nextFirstSamplesEnergy)} " +
                    "transitionDuration=${crossfadeBytesToMs(transitionCrossfadeBytes, segment.sampleRateHz)}"
            )
        }
        if (nextIndex != null && nextSegment != null && transitionCrossfadeBytes > 0) {
            queuedIndex = null
            val transitionPcm = buildCrossfadePcm(
                currentPcm = pcm,
                nextPcm = nextSegment.pcm16Stereo,
                crossfadeBytes = transitionCrossfadeBytes
            )
            writePcmRange(track, transitionPcm, 0, transitionPcm.size)
            nextInitialOffsetBytes = transitionCrossfadeBytes
            Log.d(
                FLOW_TAG,
                "CROSSFADE_APPLIED durationMs=${crossfadeBytesToMs(transitionCrossfadeBytes, segment.sampleRateHz)} " +
                    "bytes=$transitionCrossfadeBytes current=$currentIndex next=$nextIndex"
            )
        } else {
            if (nextIndex != null && nextSegment != null && antiClickFadeBytes > 0) {
                val fadeOutStart = (pcm.size - antiClickFadeBytes).coerceAtLeast(bodyEnd)
                writePcmRange(track, pcm, bodyEnd, fadeOutStart)
                writePcmRange(
                    track = track,
                    pcm = buildFadePcm(
                        sourcePcm = pcm,
                        startOffset = fadeOutStart,
                        byteCount = pcm.size - fadeOutStart,
                        fadeIn = false
                    ),
                    startOffset = 0,
                    endOffset = pcm.size - fadeOutStart
                )
                nextInitialFadeInBytes = antiClickFadeBytes
                Log.d(
                    FLOW_TAG,
                    "ANTI_CLICK_FADE_APPLIED durationMs=${crossfadeBytesToMs(antiClickFadeBytes, segment.sampleRateHz)} " +
                        "bytes=$antiClickFadeBytes current=$currentIndex next=$nextIndex"
                )
            } else {
                writePcmRange(track, pcm, bodyEnd, pcm.size)
            }
            queuedIndex = null
        }

        return SegmentTransition(nextIndex, nextInitialOffsetBytes, nextInitialFadeInBytes)
    }

    private fun writePcmRange(track: AudioTrack, pcm: ByteArray, startOffset: Int, endOffset: Int) {
        var offset = startOffset.coerceIn(0, pcm.size)
        val safeEndOffset = endOffset.coerceIn(offset, pcm.size)
        var totalWritten = 0
        while (!stopRequested.get() && offset < safeEndOffset) {
            val bytesToWrite = minOf(WRITE_CHUNK_BYTES, safeEndOffset - offset)
            val written = track.write(pcm, offset, bytesToWrite)
            if (written < 0) {
                Log.w(FLOW_TAG, "WRITE_ERROR code=$written current=$currentIndex queued=$queuedIndex")
                break
            }
            if (written == 0) {
                Log.w(FLOW_TAG, "UNDERRUN_OR_BACKPRESSURE current=$currentIndex queued=$queuedIndex offset=$offset")
                Thread.yield()
            } else {
                offset += written
                totalWritten += written
            }
        }
        if (totalWritten > 0) {
            Log.d(FLOW_TAG, "AUDIO_WRITE bytes=$totalWritten current=$currentIndex queued=$queuedIndex")
        }
    }

    private fun resolveCrossfadeBytes(current: SampleSegment, next: SampleSegment?): Int {
        if (next == null || current.sampleRateHz != next.sampleRateHz) return 0
        val maxCurrentFrames = resolveHeldTailBytes(current) / SampleSegment.BYTES_PER_STEREO_FRAME
        val maxNextFrames = next.pcm16Stereo.size / SampleSegment.BYTES_PER_STEREO_FRAME / 2
        val frames = minOf(maxCurrentFrames, maxNextFrames)
        return (frames * SampleSegment.BYTES_PER_STEREO_FRAME).coerceAtLeast(0)
    }

    private fun resolveHeldTailBytes(segment: SampleSegment): Int {
        val requestedFrames = (segment.sampleRateHz * crossfadeDurationMs.coerceAtLeast(0)) / 1_000
        val maxFrames = segment.pcm16Stereo.size / SampleSegment.BYTES_PER_STEREO_FRAME / 2
        val frames = minOf(requestedFrames, maxFrames)
        return frames * SampleSegment.BYTES_PER_STEREO_FRAME
    }

    private fun resolveAntiClickFadeBytes(current: SampleSegment, next: SampleSegment?): Int {
        if (next == null || current.sampleRateHz != next.sampleRateHz) return 0
        val requestedFrames = (current.sampleRateHz * antiClickFadeDurationMs.coerceAtLeast(0)) / 1_000
        val maxCurrentFrames = current.pcm16Stereo.size / SampleSegment.BYTES_PER_STEREO_FRAME / 2
        val maxNextFrames = next.pcm16Stereo.size / SampleSegment.BYTES_PER_STEREO_FRAME / 2
        val frames = minOf(requestedFrames, maxCurrentFrames, maxNextFrames)
        return frames * SampleSegment.BYTES_PER_STEREO_FRAME
    }

    private fun buildCrossfadePcm(
        currentPcm: ByteArray,
        nextPcm: ByteArray,
        crossfadeBytes: Int
    ): ByteArray {
        val safeBytes = alignToFrame(crossfadeBytes)
            .coerceAtMost(currentPcm.size)
            .coerceAtMost(nextPcm.size)
        val output = ByteArray(safeBytes)
        var offset = 0
        while (offset < safeBytes) {
            val frameIndex = offset / SampleSegment.BYTES_PER_STEREO_FRAME
            val frameCount = (safeBytes / SampleSegment.BYTES_PER_STEREO_FRAME).coerceAtLeast(1)
            val nextGain = ((frameIndex + 1).toFloat() / frameCount.toFloat()).coerceIn(0f, 1f)
            val currentGain = 1f - nextGain
            repeat(SampleSegment.CHANNEL_COUNT) { channel ->
                val byteOffset = offset + channel * SampleSegment.BYTES_PER_SAMPLE
                val currentOffset = currentPcm.size - safeBytes + byteOffset
                val currentSample = readPcm16Le(currentPcm, currentOffset)
                val nextSample = readPcm16Le(nextPcm, byteOffset)
                val mixed = (currentSample * currentGain + nextSample * nextGain)
                    .toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                writePcm16Le(output, byteOffset, mixed)
            }
            offset += SampleSegment.BYTES_PER_STEREO_FRAME
        }
        return output
    }

    private fun buildFadePcm(
        sourcePcm: ByteArray,
        startOffset: Int,
        byteCount: Int,
        fadeIn: Boolean
    ): ByteArray {
        val safeStart = alignToFrame(startOffset).coerceIn(0, sourcePcm.size)
        val safeBytes = alignToFrame(byteCount)
            .coerceAtLeast(0)
            .coerceAtMost(sourcePcm.size - safeStart)
        val output = ByteArray(safeBytes)
        val frameCount = (safeBytes / SampleSegment.BYTES_PER_STEREO_FRAME).coerceAtLeast(1)
        var offset = 0
        while (offset < safeBytes) {
            val frameIndex = offset / SampleSegment.BYTES_PER_STEREO_FRAME
            val progress = ((frameIndex + 1).toFloat() / frameCount.toFloat()).coerceIn(0f, 1f)
            val gain = if (fadeIn) progress else 1f - progress
            repeat(SampleSegment.CHANNEL_COUNT) { channel ->
                val byteOffset = offset + channel * SampleSegment.BYTES_PER_SAMPLE
                val sample = readPcm16Le(sourcePcm, safeStart + byteOffset)
                val faded = (sample * gain)
                    .toInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                writePcm16Le(output, byteOffset, faded)
            }
            offset += SampleSegment.BYTES_PER_STEREO_FRAME
        }
        return output
    }

    private fun readPcm16Le(bytes: ByteArray, offset: Int): Int {
        val lo = bytes[offset].toInt() and 0xFF
        val hi = bytes[offset + 1].toInt()
        return (hi shl 8) or lo
    }

    private fun writePcm16Le(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xFF).toByte()
        bytes[offset + 1] = ((value shr 8) and 0xFF).toByte()
    }

    private fun alignToFrame(byteCount: Int): Int {
        return byteCount - (byteCount % SampleSegment.BYTES_PER_STEREO_FRAME)
    }

    private fun crossfadeBytesToMs(byteCount: Int, sampleRateHz: Int): Long {
        val frames = byteCount / SampleSegment.BYTES_PER_STEREO_FRAME
        return (frames * 1_000L) / sampleRateHz.coerceAtLeast(1)
    }

    private fun calculatePcmEnergy(
        pcm: ByteArray,
        sampleRateHz: Int,
        fromEnd: Boolean
    ): Long {
        val requestedFrames = (sampleRateHz.coerceAtLeast(1) * CUT_ANALYSIS_WINDOW_MS) / 1_000
        val availableFrames = pcm.size / SampleSegment.BYTES_PER_STEREO_FRAME
        val frames = minOf(requestedFrames.coerceAtLeast(1), availableFrames).coerceAtLeast(0)
        if (frames <= 0) return 0L

        val startOffset = if (fromEnd) {
            pcm.size - frames * SampleSegment.BYTES_PER_STEREO_FRAME
        } else {
            0
        }.coerceAtLeast(0)
        val endOffset = (startOffset + frames * SampleSegment.BYTES_PER_STEREO_FRAME)
            .coerceAtMost(pcm.size)
        var sum = 0L
        var sampleCount = 0
        var offset = startOffset
        while (offset + 1 < endOffset) {
            sum += kotlin.math.abs(readPcm16Le(pcm, offset).toLong())
            sampleCount += 1
            offset += SampleSegment.BYTES_PER_SAMPLE
        }
        return if (sampleCount == 0) 0L else sum / sampleCount.toLong()
    }

    private fun createAudioTrack(sampleRateHz: Int): AudioTrack {
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRateHz)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .build()
        val minBufferBytes = AudioTrack.getMinBufferSize(
            sampleRateHz,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        require(minBufferBytes > 0) { "Invalid AudioTrack min buffer size: $minBufferBytes" }

        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                    .build()
            )
            .setAudioFormat(format)
            .setBufferSizeInBytes((minBufferBytes * BUFFER_MULTIPLIER).coerceAtLeast(WRITE_CHUNK_BYTES))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }

    private fun validateSegments(items: List<SampleSegment>) {
        if (items.isEmpty()) return
        val sampleRateHz = items.first().sampleRateHz
        require(items.all { it.sampleRateHz == sampleRateHz }) {
            "All SampleSegment items must use the same sample rate"
        }
    }

    private companion object {
        private const val TAG = "SamplerEngine"
        private const val FLOW_TAG = "ARR_SAMPLER_FLOW"
        private const val WRITE_CHUNK_BYTES = 16 * 1024
        private const val BUFFER_MULTIPLIER = 4
        private const val STOP_JOIN_TIMEOUT_MS = 500L
        private const val BYTES_PER_MB = 1024.0 * 1024.0
        private const val DEFAULT_CROSSFADE_DURATION_MS = 0
        private const val DEFAULT_ANTI_CLICK_FADE_DURATION_MS = 0
        private const val CUT_ANALYSIS_WINDOW_MS = 10
    }

    private data class SegmentTransition(
        val nextIndex: Int?,
        val nextInitialOffsetBytes: Int,
        val nextInitialFadeInBytes: Int = 0
    )
}
