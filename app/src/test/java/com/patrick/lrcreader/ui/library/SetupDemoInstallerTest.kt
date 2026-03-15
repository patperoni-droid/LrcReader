package com.patrick.lrcreader.ui.library

import org.junit.Assert.assertEquals
import org.junit.Test

class SetupDemoInstallerTest {

    @Test
    fun safMimeTypeForAsset_keepsMp3AudioMimeAndForcesNeutralMimeForLrc() {
        assertEquals("audio/mpeg", safMimeTypeForAsset("One Man Show.mp3", "audio"))
        assertEquals("application/octet-stream", safMimeTypeForAsset("One Man Show-123.lrc", "lyrics"))
        assertEquals("application/octet-stream", safMimeTypeForAsset("One Man Show-123.lrc", "accords"))
    }

    @Test
    fun listDemoAssetFiles_filtersHiddenAndSorts() {
        val files = listDemoAssetFiles(
            arrayOf("The Live Performer.mp3", ".DS_Store", "Guitar Groove Demo.mp3")
        )

        assertEquals(
            listOf("Guitar Groove Demo.mp3", "The Live Performer.mp3"),
            files
        )
    }

    @Test
    fun mergeDemoPlaylistOrder_putsDemoTracksFirstWithoutDuplicates() {
        val merged = mergeDemoPlaylistOrder(
            demoUris = listOf("demo-2", "demo-1", "demo-2"),
            existingUris = listOf("user-1", "demo-1", "user-2")
        )

        assertEquals(
            listOf("demo-2", "demo-1", "user-1", "user-2"),
            merged
        )
    }
}
