package com.patrick.lrcreader.ui

import com.patrick.lrcreader.core.buildGroupEnd
import com.patrick.lrcreader.core.buildGroupHeader
import com.patrick.lrcreader.core.buildSmpItem
import com.patrick.lrcreader.core.buildSmpOccurrenceItem
import com.patrick.lrcreader.core.getGroupUuid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistGroupDurationStatsTest {
    @Test
    fun groupWithKnownDurations_sumsOnlyGroupChildren() {
        val group = buildGroupHeader("Set 1")
        val items = listOf(
            "file://outside.mp3",
            group,
            "file://a.mp3",
            "file://b.mp3",
            "file://c.mp3",
            buildGroupEnd(requireNotNull(getGroupUuid(group))),
            "file://after.mp3"
        )

        val stats = calculatePlaylistGroupDurationStats(
            items = items,
            headerIndex = 1,
            familyById = emptyMap(),
            smpPlaybackUriById = emptyMap(),
            durationCache = mapOf(
                "file://outside.mp3" to 999_000L,
                "file://a.mp3" to 60_000L,
                "file://b.mp3" to 120_000L,
                "file://c.mp3" to 30_000L,
                "file://after.mp3" to 999_000L
            )
        )

        assertEquals(3, stats.trackCount)
        assertEquals(210_000L, stats.knownDurationMs)
        assertFalse(stats.hasUnknownDuration)
    }

    @Test
    fun emptyGroup_hasNeutralDuration() {
        val group = buildGroupHeader("Empty")
        val items = listOf(group, buildGroupEnd(requireNotNull(getGroupUuid(group))))

        val stats = calculatePlaylistGroupDurationStats(
            items = items,
            headerIndex = 0,
            familyById = emptyMap(),
            smpPlaybackUriById = emptyMap(),
            durationCache = emptyMap()
        )

        assertEquals(0, stats.trackCount)
        assertEquals(0L, stats.knownDurationMs)
        assertFalse(stats.hasUnknownDuration)
    }

    @Test
    fun separatedGroups_doNotShareDurations() {
        val first = buildGroupHeader("First")
        val second = buildGroupHeader("Second")
        val items = listOf(
            first,
            "file://first.mp3",
            buildGroupEnd(requireNotNull(getGroupUuid(first))),
            second,
            "file://second.mp3",
            buildGroupEnd(requireNotNull(getGroupUuid(second)))
        )

        val firstStats = calculatePlaylistGroupDurationStats(
            items = items,
            headerIndex = 0,
            familyById = emptyMap(),
            smpPlaybackUriById = emptyMap(),
            durationCache = mapOf("file://first.mp3" to 10_000L, "file://second.mp3" to 90_000L)
        )
        val secondStats = calculatePlaylistGroupDurationStats(
            items = items,
            headerIndex = 3,
            familyById = emptyMap(),
            smpPlaybackUriById = emptyMap(),
            durationCache = mapOf("file://first.mp3" to 10_000L, "file://second.mp3" to 90_000L)
        )

        assertEquals(10_000L, firstStats.knownDurationMs)
        assertEquals(90_000L, secondStats.knownDurationMs)
    }

    @Test
    fun sameSongIdOccurrence_countsInEachGroup() {
        val songId = "song-1"
        val first = buildGroupHeader("First")
        val second = buildGroupHeader("Second")
        val firstOccurrence = buildSmpItem(songId)
        val secondOccurrence = buildSmpOccurrenceItem(songId)
        val audioUri = "file://song-1.m4a"
        val items = listOf(
            first,
            firstOccurrence,
            buildGroupEnd(requireNotNull(getGroupUuid(first))),
            second,
            secondOccurrence,
            buildGroupEnd(requireNotNull(getGroupUuid(second)))
        )

        val commonArgs = mapOf(songId to audioUri)
        val cache = mapOf(audioUri to 45_000L)

        val firstStats = calculatePlaylistGroupDurationStats(items, 0, emptyMap(), commonArgs, cache)
        val secondStats = calculatePlaylistGroupDurationStats(items, 3, emptyMap(), commonArgs, cache)

        assertEquals(1, firstStats.trackCount)
        assertEquals(45_000L, firstStats.knownDurationMs)
        assertEquals(1, secondStats.trackCount)
        assertEquals(45_000L, secondStats.knownDurationMs)
    }

    @Test
    fun missingDuration_isUnknownNotFalseZero() {
        val group = buildGroupHeader("Missing")
        val items = listOf(
            group,
            "file://known.mp3",
            "file://missing.mp3",
            buildGroupEnd(requireNotNull(getGroupUuid(group)))
        )

        val stats = calculatePlaylistGroupDurationStats(
            items = items,
            headerIndex = 0,
            familyById = emptyMap(),
            smpPlaybackUriById = emptyMap(),
            durationCache = mapOf("file://known.mp3" to 12_000L)
        )

        assertEquals(2, stats.trackCount)
        assertEquals(12_000L, stats.knownDurationMs)
        assertTrue(stats.hasUnknownDuration)
    }

    @Test
    fun movedSongBetweenGroups_changesOnlyTargetGroupDuration() {
        val first = buildGroupHeader("First")
        val second = buildGroupHeader("Second")
        val items = mutableListOf(
            first,
            "file://a.mp3",
            "file://b.mp3",
            buildGroupEnd(requireNotNull(getGroupUuid(first))),
            second,
            buildGroupEnd(requireNotNull(getGroupUuid(second)))
        )
        val moved = items.removeAt(2)
        items.add(4, moved)
        val cache = mapOf("file://a.mp3" to 10_000L, "file://b.mp3" to 20_000L)

        val firstStats = calculatePlaylistGroupDurationStats(items, 0, emptyMap(), emptyMap(), cache)
        val secondStats = calculatePlaylistGroupDurationStats(items, 3, emptyMap(), emptyMap(), cache)

        assertEquals(1, firstStats.trackCount)
        assertEquals(10_000L, firstStats.knownDurationMs)
        assertEquals(1, secondStats.trackCount)
        assertEquals(20_000L, secondStats.knownDurationMs)
    }
}
