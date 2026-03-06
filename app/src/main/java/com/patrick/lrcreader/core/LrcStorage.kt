package com.patrick.lrcreader.core

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.security.MessageDigest

object LrcStorage {

    data class TrackLrcOrigin(
        val source: String,
        val fileName: String?,
        val debugPath: String?
    )

    private const val TAG = "LRC_STORAGE"
    private const val CACHE_DIR = "lrc_cache"

    // ------------------------------------------------------------
    // API
    // ------------------------------------------------------------

    fun loadForTrack(context: Context, trackUriString: String): String? {
        if (trackUriString.isBlank()) return null

        // ✅ 1) MODE INTERNE SPL EN PRIORITÉ (BackingTracks/Lyrics)
        if (isInternalSplMode(context)) {
            Log.i(TAG, "mode INTERNAL SPL load root=${getInternalSplRoot(context).absolutePath}")

            loadFromInternalSplFolder(context, trackUriString)?.let { txt ->
                if (txt.isNotBlank()) {
                    Log.d(TAG, "mode INTERNAL SPL load hit FILE")
                    return txt
                }
            }

            // fallback : cache interne
            loadFromInternalCache(context, trackUriString)?.let { txt ->
                if (txt.isNotBlank()) {
                    Log.d(TAG, "mode INTERNAL SPL load hit INTERNAL_CACHE")
                    return txt
                }
            }

            Log.d(TAG, "mode INTERNAL SPL load miss")
            return null
        }

        // ✅ 2) SAF seulement si on n'est PAS en mode interne
        val safDir = getConfiguredSafDir(context)
        if (safDir != null) {
            Log.i(TAG, "mode SAF load dir=${safDir.uri}")

            loadFromInternalCache(context, trackUriString)?.let { txt ->
                if (txt.isNotBlank()) {
                    Log.d(TAG, "mode SAF load hit INTERNAL_CACHE")
                    return txt
                }
            }

            loadFromConfiguredFolder(context, trackUriString)?.let { txt ->
                if (txt.isNotBlank()) {
                    Log.d(TAG, "mode SAF load hit CONFIGURED")
                    return txt
                }
            }

            Log.d(TAG, "mode SAF load miss")
            return null
        }

        // 3) Mode inconnu : fallback cache interne
        loadFromInternalCache(context, trackUriString)?.let { txt ->
            if (txt.isNotBlank()) {
                Log.d(TAG, "mode UNKNOWN load hit INTERNAL_CACHE")
                return txt
            }
        }

        Log.d(TAG, "mode UNKNOWN load miss")
        return null
    }

    fun resolveOriginForTrack(context: Context, trackUriString: String): TrackLrcOrigin? {
        if (trackUriString.isBlank()) return null

        if (isInternalSplMode(context)) {
            val (upperDir, lowerDir) = internalSplLyricsDirs(context)
            val sidecar = sidecarNameForTrack(trackUriString)

            val upper = File(upperDir, sidecar)
            if (upper.exists() && upper.isFile) {
                return TrackLrcOrigin(
                    source = "LRC_STORAGE_INTERNAL_SPL_UPPER",
                    fileName = upper.name,
                    debugPath = upper.absolutePath
                )
            }

            val lower = File(lowerDir, sidecar)
            if (lower.exists() && lower.isFile) {
                return TrackLrcOrigin(
                    source = "LRC_STORAGE_INTERNAL_SPL_LOWER",
                    fileName = lower.name,
                    debugPath = lower.absolutePath
                )
            }

            val cache = internalFile(context, trackUriString)
            if (cache.exists() && cache.isFile) {
                return TrackLrcOrigin(
                    source = "LRC_STORAGE_INTERNAL_CACHE",
                    fileName = cache.name,
                    debugPath = cache.absolutePath
                )
            }

            return null
        }

        val safDir = getConfiguredSafDir(context)
        if (safDir != null) {
            val cache = internalFile(context, trackUriString)
            if (cache.exists() && cache.isFile) {
                return TrackLrcOrigin(
                    source = "LRC_STORAGE_INTERNAL_CACHE",
                    fileName = cache.name,
                    debugPath = cache.absolutePath
                )
            }

            val fileName = fileNameForTrack(trackUriString)
            val file = safDir.findFile(fileName)
            if (file != null && file.isFile) {
                return TrackLrcOrigin(
                    source = "LRC_STORAGE_SAF_CONFIGURED",
                    fileName = file.name ?: fileName,
                    debugPath = file.uri.toString()
                )
            }

            return null
        }

        val cache = internalFile(context, trackUriString)
        if (cache.exists() && cache.isFile) {
            return TrackLrcOrigin(
                source = "LRC_STORAGE_INTERNAL_CACHE",
                fileName = cache.name,
                debugPath = cache.absolutePath
            )
        }
        return null
    }

    fun saveForTrack(context: Context, trackUriString: String, lines: List<LrcLine>) {
        Log.e(
            "DEBUG_ROOT_URI",
            "BackupFolderPrefs rootUri = ${BackupFolderPrefs.getLibraryRootUri(context)}"
        )
        if (trackUriString.isBlank()) return
        val text = linesToLrcText(lines)

        // ✅ 1) MODE INTERNE SPL EN PRIORITÉ : écrit dans BackingTracks/Lyrics/<base>.lrc
        if (isInternalSplMode(context)) {
            Log.i(TAG, "mode INTERNAL SPL save root=${getInternalSplRoot(context).absolutePath}")
            val okFile = saveToInternalSplFolder(context, trackUriString, text)
            val okInternal = saveToInternalCache(context, trackUriString, text)
            Log.i(TAG, "mode INTERNAL SPL save file=$okFile internalCache=$okInternal len=${text.length}")
            return
        }

        // ✅ 2) SAF si pas interne
        val safDir = getConfiguredSafDir(context)
        if (safDir != null) {
            Log.i(TAG, "mode SAF save dir=${safDir.uri}")
            val okInternal = saveToInternalCache(context, trackUriString, text)
            val okConfigured = saveToConfiguredFolder(context, trackUriString, text)
            Log.i(TAG, "mode SAF save internalCache=$okInternal configured=$okConfigured len=${text.length}")
            return
        }

        val okInternal = saveToInternalCache(context, trackUriString, text)
        Log.w(TAG, "mode UNKNOWN save internalCache=$okInternal len=${text.length}")
    }

    fun deleteForTrack(context: Context, trackUriString: String) {
        if (trackUriString.isBlank()) return

        // cache interne
        runCatching { internalFile(context, trackUriString).delete() }

        // SAF (hash)
        runCatching {
            val safDir = getConfiguredSafDir(context) ?: return@runCatching
            val fileName = fileNameForTrack(trackUriString)
            safDir.findFile(fileName)?.delete()
        }

        // INTERNAL SPL sidecar
        runCatching {
            val (upperDir, lowerDir) = internalSplLyricsDirs(context)
            val sidecar = sidecarNameForTrack(trackUriString)
            File(upperDir, sidecar).delete()
            File(lowerDir, sidecar).delete()
        }
    }

    // ------------------------------------------------------------
    // SAF (dossier configuré)
    // ------------------------------------------------------------

    private fun getConfiguredSafDir(context: Context): DocumentFile? {
        val folderUri = LyricsFolderPrefs.get(context) ?: return null
        if (folderUri.scheme != "content") return null
        val dir = DocumentFile.fromTreeUri(context, folderUri) ?: return null
        return if (dir.isDirectory && dir.canRead()) dir else null
    }

    private fun loadFromConfiguredFolder(context: Context, trackUriString: String): String? {
        val dir = getConfiguredSafDir(context) ?: return null
        val fileName = fileNameForTrack(trackUriString)
        val file = dir.findFile(fileName) ?: return null
        if (!file.isFile) return null

        Log.d(TAG, "mode SAF load uri=${file.uri}")

        return runCatching {
            context.contentResolver.openInputStream(file.uri)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
        }.getOrNull()
    }

    private fun saveToConfiguredFolder(context: Context, trackUriString: String, text: String): Boolean {
        val dir = getConfiguredSafDir(context) ?: return false
        val fileName = fileNameForTrack(trackUriString)

        return runCatching {
            val existing = dir.findFile(fileName)
            val target = existing ?: dir.createFile("application/octet-stream", fileName) ?: return false
            Log.d(TAG, "mode SAF save uri=${target.uri}")

            context.contentResolver.openOutputStream(target.uri, "w")?.use { out ->
                out.write(text.toByteArray(Charsets.UTF_8))
                out.flush()
            } ?: return false

            true
        }.getOrDefault(false)
    }

    // ------------------------------------------------------------
    // INTERNAL SPL
    // ------------------------------------------------------------

    private fun isInternalSplMode(context: Context): Boolean {
        // Si l'utilisateur a choisi une racine SAF (content://), alors ce n'est PAS du mode interne
        val rootUri = BackupFolderPrefs.getLibraryRootUri(context)
        return rootUri == null || rootUri.scheme == "file"
    }
    private fun getInternalSplRoot(context: Context): File {
        val rootUri = BackupFolderPrefs.getLibraryRootUri(context)
        val fromPrefs = if (rootUri?.scheme == "file" && !rootUri.path.isNullOrBlank()) {
            File(rootUri.path!!)
        } else null

        val root = fromPrefs ?: File(context.getExternalFilesDir(null), "SPL_Music")
        if (!root.exists()) root.mkdirs()
        return root
    }

    private fun internalSplLyricsDirs(context: Context): Pair<File, File> {
        val backingTracks = File(getInternalSplRoot(context), "BackingTracks")
        if (!backingTracks.exists()) backingTracks.mkdirs()

        val upper = File(backingTracks, "Lyrics")
        val lower = File(backingTracks, "lyrics")
        if (!upper.exists()) upper.mkdirs()
        if (!lower.exists()) lower.mkdirs()
        return upper to lower
    }

    private fun sidecarNameForTrack(trackUriString: String): String {
        val base = baseNameFromUriString(trackUriString)
        return "$base.lrc"
    }

    private fun loadFromInternalSplFolder(context: Context, trackUriString: String): String? {
        val (upperDir, lowerDir) = internalSplLyricsDirs(context)
        val name = sidecarNameForTrack(trackUriString)

        val upper = File(upperDir, name)
        if (upper.exists() && upper.isFile) {
            Log.d(TAG, "mode INTERNAL SPL load path=${upper.absolutePath}")
            return runCatching { upper.readText(Charsets.UTF_8) }.getOrNull()
        }

        val lower = File(lowerDir, name)
        if (lower.exists() && lower.isFile) {
            Log.d(TAG, "mode INTERNAL SPL load path=${lower.absolutePath}")
            return runCatching { lower.readText(Charsets.UTF_8) }.getOrNull()
        }

        return null
    }

    private fun saveToInternalSplFolder(context: Context, trackUriString: String, text: String): Boolean {
        val (upperDir, _) = internalSplLyricsDirs(context)
        val outFile = File(upperDir, sidecarNameForTrack(trackUriString))
        return runCatching {
            outFile.writeText(text, Charsets.UTF_8)
            Log.d(TAG, "mode INTERNAL SPL save path=${outFile.absolutePath}")
            true
        }.getOrDefault(false)
    }

    // ------------------------------------------------------------
    // Cache interne : context.filesDir/lrc_cache
    // ------------------------------------------------------------

    private fun internalFile(context: Context, trackUriString: String): File {
        val dir = File(context.filesDir, CACHE_DIR)
        if (!dir.exists()) dir.mkdirs()
        return File(dir, fileNameForTrack(trackUriString))
    }

    private fun loadFromInternalCache(context: Context, trackUriString: String): String? {
        val f = internalFile(context, trackUriString)
        if (!f.exists() || !f.isFile) return null
        return runCatching { f.readText(Charsets.UTF_8) }.getOrNull()
    }

    private fun saveToInternalCache(context: Context, trackUriString: String, text: String): Boolean {
        return runCatching {
            internalFile(context, trackUriString).writeText(text, Charsets.UTF_8)
            true
        }.getOrDefault(false)
    }

    // ------------------------------------------------------------
    // Nom de fichier (compat : hash sur URI complète)
    // ------------------------------------------------------------

    private fun fileNameForTrack(trackUriString: String): String {
        val base = baseNameFromUriString(trackUriString).take(48)
        val h = md5(trackUriString).take(10)
        return "${base}-${h}.lrc"
    }

    private fun baseNameFromUriString(trackUriString: String): String {
        val uri = runCatching { Uri.parse(trackUriString) }.getOrNull()
        val last = uri?.lastPathSegment ?: trackUriString
        val clean = last.substringAfterLast('/').substringAfterLast(':')
        val base = clean.substringBeforeLast('.', clean).trim()
        return if (base.isBlank()) "track" else base
    }

    private fun md5(s: String): String {
        val md = MessageDigest.getInstance("MD5")
        val bytes = md.digest(s.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun linesToLrcText(lines: List<LrcLine>): String {
        return lines.joinToString("\n") { line ->
            // format simple [mm:ss.xx] texte (si timeMs=0 -> juste texte)
            if (line.timeMs > 0) {
                val total = line.timeMs
                val mm = (total / 60000).toInt()
                val ss = ((total % 60000) / 1000).toInt()
                val xx = ((total % 1000) / 10).toInt()
                "[%02d:%02d.%02d] %s".format(mm, ss, xx, line.text)
            } else {
                line.text
            }
        }.trim()
    }
}
