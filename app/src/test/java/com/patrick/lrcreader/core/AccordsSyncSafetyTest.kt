package com.patrick.lrcreader.core

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccordsSyncSafetyTest {

    @Test
    fun latestAccordsWriteQueue_keepsLatestWriteWhenRapidCapturesArrive() {
        val persisted = AtomicReference("")
        val writes = CopyOnWriteArrayList<String>()
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val latestApplied = CountDownLatch(1)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val queue = LatestAccordsWriteQueue<String>(
            scope = scope
        ) { payload ->
            if (payload == "old") {
                firstStarted.countDown()
                assertTrue(releaseFirst.await(2, TimeUnit.SECONDS))
            }
            writes += payload
            persisted.set(payload)
            if (payload == "latest") {
                latestApplied.countDown()
            }
        }

        try {
            assertTrue(queue.submit("old"))
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS))

            assertTrue(queue.submit("middle"))
            assertTrue(queue.submit("latest"))

            releaseFirst.countDown()
            assertTrue(latestApplied.await(2, TimeUnit.SECONDS))

            assertEquals("latest", persisted.get())
            assertEquals(listOf("old", "latest"), writes.toList())
        } finally {
            queue.close()
            scope.cancel()
        }
    }

    @Test
    fun resolveAccordsEditTargetTrack_blocksSaveAndDeleteWhenTrackChangesDuringEdition() {
        val lockedTrack = "track://A"
        val currentTrack = "track://B"

        val saveTarget = resolveAccordsEditTargetTrack(
            lockedTrackUri = lockedTrack,
            currentTrackUri = currentTrack
        )
        val deleteTarget = resolveAccordsEditTargetTrack(
            lockedTrackUri = lockedTrack,
            currentTrackUri = currentTrack
        )

        assertFalse(saveTarget == currentTrack)
        assertFalse(deleteTarget == currentTrack)
        assertNull(saveTarget)
        assertNull(deleteTarget)
    }

    @Test
    fun runAccordsSaveIo_failure_surfacesFeedbackAndKeepsPreviousUiTruth() {
        val previous = AccordsUiTruth(
            lines = listOf(LrcLine(1000L, "Am")),
            hasSource = true
        )
        val requested = listOf(LrcLine(2000L, "G"))

        val io = runAccordsSaveIo(
            writeAccords = { null },
            ensureLyricsTwin = { AccordsEnsureResult.CREATED }
        )
        val resolved = resolveAccordsUiTruthAfterSave(
            previous = previous,
            requestedLines = requested,
            io = io
        )

        assertFalse(io.success)
        assertEquals("writeAccords", io.stage)
        assertEquals(previous, resolved)
        assertNotNull(buildAccordsIoFailureFeedback("sauvegarde", io))
        assertTrue(buildAccordsIoFailureLog("save", "track://A", io).contains("ACCORDS_IO_FAILURE"))
    }

    @Test
    fun runAccordsSaveIo_failure_onEnsureLyricsTwin_surfacesFeedbackAndKeepsPreviousUiTruth() {
        val previous = AccordsUiTruth(
            lines = listOf(LrcLine(1_000L, "Am")),
            hasSource = true
        )
        val requested = listOf(LrcLine(2_000L, "G"))
        var ensureCalled = false

        val io = runAccordsSaveIo(
            writeAccords = { "song.lrc" },
            ensureLyricsTwin = {
                ensureCalled = true
                AccordsEnsureResult.FAILED
            }
        )
        val resolved = resolveAccordsUiTruthAfterSave(
            previous = previous,
            requestedLines = requested,
            io = io
        )

        assertTrue(ensureCalled)
        assertFalse(io.success)
        assertEquals("ensureLyricsTwin:FAILED", io.stage)
        assertEquals(previous, resolved)
        assertNotNull(buildAccordsIoFailureFeedback("sauvegarde", io))
    }

    @Test
    fun runAccordsSaveIo_alreadyExists_doesNotFailAndDoesNotRollbackUiTruth() {
        val previous = AccordsUiTruth(
            lines = emptyList(),
            hasSource = false
        )
        val requested = listOf(
            LrcLine(1_000L, "Am"),
            LrcLine(2_000L, "F")
        )

        val io = runAccordsSaveIo(
            writeAccords = { "La Bamba-2df611b11e.lrc" },
            ensureLyricsTwin = { AccordsEnsureResult.ALREADY_EXISTS }
        )
        val resolved = resolveAccordsUiTruthAfterSave(
            previous = previous,
            requestedLines = requested,
            io = io
        )

        assertTrue(io.success)
        assertEquals("ok", io.stage)
        assertEquals(requested, resolved.lines)
        assertTrue(resolved.hasSource)
    }

    @Test
    fun runAccordsSaveIo_created_onVirginTrack_doesNotRollbackUi() {
        val previous = AccordsUiTruth(
            lines = emptyList(),
            hasSource = false
        )
        val requested = listOf(LrcLine(3_000L, "G"))

        val io = runAccordsSaveIo(
            writeAccords = { "La Bamba-2df611b11e.lrc" },
            ensureLyricsTwin = { AccordsEnsureResult.CREATED }
        )
        val resolved = resolveAccordsUiTruthAfterSave(
            previous = previous,
            requestedLines = requested,
            io = io
        )

        assertTrue(io.success)
        assertEquals(requested, resolved.lines)
        assertTrue(resolved.hasSource)
    }

    @Test
    fun resolveAccordsLrcFileName_onVirginTrack_usesHashedFallback() {
        val resolved = resolveAccordsLrcFileName(
            preferredLrcFileName = null,
            originLrcFileName = null,
            hashedFallbackFileName = "La Bamba-2df611b11e.lrc"
        )

        assertEquals("La Bamba-2df611b11e.lrc", resolved.fileName)
        assertEquals("hashedFallback", resolved.source)
    }

    @Test
    fun resolveAccordsLrcFileName_prefersPreferredThenOrigin_nonRegression() {
        val preferred = resolveAccordsLrcFileName(
            preferredLrcFileName = "preferred.lrc",
            originLrcFileName = "origin.lrc",
            hashedFallbackFileName = "hash.lrc"
        )
        assertEquals("preferred.lrc", preferred.fileName)
        assertEquals("preferred", preferred.source)

        val origin = resolveAccordsLrcFileName(
            preferredLrcFileName = null,
            originLrcFileName = "origin.lrc",
            hashedFallbackFileName = "hash.lrc"
        )
        assertEquals("origin.lrc", origin.fileName)
        assertEquals("origin", origin.source)
    }

    @Test
    fun hashedFileNameForTrack_producesHistoricalHashedPattern() {
        val fileName = LrcStorage.hashedFileNameForTrack("content://media/external/audio/media/42")
        assertTrue(fileName.endsWith(".lrc"))
        assertTrue(fileName.contains("-"))
        assertTrue(".*-[0-9a-f]{10}\\.lrc".toRegex().matches(fileName))
    }

    @Test
    fun runAccordsDeleteIo_failure_surfacesFeedbackAndKeepsPreviousUiTruth() {
        val previous = AccordsUiTruth(
            lines = listOf(LrcLine(1000L, "Am")),
            hasSource = true
        )

        val io = runAccordsDeleteIo(
            deleteAccords = { false }
        )
        val resolved = resolveAccordsUiTruthAfterDelete(
            previous = previous,
            io = io
        )

        assertFalse(io.success)
        assertEquals("deleteAccords", io.stage)
        assertEquals(previous, resolved)
        assertNotNull(buildAccordsIoFailureFeedback("suppression", io))
        assertTrue(buildAccordsIoFailureLog("delete", "track://A", io).contains("ACCORDS_IO_FAILURE"))
    }

    @Test
    fun runAccordsIo_success_nonRegression_updatesUiTruthAndNoFailureFeedback() {
        val previous = AccordsUiTruth(
            lines = listOf(LrcLine(1000L, "Am")),
            hasSource = true
        )
        val requested = listOf(LrcLine(2000L, "G"))

        val saveIo = runAccordsSaveIo(
            writeAccords = { "song.lrc" },
            ensureLyricsTwin = { AccordsEnsureResult.CREATED }
        )
        val afterSave = resolveAccordsUiTruthAfterSave(previous, requested, saveIo)
        assertTrue(saveIo.success)
        assertEquals(requested, afterSave.lines)
        assertTrue(afterSave.hasSource)
        assertNull(buildAccordsIoFailureFeedback("sauvegarde", saveIo))

        val deleteIo = runAccordsDeleteIo(deleteAccords = { true })
        val afterDelete = resolveAccordsUiTruthAfterDelete(afterSave, deleteIo)
        assertTrue(deleteIo.success)
        assertEquals(emptyList<LrcLine>(), afterDelete.lines)
        assertFalse(afterDelete.hasSource)
        assertNull(buildAccordsIoFailureFeedback("suppression", deleteIo))
    }

    @Test
    fun latestAccordsWriteQueue_submit_returnsFalseWhenQueueAlreadyClosed() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val queue = LatestAccordsWriteQueue<String>(
            scope = scope
        ) { }

        try {
            queue.close()
            assertFalse(queue.submit("late-write"))
        } finally {
            scope.cancel()
        }
    }
}
