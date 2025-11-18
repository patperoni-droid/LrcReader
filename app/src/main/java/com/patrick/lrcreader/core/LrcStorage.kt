package com.patrick.lrcreader.core

import android.content.Context
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/** Renvoie le fichier .lrc associé à un uriString (hashé en MD5) */
private fun lrcFileForUri(context: Context, uriString: String?): File? {
    if (uriString.isNullOrBlank()) return null

    val dir = File(context.filesDir, "lrc_cache").apply { mkdirs() }

    val md = MessageDigest.getInstance("MD5").digest(uriString.toByteArray())
    val hex = md.joinToString("") { "%02x".format(it) }

    return File(dir, "$hex.lrc")
}

/** Sauvegarde des paroles synchronisées pour un morceau donné */
fun saveLrcForTrack(
    context: Context,
    trackUriString: String?,
    lines: List<LrcLine>   // 👉 on utilise TA data class qui est dans LrcLine.kt
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

    outFile.writeText(content)
}

/** Recharge le .lrc sauvegardé pour ce morceau (si présent) */
fun loadLrcForTrack(
    context: Context,
    trackUriString: String?
): String? {
    val f = lrcFileForUri(context, trackUriString) ?: return null
    return if (f.exists() && f.isFile) f.readText() else null
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