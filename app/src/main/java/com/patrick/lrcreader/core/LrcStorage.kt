package com.patrick.lrcreader.core

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.security.MessageDigest

object LrcStorage {

    data class TrackLrcOrigin(
        val source: String,
        val fileName: String?,
        val debugPath: String?,
        val sourceType: String? = null
    )

    private data class SafResolvedFile(
        val fileName: String,
        val sourceType: String,
        val file: DocumentFile
    )

    private const val TAG = "LRC_STORAGE"
    private const val CACHE_DIR = "lrc_cache"
    private const val CANONICAL_PREF = "lrc_storage_canonical"

    // ------------------------------------------------------------
    // API
    // ------------------------------------------------------------

    fun logTrackNameDiagnostics(context: Context, trackUriString: String, stage: String) {
        if (trackUriString.isBlank()) return
        val uri = runCatching { Uri.parse(trackUriString) }.getOrNull()
        val audioBaseName = baseNameFromUriString(trackUriString)
        val displayName = resolveTrackDisplayName(context, uri)
        Log.d(
            "LrcDebug",
            "TRACK_NAME_DIAG stage=$stage trackUri=$trackUriString audioBaseName=$audioBaseName displayName=$displayName lastPathSegment=${uri?.lastPathSegment}"
        )
    }

    fun loadForTrack(context: Context, trackUriString: String): String? {
        if (trackUriString.isBlank()) return null
        val safOnlyBackend = isSafBackend(context)
        logLyricsBackend(context, safOnlyBackend)
        logTrackNameDiagnostics(context, trackUriString, stage = "LYRICS_LOAD")

        if (safOnlyBackend) {
            val safDir = getConfiguredSafDir(context)
            if (safDir == null) {
                Log.w(TAG, "mode SAF load blocked: SAF dir unavailable")
                return null
            }
            Log.i(TAG, "mode SAF load dir=${safDir.uri}")
            val resolved = resolveSafExistingFile(context, trackUriString, safDir)
            if (resolved != null) {
                rememberCanonicalFileName(context, trackUriString, resolved.fileName)
                Log.d("LrcDebug", "LYRICS_SOURCE_TYPE ${resolved.sourceType}")
                val text = readSafText(context, resolved.file)
                Log.d(
                    "LrcDebug",
                    "LYRICS_READ_RESULT file=${resolved.fileName} textLen=${text?.length ?: -1} blank=${text.isNullOrBlank()}"
                )
                if (!text.isNullOrBlank()) {
                    Log.d(TAG, "mode SAF load hit CONFIGURED file=${resolved.fileName}")
                    return text
                }
            }
            Log.d(TAG, "mode SAF load miss")
            return null
        }

        // ✅ 1) MODE INTERNE SPL EN PRIORITÉ (BackingTracks/Lyrics)
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

    fun resolveOriginForTrack(context: Context, trackUriString: String): TrackLrcOrigin? {
        if (trackUriString.isBlank()) return null
        val safOnlyBackend = isSafBackend(context)
        logLyricsBackend(context, safOnlyBackend)

        if (!safOnlyBackend) {
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
            val resolved = resolveSafExistingFile(context, trackUriString, safDir)
            if (resolved != null) {
                rememberCanonicalFileName(context, trackUriString, resolved.fileName)
                return TrackLrcOrigin(
                    source = if (resolved.sourceType == "canonical") {
                        "LRC_STORAGE_SAF_CANONICAL"
                    } else {
                        "LRC_STORAGE_SAF_LEGACY"
                    },
                    fileName = resolved.fileName,
                    debugPath = resolved.file.uri.toString(),
                    sourceType = resolved.sourceType
                )
            }

            return null
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
        val safOnlyBackend = isSafBackend(context)
        logLyricsBackend(context, safOnlyBackend)

        if (safOnlyBackend) {
            val safDir = getConfiguredSafDir(context)
            if (safDir == null) {
                Log.w(TAG, "mode SAF save blocked: SAF dir unavailable")
                return
            }
            Log.i(TAG, "mode SAF save dir=${safDir.uri}")
            val targetFileName = resolveSafWriteTargetFileName(context, trackUriString, safDir)
            val savedPath = saveToConfiguredFolder(context, safDir, targetFileName, text)
            val okConfigured = !savedPath.isNullOrBlank()
            if (okConfigured) {
                rememberCanonicalFileName(context, trackUriString, targetFileName)
                Log.i("LrcDebug", "LRC_SAVE path=$savedPath")
            }
            Log.i(TAG, "mode SAF save configured=$okConfigured len=${text.length} file=$targetFileName")
            return
        }

        // ✅ MODE INTERNE SPL : écrit dans BackingTracks/Lyrics/<base>.lrc
        if (!safOnlyBackend) {
            Log.i(TAG, "mode INTERNAL SPL save root=${getInternalSplRoot(context).absolutePath}")
            val okFile = saveToInternalSplFolder(context, trackUriString, text)
            val okInternal = saveToInternalCache(context, trackUriString, text)
            if (okFile) {
                val (upperDir, _) = internalSplLyricsDirs(context)
                val outFile = File(upperDir, sidecarNameForTrack(trackUriString))
                Log.i("LrcDebug", "LRC_SAVE path=${outFile.absolutePath}")
            }
            Log.i(TAG, "mode INTERNAL SPL save file=$okFile internalCache=$okInternal len=${text.length}")
            return
        }
    }

    fun deleteForTrack(context: Context, trackUriString: String) {
        if (trackUriString.isBlank()) return
        val safOnlyBackend = isSafBackend(context)
        logLyricsBackend(context, safOnlyBackend)

        if (safOnlyBackend) {
            runCatching {
                val safDir = getConfiguredSafDir(context)
                if (safDir == null) {
                    Log.w(TAG, "mode SAF delete blocked: SAF dir unavailable")
                    return@runCatching
                }
                val targetNames = linkedSetOf<String>().apply {
                    getRememberedCanonicalFileName(context, trackUriString)
                        ?.takeIf { it.isNotBlank() }
                        ?.let { add(it) }
                    add(fileNameForTrack(trackUriString))
                    add(sidecarNameForTrack(trackUriString))
                }
                targetNames.forEach { name ->
                    findFileIgnoreCase(safDir, name)?.let { doc ->
                        val deleted = runCatching { doc.delete() }.getOrDefault(false)
                        if (deleted) {
                            Log.i("LrcDebug", "LRC_DELETE path=${doc.uri}")
                        }
                    }
                }
                clearRememberedCanonicalFileName(context, trackUriString)
            }
            return
        }

        // cache interne
        runCatching {
            val cache = internalFile(context, trackUriString)
            if (cache.delete()) {
                Log.i("LrcDebug", "LRC_DELETE path=${cache.absolutePath}")
            }
        }

        // INTERNAL SPL sidecar
        runCatching {
            val (upperDir, lowerDir) = internalSplLyricsDirs(context)
            val sidecar = sidecarNameForTrack(trackUriString)
            val upper = File(upperDir, sidecar)
            val lower = File(lowerDir, sidecar)
            if (upper.delete()) {
                Log.i("LrcDebug", "LRC_DELETE path=${upper.absolutePath}")
            }
            if (lower.delete()) {
                Log.i("LrcDebug", "LRC_DELETE path=${lower.absolutePath}")
            }
        }
    }

    fun hashedFileNameForTrack(trackUriString: String): String {
        return fileNameForTrack(trackUriString)
    }

    // ------------------------------------------------------------
    // SAF (dossier configuré)
    // ------------------------------------------------------------

    private fun getConfiguredSafDir(context: Context): DocumentFile? {
        val explicitPrefUri = LyricsFolderPrefs.get(context)
        Log.d("LrcDebug", "LYRICS_SAF_DIR explicitPrefUri=$explicitPrefUri")
        val explicitDir = explicitPrefUri
            ?.takeIf { it.scheme == "content" }
            ?.let { folderUri ->
                DocumentFile.fromTreeUri(context, folderUri)
                    ?: DocumentFile.fromSingleUri(context, folderUri)
            }
            ?.takeIf { it.isDirectory && it.canRead() }
        Log.d(
            "LrcDebug",
            "LYRICS_SAF_DIR explicitResolved=${explicitDir?.uri} explicitChildren=${explicitDir?.let { listChildNames(it) }}"
        )
        if (explicitDir != null) {
            return explicitDir
        }

        val fallbackDir = resolveSafLyricsDirFromLibraryRoot(context)
        if (fallbackDir != null) {
            Log.i(TAG, "mode SAF fallback lyrics dir=${fallbackDir.uri}")
            LyricsFolderPrefs.save(context, fallbackDir.uri)
        }
        return fallbackDir
    }

    private fun resolveSafLyricsDirFromLibraryRoot(context: Context): DocumentFile? {
        val rootUri = BackupFolderPrefs.getLibraryRootUri(context) ?: return null
        Log.d("LrcDebug", "LYRICS_SAF_DIR fallbackRoot=$rootUri")
        if (rootUri.scheme != "content") return null
        val rootDoc = DocumentFile.fromTreeUri(context, rootUri)
            ?: DocumentFile.fromSingleUri(context, rootUri)
            ?: return null
        if (!rootDoc.isDirectory || !rootDoc.canRead()) return null

        val backingTracks = findDirByAliases(rootDoc, listOf("BackingTracks", "BackingTrack"))
        Log.d(
            "LrcDebug",
            "LYRICS_SAF_DIR backingTracks=${backingTracks?.uri} rootChildren=${listChildNames(rootDoc)}"
        )
        val safeBackingTracks = backingTracks ?: return null
        val lyricsDir = findDirByAliases(safeBackingTracks, listOf("Lyrics", "lyrics"))
        Log.d(
            "LrcDebug",
            "LYRICS_SAF_DIR resolvedLyricsDir=${lyricsDir?.uri} backingChildren=${listChildNames(safeBackingTracks)}"
        )
        val safeLyricsDir = lyricsDir ?: return null
        Log.d(
            "LrcDebug",
            "LYRICS_SAF_DIR children=${listChildNames(safeLyricsDir)}"
        )
        return safeLyricsDir.takeIf { it.isDirectory && it.canRead() }
    }

    private fun findDirByAliases(parent: DocumentFile, aliases: List<String>): DocumentFile? {
        val normalizedAliases = aliases.map { normalizeDirName(it) }.toSet()
        return parent.listFiles().firstOrNull { child ->
            child.isDirectory && normalizedAliases.contains(normalizeDirName(child.name.orEmpty()))
        }
    }

    private fun normalizeDirName(name: String): String {
        return name.trim().lowercase()
    }

    private fun resolveSafExistingFile(
        context: Context,
        trackUriString: String,
        dir: DocumentFile
    ): SafResolvedFile? {
        val base = baseNameFromUriString(trackUriString)
        val remembered = getRememberedCanonicalFileName(context, trackUriString)
        val canonicalHashed = fileNameForTrack(trackUriString)
        val sidecar = sidecarNameForTrack(trackUriString)
        val files = listSafFiles(dir)
        val children = files.map { "${it.name.orEmpty()}<file>" }
        Log.d(
            "LrcDebug",
            "LYRICS_LOOKUP_START trackUri=$trackUriString base=$base dir=${dir.uri}"
        )
        Log.d(
            "LrcDebug",
            "LYRICS_LOOKUP_TARGETS remembered=$remembered canonicalHashed=$canonicalHashed sidecar=$sidecar"
        )
        Log.d(
            "LrcDebug",
            "LYRICS_LOOKUP_CHILDREN dir=${dir.uri} childNames=$children"
        )
        if (!remembered.isNullOrBlank()) {
            val rememberedFile = findFileIgnoreCaseOrLrcTxt(files, remembered)
            Log.d(
                "LrcDebug",
                "LYRICS_LOOKUP_REMEMBERED hit=${rememberedFile != null} target=$remembered"
            )
            if (rememberedFile != null && rememberedFile.isFile) {
                Log.d(
                    "LrcDebug",
                    "LYRICS_LOOKUP_RESULT source=remembered file=${rememberedFile.name ?: remembered}"
                )
                return SafResolvedFile(
                    fileName = rememberedFile.name ?: remembered,
                    sourceType = "canonical",
                    file = rememberedFile
                )
            }
        } else {
            Log.d("LrcDebug", "LYRICS_LOOKUP_REMEMBERED hit=false target=null")
        }

        val hashedFile = findFileIgnoreCaseOrLrcTxt(files, canonicalHashed)
        Log.d(
            "LrcDebug",
            "LYRICS_LOOKUP_CANONICAL hit=${hashedFile != null} target=$canonicalHashed"
        )
        if (hashedFile != null && hashedFile.isFile) {
            Log.d(
                "LrcDebug",
                "LYRICS_LOOKUP_RESULT source=canonical file=${hashedFile.name ?: canonicalHashed}"
            )
            return SafResolvedFile(
                fileName = hashedFile.name ?: canonicalHashed,
                sourceType = "canonical",
                file = hashedFile
            )
        }

        val sidecarFile = findFileIgnoreCaseOrLrcTxt(files, sidecar)
        Log.d(
            "LrcDebug",
            "LYRICS_LOOKUP_SIDECAR hit=${sidecarFile != null} target=$sidecar"
        )
        if (sidecarFile != null && sidecarFile.isFile) {
            Log.d(
                "LrcDebug",
                "LYRICS_LOOKUP_RESULT source=sidecar file=${sidecarFile.name ?: sidecar}"
            )
            return SafResolvedFile(
                fileName = sidecarFile.name ?: sidecar,
                sourceType = "legacy",
                file = sidecarFile
            )
        }

        val legacyMatches = files.filter { child ->
                val name = child.name.orEmpty()
                name.startsWith("$base-", ignoreCase = true) &&
                    (
                        name.endsWith(".lrc", ignoreCase = true) ||
                            name.endsWith(".lrc.txt", ignoreCase = true)
                        )
            }
        Log.d(
            "LrcDebug",
            "LYRICS_LOOKUP_LEGACY prefix=$base- matches=${legacyMatches.map { it.name.orEmpty() }}"
        )
        val prefixedLegacy = legacyMatches.firstOrNull()
        if (prefixedLegacy != null) {
            Log.d(
                "LrcDebug",
                "LYRICS_LOOKUP_RESULT source=legacy file=${prefixedLegacy.name ?: canonicalHashed}"
            )
            return SafResolvedFile(
                fileName = prefixedLegacy.name ?: canonicalHashed,
                sourceType = "legacy",
                file = prefixedLegacy
            )
        }

        Log.d("LrcDebug", "LYRICS_LOOKUP_RESULT source=none file=null")
        return null
    }

    private fun resolveSafWriteTargetFileName(
        context: Context,
        trackUriString: String,
        dir: DocumentFile
    ): String {
        val existing = resolveSafExistingFile(context, trackUriString, dir)
        if (existing != null) return existing.fileName
        val remembered = getRememberedCanonicalFileName(context, trackUriString)
        if (!remembered.isNullOrBlank()) return remembered
        return fileNameForTrack(trackUriString)
    }

    private fun saveToConfiguredFolder(
        context: Context,
        dir: DocumentFile,
        targetFileName: String,
        text: String
    ): String? {
        if (targetFileName.isBlank()) return null
        return runCatching {
            val existing = findFileIgnoreCase(dir, targetFileName)
            val target = existing ?: dir.createFile("application/octet-stream", targetFileName) ?: return null
            Log.d(TAG, "mode SAF save uri=${target.uri}")

            context.contentResolver.openOutputStream(target.uri, "w")?.use { out ->
                out.write(text.toByteArray(Charsets.UTF_8))
                out.flush()
            } ?: return null

            target.uri.toString()
        }.getOrNull()
    }

    private fun readSafText(context: Context, file: DocumentFile): String? {
        return runCatching {
            context.contentResolver.openInputStream(file.uri)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
        }.getOrNull()
    }

    private fun listChildNames(dir: DocumentFile): List<String> {
        return runCatching {
            dir.listFiles().map { child ->
                val kind = if (child.isDirectory) "dir" else "file"
                "${child.name.orEmpty()}<$kind>"
            }
        }.getOrDefault(emptyList())
    }

    private fun listSafFiles(dir: DocumentFile): List<DocumentFile> {
        return runCatching {
            dir.listFiles().filter { it.isFile }
        }.getOrDefault(emptyList())
    }

    private fun findFileIgnoreCase(dir: DocumentFile, targetFileName: String): DocumentFile? {
        return dir.listFiles().firstOrNull { child ->
            child.isFile && child.name.orEmpty().equals(targetFileName, ignoreCase = true)
        }
    }

    private fun findFileIgnoreCaseOrLrcTxt(dir: DocumentFile, targetFileName: String): DocumentFile? {
        findFileIgnoreCase(dir, targetFileName)?.let { return it }
        val txtVariant = lrcTxtVariant(targetFileName) ?: return null
        return findFileIgnoreCase(dir, txtVariant)
    }

    private fun findFileIgnoreCase(entries: List<DocumentFile>, targetFileName: String): DocumentFile? {
        return entries.firstOrNull { child ->
            child.name.orEmpty().equals(targetFileName, ignoreCase = true)
        }
    }

    private fun findFileIgnoreCaseOrLrcTxt(entries: List<DocumentFile>, targetFileName: String): DocumentFile? {
        findFileIgnoreCase(entries, targetFileName)?.let { return it }
        val txtVariant = lrcTxtVariant(targetFileName) ?: return null
        return findFileIgnoreCase(entries, txtVariant)
    }

    private fun lrcTxtVariant(targetFileName: String): String? {
        val trimmed = targetFileName.trim()
        return if (trimmed.endsWith(".lrc", ignoreCase = true)) {
            "$trimmed.txt"
        } else {
            null
        }
    }

    private fun resolveTrackDisplayName(context: Context, uri: Uri?): String? {
        if (uri == null) return null
        if (uri.scheme == "file") {
            return uri.path?.let { File(it).name }?.takeIf { it.isNotBlank() }
        }

        if (uri.scheme == "content") {
            runCatching {
                context.contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) {
                            cursor.getString(idx)
                        } else {
                            null
                        }
                    } else {
                        null
                    }
                }
            }.getOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
        }

        return DocumentFile.fromSingleUri(context, uri)?.name
            ?.takeIf { it.isNotBlank() }
            ?: DocumentFile.fromTreeUri(context, uri)?.name?.takeIf { it.isNotBlank() }
    }

    private fun rememberCanonicalFileName(context: Context, trackUriString: String, fileName: String) {
        if (trackUriString.isBlank() || fileName.isBlank()) return
        context.getSharedPreferences(CANONICAL_PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(md5(trackUriString), fileName)
            .apply()
    }

    private fun getRememberedCanonicalFileName(context: Context, trackUriString: String): String? {
        if (trackUriString.isBlank()) return null
        return context.getSharedPreferences(CANONICAL_PREF, Context.MODE_PRIVATE)
            .getString(md5(trackUriString), null)
    }

    private fun clearRememberedCanonicalFileName(context: Context, trackUriString: String) {
        if (trackUriString.isBlank()) return
        context.getSharedPreferences(CANONICAL_PREF, Context.MODE_PRIVATE)
            .edit()
            .remove(md5(trackUriString))
            .apply()
    }

    // ------------------------------------------------------------
    // INTERNAL SPL
    // ------------------------------------------------------------

    private fun isSafBackend(context: Context): Boolean {
        return BackupFolderPrefs.getLibraryRootUri(context)?.scheme == "content"
    }

    private fun logLyricsBackend(context: Context, safOnlyBackend: Boolean) {
        val rootUri = BackupFolderPrefs.getLibraryRootUri(context)
        if (safOnlyBackend) {
            Log.d("LrcDebug", "LYRICS_BACKEND SAF rootUri=$rootUri")
        } else {
            Log.d("LrcDebug", "LYRICS_BACKEND APP_PRIVATE rootUri=$rootUri")
        }
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
