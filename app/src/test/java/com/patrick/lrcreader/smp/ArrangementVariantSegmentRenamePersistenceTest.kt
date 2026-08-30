package com.patrick.lrcreader.smp

import android.content.Context
import com.patrick.lrcreader.ui.renamePersistedArrangementSegment
import com.patrick.lrcreader.ui.shouldDeferVariantArrangementPersistence
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito
import java.nio.file.Files

class ArrangementVariantSegmentRenamePersistenceTest {

    @Test
    fun explicitVariantRename_persistsOnlyVariantAndSurvivesTransport() = runBlocking {
        val filesDir = Files.createTempDirectory("variant_segment_rename_").toFile()
        val context = Mockito.mock(Context::class.java)
        Mockito.`when`(context.filesDir).thenReturn(filesDir)

        try {
            val parent = arrangement(
                name = "Parent",
                entry = ArrangementEntryData(
                    entryId = "parent_intro",
                    name = "Parent intro",
                    startMs = 0L,
                    endMs = 4_000L
                )
            )
            val originalEntry = ArrangementEntryData(
                entryId = "variant_intro",
                name = "Segment 1",
                startMs = 1_000L,
                endMs = 5_000L,
                repeatCount = 3,
                muted = true,
                color = "#AABBCC"
            )
            val variant = arrangement(name = "Variante", entry = originalEntry)

            assertTrue(ArrangementStore.save(context, PARENT_ID, parent))
            assertTrue(ArrangementStore.save(context, VARIANT_ID, variant))
            val parentFile = filesDir.resolve("tracks/$PARENT_ID/arrangement.json")
            val parentBeforeRename = parentFile.readBytes()

            assertTrue(shouldDeferVariantArrangementPersistence(VARIANT_ID, false))
            assertFalse(shouldDeferVariantArrangementPersistence(VARIANT_ID, true))
            assertFalse(shouldDeferVariantArrangementPersistence(null, false))

            val editedInMemoryEntry = originalEntry.copy(
                name = "Intro",
                startMs = 2_000L,
                endMs = 6_000L,
                repeatCount = 5,
                muted = false,
                color = "#112233"
            )
            val editedInMemoryVariant = buildArrangementDataForPersistence(
                useOccurrenceModel = true,
                name = variant.name,
                sourceSongId = PARENT_ID,
                segments = listOf(editedInMemoryEntry.toSegmentData()),
                structureSegmentIds = listOf(editedInMemoryEntry.entryId),
                existingEntries = listOf(editedInMemoryEntry),
                preservedLegacySegments = emptyList()
            )
            val renamedVariant = renamePersistedArrangementSegment(
                data = requireNotNull(ArrangementStore.load(context, VARIANT_ID)),
                targetSegmentId = originalEntry.entryId,
                nextName = editedInMemoryVariant.entries.single().name
            )

            assertTrue(ArrangementStore.save(context, VARIANT_ID, renamedVariant))

            val reloadedVariant = requireNotNull(ArrangementStore.load(context, VARIANT_ID))
            val reloadedEntry = reloadedVariant.entries.single()
            assertEquals(PARENT_ID, reloadedVariant.sourceSongId)
            assertEquals("Intro", reloadedEntry.name)
            assertEquals(originalEntry.entryId, reloadedEntry.entryId)
            assertEquals(originalEntry.startMs, reloadedEntry.startMs)
            assertEquals(originalEntry.endMs, reloadedEntry.endMs)
            assertEquals(originalEntry.repeatCount, reloadedEntry.repeatCount)
            assertEquals(originalEntry.muted, reloadedEntry.muted)
            assertEquals(originalEntry.color, reloadedEntry.color)
            assertEquals(listOf(originalEntry.entryId), reloadedVariant.structureSegmentIds)
            assertArrayEquals(parentBeforeRename, parentFile.readBytes())

            val transported = ArrangementVariantsArchiveCodec.decode(
                ArrangementVariantsArchiveCodec.encode(
                    ArrangementVariantsArchive(
                        sourceSongId = PARENT_ID,
                        variants = listOf(
                            ArrangementVariantArchiveEntry(
                                id = VARIANT_ID,
                                title = "Variante",
                                arrangement = reloadedVariant
                            )
                        )
                    )
                )
            ).variants.single().arrangement.entries.single()
            assertEquals(reloadedEntry, transported)
        } finally {
            filesDir.deleteRecursively()
        }
    }

    private fun arrangement(
        name: String,
        entry: ArrangementEntryData
    ): ArrangementData = ArrangementData(
        version = 2,
        name = name,
        sourceSongId = PARENT_ID,
        segments = listOf(entry.toSegmentData()),
        structureSegmentIds = listOf(entry.entryId),
        entries = listOf(entry)
    )

    private companion object {
        const val PARENT_ID = "parent_song"
        const val VARIANT_ID = "variant_song"
    }
}
