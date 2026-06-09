package com.patrick.lrcreader.core.search

import java.text.Normalizer
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object SearchEngine {

    private val AUDIO_EXTENSIONS = setOf(
        ".mp3", ".wav", ".flac", ".m4a", ".aac", ".ogg", ".opus", ".wma", ".alac", ".aiff"
    )

    data class IndexedItem(
        val id: String,
        val displayTitle: String,
        val fallbackName: String,
        val searchText: String,
        val normalizedSearchText: String
    )

    fun index(
        id: String,
        displayTitle: String?,
        fallbackName: String
    ): IndexedItem {
        val cleanFallback = fallbackName.trim()
        val cleanDisplay = displayTitle?.trim().takeUnless { it.isNullOrBlank() } ?: cleanFallback
        val baseName = cleanFallback.substringBeforeLast('.', cleanFallback).trim()
        val text = listOf(cleanDisplay, cleanFallback, baseName)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" ")
        return IndexedItem(
            id = id,
            displayTitle = cleanDisplay,
            fallbackName = cleanFallback,
            searchText = text,
            normalizedSearchText = normalize(text)
        )
    }

    fun normalize(value: String, removeAccents: Boolean = true): String {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return ""
        val collapsed = trimmed.replace(Regex("\\s+"), " ")
        val lowered = collapsed.lowercase()
        if (!removeAccents) return lowered
        val decomposed = Normalizer.normalize(lowered, Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{M}+"), "")
    }

    fun filter(
        items: List<IndexedItem>,
        query: String,
        removeAccents: Boolean = true
    ): List<IndexedItem> {
        val normalizedQuery = normalize(query, removeAccents)
        if (normalizedQuery.isBlank()) return items
        return items
            .asSequence()
            .mapNotNull { item ->
                val haystack = if (removeAccents) {
                    item.normalizedSearchText
                } else {
                    normalize(item.searchText, removeAccents = false)
                }
                val matchIndex = haystack.indexOf(normalizedQuery)
                if (matchIndex < 0) {
                    null
                } else {
                    val title = normalize(item.displayTitle, removeAccents)
                    val titleIndex = title.indexOf(normalizedQuery)
                    val rank = when {
                        title.startsWith(normalizedQuery) -> 0
                        titleIndex >= 0 -> 1
                        matchIndex == 0 -> 2
                        else -> 3
                    }
                    SearchRank(item = item, rank = rank, matchIndex = minOf(titleIndex.takeIf { it >= 0 } ?: Int.MAX_VALUE, matchIndex))
                }
            }
            .sortedWith(
                compareBy<SearchRank> { it.rank }
                    .thenBy { it.matchIndex }
                    .thenBy { it.item.displayTitle.lowercase() }
            )
            .map { it.item }
            .toList()
    }

    private data class SearchRank(
        val item: IndexedItem,
        val rank: Int,
        val matchIndex: Int
    )

    fun restrictToIds(
        items: List<IndexedItem>,
        allowedIds: Set<String>?
    ): List<IndexedItem> {
        if (allowedIds == null) return items
        return items.filter { it.id in allowedIds }
    }

    /**
     * Restrict to [allowedIds], and synthesize missing ids when the index source is incomplete.
     * This keeps restricted search usable even if some playlist tracks are absent from index cache.
     */
    fun restrictWithFallbackIds(
        items: List<IndexedItem>,
        allowedIds: Set<String>?,
        fallbackNameForId: (String) -> String
    ): List<IndexedItem> {
        if (allowedIds == null) return items
        val restricted = restrictToIds(items, allowedIds)
        if (restricted.size == allowedIds.size) return restricted

        val out = ArrayList<IndexedItem>(restricted.size + allowedIds.size)
        out.addAll(restricted)
        val seen = restricted.asSequence().map { it.id }.toHashSet()
        allowedIds.forEach { id ->
            if (seen.add(id)) {
                val fallback = fallbackNameForId(id).trim().ifBlank { id }
                out.add(index(id = id, displayTitle = null, fallbackName = fallback))
            }
        }
        return out
    }

    fun isPlayableAudioFile(id: String, fallbackName: String): Boolean {
        val cleanName = fallbackName.trim().lowercase()
        if (AUDIO_EXTENSIONS.any { cleanName.endsWith(it) }) return true

        val decodedId = runCatching {
            URLDecoder.decode(id, StandardCharsets.UTF_8.name())
        }.getOrElse { id }.lowercase()
        val tail = decodedId.substringAfterLast('/').substringAfterLast(':')
        return AUDIO_EXTENSIONS.any { tail.endsWith(it) }
    }
}
