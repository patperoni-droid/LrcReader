package com.patrick.lrcreader.core.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioEngineCodecGuardTest {

    @Test
    fun mediaTekHardware_usesSynchronousCodecQueueing() {
        assertTrue(
            requiresSynchronousMediaCodecQueueing(
                manufacturer = "LENOVO",
                hardware = "mt8755",
                board = "mt6835"
            )
        )
    }

    @Test
    fun unrelatedHardware_keepsDefaultCodecQueueing() {
        assertFalse(
            requiresSynchronousMediaCodecQueueing(
                manufacturer = "Google",
                hardware = "tensor",
                board = "komodo"
            )
        )
    }
}
