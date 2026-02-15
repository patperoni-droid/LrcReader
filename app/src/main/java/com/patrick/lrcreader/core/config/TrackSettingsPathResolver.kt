package com.patrick.lrcreader.core.config

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.provider.DocumentsContract
import android.util.Log
import com.patrick.lrcreader.core.BackupFolderPrefs
import com.patrick.lrcreader.core.LibraryIndexCache
import java.io.File
import java.util.LinkedHashMap

internal object TrackSettingsPathResolver {

    private const val TAG = "TrackSettingsPathResolver"
    private const val MAX_RELATIVE_PATH_CACHE_SIZE = 4096

    private val cacheLock = Any()
    private val relativePathCache = object : LinkedHashMap<String, String?>(MAX_RELATIVE_PATH_CACHE_SIZE + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String?>): Boolean {
            return size > MAX_RELATIVE_PATH_CACHE_SIZE
        }
    }
    private var resolveCallCount: Long = 0
    private var resolveCacheHitCount: Long = 0
    private var resolveTotalMs: Long = 0

    fun resolveRelativeTrackPath(context: Context, uriString: String): String? {
        val tStart = SystemClock.elapsedRealtime()
        var rootKey: String? = null
        var cacheHit = false
        var resolved: String? = null

        if (uriString.isNotBlank()) {
            val rootUri = BackupFolderPrefs.getLibraryRootUri(context)
            if (rootUri != null) {
                rootKey = rootUri.toString()
                val cacheKey = "$rootKey|$uriString"

                synchronized(cacheLock) {
                    if (relativePathCache.containsKey(cacheKey)) {
                        cacheHit = true
                        resolved = relativePathCache[cacheKey]
                    }
                }

                if (!cacheHit) {
                    val trackUri = runCatching { Uri.parse(uriString) }.getOrNull()
                    if (trackUri != null) {
                        resolved = resolveFromFileRoot(rootUri, trackUri)
                            ?: resolveFromDocId(rootUri, trackUri)
                            ?: resolveFromIndex(context, rootUri, uriString)
                    }

                    synchronized(cacheLock) {
                        relativePathCache[cacheKey] = resolved
                    }
                }
            }
        }

        val elapsed = SystemClock.elapsedRealtime() - tStart
        synchronized(cacheLock) {
            resolveCallCount += 1
            resolveTotalMs += elapsed
            if (cacheHit) resolveCacheHitCount += 1
            Log.d(
                "BOOTSTEP",
                "TrackPathResolve calls=$resolveCallCount cacheHits=$resolveCacheHitCount totalMs=$resolveTotalMs lastMs=$elapsed cacheHit=$cacheHit root=$rootKey resolved=${resolved != null}"
            )
        }

        return resolved
    }

    private fun resolveFromFileRoot(rootUri: Uri, trackUri: Uri): String? {
        if (rootUri.scheme != "file" || trackUri.scheme != "file") return null

        val rootPath = rootUri.path ?: return null
        val trackPath = trackUri.path ?: return null

        val root = File(rootPath)
        val target = File(trackPath)

        val rootCanonical = runCatching { root.canonicalFile }.getOrNull() ?: return null
        val targetCanonical = runCatching { target.canonicalFile }.getOrNull() ?: return null

        val rootPrefix = rootCanonical.absolutePath.trimEnd(File.separatorChar) + File.separator
        val targetPath = targetCanonical.absolutePath
        if (!targetPath.startsWith(rootPrefix)) return null

        val rel = targetPath.removePrefix(rootPrefix)

        return sanitizeRelativePath(rel)
    }

    private fun resolveFromDocId(rootUri: Uri, trackUri: Uri): String? {
        if (rootUri.scheme != "content" || trackUri.scheme != "content") return null
        if (rootUri.authority != trackUri.authority) return null

        val rootDocId = extractDocId(rootUri) ?: return null
        val targetDocId = extractDocId(trackUri) ?: return null

        if (!targetDocId.equals(rootDocId, ignoreCase = true) &&
            !targetDocId.startsWith("$rootDocId/", ignoreCase = true)
        ) {
            return null
        }

        val rel = if (targetDocId.equals(rootDocId, ignoreCase = true)) {
            ""
        } else {
            targetDocId.removePrefix(rootDocId).trimStart('/')
        }

        return sanitizeRelativePath(rel)
    }

    private fun resolveFromIndex(context: Context, rootUri: Uri, uriString: String): String? {
        val indexAll = LibraryIndexCache.load(context).orEmpty()
        if (indexAll.isEmpty()) return null

        val byUri = indexAll.associateBy { it.uriString }
        var current = byUri[uriString] ?: return null

        val parts = ArrayList<String>()
        val rootKey = rootUri.toString()
        var guard = 0

        while (guard < 1024) {
            val parent = current.parentUriString ?: return null
            parts.add(current.name)

            if (parent == rootKey) {
                val joined = parts.asReversed().joinToString("/")
                return sanitizeRelativePath(joined)
            }

            current = byUri[parent] ?: return null
            guard++
        }

        Log.w(TAG, "resolveFromIndex aborted: parent chain too deep uri=$uriString")
        return null
    }

    private fun extractDocId(uri: Uri): String? {
        return runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
            ?: runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
    }

    private fun sanitizeRelativePath(raw: String?): String? {
        val normalized = raw
            ?.replace('\\', '/')
            ?.trim()
            ?.trim('/')
            ?: return null

        if (normalized.isBlank()) return null
        if (normalized.startsWith("../") || normalized == "..") return null

        val segments = normalized.split('/')
        if (segments.any { it == ".." || it.isBlank() }) return null

        return normalized
    }
}
