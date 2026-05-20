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
    fun groupColor_isOptionalAndPreservedOnRename() {
        val marker = buildGroupHeader("Rock")

        assertEquals(null, getGroupColorArgb(marker))

        val colored = setGroupColorArgb(marker, 0xFF2E7D32)
        val renamed = renameGroupHeader(colored, "Versions")

        assertTrue(isGroupHeader(colored))
        assertEquals(0xFF2E7D32, getGroupColorArgb(colored))
        assertEquals(0xFF2E7D32, getGroupColorArgb(renamed))
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
        val end = buildGroupEnd(getGroupUuid(header)!!)
        val prompter = "prompter://42"
        val audio = "content://media/external/audio/123"

        assertTrue(isVirtualPlaylistItem(header))
        assertTrue(isVirtualPlaylistItem(end))
        assertTrue(isVirtualPlaylistItem(prompter))
        assertFalse(isVirtualPlaylistItem(audio))

        assertFalse(isPlayableAudioItem(header))
        assertFalse(isPlayableAudioItem(end))
        assertFalse(isPlayableAudioItem(prompter))
        assertTrue(isPlayableAudioItem(audio))
    }

    @Test
    fun groupEndMarkers_uuidAndDetection_workForStartAndEnd() {
        val start = buildGroupHeader("Rock")
        val uuid = getGroupUuid(start)
        val end = buildGroupEnd(uuid!!)

        assertTrue(isGroupHeader(start))
        assertTrue(isGroupEnd(end))
        assertEquals(uuid, getGroupUuid(end))
        assertFalse(isGroupEnd(start))
    }
}
