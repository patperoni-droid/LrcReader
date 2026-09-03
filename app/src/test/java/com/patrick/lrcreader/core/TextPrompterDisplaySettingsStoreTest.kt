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
