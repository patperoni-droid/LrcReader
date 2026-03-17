package com.patrick.lrcreader.core

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.security.MessageDigest
import java.util.LinkedHashMap

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
    private const val RECENT_ORIGIN_CACHE_MAX = 32

    private val recentResolvedOrigins = object : LinkedHashMap<String, TrackLrcOrigin>(
        RECENT_ORIGIN_CACHE_MAX,
        0.75f,
        true
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TrackLrcOrigin>?): Boolean {
            return size > RECENT_ORIGIN_CACHE_MAX
        }
    }

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
        LyricsPerf.mark(trackUriString, "lrc_storage_load_start", "backend=${if (safOnlyBackend) "SAF" else "INTERNAL"}")
        logLyricsBackend(context, safOnlyBackend)
        logTrackNameDiagnostics(context, trackUriString, stage = "LYRICS_LOAD")

        if (safOnlyBackend) {
            val safDirStartMs = SystemClock.elapsedRealtime()
            val safDir = getConfiguredSafDir(context, trackUriString)
            LyricsPerf.mark(
                trackUriString,
                "saf_lyrics_dir_ready",
                "ms=${SystemClock.elapsedRealtime() - safDirStartMs} dir=${safDir?.uri}"
            )
            if (safDir == null) {
                clearRecentResolvedOrigin(trackUriString)
                Log.w(TAG, "mode SAF load blocked: SAF dir unavailable")
                return null
            }
            Log.i(TAG, "mode SAF load dir=${safDir.uri}")
            val lookupStartMs = SystemClock.elapsedRealtime()
            val resolved = resolveSafExistingFile(context, trackUriString, safDir)
            LyricsPerf.mark(
                trackUriString,
                "saf_lyrics_lookup_done",
                "ms=${SystemClock.elapsedRealtime() - lookupStartMs} hit=${resolved != null} file=${resolved?.fileName}"
            )
            if (resolved != null) {
                rememberCanonicalFileName(context, trackUriString, resolved.fileName)
                Log.d("LrcDebug", "LYRICS_SOURCE_TYPE ${resolved.sourceType}")
                val text = readSafText(context, resolved.file, trackUriString)
                Log.d(
                    "LrcDebug",
                    "LYRICS_READ_RESULT file=${resolved.fileName} textLen=${text?.length ?: -1} blank=${text.isNullOrBlank()}"
                )
                if (!text.isNullOrBlank()) {
                    cacheRecentResolvedOrigin(
                        trackUriString,
                        TrackLrcOrigin(
                            source = if (resolved.sourceType == "canonical") {
                                "LRC_STORAGE_SAF_CANONICAL"
                            } else {
                                "LRC_STORAGE_SAF_LEGACY"
                            },
                            fileName = resolved.fileName,
                            debugPath = resolved.file.uri.toString(),
                            sourceType = resolved.sourceType
                        )
                    )
                    Log.d(TAG, "mode SAF load hit CONFIGURED file=${resolved.fileName}")
                    return text
                }
            }
            clearRecentResolvedOrigin(trackUriString)
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
        clearRecentResolvedOrigin(trackUriString)
        Log.d(TAG, "mode INTERNAL SPL load miss")
        return null
    }

    fun resolveOriginForTrack(context: Context, trackUriString: String): TrackLrcOrigin? {
        if (trackUriString.isBlank()) return null
        val safOnlyBackend = isSafBackend(context)
        val originStartMs = SystemClock.elapsedRealtime()
        LyricsPerf.mark(trackUriString, "lrc_origin_resolve_start", "backend=${if (safOnlyBackend) "SAF" else "INTERNAL"}")
        logLyricsBackend(context, safOnlyBackend)

        getRecentResolvedOrigin(trackUriString)?.let { cached ->
            LyricsPerf.mark(
                trackUriString,
                "lrc_origin_resolve_done",
                "ms=${SystemClock.elapsedRealtime() - originStartMs} sourceType=${cached.sourceType} file=${cached.fileName} cache=true"
            )
            return cached
        }

        if (!safOnlyBackend) {
            val (upperDir, lowerDir) = internalSplLyricsDirs(context)
            val sidecar = sidecarNameForTrack(trackUriString)

            val upper = File(upperDir, sidecar)
            if (upper.exists() && upper.isFile) {
                return TrackLrcOrigin(
                    source = "LRC_STORAGE_INTERNAL_SPL_UPPER",
                    fileName = upper.name,
                    debugPath = upper.absolutePath
                ).also { cacheRecentResolvedOrigin(trackUriString, it) }
            }

            val lower = File(lowerDir, sidecar)
            if (lower.exists() && lower.isFile) {
                return TrackLrcOrigin(
                    source = "LRC_STORAGE_INTERNAL_SPL_LOWER",
                    fileName = lower.name,
                    debugPath = lower.absolutePath
                ).also { cacheRecentResolvedOrigin(trackUriString, it) }
            }

            val cache = internalFile(context, trackUriString)
            if (cache.exists() && cache.isFile) {
                return TrackLrcOrigin(
                    source = "LRC_STORAGE_INTERNAL_CACHE",
                    fileName = cache.name,
                    debugPath = cache.absolutePath
                ).also { cacheRecentResolvedOrigin(trackUriString, it) }
            }

            return null
        }

        val safDir = getConfiguredSafDir(context, trackUriString)
        if (safDir != null) {
            val resolved = resolveSafExistingFile(context, trackUriString, safDir)
            if (resolved != null) {
                rememberCanonicalFileName(context, trackUriString, resolved.fileName)
                LyricsPerf.mark(
                    trackUriString,
                    "lrc_origin_resolve_done",
                    "ms=${SystemClock.elapsedRealtime() - originStartMs} sourceType=${resolved.sourceType} file=${resolved.fileName}"
                )
                return TrackLrcOrigin(
                    source = if (resolved.sourceType == "canonical") {
                        "LRC_STORAGE_SAF_CANONICAL"
                    } else {
                        "LRC_STORAGE_SAF_LEGACY"
                    },
                    fileName = resolved.fileName,
                    debugPath = resolved.file.uri.toString(),
                    sourceType = resolved.sourceType
                ).also { cacheRecentResolvedOrigin(trackUriString, it) }
            }

            LyricsPerf.mark(
                trackUriString,
                "lrc_origin_resolve_done",
                "ms=${SystemClock.elapsedRealtime() - originStartMs} source=null dir=${safDir.uri}"
            )
            return null
        }
        LyricsPerf.mark(
            trackUriString,
            "lrc_origin_resolve_done",
            "ms=${SystemClock.elapsedRealtime() - originStartMs} source=null"
        )
        return null
    }

    fun saveForTrack(context: Context, trackUriString: String, lines: List<LrcLine>) {
        Log.e(
            "DEBUG_ROOT_URI",
            "BackupFolderPrefs rootUri = ${BackupFolderPrefs.getLibraryRootUri(context)}"
        )
        if (trackUriString.isBlank()) return
        clearRecentResolvedOrigin(trackUriString)
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
        clearRecentResolvedOrigin(trackUriString)
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

    private fun getConfiguredSafDir(context: Context, trackUriString: String? = null): DocumentFile? {
        val configuredStartMs = SystemClock.elapsedRealtime()
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
            "LYRICS_SAF_DIR explicitResolved=${explicitDir?.uri} explicitChildren=${explicitDir?.let { listChildNames(it, trackUriString, label = "explicit_dir_children") }}"
        )
        if (explicitDir != null) {
            LyricsPerf.mark(
                trackUriString,
                "saf_configured_dir_done",
                "ms=${SystemClock.elapsedRealtime() - configuredStartMs} mode=explicit dir=${explicitDir.uri}"
            )
            return explicitDir
        }

        val fallbackDir = resolveSafLyricsDirFromLibraryRoot(context, trackUriString)
        if (fallbackDir != null) {
            Log.i(TAG, "mode SAF fallback lyrics dir=${fallbackDir.uri}")
            LyricsFolderPrefs.save(context, fallbackDir.uri)
        }
        LyricsPerf.mark(
            trackUriString,
            "saf_configured_dir_done",
            "ms=${SystemClock.elapsedRealtime() - configuredStartMs} mode=fallback dir=${fallbackDir?.uri}"
        )
        return fallbackDir
    }

    private fun resolveSafLyricsDirFromLibraryRoot(context: Context, trackUriString: String? = null): DocumentFile? {
        val resolveStartMs = SystemClock.elapsedRealtime()
        val requestedRootUri = BackupFolderPrefsSaf.getLibraryRootUri(context)
            ?: BackupFolderPrefs.getLibraryRootUri(context)
            ?: return null
        Log.d("LrcDebug", "LYRICS_SAF_DIR fallbackRoot=$requestedRootUri")
        if (requestedRootUri.scheme != "content") return null
        val rootDoc = resolveActualSafSplRoot(context, requestedRootUri, trackUriString) ?: return null
        Log.d(
            "LrcDebug",
            "LYRICS_SAF_DIR fallbackResolvedRoot=${rootDoc.uri} rootChildren=${listChildNames(rootDoc, trackUriString, label = "fallback_root_children")}"
        )
        if (!rootDoc.isDirectory || !rootDoc.canRead()) return null

        val backingTracks = findDirByAliases(
            parent = rootDoc,
            aliases = listOf("BackingTracks", "BackingTrack"),
            trackUriString = trackUriString,
            stage = "resolve_backing_tracks"
        )
        Log.d(
            "LrcDebug",
            "LYRICS_SAF_DIR backingTracks=${backingTracks?.uri} rootChildren=${listChildNames(rootDoc, trackUriString, label = "fallback_root_children_repeat")}"
        )
        val safeBackingTracks = backingTracks ?: return null
        val lyricsDir = findDirByAliases(
            parent = safeBackingTracks,
            aliases = listOf("Lyrics", "lyrics"),
            trackUriString = trackUriString,
            stage = "resolve_lyrics_dir"
        )
        Log.d(
            "LrcDebug",
            "LYRICS_SAF_DIR resolvedLyricsDir=${lyricsDir?.uri} backingChildren=${listChildNames(safeBackingTracks, trackUriString, label = "backing_tracks_children")}"
        )
        val safeLyricsDir = lyricsDir ?: return null
        Log.d(
            "LrcDebug",
            "LYRICS_SAF_DIR children=${listChildNames(safeLyricsDir, trackUriString, label = "lyrics_dir_children")}"
        )
        LyricsPerf.mark(
            trackUriString,
            "saf_fallback_dir_done",
            "ms=${SystemClock.elapsedRealtime() - resolveStartMs} root=${rootDoc.uri} lyricsDir=${safeLyricsDir.uri}"
        )
        return safeLyricsDir.takeIf { it.isDirectory && it.canRead() }
    }

    private fun resolveActualSafSplRoot(context: Context, requestedRootUri: Uri, trackUriString: String? = null): DocumentFile? {
        val rootResolveStartMs = SystemClock.elapsedRealtime()
        val requestedRootDoc = resolveRootDocument(context, requestedRootUri, trackUriString)
            ?.takeIf { it.isDirectory && it.canRead() }
        if (requestedRootDoc != null && matchesDirAlias(requestedRootDoc, listOf("SPL_Music", "spl_music"))) {
            LyricsPerf.mark(
                trackUriString,
                "saf_root_resolve_done",
                "ms=${SystemClock.elapsedRealtime() - rootResolveStartMs} source=requested root=${requestedRootDoc.uri}"
            )
            return requestedRootDoc
        }

        val setupTreeUri = BackupFolderPrefsSaf.getSetupTreeUri(context)
            ?: BackupFolderPrefs.getSetupTreeUri(context)
        Log.d("LrcDebug", "LYRICS_SAF_DIR setupTreeUri=$setupTreeUri")
        val setupRootDoc = setupTreeUri
            ?.takeIf { it.scheme == "content" }
            ?.let { resolveRootDocument(context, it, trackUriString) }
            ?.takeIf { it.isDirectory && it.canRead() }
        Log.d(
            "LrcDebug",
            "LYRICS_SAF_DIR setupResolved=${setupRootDoc?.uri} setupChildren=${setupRootDoc?.let { listChildNames(it, trackUriString, label = "setup_root_children") }}"
        )
        if (setupRootDoc != null) {
            if (matchesDirAlias(setupRootDoc, listOf("SPL_Music", "spl_music"))) {
                LyricsPerf.mark(
                    trackUriString,
                    "saf_root_resolve_done",
                    "ms=${SystemClock.elapsedRealtime() - rootResolveStartMs} source=setup_root root=${setupRootDoc.uri}"
                )
                return setupRootDoc
            }
            val splRoot = findDirByAliases(
                parent = setupRootDoc,
                aliases = listOf("SPL_Music", "spl_music"),
                trackUriString = trackUriString,
                stage = "resolve_setup_spl_root"
            )
            Log.d("LrcDebug", "LYRICS_SAF_DIR setupSplRoot=${splRoot?.uri}")
            if (splRoot != null && splRoot.isDirectory && splRoot.canRead()) {
                LyricsPerf.mark(
                    trackUriString,
                    "saf_root_resolve_done",
                    "ms=${SystemClock.elapsedRealtime() - rootResolveStartMs} source=setup_child root=${splRoot.uri}"
                )
                return splRoot
            }
        }

        LyricsPerf.mark(
            trackUriString,
            "saf_root_resolve_done",
            "ms=${SystemClock.elapsedRealtime() - rootResolveStartMs} source=requested_fallback root=${requestedRootDoc?.uri}"
        )
        return requestedRootDoc
    }

    private fun findDirByAliases(
        parent: DocumentFile,
        aliases: List<String>,
        trackUriString: String? = null,
        stage: String = "find_dir_by_aliases"
    ): DocumentFile? {
        val listStartMs = SystemClock.elapsedRealtime()
        val normalizedAliases = aliases.map { normalizeDirName(it) }.toSet()
        val children = runCatching { parent.listFiles().toList() }.getOrDefault(emptyList())
        val result = children.firstOrNull { child ->
            child.isDirectory && normalizedAliases.contains(normalizeDirName(child.name.orEmpty()))
        }
        LyricsPerf.mark(
            trackUriString,
            "saf_list_dirs_done",
            "stage=$stage ms=${SystemClock.elapsedRealtime() - listStartMs} dir=${parent.uri} childCount=${children.size} result=${result?.uri}"
        )
        return result
    }

    private fun matchesDirAlias(dir: DocumentFile, aliases: List<String>): Boolean {
        val normalizedAliases = aliases.map { normalizeDirName(it) }.toSet()
        return normalizedAliases.contains(normalizeDirName(dir.name.orEmpty()))
    }

    private fun normalizeDirName(name: String): String {
        return name.trim().lowercase()
    }

    private fun resolveRootDocument(context: Context, rootUri: Uri, trackUriString: String? = null): DocumentFile? {
        val resolveStartMs = SystemClock.elapsedRealtime()
        val directTree = DocumentFile.fromTreeUri(context, rootUri)
        if (directTree?.isDirectory == true) {
            LyricsPerf.mark(
                trackUriString,
                "saf_root_document_done",
                "ms=${SystemClock.elapsedRealtime() - resolveStartMs} requestUri=$rootUri resolved=${directTree.uri} mode=direct_tree"
            )
            return directTree
        }

        val normalizedTreeUri = normalizeAsTreeUri(rootUri)
        val normalizedTree = normalizedTreeUri?.let { DocumentFile.fromTreeUri(context, it) }
        if (normalizedTree?.isDirectory == true) {
            LyricsPerf.mark(
                trackUriString,
                "saf_root_document_done",
                "ms=${SystemClock.elapsedRealtime() - resolveStartMs} requestUri=$rootUri resolved=${normalizedTree.uri} mode=normalized_tree"
            )
            return normalizedTree
        }

        val single = DocumentFile.fromSingleUri(context, rootUri)
        if (single?.isDirectory == true) {
            LyricsPerf.mark(
                trackUriString,
                "saf_root_document_done",
                "ms=${SystemClock.elapsedRealtime() - resolveStartMs} requestUri=$rootUri resolved=${single.uri} mode=single"
            )
            return single
        }

        Log.w(
            TAG,
            "lyricsRoot:resolve_failed requestUri=$rootUri directTree=${directTree?.uri} normalizedTree=$normalizedTreeUri single=${single?.uri}"
        )
        val fallback = directTree ?: normalizedTree ?: single
        LyricsPerf.mark(
            trackUriString,
            "saf_root_document_done",
            "ms=${SystemClock.elapsedRealtime() - resolveStartMs} requestUri=$rootUri resolved=${fallback?.uri} mode=fallback"
        )
        return fallback
    }

    private fun normalizeAsTreeUri(uri: Uri): Uri? {
        val authority = uri.authority ?: return null
        val treeId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
            ?: runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
            ?: return null
        return DocumentsContract.buildTreeDocumentUri(authority, treeId)
    }

    private fun resolveSafExistingFile(
        context: Context,
        trackUriString: String,
        dir: DocumentFile
    ): SafResolvedFile? {
        val lookupStartMs = SystemClock.elapsedRealtime()
        val base = baseNameFromUriString(trackUriString)
        val remembered = getRememberedCanonicalFileName(context, trackUriString)
        val canonicalHashed = fileNameForTrack(trackUriString)
        val sidecar = sidecarNameForTrack(trackUriString)
        val files = listSafFiles(dir, trackUriString, label = "lyrics_lookup_files")
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
                LyricsPerf.mark(
                    trackUriString,
                    "saf_lookup_result",
                    "ms=${SystemClock.elapsedRealtime() - lookupStartMs} source=remembered file=${rememberedFile.name ?: remembered}"
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
            LyricsPerf.mark(
                trackUriString,
                "saf_lookup_result",
                "ms=${SystemClock.elapsedRealtime() - lookupStartMs} source=canonical file=${hashedFile.name ?: canonicalHashed}"
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
            LyricsPerf.mark(
                trackUriString,
                "saf_lookup_result",
                "ms=${SystemClock.elapsedRealtime() - lookupStartMs} source=sidecar file=${sidecarFile.name ?: sidecar}"
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
            LyricsPerf.mark(
                trackUriString,
                "saf_lookup_result",
                "ms=${SystemClock.elapsedRealtime() - lookupStartMs} source=legacy file=${prefixedLegacy.name ?: canonicalHashed}"
            )
            return SafResolvedFile(
                fileName = prefixedLegacy.name ?: canonicalHashed,
                sourceType = "legacy",
                file = prefixedLegacy
            )
        }

        Log.d("LrcDebug", "LYRICS_LOOKUP_RESULT source=none file=null")
        LyricsPerf.mark(
            trackUriString,
            "saf_lookup_result",
            "ms=${SystemClock.elapsedRealtime() - lookupStartMs} source=none file=null"
        )
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

    private fun readSafText(context: Context, file: DocumentFile, trackUriString: String? = null): String? {
        val readStartMs = SystemClock.elapsedRealtime()
        val text = runCatching {
            context.contentResolver.openInputStream(file.uri)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
        }.getOrNull()
        LyricsPerf.mark(
            trackUriString,
            "saf_read_text_done",
            "ms=${SystemClock.elapsedRealtime() - readStartMs} file=${file.uri} len=${text?.length ?: -1} blank=${text.isNullOrBlank()}"
        )
        return text
    }

    private fun listChildNames(dir: DocumentFile, trackUriString: String? = null, label: String = "child_names"): List<String> {
        val listStartMs = SystemClock.elapsedRealtime()
        val children = runCatching {
            dir.listFiles().map { child ->
                val kind = if (child.isDirectory) "dir" else "file"
                "${child.name.orEmpty()}<$kind>"
            }
        }.getOrDefault(emptyList())
        LyricsPerf.mark(
            trackUriString,
            "saf_list_children_done",
            "label=$label ms=${SystemClock.elapsedRealtime() - listStartMs} dir=${dir.uri} count=${children.size}"
        )
        return children
    }

    private fun listSafFiles(dir: DocumentFile, trackUriString: String? = null, label: String = "list_saf_files"): List<DocumentFile> {
        val listStartMs = SystemClock.elapsedRealtime()
        val files = runCatching {
            dir.listFiles().filter { it.isFile }
        }.getOrDefault(emptyList())
        LyricsPerf.mark(
            trackUriString,
            "saf_list_files_done",
            "label=$label ms=${SystemClock.elapsedRealtime() - listStartMs} dir=${dir.uri} count=${files.size}"
        )
        return files
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

    @Synchronized
    private fun cacheRecentResolvedOrigin(trackUriString: String, origin: TrackLrcOrigin) {
        if (trackUriString.isBlank()) return
        recentResolvedOrigins[trackUriString] = origin
    }

    @Synchronized
    private fun getRecentResolvedOrigin(trackUriString: String): TrackLrcOrigin? {
        if (trackUriString.isBlank()) return null
        return recentResolvedOrigins[trackUriString]
    }

    @Synchronized
    private fun clearRecentResolvedOrigin(trackUriString: String) {
        if (trackUriString.isBlank()) return
        recentResolvedOrigins.remove(trackUriString)
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
