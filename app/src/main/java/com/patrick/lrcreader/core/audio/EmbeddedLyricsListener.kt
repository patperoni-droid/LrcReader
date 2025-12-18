package com.patrick.lrcreader.core.audio

import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.Metadata
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.id3.BinaryFrame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.charset.Charset

@UnstableApi
class EmbeddedLyricsListener : Player.Listener {

    companion object {
        private const val TAG = "LYRICS"
    }

    private val _lyrics = MutableStateFlow<String?>(null)
    val lyrics: StateFlow<String?> = _lyrics

    fun reset() {
        _lyrics.value = null
    }

    // 1) Méthode "événement metadata" (parfois ne se déclenche jamais)
    override fun onMetadata(metadata: Metadata) {
        Log.d(TAG, "onMetadata() entries=${metadata.length()}")
        tryExtractUsltFromMetadata(metadata, source = "onMetadata")
    }

    // 2) Méthode fiable : on scanne les metadata des formats audio quand les tracks sont prêts
    override fun onTracksChanged(tracks: Tracks) {
        Log.d(TAG, "onTracksChanged() groups=${tracks.groups.size}")

        for (g in tracks.groups) {
            // On ne garde que l'audio
            if (g.type != C.TRACK_TYPE_AUDIO) continue

            for (t in 0 until g.length) {
                val format = g.getTrackFormat(t)
                val md = format.metadata
                if (md != null) {
                    Log.d(TAG, "TrackFormat metadata found ✅ entries=${md.length()}")
                    val got = tryExtractUsltFromMetadata(md, source = "onTracksChanged")
                    if (got) return
                }
            }
        }

        Log.d(TAG, "No USLT found in track formats")
    }

    private fun tryExtractUsltFromMetadata(metadata: Metadata, source: String): Boolean {
        // DEBUG: log des entrées
        for (i in 0 until metadata.length()) {
            val entry = metadata[i]
            Log.d(TAG, "[$source] entry[$i] type=${entry.javaClass.simpleName} value=$entry")
        }

        // Recherche USLT via BinaryFrame id="USLT"
        for (i in 0 until metadata.length()) {
            val entry = metadata[i]
            if (entry is BinaryFrame && entry.id == "USLT") {
                Log.d(TAG, "[$source] USLT frame found ✅")

                val text = parseUslt(entry.data)
                if (!text.isNullOrBlank()) {
                    Log.d(TAG, "[$source] USLT parsed OK (${text.length} chars) ✅")
                    _lyrics.value = text
                    return true
                } else {
                    Log.d(TAG, "[$source] USLT found but empty after parse ⚠️")
                }
            }
        }
        return false
    }

    private fun parseUslt(data: ByteArray): String? {
        if (data.isEmpty()) return null

        val enc = data[0].toInt() and 0xFF

        val charset: Charset = when (enc) {
            0 -> Charsets.ISO_8859_1
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> Charsets.UTF_8
        }

        // [0]=encoding, [1..3]=language
        var pos = 1 + 3
        if (pos >= data.size) return null

        // Skip descriptor (null-terminated)
        pos = skipNullTerminatedString(data, pos, enc)
        if (pos >= data.size) return null

        val textBytes = data.copyOfRange(pos, data.size)
        return runCatching { String(textBytes, charset).trim() }.getOrNull()
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
                i++
            }
            data.size
        }
    }
}
