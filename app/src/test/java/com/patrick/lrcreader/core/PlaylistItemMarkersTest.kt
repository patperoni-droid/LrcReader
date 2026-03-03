package com.patrick.lrcreader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistItemMarkersTest {

    @Test
    fun buildGroupHeader_createsDetectableMarker() {
        val marker = buildGroupHeader("Rock")

        assertTrue(isGroupHeader(marker))
        assertEquals("Rock", getGroupTitle(marker))
    }

    @Test
    fun renameGroupHeader_preservesUuidAndUpdatesTitle() {
        val marker = buildGroupHeader("Rock")
        val oldUuid = marker.split('|', limit = 4)[2]

        val renamed = renameGroupHeader(marker, "Versions")
        val newUuid = renamed.split('|', limit = 4)[2]

        assertTrue(isGroupHeader(renamed))
        assertEquals(oldUuid, newUuid)
        assertEquals("Versions", getGroupTitle(renamed))
    }

    @Test
    fun buildGroupHeader_generatesDifferentIds() {
        val one = buildGroupHeader("A")
        val two = buildGroupHeader("A")

        assertNotEquals(one, two)
    }

    @Test
    fun virtualAndPlayableDetection_worksForGroupPrompterAudio() {
        val header = buildGroupHeader("Group")
        val prompter = "prompter://42"
        val audio = "content://media/external/audio/123"

        assertTrue(isVirtualPlaylistItem(header))
        assertTrue(isVirtualPlaylistItem(prompter))
        assertFalse(isVirtualPlaylistItem(audio))

        assertFalse(isPlayableAudioItem(header))
        assertFalse(isPlayableAudioItem(prompter))
        assertTrue(isPlayableAudioItem(audio))
    }
}
