package com.patrick.lrcreader.core

import org.junit.Assert.assertEquals
import org.junit.Test

class TrackLyricsViewPrefsTest {

    @Test
    fun buildPreferenceKey_prefersDisplayNameOverUri() {
        val key = TrackLyricsViewPrefs.buildPreferenceKey(
            trackUriString = "content://media/external/audio/media/123",
            displayName = "Mon Titre.mp3"
        )

        assertEquals("view_track_mon titre.mp3", key)
    }

    @Test
    fun buildPreferenceKey_fallsBackToFileNameFromUri() {
        val key = TrackLyricsViewPrefs.buildPreferenceKey(
            trackUriString = "file:///storage/emulated/0/Music/MonTitre.mp3",
            displayName = null
        )

        assertEquals("view_track_montitre.mp3", key)
    }

    @Test
    fun fallbackTrackFileName_handlesDocumentStyleUri() {
        val fileName = TrackLyricsViewPrefs.fallbackTrackFileName(
            "content://com.android.externalstorage.documents/document/primary:Music/MonTitre.wav"
        )

        assertEquals("MonTitre.wav", fileName)
    }
}
