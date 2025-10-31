package com.patrick.lrcreader.core

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.Charset

/**
 * Lecture manuelle du tag ID3v2 pour récupérer le frame USLT (paroles non synchronisées)
 * -> équivalent du fichier Dart que tu avais.
 */
object Id3Lyrics {

    /**
     * Lecture à partir d’un chemin classique (fichier local)
     */
    fun extractUsltFromFilePath(path: String): String? {
        val file = File(path)
        if (!file.exists()) return null
        return try {
            val raf = RandomAccessFile(file, "r")
            val header = ByteArray(10)
            val read = raf.read(header)
            if (read < 10) return null

            if (header[0].toInt() != 0x49 || header[1].toInt() != 0x44 || header[2].toInt() != 0x33)
                return null // pas d’entête ID3

            val version = header[3].toInt()
            val flags = header[5].toInt()
            val tagSize = syncSafeToInt(header.copyOfRange(6, 10))

            var pos = 10
            if ((flags and 0x40) != 0) {
                val extHeader = readAt(raf, pos, 4)
                val extSize = if (version == 4) syncSafeToInt(extHeader) else u32(extHeader)
                pos += 4 + extSize
            }

            val end = 10 + tagSize
            while (pos + 10 <= end) {
                val frameHeader = readAt(raf, pos, 10)
                val id = String(frameHeader, 0, 4, Charsets.US_ASCII)
                val frameSize =
                    if (version == 4) syncSafeToInt(frameHeader.copyOfRange(4, 8))
                    else u32(frameHeader.copyOfRange(4, 8))
                pos += 10

                if (frameSize <= 0 || pos + frameSize > end) break

                val data = readAt(raf, pos, frameSize)
                if (id == "USLT") {
                    val enc = data[0].toInt()
                    var cursor = 1 + 3
                    val terminator =
                        if (enc == 1 || enc == 2) byteArrayOf(0x00, 0x00) else byteArrayOf(0x00)
                    while (cursor + terminator.size <= data.size) {
                        if (matchTerminator(data, cursor, terminator)) {
                            cursor += terminator.size
                            break
                        }
                        cursor++
                    }
                    val textBytes = data.copyOfRange(cursor, data.size)
                    val text = decodeId3String(enc, textBytes).trim()
                    return if (text.isEmpty()) null else text
                }

                pos += frameSize
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Lecture à partir d’un flux SAF (Uri content://)
     * utilisée par le bouton “Choisir un MP3”
     */
    fun extractUsltFromUri(context: Context, uri: Uri): String? {
        return try {
            val input = context.contentResolver.openInputStream(uri) ?: return null
            val bytes = input.use { it.readBytes() }
            extractUsltFromBytes(bytes)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Lecture à partir d’un tableau de bytes déjà en mémoire (utile pour SAF)
     */
    fun extractUsltFromBytes(bytes: ByteArray): String? {
        if (bytes.size < 10) return null
        if (bytes[0] != 0x49.toByte() || bytes[1] != 0x44.toByte() || bytes[2] != 0x33.toByte())
            return null

        val version = bytes[3].toInt()
        val flags = bytes[5].toInt()
        val tagSize = syncSafeToInt(bytes.copyOfRange(6, 10))

        var pos = 10
        if ((flags and 0x40) != 0) {
            val extHeader = bytes.copyOfRange(pos, pos + 4)
            val extSize = if (version == 4) syncSafeToInt(extHeader) else u32(extHeader)
            pos += 4 + extSize
        }

        val end = 10 + tagSize
        while (pos + 10 <= end && pos + 10 <= bytes.size) {
            val frameHeader = bytes.copyOfRange(pos, pos + 10)
            val id = String(frameHeader, 0, 4, Charsets.US_ASCII)
            val frameSize =
                if (version == 4) syncSafeToInt(frameHeader.copyOfRange(4, 8))
                else u32(frameHeader.copyOfRange(4, 8))
            pos += 10
            if (frameSize <= 0 || pos + frameSize > end || pos + frameSize > bytes.size) break

            val data = bytes.copyOfRange(pos, pos + frameSize)
            if (id == "USLT") {
                val enc = data[0].toInt()
                var cursor = 1 + 3
                val terminator =
                    if (enc == 1 || enc == 2) byteArrayOf(0x00, 0x00) else byteArrayOf(0x00)
                while (cursor + terminator.size <= data.size) {
                    if (matchTerminator(data, cursor, terminator)) {
                        cursor += terminator.size
                        break
                    }
                    cursor++
                }
                val textBytes = data.copyOfRange(cursor, data.size)
                val text = decodeId3String(enc, textBytes).trim()
                return if (text.isEmpty()) null else text
            }

            pos += frameSize
        }
        return null
    }

    // ---------------- helpers ----------------

    private fun readAt(raf: RandomAccessFile, pos: Int, len: Int): ByteArray {
        raf.seek(pos.toLong())
        val bytes = ByteArray(len)
        raf.readFully(bytes)
        return bytes
    }

    private fun syncSafeToInt(b: ByteArray): Int =
        (b[0].toInt() and 0x7F shl 21) or
                (b[1].toInt() and 0x7F shl 14) or
                (b[2].toInt() and 0x7F shl 7) or
                (b[3].toInt() and 0x7F)

    private fun u32(b: ByteArray): Int =
        (b[0].toInt() and 0xFF shl 24) or
                (b[1].toInt() and 0xFF shl 16) or
                (b[2].toInt() and 0xFF shl 8) or
                (b[3].toInt() and 0xFF)

    private fun matchTerminator(data: ByteArray, start: Int, term: ByteArray): Boolean {
        if (start + term.size > data.size) return false
        for (i in term.indices) {
            if (data[start + i] != term[i]) return false
        }
        return true
    }

    /**
     * 0=ISO-8859-1, 1=UTF-16 (BOM), 2=UTF-16BE (sans BOM), 3=UTF-8
     */
    private fun decodeId3String(enc: Int, bytes: ByteArray): String {
        return try {
            when (enc) {
                0 -> bytes.toString(Charset.forName("ISO-8859-1"))
                3 -> bytes.toString(Charsets.UTF_8)
                1 -> {
                    if (bytes.size >= 2) {
                        val b0 = bytes[0].toInt() and 0xFF
                        val b1 = bytes[1].toInt() and 0xFF
                        return when {
                            b0 == 0xFF && b1 == 0xFE ->
                                String(bytes.copyOfRange(2, bytes.size), Charsets.UTF_16LE)
                            b0 == 0xFE && b1 == 0xFF ->
                                String(bytes.copyOfRange(2, bytes.size), Charsets.UTF_16BE)
                            else -> String(bytes, Charsets.UTF_16LE)
                        }
                    } else {
                        String(bytes, Charsets.UTF_16LE)
                    }
                }
                2 -> String(bytes, Charsets.UTF_16BE)
                else -> String(bytes, Charsets.UTF_8)
            }
        } catch (e: Exception) {
            String(bytes, Charsets.UTF_8)
        }
    }
}

/**
 * Petit utilitaire global pour lire un tag USLT via un Uri (depuis Compose)
 */
fun readUsltFromUri(context: Context, uri: Uri): String? {
    return Id3Lyrics.extractUsltFromUri(context, uri)
}