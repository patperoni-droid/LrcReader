package com.patrick.lrcreader.core

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class TextPrompterChordPaletteStoreTest {

    @Test
    fun unknownKeyReturnsEmptyList() {
        val context = contextWithPreferences(mutableMapOf())
        val key = requireNotNull(TextPrompterChordPaletteStore.textSongKey("missing"))

        assertEquals(emptyList<String>(), TextPrompterChordPaletteStore.get(context, key))
    }

    @Test
    fun saveNormalizesWhilePreservingOrderAndComplexChords() {
        val context = contextWithPreferences(mutableMapOf())
        val key = requireNotNull(TextPrompterChordPaletteStore.textSongKey("text-1"))

        TextPrompterChordPaletteStore.save(
            context,
            key,
            listOf(" Am ", "F#m7", "", "Bb", "Am", " C/E ", "G7sus4", "   ")
        )

        assertEquals(
            listOf("Am", "F#m7", "Bb", "C/E", "G7sus4"),
            TextPrompterChordPaletteStore.get(context, key)
        )
    }

    @Test
    fun savePreservesCaseAndDoesNotApplyEnharmonicTransformation() {
        val context = contextWithPreferences(mutableMapOf())
        val key = requireNotNull(TextPrompterChordPaletteStore.textSongKey("text-1"))

        TextPrompterChordPaletteStore.save(context, key, listOf("Am", "am", "Bb", "A#"))

        assertEquals(
            listOf("Am", "am", "Bb", "A#"),
            TextPrompterChordPaletteStore.get(context, key)
        )
    }

    @Test
    fun updateReplacesPreviousPaletteAndEmptySaveDeletesIt() {
        val storedValues = mutableMapOf<String, String>()
        val context = contextWithPreferences(storedValues)
        val key = requireNotNull(TextPrompterChordPaletteStore.textSongKey("text-1"))

        TextPrompterChordPaletteStore.save(context, key, listOf("C", "F", "G"))
        TextPrompterChordPaletteStore.save(context, key, listOf("Dm", "A7"))
        assertEquals(listOf("Dm", "A7"), TextPrompterChordPaletteStore.get(context, key))

        TextPrompterChordPaletteStore.save(context, key, listOf("", "  "))
        assertEquals(emptyList<String>(), TextPrompterChordPaletteStore.get(context, key))
        assertFalse(storedValues.containsKey("text:text-1"))
    }

    @Test
    fun deleteRemovesOnlyRequestedPalette() {
        val context = contextWithPreferences(mutableMapOf())
        val first = requireNotNull(TextPrompterChordPaletteStore.textSongKey("text-1"))
        val second = requireNotNull(TextPrompterChordPaletteStore.textSongKey("text-2"))
        TextPrompterChordPaletteStore.save(context, first, listOf("C"))
        TextPrompterChordPaletteStore.save(context, second, listOf("D"))

        TextPrompterChordPaletteStore.delete(context, first)

        assertEquals(emptyList<String>(), TextPrompterChordPaletteStore.get(context, first))
        assertEquals(listOf("D"), TextPrompterChordPaletteStore.get(context, second))
    }

    @Test
    fun clearAllRemovesEveryPalette() {
        val storedValues = mutableMapOf<String, String>()
        val context = contextWithPreferences(storedValues)
        val textKey = requireNotNull(TextPrompterChordPaletteStore.textSongKey("42"))
        val noteKey = TextPrompterChordPaletteStore.legacyNoteKey(42L)
        TextPrompterChordPaletteStore.save(context, textKey, listOf("C"))
        TextPrompterChordPaletteStore.save(context, noteKey, listOf("D"))

        TextPrompterChordPaletteStore.clearAll(context)

        assertTrue(storedValues.isEmpty())
        assertEquals(emptyList<String>(), TextPrompterChordPaletteStore.get(context, textKey))
        assertEquals(emptyList<String>(), TextPrompterChordPaletteStore.get(context, noteKey))
    }

    @Test
    fun textSongsAndLegacyNotesUseIndependentNamespacedKeys() {
        val storedValues = mutableMapOf<String, String>()
        val context = contextWithPreferences(storedValues)
        val firstText = requireNotNull(TextPrompterChordPaletteStore.textSongKey("42"))
        val secondText = requireNotNull(TextPrompterChordPaletteStore.textSongKey("43"))
        val legacyNote = TextPrompterChordPaletteStore.legacyNoteKey(42L)

        TextPrompterChordPaletteStore.save(context, firstText, listOf("C"))
        TextPrompterChordPaletteStore.save(context, secondText, listOf("D"))
        TextPrompterChordPaletteStore.save(context, legacyNote, listOf("E"))

        assertEquals(listOf("C"), TextPrompterChordPaletteStore.get(context, firstText))
        assertEquals(listOf("D"), TextPrompterChordPaletteStore.get(context, secondText))
        assertEquals(listOf("E"), TextPrompterChordPaletteStore.get(context, legacyNote))
        assertTrue(storedValues.containsKey("text:42"))
        assertTrue(storedValues.containsKey("text:43"))
        assertTrue(storedValues.containsKey("note:42"))
    }

    @Test
    fun blankTextSongIdIsRejected() {
        assertEquals(null, TextPrompterChordPaletteStore.textSongKey(""))
        assertEquals(null, TextPrompterChordPaletteStore.textSongKey("   "))
        assertNotNull(TextPrompterChordPaletteStore.textSongKey(" text-id "))
    }

    @Test
    fun paletteCanBeReadFromARecreatedContext() {
        val storedValues = mutableMapOf<String, String>()
        val firstContext = contextWithPreferences(storedValues)
        val key = requireNotNull(TextPrompterChordPaletteStore.textSongKey("text-1"))
        TextPrompterChordPaletteStore.save(firstContext, key, listOf("Am", "F"))

        val recreatedContext = contextWithPreferences(storedValues)

        assertEquals(
            listOf("Am", "F"),
            TextPrompterChordPaletteStore.get(recreatedContext, key)
        )
    }

    @Test
    fun sameTextSongIdentitySharesPaletteAcrossLibraryAndPlaylistEntryPoints() {
        val context = contextWithPreferences(mutableMapOf())
        val libraryKey = requireNotNull(
            TextPrompterChordPaletteStore.textSongKey("shared-text-id")
        )
        val playlistKey = requireNotNull(
            TextPrompterChordPaletteStore.textSongKey("shared-text-id")
        )

        TextPrompterChordPaletteStore.save(context, libraryKey, listOf("Am", "F", "C", "G"))

        assertEquals(
            listOf("Am", "F", "C", "G"),
            TextPrompterChordPaletteStore.get(context, playlistKey)
        )
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
            .thenAnswer { invocation ->
                storedValues[invocation.getArgument(0)]
            }
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
        Mockito.`when`(editor.clear()).thenAnswer {
            storedValues.clear()
            editor
        }

        return context
    }
}
