package com.patrick.lrcreader.smp

import android.content.ContentResolver
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
import com.patrick.lrcreader.core.light.LightAction
import com.patrick.lrcreader.core.light.LightCue
import com.patrick.lrcreader.core.notes.LiveNote
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipFile

class CompleteSongUnitFamilyRoundTripTest {

    @Test
    fun completeFamily_realExportDeleteImportAndScan_preservesCanonicalSnapshot() {
        val root = Files.createTempDirectory(TEMP_PREFIX).toFile()
        val filesDir = root.resolve("app_files").apply { mkdirs() }
        val cacheDir = root.resolve("cache").apply { mkdirs() }
        val libraryRoot = root.resolve("library").apply { mkdirs() }
        val preferences = linkedMapOf<String, InMemorySharedPreferences>()
        val context = Mockito.mock(Context::class.java)
        val contentResolver = Mockito.mock(ContentResolver::class.java)
        val clock = AtomicLong(10_000L)
        val workerLooper = Mockito.mock(Looper::class.java)
        val mainLooper = Mockito.mock(Looper::class.java)
        var archiveForImport: File? = null

        val logMock = Mockito.mockStatic(Log::class.java)
        val clockMock = Mockito.mockStatic(SystemClock::class.java)
        val looperMock = Mockito.mockStatic(Looper::class.java)
        val uriMock = Mockito.mockStatic(Uri::class.java)

        try {
            Mockito.`when`(context.filesDir).thenReturn(filesDir)
            Mockito.`when`(context.cacheDir).thenReturn(cacheDir)
            Mockito.`when`(context.contentResolver).thenReturn(contentResolver)
            Mockito.`when`(
                context.getSharedPreferences(Mockito.anyString(), Mockito.anyInt())
            ).thenAnswer { invocation ->
                preferences.getOrPut(invocation.getArgument(0)) {
                    InMemorySharedPreferences()
                }
            }
            Mockito.`when`(
                contentResolver.openInputStream(Mockito.any(Uri::class.java))
            ).thenAnswer {
                requireNotNull(archiveForImport).inputStream()
            }

            clockMock.`when`<Long> { SystemClock.elapsedRealtime() }
                .thenAnswer { clock.getAndIncrement() }
            looperMock.`when`<Looper?> { Looper.myLooper() }.thenReturn(workerLooper)
            looperMock.`when`<Looper> { Looper.getMainLooper() }.thenReturn(mainLooper)
            uriMock.`when`<Uri> { Uri.parse(Mockito.anyString()) }
                .thenAnswer { invocation -> fakeUri(invocation.getArgument(0)) }
            uriMock.`when`<Uri> { Uri.fromFile(Mockito.any(File::class.java)) }
                .thenAnswer { invocation ->
                    val file = invocation.getArgument<File>(0)
                    fakeUri("file://${file.absolutePath}")
                }

            val libraryRootUri = fakeUri("file://${libraryRoot.absolutePath}")
            BackupFolderPrefs.saveLibraryRootUri(context, libraryRootUri)

            val fixtures = createFixtures()
            val tracksRoot = filesDir.resolve(TRACKS_DIR_NAME).apply { mkdirs() }
            writeParent(tracksRoot.resolve(PARENT_ID), fixtures.getValue(PARENT_ID))
            writeVariant(tracksRoot.resolve(VARIANT_A_ID), fixtures.getValue(VARIANT_A_ID))
            writeVariant(tracksRoot.resolve(VARIANT_B_ID), fixtures.getValue(VARIANT_B_ID))
            fixtures.values.forEach { fixture ->
                assertTrue(
                    TitleAliasesStore.setTitleForTrack(
                        context,
                        buildSmpItem(fixture.id),
                        fixture.customTitle
                    )
                )
            }

            val original = snapshotFamily(context)
            assertFixtureIsolation(original)

            val parent = requireNotNull(
                SmpLibraryScanner(context).findSongById(PARENT_ID)
            )
            val archive = requireNotNull(
                SmpExporter.exportSongUnitToCacheSmp(context, parent)
            )
            archiveForImport = archive
            assertTrue(archive.isFile)
            assertArchiveContract(archive, fixtures)

            fixtures.keys.forEach { songId ->
                assertTrue(
                    TitleAliasesStore.clearTitleForTrack(context, buildSmpItem(songId))
                )
                assertNull(
                    TitleAliasesStore.getTitleForTrack(context, buildSmpItem(songId))
                )
            }

            val safeRoot = root.canonicalFile
            val safeTracksRoot = tracksRoot.canonicalFile
            assertTrue(safeTracksRoot.path.startsWith(safeRoot.path + File.separator))
            assertTrue(tracksRoot.deleteRecursively())
            assertFalse(tracksRoot.exists())
            assertTrue(SmpLibraryScanner(context).listSongs().isEmpty())
            fixtures.keys.forEach { songId ->
                assertFalse(filesDir.resolve("$TRACKS_DIR_NAME/$songId").exists())
            }

            val importer = SmpImporter(context)
            val importedParent = importer.importSmp(
                uri = Uri.fromFile(archive),
                preserveExistingLyricsOnReplace = false
            )
            assertNotNull("Import SMP failed: ${importer.lastFailureReason}", importedParent)
            assertEquals(PARENT_ID, importedParent?.id)

            val restored = snapshotFamily(context)
            assertEquals(original, restored)
            assertFixtureIsolation(restored)
            assertEquals(fixtures.getValue(VARIANT_B_ID).lyricsEditorRaw, restored.variants[1].lyricsEditorRaw)
            assertEquals(fixtures.getValue(VARIANT_B_ID).prompter.content, restored.variants[1].prompter?.content)
        } finally {
            runCatching {
                listOf(PARENT_ID, VARIANT_A_ID, VARIANT_B_ID).forEach { songId ->
                    TitleAliasesStore.clearTitleForTrack(context, buildSmpItem(songId))
                }
            }
            uriMock.close()
            looperMock.close()
            clockMock.close()
            logMock.close()
            if (root.name.startsWith(TEMP_PREFIX)) {
                root.deleteRecursively()
            }
        }
    }

    private fun createFixtures(): Map<String, MemberFixture> = linkedMapOf(
        PARENT_ID to MemberFixture(
            id = PARENT_ID,
            title = "Parent structurel — Été",
            customTitle = "Alias parent — scène",
            sourceSongId = null,
            lyrics = "[00:00.500]Parent intro\n[00:05.250]Parent refrain\n",
            lyricsEditorRaw = "Parent couplet\n\n  Parent refrain\n",
            chords = "[00:00.500]Am Parent\n[00:05.250]F Parent\n",
            timeline = listOf(
                TimelineMarker(500L, "Parent intro", TimelineMarkerKind.TEXT),
                TimelineMarker(5_250L, "Parent MIDI", TimelineMarkerKind.MIDI),
                TimelineMarker(9_750L, "Parent note", TimelineMarkerKind.NOTE, 2_500L)
            ),
            annotations = listOf(
                LiveNote(750L, 1_500L, "Annotation parent 1"),
                LiveNote(8_500L, 3_000L, "Annotation parent 2")
            ),
            midi = listOf(
                MidiCue(0.5, "PC", 11, 1),
                MidiCue(5.25, "CC", 64, 2)
            ),
            dmx = listOf(
                LightCue(600L, LightAction.Color(0xFF2244AA), 0.8f, 500L, 2_000L, 250L),
                LightCue(7_500L, LightAction.Blackout)
            ),
            grid = GridFixture(101.25, 1_125L, 250L, 180_000L, 4, 4),
            lyricsLineColors = linkedMapOf("0" to 0xFF112233.toInt(), "2" to 0xFF445566.toInt()),
            prompter = PrompterFixture("txt", "Prompteur parent\n\nFinal parent\n"),
            playback = PlaybackFixture(1_250L, 181_000L, 0.95f, -1, -4),
            arrangement = arrangement(
                idPrefix = "parent",
                title = "Arrangement parent",
                sourceSongId = PARENT_ID,
                firstStartMs = 0L,
                secondStartMs = 12_000L
            ),
            audio = validWavBytes(sampleSeed = 0x11)
        ),
        VARIANT_A_ID to MemberFixture(
            id = VARIANT_A_ID,
            title = "Variante A structurelle",
            customTitle = "Alias variante A",
            sourceSongId = PARENT_ID,
            lyrics = "[00:01.000]A couplet\n[00:04.500]A refrain\n",
            lyricsEditorRaw = "A couplet\n\nA refrain\n",
            chords = "[00:01.000]Dm A\n[00:04.500]G A\n",
            timeline = listOf(
                TimelineMarker(1_000L, "A départ", TimelineMarkerKind.TEXT),
                TimelineMarker(4_500L, "A lumière", TimelineMarkerKind.DMX)
            ),
            annotations = listOf(
                LiveNote(1_250L, 2_000L, "Annotation A 1"),
                LiveNote(6_000L, 4_000L, "Annotation A 2")
            ),
            midi = listOf(
                MidiCue(1.0, "PC", 21, 3),
                MidiCue(4.5, "CC", 72, 4),
                MidiCue(9.0, "PC", 42, 16)
            ),
            dmx = listOf(
                LightCue(1_100L, LightAction.Color(0xFFAA4422), 0.6f, 750L, 3_000L, 500L),
                LightCue(8_000L, LightAction.Strobe(8f), 0.4f, 0L, 1_500L, 250L)
            ),
            grid = GridFixture(96.5, 1_250L, 500L, 164_000L, 3, 4),
            lyricsLineColors = linkedMapOf("0" to 0xFFAA3300.toInt(), "1" to 0xFF00AA55.toInt()),
            prompter = PrompterFixture("txt", "Prompteur A\n\nRefrain A — été"),
            playback = PlaybackFixture(2_000L, 165_000L, 1.1f, 2, -2),
            arrangement = arrangement(
                idPrefix = "a",
                title = "Variante A structurelle",
                sourceSongId = PARENT_ID,
                firstStartMs = 1_000L,
                secondStartMs = 9_000L
            )
        ),
        VARIANT_B_ID to MemberFixture(
            id = VARIANT_B_ID,
            title = "Variante B structurelle",
            customTitle = "Alias variante B — UTF-8",
            sourceSongId = PARENT_ID,
            lyrics = "[00:00.750]B intro\n[00:07.250]B final\n",
            lyricsEditorRaw = "B intro\r\n\r\n  ligne avec espaces  \r\n\tligne tabulée\r\nÉté — fin\r\n",
            chords = "[00:00.750]C B\n[00:07.250]Em B\n",
            timeline = listOf(
                TimelineMarker(750L, "B départ", TimelineMarkerKind.TEXT),
                TimelineMarker(7_250L, "B programme", TimelineMarkerKind.MIDI),
                TimelineMarker(13_000L, "B final", TimelineMarkerKind.NOTE, 4_000L)
            ),
            annotations = listOf(
                LiveNote(900L, 3_000L, "Annotation B — entrée"),
                LiveNote(11_000L, 5_500L, "Annotation B — final")
            ),
            midi = listOf(
                MidiCue(0.75, "CC", 7, 5),
                MidiCue(7.25, "PC", 84, 10)
            ),
            dmx = listOf(
                LightCue(800L, LightAction.Color(0xFF22AA88), 1f, 1_250L, 4_000L, 1_000L),
                LightCue(12_500L, LightAction.Blackout, fadeMs = 600L)
            ),
            grid = GridFixture(132.0, 2_875L, 1_000L, 192_500L, 7, 8),
            lyricsLineColors = linkedMapOf("0" to 0xFF0033CC.toInt(), "3" to 0xFFCCAA00.toInt()),
            prompter = PrompterFixture(
                "json",
                "{\r\n  \"title\": \"Prompteur B — été\",\r\n  \"sections\": [\"Intro\", \"Final\"]\r\n}\r\n"
            ),
            playback = PlaybackFixture(0L, 193_000L, 1f, 0, 0),
            arrangement = arrangement(
                idPrefix = "b",
                title = "Variante B structurelle",
                sourceSongId = PARENT_ID,
                firstStartMs = 750L,
                secondStartMs = 14_000L
            )
        )
    )

    private fun writeParent(targetDir: File, fixture: MemberFixture) {
        assertTrue(targetDir.mkdirs())
        targetDir.resolve("audio.wav").writeBytes(requireNotNull(fixture.audio))
        targetDir.resolve("lyrics.lrc").writeText(fixture.lyrics, Charsets.UTF_8)
        targetDir.resolve(LrcStorage.LYRICS_EDITOR_RAW_FILE_NAME)
            .writeText(fixture.lyricsEditorRaw, Charsets.UTF_8)
        targetDir.resolve("chords.lrc").writeText(fixture.chords, Charsets.UTF_8)
        targetDir.resolve(SmpMidiCuesStore.MIDI_CUES_FILE_NAME)
            .writeText(MidiCue.toJsonString(fixture.midi), Charsets.UTF_8)
        targetDir.resolve(SmpLightCueStore.LIGHT_CUES_FILE_NAME)
            .writeText(LightCue.toJsonString(fixture.dmx), Charsets.UTF_8)
        targetDir.resolve("grid.json").writeText(fixture.grid.toJson(), Charsets.UTF_8)
        targetDir.resolve("prompter.${fixture.prompter.format}")
            .writeText(fixture.prompter.content, Charsets.UTF_8)
        targetDir.resolve("arrangement.json").writeText(
            ArrangementJsonCodec.encode(fixture.arrangement).toString(2),
            Charsets.UTF_8
        )
        targetDir.resolve("config.json").writeText(
            SmpConfig(
                title = fixture.title,
                id = fixture.id,
                files = SmpConfig.FilesConfig(
                    audio = "audio.wav",
                    lyrics = "lyrics.lrc",
                    chords = "chords.lrc",
                    timeline = SmpTimelineStore.TIMELINE_FILE_NAME,
                    annotations = SmpAnnotationsStore.ANNOTATIONS_FILE_NAME,
                    midiCues = SmpMidiCuesStore.MIDI_CUES_FILE_NAME,
                    dmxCues = SmpLightCueStore.LIGHT_CUES_FILE_NAME,
                    prompter = "prompter.${fixture.prompter.format}"
                ),
                playback = fixture.playback.toConfig(),
                lyricsLineColors = fixture.lyricsLineColors
            ).toJsonString(),
            Charsets.UTF_8
        )
        writeTimelineAndAnnotations(targetDir, fixture)
    }

    private fun writeVariant(targetDir: File, fixture: MemberFixture) {
        assertTrue(targetDir.mkdirs())
        ArrangementVariantStore.writeVariantFiles(
            targetDir = targetDir,
            variantId = fixture.id,
            title = fixture.title,
            sourceSongId = requireNotNull(fixture.sourceSongId),
            arrangement = fixture.arrangement,
            archivedLyrics = fixture.lyrics,
            archivedChords = fixture.chords,
            archivedLyricsLineColors = fixture.lyricsLineColors,
            archivedMidiCues = MidiCue.toJsonString(fixture.midi),
            archivedDmxCues = LightCue.toJsonString(fixture.dmx),
            archivedGrid = fixture.grid.toJson(),
            archivedPrompter = ArrangementVariantPrompterArchiveAsset(
                format = fixture.prompter.format,
                content = fixture.prompter.content
            ),
            archivedLyricsEditorRaw = fixture.lyricsEditorRaw,
            archivedPlayback = fixture.playback.toConfig()
        )
        writeTimelineAndAnnotations(targetDir, fixture)
    }

    private fun writeTimelineAndAnnotations(targetDir: File, fixture: MemberFixture) {
        val timelineFile = targetDir.resolve(SmpTimelineStore.TIMELINE_FILE_NAME)
        assertTrue(SmpTimelineStore.write(timelineFile, fixture.timeline))
        SmpTimelineStore.awaitIdle(timelineFile)
        val annotationsFile = targetDir.resolve(SmpAnnotationsStore.ANNOTATIONS_FILE_NAME)
        assertTrue(SmpAnnotationsStore.write(annotationsFile, fixture.annotations))
        SmpAnnotationsStore.awaitIdle(annotationsFile)
    }

    private fun snapshotFamily(context: Context): FamilySnapshot {
        val byId = SmpLibraryScanner(context).listSongs().associateBy(SongUnit::id)
        assertEquals(setOf(PARENT_ID, VARIANT_A_ID, VARIANT_B_ID), byId.keys)
        val parent = snapshotMember(context, byId.getValue(PARENT_ID))
        val variants = listOf(VARIANT_A_ID, VARIANT_B_ID).map { id ->
            snapshotMember(context, byId.getValue(id))
        }
        assertEquals(2, variants.size)
        assertTrue(variants.all { it.sourceSongId == PARENT_ID })
        assertNotNull(parent.audio)
        assertTrue(variants.all { it.audio == null })
        return FamilySnapshot(parent = parent, variants = variants)
    }

    private fun snapshotMember(context: Context, song: SongUnit): MemberSnapshot {
        val dir = File(requireNotNull(song.storageFolder))
        val config = requireNotNull(
            SmpConfig.fromJsonOrNull(dir.resolve("config.json").readText(Charsets.UTF_8))
        )
        val isVariant = song.arrangementSourceSongId != null
        val playback = if (isVariant) {
            requireNotNull(SmpVariantPlayback.readExplicitProfile(dir.resolve("config.json")))
        } else {
            requireNotNull(config.playback)
        }
        val timelineFile = dir.resolve(SmpTimelineStore.TIMELINE_FILE_NAME)
        val annotationsFile = dir.resolve(SmpAnnotationsStore.ANNOTATIONS_FILE_NAME)
        val midiFile = dir.resolve(SmpMidiCuesStore.MIDI_CUES_FILE_NAME)
        val dmxFile = dir.resolve(SmpLightCueStore.LIGHT_CUES_FILE_NAME)
        val gridFile = dir.resolve("grid.json")
        val prompterFile = requireNotNull(
            listOf("prompter.txt", "prompter.json", "prompteur.txt", "prompteur.json")
                .map(dir::resolve)
                .firstOrNull(File::isFile)
        )
        val persistentFiles = dir.listFiles().orEmpty()
            .filter(File::isFile)
            .map(File::getName)
            .filterNot { it == "meta.json" }
            .sorted()

        return MemberSnapshot(
            id = song.id,
            title = song.title,
            customTitle = TitleAliasesStore.getTitleForTrack(context, buildSmpItem(song.id)),
            sourceSongId = song.arrangementSourceSongId,
            persistentFiles = persistentFiles,
            audio = song.audioPath?.let(::File)?.readBytes()?.toList(),
            lyrics = dir.resolve("lyrics.lrc").readText(Charsets.UTF_8),
            lyricsEditorRaw = dir.resolve(LrcStorage.LYRICS_EDITOR_RAW_FILE_NAME)
                .readText(Charsets.UTF_8),
            chords = dir.resolve("chords.lrc").readText(Charsets.UTF_8),
            timelineRaw = timelineFile.readText(Charsets.UTF_8),
            timeline = SmpTimelineStore.read(timelineFile),
            annotationsRaw = annotationsFile.readText(Charsets.UTF_8),
            annotations = SmpAnnotationsStore.read(annotationsFile),
            midiRaw = midiFile.readText(Charsets.UTF_8),
            midi = MidiCue.listFromJsonOrEmpty(midiFile.readText(Charsets.UTF_8)),
            dmxRaw = dmxFile.readText(Charsets.UTF_8),
            dmx = LightCue.listFromJsonOrEmpty(dmxFile.readText(Charsets.UTF_8)),
            gridRaw = gridFile.readText(Charsets.UTF_8),
            grid = GridFixture.fromJson(JSONObject(gridFile.readText(Charsets.UTF_8))),
            lyricsLineColors = config.lyricsLineColors,
            prompter = PrompterFixture(prompterFile.extension.lowercase(), prompterFile.readText(Charsets.UTF_8)),
            playback = PlaybackFixture.fromConfig(playback),
            arrangement = ArrangementJsonCodec.decode(
                JSONObject(dir.resolve("arrangement.json").readText(Charsets.UTF_8))
            )
        )
    }

    private fun assertArchiveContract(
        archive: File,
        fixtures: Map<String, MemberFixture>
    ) {
        ZipFile(archive).use { zip ->
            val names = zip.entries().asSequence().map { it.name }.toList()
            assertEquals(EXPECTED_ARCHIVE_ENTRIES, names.toSet())
            assertEquals(EXPECTED_ARCHIVE_ENTRIES.size, names.size)
            assertEquals(1, names.count { it.startsWith("audio.") })
            assertFalse(names.any { it.contains("title_aliases", ignoreCase = true) })
            assertFalse(names.any { it.contains("notes", ignoreCase = true) })
            assertFalse(names.any { it.contains("prompters", ignoreCase = true) })
            assertFalse(names.any { it.contains("waveform", ignoreCase = true) })

            val config = JSONObject(zip.readText("config.json"))
            assertEquals(PARENT_ID, config.getString("id"))
            assertEquals(fixtures.getValue(PARENT_ID).title, config.getString("title"))
            assertEquals(
                fixtures.getValue(PARENT_ID).customTitle,
                config.getString("customTitle")
            )
            val parentPlayback = config.getJSONObject("playback")
            assertFalse(parentPlayback.has("eq"))
            assertFalse(parentPlayback.keys().asSequence().any { it.startsWith("lufs") })

            val variants = ArrangementVariantsArchiveCodec.decode(
                JSONObject(zip.readText(ArrangementVariantsArchiveCodec.FILE_NAME))
            )
            assertEquals(PARENT_ID, variants.sourceSongId)
            assertEquals(listOf(VARIANT_A_ID, VARIANT_B_ID), variants.variants.map { it.id })
            variants.variants.forEach { variant ->
                val fixture = fixtures.getValue(variant.id)
                assertEquals(fixture.title, variant.title)
                assertEquals(fixture.customTitle, variant.customTitle?.value)
                assertEquals(fixture.sourceSongId, variant.arrangement.sourceSongId)
                assertEquals(fixture.prompter.format, variant.prompter?.format)
                assertEquals(fixture.prompter.content, variant.prompter?.content)
                assertEquals(fixture.playback, PlaybackFixture.fromConfig(requireNotNull(variant.playback)))
            }
            val variantsJson = JSONObject(zip.readText(ArrangementVariantsArchiveCodec.FILE_NAME))
            val items = variantsJson.getJSONArray("variants")
            for (index in 0 until items.length()) {
                val item = items.getJSONObject(index)
                assertFalse(item.has("audio"))
                assertFalse(item.getJSONObject("assets").has("audio"))
                val playback = item.getJSONObject("playback")
                assertFalse(playback.has("eq"))
                assertFalse(playback.keys().asSequence().any { it.startsWith("lufs") })
            }
        }
    }

    private fun ZipFile.readText(entryName: String): String {
        val entry = requireNotNull(getEntry(entryName))
        return getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun assertFixtureIsolation(snapshot: FamilySnapshot) {
        val members = listOf(snapshot.parent) + snapshot.variants
        assertEquals(3, members.map(MemberSnapshot::id).toSet().size)
        assertEquals(3, members.map(MemberSnapshot::title).toSet().size)
        assertEquals(3, members.map(MemberSnapshot::customTitle).toSet().size)
        assertEquals(3, members.map(MemberSnapshot::lyrics).toSet().size)
        assertEquals(3, members.map(MemberSnapshot::lyricsEditorRaw).toSet().size)
        assertEquals(3, members.map(MemberSnapshot::chords).toSet().size)
        assertEquals(3, members.map(MemberSnapshot::timeline).toSet().size)
        assertEquals(3, members.map(MemberSnapshot::annotations).toSet().size)
        assertEquals(3, members.map(MemberSnapshot::midi).toSet().size)
        assertEquals(3, members.map(MemberSnapshot::dmx).toSet().size)
        assertEquals(3, members.map(MemberSnapshot::grid).toSet().size)
        assertEquals(3, members.map(MemberSnapshot::lyricsLineColors).toSet().size)
        assertEquals(3, members.map(MemberSnapshot::prompter).toSet().size)
        assertEquals(3, members.map(MemberSnapshot::playback).toSet().size)
        assertEquals(3, members.map(MemberSnapshot::arrangement).toSet().size)
        assertNotEquals(snapshot.parent.playback, snapshot.variants[0].playback)
        assertNotEquals(snapshot.variants[0].playback, snapshot.variants[1].playback)
    }

    private fun arrangement(
        idPrefix: String,
        title: String,
        sourceSongId: String,
        firstStartMs: Long,
        secondStartMs: Long
    ): ArrangementData {
        return ArrangementData(
            version = 2,
            name = title,
            sourceSongId = sourceSongId,
            updatedAt = 1_700_000_000_000L + firstStartMs,
            segments = emptyList(),
            structureSegmentIds = emptyList(),
            entries = listOf(
                ArrangementEntryData(
                    entryId = "${idPrefix}_intro",
                    name = "Intro $idPrefix",
                    startMs = firstStartMs,
                    endMs = firstStartMs + 6_000L,
                    repeatCount = 2,
                    muted = false,
                    color = "#112233"
                ),
                ArrangementEntryData(
                    entryId = "${idPrefix}_final",
                    name = "Final $idPrefix",
                    startMs = secondStartMs,
                    endMs = secondStartMs + 8_000L,
                    repeatCount = 1,
                    muted = idPrefix == "b",
                    color = "#AABBCC"
                )
            )
        )
    }

    private fun validWavBytes(sampleSeed: Int): ByteArray {
        val samples = byteArrayOf(sampleSeed.toByte(), (sampleSeed + 1).toByte(), (sampleSeed + 2).toByte(), (sampleSeed + 3).toByte())
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

    private fun fakeUri(value: String): Uri {
        val uri = Mockito.mock(Uri::class.java)
        val scheme = value.substringBefore(":", missingDelimiterValue = "")
        val path = if (scheme == "file") value.removePrefix("file://") else value.substringAfter("://", "")
            .substringAfter('/', "")
            .let { "/$it" }
        Mockito.`when`(uri.toString()).thenReturn(value)
        Mockito.`when`(uri.scheme).thenReturn(scheme)
        Mockito.`when`(uri.path).thenReturn(path)
        Mockito.`when`(uri.lastPathSegment).thenReturn(value.substringAfterLast('/'))
        return uri
    }

    private data class MemberFixture(
        val id: String,
        val title: String,
        val customTitle: String,
        val sourceSongId: String?,
        val lyrics: String,
        val lyricsEditorRaw: String,
        val chords: String,
        val timeline: List<TimelineMarker>,
        val annotations: List<LiveNote>,
        val midi: List<MidiCue>,
        val dmx: List<LightCue>,
        val grid: GridFixture,
        val lyricsLineColors: Map<String, Int>,
        val prompter: PrompterFixture,
        val playback: PlaybackFixture,
        val arrangement: ArrangementData,
        val audio: ByteArray? = null
    )

    private data class FamilySnapshot(
        val parent: MemberSnapshot,
        val variants: List<MemberSnapshot>
    )

    private data class MemberSnapshot(
        val id: String,
        val title: String,
        val customTitle: String?,
        val sourceSongId: String?,
        val persistentFiles: List<String>,
        val audio: List<Byte>?,
        val lyrics: String,
        val lyricsEditorRaw: String,
        val chords: String,
        val timelineRaw: String,
        val timeline: List<TimelineMarker>,
        val annotationsRaw: String,
        val annotations: List<LiveNote>,
        val midiRaw: String,
        val midi: List<MidiCue>,
        val dmxRaw: String,
        val dmx: List<LightCue>,
        val gridRaw: String,
        val grid: GridFixture,
        val lyricsLineColors: Map<String, Int>?,
        val prompter: PrompterFixture?,
        val playback: PlaybackFixture,
        val arrangement: ArrangementData
    )

    private data class PrompterFixture(
        val format: String,
        val content: String
    )

    private data class PlaybackFixture(
        val trimStartMs: Long,
        val trimEndMs: Long?,
        val tempo: Float,
        val pitchSemi: Int,
        val volumeDb: Int
    ) {
        fun toConfig() = SmpConfig.PlaybackConfig(
            trimStartMs = trimStartMs,
            trimEndMs = trimEndMs,
            tempo = tempo,
            pitchSemi = pitchSemi,
            volumeDb = volumeDb,
            volumeSource = SmpConfig.PlaybackConfig.VOLUME_SOURCE_MANUAL
        )

        companion object {
            fun fromConfig(config: SmpConfig.PlaybackConfig) = PlaybackFixture(
                trimStartMs = config.trimStartMs ?: 0L,
                trimEndMs = config.trimEndMs,
                tempo = config.tempo ?: 1f,
                pitchSemi = config.pitchSemi ?: 0,
                volumeDb = config.volumeDb ?: 0
            )
        }
    }

    private data class GridFixture(
        val tempoBpm: Double,
        val syncPointMs: Long,
        val inMs: Long,
        val outMs: Long,
        val numerator: Int,
        val denominator: Int
    ) {
        fun toJson(): String = JSONObject()
            .put("tempoBpm", tempoBpm)
            .put("syncPointMs", syncPointMs)
            .put("inMs", inMs)
            .put("outMs", outMs)
            .put("timeSignatureNumerator", numerator)
            .put("timeSignatureDenominator", denominator)
            .toString(2)

        companion object {
            fun fromJson(json: JSONObject) = GridFixture(
                tempoBpm = json.getDouble("tempoBpm"),
                syncPointMs = json.getLong("syncPointMs"),
                inMs = json.getLong("inMs"),
                outMs = json.getLong("outMs"),
                numerator = json.getInt("timeSignatureNumerator"),
                denominator = json.getInt("timeSignatureDenominator")
            )
        }
    }

    private class InMemorySharedPreferences : SharedPreferences {
        private val values = linkedMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = values.toMutableMap()
        override fun getString(key: String, defValue: String?): String? = values[key] as? String ?: defValue
        override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
            (values[key] as? Set<*>)?.filterIsInstance<String>()?.toMutableSet() ?: defValues
        override fun getInt(key: String, defValue: Int): Int = values[key] as? Int ?: defValue
        override fun getLong(key: String, defValue: Long): Long = values[key] as? Long ?: defValue
        override fun getFloat(key: String, defValue: Float): Float = values[key] as? Float ?: defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
        override fun contains(key: String): Boolean = values.containsKey(key)
        override fun edit(): SharedPreferences.Editor = Editor(values)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        private class Editor(
            private val values: MutableMap<String, Any?>
        ) : SharedPreferences.Editor {
            private val updates = linkedMapOf<String, Any?>()
            private val removals = linkedSetOf<String>()
            private var clearRequested = false

            override fun putString(key: String, value: String?): SharedPreferences.Editor = apply { updates[key] = value }
            override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor =
                apply { updates[key] = values?.toSet() }
            override fun putInt(key: String, value: Int): SharedPreferences.Editor = apply { updates[key] = value }
            override fun putLong(key: String, value: Long): SharedPreferences.Editor = apply { updates[key] = value }
            override fun putFloat(key: String, value: Float): SharedPreferences.Editor = apply { updates[key] = value }
            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = apply { updates[key] = value }
            override fun remove(key: String): SharedPreferences.Editor = apply { removals += key }
            override fun clear(): SharedPreferences.Editor = apply { clearRequested = true }
            override fun commit(): Boolean {
                if (clearRequested) values.clear()
                removals.forEach(values::remove)
                updates.forEach { (key, value) ->
                    if (value == null) values.remove(key) else values[key] = value
                }
                return true
            }
            override fun apply() {
                commit()
            }
        }
    }

    private companion object {
        const val TEMP_PREFIX = "complete_song_unit_family_"
        const val TRACKS_DIR_NAME = "tracks"
        const val PARENT_ID = "family_parent"
        const val VARIANT_A_ID = "family_variant_a"
        const val VARIANT_B_ID = "family_variant_b"

        val EXPECTED_ARCHIVE_ENTRIES = setOf(
            "config.json",
            "audio.wav",
            "lyrics.lrc",
            LrcStorage.LYRICS_EDITOR_RAW_FILE_NAME,
            "chords.lrc",
            SmpTimelineStore.TIMELINE_FILE_NAME,
            SmpAnnotationsStore.ANNOTATIONS_FILE_NAME,
            SmpMidiCuesStore.MIDI_CUES_FILE_NAME,
            SmpLightCueStore.LIGHT_CUES_FILE_NAME,
            "grid.json",
            "prompter.txt",
            "arrangement.json",
            ArrangementVariantsArchiveCodec.FILE_NAME
        )
    }
}
