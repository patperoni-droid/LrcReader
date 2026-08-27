package com.patrick.lrcreader.ui

import android.content.Context
import android.content.SharedPreferences
import com.patrick.lrcreader.smp.SmpFamilyAudioHashCache
import com.patrick.lrcreader.smp.SmpFamilyFingerprintResult
import com.patrick.lrcreader.smp.SongUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito

class LibraryUpdateV0Test {

    @Test
    fun persistedPermissionExactlyOnWorkingFolderIsAccepted() {
        assertTrue(
            permissionCovers(
                permissionTreeDocumentId = "primary:Music/Backup",
                folderDocumentId = "primary:Music/Backup"
            )
        )
    }

    @Test
    fun persistedPermissionOnParentTreeCoversWorkingChildFolder() {
        assertTrue(
            permissionCovers(
                permissionTreeDocumentId = "primary:Music",
                folderDocumentId = "primary:Music/Backup"
            )
        )
    }

    @Test
    fun differentChildUriRemainsCoveredByRecordedParentPermission() {
        assertTrue(
            permissionCovers(
                permissionTreeDocumentId = "primary:Music",
                folderDocumentId = "primary:Music/Export_2026-08-10"
            )
        )
    }

    @Test
    fun readOnlyPermissionIsRejected() {
        assertFalse(
            permissionCovers(
                permissionTreeDocumentId = "primary:Music",
                folderDocumentId = "primary:Music/Backup",
                permissionCanWrite = false
            )
        )
    }

    @Test
    fun unrelatedOrLostPermissionIsRejected() {
        assertFalse(
            permissionCovers(
                permissionTreeDocumentId = "primary:Documents",
                folderDocumentId = "primary:Music/Backup"
            )
        )
    }

    @Test
    fun deletedWorkingFolderIsRejectedEvenWithParentPermission() {
        assertFalse(
            isResolvedSafFolderUsable(
                hasWritableTreePermission = true,
                folderExistsAndIsDirectory = false
            )
        )
    }

    @Test
    fun providerCanWriteFalseDoesNotOverridePersistedWriteAndExistingFolder() {
        assertTrue(
            isResolvedSafFolderUsable(
                hasWritableTreePermission = true,
                folderExistsAndIsDirectory = true
            )
        )
    }

    @Test
    fun currentAndPreviousV0FolderUriShapesResolveSameDocumentId() {
        val currentDocumentId = storedSafFolderDocumentId(
            documentId = "primary:Music/Backup",
            treeDocumentId = "primary:Music"
        )
        val previousV0DocumentId = storedSafFolderDocumentId(
            documentId = null,
            treeDocumentId = "primary:Music/Backup"
        )

        assertEquals("primary:Music/Backup", currentDocumentId)
        assertEquals(currentDocumentId, previousV0DocumentId)
    }

    @Test
    fun initialStateWithoutSuccessfulBackupKeepsUpdateUnavailable() {
        assertFalse(isLibraryUpdateAvailable(null))
    }

    @Test
    fun successfulFirstBackupRegistersExactWorkingFolder() {
        var saved: LibraryUpdateReference? = null

        val reference = registerSuccessfulLibraryBackupV0(
            treeUri = "content://root",
            folderUri = "content://root/Export_2026",
            expectedFamilyCount = 1,
            exportedArchivesBySongId = mapOf("parent" to "content://archive/parent"),
            failureCount = 0,
            saveReference = { saved = it; true }
        )

        assertNotNull(reference)
        assertEquals("content://root/Export_2026", saved?.folderUri)
        assertEquals("content://archive/parent", saved?.archivesBySongId?.get("parent"))
    }

    @Test
    fun successfulBackupMakesSharedButtonStateAvailableImmediately() {
        val successfulReference = reference(mapOf("parent" to "archive-parent"))

        val composeReference = libraryUpdateReferenceAfterBackup(
            currentReference = null,
            successfulBackupReference = successfulReference
        )

        assertEquals(successfulReference, composeReference)
        assertTrue(isLibraryUpdateAvailable(composeReference))
    }

    @Test
    fun failedFirstBackupDoesNotRegisterWorkingFolder() {
        var saveCalled = false

        val reference = registerSuccessfulLibraryBackupV0(
            treeUri = "content://root",
            folderUri = "content://root/Export_2026",
            expectedFamilyCount = 1,
            exportedArchivesBySongId = emptyMap(),
            failureCount = 1,
            saveReference = { saveCalled = true; true }
        )

        assertNull(reference)
        assertFalse(saveCalled)
    }

    @Test
    fun failedBackupDoesNotActivateSharedButtonState() {
        val composeReference = libraryUpdateReferenceAfterBackup(
            currentReference = null,
            successfulBackupReference = null
        )

        assertNull(composeReference)
        assertFalse(isLibraryUpdateAvailable(composeReference))
    }

    @Test
    fun failedLaterBackupDoesNotHidePreviouslyAvailableButton() {
        val previousReference = reference(mapOf("parent" to "archive-parent"))

        val composeReference = libraryUpdateReferenceAfterBackup(
            currentReference = previousReference,
            successfulBackupReference = null
        )

        assertEquals(previousReference, composeReference)
        assertTrue(isLibraryUpdateAvailable(composeReference))
    }

    @Test
    fun encodedReferenceSurvivesSimulatedRestart() {
        val before = reference(mapOf("parent" to "archive-old"))
        var persistedJson: String? = null
        val context = Mockito.mock(Context::class.java)
        val preferences = Mockito.mock(SharedPreferences::class.java)
        val editor = Mockito.mock(SharedPreferences.Editor::class.java)
        Mockito.`when`(context.getSharedPreferences(Mockito.anyString(), Mockito.anyInt()))
            .thenReturn(preferences)
        Mockito.`when`(preferences.edit()).thenReturn(editor)
        Mockito.`when`(editor.putString(Mockito.anyString(), Mockito.anyString()))
            .thenAnswer { invocation ->
                persistedJson = invocation.getArgument(1)
                editor
            }
        Mockito.`when`(editor.commit()).thenReturn(true)
        Mockito.`when`(preferences.getString(Mockito.anyString(), Mockito.isNull()))
            .thenAnswer { persistedJson }

        assertTrue(LibraryUpdateReferenceStore.save(context, before))
        val after = LibraryUpdateReferenceStore.load(context)

        assertEquals(before, after)
    }

    @Test
    fun sameSongIdReplacesKnownArchive() {
        val gateway = FakeGateway(mapOf("archive-old" to "parent"))

        val result = update(reference(mapOf("parent" to "archive-old")), listOf(song("parent")), gateway)

        assertEquals(1, result.updatedCount)
        assertEquals(0, result.addedCount)
        assertFalse(gateway.archives.containsKey("archive-old"))
        assertEquals("parent", gateway.archives[result.reference.archivesBySongId.getValue("parent")]?.songId)
    }

    @Test
    fun newSongIdAddsArchive() {
        val gateway = FakeGateway()

        val result = update(reference(), listOf(song("new-parent")), gateway)

        assertEquals(0, result.updatedCount)
        assertEquals(1, result.addedCount)
        assertTrue(result.reference.archivesBySongId.containsKey("new-parent"))
    }

    @Test
    fun familyAbsentFromRuntimeIsPreserved() {
        val gateway = FakeGateway(
            mapOf("archive-a" to "a", "archive-absent" to "absent")
        )
        val before = reference(
            mapOf("a" to "archive-a", "absent" to "archive-absent")
        )

        val result = update(before, listOf(song("a")), gateway)

        assertEquals("archive-absent", result.reference.archivesBySongId["absent"])
        assertEquals("absent", gateway.archives["archive-absent"]?.songId)
    }

    @Test
    fun changedTitleStillReplacesBySongId() {
        val gateway = FakeGateway(mapOf("archive-old" to "same-id"))

        val result = update(
            reference(mapOf("same-id" to "archive-old")),
            listOf(song("same-id", title = "Titre totalement modifié")),
            gateway
        )

        assertEquals(1, result.updatedCount)
        assertEquals("same-id", gateway.publishedSongs.single().id)
    }

    @Test
    fun exportFailureKeepsPreviousArchive() {
        val gateway = FakeGateway(mapOf("archive-old" to "parent")).apply {
            failedSongIds += "parent"
        }
        val before = reference(mapOf("parent" to "archive-old"))

        val result = update(before, listOf(song("parent")), gateway)

        assertEquals(1, result.failedCount)
        assertEquals(before, result.reference)
        assertEquals("parent", gateway.archives["archive-old"]?.songId)
    }

    @Test
    fun inaccessibleFolderPerformsNoWrite() {
        val gateway = FakeGateway(mapOf("archive-old" to "parent")).apply { writable = false }
        var saveCalled = false

        val result = updateLibraryFamiliesV0(
            reference = reference(mapOf("parent" to "archive-old")),
            runtimeSongs = listOf(song("parent")),
            gateway = gateway,
            saveReference = { saveCalled = true; true }
        )

        assertTrue(result.folderInaccessible)
        assertTrue(gateway.publishedSongs.isEmpty())
        assertFalse(saveCalled)
    }

    @Test
    fun variantsTravelOnlyThroughTheirParentFamilyArchive() {
        val gateway = FakeGateway()
        val songs = listOf(
            song("parent"),
            song("variant-a", sourceSongId = "parent"),
            song("variant-b", sourceSongId = "parent")
        )

        val result = update(reference(), songs, gateway)

        assertEquals(listOf("parent"), gateway.publishedSongs.map { it.id })
        assertEquals(setOf("parent"), result.reference.archivesBySongId.keys)
    }

    @Test
    fun currentLyricsPathIsPassedToCertifiedFamilyExporter() {
        val gateway = FakeGateway(mapOf("archive-old" to "parent"))
        val edited = song("parent", lyricsPath = "/runtime/parent/lyrics-modified.lrc")

        update(reference(mapOf("parent" to "archive-old")), listOf(edited), gateway)

        assertEquals("/runtime/parent/lyrics-modified.lrc", gateway.publishedSongs.single().lyricsPath)
    }

    @Test
    fun updateDoesNotTouchGlobalBackupFiles() {
        val gateway = FakeGateway(mapOf("archive-old" to "parent"))
        val globalFiles = linkedMapOf(
            "state.json" to "state-before",
            "prompters.json" to "prompters-before",
            "notes.json" to "notes-before"
        )

        update(reference(mapOf("parent" to "archive-old")), listOf(song("parent")), gateway)

        assertEquals(
            mapOf(
                "state.json" to "state-before",
                "prompters.json" to "prompters-before",
                "notes.json" to "notes-before"
            ),
            globalFiles
        )
    }

    @Test
    fun referenceWriteFailureKeepsOldArchiveAndDiscardsNewOne() {
        val gateway = FakeGateway(mapOf("archive-old" to "parent"))
        val before = reference(mapOf("parent" to "archive-old"))

        val result = updateLibraryFamiliesV0(
            reference = before,
            runtimeSongs = listOf(song("parent")),
            gateway = gateway,
            saveReference = { false }
        )

        assertEquals(before, result.reference)
        assertEquals("parent", gateway.archives["archive-old"]?.songId)
        assertEquals(setOf("archive-old"), gateway.archives.keys)
    }

    @Test
    fun oldArchiveDeletionFailureRollsBackReferenceAndDiscardsPublishedArchive() {
        val gateway = FakeGateway(mapOf("archive-old" to "parent")).apply {
            undeletableUris += "archive-old"
        }
        val before = reference(mapOf("parent" to "archive-old"))

        val result = update(before, listOf(song("parent")), gateway)

        assertEquals(1, result.failedCount)
        assertEquals(0, result.updatedCount)
        assertEquals(before, result.reference)
        assertEquals(setOf("archive-old"), gateway.archives.keys)
    }

    @Test
    fun ambiguousCleanupFailureIsReportedAndNeverCountedAsSuccessfulUpdate() {
        val gateway = FakeGateway(mapOf("archive-old" to "parent")).apply {
            undeletableUris += "archive-old"
            undeletablePublishedArchives = true
        }
        val before = reference(mapOf("parent" to "archive-old"))

        val result = update(before, listOf(song("parent")), gateway)

        assertEquals(1, result.failedCount)
        assertEquals(0, result.updatedCount)
        assertEquals(before, result.reference)
        assertEquals(2, gateway.archives.size)
    }

    @Test
    fun temporaryAndRollbackArchivesAreIgnoredByRestoreScan() {
        assertTrue(isMacBackupNoiseFile(".lyrics_parent_update.smp.part"))
        assertTrue(isMacBackupNoiseFile(".lyrics_parent_update.smp.rollback"))
        assertFalse(isMacBackupNoiseFile("lyrics_parent_update.smp"))
    }

    @Test
    fun mismatchedMappedArchiveIsNeverDeleted() {
        val gateway = FakeGateway(mapOf("archive-other" to "another-song"))
        val before = reference(mapOf("parent" to "archive-other"))

        val result = update(before, listOf(song("parent")), gateway)

        assertEquals(1, result.failedCount)
        assertEquals(before, result.reference)
        assertEquals("another-song", gateway.archives["archive-other"]?.songId)
        assertEquals(setOf("archive-other"), gateway.archives.keys)
    }

    @Test
    fun legacyBackupWithoutV0ReferenceRemainsAnUnmodifiedRestoreInput() {
        assertNull(LibraryUpdateReferenceCodec.decode(null))
        assertNull(LibraryUpdateReferenceCodec.decode(""))
        assertNull(LibraryUpdateReferenceCodec.decode("{\"state\":\"legacy\"}"))
    }

    @Test
    fun unchangedFamilyIsNotExported() {
        val gateway = FakeGateway(mapOf("archive-old" to "parent"))
        val fingerprint = fingerprint("parent", 'a')
        val before = reference(
            archives = mapOf("parent" to "archive-old"),
            fingerprints = mapOf("parent" to fingerprint.fingerprint),
            audioHashes = mapOf("parent" to requireNotNull(fingerprint.audioHashCache))
        )

        val result = updateV1(
            before,
            listOf(song("parent")),
            gateway,
            fingerprintFor = { fingerprint }
        )

        assertEquals(0, result.updatedCount)
        assertEquals(0, result.addedCount)
        assertEquals(1, result.unchangedCount)
        assertEquals(0, result.failedCount)
        assertTrue(gateway.publishedSongs.isEmpty())
        assertEquals(before, result.reference)
    }

    @Test
    fun matchingFamilyFingerprintNeedsNoBackupUpdate() {
        val unchanged = fingerprint("parent", 'a')
        val reference = reference(
            archives = mapOf("parent" to "content://backup/parent.smp"),
            fingerprints = mapOf("parent" to unchanged.fingerprint),
            audioHashes = mapOf("parent" to requireNotNull(unchanged.audioHashCache))
        )

        assertFalse(
            isLibraryBackupUpdateNeeded(
                reference = reference,
                runtimeSongs = listOf(song("parent")),
                calculateFingerprint = { _, _ -> unchanged }
            )
        )
    }

    @Test
    fun changedOrMissingFamilyNeedsBackupUpdate() {
        val previous = fingerprint("parent", 'a')
        val changed = fingerprint("parent", 'b')
        val reference = reference(
            archives = mapOf("parent" to "content://backup/parent.smp"),
            fingerprints = mapOf("parent" to previous.fingerprint),
            audioHashes = mapOf("parent" to requireNotNull(previous.audioHashCache))
        )

        assertTrue(
            isLibraryBackupUpdateNeeded(
                reference = reference,
                runtimeSongs = listOf(song("parent")),
                calculateFingerprint = { _, _ -> changed }
            )
        )
        assertTrue(
            isLibraryBackupUpdateNeeded(
                reference = reference,
                runtimeSongs = listOf(song("parent"), song("new-parent")),
                calculateFingerprint = { song, _ ->
                    if (song.id == "parent") previous else changed
                }
            )
        )
    }

    @Test
    fun missingOrForeignBackupArchiveNeedsUpdate() {
        val unchanged = fingerprint("parent", 'a')
        val reference = reference(
            archives = mapOf("parent" to "content://backup/parent.smp"),
            fingerprints = mapOf("parent" to unchanged.fingerprint),
            audioHashes = mapOf("parent" to requireNotNull(unchanged.audioHashCache))
        )

        assertTrue(
            isLibraryBackupUpdateNeeded(
                reference = reference,
                runtimeSongs = listOf(song("parent")),
                calculateFingerprint = { _, _ -> unchanged },
                isArchiveOwnedBySong = { _, _ -> false }
            )
        )
    }

    @Test
    fun changedFamilyIsExportedAndItsFingerprintIsReplaced() {
        val gateway = FakeGateway(mapOf("archive-old" to "parent"))
        val old = fingerprint("parent", 'a')
        val changed = fingerprint("parent", 'b')
        val before = reference(
            archives = mapOf("parent" to "archive-old"),
            fingerprints = mapOf("parent" to old.fingerprint),
            audioHashes = mapOf("parent" to requireNotNull(old.audioHashCache))
        )

        val result = updateV1(
            before,
            listOf(song("parent")),
            gateway,
            fingerprintFor = { changed }
        )

        assertEquals(1, result.updatedCount)
        assertEquals(0, result.unchangedCount)
        assertEquals(listOf("parent"), gateway.publishedSongs.map(SongUnit::id))
        assertEquals(changed.fingerprint, result.reference.fingerprintsBySongId["parent"])
        assertEquals(changed.audioHashCache, result.reference.audioHashesBySongId["parent"])
        assertFalse(
            isLibraryBackupUpdateNeeded(
                reference = result.reference,
                runtimeSongs = listOf(song("parent")),
                calculateFingerprint = { _, _ -> changed },
                isArchiveOwnedBySong = gateway::isArchiveOwnedBySong
            )
        )
    }

    @Test
    fun missingFingerprintFromV0ForcesOneExportThenNextUpdateSkips() {
        val gateway = FakeGateway(mapOf("archive-old" to "parent"))
        val current = fingerprint("parent", 'a')
        var saved = reference(mapOf("parent" to "archive-old"))

        val first = updateV1(saved, listOf(song("parent")), gateway, { current }) { saved = it }
        val publishedAfterFirst = gateway.publishedSongs.size
        val second = updateV1(
            saved,
            listOf(song("parent")),
            gateway,
            fingerprintFor = { current }
        )

        assertEquals(1, first.updatedCount)
        assertEquals(1, second.unchangedCount)
        assertEquals(publishedAfterFirst, gateway.publishedSongs.size)
    }

    @Test
    fun incompleteAudioCacheForcesSafeReExport() {
        val gateway = FakeGateway(mapOf("archive-old" to "parent"))
        val current = fingerprint("parent", 'a')
        val before = reference(
            archives = mapOf("parent" to "archive-old"),
            fingerprints = mapOf("parent" to current.fingerprint)
        )

        val result = updateV1(
            before,
            listOf(song("parent")),
            gateway,
            fingerprintFor = { current }
        )

        assertEquals(1, result.updatedCount)
        assertEquals(0, result.unchangedCount)
        assertEquals(1, gateway.publishedSongs.size)
    }

    @Test
    fun incoherentArchiveMappingForcesReExportWithoutDeletingForeignArchive() {
        val gateway = FakeGateway(mapOf("archive-foreign" to "another-song"))
        val current = fingerprint("parent", 'a')
        val before = reference(
            archives = mapOf("parent" to "archive-foreign"),
            fingerprints = mapOf("parent" to current.fingerprint),
            audioHashes = mapOf("parent" to requireNotNull(current.audioHashCache))
        )

        val result = updateV1(
            before,
            listOf(song("parent")),
            gateway,
            fingerprintFor = { current }
        )

        assertEquals(1, result.updatedCount)
        assertEquals(0, result.failedCount)
        assertEquals("another-song", gateway.archives["archive-foreign"]?.songId)
        assertNotEquals("archive-foreign", result.reference.archivesBySongId["parent"])
    }

    @Test
    fun corruptV1SubIndexesAreIgnoredForSafeReExport() {
        val decoded = requireNotNull(
            LibraryUpdateReferenceCodec.decode(
                """{"treeUri":"tree","folderUri":"folder","archives":{"parent":"archive"},"fingerprints":{"parent":"bad"},"audioHashes":{"parent":{"fileIdentity":"audio","size":1,"lastModified":1,"sha256":"bad"}}}"""
            )
        )

        assertTrue(decoded.fingerprintsBySongId.isEmpty())
        assertTrue(decoded.audioHashesBySongId.isEmpty())
        val gateway = FakeGateway(mapOf("archive" to "parent"))
        val result = updateV1(
            decoded,
            listOf(song("parent")),
            gateway,
            fingerprintFor = { fingerprint("parent", 'a') }
        )
        assertEquals(1, result.updatedCount)
        assertEquals(1, gateway.publishedSongs.size)
    }

    @Test
    fun failedChangedExportKeepsPreviousFingerprintAndArchive() {
        val gateway = FakeGateway(mapOf("archive-old" to "parent")).apply {
            failedSongIds += "parent"
        }
        val old = fingerprint("parent", 'a')
        val changed = fingerprint("parent", 'b')
        val before = reference(
            archives = mapOf("parent" to "archive-old"),
            fingerprints = mapOf("parent" to old.fingerprint),
            audioHashes = mapOf("parent" to requireNotNull(old.audioHashCache))
        )

        val result = updateV1(
            before,
            listOf(song("parent")),
            gateway,
            fingerprintFor = { changed }
        )

        assertEquals(1, result.failedCount)
        assertEquals(before, result.reference)
        assertEquals("parent", gateway.archives["archive-old"]?.songId)
    }

    @Test
    fun failedReferencePublicationKeepsPreviousFingerprintAndArchive() {
        val gateway = FakeGateway(mapOf("archive-old" to "parent"))
        val old = fingerprint("parent", 'a')
        val changed = fingerprint("parent", 'b')
        val before = reference(
            archives = mapOf("parent" to "archive-old"),
            fingerprints = mapOf("parent" to old.fingerprint),
            audioHashes = mapOf("parent" to requireNotNull(old.audioHashCache))
        )

        val result = updateLibraryFamiliesV0(
            reference = before,
            runtimeSongs = listOf(song("parent")),
            gateway = gateway,
            calculateFingerprint = { _, _ -> changed },
            saveReference = { false }
        )

        assertEquals(1, result.failedCount)
        assertEquals(before, result.reference)
        assertEquals(setOf("archive-old"), gateway.archives.keys)
    }

    @Test
    fun fiveHundredFamiliesWithTwoChangesExportExactlyTwoArchives() {
        val songs = (0 until 500).map { song("family-$it") }
        val archives = songs.associate { it.id to "archive-${it.id}" }
        val stored = songs.associate { it.id to fingerprint(it.id, 'a') }
        val runtime = stored.toMutableMap().apply {
            this["family-17"] = fingerprint("family-17", 'b')
            this["family-423"] = fingerprint("family-423", 'c')
        }
        val before = reference(
            archives = archives,
            fingerprints = stored.mapValues { it.value.fingerprint },
            audioHashes = stored.mapValues { requireNotNull(it.value.audioHashCache) }
        )
        val gateway = FakeGateway(archives.entries.associate { (songId, uri) -> uri to songId })

        val result = updateV1(
            before,
            songs,
            gateway,
            fingerprintFor = { runtime.getValue(it.id) }
        )

        assertEquals(2, result.updatedCount)
        assertEquals(498, result.unchangedCount)
        assertEquals(0, result.failedCount)
        assertEquals(setOf("family-17", "family-423"), gateway.publishedSongs.mapTo(linkedSetOf(), SongUnit::id))
    }

    @Test
    fun successfulBackupStoresOnlyFingerprintsOfPublishedFamilies() {
        val parent = fingerprint("parent", 'a')
        val orphan = fingerprint("orphan", 'b')

        val reference = requireNotNull(
            registerSuccessfulLibraryBackupV0(
                treeUri = "tree",
                folderUri = "folder",
                expectedFamilyCount = 1,
                exportedArchivesBySongId = mapOf("parent" to "archive-parent"),
                fingerprintsBySongId = mapOf(
                    "parent" to parent.fingerprint,
                    "orphan" to orphan.fingerprint
                ),
                audioHashesBySongId = mapOf(
                    "parent" to requireNotNull(parent.audioHashCache),
                    "orphan" to requireNotNull(orphan.audioHashCache)
                ),
                failureCount = 0,
                saveReference = { true }
            )
        )

        assertEquals(setOf("parent"), reference.fingerprintsBySongId.keys)
        assertEquals(setOf("parent"), reference.audioHashesBySongId.keys)
    }

    private fun update(
        reference: LibraryUpdateReference,
        songs: List<SongUnit>,
        gateway: FakeGateway
    ): LibraryUpdateResult = updateLibraryFamiliesV0(
        reference = reference,
        runtimeSongs = songs,
        gateway = gateway,
        saveReference = { true }
    )

    private fun updateV1(
        reference: LibraryUpdateReference,
        songs: List<SongUnit>,
        gateway: FakeGateway,
        fingerprintFor: (SongUnit) -> SmpFamilyFingerprintResult,
        saveReference: (LibraryUpdateReference) -> Unit = {}
    ): LibraryUpdateResult = updateLibraryFamiliesV0(
        reference = reference,
        runtimeSongs = songs,
        gateway = gateway,
        calculateFingerprint = { song, _ -> fingerprintFor(song) },
        saveReference = { next -> saveReference(next); true }
    )

    private fun permissionCovers(
        permissionTreeDocumentId: String,
        folderDocumentId: String,
        permissionCanWrite: Boolean = true
    ): Boolean = safTreePermissionCoversFolder(
        recordedRootAuthority = "com.android.externalstorage.documents",
        recordedRootDocumentId = "primary:Music",
        folderAuthority = "com.android.externalstorage.documents",
        folderDocumentId = folderDocumentId,
        permissionAuthority = "com.android.externalstorage.documents",
        permissionTreeDocumentId = permissionTreeDocumentId,
        permissionCanRead = true,
        permissionCanWrite = permissionCanWrite
    )

    private fun reference(
        archives: Map<String, String> = emptyMap(),
        fingerprints: Map<String, String> = emptyMap(),
        audioHashes: Map<String, SmpFamilyAudioHashCache> = emptyMap()
    ) = LibraryUpdateReference(
        treeUri = "content://tree/root",
        folderUri = "content://tree/root/backup",
        archivesBySongId = archives,
        fingerprintsBySongId = fingerprints,
        audioHashesBySongId = audioHashes
    )

    private fun fingerprint(songId: String, marker: Char): SmpFamilyFingerprintResult {
        val hash = marker.toString().repeat(64)
        return SmpFamilyFingerprintResult(
            fingerprint = hash,
            audioHashCache = SmpFamilyAudioHashCache(
                fileIdentity = "/runtime/$songId/audio.mp3",
                size = marker.code.toLong(),
                lastModified = marker.code.toLong(),
                sha256 = hash
            )
        )
    }

    private fun song(
        id: String,
        title: String = id,
        lyricsPath: String? = null,
        sourceSongId: String? = null
    ) = SongUnit(
        id = id,
        title = title,
        audioPath = "/runtime/$id/audio.mp3",
        lyricsPath = lyricsPath,
        chordsPath = null,
        annotationsPath = null,
        midiPath = null,
        dmxPath = null,
        prompterPath = null,
        arrangementSourceSongId = sourceSongId
    )

    private data class FakeArchive(val songId: String, val lyricsPath: String?)

    private class FakeGateway(initialArchives: Map<String, String> = emptyMap()) :
        LibraryUpdateArchiveGateway {
        val archives = initialArchives.mapValuesTo(linkedMapOf()) { FakeArchive(it.value, null) }
        val publishedSongs = mutableListOf<SongUnit>()
        val failedSongIds = mutableSetOf<String>()
        val undeletableUris = mutableSetOf<String>()
        var undeletablePublishedArchives = false
        var writable = true
        private var nextArchive = 1

        override fun isFolderWritable(reference: LibraryUpdateReference): Boolean = writable

        override fun isArchiveOwnedBySong(archiveUri: String, songId: String): Boolean =
            archives[archiveUri]?.songId == songId

        override fun publishFamily(reference: LibraryUpdateReference, song: SongUnit): String? {
            publishedSongs += song
            if (song.id in failedSongIds) return null
            val uri = "archive-new-${nextArchive++}"
            archives[uri] = FakeArchive(song.id, song.lyricsPath)
            return uri
        }

        override fun deleteArchiveIfOwnedBySong(archiveUri: String, songId: String): Boolean {
            if (archives[archiveUri]?.songId != songId) return false
            if (archiveUri in undeletableUris) return false
            if (undeletablePublishedArchives && archiveUri.startsWith("archive-new-")) return false
            archives.remove(archiveUri)
            return true
        }
    }
}
