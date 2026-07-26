package com.patrick.lrcreader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Files

class LrcStorageSmpIdentityTest {

    @Test
    fun resolvesAudioLessVariantFolderFromSmpSongId() {
        val filesDir = Files.createTempDirectory("lrc_storage_variant_").toFile()
        try {
            val variantDir = filesDir.resolve("tracks/variant_01").apply { mkdirs() }
            variantDir.resolve("config.json").writeText("""{"version":1,"id":"variant_01"}""")

            val resolved = LrcStorage.resolveSmpRuntimeSongDir(
                filesDir = filesDir,
                trackUriString = buildSmpItem("variant_01")
            )

            assertEquals(variantDir.canonicalFile, resolved)
        } finally {
            filesDir.deleteRecursively()
        }
    }

    @Test
    fun rejectsMissingOrEscapingSmpSongFolder() {
        val filesDir = Files.createTempDirectory("lrc_storage_variant_invalid_").toFile()
        try {
            filesDir.resolve("tracks").mkdirs()

            assertNull(
                LrcStorage.resolveSmpRuntimeSongDir(
                    filesDir = filesDir,
                    trackUriString = buildSmpItem("missing")
                )
            )
            assertNull(
                LrcStorage.resolveSmpRuntimeSongDir(
                    filesDir = filesDir,
                    trackUriString = buildSmpItem("../outside")
                )
            )
        } finally {
            filesDir.deleteRecursively()
        }
    }
}
