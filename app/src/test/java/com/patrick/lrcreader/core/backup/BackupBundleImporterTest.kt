package com.patrick.lrcreader.core.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupBundleImporterTest {

    @Test
    fun isBundleFileName_acceptsSplbackupAndSplbackupZip() {
        assertTrue(BackupBundleImporter.isBundleFileName("manual_restore.splbackup"))
        assertTrue(BackupBundleImporter.isBundleFileName("manual_restore.splbackup.zip"))
    }

    @Test
    fun importBundleIfApplicable_importsSingleSmpAndReturnsImportedSongIds() {
        val payload = BackupBundlePayload(
            stateJson = """{"playlists":{"Live":["smp://song_001"]}}""",
            smpFiles = listOf(
                BackupBundleSmpFile(
                    songId = "song_001",
                    entryName = "smp/song_001.smp",
                    bytes = byteArrayOf(1, 2, 3)
                )
            )
        )

        val result = BackupBundleImporter.importBundleIfApplicable(
            fileName = "manual_restore.splbackup",
            readPayload = { payload },
            importSmpFile = { smpFile ->
                BackupBundleSmpImportResult.Success(
                    importedSong = BackupBundleImportedSong(
                        bundleSongId = smpFile.songId,
                        importedSongId = smpFile.songId,
                        storageFolder = "/tmp/${smpFile.songId}"
                    )
                )
            }
        )

        val success = result as BackupBundleImportResult.Success
        assertEquals(listOf("song_001"), success.importedSongIds)
        assertEquals(mapOf("song_001" to "song_001"), success.songIdRemap)
        assertEquals(payload.stateJson, success.stateJson)
    }

    @Test
    fun importBundleIfApplicable_importsSeveralSmpsInManifestOrder() {
        val payload = BackupBundlePayload(
            stateJson = """{"playlists":{"Live":["smp://song_002","smp://song_001"]}}""",
            smpFiles = listOf(
                BackupBundleSmpFile(
                    songId = "song_002",
                    entryName = "smp/song_002.smp",
                    bytes = byteArrayOf(2)
                ),
                BackupBundleSmpFile(
                    songId = "song_001",
                    entryName = "smp/song_001.smp",
                    bytes = byteArrayOf(1)
                )
            )
        )

        val importedOrder = mutableListOf<String>()
        val result = BackupBundleImporter.importBundleIfApplicable(
            fileName = "manual_restore.splbackup",
            readPayload = { payload },
            importSmpFile = { smpFile ->
                importedOrder += smpFile.songId
                BackupBundleSmpImportResult.Success(
                    importedSong = BackupBundleImportedSong(
                        bundleSongId = smpFile.songId,
                        importedSongId = smpFile.songId
                    )
                )
            }
        )

        val success = result as BackupBundleImportResult.Success
        assertEquals(listOf("song_002", "song_001"), importedOrder)
        assertEquals(listOf("song_002", "song_001"), success.importedSongIds)
    }

    @Test
    fun importBundleIfApplicable_rejectsInvalidBundle() {
        val result = BackupBundleImporter.importBundleIfApplicable(
            fileName = "manual_restore.splbackup",
            readPayload = { null },
            importSmpFile = { error("Should not import any SMP for invalid bundle") }
        )

        assertEquals(BackupBundleImportResult.InvalidBundle, result)
    }

    @Test
    fun importBundleIfApplicable_rejectsTotallyWhenOneSmpImportFails() {
        val payload = BackupBundlePayload(
            stateJson = """{"playlists":{"Live":["smp://song_001","smp://song_002"]}}""",
            smpFiles = listOf(
                BackupBundleSmpFile(
                    songId = "song_001",
                    entryName = "smp/song_001.smp",
                    bytes = byteArrayOf(1)
                ),
                BackupBundleSmpFile(
                    songId = "song_002",
                    entryName = "smp/song_002.smp",
                    bytes = byteArrayOf(2)
                )
            )
        )

        val rolledBackSongIds = mutableListOf<String>()
        val result = BackupBundleImporter.importBundleIfApplicable(
            fileName = "manual_restore.splbackup",
            readPayload = { payload },
            importSmpFile = { smpFile ->
                if (smpFile.songId == "song_002") {
                    BackupBundleSmpImportResult.Failure("zip corrompu")
                } else {
                    BackupBundleSmpImportResult.Success(
                        importedSong = BackupBundleImportedSong(
                            bundleSongId = smpFile.songId,
                            importedSongId = smpFile.songId,
                            storageFolder = "/tmp/${smpFile.songId}"
                        )
                    )
                }
            },
            rollbackImportedSong = { importedSong ->
                rolledBackSongIds += importedSong.importedSongId
            }
        )

        val failed = result as BackupBundleImportResult.SmpImportFailed
        assertEquals("song_002", failed.songId)
        assertEquals("zip corrompu", failed.reason)
        assertEquals(listOf("song_001"), rolledBackSongIds)
    }

    @Test
    fun importBundleIfApplicable_returnsNotBundleForLegacyJsonName() {
        val result = BackupBundleImporter.importBundleIfApplicable(
            fileName = "legacy_backup.json",
            readPayload = { error("Should not read bundle payload for legacy json") },
            importSmpFile = { error("Should not import any SMP for legacy json") }
        )

        assertTrue(result is BackupBundleImportResult.NotBundle)
    }
}
