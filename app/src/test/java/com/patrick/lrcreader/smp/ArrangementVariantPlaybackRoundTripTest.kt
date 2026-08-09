package com.patrick.lrcreader.smp

import android.content.Context
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito
import java.nio.file.Files

class ArrangementVariantPlaybackRoundTripTest {

    @Test
    fun parentProfileIsNotStoredInsideVariantEntry() {
        val encoded = encodeFamily(profileA(), profileB())

        assertEquals(PARENT_ID, encoded.getString("sourceSongId"))
        assertFalse(encoded.has("playback"))
    }

    @Test
    fun variantAPreservesItsFivePlaybackValues() {
        assertProfileEquals(profileA(), roundTrip(profileA(), profileB()).variants[0].playback)
    }

    @Test
    fun variantBPreservesItsFivePlaybackValues() {
        assertProfileEquals(profileB(), roundTrip(profileA(), profileB()).variants[1].playback)
    }

    @Test
    fun completeFamilyRoundTripPreservesIndependentProfiles() {
        val decoded = roundTrip(profileA(), profileB())

        assertEquals(listOf(VARIANT_A, VARIANT_B), decoded.variants.map { it.id })
        assertProfileEquals(profileA(), decoded.variants[0].playback)
        assertProfileEquals(profileB(), decoded.variants[1].playback)
    }

    @Test
    fun legacyArchiveWithoutPlaybackKeepsContractAbsent() {
        val encoded = ArrangementVariantsArchiveCodec.encode(
            archive(playbackA = null, playbackB = null)
        )
        val decoded = ArrangementVariantsArchiveCodec.decode(encoded)

        assertFalse(encoded.getJSONArray("variants").getJSONObject(0).has("playback"))
        assertNull(decoded.variants[0].playback)
        assertNull(decoded.variants[1].playback)
    }

    @Test
    fun explicitNeutralProfileIsDistinctFromContractAbsence() {
        val encoded = encodeFamily(neutralProfile(), profileB())
        val variantJson = encoded.getJSONArray("variants").getJSONObject(0)
        val decoded = ArrangementVariantsArchiveCodec.decode(encoded).variants[0].playback

        assertTrue(variantJson.has("playback"))
        assertNotNull(decoded)
        assertProfileEquals(neutralProfile(), decoded)
    }

    @Test
    fun playbackOwnerIdentityIsVariantSongId() {
        val decoded = roundTrip(profileA(), profileB())

        assertEquals(VARIANT_A, decoded.variants[0].id)
        assertEquals(VARIANT_B, decoded.variants[1].id)
        assertEquals(PARENT_ID, decoded.variants[0].arrangement.sourceSongId)
    }

    @Test
    fun playbackContractContainsNoParentAudioUri() {
        val playback = encodeFamily(profileA(), profileB())
            .getJSONArray("variants")
            .getJSONObject(0)
            .getJSONObject("playback")

        assertFalse(playback.has("uri"))
        assertFalse(playback.has("audio"))
        assertFalse(playback.toString().contains("file:"))
    }

    @Test
    fun switchingAcrossFamilyDoesNotMixProfiles() {
        val decoded = roundTrip(profileA(), profileB())
        val byId = decoded.variants.associateBy(ArrangementVariantArchiveEntry::id)

        assertProfileEquals(profileA(), byId.getValue(VARIANT_A).playback)
        assertProfileEquals(profileB(), byId.getValue(VARIANT_B).playback)
        assertProfileEquals(profileA(), byId.getValue(VARIANT_A).playback)
    }

    @Test
    fun changingVariantATempoLeavesVariantBUntouched() {
        val changedA = profileA().copy(tempo = 1.25f)
        val decoded = roundTrip(changedA, profileB())

        assertEquals(1.25f, decoded.variants[0].playback?.tempo)
        assertProfileEquals(profileB(), decoded.variants[1].playback)
    }

    @Test
    fun changingVariantBPitchLeavesVariantAUntouched() {
        val changedB = profileB().copy(pitchSemi = -4)
        val decoded = roundTrip(profileA(), changedB)

        assertProfileEquals(profileA(), decoded.variants[0].playback)
        assertEquals(-4, decoded.variants[1].playback?.pitchSemi)
    }

    @Test
    fun changingVariantAGainLeavesVariantBUntouched() {
        val changedA = profileA().copy(volumeDb = 6)
        val decoded = roundTrip(changedA, profileB())

        assertEquals(6, decoded.variants[0].playback?.volumeDb)
        assertProfileEquals(profileB(), decoded.variants[1].playback)
    }

    @Test
    fun eachVariantKeepsItsOwnInAndOutPoints() {
        val decoded = roundTrip(profileA(), profileB())

        assertEquals(20_000L, decoded.variants[0].playback?.trimStartMs)
        assertEquals(210_000L, decoded.variants[0].playback?.trimEndMs)
        assertEquals(5_000L, decoded.variants[1].playback?.trimStartMs)
        assertEquals(260_000L, decoded.variants[1].playback?.trimEndMs)
    }

    @Test
    fun selectedVariantDoesNotAlterSiblingPlayback() {
        val source = archive(profileA(), profileB()).copy(selectedVariantId = VARIANT_A)
        val decoded = ArrangementVariantsArchiveCodec.decode(
            ArrangementVariantsArchiveCodec.encode(source)
        )

        assertEquals(VARIANT_A, decoded.selectedVariantId)
        assertProfileEquals(profileB(), decoded.variants.single { it.id == VARIANT_B }.playback)
    }

    @Test
    fun restoringExistingVariantReplacesItsExplicitPlayback() {
        val root = Files.createTempDirectory("variant_playback_replace_").toFile()
        try {
            val existing = root.resolve("existing").apply { mkdirs() }
            val target = root.resolve("target").apply { mkdirs() }
            writeVariantConfig(existing, profileA())

            ArrangementVariantStore.writeVariantFiles(
                targetDir = target,
                variantId = VARIANT_A,
                title = "Variante A",
                sourceSongId = PARENT_ID,
                arrangement = arrangement(VARIANT_A),
                existingVariantDir = existing,
                archivedPlayback = profileB()
            )

            assertProfileEquals(
                profileB(),
                SmpVariantPlayback.readExplicitProfile(target.resolve("config.json"))
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun legacyRestorePreservesExistingLocalVariantPlayback() {
        val root = Files.createTempDirectory("variant_playback_legacy_").toFile()
        try {
            val existing = root.resolve("existing").apply { mkdirs() }
            val target = root.resolve("target").apply { mkdirs() }
            writeVariantConfig(existing, profileA())

            ArrangementVariantStore.writeVariantFiles(
                targetDir = target,
                variantId = VARIANT_A,
                title = "Variante A",
                sourceSongId = PARENT_ID,
                arrangement = arrangement(VARIANT_A),
                existingVariantDir = existing,
                archivedPlayback = null
            )

            assertProfileEquals(
                profileA(),
                SmpVariantPlayback.readExplicitProfile(target.resolve("config.json"))
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun invalidPlaybackValuesAreClampedToExistingBounds() {
        val decoded = SmpVariantPlayback.decode(
            JSONObject()
                .put("trimStartMs", -10)
                .put("trimEndMs", -20)
                .put("tempo", 8.0)
                .put("pitchSemi", -99)
                .put("volumeDb", 42)
        )

        assertEquals(0L, decoded.trimStartMs)
        assertNull(decoded.trimEndMs)
        assertEquals(2f, decoded.tempo)
        assertEquals(-6, decoded.pitchSemi)
        assertEquals(6, decoded.volumeDb)
    }

    @Test
    fun codecVersionRemainsBackwardCompatibleVersionOne() {
        assertEquals(1, encodeFamily(profileA(), profileB()).getInt("version"))
    }

    @Test
    fun playbackContractTransportsNoEqOrLufsFields() {
        val source = profileA().copy(
            volumeSource = SmpConfig.PlaybackConfig.VOLUME_SOURCE_LUFS,
            lufsMeasured = -10f,
            lufsTarget = -14f,
            lufsAutoDb = -4f,
            lufsManualDb = 2
        )
        val playback = SmpVariantPlayback.encode(source)

        assertFalse(playback.has("eq"))
        assertFalse(playback.has("volumeSource"))
        assertFalse(playback.keys().asSequence().any { it.startsWith("lufs") })
    }

    @Test
    fun playbackIsAPropertyOfVariantNotAnAssetOrAudioFile() {
        val variant = encodeFamily(profileA(), profileB())
            .getJSONArray("variants")
            .getJSONObject(0)

        assertTrue(variant.has("playback"))
        assertFalse(variant.optJSONObject("assets")?.has("playback") == true)
        assertEquals(setOf("trimStartMs", "trimEndMs", "tempo", "pitchSemi", "volumeDb"),
            variant.getJSONObject("playback").keys().asSequence().toSet())
    }

    @Test
    fun localVariantUpdatePreservesArrangementIdentityAndUnrelatedMetadata() {
        val root = Files.createTempDirectory("variant_playback_local_update_").toFile()
        try {
            val configFile = root.resolve("config.json")
            writeVariantConfig(root, profileA())
            val rawJson = SmpVariantPlayback.mergeProfileUpdate(
                context = Mockito.mock(Context::class.java),
                configFile = configFile
            ) { current -> current?.copy(tempo = 1.25f) }

            val updated = JSONObject(requireNotNull(rawJson))
            assertEquals(PARENT_ID, updated.getJSONObject("arrangementVariant").getString("sourceSongId"))
            assertEquals(VARIANT_A, updated.getString("id"))
            assertEquals(1.25, updated.getJSONObject("playback").getDouble("tempo"), 0.0)
            assertEquals(-2, updated.getJSONObject("playback").getInt("pitchSemi"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun localVariantUpdateKeepsNeutralValuesExplicit() {
        val root = Files.createTempDirectory("variant_playback_neutral_update_").toFile()
        try {
            val configFile = root.resolve("config.json")
            writeVariantConfig(root, neutralProfile())
            val rawJson = SmpVariantPlayback.mergeProfileUpdate(
                context = Mockito.mock(Context::class.java),
                configFile = configFile
            ) { current -> current?.copy(volumeDb = -1) }

            val playback = JSONObject(requireNotNull(rawJson)).getJSONObject("playback")
            assertEquals(0L, playback.getLong("trimStartMs"))
            assertEquals(1.0, playback.getDouble("tempo"), 0.0)
            assertEquals(0, playback.getInt("pitchSemi"))
            assertEquals(-1, playback.getInt("volumeDb"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun updatingOneLocalVariantConfigCannotChangeSiblingFile() {
        val root = Files.createTempDirectory("variant_playback_sibling_").toFile()
        try {
            val variantADir = root.resolve(VARIANT_A).apply { mkdirs() }
            val variantBDir = root.resolve(VARIANT_B).apply { mkdirs() }
            writeVariantConfig(variantADir, profileA())
            writeVariantConfig(variantBDir, profileB(), VARIANT_B)
            val siblingBefore = variantBDir.resolve("config.json").readText(Charsets.UTF_8)

            val updatedA = SmpVariantPlayback.mergeProfileUpdate(
                context = Mockito.mock(Context::class.java),
                configFile = variantADir.resolve("config.json")
            ) { current -> current?.copy(pitchSemi = 5) }
            variantADir.resolve("config.json").writeText(requireNotNull(updatedA), Charsets.UTF_8)

            assertEquals(siblingBefore, variantBDir.resolve("config.json").readText(Charsets.UTF_8))
            assertEquals(5, SmpVariantPlayback.readExplicitProfile(variantADir.resolve("config.json"))?.pitchSemi)
            assertProfileEquals(profileB(), SmpVariantPlayback.readExplicitProfile(variantBDir.resolve("config.json")))
        } finally {
            root.deleteRecursively()
        }
    }

    private fun roundTrip(
        playbackA: SmpConfig.PlaybackConfig?,
        playbackB: SmpConfig.PlaybackConfig?
    ): ArrangementVariantsArchive = ArrangementVariantsArchiveCodec.decode(
        encodeFamily(playbackA, playbackB)
    )

    private fun encodeFamily(
        playbackA: SmpConfig.PlaybackConfig?,
        playbackB: SmpConfig.PlaybackConfig?
    ): JSONObject = ArrangementVariantsArchiveCodec.encode(archive(playbackA, playbackB))

    private fun archive(
        playbackA: SmpConfig.PlaybackConfig?,
        playbackB: SmpConfig.PlaybackConfig?
    ) = ArrangementVariantsArchive(
        sourceSongId = PARENT_ID,
        variants = listOf(
            ArrangementVariantArchiveEntry(
                id = VARIANT_A,
                title = "Variante A",
                arrangement = arrangement(VARIANT_A),
                playback = playbackA
            ),
            ArrangementVariantArchiveEntry(
                id = VARIANT_B,
                title = "Variante B",
                arrangement = arrangement(VARIANT_B),
                playback = playbackB
            )
        )
    )

    private fun arrangement(variantId: String) = ArrangementData(
        version = 2,
        name = variantId,
        sourceSongId = PARENT_ID,
        updatedAt = 1234L,
        segments = emptyList(),
        structureSegmentIds = emptyList(),
        entries = listOf(
            ArrangementEntryData(
                entryId = "entry_$variantId",
                name = "Segment",
                startMs = 0L,
                endMs = 300_000L
            )
        )
    )

    private fun profileA() = SmpConfig.PlaybackConfig(
        trimStartMs = 20_000L,
        trimEndMs = 210_000L,
        tempo = 0.9f,
        pitchSemi = -2,
        volumeDb = -3,
        volumeSource = SmpConfig.PlaybackConfig.VOLUME_SOURCE_MANUAL
    )

    private fun profileB() = SmpConfig.PlaybackConfig(
        trimStartMs = 5_000L,
        trimEndMs = 260_000L,
        tempo = 1.1f,
        pitchSemi = 1,
        volumeDb = 2,
        volumeSource = SmpConfig.PlaybackConfig.VOLUME_SOURCE_MANUAL
    )

    private fun neutralProfile() = SmpConfig.PlaybackConfig(
        trimStartMs = 0L,
        trimEndMs = null,
        tempo = 1f,
        pitchSemi = 0,
        volumeDb = 0,
        volumeSource = SmpConfig.PlaybackConfig.VOLUME_SOURCE_MANUAL
    )

    private fun writeVariantConfig(
        directory: java.io.File,
        playback: SmpConfig.PlaybackConfig,
        variantId: String = VARIANT_A
    ) {
        directory.resolve("config.json").writeText(
            JSONObject()
                .put("version", 1)
                .put("id", variantId)
                .put("title", "Variante A")
                .put("arrangementVariant", JSONObject().put("sourceSongId", PARENT_ID))
                .put("playback", SmpVariantPlayback.encode(playback))
                .toString(2),
            Charsets.UTF_8
        )
    }

    private fun assertProfileEquals(
        expected: SmpConfig.PlaybackConfig,
        actual: SmpConfig.PlaybackConfig?
    ) {
        assertNotNull(actual)
        assertEquals(expected.trimStartMs, actual?.trimStartMs)
        assertEquals(expected.trimEndMs, actual?.trimEndMs)
        assertEquals(expected.tempo, actual?.tempo)
        assertEquals(expected.pitchSemi, actual?.pitchSemi)
        assertEquals(expected.volumeDb, actual?.volumeDb)
    }

    private companion object {
        const val PARENT_ID = "parent_song"
        const val VARIANT_A = "variant_a"
        const val VARIANT_B = "variant_b"
    }
}
