package com.patrick.lrcreader.core

import android.content.Context
import android.util.Log
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic

class BackupManagerPlaylistRestoreModeTest {

    private lateinit var logMock: MockedStatic<Log>

    @Before
    fun setUp() {
        logMock = mockStatic(Log::class.java)
        PlaylistRepository.clearAll()
    }

    @After
    fun tearDown() {
        PlaylistRepository.clearAll()
        logMock.close()
    }

    @Test
    fun mergePlaylists_keepsLocalPlaylistAndRenamesRestoredConflict() {
        val localSong = buildSmpItem("local-song")
        PlaylistRepository.addPlaylist("Concert")
        PlaylistRepository.assignSongToPlaylist("Concert", localSong, "local-song")

        BackupManager.importState(
            context = mock(Context::class.java),
            json = backupStateJson(
                playlistName = "Concert",
                songId = "backup-song"
            ),
            mergePlaylists = true
        )

        assertEquals(
            listOf("Concert", "Concert (restaurée)"),
            PlaylistRepository.getPlaylists()
        )
        assertEquals(
            listOf(localSong),
            PlaylistRepository.getAllSongsRaw("Concert")
        )
        assertEquals(
            listOf(buildSmpItem("backup-song")),
            PlaylistRepository.getAllSongsRaw("Concert (restaurée)")
        )
    }

    @Test
    fun replacePlaylists_clearsLocalPlaylistsAndUsesOriginalBackupName() {
        PlaylistRepository.addPlaylist("Locale")
        PlaylistRepository.assignSongToPlaylist(
            "Locale",
            buildSmpItem("local-song"),
            "local-song"
        )
        PlaylistRepository.addPlaylist("Concert")
        PlaylistRepository.assignSongToPlaylist(
            "Concert",
            buildSmpItem("old-concert-song"),
            "old-concert-song"
        )

        BackupManager.importState(
            context = mock(Context::class.java),
            json = backupStateJson(
                playlistName = "Concert",
                songId = "backup-song"
            ),
            mergePlaylists = false
        )

        assertEquals(listOf("Concert"), PlaylistRepository.getPlaylists())
        assertFalse(PlaylistRepository.getPlaylists().contains("Concert (restaurée)"))
        assertEquals(
            listOf(buildSmpItem("backup-song")),
            PlaylistRepository.getAllSongsRaw("Concert")
        )
    }

    @Test
    fun replacePlaylists_withEmptyBackupLeavesNoLocalPlaylist() {
        PlaylistRepository.addPlaylist("Locale")
        PlaylistRepository.assignSongToPlaylist(
            "Locale",
            buildSmpItem("local-song"),
            "local-song"
        )

        BackupManager.importState(
            context = mock(Context::class.java),
            json = """{"playlists": {}}""",
            mergePlaylists = false
        )

        assertEquals(emptyList<String>(), PlaylistRepository.getPlaylists())
    }

    private fun backupStateJson(
        playlistName: String,
        songId: String
    ): String {
        return """
            {
              "playlists": {
                "$playlistName": [
                  {
                    "uri": "${buildSmpItem(songId)}",
                    "songId": "$songId"
                  }
                ]
              }
            }
        """.trimIndent()
    }
}
