package com.patrick.lrcreader.smp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArrangementEditingTargetTest {

    @Test
    fun sourceSong_ownsProjectAndAudio() {
        val source = song(id = "source", audioPath = "/tracks/source/audio.mp3")

        val target = resolveArrangementEditingTarget(source, mapOf(source.id to source))

        assertEquals("source", target?.ownerSongId)
        assertEquals("source", target?.sourceSongId)
        assertNull(target?.variantSongId)
    }

    @Test
    fun variant_ownsProjectWhileParentProvidesAudio() {
        val source = song(id = "source", audioPath = "/tracks/source/audio.mp3")
        val variant = song(
            id = "variant",
            audioPath = null,
            arrangementSourceSongId = source.id
        )

        val target = resolveArrangementEditingTarget(
            selectedSong = variant,
            songsById = mapOf(source.id to source, variant.id to variant)
        )

        assertEquals("variant", target?.ownerSongId)
        assertEquals("source", target?.sourceSongId)
        assertEquals("variant", target?.variantSongId)
    }

    @Test
    fun orphanVariant_isRejected() {
        val variant = song(
            id = "variant",
            audioPath = null,
            arrangementSourceSongId = "missing"
        )

        assertNull(resolveArrangementEditingTarget(variant, mapOf(variant.id to variant)))
    }

    private fun song(
        id: String,
        audioPath: String?,
        arrangementSourceSongId: String? = null
    ): SongUnit = SongUnit(
        id = id,
        title = id,
        audioPath = audioPath,
        lyricsPath = null,
        chordsPath = null,
        annotationsPath = null,
        midiPath = null,
        dmxPath = null,
        prompterPath = null,
        arrangementSourceSongId = arrangementSourceSongId
    )
}
