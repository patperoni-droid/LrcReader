package com.patrick.lrcreader.ui

import android.content.Context
import android.content.SharedPreferences
import com.patrick.lrcreader.core.PlaybackRouter
import com.patrick.lrcreader.core.PlaylistRepository
import com.patrick.lrcreader.core.TextPrompterAlignment
import com.patrick.lrcreader.core.TextPrompterDisplaySettings
import com.patrick.lrcreader.core.TextPrompterDisplaySettingsStore
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
            playlistName = "Set live"
        )

        assertNotNull(created)
        assertTrue(PlaylistRepository.getAllSongsRaw("Set live").contains(created!!.uri))
        assertTrue(storedPaletteValues.isEmpty())
    }

    @Test
    fun `creation needs no manual palette state`() {
        assertTrue(storedPaletteValues.isEmpty())

        val created = requireNotNull(
            createScrollingText(
                context = context,
                title = "Accords",
                content = "Je [Dmaj7]voulais [F#m7]te [C/E]dire"
            )
        )

        assertEquals(
            "Je [Dmaj7]voulais [F#m7]te [C/E]dire",
            TextSongRepository.get(context, created.id)?.content
        )
        assertTrue(storedPaletteValues.isEmpty())
    }

    @Test
    fun `creation preserves manual ChordPro without palette store entry`() {
        val created = requireNotNull(
            createScrollingText(
                context = context,
                title = "Saisie manuelle",
                content = "Je [Am]voulais te dire"
            )
        )

        assertEquals(
            "Je [Am]voulais te dire",
            TextSongRepository.get(context, created.id)?.content
        )
        assertTrue(storedPaletteValues.isEmpty())
    }

    @Test
    fun `center alignment is persisted only under created text id`() {
        assertTrue(storedPaletteValues.isEmpty())

        val created = requireNotNull(
            createScrollingText(
                context = context,
                title = "Texte centré",
                content = "Contenu",
                displaySettings = TextPrompterDisplaySettings(TextPrompterAlignment.CENTER)
            )
        )

        val key = requireNotNull(TextPrompterDisplaySettingsStore.textSongKey(created.id))
        assertEquals(
            TextPrompterAlignment.CENTER,
            TextPrompterDisplaySettingsStore.get(context, key).alignment
        )
        assertEquals(setOf("text:${created.id}"), storedPaletteValues.keys)
    }

    @Test
    fun `new text keeps start as implicit default`() {
        val created = requireNotNull(
            createScrollingText(context = context, title = "Texte", content = "Contenu")
        )

        val key = requireNotNull(TextPrompterDisplaySettingsStore.textSongKey(created.id))
        assertEquals(
            TextPrompterAlignment.START,
            TextPrompterDisplaySettingsStore.get(context, key).alignment
        )
        assertTrue(storedPaletteValues.isEmpty())
    }

    @Test
    fun `successive creations keep independent ChordPro content`() {
        val first = requireNotNull(
            createScrollingText(context, "Premier", "[Am]Texte [F]1")
        )
        val second = requireNotNull(
            createScrollingText(context, "Second", "[C/E]Texte 2")
        )

        assertEquals("[Am]Texte [F]1", TextSongRepository.get(context, first.id)?.content)
        assertEquals("[C/E]Texte 2", TextSongRepository.get(context, second.id)?.content)
        assertTrue(storedPaletteValues.isEmpty())
    }

    @Test
    fun `blank title or content is rejected`() {
        assertNull(createScrollingText(context, "", "Contenu"))
        assertNull(createScrollingText(context, "Titre", "   "))
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
