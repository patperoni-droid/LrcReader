package com.patrick.lrcreader.ui.library

import android.content.Context
import android.content.SharedPreferences
import com.patrick.lrcreader.core.PlaylistRepository
import com.patrick.lrcreader.core.TextPrompterAlignment
import com.patrick.lrcreader.core.TextPrompterDisplaySettings
import com.patrick.lrcreader.core.TextPrompterDisplaySettingsStore
import com.patrick.lrcreader.core.TextSongRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

class LibraryPrompterDeletionTest {

    private lateinit var context: Context
    private val storedDisplaySettings = mutableMapOf<String, String>()

    @Before
    fun setUp() {
        context = Mockito.mock(Context::class.java)
        val preferences = Mockito.mock(SharedPreferences::class.java)
        val editor = Mockito.mock(SharedPreferences.Editor::class.java)
        Mockito.`when`(context.getSharedPreferences(Mockito.anyString(), Mockito.anyInt()))
            .thenReturn(preferences)
        Mockito.`when`(preferences.getString(Mockito.anyString(), Mockito.isNull()))
            .thenAnswer { invocation -> storedDisplaySettings[invocation.getArgument(0)] }
        Mockito.`when`(preferences.edit()).thenReturn(editor)
        Mockito.`when`(editor.putString(Mockito.anyString(), Mockito.anyString()))
            .thenAnswer { invocation ->
                storedDisplaySettings[invocation.getArgument(0)] = invocation.getArgument(1)
                editor
            }
        Mockito.`when`(editor.remove(Mockito.anyString()))
            .thenAnswer { invocation ->
                storedDisplaySettings.remove(invocation.getArgument(0))
                editor
            }
        PlaylistRepository.clearAll()
        TextSongRepository.setInMemoryOnlyForTests(true)
        TextSongRepository.clearAll(context)
    }

    @After
    fun tearDown() {
        storedDisplaySettings.clear()
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
        val displaySettingsKey = requireNotNull(
            TextPrompterDisplaySettingsStore.textSongKey(id)
        )
        TextPrompterDisplaySettingsStore.save(
            context,
            displaySettingsKey,
            TextPrompterDisplaySettings(TextPrompterAlignment.CENTER)
        )

        val deleted = deletePrompterAndRemoveFromAllPlaylists(context, prompterUri)

        assertTrue(deleted)
        assertNull(TextSongRepository.get(context, id))
        assertFalse(PlaylistRepository.getAllSongsRaw("Playlist A").contains(prompterUri))
        assertFalse(PlaylistRepository.getAllSongsRaw("Playlist B").contains(prompterUri))
        assertTrue(PlaylistRepository.getAllSongsRaw("Playlist B").contains(otherTrack))
        assertEquals(
            TextPrompterAlignment.START,
            TextPrompterDisplaySettingsStore.get(context, displaySettingsKey).alignment
        )
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
