package com.patrick.lrcreader.core

import android.util.Log
import android.content.Context
import java.io.File
import java.security.MessageDigest
import java.util.Locale

object LrcStorage {

    /** Renvoie le fichier .lrc associé à un uriString (hashé en MD5) */
    private fun lrcFileForUri(context: Context, uriString: String?): File? {
        if (uriString.isNullOrBlank()) return null

        val dir = File(context.filesDir, "lrc_cache").apply { mkdirs() }

        val md = MessageDigest.getInstance("MD5").digest(uriString.toByteArray())
        val hex = md.joinToString("") { "%02x".format(it) }

        return File(dir, "$hex.lrc")
    }

    /** Sauvegarde des paroles synchronisées pour un morceau donné */
    /** Sauvegarde des paroles synchronisées pour un morceau donné */
    fun saveForTrack(
        context: Context,
        trackUriString: String?,
        lines: List<LrcLine>
    ) {
        val outFile = lrcFileForUri(context, trackUriString) ?: return

        val content = buildString {
            lines.forEach { line ->
                val ts = if (line.timeMs > 0L) {
                    formatLrcTimestamp(line.timeMs)
                } else {
                    "00:00.00"
                }
                append("[")
                append(ts)
                append("]")
                append(line.text)
                append('\n')
            }
        }

        Log.d(
            "LrcDebug",
            "SAVE track=$trackUriString file=${outFile.name} len=${content.length} lines=${lines.size}"
        )

        outFile.writeText(content)
    }
    fun deleteForTrack(
        context: Context,
        trackUriString: String?
    ) {
        val f = lrcFileForUri(context, trackUriString) ?: return
        if (f.exists()) {
            f.delete()
            Log.d(
                "LrcDebug",
                "DELETE track=$trackUriString file=${f.name}"
            )
        }
    }
    /** Recharge le .lrc sauvegardé pour ce morceau (si présent) */
    fun loadForTrack(
        context: Context,
        trackUriString: String?
    ): String? {
        val f = lrcFileForUri(context, trackUriString) ?: return null
        val exists = f.exists() && f.isFile

        Log.d(
            "LrcDebug",
            "LOAD track=$trackUriString file=${f.name} exists=$exists"
        )

        return if (exists) f.readText() else null
    }

    /** Format LRC style : mm:ss.xx */
    private fun formatLrcTimestamp(ms: Long): String {
        if (ms <= 0L) return "00:00.00"
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val hundredths = (ms % 1000) / 10
        return String.format(Locale.US, "%02d:%02d.%02d", minutes, seconds, hundredths)
    }
}