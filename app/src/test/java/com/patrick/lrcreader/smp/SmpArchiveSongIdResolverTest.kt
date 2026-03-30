package com.patrick.lrcreader.smp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SmpArchiveSongIdResolverTest {

    @Test
    fun readStableSongId_returnsSanitizedConfigId() {
        val archive = archiveWithConfig(
            """
            {
              "id": " Live Demo/01 ",
              "title": "Live Demo"
            }
            """.trimIndent()
        )

        val stableSongId = SmpArchiveSongIdResolver.readStableSongId(ByteArrayInputStream(archive))

        assertEquals("Live_Demo_01", stableSongId)
    }

    @Test
    fun readStableSongId_returnsNullWhenConfigIdIsMissing() {
        val archive = archiveWithConfig(
            """
            {
              "title": "No Stable Id"
            }
            """.trimIndent()
        )

        val stableSongId = SmpArchiveSongIdResolver.readStableSongId(ByteArrayInputStream(archive))

        assertNull(stableSongId)
    }

    private fun archiveWithConfig(configJson: String): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("config.json"))
            zip.write(configJson.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return output.toByteArray()
    }
}
