package com.patrick.lrcreader.smp

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class PreparedArrangementPlaybackTest {

    @Test
    fun normalSongPreparation_expandsOneAndManyRepeatsFromOneAudioSource() {
        val sourceUri = audioUri("normal")

        val prepared = requireNotNull(
            ArrangementPlaybackPreparer.prepare(
                ownerSongId = "song_normal",
                audioSourceSongId = "song_normal",
                title = "Normal",
                sourceAudioUri = sourceUri,
                playbackProfile = null,
                assetTimeDomain = ArrangementAssetTimeDomain.SOURCE,
                arrangement = arrangement(sourceSongId = "song_normal")
            )
        )

        assertEquals("song_normal", prepared.ownerSongId)
        assertEquals("song_normal", prepared.audioSourceSongId)
        assertEquals(ArrangementAssetTimeDomain.SOURCE, prepared.assetTimeDomain)
        assertEquals(listOf("intro", "chorus", "chorus"), prepared.occurrences.map { it.entryId })
        assertEquals(listOf(1, 2, 2), prepared.occurrences.map { it.repeatCount })
        assertEquals(listOf(0, 0, 1), prepared.occurrences.map { it.repeatIndex })
        assertEquals(listOf(0L, 1_000L, 3_000L), prepared.occurrences.map { it.arrangementStartMs })
        assertEquals(listOf("intro", "chorus"), prepared.navigationItems.map { it.entryId })
        assertEquals(listOf(1, 2), prepared.navigationItems.map { it.repeatCount })
        assertTrue(prepared.mediaItems.all { mediaItem ->
            mediaItem.localConfiguration?.uri === sourceUri
        })
    }

    @Test
    fun parentSongPreparation_keepsTheParentAsOwnerAndAudioSource() {
        val parentUri = audioUri("parent")

        val prepared = requireNotNull(
            ArrangementPlaybackPreparer.prepare(
                ownerSongId = "parent_song",
                audioSourceSongId = "parent_song",
                title = "Parent with child variants",
                sourceAudioUri = parentUri,
                playbackProfile = null,
                assetTimeDomain = ArrangementAssetTimeDomain.SOURCE,
                arrangement = arrangement(sourceSongId = "parent_song")
            )
        )

        assertEquals("parent_song", prepared.ownerSongId)
        assertEquals("parent_song", prepared.audioSourceSongId)
        assertEquals(ArrangementAssetTimeDomain.SOURCE, prepared.assetTimeDomain)
        assertTrue(prepared.occurrences.all { occurrence ->
            occurrence.id.ownerSongId == "parent_song" &&
                occurrence.groupKey.ownerSongId == "parent_song"
        })
        assertTrue(prepared.mediaItems.all { mediaItem ->
            mediaItem.localConfiguration?.uri === parentUri
        })
    }

    @Test
    fun variantAdapter_preservesLegacyRuntimeClipsOrderKeysDurationAndProfile() {
        val parentUri = audioUri("variant-parent")
        val profile = SmpConfig.PlaybackConfig(
            trimStartMs = null,
            trimEndMs = null,
            tempo = 1.1f,
            pitchSemi = 2,
            volumeDb = -3
        )

        val prepared = requireNotNull(
            prepareVirtualArrangementPlayback(
                variantSongId = "variant_song",
                title = "Variant",
                sourceSongId = "parent_song",
                sourceAudioUri = parentUri,
                playbackProfile = profile,
                arrangement = arrangement(sourceSongId = "parent_song")
            )
        )

        assertEquals("variant_song", prepared.variantSongId)
        assertEquals("parent_song", prepared.sourceSongId)
        assertEquals("file:///music/variant-parent.mp3", prepared.sourceAudioUri)
        assertSame(profile, prepared.playbackProfile)
        assertEquals(ArrangementAssetTimeDomain.ARRANGEMENT, prepared.assetTimeDomain)
        assertEquals(
            listOf("variant_song:0:0", "variant_song:2:0", "variant_song:2:1"),
            prepared.livePlan.occurrences.map { occurrence -> occurrence.key }
        )
        assertEquals(
            listOf("Intro" to "amber", "Chorus" to "blue", "Chorus" to "blue"),
            prepared.livePlan.occurrences.map { occurrence ->
                occurrence.label to occurrence.color
            }
        )
        assertEquals(listOf(1_000L, 2_000L, 2_000L), prepared.occurrenceDurationsMs)
        assertEquals(5_000L, prepared.durationMs)
        assertEquals(
            listOf(10_000L to 11_000L, 20_000L to 22_000L, 20_000L to 22_000L),
            prepared.mediaItems.map { mediaItem ->
                mediaItem.clippingConfiguration.startPositionMs to
                    mediaItem.clippingConfiguration.endPositionMs
            }
        )
        assertTrue(prepared.mediaItems.all { mediaItem ->
            mediaItem.localConfiguration?.uri === parentUri
        })
        assertEquals(listOf(0L, 1_000L), prepared.navigationItems.map { it.navigationPositionMs })
    }

    @Test
    fun identitiesUseOwnerEntryAndRepeatAndGroupsDoNotUsePresentationData() {
        val first = requireNotNull(
            ArrangementPlaybackPreparer.prepare(
                ownerSongId = "owner",
                audioSourceSongId = "source",
                title = "First title",
                sourceAudioUri = audioUri("first-file"),
                playbackProfile = null,
                assetTimeDomain = ArrangementAssetTimeDomain.ARRANGEMENT,
                arrangement = sameLabelArrangement("source")
            )
        )
        val second = requireNotNull(
            ArrangementPlaybackPreparer.prepare(
                ownerSongId = "owner",
                audioSourceSongId = "source",
                title = "Different title",
                sourceAudioUri = audioUri("renamed-file"),
                playbackProfile = null,
                assetTimeDomain = ArrangementAssetTimeDomain.ARRANGEMENT,
                arrangement = sameLabelArrangement("source")
            )
        )

        assertEquals(first.occurrences.map { it.id }, second.occurrences.map { it.id })
        assertEquals(first.occurrences.map { it.groupKey }, second.occurrences.map { it.groupKey })
        assertEquals(first.occurrences.size, first.occurrences.map { it.id }.toSet().size)
        assertNotEquals(first.occurrences[0].id, first.occurrences[1].id)
        assertEquals(first.occurrences[0].groupKey, first.occurrences[1].groupKey)
        assertNotEquals(first.occurrences[0].groupKey, first.occurrences[2].groupKey)
        assertEquals("Même nom", first.occurrences[0].label)
        assertEquals("Même nom", first.occurrences[2].label)
    }

    @Test
    fun timeConversionsRequireAnOccurrenceToResolveRepeatedSourceTime() {
        val prepared = requireNotNull(
            ArrangementPlaybackPreparer.prepare(
                ownerSongId = "variant",
                audioSourceSongId = "parent",
                title = "Variant",
                sourceAudioUri = audioUri("time"),
                playbackProfile = null,
                assetTimeDomain = ArrangementAssetTimeDomain.ARRANGEMENT,
                arrangement = arrangement(sourceSongId = "parent")
            )
        )
        val firstChorus = prepared.occurrences[1]
        val secondChorus = prepared.occurrences[2]

        assertEquals(20_000L, secondChorus.sourcePositionMs(0L))
        assertEquals(22_000L, secondChorus.sourcePositionMs(secondChorus.durationMs))
        assertEquals(3_000L, secondChorus.arrangementPositionMs(0L))
        assertEquals(5_000L, secondChorus.arrangementPositionMs(secondChorus.durationMs))
        assertEquals(500L, secondChorus.localPositionFromSourceMs(20_500L))
        assertEquals(500L, secondChorus.localPositionFromArrangementMs(3_500L))
        assertNull(secondChorus.localPositionFromSourceMs(19_999L))
        assertNull(secondChorus.localPositionFromArrangementMs(5_001L))
        assertEquals(
            1_500L,
            prepared.arrangementPositionFromSourceMs(firstChorus.id, 20_500L)
        )
        assertEquals(
            3_500L,
            prepared.arrangementPositionFromSourceMs(secondChorus.id, 20_500L)
        )
        assertNull(
            prepared.arrangementPositionFromSourceMs(
                occurrenceId = ArrangementPlaybackOccurrenceId("variant", "missing", 0),
                sourcePositionMs = 20_500L
            )
        )
    }

    private fun arrangement(sourceSongId: String): ArrangementData = ArrangementData(
        version = 2,
        sourceSongId = sourceSongId,
        segments = emptyList(),
        structureSegmentIds = emptyList(),
        entries = listOf(
            ArrangementEntryData(
                entryId = "intro",
                name = "Intro",
                startMs = 10_000L,
                endMs = 11_000L,
                repeatCount = 1,
                color = "amber"
            ),
            ArrangementEntryData(
                entryId = "muted",
                name = "Muted",
                startMs = 30_000L,
                endMs = 31_000L,
                muted = true
            ),
            ArrangementEntryData(
                entryId = "chorus",
                name = "Chorus",
                startMs = 20_000L,
                endMs = 22_000L,
                repeatCount = 2,
                color = "blue"
            )
        )
    )

    private fun sameLabelArrangement(sourceSongId: String): ArrangementData = ArrangementData(
        version = 2,
        sourceSongId = sourceSongId,
        segments = emptyList(),
        structureSegmentIds = emptyList(),
        entries = listOf(
            ArrangementEntryData("entry_a", "Même nom", 1_000L, 2_000L, repeatCount = 2),
            ArrangementEntryData("entry_b", "Même nom", 3_000L, 4_000L)
        )
    )

    private fun audioUri(label: String): Uri = Mockito.mock(Uri::class.java).also { uri ->
        Mockito.`when`(uri.toString()).thenReturn("file:///music/$label.mp3")
    }
}
