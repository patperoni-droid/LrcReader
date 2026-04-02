package com.patrick.lrcreader.core

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun played_state_survives_songId_upgrade_from_legacy_item() {
        val playlistName = "Playlist"
        val smpUri = buildSmpItem("song_004")

        PlaylistRepository.addPlaylist(playlistName)
        PlaylistRepository.assignSongToPlaylist(playlistName, smpUri)
        PlaylistRepository.markSongPlayed(playlistName, smpUri)

        assertTrue(PlaylistRepository.isSongPlayed(playlistName, smpUri))

        PlaylistRepository.assignSongToPlaylist(
            playlistName = playlistName,
            songUri = smpUri,
            songId = "song_004"
        )

        assertTrue(PlaylistRepository.isSongPlayed(playlistName, smpUri))
        assertEquals(listOf(smpUri), PlaylistRepository.getPlayedRaw(playlistName))
    }

    @Test
    fun review_state_survives_uri_replace_when_songId_is_present() {
        val playlistName = "Playlist"
        val oldUri = "file:///tracks/legacy-review-a.mp3"
        val newUri = "file:///tracks/legacy-review-b.mp3"

        PlaylistRepository.addPlaylist(playlistName)
        PlaylistRepository.assignSongToPlaylist(
            playlistName = playlistName,
            songUri = oldUri,
            songId = "song_005"
        )
        PlaylistRepository.setSongToReview(playlistName, oldUri, true)

        PlaylistRepository.replaceSongUriEverywhere(oldUri, newUri)

        assertEquals("song_005", PlaylistRepository.getPlaylistItem(playlistName, newUri)?.songId)
        assertTrue(PlaylistRepository.isSongToReview(playlistName, newUri))
        assertFalse(PlaylistRepository.isSongToReview(playlistName, oldUri))
    }

    @Test
    fun custom_title_survives_uri_replace_when_songId_is_present() {
        val playlistName = "Playlist"
        val oldUri = "file:///tracks/legacy-title-a.mp3"
        val newUri = "file:///tracks/legacy-title-b.mp3"

        PlaylistRepository.addPlaylist(playlistName)
        PlaylistRepository.assignSongToPlaylist(
            playlistName = playlistName,
            songUri = oldUri,
            songId = "song_006"
        )
        PlaylistRepository.renameSongInPlaylist(playlistName, oldUri, "My SMP Title")

        PlaylistRepository.replaceSongUriEverywhere(oldUri, newUri)

        assertEquals("My SMP Title", PlaylistRepository.getCustomTitle(playlistName, newUri))
        assertEquals("My SMP Title", PlaylistRepository.getAnyCustomTitleForUri(newUri))
    }

    @Test
    fun legacy_states_still_work_without_songId() {
        val playlistName = "Playlist"
        val legacyUri = "file:///tracks/plain-legacy.mp3"

        PlaylistRepository.addPlaylist(playlistName)
        PlaylistRepository.assignSongToPlaylist(playlistName, legacyUri)
        PlaylistRepository.markSongPlayed(playlistName, legacyUri)
        PlaylistRepository.setSongToReview(playlistName, legacyUri, true)
        PlaylistRepository.renameSongInPlaylist(playlistName, legacyUri, "Legacy Title")

        assertTrue(PlaylistRepository.isSongPlayed(playlistName, legacyUri))
        assertTrue(PlaylistRepository.isSongToReview(playlistName, legacyUri))
        assertEquals("Legacy Title", PlaylistRepository.getCustomTitle(playlistName, legacyUri))
        assertEquals(listOf(legacyUri), PlaylistRepository.getPlayedRaw(playlistName))
    }
}
