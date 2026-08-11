package com.patrick.lrcreader.ui

import android.content.Context
import com.patrick.lrcreader.core.PlaybackRouter
import com.patrick.lrcreader.core.PlaylistRepository
import com.patrick.lrcreader.core.TextSongRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

class ScrollingTextCreationTest {

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
    fun `library creation adds catalog item without playlist assignment`() {
        PlaylistRepository.addPlaylist("Existing playlist")

        val created = createScrollingText(
            context = context,
            title = "  Mon texte  ",
            content = "  Première ligne  ",
            playlistName = null
        )

        assertNotNull(created)
        assertEquals(listOf("Mon texte"), TextSongRepository.listAll(context).map { it.title })
        assertTrue(PlaylistRepository.getAllSongsRaw("Existing playlist").isEmpty())
        val target = PlaybackRouter.resolve(created!!.uri, playlist = null)
        assertTrue(target is PlaybackRouter.Target.Prompter)
        assertEquals(created.id, (target as PlaybackRouter.Target.Prompter).id)
    }

    @Test
    fun `playlist creation keeps assigning new text to selected playlist`() {
        PlaylistRepository.addPlaylist("Set live")

        val created = createScrollingText(
            context = context,
            title = "Texte live",
            content = "Contenu live",
            playlistName = "Set live"
        )

        assertNotNull(created)
        assertTrue(PlaylistRepository.getAllSongsRaw("Set live").contains(created!!.uri))
    }

    @Test
    fun `blank title or content is rejected`() {
        assertNull(createScrollingText(context, "", "Contenu"))
        assertNull(createScrollingText(context, "Titre", "   "))
        assertTrue(TextSongRepository.listAll(context).isEmpty())
        assertFalse(PlaylistRepository.getPlaylists().isNotEmpty())
    }
}
