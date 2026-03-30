package com.patrick.lrcreader.smp

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class SmpUserArchiveRebuilderTest {

    @Test
    fun buildPartialSyncPlan_importsAllArchivesWhenRuntimeIsEmpty() {
        val plan = SmpUserArchiveRebuilder.buildPartialSyncPlan(
            runtimeSongIds = emptySet(),
            candidates = listOf(
                archiveCandidate("content://archives/A", "song_A"),
                archiveCandidate("content://archives/B", "song_B")
            )
        )

        assertEquals(listOf("song_A", "song_B"), plan.archivesToImport.mapNotNull { it.stableSongId })
        assertTrue(plan.skippedInvalidArchives.isEmpty())
        assertTrue(plan.skippedDuplicateSongIds.isEmpty())
    }

    @Test
    fun buildPartialSyncPlan_importsOnlyMissingArchivesAndSkipsAmbiguousOnes() {
        val plan = SmpUserArchiveRebuilder.buildPartialSyncPlan(
            runtimeSongIds = setOf("song_A"),
            candidates = listOf(
                archiveCandidate("content://archives/A", "song_A"),
                archiveCandidate("content://archives/B", "song_B"),
                archiveCandidate("content://archives/B_legacy", "song_B"),
                archiveCandidate("content://archives/invalid", null)
            )
        )

        assertEquals(listOf("song_B"), plan.archivesToImport.mapNotNull { it.stableSongId })
        assertEquals(
            listOf("content://archives/invalid"),
            plan.skippedInvalidArchives.map { it.toString() }
        )
        assertEquals(setOf("song_B"), plan.skippedDuplicateSongIds)
    }

    private fun archiveCandidate(uri: String, stableSongId: String?) = SmpUserArchiveCandidate(
        archiveUri = testUri(uri),
        stableSongId = stableSongId
    )

    private fun testUri(value: String): Uri {
        val uri = Mockito.mock(Uri::class.java)
        Mockito.`when`(uri.toString()).thenReturn(value)
        return uri
    }
}
