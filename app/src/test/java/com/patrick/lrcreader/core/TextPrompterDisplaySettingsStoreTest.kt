package com.patrick.lrcreader.core

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class TextPrompterDisplaySettingsStoreTest {

    @Test
    fun missingSettingsUseStartAlignment() {
        val context = contextWithPreferences(mutableMapOf())
        val key = requireNotNull(TextPrompterDisplaySettingsStore.textSongKey("missing"))

        assertEquals(
            TextPrompterDisplaySettings(),
            TextPrompterDisplaySettingsStore.get(context, key)
        )
        assertEquals(0, TextPrompterDisplaySettingsStore.get(context, key).transposeSemitones)
    }

    @Test
    fun positiveTranspositionSurvivesSaveAndReload() {
        val storedValues = mutableMapOf<String, String>()
        val key = requireNotNull(TextPrompterDisplaySettingsStore.textSongKey("text-1"))

        TextPrompterDisplaySettingsStore.saveTransposeSemitones(
            contextWithPreferences(storedValues),
            key,
            2
        )

        assertEquals(
            2,
            TextPrompterDisplaySettingsStore.get(
                contextWithPreferences(storedValues),
                key
            ).transposeSemitones
        )
    }

    @Test
    fun negativeTranspositionSurvivesSaveAndReload() {
        val storedValues = mutableMapOf<String, String>()
        val key = requireNotNull(TextPrompterDisplaySettingsStore.textSongKey("text-1"))

        TextPrompterDisplaySettingsStore.saveTransposeSemitones(
            contextWithPreferences(storedValues),
            key,
            -3
        )

        assertEquals(
            -3,
            TextPrompterDisplaySettingsStore.get(
                contextWithPreferences(storedValues),
                key
            ).transposeSemitones
        )
    }

    @Test
    fun textsKeepIndependentTranspositionValues() {
        val storedValues = mutableMapOf<String, String>()
        val context = contextWithPreferences(storedValues)
        val first = requireNotNull(TextPrompterDisplaySettingsStore.textSongKey("text-a"))
        val second = requireNotNull(TextPrompterDisplaySettingsStore.textSongKey("text-b"))

        TextPrompterDisplaySettingsStore.saveTransposeSemitones(context, first, 2)
        TextPrompterDisplaySettingsStore.saveTransposeSemitones(context, second, -3)

        assertEquals(2, TextPrompterDisplaySettingsStore.get(context, first).transposeSemitones)
        assertEquals(-3, TextPrompterDisplaySettingsStore.get(context, second).transposeSemitones)
    }

    @Test
    fun audioLyricsDefaultToZeroAndUseSongIdNamespace() {
        val context = contextWithPreferences(mutableMapOf())
        val key = requireNotNull(TextPrompterDisplaySettingsStore.audioLyricsSongKey("song-a"))

        assertEquals("audio-lyrics:song-a", key.storageKey)
        assertEquals(0, TextPrompterDisplaySettingsStore.get(context, key).transposeSemitones)
        assertEquals(null, TextPrompterDisplaySettingsStore.audioLyricsSongKey("   "))
    }

    @Test
    fun audioLyricsSongsKeepIndependentValuesAcrossNavigation() {
        val storedValues = mutableMapOf<String, String>()
        val context = contextWithPreferences(storedValues)
        val songA = requireNotNull(TextPrompterDisplaySettingsStore.audioLyricsSongKey("song-a"))
        val songB = requireNotNull(TextPrompterDisplaySettingsStore.audioLyricsSongKey("song-b"))

        TextPrompterDisplaySettingsStore.saveTransposeSemitones(context, songA, 1)
        TextPrompterDisplaySettingsStore.saveTransposeSemitones(context, songB, -1)

        assertEquals(1, TextPrompterDisplaySettingsStore.get(context, songA).transposeSemitones)
        assertEquals(-1, TextPrompterDisplaySettingsStore.get(context, songB).transposeSemitones)
        assertEquals(1, TextPrompterDisplaySettingsStore.get(context, songA).transposeSemitones)
    }

    @Test
    fun audioLyricsTranspositionIsClampedToSupportedLimits() {
        val storedValues = mutableMapOf<String, String>()
        val context = contextWithPreferences(storedValues)
        val upper = requireNotNull(TextPrompterDisplaySettingsStore.audioLyricsSongKey("upper"))
        val lower = requireNotNull(TextPrompterDisplaySettingsStore.audioLyricsSongKey("lower"))

        TextPrompterDisplaySettingsStore.saveTransposeSemitones(context, upper, 11)
        TextPrompterDisplaySettingsStore.saveTransposeSemitones(context, lower, -11)
        assertEquals(11, TextPrompterDisplaySettingsStore.get(context, upper).transposeSemitones)
        assertEquals(-11, TextPrompterDisplaySettingsStore.get(context, lower).transposeSemitones)

        TextPrompterDisplaySettingsStore.saveTransposeSemitones(context, upper, 12)
        TextPrompterDisplaySettingsStore.saveTransposeSemitones(context, lower, -12)
        assertEquals(11, TextPrompterDisplaySettingsStore.get(context, upper).transposeSemitones)
        assertEquals(-11, TextPrompterDisplaySettingsStore.get(context, lower).transposeSemitones)
    }

    @Test
    fun invalidAndOutOfRangeValuesLoadSafely() {
        val key = requireNotNull(TextPrompterDisplaySettingsStore.textSongKey("text-1"))

        assertEquals(
            0,
            TextPrompterDisplaySettingsStore.get(
                contextWithPreferences(
                    mutableMapOf("text:text-1" to "{\"transposeSemitones\":\"invalid\"}")
                ),
                key
            ).transposeSemitones
        )
        assertEquals(
            11,
            TextPrompterDisplaySettingsStore.get(
                contextWithPreferences(
                    mutableMapOf("text:text-1" to "{\"transposeSemitones\":99}")
                ),
                key
            ).transposeSemitones
        )
        assertEquals(
            -11,
            TextPrompterDisplaySettingsStore.get(
                contextWithPreferences(
                    mutableMapOf("text:text-1" to "{\"transposeSemitones\":-99}")
                ),
                key
            ).transposeSemitones
        )

        val storedValues = mutableMapOf<String, String>()
        val context = contextWithPreferences(storedValues)
        TextPrompterDisplaySettingsStore.saveTransposeSemitones(context, key, 99)
        assertEquals(11, TextPrompterDisplaySettingsStore.get(context, key).transposeSemitones)
    }

    @Test
    fun historicalAlignmentOnlyValueDefaultsTranspositionToZero() {
        val context = contextWithPreferences(
            mutableMapOf("text:text-1" to "{\"alignment\":\"CENTER\"}")
        )
        val key = requireNotNull(TextPrompterDisplaySettingsStore.textSongKey("text-1"))
        val settings = TextPrompterDisplaySettingsStore.get(context, key)

        assertEquals(TextPrompterAlignment.CENTER, settings.alignment)
        assertEquals(0, settings.transposeSemitones)
    }

    @Test
    fun returningToZeroRemovesDefaultOverride() {
        val storedValues = mutableMapOf<String, String>()
        val context = contextWithPreferences(storedValues)
        val key = requireNotNull(TextPrompterDisplaySettingsStore.textSongKey("text-1"))

        TextPrompterDisplaySettingsStore.saveTransposeSemitones(context, key, 2)
        TextPrompterDisplaySettingsStore.saveTransposeSemitones(context, key, 0)

        assertFalse(storedValues.containsKey("text:text-1"))
        assertEquals(0, TextPrompterDisplaySettingsStore.get(context, key).transposeSemitones)
    }

    @Test
    fun savingAlignmentPreservesExistingTransposition() {
        val storedValues = mutableMapOf<String, String>()
        val context = contextWithPreferences(storedValues)
        val key = requireNotNull(TextPrompterDisplaySettingsStore.textSongKey("text-1"))

        TextPrompterDisplaySettingsStore.saveTransposeSemitones(context, key, 2)
        TextPrompterDisplaySettingsStore.saveAlignment(
            context,
            key,
            TextPrompterAlignment.CENTER
        )

        assertEquals(
            TextPrompterDisplaySettings(TextPrompterAlignment.CENTER, 2),
            TextPrompterDisplaySettingsStore.get(context, key)
        )

        TextPrompterDisplaySettingsStore.saveTransposeSemitones(context, key, -3)

        assertEquals(
            TextPrompterDisplaySettings(TextPrompterAlignment.CENTER, -3),
            TextPrompterDisplaySettingsStore.get(context, key)
        )
    }

    @Test
    fun centerAlignmentSurvivesSaveAndReload() {
        val storedValues = mutableMapOf<String, String>()
        val key = requireNotNull(TextPrompterDisplaySettingsStore.textSongKey("text-1"))
        TextPrompterDisplaySettingsStore.save(
            contextWithPreferences(storedValues),
            key,
            TextPrompterDisplaySettings(TextPrompterAlignment.CENTER)
        )

        val reloaded = TextPrompterDisplaySettingsStore.get(
            contextWithPreferences(storedValues),
            key
        )

        assertEquals(TextPrompterAlignment.CENTER, reloaded.alignment)
    }

    @Test
    fun savingStartRemovesStoredOverrideAndRestoresDefault() {
        val storedValues = mutableMapOf<String, String>()
        val context = contextWithPreferences(storedValues)
        val key = requireNotNull(TextPrompterDisplaySettingsStore.textSongKey("text-1"))
        TextPrompterDisplaySettingsStore.save(
            context,
            key,
            TextPrompterDisplaySettings(TextPrompterAlignment.CENTER)
        )

        TextPrompterDisplaySettingsStore.save(context, key, TextPrompterDisplaySettings())

        assertFalse(storedValues.containsKey("text:text-1"))
        assertEquals(TextPrompterAlignment.START, TextPrompterDisplaySettingsStore.get(context, key).alignment)
    }

    @Test
    fun deleteRemovesStoredOverrideAndRestoresStart() {
        val storedValues = mutableMapOf<String, String>()
        val context = contextWithPreferences(storedValues)
        val key = requireNotNull(TextPrompterDisplaySettingsStore.textSongKey("text-1"))
        TextPrompterDisplaySettingsStore.save(
            context,
            key,
            TextPrompterDisplaySettings(TextPrompterAlignment.CENTER)
        )

        TextPrompterDisplaySettingsStore.delete(context, key)

        assertEquals(TextPrompterAlignment.START, TextPrompterDisplaySettingsStore.get(context, key).alignment)
    }

    @Test
    fun textSongsAndLegacyNotesUseIndependentNamespacedKeys() {
        val storedValues = mutableMapOf<String, String>()
        val context = contextWithPreferences(storedValues)
        val textKey = requireNotNull(TextPrompterDisplaySettingsStore.textSongKey("42"))
        val noteKey = TextPrompterDisplaySettingsStore.legacyNoteKey(42L)

        assertNotEquals(textKey, noteKey)
        TextPrompterDisplaySettingsStore.save(
            context,
            textKey,
            TextPrompterDisplaySettings(TextPrompterAlignment.CENTER)
        )
        TextPrompterDisplaySettingsStore.save(
            context,
            noteKey,
            TextPrompterDisplaySettings(TextPrompterAlignment.CENTER)
        )

        assertTrue(storedValues.containsKey("text:42"))
        assertTrue(storedValues.containsKey("note:42"))
    }

    @Test
    fun invalidStoredValueFallsBackToStart() {
        val context = contextWithPreferences(
            mutableMapOf("text:text-1" to "{\"alignment\":\"UNKNOWN\"}")
        )
        val key = requireNotNull(TextPrompterDisplaySettingsStore.textSongKey("text-1"))

        assertEquals(TextPrompterAlignment.START, TextPrompterDisplaySettingsStore.get(context, key).alignment)
    }

    @Test
    fun blankTextSongIdIsRejected() {
        assertEquals(null, TextPrompterDisplaySettingsStore.textSongKey(""))
        assertEquals(null, TextPrompterDisplaySettingsStore.textSongKey("   "))
        assertNotNull(TextPrompterDisplaySettingsStore.textSongKey(" text-id "))
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
        Mockito.`when`(editor.clear()).thenAnswer {
            storedValues.clear()
            editor
        }

        return context
    }
}
