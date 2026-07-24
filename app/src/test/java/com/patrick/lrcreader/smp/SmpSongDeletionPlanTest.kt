package com.patrick.lrcreader.smp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmpSongDeletionPlanTest {

    @Test
    fun parentDeletionIncludesItsVariantsOnly() {
        val parent = song("parent")
        val firstVariant = song("variant_b", sourceSongId = parent.id)
        val secondVariant = song("variant_a", sourceSongId = parent.id)
        val unrelated = song("unrelated")
        val songs = listOf(parent, firstVariant, secondVariant, unrelated).associateBy(SongUnit::id)

        val plan = buildSmpSongDeletionPlan(parent.id, songs)

        assertEquals(listOf("parent", "variant_a", "variant_b"), plan?.songs?.map(SongUnit::id))
        assertEquals(2, plan?.variantCount)
    }

    @Test
    fun variantDeletionDoesNotIncludeParentOrSibling() {
        val parent = song("parent")
        val variant = song("variant", sourceSongId = parent.id)
        val sibling = song("sibling", sourceSongId = parent.id)
        val songs = listOf(parent, variant, sibling).associateBy(SongUnit::id)

        val plan = buildSmpSongDeletionPlan(variant.id, songs)

        assertEquals(listOf("variant"), plan?.songs?.map(SongUnit::id))
        assertEquals(1, plan?.variantCount)
    }

    @Test
    fun missingSongDoesNotProduceDeletionPlan() {
        assertNull(buildSmpSongDeletionPlan("missing", emptyMap()))
    }

    private fun song(songId: String, sourceSongId: String? = null): SongUnit {
        return SongUnit(
            id = songId,
            title = songId,
            audioPath = if (sourceSongId == null) "/tmp/$songId/audio.mp3" else null,
            lyricsPath = null,
            chordsPath = null,
            annotationsPath = null,
            midiPath = null,
            dmxPath = null,
            prompterPath = null,
            arrangementSourceSongId = sourceSongId
        )
    }
}
