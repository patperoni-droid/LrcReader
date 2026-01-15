package com.patrick.lrcreader.core

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.Charset
import java.util.Locale

/**
 * Lecture manuelle du tag ID3v2 pour récupérer :
 * - USLT : paroles non synchronisées (texte brut)
 * - SYLT : paroles synchronisées (timecodes) -> converties en texte LRC
 */
object Id3Lyrics {

    /** Lecture USLT à partir d’un chemin classique (fichier local) */
    fun extractUsltFromFilePath(path: String): String? =
        extractFrameTextFromFilePath(path, targetFrame = "USLT")

    /** Lecture USLT à partir d’un flux SAF (Uri content://) */
    fun extractUsltFromUri(context: Context, uri: Uri): String? =
        extractFrameTextFromUri(context, uri, targetFrame = "USLT")

    /** Lecture USLT à partir d’un tableau de bytes déjà en mémoire */
    fun extractUsltFromBytes(bytes: ByteArray): String? =
        extractFrameTextFromBytes(bytes, targetFrame = "USLT")

    /**
     * Lecture SYLT (synchronisé) et conversion en texte LRC.
     * Retourne un texte du style : [00:12.34]Hello ...
     */
    fun extractSyltAsLrcFromUri(context: Context, uri: Uri): String? {
        return try {
            val input = context.contentResolver.openInputStream(uri) ?: return null
            val bytes = input.use { it.readBytes() }
            extractSyltAsLrcFromBytes(bytes)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun extractSyltAsLrcFromBytes(bytes: ByteArray): String? =
        extractFrameTextFromBytes(bytes, targetFrame = "SYLT")

    // ---------------- core extractors ----------------

    private fun extractFrameTextFromFilePath(path: String, targetFrame: String): String? {
        val file = File(path)
        if (!file.exists()) return null

        return try {
            RandomAccessFile(file, "r").use { raf ->
                val header = ByteArray(10)
                val read = raf.read(header)
                if (read < 10) return null

                if (header[0].toInt() != 0x49 || header[1].toInt() != 0x44 || header[2].toInt() != 0x33)
                    return null // pas d’entête ID3

                val version = header[3].toInt() // 3 ou 4
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
                    if (id == targetFrame) {
                        return when (targetFrame) {
                            "USLT" -> parseUsltFrameToText(data)
                            "SYLT" -> parseSyltFrameToLrc(data)
                            else -> null
                        }
                    }
                    pos += frameSize
                }
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun extractFrameTextFromUri(context: Context, uri: Uri, targetFrame: String): String? {
        return try {
            val input = context.contentResolver.openInputStream(uri) ?: return null
            val bytes = input.use { it.readBytes() }
            extractFrameTextFromBytes(bytes, targetFrame)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun extractFrameTextFromBytes(bytes: ByteArray, targetFrame: String): String? {
        val frame = findFrame(bytes, targetFrame) ?: return null
        return when (targetFrame) {
            "USLT" -> parseUsltFrameToText(frame)
            "SYLT" -> parseSyltFrameToLrc(frame)
            else -> null
        }
    }

    /**
     * Trouve le payload d’un frame (USLT/SYLT).
     * Gère ID3v2.3 et v2.4 (taille syncsafe pour v2.4).
     */
    private fun findFrame(bytes: ByteArray, targetFrame: String): ByteArray? {
        if (bytes.size < 10) return null
        if (bytes[0] != 0x49.toByte() || bytes[1] != 0x44.toByte() || bytes[2] != 0x33.toByte())
            return null

        val version = bytes[3].toInt()
        val flags = bytes[5].toInt()
        val tagSize = syncSafeToInt(bytes.copyOfRange(6, 10))

        var pos = 10
        if ((flags and 0x40) != 0) {
            if (pos + 4 > bytes.size) return null
            val extHeader = bytes.copyOfRange(pos, pos + 4)
            val extSize = if (version == 4) syncSafeToInt(extHeader) else u32(extHeader)
            pos += 4 + extSize
        }

        val end = minOf(10 + tagSize, bytes.size)
        while (pos + 10 <= end) {
            val frameHeader = bytes.copyOfRange(pos, pos + 10)

            // padding: fin des frames
            if (frameHeader[0].toInt() == 0 && frameHeader[1].toInt() == 0) return null

            val id = String(frameHeader, 0, 4, Charsets.US_ASCII)
            val frameSize =
                if (version == 4) syncSafeToInt(frameHeader.copyOfRange(4, 8))
                else u32(frameHeader.copyOfRange(4, 8))

            pos += 10
            if (frameSize <= 0 || pos + frameSize > end) return null

            val data = bytes.copyOfRange(pos, pos + frameSize)
            if (id == targetFrame) return data

            pos += frameSize
        }
        return null
    }

    // ---------------- frame parsers ----------------

    /**
     * USLT payload -> texte brut
     * Format: [enc][lang(3)][contentDesc(0-term)][lyrics]
     */
    private fun parseUsltFrameToText(data: ByteArray): String? {
        if (data.isEmpty()) return null
        val enc = data[0].toInt()
        var cursor = 1 + 3 // encoding + language

        val term = if (enc == 1 || enc == 2) byteArrayOf(0x00, 0x00) else byteArrayOf(0x00)

        // saute content descriptor
        while (cursor + term.size <= data.size) {
            if (matchTerminator(data, cursor, term)) {
                cursor += term.size
                break
            }
            cursor++
        }

        if (cursor >= data.size) return null
        val textBytes = data.copyOfRange(cursor, data.size)
        val text = decodeId3String(enc, textBytes).trim()
        return if (text.isEmpty()) null else text
    }

    /**
     * SYLT payload -> convertit en texte LRC.
     * SYLT: [enc][lang(3)][timestampFormat][contentType][contentDesc(0-term)][(text 0-term)(time 4bytes)]*
     *
     * timestampFormat :
     *  - 1 = ms (on gère)
     *  - 2 = frames (ignoré)
     */
    private fun parseSyltFrameToLrc(data: ByteArray): String? {
        if (data.size < 6) return null
        val enc = data[0].toInt()
        var cursor = 1

        // lang
        cursor += 3
        if (cursor >= data.size) return null

        val timestampFormat = data[cursor].toInt() and 0xFF
        cursor += 1

        // contentType
        cursor += 1
        if (cursor >= data.size) return null

        val term = if (enc == 1 || enc == 2) byteArrayOf(0x00, 0x00) else byteArrayOf(0x00)

        // saute content descriptor
        while (cursor + term.size <= data.size) {
            if (matchTerminator(data, cursor, term)) {
                cursor += term.size
                break
            }
            cursor++
        }
        if (cursor >= data.size) return null

        val sb = StringBuilder()
        while (cursor < data.size) {
            // text jusqu'au terminator
            val textStart = cursor
            var textEnd = -1
            while (cursor + term.size <= data.size) {
                if (matchTerminator(data, cursor, term)) {
                    textEnd = cursor
                    cursor += term.size
                    break
                }
                cursor++
            }
            if (textEnd < 0) break

            // time (4 bytes)
            if (cursor + 4 > data.size) break
            val t = u32(data.copyOfRange(cursor, cursor + 4))
            cursor += 4

            if (timestampFormat != 1) continue // ms only

            val textBytes = data.copyOfRange(textStart, textEnd)
            val text = decodeId3String(enc, textBytes).trim()
            if (text.isNotBlank()) {
                sb.append('[').append(formatLrcTimestamp(t.toLong())).append(']').append(text).append('\n')
            }
        }

        val out = sb.toString().trim()
        return if (out.isEmpty()) null else out
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
                        when {
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
        } catch (_: Exception) {
            String(bytes, Charsets.UTF_8)
        }
    }

    /** Format LRC : mm:ss.xx */
    private fun formatLrcTimestamp(ms: Long): String {
        if (ms <= 0L) return "00:00.00"
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val hundredths = (ms % 1000) / 10
        return String.format(Locale.US, "%02d:%02d.%02d", minutes, seconds, hundredths)
    }
}

/** Utilitaire global pour lire un tag USLT via un Uri */
fun readUsltFromUri(context: Context, uri: Uri): String? {
    return Id3Lyrics.extractUsltFromUri(context, uri)
}

/** NEW : Utilitaire global pour lire SYLT (converti en LRC) via un Uri */
fun readSyltAsLrcFromUri(context: Context, uri: Uri): String? {
    return Id3Lyrics.extractSyltAsLrcFromUri(context, uri)
}