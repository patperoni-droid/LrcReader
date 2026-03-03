package com.patrick.lrcreader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistQueueUtilsTest {

    @Test
    fun nextPlayableIndexAtOrAfter_skipsVirtualItems() {
        val header = buildGroupHeader("Rock")
        val queue = listOf(header, "prompter://99", "content://audio/1", "content://audio/2")

        val index = nextPlayableIndexAtOrAfter(queue, 0)

        assertEquals(2, index)
    }

    @Test
    fun nextPlayableUriAfter_returnsNextAudioOnly() {
        val header = buildGroupHeader("Rock")
        val queue = listOf("content://audio/1", header, "prompter://99", "content://audio/2")

        val next = nextPlayableUriAfter(queue, 0)

        assertEquals("content://audio/2", next)
    }

    @Test
    fun nextPlayableUriAfter_returnsNullWhenNoAudioAfter() {
        val header = buildGroupHeader("Rock")
        val queue = listOf("content://audio/1", header, "prompter://99")

        val next = nextPlayableUriAfter(queue, 0)

        assertNull(next)
    }
}
