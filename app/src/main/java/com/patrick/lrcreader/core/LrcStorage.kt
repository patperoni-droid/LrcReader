package com.patrick.lrcreader.core

import android.content.Context
import android.net.Uri
import android.system.Os
import android.os.SystemClock
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.patrick.lrcreader.smp.SmpConfig
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.LinkedHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

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

    private data class SmpResolvedLyrics(
        val file: File,
        val fileName: String
    )

    private data class WorkspaceFolderSpec(
        val preferredName: String,
        val aliasName: String,
        val stageKey: String
    )

    private const val TAG = "LRC_STORAGE"
    private const val WORKSPACE_LOG_TAG = "LRC_WORKSPACE"
    private const val LYRICS_AUTOSAVE_CRASH_DIAG_TAG = "LYRICS_AUTOSAVE_CRASH_DIAG"
    private const val CANONICAL_PREF = "lrc_storage_canonical"
    private const val SMP_ALIAS_PREF = "lrc_storage_smp_alias"
    private const val RECENT_ORIGIN_CACHE_MAX = 32

    private val lyricsFolderSpec = WorkspaceFolderSpec(
        preferredName = "Lyrics",
        aliasName = "lyrics",
        stageKey = "lyrics"
    )
    private val accordsFolderSpec = WorkspaceFolderSpec(
        preferredName = "Accords",
        aliasName = "accords",
        stageKey = "accords"
    )

    private val recentResolvedOrigins = object : LinkedHashMap<String, TrackLrcOrigin>(
        RECENT_ORIGIN_CACHE_MAX,
        0.75f,
        true
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TrackLrcOrigin>?): Boolean {
            return size > RECENT_ORIGIN_CACHE_MAX
        }
    }

    private val lyricsSaveLock = ReentrantLock()

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
        val effectiveTrackUriString = resolveRuntimeAlias(context, trackUriString)
        val safOnlyBackend = isSafBackend(context)
        resolveSmpLyricsTarget(context, effectiveTrackUriString, requireExisting = false)?.let { resolved ->
            val text = if (resolved.file.isFile) {
                runCatching { resolved.file.readText(Charsets.UTF_8) }.getOrNull()
            } else {
                null
            }
            if (!text.isNullOrBlank()) {
                cacheRecentResolvedOrigin(
                    trackUriString,
                    TrackLrcOrigin(
                        source = "LRC_STORAGE_SMP",
                        fileName = resolved.fileName,
                        debugPath = resolved.file.absolutePath,
                        sourceType = "smp"
                    )
                )
                Log.d(TAG, "mode SMP load path=${resolved.file.absolutePath}")
                return text
            }
            clearRecentResolvedOrigin(trackUriString)
            Log.d(TAG, "mode SMP load miss path=${resolved.file.absolutePath}")
            return null
        }
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
        val internalRoot = getInternalSplRoot(context)
        if (internalRoot == null) {
            clearRecentResolvedOrigin(trackUriString)
            Log.w(TAG, "mode INTERNAL SPL load blocked: workspace root unavailable")
            return null
        }
        Log.i(TAG, "mode INTERNAL SPL load root=${internalRoot.absolutePath}")
        loadFromInternalSplFolder(context, trackUriString)?.let { txt ->
            if (txt.isNotBlank()) {
                Log.d(TAG, "mode INTERNAL SPL load hit FILE")
                return txt
            }
        }
        clearRecentResolvedOrigin(trackUriString)
        Log.d(TAG, "mode INTERNAL SPL load miss")
        return null
    }

    fun resolveOriginForTrack(context: Context, trackUriString: String): TrackLrcOrigin? {
        if (trackUriString.isBlank()) return null
        val effectiveTrackUriString = resolveRuntimeAlias(context, trackUriString)
        val safOnlyBackend = isSafBackend(context)
        val originStartMs = SystemClock.elapsedRealtime()
        LyricsPerf.mark(trackUriString, "lrc_origin_resolve_start", "backend=${if (safOnlyBackend) "SAF" else "INTERNAL"}")
        logLyricsBackend(context, safOnlyBackend)

        if (effectiveTrackUriString == trackUriString) {
            getRecentResolvedOrigin(trackUriString)?.let { cached ->
                LyricsPerf.mark(
                    trackUriString,
                    "lrc_origin_resolve_done",
                    "ms=${SystemClock.elapsedRealtime() - originStartMs} sourceType=${cached.sourceType} file=${cached.fileName} cache=true"
                )
                return cached
            }
        }

        resolveSmpLyricsTarget(context, effectiveTrackUriString, requireExisting = true)?.let { resolved ->
            return TrackLrcOrigin(
                source = "LRC_STORAGE_SMP",
                fileName = resolved.fileName,
                debugPath = resolved.file.absolutePath,
                sourceType = "smp"
            ).also { cacheRecentResolvedOrigin(trackUriString, it) }
        }

        if (!safOnlyBackend) {
            val dirs = internalSplLyricsDirs(context, createIfMissing = false) ?: return null
            val (upperDir, lowerDir) = dirs
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

    fun saveForTrack(context: Context, trackUriString: String, lines: List<LrcLine>): Boolean {
        if (lyricsSaveLock.isLocked) {
            Log.d(
                LYRICS_AUTOSAVE_CRASH_DIAG_TAG,
                "AUTOSAVE_CONCURRENT_BLOCKED trackUri=$trackUriString"
            )
        }
        return lyricsSaveLock.withLock {
            saveForTrackLocked(context, trackUriString, lines)
        }
    }

    private fun saveForTrackLocked(context: Context, trackUriString: String, lines: List<LrcLine>): Boolean {
        if (trackUriString.isBlank()) return false
        clearRecentResolvedOrigin(trackUriString)
        val effectiveTrackUriString = resolveRuntimeAlias(context, trackUriString)
        if (effectiveTrackUriString != trackUriString) {
            clearRecentResolvedOrigin(effectiveTrackUriString)
        }
        val text = linesToLrcText(lines)
        val safOnlyBackend = isSafBackend(context)
        logLyricsBackend(context, safOnlyBackend)

        resolveSmpLyricsTarget(context, effectiveTrackUriString, requireExisting = false)?.let { resolved ->
            val written = runCatching {
                resolved.file.parentFile?.mkdirs()
                writeTextAtomically(
                    target = resolved.file,
                    text = text,
                    lineCount = lines.size
                )
            }.getOrDefault(false)
            if (written) {
                cacheRecentResolvedOrigin(
                    trackUriString,
                    TrackLrcOrigin(
                        source = "LRC_STORAGE_SMP",
                        fileName = resolved.fileName,
                        debugPath = resolved.file.absolutePath,
                        sourceType = "smp"
                    )
                )
                Log.i("LrcDebug", "LRC_SAVE path=${resolved.file.absolutePath}")
            } else {
                Log.w(TAG, "mode SMP save failed path=${resolved.file.absolutePath}")
            }
            Log.i(TAG, "mode SMP save file=$written len=${text.length} target=${resolved.fileName}")
            return written
        }

        if (safOnlyBackend) {
            val safDir = getConfiguredSafDir(context, trackUriString, createIfMissing = true)
            if (safDir == null) {
                Log.w(TAG, "mode SAF save blocked: SAF dir unavailable")
                return false
            }
            Log.i(TAG, "mode SAF save dir=${safDir.uri}")
            val targetFileName = resolveSafWriteTargetFileName(context, trackUriString, safDir)
            val savedPath = saveToConfiguredFolder(context, safDir, targetFileName, text)
            val okConfigured = !savedPath.isNullOrBlank()
            if (okConfigured) {
                rememberCanonicalFileName(context, trackUriString, targetFileName)
                logWorkspaceSuccess(
                    stage = "save_saf_lyrics",
                    snapshot = resolveWorkspaceSnapshot(context),
                    finalPath = savedPath
                )
                Log.i("LrcDebug", "LRC_SAVE path=$savedPath")
            } else {
                logWorkspaceFailure(
                    stage = "save_saf_lyrics",
                    snapshot = resolveWorkspaceSnapshot(context),
                    finalPath = "${safDir.uri}/$targetFileName",
                    error = "write_failed"
                )
            }
            Log.i(TAG, "mode SAF save configured=$okConfigured len=${text.length} file=$targetFileName")
            return okConfigured
        }

        // ✅ MODE INTERNE SPL : écrit dans BackingTracks/Lyrics/<base>.lrc
        if (!safOnlyBackend) {
            val internalRoot = getInternalSplRoot(context)
            if (internalRoot == null) {
                Log.w(TAG, "mode INTERNAL SPL save blocked: workspace root unavailable")
                return false
            }
            Log.i(TAG, "mode INTERNAL SPL save root=${internalRoot.absolutePath}")
            val okFile = saveToInternalSplFolder(context, trackUriString, text)
            if (okFile) {
                internalSplLyricsDirs(context, createIfMissing = false)?.first?.let { upperDir ->
                    val outFile = File(upperDir, sidecarNameForTrack(trackUriString))
                    Log.i("LrcDebug", "LRC_SAVE path=${outFile.absolutePath}")
                }
            }
            Log.i(TAG, "mode INTERNAL SPL save file=$okFile len=${text.length}")
            return okFile
        }
        return false
    }

    fun deleteForTrack(context: Context, trackUriString: String): Boolean {
        if (trackUriString.isBlank()) return false
        clearRecentResolvedOrigin(trackUriString)
        val safOnlyBackend = isSafBackend(context)
        logLyricsBackend(context, safOnlyBackend)

        resolveSmpLyricsTarget(context, trackUriString, requireExisting = false)?.let { resolved ->
            val deleted = !resolved.file.exists() || resolved.file.delete()
            if (deleted && !resolved.file.exists()) {
                Log.i("LrcDebug", "LRC_DELETE path=${resolved.file.absolutePath}")
            } else {
                Log.w(TAG, "mode SMP delete failed path=${resolved.file.absolutePath}")
            }
            return deleted
        }

        if (safOnlyBackend) {
            var deletedAny = false
            runCatching {
                val safDir = getConfiguredSafDir(context)
                if (safDir == null) {
                    Log.w(TAG, "mode SAF delete blocked: SAF dir unavailable")
                    return@runCatching false
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
                            deletedAny = true
                            logWorkspaceSuccess(
                                stage = "delete_saf_lyrics",
                                snapshot = resolveWorkspaceSnapshot(context),
                                finalPath = doc.uri.toString()
                            )
                            Log.i("LrcDebug", "LRC_DELETE path=${doc.uri}")
                        }
                    }
                }
                clearRememberedCanonicalFileName(context, trackUriString)
                true
            }
            return deletedAny
        }

        // INTERNAL SPL sidecar
        var deletedAny = false
        runCatching {
            val dirs = internalSplLyricsDirs(context, createIfMissing = false)
                ?: return@runCatching
            val (upperDir, lowerDir) = dirs
            val sidecar = sidecarNameForTrack(trackUriString)
            val upper = File(upperDir, sidecar)
            val lower = File(lowerDir, sidecar)
            if (upper.delete()) {
                deletedAny = true
                logWorkspaceSuccess(
                    stage = "delete_internal_lyrics",
                    snapshot = resolveWorkspaceSnapshot(context),
                    finalPath = upper.absolutePath
                )
                Log.i("LrcDebug", "LRC_DELETE path=${upper.absolutePath}")
            }
            if (lower.delete()) {
                deletedAny = true
                logWorkspaceSuccess(
                    stage = "delete_internal_lyrics",
                    snapshot = resolveWorkspaceSnapshot(context),
                    finalPath = lower.absolutePath
                )
                Log.i("LrcDebug", "LRC_DELETE path=${lower.absolutePath}")
            }
        }
        return deletedAny
    }

    fun isSmpRuntimeTrack(context: Context, trackUriString: String): Boolean {
        if (trackUriString.isBlank()) return false
        return resolveSmpRuntimeSongDir(context, resolveRuntimeAlias(context, trackUriString)) != null
    }

    fun rememberRuntimeAlias(context: Context, sourceTrackUriString: String, runtimeTrackUriString: String) {
        if (sourceTrackUriString.isBlank() || runtimeTrackUriString.isBlank()) return
        if (sourceTrackUriString == runtimeTrackUriString) return
        context.getSharedPreferences(SMP_ALIAS_PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(md5(sourceTrackUriString), runtimeTrackUriString)
            .apply()
        clearRecentResolvedOrigin(sourceTrackUriString)
        clearRecentResolvedOrigin(runtimeTrackUriString)
    }

    fun resolveRuntimeAlias(context: Context, trackUriString: String): String {
        if (trackUriString.isBlank()) return trackUriString
        return context.getSharedPreferences(SMP_ALIAS_PREF, Context.MODE_PRIVATE)
            .getString(md5(trackUriString), null)
            ?.takeIf { it.isNotBlank() }
            ?: trackUriString
    }

    fun currentWorkspaceScopeKey(context: Context): String? {
        return resolveWorkspaceSnapshot(context).workspaceRootUri?.toString()
    }

    fun isWorkspaceSaf(context: Context): Boolean {
        return isSafBackend(context)
    }

    fun loadAccordsForTrack(
        context: Context,
        trackUriString: String,
        preferredLrcFileName: String?
    ): String? {
        if (trackUriString.isBlank()) return null
        resolveSmpAccordsTarget(context, trackUriString, requireExisting = false)?.let { resolved ->
            val text = if (resolved.file.isFile) {
                runCatching { resolved.file.readText(Charsets.UTF_8) }.getOrNull()
            } else {
                null
            }
            if (text != null) {
                Log.d(TAG, "mode SMP accords load path=${resolved.file.absolutePath}")
                return text
            }
            return null
        }

        val targetNames = resolveAccordsReadTargetNames(trackUriString, preferredLrcFileName)
        if (targetNames.isEmpty()) return null
        val safOnlyBackend = isSafBackend(context)
        logLyricsBackend(context, safOnlyBackend)
        logTrackNameDiagnostics(context, trackUriString, stage = "ACCORDS_LOAD")

        if (safOnlyBackend) {
            val safDir = getConfiguredSafAccordsDir(context, trackUriString, createIfMissing = false)
                ?: return null
            val resolved = resolveSafFileByNames(safDir, targetNames)
                ?: return null
            return readSafText(context, resolved, trackUriString)
        }

        val dirs = internalSplAccordsDirs(context, createIfMissing = false) ?: return null
        return readFromInternalDirsByNames(
            context = context,
            dirs = dirs,
            targetNames = targetNames,
            stageKey = accordsFolderSpec.stageKey
        )
    }

    fun saveAccordsForTrack(
        context: Context,
        trackUriString: String,
        preferredLrcFileName: String?,
        lines: List<LrcLine>
    ): String? {
        if (trackUriString.isBlank()) return null
        val text = linesToLrcText(lines)
        resolveSmpAccordsTarget(context, trackUriString, requireExisting = false)?.let { resolved ->
            val written = runCatching {
                resolved.file.parentFile?.mkdirs()
                resolved.file.writeText(text, Charsets.UTF_8)
                true
            }.getOrDefault(false)
            if (written) {
                Log.i("LrcDebug", "ACCORDS_SAVE path=${resolved.file.absolutePath}")
                return resolved.fileName
            }
            Log.w(TAG, "mode SMP accords save failed path=${resolved.file.absolutePath}")
            return null
        }

        val targetName = resolveAccordsFileNameForTrack(context, trackUriString, preferredLrcFileName)
        if (targetName.isBlank()) return null
        val safOnlyBackend = isSafBackend(context)
        logLyricsBackend(context, safOnlyBackend)

        if (safOnlyBackend) {
            val safDir = getConfiguredSafAccordsDir(context, trackUriString, createIfMissing = true)
                ?: return null
            val savedPath = saveToConfiguredFolder(context, safDir, targetName, text)
            if (!savedPath.isNullOrBlank()) {
                logWorkspaceSuccess(
                    stage = "save_saf_accords",
                    snapshot = resolveWorkspaceSnapshot(context),
                    finalPath = savedPath
                )
                return targetName
            }
            logWorkspaceFailure(
                stage = "save_saf_accords",
                snapshot = resolveWorkspaceSnapshot(context),
                finalPath = "${safDir.uri}/$targetName",
                error = "write_failed"
            )
            return null
        }

        val dirs = internalSplAccordsDirs(context, createIfMissing = true) ?: return null
        return if (
            saveToInternalDirsByName(
                context = context,
                dirs = dirs,
                targetName = targetName,
                text = text,
                stageKey = accordsFolderSpec.stageKey
            )
        ) {
            targetName
        } else {
            null
        }
    }

    fun deleteAccordsForTrack(
        context: Context,
        trackUriString: String,
        preferredLrcFileName: String?
    ): Boolean {
        if (trackUriString.isBlank()) return false
        resolveSmpAccordsTarget(context, trackUriString, requireExisting = false)?.let { resolved ->
            val deleted = !resolved.file.exists() || resolved.file.delete()
            if (deleted && !resolved.file.exists()) {
                Log.i("LrcDebug", "ACCORDS_DELETE path=${resolved.file.absolutePath}")
            }
            return deleted
        }

        val targetNames = resolveAccordsDeleteTargetNames(trackUriString, preferredLrcFileName)
        if (targetNames.isEmpty()) return false
        val safOnlyBackend = isSafBackend(context)
        logLyricsBackend(context, safOnlyBackend)

        if (safOnlyBackend) {
            val safDir = getConfiguredSafAccordsDir(context, trackUriString, createIfMissing = false)
                ?: return false
            var deletedAny = false
            targetNames.forEach { name ->
                findFileIgnoreCase(safDir, name)?.let { doc ->
                    val deleted = runCatching { doc.delete() }.getOrDefault(false)
                    if (deleted) {
                        deletedAny = true
                        logWorkspaceSuccess(
                            stage = "delete_saf_accords",
                            snapshot = resolveWorkspaceSnapshot(context),
                            finalPath = doc.uri.toString()
                        )
                    }
                }
            }
            return deletedAny
        }

        val dirs = internalSplAccordsDirs(context, createIfMissing = false) ?: return false
        return deleteFromInternalDirsByNames(
            context = context,
            dirs = dirs,
            targetNames = targetNames,
            stageKey = accordsFolderSpec.stageKey
        )
    }

    fun ensureAccordsFileForTrack(
        context: Context,
        trackUriString: String,
        preferredLrcFileName: String?
    ): AccordsEnsureResult {
        if (trackUriString.isBlank()) return AccordsEnsureResult.FAILED
        val existing = loadAccordsForTrack(context, trackUriString, preferredLrcFileName)
        if (existing != null) return AccordsEnsureResult.ALREADY_EXISTS
        return if (saveAccordsForTrack(context, trackUriString, preferredLrcFileName, emptyList()) != null) {
            AccordsEnsureResult.CREATED
        } else {
            AccordsEnsureResult.FAILED
        }
    }

    fun ensureLyricsFileForTrack(
        context: Context,
        trackUriString: String,
        preferredLrcFileName: String?
    ): AccordsEnsureResult {
        if (trackUriString.isBlank()) return AccordsEnsureResult.FAILED
        val existing = resolveOriginForTrack(context, trackUriString)
        if (existing != null) return AccordsEnsureResult.ALREADY_EXISTS
        return if (saveForTrack(context, trackUriString, emptyList()) ) {
            preferredLrcFileName?.trim()?.takeIf { it.isNotBlank() }?.let {
                rememberCanonicalFileName(context, trackUriString, it)
            }
            AccordsEnsureResult.CREATED
        } else {
            AccordsEnsureResult.FAILED
        }
    }

    fun hashedFileNameForTrack(trackUriString: String): String {
        return fileNameForTrack(trackUriString)
    }

    // ------------------------------------------------------------
    // SAF (dossier configuré)
    // ------------------------------------------------------------

    private fun getConfiguredSafDir(
        context: Context,
        trackUriString: String? = null,
        createIfMissing: Boolean = false
    ): DocumentFile? {
        return getConfiguredSafDirForSpec(
            context = context,
            trackUriString = trackUriString,
            createIfMissing = createIfMissing,
            spec = lyricsFolderSpec
        )
    }

    private fun getConfiguredSafAccordsDir(
        context: Context,
        trackUriString: String? = null,
        createIfMissing: Boolean = false
    ): DocumentFile? {
        return getConfiguredSafDirForSpec(
            context = context,
            trackUriString = trackUriString,
            createIfMissing = createIfMissing,
            spec = accordsFolderSpec
        )
    }

    private fun getConfiguredSafDirForSpec(
        context: Context,
        trackUriString: String? = null,
        createIfMissing: Boolean = false,
        spec: WorkspaceFolderSpec
    ): DocumentFile? {
        val configuredStartMs = SystemClock.elapsedRealtime()
        val dir = resolveSafTextDirFromWorkspace(context, trackUriString, createIfMissing, spec)
        LyricsPerf.mark(
            trackUriString,
            "saf_configured_dir_done",
            "ms=${SystemClock.elapsedRealtime() - configuredStartMs} mode=workspace dir=${dir?.uri} bucket=${spec.stageKey}"
        )
        return dir
    }

    private fun resolveSafTextDirFromWorkspace(
        context: Context,
        trackUriString: String? = null,
        createIfMissing: Boolean = false,
        spec: WorkspaceFolderSpec
    ): DocumentFile? {
        val resolveStartMs = SystemClock.elapsedRealtime()
        val snapshot = resolveWorkspaceSnapshot(context)
        val requestedRootUri = snapshot.workspaceRootUri
        if (
            snapshot.mode != StorageModePrefs.Mode.SAF ||
            !snapshot.isUsable ||
            requestedRootUri == null ||
            requestedRootUri.scheme != "content"
        ) {
            logWorkspaceFailure(
                stage = "resolve_saf_lyrics_dir",
                snapshot = snapshot,
                error = "workspace_root_unavailable"
            )
            return null
        }
        val rootDoc = resolveRootDocument(context, requestedRootUri, trackUriString)
            ?.takeIf { it.isDirectory && it.canRead() }
            ?: run {
                logWorkspaceFailure(
                    stage = "resolve_saf_root_doc",
                    snapshot = snapshot,
                    error = "workspace_root_unreadable"
                )
                return null
            }
        Log.d(
            "LrcDebug",
            "LYRICS_SAF_DIR workspaceResolvedRoot=${rootDoc.uri} rootChildren=${listChildNames(rootDoc, trackUriString, label = "workspace_root_children")}"
        )

        val backingTracks = if (createIfMissing) {
            ensureDirByAliases(
                parent = rootDoc,
                preferredName = "BackingTracks",
                aliases = listOf("BackingTrack"),
                trackUriString = trackUriString,
                stage = "ensure_backing_tracks"
            )
        } else {
            findDirByAliases(
                parent = rootDoc,
                aliases = listOf("BackingTracks", "BackingTrack"),
                trackUriString = trackUriString,
                stage = "resolve_backing_tracks"
            )
        }
        Log.d(
            "LrcDebug",
            "LYRICS_SAF_DIR backingTracks=${backingTracks?.uri} rootChildren=${listChildNames(rootDoc, trackUriString, label = "workspace_root_children_repeat")}"
        )
        val safeBackingTracks = backingTracks ?: return null
        val targetDir = if (createIfMissing) {
            ensureDirByAliases(
                parent = safeBackingTracks,
                preferredName = spec.preferredName,
                aliases = listOf(spec.aliasName),
                trackUriString = trackUriString,
                stage = "ensure_${spec.stageKey}_dir"
            )
        } else {
            findDirByAliases(
                parent = safeBackingTracks,
                aliases = listOf(spec.preferredName, spec.aliasName),
                trackUriString = trackUriString,
                stage = "resolve_${spec.stageKey}_dir"
            )
        }
        Log.d(
            "LrcDebug",
            "LYRICS_SAF_DIR resolvedTargetDir=${targetDir?.uri} backingChildren=${listChildNames(safeBackingTracks, trackUriString, label = "backing_tracks_children")}"
        )
        val safeTargetDir = targetDir ?: return null
        Log.d(
            "LrcDebug",
            "LYRICS_SAF_DIR children=${listChildNames(safeTargetDir, trackUriString, label = "${spec.stageKey}_dir_children")}"
        )
        LyricsPerf.mark(
            trackUriString,
            "saf_fallback_dir_done",
            "ms=${SystemClock.elapsedRealtime() - resolveStartMs} root=${rootDoc.uri} dir=${safeTargetDir.uri} bucket=${spec.stageKey}"
        )
        logWorkspaceSuccess(
            stage = "resolve_saf_${spec.stageKey}_dir",
            snapshot = snapshot,
            finalPath = safeTargetDir.uri.toString()
        )
        return safeTargetDir.takeIf { it.isDirectory && it.canRead() }
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

    private fun ensureDirByAliases(
        parent: DocumentFile,
        preferredName: String,
        aliases: List<String>,
        trackUriString: String? = null,
        stage: String = "ensure_dir_by_aliases"
    ): DocumentFile? {
        findDirByAliases(
            parent = parent,
            aliases = listOf(preferredName) + aliases,
            trackUriString = trackUriString,
            stage = stage
        )?.let { return it }

        val created = runCatching { parent.createDirectory(preferredName) }.getOrNull()
        LyricsPerf.mark(
            trackUriString,
            "saf_create_dir_done",
            "stage=$stage parent=${parent.uri} name=$preferredName created=${created?.uri}"
        )
        return created?.takeIf { it.isDirectory && it.canRead() }
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
            "LYRICS_LOOKUP_CHILDREN dir=${dir.uri} fileCount=${files.size}"
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

    private fun resolveSafFileByNames(
        dir: DocumentFile,
        targetNames: List<String>
    ): DocumentFile? {
        val files = listSafFiles(dir)
        targetNames.forEach { name ->
            findFileIgnoreCaseOrLrcTxt(files, name)?.let { return it }
        }
        return null
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
                Log.d(
                    LYRICS_AUTOSAVE_CRASH_DIAG_TAG,
                    "AUTOSAVE_WRITE_START filePath=${target.uri}"
                )
                out.write(text.toByteArray(Charsets.UTF_8))
                out.flush()
            } ?: return null
            Log.d(
                LYRICS_AUTOSAVE_CRASH_DIAG_TAG,
                "AUTOSAVE_WRITE_OK fileSize=${text.toByteArray(Charsets.UTF_8).size} lineCount=${text.lineSequence().count()}"
            )

            target.uri.toString()
        }.onFailure { error ->
            Log.e(
                LYRICS_AUTOSAVE_CRASH_DIAG_TAG,
                "AUTOSAVE_WRITE_FAIL exception=${error.message}",
                error
            )
        }.getOrNull()
    }

    private fun readSafText(context: Context, file: DocumentFile, trackUriString: String? = null): String? {
        val readStartMs = SystemClock.elapsedRealtime()
        val snapshot = resolveWorkspaceSnapshot(context)
        val text = runCatching {
            context.contentResolver.openInputStream(file.uri)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
        }.onSuccess {
            logWorkspaceSuccess(
                stage = "read_saf_lyrics",
                snapshot = snapshot,
                finalPath = file.uri.toString()
            )
        }.onFailure {
            logWorkspaceFailure(
                stage = "read_saf_lyrics",
                snapshot = snapshot,
                finalPath = file.uri.toString(),
                error = "read_failed"
            )
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

        if (uri.scheme != "content") {
            return uri.lastPathSegment?.takeIf { it.isNotBlank() }
                ?: uri.authority?.takeIf { it.isNotBlank() }
        }

        return runCatching {
            DocumentFile.fromSingleUri(context, uri)?.name
                ?.takeIf { it.isNotBlank() }
                ?: DocumentFile.fromTreeUri(context, uri)?.name?.takeIf { it.isNotBlank() }
        }.getOrNull()
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

    private fun resolveAccordsReadTargetNames(
        trackUriString: String,
        preferredLrcFileName: String?
    ): List<String> {
        val preferred = preferredLrcFileName?.trim().orEmpty()
        if (preferred.isNotBlank()) return listOf(preferred)
        val sidecar = sidecarNameForTrack(trackUriString)
        val hashed = fileNameForTrack(trackUriString)
        return linkedSetOf(sidecar, hashed).toList()
    }

    private fun resolveAccordsDeleteTargetNames(
        trackUriString: String,
        preferredLrcFileName: String?
    ): Set<String> {
        return linkedSetOf<String>().apply {
            preferredLrcFileName?.trim()?.takeIf { it.isNotBlank() }?.let { add(it) }
            add(fileNameForTrack(trackUriString))
            add(sidecarNameForTrack(trackUriString))
        }
    }

    private fun resolveAccordsFileNameForTrack(
        context: Context,
        trackUriString: String,
        preferredLrcFileName: String?
    ): String {
        preferredLrcFileName?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
        resolveOriginForTrack(context, trackUriString)?.fileName
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        return fileNameForTrack(trackUriString)
    }

    private fun loadFromSmpSongFolder(context: Context, trackUriString: String): SmpResolvedLyrics? {
        return resolveSmpLyricsTarget(context, trackUriString, requireExisting = true)
    }

    private fun resolveSmpLyricsTarget(
        context: Context,
        trackUriString: String,
        requireExisting: Boolean
    ): SmpResolvedLyrics? {
        return resolveSmpTextTarget(
            context = context,
            trackUriString = trackUriString,
            transportNameSelector = { null },
            fallbackName = "lyrics.lrc",
            requireExisting = requireExisting
        )
    }

    private fun resolveSmpAccordsTarget(
        context: Context,
        trackUriString: String,
        requireExisting: Boolean
    ): SmpResolvedLyrics? {
        return resolveSmpTextTarget(
            context = context,
            trackUriString = trackUriString,
            transportNameSelector = { it.files?.chords },
            fallbackName = "chords.lrc",
            requireExisting = requireExisting
        )
    }

    private fun resolveSmpTextTarget(
        context: Context,
        trackUriString: String,
        transportNameSelector: (SmpConfig) -> String?,
        fallbackName: String,
        requireExisting: Boolean
    ): SmpResolvedLyrics? {
        val songDir = resolveSmpRuntimeSongDir(context, trackUriString) ?: return null
        val configFile = File(songDir, "config.json")
        val config = runCatching {
            SmpConfig.fromJsonOrNull(configFile.readText(Charsets.UTF_8))
        }.getOrNull() ?: return null

        val transportName = transportNameSelector(config)?.trim().takeUnless { it.isNullOrBlank() } ?: fallbackName
        val targetFile = resolveSongUnitChildFile(songDir, transportName) ?: return null
        if (requireExisting && !targetFile.isFile) {
            return null
        }

        return SmpResolvedLyrics(
            file = targetFile,
            fileName = targetFile.name
        )
    }

    private fun resolveSmpRuntimeSongDir(
        context: Context,
        trackUriString: String
    ): File? {
        val trackUri = runCatching { Uri.parse(trackUriString) }.getOrNull() ?: return null
        if (trackUri.scheme != "file") return null

        val audioPath = trackUri.path?.takeIf { it.isNotBlank() } ?: return null
        val audioFile = File(audioPath)
        if (!audioFile.isFile || !audioFile.name.startsWith("audio.", ignoreCase = true)) {
            return null
        }

        val songDir = audioFile.parentFile?.canonicalFile ?: return null
        val tracksRoot = File(context.filesDir, "tracks").canonicalFile
        if (songDir.parentFile?.canonicalFile != tracksRoot) {
            return null
        }

        val configFile = File(songDir, "config.json")
        if (!configFile.isFile) {
            return null
        }

        return songDir
    }

    private fun resolveSongUnitChildFile(songDir: File, transportName: String): File? {
        val cleanName = transportName.trim()
        if (cleanName.isEmpty()) {
            return null
        }

        return runCatching {
            val canonicalSongDir = songDir.canonicalFile
            val canonicalChild = File(canonicalSongDir, cleanName).canonicalFile
            val songPath = canonicalSongDir.path
            if (!canonicalChild.path.startsWith("$songPath${File.separator}") && canonicalChild.path != songPath) {
                return null
            }
            canonicalChild
        }.getOrNull()
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
        val snapshot = WorkspaceResolver.resolve(context)
        return snapshot.mode == StorageModePrefs.Mode.SAF
    }

    private fun logLyricsBackend(context: Context, safOnlyBackend: Boolean) {
        val snapshot = resolveWorkspaceSnapshot(context)
        logWorkspaceSuccess(
            stage = if (safOnlyBackend) "backend_saf" else "backend_file",
            snapshot = snapshot,
            finalPath = snapshot.workspaceRootUri?.toString()
        )
    }

    private fun getInternalSplRoot(context: Context): File? {
        val snapshot = resolveWorkspaceSnapshot(context)
        val rootUri = snapshot.workspaceRootUri
        if (
            snapshot.mode != StorageModePrefs.Mode.INTERNAL ||
            !snapshot.isUsable ||
            rootUri == null ||
            rootUri.scheme != "file" ||
            rootUri.path.isNullOrBlank()
        ) {
            logWorkspaceFailure(
                stage = "resolve_internal_root",
                snapshot = snapshot,
                error = "workspace_root_unavailable"
            )
            return null
        }

        val root = runCatching { File(rootUri.path!!).canonicalFile }.getOrNull()
        if (root == null || !root.exists() || !root.isDirectory) {
            logWorkspaceFailure(
                stage = "resolve_internal_root",
                snapshot = snapshot,
                finalPath = root?.absolutePath,
                error = "workspace_root_unreadable"
            )
            return null
        }
        logWorkspaceSuccess(
            stage = "resolve_internal_root",
            snapshot = snapshot,
            finalPath = root.absolutePath
        )
        return root
    }

    private fun internalSplLyricsDirs(
        context: Context,
        createIfMissing: Boolean
    ): Pair<File, File>? {
        return internalSplDirs(context, createIfMissing, lyricsFolderSpec)
    }

    private fun internalSplAccordsDirs(
        context: Context,
        createIfMissing: Boolean
    ): Pair<File, File>? {
        return internalSplDirs(context, createIfMissing, accordsFolderSpec)
    }

    private fun internalSplDirs(
        context: Context,
        createIfMissing: Boolean,
        spec: WorkspaceFolderSpec
    ): Pair<File, File>? {
        val snapshot = resolveWorkspaceSnapshot(context)
        val root = getInternalSplRoot(context) ?: return null
        val backingTracks = sequenceOf(
            File(root, "BackingTracks"),
            File(root, "BackingTrack")
        ).firstOrNull { it.exists() && it.isDirectory }
            ?: if (createIfMissing) {
                File(root, "BackingTracks").apply { mkdirs() }
            } else {
                null
            }
            ?: run {
                logWorkspaceFailure(
                    stage = "resolve_internal_${spec.stageKey}_dir",
                    snapshot = snapshot,
                    finalPath = root.absolutePath,
                    error = "backing_tracks_missing"
                )
                return null
            }
        val upper = File(backingTracks, spec.preferredName)
        val lower = File(backingTracks, spec.aliasName)
        if (createIfMissing) {
            if (!upper.exists()) upper.mkdirs()
            if (!lower.exists()) lower.mkdirs()
        }
        if ((!upper.exists() || !upper.isDirectory) && (!lower.exists() || !lower.isDirectory)) {
            logWorkspaceFailure(
                stage = "resolve_internal_${spec.stageKey}_dir",
                snapshot = snapshot,
                finalPath = backingTracks.absolutePath,
                error = "${spec.stageKey}_dir_unavailable"
            )
            return null
        }
        logWorkspaceSuccess(
            stage = "resolve_internal_${spec.stageKey}_dir",
            snapshot = snapshot,
            finalPath = upper.takeIf { it.exists() && it.isDirectory }?.absolutePath
                ?: lower.takeIf { it.exists() && it.isDirectory }?.absolutePath
        )
        return upper to lower
    }

    private fun sidecarNameForTrack(trackUriString: String): String {
        val base = baseNameFromUriString(trackUriString)
        return "$base.lrc"
    }

    private fun loadFromInternalSplFolder(context: Context, trackUriString: String): String? {
        return readFromInternalDirsByNames(
            context = context,
            dirs = internalSplLyricsDirs(context, createIfMissing = false) ?: return null,
            targetNames = listOf(sidecarNameForTrack(trackUriString)),
            stageKey = lyricsFolderSpec.stageKey
        )
    }

    private fun saveToInternalSplFolder(context: Context, trackUriString: String, text: String): Boolean {
        return saveToInternalDirsByName(
            context = context,
            dirs = internalSplLyricsDirs(context, createIfMissing = true) ?: return false,
            targetName = sidecarNameForTrack(trackUriString),
            text = text,
            stageKey = lyricsFolderSpec.stageKey
        )
    }

    private fun readFromInternalDirsByNames(
        context: Context,
        dirs: Pair<File, File>,
        targetNames: List<String>,
        stageKey: String
    ): String? {
        val cleanTargetNames = targetNames.map { it.trim() }.filter { it.isNotBlank() }
        if (cleanTargetNames.isEmpty()) return null
        val (upperDir, lowerDir) = dirs
        val candidates = cleanTargetNames.flatMap { name ->
            listOf(File(upperDir, name), File(lowerDir, name))
        }
        candidates.firstOrNull { it.exists() && it.isFile }?.let { file ->
            Log.d(TAG, "mode INTERNAL ${stageKey.uppercase()} load path=${file.absolutePath}")
            return runCatching { file.readText(Charsets.UTF_8) }
                .onSuccess {
                    logWorkspaceSuccess(
                        stage = "load_internal_$stageKey",
                        snapshot = resolveWorkspaceSnapshot(context),
                        finalPath = file.absolutePath
                    )
                }
                .onFailure {
                    logWorkspaceFailure(
                        stage = "load_internal_$stageKey",
                        snapshot = resolveWorkspaceSnapshot(context),
                        finalPath = file.absolutePath,
                        error = "read_failed"
                    )
                }
                .getOrNull()
        }
        return null
    }

    private fun saveToInternalDirsByName(
        context: Context,
        dirs: Pair<File, File>,
        targetName: String,
        text: String,
        stageKey: String
    ): Boolean {
        val cleanTargetName = targetName.trim()
        if (cleanTargetName.isBlank()) return false
        val (upperDir, _) = dirs
        val outFile = File(upperDir, cleanTargetName)
        return runCatching {
            if (stageKey == lyricsFolderSpec.stageKey) {
                writeTextAtomically(
                    target = outFile,
                    text = text,
                    lineCount = text.lineSequence().count()
                )
            } else {
                outFile.writeText(text, Charsets.UTF_8)
            }
            logWorkspaceSuccess(
                stage = "save_internal_$stageKey",
                snapshot = resolveWorkspaceSnapshot(context),
                finalPath = outFile.absolutePath
            )
            Log.d(TAG, "mode INTERNAL ${stageKey.uppercase()} save path=${outFile.absolutePath}")
            true
        }.onFailure {
            logWorkspaceFailure(
                stage = "save_internal_$stageKey",
                snapshot = resolveWorkspaceSnapshot(context),
                finalPath = outFile.absolutePath,
                error = "write_failed"
            )
        }.getOrDefault(false)
    }

    private fun deleteFromInternalDirsByNames(
        context: Context,
        dirs: Pair<File, File>,
        targetNames: Set<String>,
        stageKey: String
    ): Boolean {
        val cleanTargetNames = targetNames.map { it.trim() }.filter { it.isNotBlank() }
        if (cleanTargetNames.isEmpty()) return false
        val (upperDir, lowerDir) = dirs
        var deletedAny = false
        cleanTargetNames.forEach { name ->
            listOf(File(upperDir, name), File(lowerDir, name)).forEach { file ->
                if (file.delete()) {
                    deletedAny = true
                    logWorkspaceSuccess(
                        stage = "delete_internal_$stageKey",
                        snapshot = resolveWorkspaceSnapshot(context),
                        finalPath = file.absolutePath
                    )
                }
            }
        }
        return deletedAny
    }

    private fun resolveWorkspaceSnapshot(context: Context): WorkspaceResolver.Snapshot {
        return WorkspaceResolver.resolve(context)
    }

    private fun logWorkspaceSuccess(
        stage: String,
        snapshot: WorkspaceResolver.Snapshot,
        finalPath: String?
    ) {
        Log.i(
            WORKSPACE_LOG_TAG,
            "stage=$stage mode=${workspaceModeLabel(snapshot)} status=${snapshot.status} root=${snapshot.workspaceRootUri} path=$finalPath error=null detail=${snapshot.detail}"
        )
    }

    private fun logWorkspaceFailure(
        stage: String,
        snapshot: WorkspaceResolver.Snapshot,
        finalPath: String? = null,
        error: String
    ) {
        Log.w(
            WORKSPACE_LOG_TAG,
            "stage=$stage mode=${workspaceModeLabel(snapshot)} status=${snapshot.status} root=${snapshot.workspaceRootUri} path=$finalPath error=$error detail=${snapshot.detail}"
        )
    }

    private fun workspaceModeLabel(snapshot: WorkspaceResolver.Snapshot): String {
        return when (snapshot.mode) {
            StorageModePrefs.Mode.SAF -> "SAF"
            StorageModePrefs.Mode.INTERNAL -> "FILE"
        }
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

    private fun writeTextAtomically(
        target: File,
        text: String,
        lineCount: Int
    ): Boolean {
        val parent = target.parentFile ?: return false
        if (!parent.exists() && !parent.mkdirs()) return false
        val tmp = File(parent, "${target.name}.tmp")
        return runCatching {
            Log.d(
                LYRICS_AUTOSAVE_CRASH_DIAG_TAG,
                "AUTOSAVE_WRITE_START filePath=${target.absolutePath}"
            )
            val bytes = text.toByteArray(Charsets.UTF_8)
            FileOutputStream(tmp, false).use { output ->
                output.write(bytes)
                output.flush()
                output.fd.sync()
            }
            runCatching {
                Os.rename(tmp.absolutePath, target.absolutePath)
            }.getOrElse {
                if (target.exists() && !target.delete()) throw it
                if (!tmp.renameTo(target)) throw it
            }
            Log.d(
                LYRICS_AUTOSAVE_CRASH_DIAG_TAG,
                "AUTOSAVE_WRITE_OK fileSize=${target.length()} lineCount=$lineCount"
            )
            true
        }.onFailure { error ->
            runCatching { tmp.delete() }
            Log.e(
                LYRICS_AUTOSAVE_CRASH_DIAG_TAG,
                "AUTOSAVE_WRITE_FAIL exception=${error.message}",
                error
            )
        }.getOrDefault(false)
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
