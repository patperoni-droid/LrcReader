package com.patrick.lrcreader.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class MoreScreenBackupNameTest {

    @Test
    fun defaultNameKeepsCurrentDateBasedFormat() {
        val name = buildDefaultLiveSongsExportName(Date(0L))

        assertTrue(name.matches(Regex("Export_\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}")))
    }

    @Test
    fun unchangedDefaultNameIsPreservedExactly() {
        val defaultName = "Export_2026-07-26_18-30"

        assertEquals(
            defaultName,
            normalizeLiveSongsExportName(defaultName, defaultName)
        )
    }

    @Test
    fun customNameIsPreserved() {
        assertEquals(
            "Avant mise à jour 0.4.3",
            normalizeLiveSongsExportName(
                requestedName = "Avant mise à jour 0.4.3",
                defaultName = "Export_2026-07-26_18-30"
            )
        )
    }

    @Test
    fun blankNameFallsBackToGeneratedDefault() {
        val defaultName = "Export_2026-07-26_18-30"

        assertEquals(
            defaultName,
            normalizeLiveSongsExportName("   ", defaultName)
        )
    }

    @Test
    fun pathSeparatorsCannotCreateNestedBackupFolders() {
        assertEquals(
            "LIVE-TEST-Variantes",
            normalizeLiveSongsExportName(
                requestedName = "LIVE/TEST\\Variantes",
                defaultName = "Export_2026-07-26_18-30"
            )
        )
    }
}
