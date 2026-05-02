package com.patrick.lrcreader.core.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min

object ArrangementWavRenderer {
    private const val DEQUEUE_TIMEOUT_US = 10_000L
    private const val PCM_16_BIT_BYTES_PER_SAMPLE = 2
    private const val COPY_BUFFER_SIZE_BYTES = 64 * 1024
    private const val WAV_HEADER_SIZE_BYTES = 44L
    private const val PREVIEW_FILE_PREFIX = "arrangement_preview_"

    suspend fun render(
        context: Context,
        audioPath: String,
        segments: List<Pair<Long, Long>>,
        outputFile: File? = null
    ): File = withContext(Dispatchers.IO) {
        val audioFile = File(audioPath)
        require(audioFile.isFile) { "Audio file not found: $audioPath" }

        val normalizedSegments = segments
            .map { (startMs, endMs) ->
                startMs.coerceAtLeast(0L) to endMs.coerceAtLeast(0L)
            }
            .mapNotNull { (startMs, endMs) ->
                if (endMs <= startMs) null else startMs to endMs
            }
        require(normalizedSegments.isNotEmpty()) { "No valid arrangement segments to render" }

        val sourceWavFile = File(
            context.cacheDir,
            "arrangement_source_${System.currentTimeMillis()}.wav"
        )
        val previewWavFile = outputFile ?: File(
            context.cacheDir,
            "${PREVIEW_FILE_PREFIX}${System.currentTimeMillis()}.wav"
        )

        runCatching { sourceWavFile.delete() }
        runCatching { previewWavFile.delete() }

        try {
            decodeEntireSourceToWav(
                audioPath = audioPath,
                outputFile = sourceWavFile
            )
            require(sourceWavFile.isFile && sourceWavFile.length() > WAV_HEADER_SIZE_BYTES) {
                "Decoded source WAV is empty"
            }

            val sourceInfo = readWavHeader(sourceWavFile)
            require(sourceInfo.bitsPerSample == 16) {
                "Only PCM 16-bit WAV source is supported for arrangement render"
            }

            buildPreviewWavFromSource(
                sourceWavFile = sourceWavFile,
                sourceInfo = sourceInfo,
                segments = normalizedSegments,
                outputFile = previewWavFile
            )

            require(previewWavFile.isFile && previewWavFile.length() > WAV_HEADER_SIZE_BYTES) {
                "Rendered preview WAV is empty"
            }
            previewWavFile
        } catch (error: Throwable) {
            runCatching { sourceWavFile.delete() }
            runCatching { previewWavFile.delete() }
            throw error
        } finally {
            runCatching { sourceWavFile.delete() }
        }
    }

    private fun decodeEntireSourceToWav(
        audioPath: String,
        outputFile: File
    ) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var writer: WavFileWriter? = null

        try {
            extractor.setDataSource(audioPath)
            val audioTrackIndex = findAudioTrackIndex(extractor)
                ?: error("No audio track found in $audioPath")
            extractor.selectTrack(audioTrackIndex)

            val trackFormat = extractor.getTrackFormat(audioTrackIndex)
            val mime = trackFormat.getString(MediaFormat.KEY_MIME)
                ?: error("Missing audio mime")
            val sampleRate = trackFormat.getIntegerOrNull(MediaFormat.KEY_SAMPLE_RATE)
                ?: error("Missing sample rate")
            val channelCount = trackFormat.getIntegerOrNull(MediaFormat.KEY_CHANNEL_COUNT)
                ?: 1

            codec = MediaCodec.createDecoderByType(mime).also {
                it.configure(trackFormat, null, null, 0)
                it.start()
            }

            val bufferInfo = MediaCodec.BufferInfo()
            var inputEnded = false
            var outputEnded = false
            var outputSampleRate = sampleRate
            var outputChannelCount = channelCount
            var outputEncoding = AudioFormat.ENCODING_PCM_16BIT

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
                        outputSampleRate = outputFormat.getIntegerOrNull(MediaFormat.KEY_SAMPLE_RATE)
                            ?: outputSampleRate
                        outputChannelCount = outputFormat.getIntegerOrNull(MediaFormat.KEY_CHANNEL_COUNT)
                            ?: outputChannelCount
                        outputEncoding = outputFormat.getIntegerOrNull(MediaFormat.KEY_PCM_ENCODING)
                            ?: AudioFormat.ENCODING_PCM_16BIT
                        require(outputEncoding == AudioFormat.ENCODING_PCM_16BIT) {
                            "Only PCM 16-bit output is supported for arrangement render"
                        }
                        if (writer == null) {
                            writer = WavFileWriter.init(
                                file = outputFile,
                                sampleRate = outputSampleRate,
                                channelCount = outputChannelCount
                            )
                        }
                    }

                    else -> {
                        if (outputIndex < 0) continue
                        if (bufferInfo.size > 0) {
                            val outputBuffer = codec.getOutputBuffer(outputIndex)
                            if (outputBuffer != null) {
                                val chunk = outputBuffer.duplicate().apply {
                                    position(bufferInfo.offset)
                                    limit(bufferInfo.offset + bufferInfo.size)
                                }
                                val bytes = ByteArray(chunk.remaining())
                                chunk.get(bytes)
                                (writer ?: WavFileWriter.init(
                                    file = outputFile,
                                    sampleRate = outputSampleRate,
                                    channelCount = outputChannelCount
                                ).also { writer = it }).writePcm(bytes)
                            }
                        }
                        codec.releaseOutputBuffer(outputIndex, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputEnded = true
                        }
                    }
                }
            }

            val finalizedFile = writer?.finalizeFile()
                ?: error("Failed to initialize source WAV writer")
            require(finalizedFile.isFile && finalizedFile.length() > WAV_HEADER_SIZE_BYTES) {
                "Decoded source WAV is empty"
            }
        } finally {
            runCatching { writer?.close() }
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun buildPreviewWavFromSource(
        sourceWavFile: File,
        sourceInfo: WavInfo,
        segments: List<Pair<Long, Long>>,
        outputFile: File
    ) {
        val bytesPerFrame = sourceInfo.channelCount * (sourceInfo.bitsPerSample / 8)
        require(bytesPerFrame > 0) { "Invalid bytes per frame: $bytesPerFrame" }

        val writer = WavFileWriter.init(
            file = outputFile,
            sampleRate = sourceInfo.sampleRate,
            channelCount = sourceInfo.channelCount
        )

        try {
            RandomAccessFile(sourceWavFile, "r").use { input ->
                val copyBuffer = ByteArray(COPY_BUFFER_SIZE_BYTES)
                segments.forEach { (startMs, endMs) ->
                    val startFrame = ((startMs * sourceInfo.sampleRate.toLong()) / 1_000L)
                        .coerceAtLeast(0L)
                    val endFrame = ((endMs * sourceInfo.sampleRate.toLong()) / 1_000L)
                        .coerceAtLeast(startFrame)
                    if (endFrame <= startFrame) return@forEach

                    val startByteOffset = sourceInfo.dataOffset + startFrame * bytesPerFrame.toLong()
                    val endByteOffset = sourceInfo.dataOffset + endFrame * bytesPerFrame.toLong()
                    val clampedStart = startByteOffset.coerceAtLeast(sourceInfo.dataOffset)
                    val clampedEnd = min(sourceInfo.dataEndOffset, endByteOffset)
                    if (clampedEnd <= clampedStart) return@forEach

                    input.seek(clampedStart)
                    var remainingBytes = clampedEnd - clampedStart
                    while (remainingBytes > 0L) {
                        val bytesToRead = min(copyBuffer.size.toLong(), remainingBytes).toInt()
                        val readCount = input.read(copyBuffer, 0, bytesToRead)
                        if (readCount <= 0) break
                        writer.writePcm(
                            if (readCount == copyBuffer.size) {
                                copyBuffer
                            } else {
                                copyBuffer.copyOf(readCount)
                            }
                        )
                        remainingBytes -= readCount.toLong()
                    }
                }
            }

            writer.finalizeFile()
        } catch (error: Throwable) {
            runCatching { writer.close() }
            runCatching { outputFile.delete() }
            throw error
        }
    }

    private fun readWavHeader(file: File): WavInfo {
        RandomAccessFile(file, "r").use { input ->
            require(readAscii(input, 4) == "RIFF") { "Invalid WAV header: missing RIFF" }
            readIntLe(input)
            require(readAscii(input, 4) == "WAVE") { "Invalid WAV header: missing WAVE" }

            var sampleRate: Int? = null
            var channelCount: Int? = null
            var bitsPerSample: Int? = null
            var dataOffset: Long? = null
            var dataSizeBytes: Long? = null

            while (input.filePointer < input.length()) {
                val chunkId = readAscii(input, 4)
                val chunkSize = readIntLe(input).toLong() and 0xFFFFFFFFL
                val chunkDataStart = input.filePointer

                when (chunkId) {
                    "fmt " -> {
                        readShortLe(input)
                        channelCount = readShortLe(input)
                        sampleRate = readIntLe(input)
                        readIntLe(input)
                        readShortLe(input)
                        bitsPerSample = readShortLe(input)
                    }
                    "data" -> {
                        dataOffset = chunkDataStart
                        dataSizeBytes = chunkSize
                        break
                    }
                }

                val nextChunkOffset = chunkDataStart + chunkSize + (chunkSize and 1L)
                input.seek(nextChunkOffset)
            }

            val safeSampleRate = sampleRate ?: error("WAV sampleRate not found")
            val safeChannelCount = channelCount ?: error("WAV channelCount not found")
            val safeBitsPerSample = bitsPerSample ?: error("WAV bitsPerSample not found")
            val safeDataOffset = dataOffset ?: error("WAV data chunk not found")
            val safeDataSizeBytes = dataSizeBytes ?: error("WAV data size not found")

            return WavInfo(
                sampleRate = safeSampleRate,
                channelCount = safeChannelCount,
                bitsPerSample = safeBitsPerSample,
                dataOffset = safeDataOffset,
                dataSizeBytes = safeDataSizeBytes
            )
        }
    }

    private fun findAudioTrackIndex(extractor: MediaExtractor): Int? {
        for (index in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(index)
                .getString(MediaFormat.KEY_MIME)
                .orEmpty()
            if (mime.startsWith("audio/")) return index
        }
        return null
    }

    private fun readAscii(input: RandomAccessFile, byteCount: Int): String {
        val buffer = ByteArray(byteCount)
        input.readFully(buffer)
        return String(buffer, Charsets.US_ASCII)
    }

    private fun readIntLe(input: RandomAccessFile): Int {
        val b0 = input.read()
        val b1 = input.read()
        val b2 = input.read()
        val b3 = input.read()
        require(b3 >= 0) { "Unexpected end of file while reading WAV int" }
        return (b0 and 0xFF) or
            ((b1 and 0xFF) shl 8) or
            ((b2 and 0xFF) shl 16) or
            ((b3 and 0xFF) shl 24)
    }

    private fun readShortLe(input: RandomAccessFile): Int {
        val b0 = input.read()
        val b1 = input.read()
        require(b1 >= 0) { "Unexpected end of file while reading WAV short" }
        return (b0 and 0xFF) or ((b1 and 0xFF) shl 8)
    }

    private fun MediaFormat.getIntegerOrNull(key: String): Int? =
        if (containsKey(key)) getInteger(key) else null

    private data class WavInfo(
        val sampleRate: Int,
        val channelCount: Int,
        val bitsPerSample: Int,
        val dataOffset: Long,
        val dataSizeBytes: Long
    ) {
        val dataEndOffset: Long
            get() = dataOffset + dataSizeBytes
    }
}
