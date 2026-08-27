package com.patrick.lrcreader.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsEditorDisposePersistenceTest {

    @Test
    fun saveConfirmationBecomesVisibleThenDismissesAutomatically() = runBlocking {
        val visibilityChanges = mutableListOf<Boolean>()
        var waitedDurationMs = 0L

        showTemporaryLyricsSaveConfirmation(
            wait = { durationMs -> waitedDurationMs = durationMs },
            onVisibilityChange = visibilityChanges::add
        )

        assertEquals(listOf(true, false), visibilityChanges)
        assertEquals(1_800L, waitedDurationMs)
    }

    @Test
    fun changedLyricsAreFlushedWhenEditorLeavesComposition() {
        assertTrue(
            shouldFlushLyricsDraftOnEditorDispose(
                currentTrackUri = "file:///runtime/tracks/song/audio.mp3",
                showChordPalette = false,
                lastPersistedSignature = "old",
                currentSignature = "new"
            )
        )
    }

    @Test
    fun unchangedLyricsAndChordEditorDoNotTriggerExtraFlush() {
        assertFalse(
            shouldFlushLyricsDraftOnEditorDispose(
                currentTrackUri = "file:///runtime/tracks/song/audio.mp3",
                showChordPalette = false,
                lastPersistedSignature = "same",
                currentSignature = "same"
            )
        )
        assertFalse(
            shouldFlushLyricsDraftOnEditorDispose(
                currentTrackUri = "file:///runtime/tracks/song/audio.mp3",
                showChordPalette = true,
                lastPersistedSignature = "old",
                currentSignature = "new"
            )
        )
    }

    @Test
    fun libraryUpdateWaitsForTrackedEditorFlush() = runBlocking {
        val flush = CompletableDeferred<Boolean>()
        LyricsEditorPersistenceBarrier.track(flush)
        var updateMayScanRuntime = false
        val updateWaiter = launch {
            updateMayScanRuntime = LyricsEditorPersistenceBarrier.awaitPending()
        }

        yield()
        assertFalse(updateMayScanRuntime)
        flush.complete(true)
        updateWaiter.join()

        assertTrue(updateMayScanRuntime)
    }

    @Test
    fun libraryUpdateForcesActiveEditorDraftBeforeScanningRuntime() = runBlocking {
        var runtimeLyrics = "old"
        val activeHandle = LyricsEditorPersistenceBarrier.registerActive {
            runtimeLyrics = "latest editor draft"
            true
        }

        try {
            assertTrue(LyricsEditorPersistenceBarrier.awaitPending())
            assertTrue(runtimeLyrics == "latest editor draft")
        } finally {
            LyricsEditorPersistenceBarrier.unregisterActive(activeHandle)
        }
    }

    @Test
    fun failedEditorFlushPreventsLibraryUpdateScan() = runBlocking {
        val flush = CompletableDeferred<Boolean>()
        LyricsEditorPersistenceBarrier.track(flush)
        flush.complete(false)

        assertFalse(LyricsEditorPersistenceBarrier.awaitPending())
    }

    @Test
    fun manualValidationConfirmsOnlyAfterPersistenceSucceeds() = runBlocking {
        val persistence = CompletableDeferred<Boolean>()
        var confirmationShown = false
        val save = launch {
            persistManualLyricsSave(
                persist = { persistence.await() },
                onSuccess = { confirmationShown = true }
            )
        }

        yield()
        assertFalse(confirmationShown)
        persistence.complete(true)
        save.join()

        assertTrue(confirmationShown)
    }

    @Test
    fun failedManualValidationNeverShowsSuccessConfirmation() = runBlocking {
        var confirmationShown = false

        assertFalse(
            persistManualLyricsSave(
                persist = { false },
                onSuccess = { confirmationShown = true }
            )
        )
        assertFalse(confirmationShown)
    }
}
