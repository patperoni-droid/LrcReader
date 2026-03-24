package com.patrick.lrcreader.core.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupBundleIoTest {

    @Test
    fun manifestRoundTrip_preservesFields() {
        val manifest = BackupBundleManifest(
            stateEntry = "state.json",
            smpDir = "smp",
            songs = listOf(
                BackupBundleSongEntry(
                    songId = "song_001",
                    entry = "smp/song_001.smp"
                )
            )
        )

        val restored = BackupBundleManifest.fromJsonOrNull(manifest.toJsonString())

        assertNotNull(restored)
        assertEquals(manifest, restored)
    }

    @Test
    fun bundleRoundTrip_restoresStateAndSmpFiles() {
        val payload = BackupBundlePayload(
            stateJson = """{"playlists":{"Live":["smp://song_001"]}}""",
            smpFiles = listOf(
                BackupBundleSmpFile(
                    songId = "song_001",
                    entryName = "smp/song_001.smp",
                    bytes = "fake-smp-data".toByteArray(Charsets.UTF_8)
                )
            )
        )

        val output = ByteArrayOutputStream()
        BackupBundleIo.write(output, payload)

        val restored = BackupBundleIo.readOrNull(
            ByteArrayInputStream(output.toByteArray())
        )

        assertNotNull(restored)
        assertEquals(payload.stateJson, restored?.stateJson)
        assertEquals(1, restored?.manifest?.songs?.size)
        assertEquals("song_001", restored?.manifest?.songs?.first()?.songId)
        assertEquals("smp/song_001.smp", restored?.smpFiles?.first()?.entryName)
        assertArrayEquals(payload.smpFiles.first().bytes, restored?.smpFiles?.first()?.bytes)
    }

    @Test
    fun readOrNull_returnsNullWhenRequiredSmpEntryIsMissing() {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zipOutput ->
            zipOutput.putNextEntry(ZipEntry(BACKUP_BUNDLE_MANIFEST_ENTRY))
            zipOutput.write(
                """
                {
                  "format": "$BACKUP_BUNDLE_FORMAT",
                  "version": $BACKUP_BUNDLE_VERSION,
                  "stateEntry": "state.json",
                  "smpDir": "smp",
                  "songs": [
                    { "songId": "song_001", "entry": "smp/song_001.smp" }
                  ]
                }
                """.trimIndent().toByteArray(Charsets.UTF_8)
            )
            zipOutput.closeEntry()

            zipOutput.putNextEntry(ZipEntry("state.json"))
            zipOutput.write("""{"playlists":{}}""".toByteArray(Charsets.UTF_8))
            zipOutput.closeEntry()
        }

        val restored = BackupBundleIo.readOrNull(
            ByteArrayInputStream(output.toByteArray())
        )

        assertNull(restored)
    }
}
