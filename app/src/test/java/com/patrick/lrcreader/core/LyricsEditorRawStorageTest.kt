package com.patrick.lrcreader.core

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsEditorRawStorageTest {

    @Test
    fun autosaveAndManualSave_roundTripRawTextExactly() {
        val songDir = Files.createTempDirectory("lyrics-editor-raw").toFile()
        val rawText = "Couplet 1\nPremière ligne\nDeuxième ligne\n\nRefrain\nPremière ligne du refrain\n\nDeuxième ligne du refrain\n\nCouplet 2\n"

        try {
            assertTrue(LrcStorage.saveEditorRawToSongDir(songDir, rawText))
            assertEquals(rawText, LrcStorage.loadEditorRawFromSongDir(songDir))

            val manuallySaved = "$rawText\nPont\n"
            assertTrue(LrcStorage.saveEditorRawToSongDir(songDir, manuallySaved))
            assertEquals(manuallySaved, LrcStorage.loadEditorRawFromSongDir(songDir))
        } finally {
            songDir.deleteRecursively()
        }
    }

    @Test
    fun legacySong_withoutRawFile_returnsNullForFallback() {
        val songDir = Files.createTempDirectory("lyrics-editor-legacy").toFile()

        try {
            assertNull(LrcStorage.loadEditorRawFromSongDir(songDir))
        } finally {
            songDir.deleteRecursively()
        }
    }
}
