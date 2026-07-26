package com.patrick.lrcreader.ui.library

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryImportRoutingTest {

    @Test
    fun audioChoiceUsesOnlyExistingAudioPipeline() {
        var audioCalls = 0
        var smpCalls = 0

        dispatchLibraryImport(
            kind = LibraryImportKind.Audio,
            onImportAudio = { audioCalls += 1 },
            onImportSmp = { smpCalls += 1 }
        )

        assertEquals(1, audioCalls)
        assertEquals(0, smpCalls)
    }

    @Test
    fun smpChoiceUsesOnlyExistingSmpPipeline() {
        var audioCalls = 0
        var smpCalls = 0

        dispatchLibraryImport(
            kind = LibraryImportKind.Smp,
            onImportAudio = { audioCalls += 1 },
            onImportSmp = { smpCalls += 1 }
        )

        assertEquals(0, audioCalls)
        assertEquals(1, smpCalls)
    }
}
