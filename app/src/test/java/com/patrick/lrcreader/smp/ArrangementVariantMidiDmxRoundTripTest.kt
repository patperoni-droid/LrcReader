package com.patrick.lrcreader.smp

import com.patrick.lrcreader.core.light.LightAction
import com.patrick.lrcreader.core.light.LightCue
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

class ArrangementVariantMidiDmxRoundTripTest {

    @Test
    fun familyRoundTrip_preservesEachVariantMidiAndDmxExactly() {
        val root = Files.createTempDirectory("variant_midi_dmx_round_trip_").toFile()
        try {
            val runtimeRoot = root.resolve("runtime").apply { mkdirs() }
            val restoredRoot = root.resolve("restored").apply { mkdirs() }
            val midiByVariant = linkedMapOf(
                "variant_a" to listOf(
                    MidiCue(time = 0.5, type = "PC", value = 12, channel = 1),
                    MidiCue(time = 3.25, type = "CC", value = 64, channel = 2),
                    MidiCue(time = 9.0, type = "PC", value = 42, channel = 16)
                ),
                "variant_b" to listOf(
                    MidiCue(time = 0.75, type = "CC", value = 7, channel = 3),
                    MidiCue(time = 4.5, type = "PC", value = 8, channel = 10),
                    MidiCue(time = 14.25, type = "CC", value = 127, channel = 15)
                )
            )
            val dmxByVariant = linkedMapOf(
                "variant_a" to listOf(
                    LightCue(
                        timeMs = 500L,
                        action = LightAction.Color(0xFF3366CC),
                        intensity = 0.75f,
                        fadeMs = 1_000L,
                        durationMs = 4_000L,
                        fadeOutMs = 750L
                    ),
                    LightCue(
                        timeMs = 5_000L,
                        action = LightAction.Strobe(7.5f),
                        intensity = 0.5f,
                        fadeMs = 250L,
                        durationMs = 2_000L,
                        fadeOutMs = 500L
                    ),
                    LightCue(timeMs = 9_500L, action = LightAction.Blackout)
                ),
                "variant_b" to listOf(
                    LightCue(
                        timeMs = 750L,
                        action = LightAction.Color(0xFFFF8800),
                        intensity = 1f,
                        fadeMs = 2_000L,
                        durationMs = 6_000L,
                        fadeOutMs = 1_500L
                    ),
                    LightCue(
                        timeMs = 8_000L,
                        action = LightAction.Strobe(12f),
                        intensity = 0.25f,
                        fadeMs = 0L,
                        durationMs = 3_000L,
                        fadeOutMs = 250L
                    ),
                    LightCue(timeMs = 15_000L, action = LightAction.Blackout, fadeMs = 500L)
                )
            )

            val sourceEntries = midiByVariant.keys.map { variantId ->
                val variantDir = runtimeRoot.resolve(variantId).apply { mkdirs() }
                variantDir.resolve(SmpMidiCuesStore.MIDI_CUES_FILE_NAME).writeText(
                    MidiCue.toJsonString(midiByVariant.getValue(variantId)),
                    Charsets.UTF_8
                )
                variantDir.resolve(SmpLightCueStore.LIGHT_CUES_FILE_NAME).writeText(
                    LightCue.toJsonString(dmxByVariant.getValue(variantId)),
                    Charsets.UTF_8
                )
                ArrangementVariantArchiveEntry(
                    id = variantId,
                    title = variantId,
                    arrangement = arrangementFor(variantId),
                    midiCues = variantDir.resolve(SmpMidiCuesStore.MIDI_CUES_FILE_NAME)
                        .readText(Charsets.UTF_8),
                    dmxCues = variantDir.resolve(SmpLightCueStore.LIGHT_CUES_FILE_NAME)
                        .readText(Charsets.UTF_8)
                )
            }
            val encoded = ArrangementVariantsArchiveCodec.encode(
                ArrangementVariantsArchive(
                    sourceSongId = PARENT_ID,
                    variants = sourceEntries
                )
            )

            runtimeRoot.deleteRecursively()

            val decoded = ArrangementVariantsArchiveCodec.decode(encoded)
            decoded.variants.forEach { variant ->
                val targetDir = restoredRoot.resolve(variant.id).apply { mkdirs() }
                ArrangementVariantStore.writeVariantFiles(
                    targetDir = targetDir,
                    variantId = variant.id,
                    title = variant.title,
                    sourceSongId = PARENT_ID,
                    arrangement = variant.arrangement,
                    archivedMidiCues = variant.midiCues,
                    archivedDmxCues = variant.dmxCues
                )
            }

            assertEquals(midiByVariant.keys.toList().sorted(), decoded.variants.map { it.id })
            midiByVariant.keys.forEach { variantId ->
                assertEquals(
                    midiByVariant.getValue(variantId),
                    MidiCue.listFromJsonOrEmpty(
                        restoredRoot.resolve(variantId)
                            .resolve(SmpMidiCuesStore.MIDI_CUES_FILE_NAME)
                            .readText(Charsets.UTF_8)
                    )
                )
                assertEquals(
                    dmxByVariant.getValue(variantId),
                    LightCue.listFromJsonOrEmpty(
                        restoredRoot.resolve(variantId)
                            .resolve(SmpLightCueStore.LIGHT_CUES_FILE_NAME)
                            .readText(Charsets.UTF_8)
                    )
                )
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun archiveWithoutMidiOrDmx_preservesExistingVariantAssets() {
        val root = Files.createTempDirectory("variant_midi_dmx_selective_restore_").toFile()
        try {
            val existing = root.resolve("existing").apply { mkdirs() }
            val target = root.resolve("target").apply { mkdirs() }
            val midi = listOf(MidiCue(time = 1.0, type = "PC", value = 5, channel = 1))
            val dmx = listOf(
                LightCue(timeMs = 1_000L, action = LightAction.Blackout, fadeMs = 500L)
            )
            existing.resolve(SmpMidiCuesStore.MIDI_CUES_FILE_NAME).writeText(
                MidiCue.toJsonString(midi),
                Charsets.UTF_8
            )
            existing.resolve(SmpLightCueStore.LIGHT_CUES_FILE_NAME).writeText(
                LightCue.toJsonString(dmx),
                Charsets.UTF_8
            )

            ArrangementVariantStore.writeVariantFiles(
                targetDir = target,
                variantId = "variant",
                title = "Variante",
                sourceSongId = PARENT_ID,
                arrangement = arrangementFor("variant"),
                existingVariantDir = existing
            )

            assertEquals(
                midi,
                MidiCue.listFromJsonOrEmpty(
                    target.resolve(SmpMidiCuesStore.MIDI_CUES_FILE_NAME).readText(Charsets.UTF_8)
                )
            )
            assertEquals(
                dmx,
                LightCue.listFromJsonOrEmpty(
                    target.resolve(SmpLightCueStore.LIGHT_CUES_FILE_NAME).readText(Charsets.UTF_8)
                )
            )
        } finally {
            root.deleteRecursively()
        }
    }

    private fun arrangementFor(variantId: String) = ArrangementData(
        version = 2,
        name = variantId,
        sourceSongId = PARENT_ID,
        updatedAt = 1234L,
        segments = emptyList(),
        structureSegmentIds = emptyList(),
        entries = listOf(
            ArrangementEntryData(
                entryId = "segment_$variantId",
                name = "Segment",
                startMs = 0L,
                endMs = 20_000L
            )
        )
    )

    private companion object {
        const val PARENT_ID = "parent_song"
    }
}
