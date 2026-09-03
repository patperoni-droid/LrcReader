package com.patrick.lrcreader.ui

import android.content.Context
import android.content.SharedPreferences
import com.patrick.lrcreader.core.PlaybackRouter
import com.patrick.lrcreader.core.PlaylistRepository
import com.patrick.lrcreader.core.TextPrompterChordPaletteStore
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
    private val storedPaletteValues = mutableMapOf<String, String>()

    @Before
    fun setUp() {
        storedPaletteValues.clear()
        context = contextWithPreferences(storedPaletteValues)
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
            paletteChords = listOf("Am", "F#m7", "C/E"),
            playlistName = "Set live"
        )

        assertNotNull(created)
        assertTrue(PlaylistRepository.getAllSongsRaw("Set live").contains(created!!.uri))
        val key = requireNotNull(TextPrompterChordPaletteStore.textSongKey(created.id))
        assertEquals(
            listOf("Am", "F#m7", "C/E"),
            TextPrompterChordPaletteStore.get(context, key)
        )
    }

    @Test
    fun `creation persists complex palette only under real text id`() {
        assertTrue(storedPaletteValues.isEmpty())

        val created = requireNotNull(
            createScrollingText(
                context = context,
                title = "Accords",
                content = "Je voulais te dire",
                paletteChords = listOf("Dmaj7", "F#m7", "C/E")
            )
        )

        val key = requireNotNull(TextPrompterChordPaletteStore.textSongKey(created.id))
        assertEquals(
            listOf("Dmaj7", "F#m7", "C/E"),
            TextPrompterChordPaletteStore.get(context, key)
        )
        assertEquals(setOf("text:${created.id}"), storedPaletteValues.keys)
    }

    @Test
    fun `empty palette creates no store entry and preserves manual ChordPro`() {
        val created = requireNotNull(
            createScrollingText(
                context = context,
                title = "Saisie manuelle",
                content = "Je [Am]voulais te dire",
                paletteChords = emptyList()
            )
        )

        assertEquals(
            "Je [Am]voulais te dire",
            TextSongRepository.get(context, created.id)?.content
        )
        assertTrue(storedPaletteValues.isEmpty())
    }

    @Test
    fun `successive creations keep independent palette drafts`() {
        val first = requireNotNull(
            createScrollingText(context, "Premier", "Texte 1", listOf("Am", "F"))
        )
        val second = requireNotNull(
            createScrollingText(context, "Second", "Texte 2", listOf("C/E"))
        )

        assertEquals(
            listOf("Am", "F"),
            TextPrompterChordPaletteStore.get(
                context,
                requireNotNull(TextPrompterChordPaletteStore.textSongKey(first.id))
            )
        )
        assertEquals(
            listOf("C/E"),
            TextPrompterChordPaletteStore.get(
                context,
                requireNotNull(TextPrompterChordPaletteStore.textSongKey(second.id))
            )
        )
    }

    @Test
    fun `blank title or content is rejected`() {
        assertNull(createScrollingText(context, "", "Contenu", listOf("Am")))
        assertNull(createScrollingText(context, "Titre", "   ", listOf("F")))
        assertTrue(TextSongRepository.listAll(context).isEmpty())
        assertFalse(PlaylistRepository.getPlaylists().isNotEmpty())
        assertTrue(storedPaletteValues.isEmpty())
    }

    private fun contextWithPreferences(
        storedValues: MutableMap<String, String>
    ): Context {
        val context = Mockito.mock(Context::class.java)
        val preferences = Mockito.mock(SharedPreferences::class.java)
        val editor = Mockito.mock(SharedPreferences.Editor::class.java)

        Mockito.`when`(context.getSharedPreferences(Mockito.anyString(), Mockito.anyInt()))
            .thenReturn(preferences)
        Mockito.`when`(preferences.getString(Mockito.anyString(), Mockito.isNull()))
            .thenAnswer { invocation -> storedValues[invocation.getArgument(0)] }
        Mockito.`when`(preferences.edit()).thenReturn(editor)
        Mockito.`when`(editor.putString(Mockito.anyString(), Mockito.anyString()))
            .thenAnswer { invocation ->
                storedValues[invocation.getArgument(0)] = invocation.getArgument(1)
                editor
            }
        Mockito.`when`(editor.remove(Mockito.anyString()))
            .thenAnswer { invocation ->
                storedValues.remove(invocation.getArgument(0))
                editor
            }

        return context
    }
}
