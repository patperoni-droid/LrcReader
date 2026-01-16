package com.patrick.lrcreader.core

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.security.MessageDigest

object LrcStorage {

    // Nom de fichier déterministe pour éviter collisions + pas besoin de scanner
    private fun fileNameForTrack(trackUriString: String): String {
        val md5 = MessageDigest.getInstance("MD5")
            .digest(trackUriString.toByteArray())
            .joinToString("") { "%02x".format(it) }

        val base = runCatching {
            Uri.parse(trackUriString).lastPathSegment ?: "track"
        }.getOrNull() ?: "track"

        val cleanBase = base.substringAfterLast('/').substringBeforeLast('.')
            .take(40)
            .ifBlank { "track" }

        return "${cleanBase}-${md5.take(10)}.lrc"
    }

    private fun getLyricsDir(context: Context): DocumentFile? {
        val dirUri = LyricsFolderPrefs.get(context) ?: return null
        return DocumentFile.fromTreeUri(context, dirUri)
    }

    fun loadForTrack(context: Context, trackUriString: String?): String? {
        if (trackUriString.isNullOrBlank()) return null

        // 1) Dossier externe SPL_Music/BackingTracks/lyrics (persistant)
        val dir = getLyricsDir(context)
        if (dir != null && dir.isDirectory) {
            val targetName = fileNameForTrack(trackUriString)
            val existing = dir.findFile(targetName)
            if (existing != null && existing.isFile) {
                return runCatching {
                    context.contentResolver.openInputStream(existing.uri)
                        ?.bufferedReader(Charsets.UTF_8)
                        ?.use { it.readText() }
                }.getOrNull()
            }
        }

        // 2) Fallback : ancien cache interne (compat)
        return runCatching { LegacyInternalCache.load(context, trackUriString) }.getOrNull()
    }

    fun saveForTrack(
        context: Context,
        trackUriString: String?,
        lines: List<LrcLine>
    ) {
        android.util.Log.d("LrcDebug", "SAVE called track=$trackUriString lines=${lines.size}")

        if (trackUriString.isNullOrBlank()) {
            android.util.Log.d("LrcDebug", "SAVE abort: trackUriString is null/blank")
            return
        }

        val dir = getLyricsDir(context)
        android.util.Log.d("LrcDebug", "SAVE lyricsDir=${dir?.uri}")

        if (dir == null || !dir.isDirectory) {
            android.util.Log.d("LrcDebug", "SAVE abort: lyricsDir invalid")
            return
        }

        val name = fileNameForTrack(trackUriString)
        android.util.Log.d("LrcDebug", "SAVE filename=$name")

        val content = lines.joinToString("\n") { line ->
            if (line.timeMs > 0L) {
                val mm = (line.timeMs / 60000).toInt()
                val ss = ((line.timeMs % 60000) / 1000).toInt()
                val xx = ((line.timeMs % 1000) / 10).toInt()
                "[%02d:%02d.%02d] %s".format(mm, ss, xx, line.text)
            } else {
                line.text
            }
        }

        android.util.Log.d("LrcDebug", "SAVE contentLength=${content.length}")

        // Supprime l’ancien fichier si présent
        dir.findFile(name)?.let {
            android.util.Log.d("LrcDebug", "SAVE deleting existing file ${it.uri}")
            it.delete()
        }

        // Création du fichier
        val doc = dir.createFile("application/x-lrc", name)
        if (doc == null) {
            android.util.Log.d("LrcDebug", "SAVE abort: createFile failed")
            return
        }
        android.util.Log.d("LrcDebug", "SAVE file created uri=${doc.uri}")

        // Écriture
        val os = context.contentResolver.openOutputStream(doc.uri, "wt")
        if (os == null) {
            android.util.Log.e("LrcDebug", "SAVE abort: openOutputStream returned null for uri=${doc.uri}")
            return
        }
        os.use { it.write(content.toByteArray(Charsets.UTF_8)) }
        android.util.Log.d("LrcDebug", "SAVE DONE ✅ bytes=${content.toByteArray(Charsets.UTF_8).size}")

        // ✅ MAJ INDEX (pas besoin de rescan)
        val parent = dir.uri.toString()

        val newIndex = com.patrick.lrcreader.core.LibraryIndexCache.upsert(
            context = context,
            uriString = doc.uri.toString(),
            name = name,
            isDirectory = false,
            parentUriString = parent
        )
        android.util.Log.d("LrcDebug", "INDEX upsert lrc ok size=${newIndex.size}")

        // ✅ Réveille l'UI (si tu observes une "version" côté LibraryScreen)

    }

    // ---------- Helpers LRC ----------
    private fun formatLrcTime(ms: Long): String {
        val total = (ms / 10) // centièmes
        val min = total / 6000
        val sec = (total % 6000) / 100
        val cs = total % 100
        return "[%02d:%02d.%02d]".format(min, sec, cs)
    }

    fun deleteForTrack(context: Context, trackUriString: String?) {
        if (trackUriString.isNullOrBlank()) return
        android.util.Log.d("LrcDebug", "DELETE called track=$trackUriString")

        // 1) Suppression dans SPL_Music/BackingTracks/lyrics
        val dir = getLyricsDir(context)
        if (dir != null && dir.isDirectory) {
            val name = fileNameForTrack(trackUriString)
            val file = dir.findFile(name)
            if (file != null) {
                android.util.Log.d("LrcDebug", "DELETE external ${file.uri}")
                file.delete()
            } else {
                android.util.Log.d("LrcDebug", "DELETE external: file not found")
            }
        }

        // 2) Fallback : ancien cache interne
        runCatching {
            val dirInternal = java.io.File(context.filesDir, "lrc_cache").apply { mkdirs() }
            val md5 = MessageDigest.getInstance("MD5")
                .digest(trackUriString.toByteArray())
                .joinToString("") { "%02x".format(it) }
            val f = java.io.File(dirInternal, "$md5.lrc")
            if (f.exists()) {
                android.util.Log.d("LrcDebug", "DELETE internal ${f.absolutePath}")
                f.delete()
            }
        }

        // ✅ Réveille l'UI après suppression

    }
}


/**
 * Ancien système interne (pour ne pas casser les users existants).
 * Tu peux le supprimer plus tard quand tout le monde aura migré.
 */
private object LegacyInternalCache {
    private fun fileForUri(context: Context, trackUriString: String): java.io.File {
        val dir = java.io.File(context.filesDir, "lrc_cache").apply { mkdirs() }
        val md5 = MessageDigest.getInstance("MD5")
            .digest(trackUriString.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return java.io.File(dir, "$md5.lrc")
    }

    fun load(context: Context, trackUriString: String): String? {
        val f = fileForUri(context, trackUriString)
        return if (f.exists()) runCatching { f.readText(Charsets.UTF_8) }.getOrNull() else null
    }

    fun save(context: Context, trackUriString: String, text: String) {
        val f = fileForUri(context, trackUriString)
        runCatching { f.writeText(text, Charsets.UTF_8) }
    }
}