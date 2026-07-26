package com.patrick.lrcreader.smp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SmpExporterVariantRequestTest {

    @Test
    fun parentShareKeepsExistingParentExportBehavior() {
        val parent = song(id = "parent")

        val request = SmpExporter.resolveExportRequest(parent) { null }

        assertEquals(parent, request.packageSong)
        assertNull(request.selectedVariantId)
    }

    @Test
    fun variantShareUsesParentPackageAndKeepsVariantIdentity() {
        val parent = song(id = "parent")
        val variant = song(
            id = "variant",
            arrangementSourceSongId = parent.id
        )

        val request = SmpExporter.resolveExportRequest(variant) { songId ->
            parent.takeIf { it.id == songId }
        }

        assertEquals(parent, request.packageSong)
        assertEquals(variant.id, request.selectedVariantId)
    }

    @Test
    fun variantShareFailsWhenParentIsMissing() {
        val variant = song(
            id = "variant",
            arrangementSourceSongId = "missing_parent"
        )

        assertThrows(IllegalStateException::class.java) {
            SmpExporter.resolveExportRequest(variant) { null }
        }
    }

    private fun song(
        id: String,
        arrangementSourceSongId: String? = null
    ): SongUnit = SongUnit(
        id = id,
        title = id,
        audioPath = null,
        lyricsPath = null,
        chordsPath = null,
        annotationsPath = null,
        midiPath = null,
        dmxPath = null,
        prompterPath = null,
        arrangementSourceSongId = arrangementSourceSongId
    )
}
