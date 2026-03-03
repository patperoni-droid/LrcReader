package com.patrick.lrcreader.core.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchEngineTest {

    @Test
    fun test_alias_priority() {
        val items = listOf(
            SearchEngine.index(
                id = "uri-1",
                displayTitle = "Volare Live",
                fallbackName = "x345ax.mp3"
            )
        )

        val results = SearchEngine.filter(items, "volare")

        assertEquals(listOf("uri-1"), results.map { it.id })
    }

    @Test
    fun test_filename_fallback() {
        val items = listOf(
            SearchEngine.index(
                id = "uri-1",
                displayTitle = null,
                fallbackName = "Volare x345ax.mp3"
            )
        )

        val byName = SearchEngine.filter(items, "volare")
        val byBaseName = SearchEngine.filter(items, "x345ax")

        assertEquals(listOf("uri-1"), byName.map { it.id })
        assertEquals(listOf("uri-1"), byBaseName.map { it.id })
    }

    @Test
    fun test_accent_insensitive() {
        val items = listOf(
            SearchEngine.index(
                id = "uri-1",
                displayTitle = "Café del mar",
                fallbackName = "track.mp3"
            )
        )

        val accentInsensitive = SearchEngine.filter(items, "cafe")
        val accentSensitive = SearchEngine.filter(items, "cafe", removeAccents = false)

        assertEquals(listOf("uri-1"), accentInsensitive.map { it.id })
        assertTrue(accentSensitive.isEmpty())
    }

    @Test
    fun test_playlist_restrict() {
        val items = listOf(
            SearchEngine.index(id = "uri-1", displayTitle = "Volare", fallbackName = "a.mp3"),
            SearchEngine.index(id = "uri-2", displayTitle = "Bella", fallbackName = "b.mp3"),
            SearchEngine.index(id = "uri-3", displayTitle = "Amore", fallbackName = "c.mp3")
        )

        val restricted = SearchEngine.restrictToIds(items, setOf("uri-2"))
        val results = SearchEngine.filter(restricted, "a")

        assertEquals(listOf("uri-2"), results.map { it.id })
    }

    @Test
    fun test_blank_query() {
        val items = listOf(
            SearchEngine.index(id = "uri-1", displayTitle = "Volare", fallbackName = "a.mp3"),
            SearchEngine.index(id = "uri-2", displayTitle = "Bella", fallbackName = "b.mp3"),
            SearchEngine.index(id = "uri-3", displayTitle = "Amore", fallbackName = "c.mp3")
        )

        val restricted = SearchEngine.restrictToIds(items, setOf("uri-1", "uri-3"))
        val results = SearchEngine.filter(restricted, "   ")

        assertEquals(listOf("uri-1", "uri-3"), results.map { it.id })
    }
}
