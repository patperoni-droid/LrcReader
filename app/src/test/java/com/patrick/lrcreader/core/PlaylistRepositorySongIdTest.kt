package com.patrick.lrcreader.core

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class PlaylistRepositorySongIdTest {

    @Before
    fun setUp() {
        PlaylistRepository.clearAll()
    }

    @After
    fun tearDown() {
        PlaylistRepository.clearAll()
    }

    @Test
    fun legacy_assignment_keeps_songId_null() {
        val playlistName = "Playlist"
        val smpUri = buildSmpItem("song_001")

        PlaylistRepository.addPlaylist(playlistName)
        PlaylistRepository.assignSongToPlaylist(playlistName, smpUri)

        assertEquals(listOf(smpUri), PlaylistRepository.getAllSongsRaw(playlistName))
        assertEquals(
            listOf(PlaylistItem(uri = smpUri, songId = null)),
            PlaylistRepository.getAllItemsRaw(playlistName)
        )
    }

    @Test
    fun explicit_smp_assignment_stores_songId() {
        val playlistName = "Playlist"
        val smpUri = buildSmpItem("song_002")

        PlaylistRepository.addPlaylist(playlistName)
        PlaylistRepository.assignSongToPlaylist(
            playlistName = playlistName,
            songUri = smpUri,
            songId = "song_002"
        )

        assertEquals(listOf(smpUri), PlaylistRepository.getAllSongsRaw(playlistName))
        assertEquals("song_002", PlaylistRepository.getPlaylistItem(playlistName, smpUri)?.songId)
        assertEquals(
            listOf(PlaylistItem(uri = smpUri, songId = "song_002")),
            PlaylistRepository.getItemsFor(playlistName)
        )
    }

    @Test
    fun explicit_songId_updates_existing_legacy_item_without_duplicate() {
        val playlistName = "Playlist"
        val smpUri = buildSmpItem("song_003")

        PlaylistRepository.addPlaylist(playlistName)
        PlaylistRepository.assignSongToPlaylist(playlistName, smpUri)
        assertNull(PlaylistRepository.getPlaylistItem(playlistName, smpUri)?.songId)

        PlaylistRepository.assignSongToPlaylist(
            playlistName = playlistName,
            songUri = smpUri,
            songId = "song_003"
        )

        assertEquals(listOf(smpUri), PlaylistRepository.getAllSongsRaw(playlistName))
        assertEquals("song_003", PlaylistRepository.getPlaylistItem(playlistName, smpUri)?.songId)
    }
}
