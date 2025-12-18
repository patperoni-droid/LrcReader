package com.patrick.lrcreader.core.audio

import java.nio.charset.Charset
import kotlin.math.floor

/**
 * Parse un frame ID3 "SYLT" (synchronised lyrics) et le convertit en texte LRC
 * (lignes "[mm:ss.xx]paroles") pour réutiliser parseLrc() tel quel.
 *
 * Spéc ID3 SYLT (v2.3/v2.4):
 * [0]=encoding
 * [1..3]=language
 * [4]=timestamp format (1=frames, 2=milliseconds)
 * [5]=content type
 * puis descriptor (null-terminated)
 * puis répétitions: (text null-terminated) + (timestamp 4 bytes big-endian)
 */
object SyltParser {

    fun parseToLrcTextOrNull(data: ByteArray): String? {
        val lines = parseToTimedLinesOrNull(data) ?: return null
        if (lines.isEmpty()) return null

        // Convertit en LRC
        val sb = StringBuilder(lines.size * 24)
        for ((timeMs, text) in lines) {
            val tag = toLrcTag(timeMs)
            sb.append(tag).append(text).append('\n')
        }
        return sb.toString().trim().ifBlank { null }
    }

    /**
     * Retourne une liste (timeMs, text) triée.
     * Ne gère que timestampFormat == 2 (milliseconds).
     */
    fun parseToTimedLinesOrNull(data: ByteArray): List<Pair<Long, String>>? {
        if (data.isEmpty()) return null

        val enc = data[0].toInt() and 0xFF
        val charset: Charset = when (enc) {
            0 -> Charsets.ISO_8859_1
            1 -> Charsets.UTF_16      // avec BOM
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> Charsets.UTF_8
        }

        // 1 byte enc + 3 bytes lang + 1 byte tsFormat + 1 byte contentType
        var pos = 1 + 3
        if (pos + 2 > data.size) return null

        val timestampFormat = data[pos].toInt() and 0xFF
        pos += 1
        pos += 1 // content type (on ne l'utilise pas)

        // On ne sait convertir correctement que ms
        if (timestampFormat != 2) return null

        // Skip descriptor (null-terminated)
        pos = skipNullTerminatedString(data, pos, enc)
        if (pos >= data.size) return null

        val out = ArrayList<Pair<Long, String>>(64)

        while (pos < data.size) {
            val textRes = readNullTerminatedString(data, pos, enc, charset)
            val text = textRes.first
            pos = textRes.second
            if (pos + 4 > data.size) break

            val ts = readUInt32BE(data, pos)
            pos += 4

            val clean = text.trim()
            if (clean.isNotEmpty()) {
                out.add(ts to clean)
            }
        }

        if (out.isEmpty()) return null

        // Tri + dédoublonnage simple (même time+text)
        return out
            .sortedBy { it.first }
            .distinctBy { "${it.first}__${it.second}" }
    }

    private fun readUInt32BE(b: ByteArray, offset: Int): Long {
        val b0 = b[offset].toLong() and 0xFF
        val b1 = b[offset + 1].toLong() and 0xFF
        val b2 = b[offset + 2].toLong() and 0xFF
        val b3 = b[offset + 3].toLong() and 0xFF
        return (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
    }

    private fun toLrcTag(timeMs: Long): String {
        val totalSeconds = timeMs / 1000.0
        val mm = floor(totalSeconds / 60.0).toInt()
        val ss = floor(totalSeconds % 60.0).toInt()
        val cs = floor((timeMs % 1000) / 10.0).toInt() // centièmes

        val mmStr = mm.toString().padStart(2, '0')
        val ssStr = ss.toString().padStart(2, '0')
        val csStr = cs.toString().padStart(2, '0')
        return "[$mmStr:$ssStr.$csStr]"
    }

    private fun readNullTerminatedString(
        data: ByteArray,
        start: Int,
        enc: Int,
        charset: Charset
    ): Pair<String, Int> {
        var i = start

        return if (enc == 1 || enc == 2) {
            // UTF-16: terminator 0x00 0x00
            while (i + 1 < data.size) {
                if (data[i] == 0.toByte() && data[i + 1] == 0.toByte()) {
                    val bytes = data.copyOfRange(start, i)
                    val s = runCatching { String(bytes, charset) }.getOrNull().orEmpty()
                    return s to (i + 2)
                }
                i += 2
            }
            val bytes = data.copyOfRange(start, data.size)
            val s = runCatching { String(bytes, charset) }.getOrNull().orEmpty()
            s to data.size
        } else {
            // ISO-8859-1 / UTF-8: terminator 0x00
            while (i < data.size) {
                if (data[i] == 0.toByte()) {
                    val bytes = data.copyOfRange(start, i)
                    val s = runCatching { String(bytes, charset) }.getOrNull().orEmpty()
                    return s to (i + 1)
                }
                i += 1
            }
            val bytes = data.copyOfRange(start, data.size)
            val s = runCatching { String(bytes, charset) }.getOrNull().orEmpty()
            s to data.size
        }
    }

    private fun skipNullTerminatedString(data: ByteArray, start: Int, enc: Int): Int {
        var i = start
        return if (enc == 1 || enc == 2) {
            while (i + 1 < data.size) {
                if (data[i] == 0.toByte() && data[i + 1] == 0.toByte()) return i + 2
                i += 2
            }
            data.size
        } else {
            while (i < data.size) {
                if (data[i] == 0.toByte()) return i + 1
                i += 1
            }
            data.size
        }
    }
}
