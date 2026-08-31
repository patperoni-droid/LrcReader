package com.patrick.lrcreader.smp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrangementNavigationTest {

    @Test
    fun linearNavigation_withoutEntriesIsEmpty() {
        val arrangement = ArrangementData(
            sourceSongId = "song_parent",
            segments = emptyList(),
            structureSegmentIds = emptyList()
        )

        assertTrue(arrangement.toLinearNavigationItems("song_parent").isEmpty())
    }

    @Test
    fun linearNavigation_preservesOrderIdentityRepeatsAndSourceTime() {
        val arrangement = ArrangementData(
            version = 2,
            sourceSongId = "song_parent",
            segments = emptyList(),
            structureSegmentIds = listOf("intro", "chorus", "outro"),
            entries = listOf(
                ArrangementEntryData("intro", "Intro", 0L, 1_000L),
                ArrangementEntryData("chorus", "Refrain", 4_000L, 6_000L, repeatCount = 2),
                ArrangementEntryData("outro", "FIN", 9_000L, 10_000L)
            )
        )

        val items = arrangement.toLinearNavigationItems("song_parent")

        assertEquals(listOf("intro", "chorus", "outro"), items.map { it.entryId })
        assertEquals(listOf(0L, 4_000L, 9_000L), items.map { it.navigationPositionMs })
        assertEquals(2, items[1].repeatCount)
        assertTrue(items.all { it.ownerSongId == "song_parent" })
    }

    @Test
    fun linearNavigation_skipsMutedAndKeepsHomonymsDistinct() {
        val arrangement = ArrangementData(
            version = 2,
            sourceSongId = "song_parent",
            segments = emptyList(),
            structureSegmentIds = listOf("chorus_a", "muted", "chorus_b"),
            entries = listOf(
                ArrangementEntryData("chorus_a", "Refrain", 1_000L, 2_000L),
                ArrangementEntryData("muted", "Solo", 2_000L, 3_000L, muted = true),
                ArrangementEntryData("chorus_b", "Refrain", 3_000L, 4_000L)
            )
        )

        val items = arrangement.toLinearNavigationItems("song_parent")

        assertEquals(listOf("chorus_a", "chorus_b"), items.map { it.entryId })
        assertEquals(listOf("Refrain", "Refrain"), items.map { it.name })
    }

    @Test
    fun virtualNavigation_usesCumulativeStartOfFirstPlayableRepeat() {
        val occurrences = prepareArrangementOccurrences(
            segments = listOf(
                ArrangementSegmentData("intro", "Intro", 10_000L, 12_000L),
                ArrangementSegmentData("muted", "Couplet", 20_000L, 23_000L),
                ArrangementSegmentData("outro", "FIN", 30_000L, 31_000L)
            ),
            structureSegmentIds = listOf("intro", "muted", "outro"),
            entries = listOf(
                ArrangementEntryData("intro", "Intro", 10_000L, 12_000L, repeatCount = 2),
                ArrangementEntryData("muted", "Couplet", 20_000L, 23_000L, muted = true),
                ArrangementEntryData("outro", "FIN", 30_000L, 31_000L)
            ),
            useOccurrenceModel = true
        )

        val items = occurrences.toVirtualNavigationItems("variant_live")

        assertEquals(listOf("intro", "outro"), items.map { it.entryId })
        assertEquals(listOf(0L, 4_000L), items.map { it.navigationPositionMs })
        assertEquals(2, items.first().repeatCount)
        assertTrue(items.all { it.ownerSongId == "variant_live" })
    }
}
