package com.patrick.lrcreader.ui

import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.patrick.lrcreader.core.BackupFolderPrefs
import com.patrick.lrcreader.core.LrcLine
import com.patrick.lrcreader.core.LrcStorage
import com.patrick.lrcreader.core.StorageModePrefs
import com.patrick.lrcreader.core.buildSmpItem
import com.patrick.lrcreader.smp.ArrangementData
import com.patrick.lrcreader.smp.ArrangementEntryData
import com.patrick.lrcreader.smp.ArrangementJsonCodec
import com.patrick.lrcreader.smp.ArrangementVariantStore
import com.patrick.lrcreader.smp.SmpArchiveSongIdResolver
import com.patrick.lrcreader.smp.SmpConfig
import com.patrick.lrcreader.smp.SmpExporter
import com.patrick.lrcreader.smp.SmpFamilyFingerprint
import com.patrick.lrcreader.smp.SmpImporter
import com.patrick.lrcreader.smp.SmpLibraryScanner
import com.patrick.lrcreader.smp.SongUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito
import java.io.File
import java.nio.file.Files
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipFile

class LibraryUpdateLyricsRoundTripTest {

    @Test
    fun lyricsEdit_marksBackupDirtyThenUpdateRestoresCleanStateAndLatestLyrics() = runBlocking {
        val root = Files.createTempDirectory(TEMP_PREFIX).toFile()
        val filesDir = root.resolve("files").apply { mkdirs() }
        val cacheDir = root.resolve("cache").apply { mkdirs() }
        val backupDir = root.resolve("backup").apply { mkdirs() }
        val preferences = linkedMapOf<String, InMemorySharedPreferences>()
        val context = Mockito.mock(Context::class.java)
        val resolver = Mockito.mock(ContentResolver::class.java)
        val clock = AtomicLong(10_000L)
        val workerLooper = Mockito.mock(Looper::class.java)
        val mainLooper = Mockito.mock(Looper::class.java)
        val logMock = Mockito.mockStatic(Log::class.java)
        val clockMock = Mockito.mockStatic(SystemClock::class.java)
        val looperMock = Mockito.mockStatic(Looper::class.java)
        val uriMock = Mockito.mockStatic(Uri::class.java)
        var loggedWriteFailure: Throwable? = null

        try {
            logMock.`when`<Int> {
                Log.e(Mockito.anyString(), Mockito.anyString(), Mockito.nullable(Throwable::class.java))
            }.thenAnswer { invocation ->
                loggedWriteFailure = invocation.getArgument(2)
                0
            }
            logMock.`when`<Int> { Log.e(Mockito.anyString(), Mockito.anyString()) }
                .thenReturn(0)
            Mockito.`when`(context.filesDir).thenReturn(filesDir)
            Mockito.`when`(context.cacheDir).thenReturn(cacheDir)
            Mockito.`when`(context.contentResolver).thenReturn(resolver)
            Mockito.`when`(context.getSharedPreferences(Mockito.anyString(), Mockito.anyInt()))
                .thenAnswer { invocation ->
                    preferences.getOrPut(invocation.getArgument(0)) { InMemorySharedPreferences() }
                }
            Mockito.`when`(resolver.openInputStream(Mockito.any(Uri::class.java)))
                .thenAnswer { invocation ->
                    File(requireNotNull(invocation.getArgument<Uri>(0).path)).inputStream()
                }
            clockMock.`when`<Long> { SystemClock.elapsedRealtime() }
                .thenAnswer { clock.getAndIncrement() }
            looperMock.`when`<Looper?> { Looper.myLooper() }.thenReturn(workerLooper)
            looperMock.`when`<Looper> { Looper.getMainLooper() }.thenReturn(mainLooper)
            uriMock.`when`<Uri> { Uri.parse(Mockito.anyString()) }
                .thenAnswer { invocation -> fakeUri(invocation.getArgument(0)) }
            uriMock.`when`<Uri> { Uri.fromFile(Mockito.any(File::class.java)) }
                .thenAnswer { invocation -> fakeUri("file://${invocation.getArgument<File>(0).absolutePath}") }
            BackupFolderPrefs.saveLibraryRootUri(context, fakeUri("file://${root.absolutePath}"))
            StorageModePrefs.set(context, StorageModePrefs.Mode.INTERNAL)

            val tracksRoot = filesDir.resolve("tracks").apply { mkdirs() }
            writeParent(tracksRoot.resolve(PARENT_ID), PARENT_ID, "Parent", OLD_LRC, OLD_RAW)
            writeVariant(tracksRoot.resolve(VARIANT_ID), OLD_VARIANT_LRC, OLD_VARIANT_RAW)
            val gateway = RealArchiveGateway(context, backupDir)
            val initialParent = requireNotNull(SmpLibraryScanner(context).findSongById(PARENT_ID))
            val oldArchive = requireNotNull(SmpExporter.exportSongUnitToCacheSmp(context, initialParent))
                .copyTo(backupDir.resolve("$PARENT_ID.smp"))
            var reference = LibraryUpdateReference(
                treeUri = backupDir.toURI().toString(),
                folderUri = backupDir.toURI().toString(),
                archivesBySongId = mapOf(PARENT_ID to oldArchive.absolutePath)
            )
            val stateFile = backupDir.resolve("state.json").apply { writeText("state-before") }
            val promptersFile = backupDir.resolve("prompters.json").apply { writeText("prompters-before") }

            val activeEditorTrackUri = buildSmpItem(PARENT_ID)
            assertEquals(
                tracksRoot.resolve(PARENT_ID).canonicalFile,
                LrcStorage.resolveSmpRuntimeSongDir(filesDir, activeEditorTrackUri)
            )
            assertTrue(LrcStorage.isSmpRuntimeTrack(context, activeEditorTrackUri))
            assertEquals(OLD_LRC, LrcStorage.loadForTrack(context, activeEditorTrackUri))
            var editorLrcSaved = false
            var editorRawSaved = false
            val activeEditorFlush = LyricsEditorPersistenceBarrier.registerActive {
                editorLrcSaved = LrcStorage.saveForTrack(
                    context = context,
                    trackUriString = activeEditorTrackUri,
                    lines = listOf(LrcLine(0L, NEW_EDITOR_LRC))
                )
                editorRawSaved = editorLrcSaved && LrcStorage.saveEditorRawForTrack(
                    context = context,
                    trackUriString = activeEditorTrackUri,
                    rawText = NEW_RAW
                )
                editorLrcSaved && editorRawSaved
            }
            writeLyrics(tracksRoot.resolve(VARIANT_ID), NEW_VARIANT_LRC, NEW_VARIANT_RAW)
            val firstUpdate = try {
                runUpdate(context, gateway, reference) { reference = it }
            } finally {
                LyricsEditorPersistenceBarrier.unregisterActive(activeEditorFlush)
            }

            assertTrue(
                "Editor flush failed: lrc=$editorLrcSaved raw=$editorRawSaved uri=$activeEditorTrackUri error=$loggedWriteFailure",
                editorLrcSaved && editorRawSaved
            )
            assertEquals(1, firstUpdate.updatedCount)
            assertEquals(0, firstUpdate.failedCount)
            assertEquals(NEW_EDITOR_LRC, tracksRoot.resolve(PARENT_ID).resolve("lyrics.lrc").readText())
            assertEquals(NEW_RAW, tracksRoot.resolve(PARENT_ID).resolve(LrcStorage.LYRICS_EDITOR_RAW_FILE_NAME).readText())
            assertEquals(1, activeArchives(backupDir, PARENT_ID).size)
            assertArchiveLyrics(
                File(reference.archivesBySongId.getValue(PARENT_ID)),
                NEW_EDITOR_LRC,
                NEW_RAW,
                NEW_VARIANT_LRC,
                NEW_VARIANT_RAW
            )

            val publishedAfterFirstUpdate = gateway.publishedCount
            val unchangedUpdate = runUpdate(context, gateway, reference) { reference = it }
            assertEquals(0, unchangedUpdate.updatedCount)
            assertEquals(1, unchangedUpdate.unchangedCount)
            assertEquals(publishedAfterFirstUpdate, gateway.publishedCount)

            val pendingLyricsFlush = LyricsEditorPersistenceBarrier.registerActive {
                writeLyrics(tracksRoot.resolve(PARENT_ID), THIRD_LRC, THIRD_RAW)
                true
            }
            val dirtyAfterLyricsEdit = try {
                detectLibraryBackupUpdateNeededAfterPendingWrites(
                    awaitPendingWrites = LyricsEditorPersistenceBarrier::awaitPending,
                    detectPersistedChanges = {
                        detectUpdateNeeded(context, gateway, reference)
                    }
                )
            } finally {
                LyricsEditorPersistenceBarrier.unregisterActive(pendingLyricsFlush)
            }
            assertTrue(dirtyAfterLyricsEdit)

            val secondUpdate = runUpdate(context, gateway, reference) { reference = it }

            assertEquals(1, secondUpdate.updatedCount)
            assertEquals(0, secondUpdate.failedCount)
            assertFalse(
                detectLibraryBackupUpdateNeededAfterPendingWrites(
                    awaitPendingWrites = LyricsEditorPersistenceBarrier::awaitPending,
                    detectPersistedChanges = {
                        detectUpdateNeeded(context, gateway, reference)
                    }
                )
            )
            assertEquals(1, activeArchives(backupDir, PARENT_ID).size)
            assertArchiveLyrics(
                File(reference.archivesBySongId.getValue(PARENT_ID)),
                THIRD_LRC,
                THIRD_RAW,
                NEW_VARIANT_LRC,
                NEW_VARIANT_RAW
            )

            writeParent(
                tracksRoot.resolve(NEW_SONG_ID),
                NEW_SONG_ID,
                "Nouveau MP3",
                "[00:00.000]Nouveau\n",
                "Nouveau\n",
                audioName = "audio.mp3"
            )
            val thirdUpdate = runUpdate(context, gateway, reference) { reference = it }
            assertEquals(0, thirdUpdate.updatedCount)
            assertEquals(1, thirdUpdate.addedCount)
            assertEquals(1, thirdUpdate.unchangedCount)
            assertEquals(0, thirdUpdate.failedCount)
            assertEquals(1, activeArchives(backupDir, PARENT_ID).size)
            assertEquals(1, activeArchives(backupDir, NEW_SONG_ID).size)

            assertEquals("state-before", stateFile.readText())
            assertEquals("prompters-before", promptersFile.readText())

            assertTrue(tracksRoot.deleteRecursively())
            val archiveChosenByRestore = activeArchives(backupDir, PARENT_ID).single()
            val importer = SmpImporter(context)
            val restored = importer.importSmp(
                Uri.fromFile(archiveChosenByRestore),
                preserveExistingLyricsOnReplace = false
            )
            assertNotNull("Import failed: ${importer.lastFailureReason}", restored)
            val restoredParentDir = filesDir.resolve("tracks/$PARENT_ID")
            val restoredVariantDir = filesDir.resolve("tracks/$VARIANT_ID")
            assertEquals(THIRD_LRC, restoredParentDir.resolve("lyrics.lrc").readText())
            assertEquals(THIRD_RAW, restoredParentDir.resolve(LrcStorage.LYRICS_EDITOR_RAW_FILE_NAME).readText())
            assertEquals(NEW_VARIANT_LRC, restoredVariantDir.resolve("lyrics.lrc").readText())
            assertEquals(NEW_VARIANT_RAW, restoredVariantDir.resolve(LrcStorage.LYRICS_EDITOR_RAW_FILE_NAME).readText())
            assertFalse(restoredParentDir.resolve("lyrics.lrc").readText().contains("Anciennes"))
        } finally {
            uriMock.close()
            looperMock.close()
            clockMock.close()
            logMock.close()
            if (root.name.startsWith(TEMP_PREFIX)) root.deleteRecursively()
        }
    }

    private suspend fun runUpdate(
        context: Context,
        gateway: RealArchiveGateway,
        reference: LibraryUpdateReference,
        save: (LibraryUpdateReference) -> Unit
    ): LibraryUpdateResult {
        check(LyricsEditorPersistenceBarrier.awaitPending())
        val fingerprint = SmpFamilyFingerprint()
        return updateLibraryFamiliesV0(
            reference = reference,
            runtimeSongs = SmpLibraryScanner(context).listSongs(),
            gateway = gateway,
            calculateFingerprint = { song, cachedAudio ->
                fingerprint.calculate(context, song, cachedAudio)
            },
            saveReference = { next -> save(next); true }
        )
    }

    private fun detectUpdateNeeded(
        context: Context,
        gateway: RealArchiveGateway,
        reference: LibraryUpdateReference
    ): Boolean {
        val fingerprint = SmpFamilyFingerprint()
        return isLibraryBackupUpdateNeeded(
            reference = reference,
            runtimeSongs = SmpLibraryScanner(context).listSongs(),
            calculateFingerprint = { song, cachedAudio ->
                fingerprint.calculate(context, song, cachedAudio)
            },
            isArchiveOwnedBySong = gateway::isArchiveOwnedBySong
        )
    }

    private fun activeArchives(folder: File, songId: String): List<File> = folder.listFiles()
        .orEmpty()
        .filter { file -> file.extension == "smp" && file.inputStream().use { SmpArchiveSongIdResolver.readStableSongId(it) } == songId }

    private fun assertArchiveLyrics(
        archive: File,
        parentLrc: String,
        parentRaw: String,
        variantLrc: String,
        variantRaw: String
    ) {
        ZipFile(archive).use { zip ->
            assertEquals(parentLrc, zip.readText("lyrics.lrc"))
            assertEquals(parentRaw, zip.readText(LrcStorage.LYRICS_EDITOR_RAW_FILE_NAME))
            val variants = zip.readText("arrangement_variants.json")
            assertTrue(variants.contains(variantLrc.trim()))
            assertTrue(variants.contains(variantRaw.replace("\n", "\\n").trim()))
        }
    }

    private fun ZipFile.readText(name: String): String = getInputStream(requireNotNull(getEntry(name)))
        .bufferedReader(Charsets.UTF_8)
        .use { it.readText() }

    private fun writeParent(
        dir: File,
        id: String,
        title: String,
        lrc: String,
        raw: String,
        audioName: String = "audio.wav"
    ) {
        assertTrue(dir.mkdirs())
        dir.resolve(audioName).writeBytes(validWavBytes())
        writeLyrics(dir, lrc, raw)
        dir.resolve("arrangement.json").writeText(
            ArrangementJsonCodec.encode(arrangement(id, title)).toString(2),
            Charsets.UTF_8
        )
        dir.resolve("config.json").writeText(
            SmpConfig(
                title = title,
                id = id,
                files = SmpConfig.FilesConfig(audioName, "lyrics.lrc", null, null, null, null, null, null)
            ).toJsonString()
        )
    }

    private fun writeVariant(dir: File, lrc: String, raw: String) {
        assertTrue(dir.mkdirs())
        ArrangementVariantStore.writeVariantFiles(
            targetDir = dir,
            variantId = VARIANT_ID,
            title = "Variante",
            sourceSongId = PARENT_ID,
            arrangement = arrangement(PARENT_ID, "Variante"),
            archivedLyrics = lrc,
            archivedLyricsEditorRaw = raw,
            archivedPlayback = SmpConfig.PlaybackConfig(0L, null, 1f, 0, 0)
        )
    }

    private fun writeLyrics(dir: File, lrc: String, raw: String) {
        dir.resolve("lyrics.lrc").writeText(lrc, Charsets.UTF_8)
        dir.resolve(LrcStorage.LYRICS_EDITOR_RAW_FILE_NAME).writeText(raw, Charsets.UTF_8)
    }

    private fun arrangement(sourceSongId: String, title: String) = ArrangementData(
        version = 2,
        name = title,
        sourceSongId = sourceSongId,
        updatedAt = 1_700_000_000_000L,
        segments = emptyList(),
        structureSegmentIds = emptyList(),
        entries = listOf(
            ArrangementEntryData("entry_intro", "Couplet", 0L, 1_000L),
            ArrangementEntryData("entry_final", "Final", 1_000L, 2_000L)
        )
    )

    private fun validWavBytes(): ByteArray {
        val samples = byteArrayOf(1, 2, 3, 4)
        return ByteBuffer.allocate(44 + samples.size)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                put("RIFF".toByteArray(Charsets.US_ASCII))
                putInt(36 + samples.size)
                put("WAVE".toByteArray(Charsets.US_ASCII))
                put("fmt ".toByteArray(Charsets.US_ASCII))
                putInt(16)
                putShort(1)
                putShort(1)
                putInt(8_000)
                putInt(8_000)
                putShort(1)
                putShort(8)
                put("data".toByteArray(Charsets.US_ASCII))
                putInt(samples.size)
                put(samples)
            }
            .array()
    }

    private fun fakeUri(raw: String): Uri = Mockito.mock(Uri::class.java).also { uri ->
        val path = raw.removePrefix("file://")
        Mockito.`when`(uri.scheme).thenReturn("file")
        Mockito.`when`(uri.path).thenReturn(path)
        Mockito.`when`(uri.lastPathSegment).thenReturn(File(path).name)
        Mockito.`when`(uri.toString()).thenReturn(raw)
    }

    private class RealArchiveGateway(
        private val context: Context,
        private val backupDir: File
    ) : LibraryUpdateArchiveGateway {
        private var sequence = 0
        var publishedCount = 0
            private set

        override fun isFolderWritable(reference: LibraryUpdateReference): Boolean = backupDir.isDirectory

        override fun isArchiveOwnedBySong(archiveUri: String, songId: String): Boolean {
            val archive = File(archiveUri)
            return archive.isFile && archive.inputStream().use {
                SmpArchiveSongIdResolver.readStableSongId(it)
            } == songId
        }

        override fun publishFamily(reference: LibraryUpdateReference, song: SongUnit): String? {
            publishedCount += 1
            val current = SmpLibraryScanner(context).findSongById(song.id) ?: return null
            val exported = SmpExporter.exportSongUnitToCacheSmp(context, current) ?: return null
            val target = backupDir.resolve("${song.id}_update_${++sequence}.smp")
            return exported.copyTo(target).absolutePath
        }

        override fun deleteArchiveIfOwnedBySong(archiveUri: String, songId: String): Boolean {
            val archive = File(archiveUri)
            return isArchiveOwnedBySong(archiveUri, songId) && archive.delete()
        }
    }

    private class InMemorySharedPreferences : SharedPreferences {
        private val values = linkedMapOf<String, Any?>()
        override fun getAll(): MutableMap<String, *> = values.toMutableMap()
        override fun getString(key: String, defValue: String?): String? = values[key] as? String ?: defValue
        override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun getInt(key: String, defValue: Int): Int = values[key] as? Int ?: defValue
        override fun getLong(key: String, defValue: Long): Long = values[key] as? Long ?: defValue
        override fun getFloat(key: String, defValue: Float): Float = values[key] as? Float ?: defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
        override fun contains(key: String): Boolean = values.containsKey(key)
        override fun edit(): SharedPreferences.Editor = Editor(values)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        private class Editor(private val values: MutableMap<String, Any?>) : SharedPreferences.Editor {
            private val updates = linkedMapOf<String, Any?>()
            override fun putString(key: String, value: String?) = apply { updates[key] = value }
            override fun putStringSet(key: String, values: MutableSet<String>?) = this
            override fun putInt(key: String, value: Int) = apply { updates[key] = value }
            override fun putLong(key: String, value: Long) = apply { updates[key] = value }
            override fun putFloat(key: String, value: Float) = apply { updates[key] = value }
            override fun putBoolean(key: String, value: Boolean) = apply { updates[key] = value }
            override fun remove(key: String) = apply { updates[key] = null }
            override fun clear() = apply { values.clear() }
            override fun commit(): Boolean { updates.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }; return true }
            override fun apply() { commit() }
        }
    }

    private companion object {
        const val TEMP_PREFIX = "library_update_lyrics_"
        const val PARENT_ID = "lyrics_parent"
        const val VARIANT_ID = "lyrics_variant"
        const val NEW_SONG_ID = "new_mp3"
        const val OLD_LRC = "[00:00.000]Anciennes paroles\n"
        const val OLD_RAW = "Ancien texte brut\n"
        const val NEW_EDITOR_LRC = "Nouvelles paroles"
        const val NEW_RAW = "Nouveau couplet\n\nNouveau refrain\n"
        const val THIRD_LRC = "[00:00.000]Troisièmes paroles\n"
        const val THIRD_RAW = "Troisième couplet\n\n\nTroisième refrain\n"
        const val OLD_VARIANT_LRC = "[00:00.000]Ancienne variante\n"
        const val OLD_VARIANT_RAW = "Ancienne variante brute\n"
        const val NEW_VARIANT_LRC = "[00:00.000]Nouvelle variante\n"
        const val NEW_VARIANT_RAW = "Variante couplet\n\nVariante refrain\n"
    }
}
