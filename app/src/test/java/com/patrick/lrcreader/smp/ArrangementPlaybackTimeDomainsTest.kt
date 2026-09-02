package com.patrick.lrcreader.smp

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito

class ArrangementPlaybackTimeDomainsTest {

    @Test
    fun repeatedSourceSegmentRoutesNormalParentAssetsAndStructureSeparately() {
        val prepared = repeatedSegmentPlayback(ArrangementAssetTimeDomain.SOURCE)
        val firstOccurrence = requireNotNull(
            prepared.clockSnapshotAt(
                occurrenceIndex = 0,
                localPositionMs = 5_000L,
                sourceDurationMs = 120_000L
            )
        )
        val secondOccurrence = requireNotNull(
            prepared.clockSnapshotAt(
                occurrenceIndex = 1,
                localPositionMs = 5_000L,
                sourceDurationMs = 120_000L
            )
        )

        val first = routeArrangementPlaybackTimeDomains(
            clockSnapshot = firstOccurrence,
            historicalPositionMs = 5_000L,
            historicalDurationMs = 20_000L
        )
        val second = routeArrangementPlaybackTimeDomains(
            clockSnapshot = secondOccurrence,
            historicalPositionMs = 5_000L,
            historicalDurationMs = 20_000L
        )

        assertEquals(65_000L, first.assetPositionMs)
        assertEquals(65_000L, second.assetPositionMs)
        assertEquals(5_000L, first.structurePositionMs)
        assertEquals(25_000L, second.structurePositionMs)
        assertEquals(120_000L, second.assetDurationMs)
        assertEquals(40_000L, second.structureDurationMs)
    }

    @Test
    fun repeatedSourceSegmentKeepsVariantAssetsOnArrangementClock() {
        val prepared = repeatedSegmentPlayback(ArrangementAssetTimeDomain.ARRANGEMENT)
        val snapshot = requireNotNull(
            prepared.clockSnapshotAt(
                occurrenceIndex = 1,
                localPositionMs = 5_000L,
                sourceDurationMs = 120_000L
            )
        )

        val routed = routeArrangementPlaybackTimeDomains(
            clockSnapshot = snapshot,
            historicalPositionMs = 5_000L,
            historicalDurationMs = 20_000L
        )

        assertEquals(5_000L, snapshot.localPositionMs)
        assertEquals(65_000L, snapshot.sourcePositionMs)
        assertEquals(25_000L, snapshot.arrangementPositionMs)
        assertEquals(25_000L, routed.assetPositionMs)
        assertEquals(25_000L, routed.structurePositionMs)
        assertEquals(40_000L, routed.assetDurationMs)
        assertEquals(40_000L, routed.structureDurationMs)
    }

    @Test
    fun continuousPlaybackKeepsHistoricalPositionAndDurationForBothDomains() {
        val routed = routeArrangementPlaybackTimeDomains(
            clockSnapshot = null,
            historicalPositionMs = 12_345L,
            historicalDurationMs = 98_765L
        )

        assertEquals(12_345L, routed.assetPositionMs)
        assertEquals(12_345L, routed.structurePositionMs)
        assertEquals(98_765L, routed.assetDurationMs)
        assertEquals(98_765L, routed.structureDurationMs)
    }

    @Test
    fun preparedDurationOverridePreservesCurrentVariantTrimDuration() {
        val prepared = repeatedSegmentPlayback(ArrangementAssetTimeDomain.ARRANGEMENT)
        val snapshot = requireNotNull(prepared.clockSnapshotAt(1, 0L))

        val routed = routeArrangementPlaybackTimeDomains(
            clockSnapshot = snapshot,
            historicalPositionMs = 0L,
            historicalDurationMs = 35_000L,
            preparedStructureDurationMs = 35_000L
        )

        assertEquals(35_000L, routed.assetDurationMs)
        assertEquals(35_000L, routed.structureDurationMs)
    }

    @Test
    fun occurrenceBoundaryKeepsVariantAssetAndStructureClocksAligned() {
        val prepared = repeatedSegmentPlayback(ArrangementAssetTimeDomain.ARRANGEMENT)
        val firstEnd = requireNotNull(prepared.clockSnapshotAt(0, 20_000L))
        val secondStart = requireNotNull(prepared.clockSnapshotAt(1, 0L))

        val routedFirstEnd = routeArrangementPlaybackTimeDomains(
            firstEnd,
            historicalPositionMs = 20_000L,
            historicalDurationMs = 40_000L
        )
        val routedSecondStart = routeArrangementPlaybackTimeDomains(
            secondStart,
            historicalPositionMs = 0L,
            historicalDurationMs = 40_000L
        )

        assertEquals(20_000L, routedFirstEnd.assetPositionMs)
        assertEquals(20_000L, routedFirstEnd.structurePositionMs)
        assertEquals(routedFirstEnd, routedSecondStart)
    }

    private fun repeatedSegmentPlayback(
        assetTimeDomain: ArrangementAssetTimeDomain
    ): PreparedArrangementPlayback = requireNotNull(
        ArrangementPlaybackPreparer.prepare(
            ownerSongId = "owner",
            audioSourceSongId = "source",
            title = "Repeated segment",
            sourceAudioUri = audioUri(),
            playbackProfile = null,
            assetTimeDomain = assetTimeDomain,
            arrangement = ArrangementData(
                version = 2,
                sourceSongId = "source",
                segments = emptyList(),
                structureSegmentIds = emptyList(),
                entries = listOf(
                    ArrangementEntryData(
                        entryId = "segment",
                        name = "Segment",
                        startMs = 60_000L,
                        endMs = 80_000L,
                        repeatCount = 2
                    )
                )
            )
        )
    )

    private fun audioUri(): Uri = Mockito.mock(Uri::class.java).also { uri ->
        Mockito.`when`(uri.toString()).thenReturn("file:///music/source.mp3")
    }
}
