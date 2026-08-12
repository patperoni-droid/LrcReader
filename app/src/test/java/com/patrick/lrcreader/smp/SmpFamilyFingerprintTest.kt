package com.patrick.lrcreader.smp

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.patrick.lrcreader.core.BackupFolderPrefs
import com.patrick.lrcreader.core.LrcStorage
import com.patrick.lrcreader.core.buildSmpItem
import com.patrick.lrcreader.core.config.TitleAliasesStore
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicLong

class SmpFamilyFingerprintTest {

    @Test
    fun fingerprintIsStableAndCoversEveryCertifiedFamilyComponent() {
        val root = Files.createTempDirectory(TEMP_PREFIX).toFile()
        val filesDir = root.resolve("files").apply { mkdirs() }
        val cacheDir = root.resolve("cache").apply { mkdirs() }
        val libraryRoot = root.resolve("library").apply { mkdirs() }
        val preferences = linkedMapOf<String, InMemorySharedPreferences>()
        val context = Mockito.mock(Context::class.java)
        val clock = AtomicLong(10_000L)
        val workerLooper = Mockito.mock(Looper::class.java)
        val mainLooper = Mockito.mock(Looper::class.java)
        val logMock = Mockito.mockStatic(Log::class.java)
        val clockMock = Mockito.mockStatic(SystemClock::class.java)
        val looperMock = Mockito.mockStatic(Looper::class.java)
        val uriMock = Mockito.mockStatic(Uri::class.java)

        try {
            Mockito.`when`(context.filesDir).thenReturn(filesDir)
            Mockito.`when`(context.cacheDir).thenReturn(cacheDir)
            Mockito.`when`(context.applicationContext).thenReturn(context)
            Mockito.`when`(context.getSharedPreferences(Mockito.anyString(), Mockito.anyInt()))
                .thenAnswer { invocation ->
                    preferences.getOrPut(invocation.getArgument(0)) { InMemorySharedPreferences() }
                }
            clockMock.`when`<Long> { SystemClock.elapsedRealtime() }
                .thenAnswer { clock.getAndIncrement() }
            looperMock.`when`<Looper?> { Looper.myLooper() }.thenReturn(workerLooper)
            looperMock.`when`<Looper> { Looper.getMainLooper() }.thenReturn(mainLooper)
            uriMock.`when`<Uri> { Uri.parse(Mockito.anyString()) }
                .thenAnswer { invocation -> fakeUri(invocation.getArgument(0)) }
            uriMock.`when`<Uri> { Uri.fromFile(Mockito.any(File::class.java)) }
                .thenAnswer { invocation ->
                    fakeUri("file://${invocation.getArgument<File>(0).absolutePath}")
                }
            BackupFolderPrefs.saveLibraryRootUri(context, fakeUri("file://${libraryRoot.absolutePath}"))

            val tracks = filesDir.resolve("tracks").apply { mkdirs() }
            val parentDir = tracks.resolve(PARENT_ID)
            val variantDir = tracks.resolve(VARIANT_ID)
            writeParent(parentDir)
            writeVariant(variantDir, VARIANT_ID, "Variante A")
            val parent = requireNotNull(SmpLibraryScanner(context).findSongById(PARENT_ID))
            val calculator = SmpFamilyFingerprint()

            val first = requireNotNull(calculator.calculate(context, parent))
            val second = requireNotNull(calculator.calculate(context, parent, first.audioHashCache))
            assertEquals(first, second)

            val dummyArchive = root.resolve("archive-a.smp").apply { writeText("archive") }
            val archiveIndependent = requireNotNull(calculator.calculate(context, parent, first.audioHashCache))
            assertTrue(dummyArchive.renameTo(root.resolve("archive-b.smp")))
            root.resolve("archive-b.smp").setLastModified(1_900_000_000_000L)
            assertEquals(
                archiveIndependent.fingerprint,
                requireNotNull(calculator.calculate(context, parent, first.audioHashCache)).fingerprint
            )

            context.getSharedPreferences("device-only", Context.MODE_PRIVATE)
                .edit().putBoolean("presentation", true).commit()
            assertEquals(
                first.fingerprint,
                requireNotNull(calculator.calculate(context, parent, first.audioHashCache)).fingerprint
            )

            assertFileMutationChanges(calculator, context, parent, parentDir.resolve("lyrics.lrc"), "[00:00.000]Paroles 2\n")
            assertFileMutationChanges(calculator, context, parent, parentDir.resolve(LrcStorage.LYRICS_EDITOR_RAW_FILE_NAME), "Couplet\n\n\nRefrain modifié\n")
            assertFileMutationChanges(calculator, context, parent, parentDir.resolve("chords.lrc"), "[00:00.000]Dm\n")
            assertFileMutationChanges(calculator, context, parent, parentDir.resolve("timeline.json"), "{\"events\":[{\"timeMs\":20}]}" )
            assertFileMutationChanges(calculator, context, parent, parentDir.resolve("annotations.json"), "[{\"timeMs\":30,\"text\":\"note\"}]" )
            assertFileMutationChanges(calculator, context, parent, parentDir.resolve("midi_cues.json"), "[{\"timeMs\":40,\"value\":2}]" )
            assertFileMutationChanges(calculator, context, parent, parentDir.resolve("dmx_cues.json"), "[{\"timeMs\":50,\"value\":3}]" )
            assertFileMutationChanges(calculator, context, parent, parentDir.resolve("grid.json"), "{\"bpm\":98.5,\"offsetMs\":50}" )
            assertFileMutationChanges(calculator, context, parent, parentDir.resolve("prompter.txt"), "Prompteur parent modifié\n")
            assertFileMutationChanges(calculator, context, parent, parentDir.resolve("arrangement.json"), ArrangementJsonCodec.encode(arrangement(PARENT_ID, "Parent modifié", 2_000L)).toString(2))

            val audio = parentDir.resolve("audio.wav")
            val beforeAudio = requireNotNull(calculator.calculate(context, parent))
            audio.writeBytes(byteArrayOf(9, 8, 7, 6, 5, 4))
            assertNotEquals(
                beforeAudio.fingerprint,
                requireNotNull(calculator.calculate(context, parent, beforeAudio.audioHashCache)).fingerprint
            )
            audio.writeBytes(byteArrayOf(1, 2, 3, 4))

            assertConfigMutationChanges(calculator, context, parent, parentDir) { config ->
                config.getJSONObject("playback").put("trimStartMs", 2_000L)
            }
            assertConfigMutationChanges(calculator, context, parent, parentDir) { config ->
                config.getJSONObject("playback").put("trimEndMs", 80_000L)
            }
            assertConfigMutationChanges(calculator, context, parent, parentDir) { config ->
                config.getJSONObject("playback").put("tempo", 1.15)
            }
            assertConfigMutationChanges(calculator, context, parent, parentDir) { config ->
                config.getJSONObject("playback").put("pitchSemi", 3)
            }
            assertConfigMutationChanges(calculator, context, parent, parentDir) { config ->
                config.getJSONObject("playback").put("volumeDb", -6)
            }
            assertConfigMutationChanges(calculator, context, parent, parentDir) { config ->
                config.getJSONObject("lyricsLineColors").put("0", 0xFF00AA55.toInt())
            }

            val beforeAlias = requireNotNull(calculator.calculate(context, parent)).fingerprint
            assertTrue(TitleAliasesStore.setTitleForTrack(context, buildSmpItem(PARENT_ID), "Alias scène"))
            assertNotEquals(beforeAlias, requireNotNull(calculator.calculate(context, parent)).fingerprint)
            assertTrue(TitleAliasesStore.clearTitleForTrack(context, buildSmpItem(PARENT_ID)))

            val beforeVariantAlias = requireNotNull(calculator.calculate(context, parent)).fingerprint
            assertTrue(TitleAliasesStore.setTitleForTrack(context, buildSmpItem(VARIANT_ID), "Alias variante"))
            assertNotEquals(beforeVariantAlias, requireNotNull(calculator.calculate(context, parent)).fingerprint)
            assertTrue(TitleAliasesStore.clearTitleForTrack(context, buildSmpItem(VARIANT_ID)))

            assertFileMutationChanges(calculator, context, parent, variantDir.resolve("lyrics.lrc"), "[00:00.000]Variante modifiée\n")
            assertFileMutationChanges(calculator, context, parent, variantDir.resolve(LrcStorage.LYRICS_EDITOR_RAW_FILE_NAME), "Variante\n\nBrute modifiée\n")
            assertFileMutationChanges(calculator, context, parent, variantDir.resolve("grid.json"), "{\"bpm\":111.0}" )
            assertFileMutationChanges(calculator, context, parent, variantDir.resolve("prompter.txt"), "Prompteur variante modifié\n")
            assertConfigMutationChanges(calculator, context, parent, variantDir) { config ->
                config.getJSONObject("playback").put("tempo", 0.85)
            }
            assertConfigMutationChanges(calculator, context, parent, variantDir) { config ->
                config.getJSONObject("lyricsLineColors").put("1", 0xFFAA0055.toInt())
            }

            val beforeAdd = requireNotNull(calculator.calculate(context, parent)).fingerprint
            writeVariant(tracks.resolve(VARIANT_B_ID), VARIANT_B_ID, "Variante B")
            assertNotEquals(beforeAdd, requireNotNull(calculator.calculate(context, parent)).fingerprint)
            assertTrue(tracks.resolve(VARIANT_B_ID).deleteRecursively())

            val beforeRemove = requireNotNull(calculator.calculate(context, parent)).fingerprint
            assertTrue(variantDir.deleteRecursively())
            assertNotEquals(beforeRemove, requireNotNull(calculator.calculate(context, parent)).fingerprint)
        } finally {
            runCatching { TitleAliasesStore.clearTitleForTrack(context, buildSmpItem(PARENT_ID)) }
            runCatching { TitleAliasesStore.clearTitleForTrack(context, buildSmpItem(VARIANT_ID)) }
            uriMock.close()
            looperMock.close()
            clockMock.close()
            logMock.close()
            if (root.name.startsWith(TEMP_PREFIX)) root.deleteRecursively()
        }
    }

    private fun assertFileMutationChanges(
        calculator: SmpFamilyFingerprint,
        context: Context,
        parent: SongUnit,
        file: File,
        replacement: String
    ) {
        val original = file.readBytes()
        val before = requireNotNull(calculator.calculate(context, parent)).fingerprint
        file.writeText(replacement, Charsets.UTF_8)
        val after = requireNotNull(calculator.calculate(context, parent)).fingerprint
        assertNotEquals(file.name, before, after)
        file.writeBytes(original)
        assertEquals(file.name, before, requireNotNull(calculator.calculate(context, parent)).fingerprint)
    }

    private fun assertConfigMutationChanges(
        calculator: SmpFamilyFingerprint,
        context: Context,
        parent: SongUnit,
        dir: File,
        mutate: (JSONObject) -> Unit
    ) {
        val file = dir.resolve("config.json")
        val original = file.readText(Charsets.UTF_8)
        val before = requireNotNull(calculator.calculate(context, parent)).fingerprint
        val changed = JSONObject(original).also(mutate)
        file.writeText(changed.toString(2), Charsets.UTF_8)
        assertNotEquals(dir.name, before, requireNotNull(calculator.calculate(context, parent)).fingerprint)
        file.writeText(original, Charsets.UTF_8)
        assertEquals(dir.name, before, requireNotNull(calculator.calculate(context, parent)).fingerprint)
    }

    private fun writeParent(dir: File) {
        assertTrue(dir.mkdirs())
        dir.resolve("audio.wav").writeBytes(byteArrayOf(1, 2, 3, 4))
        dir.resolve("lyrics.lrc").writeText("[00:00.000]Paroles\n")
        dir.resolve(LrcStorage.LYRICS_EDITOR_RAW_FILE_NAME).writeText("Couplet\n\nRefrain\n")
        dir.resolve("chords.lrc").writeText("[00:00.000]Am\n")
        dir.resolve("timeline.json").writeText("{\"events\":[]}")
        dir.resolve("annotations.json").writeText("[]")
        dir.resolve("midi_cues.json").writeText("[]")
        dir.resolve("dmx_cues.json").writeText("[]")
        dir.resolve("grid.json").writeText("{\"bpm\":96.0}")
        dir.resolve("prompter.txt").writeText("Prompteur parent\n")
        dir.resolve("arrangement.json").writeText(
            ArrangementJsonCodec.encode(arrangement(PARENT_ID, "Parent", 1_000L)).toString(2)
        )
        dir.resolve("config.json").writeText(
            SmpConfig(
                title = "Parent",
                id = PARENT_ID,
                files = SmpConfig.FilesConfig(
                    audio = "audio.wav",
                    lyrics = "lyrics.lrc",
                    chords = "chords.lrc",
                    timeline = "timeline.json",
                    annotations = "annotations.json",
                    midiCues = "midi_cues.json",
                    dmxCues = "dmx_cues.json",
                    prompter = "prompter.txt"
                ),
                playback = SmpConfig.PlaybackConfig(1_000L, 90_000L, 1f, 0, -2),
                lyricsLineColors = mapOf("0" to 0xFF112233.toInt())
            ).toJsonString()
        )
    }

    private fun writeVariant(dir: File, variantId: String, title: String) {
        assertTrue(dir.mkdirs())
        ArrangementVariantStore.writeVariantFiles(
            targetDir = dir,
            variantId = variantId,
            title = title,
            sourceSongId = PARENT_ID,
            arrangement = arrangement(PARENT_ID, title, 1_500L),
            archivedLyrics = "[00:00.000]Variante\n",
            archivedChords = "[00:00.000]Dm\n",
            archivedLyricsLineColors = mapOf("0" to 0xFF445566.toInt()),
            archivedTimeline = "{\"events\":[]}",
            archivedAnnotations = "[]",
            archivedMidiCues = "[]",
            archivedDmxCues = "[]",
            archivedGrid = "{\"bpm\":108.0}",
            archivedPrompter = ArrangementVariantPrompterArchiveAsset("txt", "Prompteur variante\n"),
            archivedLyricsEditorRaw = "Variante\n\nBrute\n",
            archivedPlayback = SmpConfig.PlaybackConfig(2_000L, 80_000L, 1.05f, 1, -3)
        )
    }

    private fun arrangement(sourceSongId: String, title: String, startMs: Long) = ArrangementData(
        version = 2,
        name = title,
        sourceSongId = sourceSongId,
        updatedAt = 1_700_000_000_000L,
        segments = emptyList(),
        structureSegmentIds = emptyList(),
        entries = listOf(ArrangementEntryData("entry-$title", title, startMs, startMs + 1_000L))
    )

    private fun fakeUri(raw: String): Uri = Mockito.mock(Uri::class.java).also { uri ->
        val path = raw.removePrefix("file://")
        Mockito.`when`(uri.scheme).thenReturn("file")
        Mockito.`when`(uri.path).thenReturn(path)
        Mockito.`when`(uri.lastPathSegment).thenReturn(File(path).name)
        Mockito.`when`(uri.toString()).thenReturn(raw)
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
            override fun commit(): Boolean {
                updates.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
                return true
            }
            override fun apply() { commit() }
        }
    }

    private companion object {
        const val TEMP_PREFIX = "smp_family_fingerprint_"
        const val PARENT_ID = "fingerprint-parent"
        const val VARIANT_ID = "fingerprint-variant-a"
        const val VARIANT_B_ID = "fingerprint-variant-b"
    }
}
