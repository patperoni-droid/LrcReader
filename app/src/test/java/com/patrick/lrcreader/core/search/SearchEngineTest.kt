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

    @Test
    fun test_playlist_restrict_fallback_for_missing_ids() {
        val items = listOf(
            SearchEngine.index(id = "uri-1", displayTitle = "Volare", fallbackName = "a.mp3")
        )

        val restricted = SearchEngine.restrictWithFallbackIds(
            items = items,
            allowedIds = setOf("uri-1", "uri-missing")
        ) { id ->
            if (id == "uri-missing") "Bella Ciao.mp3" else id
        }
        val results = SearchEngine.filter(restricted, "bella")

        assertEquals(listOf("uri-missing"), results.map { it.id })
    }

    @Test
    fun test_is_playable_audio_file_filters_non_audio() {
        assertTrue(
            SearchEngine.isPlayableAudioFile(
                id = "content://tree/primary%3ABackingTracks%2FAudio%2FTrack.mp3",
                fallbackName = "Track.mp3"
            )
        )
        assertTrue(
            SearchEngine.isPlayableAudioFile(
                id = "content://tree/primary%3ABackingTracks%2FAudio%2FTrack.wav",
                fallbackName = "Track"
            )
        )
        assertTrue(
            !SearchEngine.isPlayableAudioFile(
                id = "content://tree/primary%3ABackingTracks%2FLyrics%2FTrack.lrc",
                fallbackName = "Track.lrc"
            )
        )
        assertTrue(
            !SearchEngine.isPlayableAudioFile(
                id = "content://tree/primary%3ABackingTracks%2FConfig%2Fbackup.json",
                fallbackName = "backup.json"
            )
        )
        assertTrue(
            !SearchEngine.isPlayableAudioFile(
                id = "content://tree/primary%3ABackingTracks%2FBackups%2Fexport.zip",
                fallbackName = "export.zip"
            )
        )
    }
}
