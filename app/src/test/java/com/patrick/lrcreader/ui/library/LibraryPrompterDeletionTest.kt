package com.patrick.lrcreader.ui.library

import android.content.Context
import com.patrick.lrcreader.core.PlaylistRepository
import com.patrick.lrcreader.core.TextSongRepository
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

class LibraryPrompterDeletionTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = Mockito.mock(Context::class.java)
        PlaylistRepository.clearAll()
        TextSongRepository.setInMemoryOnlyForTests(true)
        TextSongRepository.clearAll(context)
    }

    @After
    fun tearDown() {
        TextSongRepository.clearAll(context)
        TextSongRepository.setInMemoryOnlyForTests(false)
        PlaylistRepository.clearAll()
    }

    @Test
    fun deletePrompter_removesFromRepository_and_allPlaylists() {
        val id = TextSongRepository.create(context, "Texte test", "Contenu test")
        val prompterUri = TextSongRepository.resolvePrompterUri(id)
        val otherTrack = "content://media/external/audio/42"

        PlaylistRepository.addPlaylist("Playlist A")
        PlaylistRepository.addPlaylist("Playlist B")
        PlaylistRepository.assignSongToPlaylist("Playlist A", prompterUri)
        PlaylistRepository.assignSongToPlaylist("Playlist B", otherTrack)
        PlaylistRepository.assignSongToPlaylist("Playlist B", prompterUri)

        val deleted = deletePrompterAndRemoveFromAllPlaylists(context, prompterUri)

        assertTrue(deleted)
        assertNull(TextSongRepository.get(context, id))
        assertFalse(PlaylistRepository.getAllSongsRaw("Playlist A").contains(prompterUri))
        assertFalse(PlaylistRepository.getAllSongsRaw("Playlist B").contains(prompterUri))
        assertTrue(PlaylistRepository.getAllSongsRaw("Playlist B").contains(otherTrack))
    }

    @Test
    fun deleteMultiplePrompters_reusesDeletionPipeline_forEachSelection() {
        val firstId = TextSongRepository.create(context, "Premier texte", "Premier contenu")
        val secondId = TextSongRepository.create(context, "Deuxième texte", "Deuxième contenu")
        val retainedId = TextSongRepository.create(context, "Texte conservé", "Contenu conservé")
        val firstUri = TextSongRepository.resolvePrompterUri(firstId)
        val secondUri = TextSongRepository.resolvePrompterUri(secondId)
        val retainedUri = TextSongRepository.resolvePrompterUri(retainedId)

        PlaylistRepository.addPlaylist("Playlist A")
        PlaylistRepository.addPlaylist("Playlist B")
        PlaylistRepository.assignSongToPlaylist("Playlist A", firstUri)
        PlaylistRepository.assignSongToPlaylist("Playlist A", retainedUri)
        PlaylistRepository.assignSongToPlaylist("Playlist B", secondUri)

        setOf(firstUri, secondUri).forEach { uri ->
            assertTrue(deletePrompterAndRemoveFromAllPlaylists(context, uri))
        }

        assertNull(TextSongRepository.get(context, firstId))
        assertNull(TextSongRepository.get(context, secondId))
        assertTrue(TextSongRepository.get(context, retainedId) != null)
        assertFalse(PlaylistRepository.getAllSongsRaw("Playlist A").contains(firstUri))
        assertFalse(PlaylistRepository.getAllSongsRaw("Playlist B").contains(secondUri))
        assertTrue(PlaylistRepository.getAllSongsRaw("Playlist A").contains(retainedUri))
    }
}
