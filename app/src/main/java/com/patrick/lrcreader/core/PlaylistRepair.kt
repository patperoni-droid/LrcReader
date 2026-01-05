package com.patrick.lrcreader.core

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import java.net.URLDecoder
import java.text.Normalizer

object PlaylistRepair {

    private const val TAG = "PlaylistRepair"

    fun repairDeadUrisFromIndex(
        context: Context,
        indexAll: List<LibraryIndexCache.CachedEntry>
    ) {
        if (indexAll.isEmpty()) {
            Log.w(TAG, "repair skipped: indexAll empty")
            return
        }

        val playlists = PlaylistRepository.getPlaylists()
        if (playlists.isEmpty()) {
            Log.w(TAG, "repair skipped: no playlists")
            return
        }

        // ✅ maps normalisées
        val byNormFull = HashMap<String, String>(indexAll.size * 2)
        val byNormBase = HashMap<String, String>(indexAll.size * 2)

        indexAll.forEach { e ->
            if (e.isDirectory) return@forEach
            val full = e.name
            val fullKey = norm(full)
            val baseKey = norm(baseName(full))

            if (fullKey.isNotBlank() && !byNormFull.containsKey(fullKey)) {
                byNormFull[fullKey] = e.uriString
            }
            if (baseKey.isNotBlank() && !byNormBase.containsKey(baseKey)) {
                byNormBase[baseKey] = e.uriString
            }
        }

        var repairedCount = 0
        var checked = 0

        playlists.forEach { pl ->
            val songs = PlaylistRepository.getSongsFor(pl)
            songs.forEach { oldUriString ->
                if (oldUriString.startsWith("prompter://")) return@forEach
                checked++

                if (canOpen(context, oldUriString)) return@forEach

                val wantedFromUri = extractFileNameSafe(oldUriString)
                val wantedFromCustom = PlaylistRepository.getAnyCustomTitleForUri(oldUriString)

                val candidatesRaw = listOfNotNull(wantedFromCustom, wantedFromUri)
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()

                if (candidatesRaw.isEmpty()) return@forEach

                // candidates : full + base + tentative “avec extension”
                val extFromUri = wantedFromUri?.substringAfterLast('.', "")
                    ?.takeIf { it.isNotBlank() }

                val candidates = LinkedHashSet<String>()
                candidatesRaw.forEach { c ->
                    candidates.add(c)
                    candidates.add(baseName(c))
                    if (!c.contains('.') && !extFromUri.isNullOrBlank()) {
                        candidates.add("$c.$extFromUri")
                    }
                }

                val candKeys = candidates.map { norm(it) }.filter { it.isNotBlank() }.distinct()
                Log.w(TAG, "DEAD uri=$oldUriString candidates=$candidates candKeys=$candKeys")

                var found: String? = null

                // 1) match FULL normalisé
                for (k in candKeys) {
                    found = byNormFull[k]
                    if (!found.isNullOrBlank()) break
                }

                // 2) match BASE normalisé
                if (found.isNullOrBlank()) {
                    for (k in candKeys) {
                        found = byNormBase[k]
                        if (!found.isNullOrBlank()) break
                    }
                }

                if (found.isNullOrBlank()) {
                    Log.w(TAG, "no match in index for candKeys=$candKeys (old=$oldUriString)")
                    return@forEach
                }

                if (!canOpen(context, found)) {
                    Log.w(TAG, "candidate not readable: $found for candKeys=$candKeys")
                    return@forEach
                }

                Log.w(TAG, "REPAIR old=$oldUriString -> new=$found candKeys=$candKeys")

                PlaylistRepository.replaceSongUriEverywhere(oldUriString, found)

                // Nettoyage titres custom
                PlaylistRepository.clearCustomTitleEverywhere(oldUriString)
                PlaylistRepository.clearCustomTitleEverywhere(found)

                repairedCount++
            }
        }

        Log.w(TAG, "repair finished. checked=$checked repairedCount=$repairedCount")
    }

    private fun baseName(name: String): String {
        val n = name.trim()
        val dot = n.lastIndexOf('.')
        return if (dot > 0) n.substring(0, dot) else n
    }

    private fun norm(s: String?): String {
        if (s.isNullOrBlank()) return ""
        val decoded = runCatching { URLDecoder.decode(s, "UTF-8") }.getOrElse { s }
        val trimmed = decoded.trim().replace(Regex("\\s+"), " ")
        val lower = trimmed.lowercase()

        // enlever accents (é -> e, ç -> c, etc.)
        val noAccents = Normalizer.normalize(lower, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")

        // option : enlever certains séparateurs “invisibles”
        return noAccents.replace('\u00A0', ' ').trim().replace(Regex("\\s+"), " ")
    }

    private fun canOpen(context: Context, uriString: String): Boolean {
        return runCatching {
            val uri = Uri.parse(uriString)
            context.contentResolver.openFileDescriptor(uri, "r")?.use { }
                ?: error("openFileDescriptor returned null")
            true
        }.getOrElse { false }
    }

    private fun extractFileNameSafe(uriString: String): String? {
        val fromDocId = runCatching {
            val uri = Uri.parse(uriString)
            val docId = DocumentsContract.getDocumentId(uri)
            docId.substringAfterLast('/')
        }.getOrNull()

        val raw = fromDocId ?: runCatching { Uri.parse(uriString).lastPathSegment }.getOrNull()
        if (raw.isNullOrBlank()) return null

        return runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrElse { raw }
    }
}