package com.patrick.lrcreader.core.audio

import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

class WavFileWriter(
    private val file: File,
    private val sampleRate: Int,
    private val channelCount: Int
) : Closeable {

    private val output = BufferedOutputStream(FileOutputStream(file))
    private var dataSizeBytes: Long = 0L
    private var finalized = false

    init {
        require(sampleRate > 0) { "sampleRate must be > 0" }
        require(channelCount > 0) { "channelCount must be > 0" }
        writeHeaderPlaceholder()
    }

    fun writePcm(buffer: ByteArray) {
        if (buffer.isEmpty()) return
        check(!finalized) { "WAV file already finalized" }
        output.write(buffer)
        dataSizeBytes += buffer.size.toLong()
    }

    fun finalizeFile(): File {
        if (finalized) return file
        finalized = true
        output.flush()
        output.close()

        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(0L)
            writeAscii(raf, "RIFF")
            writeIntLe(raf, (36L + dataSizeBytes).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            writeAscii(raf, "WAVE")
            writeAscii(raf, "fmt ")
            writeIntLe(raf, 16)
            writeShortLe(raf, 1)
            writeShortLe(raf, channelCount.toShort())
            writeIntLe(raf, sampleRate)
            writeIntLe(raf, sampleRate * channelCount * PCM_16_BIT_BYTES_PER_SAMPLE)
            writeShortLe(raf, (channelCount * PCM_16_BIT_BYTES_PER_SAMPLE).toShort())
            writeShortLe(raf, 16)
            writeAscii(raf, "data")
            writeIntLe(raf, dataSizeBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        }

        return file
    }

    override fun close() {
        if (!finalized) {
            output.close()
        }
    }

    private fun writeHeaderPlaceholder() {
        repeat(WAV_HEADER_SIZE_BYTES) {
            output.write(0)
        }
    }

    private fun writeAscii(raf: RandomAccessFile, value: String) {
        raf.write(value.toByteArray(Charsets.US_ASCII))
    }

    private fun writeIntLe(raf: RandomAccessFile, value: Int) {
        raf.write(value and 0xFF)
        raf.write((value shr 8) and 0xFF)
        raf.write((value shr 16) and 0xFF)
        raf.write((value shr 24) and 0xFF)
    }

    private fun writeShortLe(raf: RandomAccessFile, value: Short) {
        val intValue = value.toInt() and 0xFFFF
        raf.write(intValue and 0xFF)
        raf.write((intValue shr 8) and 0xFF)
    }

    companion object {
        private const val WAV_HEADER_SIZE_BYTES = 44
        private const val PCM_16_BIT_BYTES_PER_SAMPLE = 2

        fun init(file: File, sampleRate: Int, channelCount: Int): WavFileWriter =
            WavFileWriter(file, sampleRate, channelCount)
    }
}
