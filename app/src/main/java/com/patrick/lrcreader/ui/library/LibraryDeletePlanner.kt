package com.patrick.lrcreader.ui.library

import android.net.Uri
import com.patrick.lrcreader.core.LibraryIndexCache
import java.util.Locale

internal object LibraryDeletePlanner {

    data class AssociatedLrcMatch(
        val uriString: String,
        val role: LibraryDeleteRole,
        val displayName: String
    )

    private val audioExtensions = setOf(
        "mp3", "wav", "m4a", "aac", "flac", "ogg", "mp4", "mkv", "webm", "mov", "avi"
    )

    fun buildPlan(
        target: Uri,
        indexAll: List<LibraryIndexCache.CachedEntry>
    ): LibraryDeletePlan {
        val targetEntry = indexAll.firstOrNull { it.uriString == target.toString() }
        val targetName = targetEntry?.name ?: guessDisplayName(target)
        val isAudio = isAudioFileName(targetName)
        val targetItem = LibraryDeleteItem(
            uri = target,
            role = if (isAudio) LibraryDeleteRole.AUDIO else LibraryDeleteRole.FILE,
            displayName = targetName
        )

        if (!isAudio || targetEntry == null) {
            return LibraryDeletePlan(target = targetItem, associated = emptyList())
        }

        val associated = findAssociatedLrcMatches(
            targetUriString = target.toString(),
            indexAll = indexAll
        )
            .mapNotNull { match ->
                runCatching {
                    LibraryDeleteItem(
                        uri = Uri.parse(match.uriString),
                        role = match.role,
                        displayName = match.displayName
                    )
                }.getOrNull()
            }
            .distinctBy { it.uri.toString() }

        return LibraryDeletePlan(
            target = targetItem,
            associated = associated
        )
    }

    fun isAudioFileName(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return ext in audioExtensions
    }

    private fun findBackingTracksAncestorUri(
        targetEntry: LibraryIndexCache.CachedEntry,
        byUri: Map<String, LibraryIndexCache.CachedEntry>
    ): String? {
        var cursor = targetEntry.parentUriString
        while (!cursor.isNullOrBlank()) {
            val entry = byUri[cursor] ?: break
            if (isAlias(entry.name, setOf("backingtracks", "backingtrack"))) {
                return entry.uriString
            }
            cursor = entry.parentUriString
        }
        return null
    }

    fun findAssociatedLrcMatches(
        targetUriString: String,
        indexAll: List<LibraryIndexCache.CachedEntry>
    ): List<AssociatedLrcMatch> {
        val targetEntry = indexAll.firstOrNull { it.uriString == targetUriString } ?: return emptyList()
        if (targetEntry.isDirectory || !isAudioFileName(targetEntry.name)) return emptyList()

        val baseName = targetEntry.name.substringBeforeLast('.', targetEntry.name).trim()
        if (baseName.isBlank()) return emptyList()
        val wantedLrc = "$baseName.lrc"

        val byUri = indexAll.associateBy { it.uriString }
        val backingRootUri = findBackingTracksAncestorUri(targetEntry, byUri) ?: return emptyList()

        val childrenOfBacking = indexAll.filter {
            it.isDirectory && it.parentUriString == backingRootUri
        }
        val lyricsDirUris = childrenOfBacking
            .filter { isAlias(it.name, setOf("lyrics")) }
            .map { it.uriString }
            .toSet()
        val accordsDirUris = childrenOfBacking
            .filter { isAlias(it.name, setOf("accords")) }
            .map { it.uriString }
            .toSet()

        val associated = linkedMapOf<String, AssociatedLrcMatch>()
        findLrcInDirs(indexAll, lyricsDirUris, wantedLrc)?.let {
            associated[it.uriString] = it.copy(role = LibraryDeleteRole.LYRICS)
        }
        findLrcInDirs(indexAll, accordsDirUris, wantedLrc)?.let {
            associated[it.uriString] = it.copy(role = LibraryDeleteRole.ACCORDS)
        }
        return associated.values.toList()
    }

    private fun findLrcInDirs(
        indexAll: List<LibraryIndexCache.CachedEntry>,
        dirUris: Set<String>,
        wantedName: String
    ): AssociatedLrcMatch? {
        if (dirUris.isEmpty()) return null
        val hit = indexAll.firstOrNull { entry ->
            !entry.isDirectory &&
                entry.parentUriString in dirUris &&
                entry.name.equals(wantedName, ignoreCase = true)
        } ?: return null

        return AssociatedLrcMatch(
            uriString = hit.uriString,
            role = LibraryDeleteRole.FILE,
            displayName = hit.name
        )
    }

    private fun isAlias(name: String?, aliases: Set<String>): Boolean {
        val normalized = normalize(name)
        return normalized in aliases
    }

    private fun normalize(name: String?): String {
        return (name ?: "").trim().lowercase(Locale.ROOT)
    }

    private fun guessDisplayName(uri: Uri): String {
        val raw = uri.lastPathSegment.orEmpty().substringAfterLast('/')
        return raw.ifBlank { "fichier" }
    }
}
