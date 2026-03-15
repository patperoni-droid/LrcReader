package com.patrick.lrcreader.ui.library

import com.patrick.lrcreader.core.LibraryIndexCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

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

    @Test
    fun findAssociatedLrcMatches_findsHashedLrcNamesFromTrackUriHash() {
        val rootUri = "content://spl/root"
        val backingUri = "content://spl/backing"
        val audioDirUri = "content://spl/audio"
        val lyricsDirUri = "content://spl/lyrics"
        val accordsDirUri = "content://spl/accords"
        val audioUri = "content://spl/audio/conkisador.mp3"
        val hash10 = md5(audioUri).take(10)
        val hashedName = "conkisador-$hash10.lrc"
        val lyricsUri = "content://spl/lyrics/$hashedName"
        val accordsUri = "content://spl/accords/$hashedName"

        val index = listOf(
            entry(rootUri, "SPL_Music", true, null),
            entry(backingUri, "BackingTracks", true, rootUri),
            entry(audioDirUri, "Audio", true, backingUri),
            entry(lyricsDirUri, "Lyrics", true, backingUri),
            entry(accordsDirUri, "Accords", true, backingUri),
            entry(audioUri, "conkisador.mp3", false, audioDirUri),
            entry(lyricsUri, hashedName, false, lyricsDirUri),
            entry(accordsUri, hashedName, false, accordsDirUri)
        )

        val associated = LibraryDeletePlanner.findAssociatedLrcMatches(
            targetUriString = audioUri,
            indexAll = index
        )

        assertEquals(2, associated.size)
        val names = associated.map { it.displayName }.toSet()
        assertTrue(names.contains(hashedName))
        val roles = associated.map { it.role }.toSet()
        assertTrue(roles.contains(LibraryDeleteRole.LYRICS))
        assertTrue(roles.contains(LibraryDeleteRole.ACCORDS))
    }

    @Test
    fun findAssociatedLrcMatches_findsHistoricalHashedLrcNames_notBasedOnCurrentUriHash() {
        val rootUri = "content://spl/root"
        val backingUri = "content://spl/backing"
        val audioDirUri = "content://spl/audio"
        val lyricsDirUri = "content://spl/lyrics"
        val accordsDirUri = "content://spl/accords"
        val audioUri = "content://spl/audio/conkisador.mp3"
        val historicalHashedName = "conkisador-5093ad2667.lrc"
        val lyricsUri = "content://spl/lyrics/$historicalHashedName"
        val accordsUri = "content://spl/accords/$historicalHashedName"

        val index = listOf(
            entry(rootUri, "SPL_Music", true, null),
            entry(backingUri, "BackingTracks", true, rootUri),
            entry(audioDirUri, "Audio", true, backingUri),
            entry(lyricsDirUri, "Lyrics", true, backingUri),
            entry(accordsDirUri, "Accords", true, backingUri),
            entry(audioUri, "conkisador.mp3", false, audioDirUri),
            entry(lyricsUri, historicalHashedName, false, lyricsDirUri),
            entry(accordsUri, historicalHashedName, false, accordsDirUri)
        )

        val associated = LibraryDeletePlanner.findAssociatedLrcMatches(
            targetUriString = audioUri,
            indexAll = index
        )

        assertEquals(2, associated.size)
        val names = associated.map { it.displayName }.toSet()
        assertTrue(names.contains(historicalHashedName))
        val roles = associated.map { it.role }.toSet()
        assertTrue(roles.contains(LibraryDeleteRole.LYRICS))
        assertTrue(roles.contains(LibraryDeleteRole.ACCORDS))
    }

    @Test
    fun findAssociatedLrcMatches_usesDisplayNameBase_forEncodedSafUri() {
        val rootUri = "content://com.android.externalstorage.documents/tree/primary%3ADocuments/document/primary%3ADocuments%2FSPL_Music"
        val backingUri = "$rootUri%2FBackingTracks"
        val audioDirUri = "$backingUri%2Faudio"
        val lyricsDirUri = "$backingUri%2Flyrics"
        val accordsDirUri = "$backingUri%2FAccords"
        val audioUri = "$audioDirUri%2FCONKISADOR.mp3"
        val historicalHashedName = "CONKISADOR-5093ad2667.lrc"
        val lyricsUri = "$lyricsDirUri%2F$historicalHashedName"
        val accordsUri = "$accordsDirUri%2F$historicalHashedName"

        val index = listOf(
            entry(rootUri, "SPL_Music", true, null),
            entry(backingUri, "BackingTracks", true, rootUri),
            entry(audioDirUri, "audio", true, backingUri),
            entry(lyricsDirUri, "lyrics", true, backingUri),
            entry(accordsDirUri, "Accords", true, backingUri),
            entry(audioUri, "CONKISADOR.mp3", false, audioDirUri),
            entry(lyricsUri, historicalHashedName, false, lyricsDirUri),
            entry(accordsUri, historicalHashedName, false, accordsDirUri)
        )

        val associated = LibraryDeletePlanner.findAssociatedLrcMatches(
            targetUriString = audioUri,
            indexAll = index
        )

        assertEquals(2, associated.size)
        val names = associated.map { it.displayName }.toSet()
        assertTrue(names.contains(historicalHashedName))
        val roles = associated.map { it.role }.toSet()
        assertTrue(roles.contains(LibraryDeleteRole.LYRICS))
        assertTrue(roles.contains(LibraryDeleteRole.ACCORDS))
    }

    @Test
    fun findAssociatedLrcMatches_prefersResolvedPlayerFileName_whenProvided() {
        val rootUri = "content://spl/root"
        val backingUri = "content://spl/backing"
        val audioDirUri = "content://spl/audio"
        val lyricsDirUri = "content://spl/lyrics"
        val accordsDirUri = "content://spl/accords"
        val audioUri = "content://spl/audio/song.mp3"
        val resolvedName = "song-custom-origin.lrc"
        val lyricsUri = "content://spl/lyrics/$resolvedName"
        val accordsUri = "content://spl/accords/$resolvedName"

        val index = listOf(
            entry(rootUri, "SPL_Music", true, null),
            entry(backingUri, "BackingTracks", true, rootUri),
            entry(audioDirUri, "Audio", true, backingUri),
            entry(lyricsDirUri, "Lyrics", true, backingUri),
            entry(accordsDirUri, "Accords", true, backingUri),
            entry(audioUri, "song.mp3", false, audioDirUri),
            entry(lyricsUri, resolvedName, false, lyricsDirUri),
            entry(accordsUri, resolvedName, false, accordsDirUri)
        )

        val associated = LibraryDeletePlanner.findAssociatedLrcMatches(
            targetUriString = audioUri,
            indexAll = index,
            preferredLrcFileName = resolvedName
        )

        assertEquals(2, associated.size)
        val names = associated.map { it.displayName }.toSet()
        assertTrue(names.contains(resolvedName))
        val roles = associated.map { it.role }.toSet()
        assertTrue(roles.contains(LibraryDeleteRole.LYRICS))
        assertTrue(roles.contains(LibraryDeleteRole.ACCORDS))
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

    private fun md5(text: String): String {
        val md = MessageDigest.getInstance("MD5")
        return md.digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
