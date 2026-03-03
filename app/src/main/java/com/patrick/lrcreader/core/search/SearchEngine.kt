package com.patrick.lrcreader.core.search

import java.text.Normalizer

object SearchEngine {

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
        return items.filter { item ->
            val haystack = if (removeAccents) {
                item.normalizedSearchText
            } else {
                normalize(item.searchText, removeAccents = false)
            }
            haystack.contains(normalizedQuery)
        }
    }

    fun restrictToIds(
        items: List<IndexedItem>,
        allowedIds: Set<String>?
    ): List<IndexedItem> {
        if (allowedIds == null) return items
        return items.filter { it.id in allowedIds }
    }
}
