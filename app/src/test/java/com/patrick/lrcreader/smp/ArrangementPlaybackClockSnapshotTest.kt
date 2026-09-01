package com.patrick.lrcreader.smp

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.Mockito

class ArrangementPlaybackClockSnapshotTest {

    @Test
    fun localPositionExposesSourceArrangementAndSelectedAssetClocks() {
        val prepared = preparedPlayback(
            ownerSongId = "normal",
            audioSourceSongId = "normal",
            assetTimeDomain = ArrangementAssetTimeDomain.SOURCE
        )
        val occurrence = prepared.occurrences.first()
        val snapshot = requireNotNull(
            prepared.clockSnapshot(
                occurrenceId = occurrence.id,
                localPositionMs = 400L,
                sourceDurationMs = 60_000L
            )
        )

        assertEquals(occurrence.id, snapshot.occurrenceId)
        assertEquals(0, snapshot.occurrenceIndex)
        assertEquals(occurrence.groupKey, snapshot.groupKey)
        assertEquals(400L, snapshot.localPositionMs)
        assertEquals(10_400L, snapshot.sourcePositionMs)
        assertEquals(400L, snapshot.arrangementPositionMs)
        assertEquals(1_000L, snapshot.occurrenceDurationMs)
        assertEquals(60_000L, snapshot.sourceDurationMs)
        assertEquals(5_000L, snapshot.arrangementDurationMs)
        assertEquals(10_400L, snapshot.assetPositionMs)
        assertEquals(10_400L, snapshot.positionMs(ArrangementAssetTimeDomain.SOURCE))
        assertEquals(400L, snapshot.positionMs(ArrangementAssetTimeDomain.ARRANGEMENT))
    }

    @Test
    fun localPositionUsesTheExistingClosedAndClampedOccurrenceContract() {
        val prepared = preparedPlayback()
        val occurrence = prepared.occurrences.first()

        assertClock(prepared, occurrence.id, -1L, 0L, 10_000L, 0L)
        assertClock(prepared, occurrence.id, 0L, 0L, 10_000L, 0L)
        assertClock(prepared, occurrence.id, 500L, 500L, 10_500L, 500L)
        assertClock(prepared, occurrence.id, 999L, 999L, 10_999L, 999L)
        assertClock(prepared, occurrence.id, 1_000L, 1_000L, 11_000L, 1_000L)
        assertClock(prepared, occurrence.id, 1_001L, 1_000L, 11_000L, 1_000L)
    }

    @Test
    fun repeatedSourceRangeKeepsSourceClockAndSeparatesArrangementClock() {
        val prepared = preparedPlayback()
        val firstRepeat = prepared.occurrences[1]
        val secondRepeat = prepared.occurrences[2]
        val firstSnapshot = requireNotNull(prepared.clockSnapshot(firstRepeat.id, 750L))
        val secondSnapshot = requireNotNull(prepared.clockSnapshot(secondRepeat.id, 750L))

        assertEquals(20_750L, firstSnapshot.sourcePositionMs)
        assertEquals(firstSnapshot.sourcePositionMs, secondSnapshot.sourcePositionMs)
        assertEquals(1_750L, firstSnapshot.arrangementPositionMs)
        assertEquals(3_750L, secondSnapshot.arrangementPositionMs)
        assertNotEquals(firstSnapshot.occurrenceId, secondSnapshot.occurrenceId)
        assertEquals(firstSnapshot.groupKey, secondSnapshot.groupKey)
        assertEquals(
            1_750L,
            prepared.arrangementPositionFromSourceMs(firstRepeat.id, 20_750L)
        )
        assertEquals(
            3_750L,
            prepared.arrangementPositionFromSourceMs(secondRepeat.id, 20_750L)
        )
    }

    @Test
    fun opaqueMediaIdLookupDoesNotDependOnItsTextFormatOrQueueIndex() {
        val prepared = preparedPlayback(ownerSongId = "owner:with:separators")
        val selected = prepared.occurrences[2]
        val snapshot = requireNotNull(
            prepared.clockSnapshotForMediaId(
                mediaId = selected.mediaId,
                localPositionMs = 250L
            )
        )

        assertEquals(selected.id, snapshot.occurrenceId)
        assertEquals(2, snapshot.occurrenceIndex)
        assertEquals(3_250L, snapshot.arrangementPositionMs)
        assertNull(prepared.clockSnapshotForMediaId("owner:with:separators:2:99", 250L))
    }

    @Test
    fun structuredIdentityKeepsSameLabelsAndRepeatsDistinct() {
        val prepared = preparedPlayback()
        val intro = prepared.occurrences[0]
        val firstRepeat = prepared.occurrences[1]
        val secondRepeat = prepared.occurrences[2]

        assertEquals(intro.label, firstRepeat.label)
        assertNotEquals(intro.groupKey, firstRepeat.groupKey)
        assertEquals(firstRepeat.label, secondRepeat.label)
        assertEquals(firstRepeat.groupKey, secondRepeat.groupKey)
        assertNotEquals(firstRepeat.id, secondRepeat.id)
        assertNull(
            prepared.clockSnapshot(
                ArrangementPlaybackOccurrenceId("other_owner", firstRepeat.entryId, 0),
                0L
            )
        )
    }

    @Test
    fun normalParentAndVariantExposeTheirPreparedIdentityAndAssetDomain() {
        val normal = preparedPlayback(
            ownerSongId = "normal",
            audioSourceSongId = "normal",
            assetTimeDomain = ArrangementAssetTimeDomain.SOURCE
        )
        val parent = preparedPlayback(
            ownerSongId = "parent",
            audioSourceSongId = "parent",
            assetTimeDomain = ArrangementAssetTimeDomain.SOURCE
        )
        val variant = preparedPlayback(
            ownerSongId = "variant",
            audioSourceSongId = "parent",
            assetTimeDomain = ArrangementAssetTimeDomain.ARRANGEMENT
        )

        assertPreparedIdentity(normal, "normal", "normal", ArrangementAssetTimeDomain.SOURCE)
        assertPreparedIdentity(parent, "parent", "parent", ArrangementAssetTimeDomain.SOURCE)
        assertPreparedIdentity(variant, "variant", "parent", ArrangementAssetTimeDomain.ARRANGEMENT)
        assertEquals(10_250L, requireNotNull(normal.clockSnapshotAt(0, 250L)).assetPositionMs)
        assertEquals(250L, requireNotNull(variant.clockSnapshotAt(0, 250L)).assetPositionMs)
    }

    @Test
    fun variantCompatibilityUsesOpaqueMediaIdAfterFutureQueueReplacement() {
        val prepared = preparedPlayback(
            ownerSongId = "variant",
            audioSourceSongId = "parent",
            assetTimeDomain = ArrangementAssetTimeDomain.ARRANGEMENT
        )
        val runtime = PreparedVirtualArrangementPlayback(prepared)
        val firstOccurrence = prepared.occurrences[0]
        val firstRepeat = prepared.occurrences[1]
        val destination = prepared.occurrences[2]

        val first = requireNotNull(runtime.clockSnapshot(firstOccurrence.mediaId, 0, 0L))
        val firstBoundary = requireNotNull(
            runtime.clockSnapshot(firstOccurrence.mediaId, 0, firstOccurrence.durationMs)
        )
        val intermediate = requireNotNull(runtime.clockSnapshot(firstRepeat.mediaId, 1, 500L))
        val replacedQueueDestination = requireNotNull(
            runtime.clockSnapshot(
                currentMediaId = destination.mediaId,
                fallbackOccurrenceIndex = 1,
                localPositionMs = 500L
            )
        )
        val boundary = requireNotNull(runtime.clockSnapshot(destination.mediaId, 0, 2_000L))

        assertEquals(0L, first.arrangementPositionMs)
        assertEquals(1_000L, firstBoundary.arrangementPositionMs)
        assertEquals(1_500L, intermediate.arrangementPositionMs)
        assertEquals(3_500L, replacedQueueDestination.arrangementPositionMs)
        assertEquals(5_000L, boundary.arrangementPositionMs)
    }

    @Test
    fun invalidLookupEmptyModelAndNegativeSourceDurationFailExplicitly() {
        val prepared = preparedPlayback()
        val empty = emptyPreparedPlayback()

        assertNull(prepared.clockSnapshotAt(-1, 0L))
        assertNull(prepared.clockSnapshotAt(99, 0L))
        assertNull(empty.clockSnapshotAt(0, 0L))
        assertThrows(IllegalArgumentException::class.java) {
            prepared.clockSnapshotAt(0, 0L, sourceDurationMs = -1L)
        }
    }

    private fun assertClock(
        prepared: PreparedArrangementPlayback,
        occurrenceId: ArrangementPlaybackOccurrenceId,
        requestedLocalMs: Long,
        expectedLocalMs: Long,
        expectedSourceMs: Long,
        expectedArrangementMs: Long
    ) {
        val snapshot = requireNotNull(prepared.clockSnapshot(occurrenceId, requestedLocalMs))
        assertEquals(expectedLocalMs, snapshot.localPositionMs)
        assertEquals(expectedSourceMs, snapshot.sourcePositionMs)
        assertEquals(expectedArrangementMs, snapshot.arrangementPositionMs)
    }

    private fun assertPreparedIdentity(
        prepared: PreparedArrangementPlayback,
        ownerSongId: String,
        audioSourceSongId: String,
        assetTimeDomain: ArrangementAssetTimeDomain
    ) {
        val snapshot = requireNotNull(prepared.clockSnapshotAt(0, 0L))
        assertEquals(ownerSongId, prepared.ownerSongId)
        assertEquals(audioSourceSongId, prepared.audioSourceSongId)
        assertEquals(ownerSongId, snapshot.occurrenceId.ownerSongId)
        assertEquals(assetTimeDomain, snapshot.assetTimeDomain)
    }

    private fun preparedPlayback(
        ownerSongId: String = "owner",
        audioSourceSongId: String = "source",
        assetTimeDomain: ArrangementAssetTimeDomain = ArrangementAssetTimeDomain.ARRANGEMENT
    ): PreparedArrangementPlayback = requireNotNull(
        ArrangementPlaybackPreparer.prepare(
            ownerSongId = ownerSongId,
            audioSourceSongId = audioSourceSongId,
            title = "Prepared",
            sourceAudioUri = audioUri("clock"),
            playbackProfile = null,
            assetTimeDomain = assetTimeDomain,
            arrangement = arrangement(audioSourceSongId)
        )
    )

    private fun emptyPreparedPlayback(): PreparedArrangementPlayback = PreparedArrangementPlayback(
        ownerSongId = "owner",
        audioSourceSongId = "source",
        title = "Empty",
        sourceAudioUri = audioUri("empty"),
        playbackProfile = null,
        assetTimeDomain = ArrangementAssetTimeDomain.ARRANGEMENT,
        occurrences = emptyList(),
        mediaItems = emptyList(),
        livePlan = LiveArrangementPlan(emptyList()),
        navigationItems = emptyList()
    )

    private fun arrangement(sourceSongId: String): ArrangementData = ArrangementData(
        version = 2,
        sourceSongId = sourceSongId,
        segments = emptyList(),
        structureSegmentIds = emptyList(),
        entries = listOf(
            ArrangementEntryData("intro", "Same", 10_000L, 11_000L),
            ArrangementEntryData(
                entryId = "chorus",
                name = "Same",
                startMs = 20_000L,
                endMs = 22_000L,
                repeatCount = 2
            )
        )
    )

    private fun audioUri(label: String): Uri = Mockito.mock(Uri::class.java).also { uri ->
        Mockito.`when`(uri.toString()).thenReturn("file:///music/$label.mp3")
    }
}
