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

    fun loadSegments(nextSegments: List<SampleSegment>) {
        stop()
        validateSegments(nextSegments)
        segments = nextSegments.toList()
        currentIndex = null
        queuedIndex = null

        val estimatedRamBytes = nextSegments.sumOf { it.estimatedRamBytes.toLong() }
        Log.d(
            TAG,
            "LOAD_SEGMENTS count=${nextSegments.size} sampleRateHz=${nextSegments.firstOrNull()?.sampleRateHz ?: 0} estimatedRamBytes=$estimatedRamBytes estimatedRamMb=${"%.2f".format(estimatedRamBytes / BYTES_PER_MB)}"
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

        Log.d(TAG, "PLAY index=$index name=${snapshot[index].name}")
        thread.start()
    }

    fun queueNext(index: Int) {
        val snapshot = segments
        require(index in snapshot.indices) { "Invalid queued segment index: $index" }
        queuedIndex = index
        Log.d(TAG, "QUEUE_NEXT index=$index name=${snapshot[index].name}")
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

        runCatching { trackToRelease?.pause() }
        runCatching { trackToRelease?.flush() }
        runCatching { trackToRelease?.stop() }
        runCatching { trackToRelease?.release() }

        if (threadToJoin != null && threadToJoin !== Thread.currentThread()) {
            runCatching { threadToJoin.join(STOP_JOIN_TIMEOUT_MS) }
        }

        currentIndex = null
        queuedIndex = null
        Log.d(TAG, "STOP")
    }

    fun release() {
        stop()
        segments = emptyList()
        Log.d(TAG, "RELEASE")
    }

    private fun playFromIndex(startIndex: Int) {
        val sampleRateHz = segments.getOrNull(startIndex)?.sampleRateHz ?: return
        val track = createAudioTrack(sampleRateHz)
        synchronized(stateLock) {
            audioTrack = track
        }

        try {
            track.play()
            var index: Int? = startIndex
            while (!stopRequested.get() && index != null) {
                val segment = segments.getOrNull(index) ?: break
                currentIndex = index
                Log.d(
                    TAG,
                    "SEGMENT_START index=$index name=${segment.name} queued=$queuedIndex bytes=${segment.estimatedRamBytes}"
                )
                writeSegment(track, segment)
                if (stopRequested.get()) break

                val nextIndex = queuedIndex
                queuedIndex = null
                index = nextIndex
                Log.d(TAG, "SEGMENT_END next=$index")
            }
        } catch (error: Throwable) {
            Log.w(TAG, "PLAYBACK_ERROR message=${error.message}", error)
        } finally {
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
        }
    }

    private fun writeSegment(track: AudioTrack, segment: SampleSegment) {
        val pcm = segment.pcm16Stereo
        var offset = 0
        while (!stopRequested.get() && offset < pcm.size) {
            val bytesToWrite = minOf(WRITE_CHUNK_BYTES, pcm.size - offset)
            val written = track.write(pcm, offset, bytesToWrite)
            if (written < 0) {
                Log.w(TAG, "WRITE_ERROR code=$written current=$currentIndex queued=$queuedIndex")
                break
            }
            if (written == 0) {
                Thread.yield()
            } else {
                offset += written
            }
        }
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
        private const val WRITE_CHUNK_BYTES = 16 * 1024
        private const val BUFFER_MULTIPLIER = 4
        private const val STOP_JOIN_TIMEOUT_MS = 500L
        private const val BYTES_PER_MB = 1024.0 * 1024.0
    }
}
