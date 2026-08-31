package com.patrick.lrcreader.core

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class ChordPaletteStoreTest {

    @Test
    fun palettesAreIsolatedBySongIdAndLegacyEntryIsUntouched() {
        val storedValues = mutableMapOf(
            "palette_lyrics.lrc" to "LEGACY_GLOBAL_PALETTE"
        )
        val context = contextWithPreferences(storedValues)

        ChordPaletteStore.saveRaw(context, "song-a", "C, Am, F, G")
        ChordPaletteStore.saveRaw(context, "song-b", "D, Bm, G, A")

        assertEquals("C, Am, F, G", ChordPaletteStore.loadRaw(context, "song-a"))
        assertEquals("D, Bm, G, A", ChordPaletteStore.loadRaw(context, "song-b"))
        assertEquals("", ChordPaletteStore.loadRaw(context, "song-c"))
        assertEquals("C, Am, F, G", ChordPaletteStore.loadRaw(context, "song-a"))
        assertEquals("", ChordPaletteStore.loadRaw(context, "song-c"))
        assertEquals("D, Bm, G, A", ChordPaletteStore.loadRaw(context, "song-b"))

        ChordPaletteStore.saveRaw(context, "song-a", "C, F")
        assertEquals("D, Bm, G, A", ChordPaletteStore.loadRaw(context, "song-b"))

        ChordPaletteStore.clear(context, "song-c")
        assertEquals("C, F", ChordPaletteStore.loadRaw(context, "song-a"))
        assertEquals("D, Bm, G, A", ChordPaletteStore.loadRaw(context, "song-b"))

        ChordPaletteStore.clear(context, "song-a")
        assertEquals("", ChordPaletteStore.loadRaw(context, "song-a"))
        assertEquals("D, Bm, G, A", ChordPaletteStore.loadRaw(context, "song-b"))

        val beforeInvalidWrites = storedValues.toMap()
        ChordPaletteStore.saveRaw(context, "", "INVALID")
        ChordPaletteStore.saveRaw(context, "   ", "INVALID")
        ChordPaletteStore.clear(context, "")
        assertEquals(beforeInvalidWrites, storedValues)

        assertEquals("LEGACY_GLOBAL_PALETTE", storedValues["palette_lyrics.lrc"])
        assertFalse(ChordPaletteStore.loadRaw(context, "song-a").contains("LEGACY"))
        assertFalse(ChordPaletteStore.loadRaw(context, "song-b").contains("LEGACY"))
        assertTrue(storedValues.containsKey("palette_lyrics.lrc"))
    }

    @Test
    fun missingSongPaletteCanBeInferredWithoutLegacyFallback() {
        val storedValues = mutableMapOf(
            "palette_lyrics.lrc" to "Bbmaj7, Eb/G"
        )
        val context = contextWithPreferences(storedValues)
        val songId = "song-with-own-chords"

        val saved = ChordPaletteStore.loadRaw(context, songId)
        assertEquals("", saved)

        val inferred = inferChordPaletteFromText("C Am\nF G")
        ChordPaletteStore.saveRaw(context, songId, inferred.joinToString(", "))

        assertEquals("C, Am, F, G", ChordPaletteStore.loadRaw(context, songId))
        assertEquals("Bbmaj7, Eb/G", storedValues["palette_lyrics.lrc"])
    }

    @Test
    fun parentAndArrangementVariantUseIndependentPalettes() {
        val storedValues = mutableMapOf<String, String>()
        val context = contextWithPreferences(storedValues)

        ChordPaletteStore.saveRaw(context, "parent-song-id", "C, F, G")
        ChordPaletteStore.saveRaw(context, "arrangement-variant-id", "Dm, Gm, A")

        assertEquals("C, F, G", ChordPaletteStore.loadRaw(context, "parent-song-id"))
        assertEquals("Dm, Gm, A", ChordPaletteStore.loadRaw(context, "arrangement-variant-id"))
        assertTrue(storedValues.containsKey("palette_songId::parent-song-id"))
        assertTrue(storedValues.containsKey("palette_songId::arrangement-variant-id"))
    }

    private fun contextWithPreferences(
        storedValues: MutableMap<String, String>
    ): Context {
        val context = Mockito.mock(Context::class.java)
        val preferences = Mockito.mock(SharedPreferences::class.java)
        val editor = Mockito.mock(SharedPreferences.Editor::class.java)

        Mockito.`when`(context.getSharedPreferences(Mockito.anyString(), Mockito.anyInt()))
            .thenReturn(preferences)
        Mockito.`when`(preferences.getString(Mockito.anyString(), Mockito.anyString()))
            .thenAnswer { invocation ->
                storedValues[invocation.getArgument(0)] ?: invocation.getArgument(1)
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

        return context
    }
}
