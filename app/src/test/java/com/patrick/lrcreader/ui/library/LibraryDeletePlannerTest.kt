package com.patrick.lrcreader.ui.library

import com.patrick.lrcreader.core.LibraryIndexCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryDeletePlannerTest {

    @Test
    fun buildPlan_findsLyricsAndAccordsForAudioInBackingTracks() {
        val rootUri = "content://spl/root"
        val backingUri = "content://spl/backing"
        val audioDirUri = "content://spl/audio"
        val lyricsDirUri = "content://spl/lyrics"
        val accordsDirUri = "content://spl/accords"
        val audioUri = "content://spl/audio/song.mp3"
        val lyricsUri = "content://spl/lyrics/song.lrc"
        val accordsUri = "content://spl/accords/song.lrc"

        val index = listOf(
            entry(rootUri, "SPL_Music", true, null),
            entry(backingUri, "BackingTracks", true, rootUri),
            entry(audioDirUri, "Audio", true, backingUri),
            entry(lyricsDirUri, "Lyrics", true, backingUri),
            entry(accordsDirUri, "Accords", true, backingUri),
            entry(audioUri, "song.mp3", false, audioDirUri),
            entry(lyricsUri, "song.lrc", false, lyricsDirUri),
            entry(accordsUri, "song.lrc", false, accordsDirUri)
        )

        val associated = LibraryDeletePlanner.findAssociatedLrcMatches(
            targetUriString = audioUri,
            indexAll = index
        )

        assertEquals(2, associated.size)
        val roles = associated.map { it.role }.toSet()
        assertTrue(roles.contains(LibraryDeleteRole.LYRICS))
        assertTrue(roles.contains(LibraryDeleteRole.ACCORDS))
    }

    @Test
    fun buildPlan_returnsSimpleDeleteForNonAudioTarget() {
        val lrcUri = "content://spl/lyrics/song.lrc"
        val index = listOf(
            entry("content://spl/root", "SPL_Music", true, null),
            entry("content://spl/backing", "BackingTracks", true, "content://spl/root"),
            entry("content://spl/lyrics", "Lyrics", true, "content://spl/backing"),
            entry(lrcUri, "song.lrc", false, "content://spl/lyrics")
        )

        val associated = LibraryDeletePlanner.findAssociatedLrcMatches(
            targetUriString = lrcUri,
            indexAll = index
        )

        assertFalse(LibraryDeletePlanner.isAudioFileName("song.lrc"))
        assertTrue(associated.isEmpty())
    }

    @Test
    fun isAudioFileName_recognizesExpectedExtensions() {
        assertTrue(LibraryDeletePlanner.isAudioFileName("track.mp3"))
        assertTrue(LibraryDeletePlanner.isAudioFileName("track.WAV"))
        assertTrue(LibraryDeletePlanner.isAudioFileName("track.m4a"))
        assertFalse(LibraryDeletePlanner.isAudioFileName("track.lrc"))
        assertFalse(LibraryDeletePlanner.isAudioFileName("track.json"))
    }

    private fun entry(
        uriString: String,
        name: String,
        isDirectory: Boolean,
        parentUriString: String?
    ): LibraryIndexCache.CachedEntry {
        return LibraryIndexCache.CachedEntry(
            uriString = uriString,
            name = name,
            isDirectory = isDirectory,
            parentUriString = parentUriString
        )
    }
}
