package com.patrick.lrcreader.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackRouterTest {

    @Test
    fun resolve_groupHeader_returnsUnknown() {
        val header = buildGroupHeader("Rock")

        val target = PlaybackRouter.resolve(header, "PL")

        assertTrue(target is PlaybackRouter.Target.Unknown)
    }

    @Test
    fun resolve_prompter_keepsPrompterBehavior() {
        val target = PlaybackRouter.resolve("prompter://42", "PL")

        assertTrue(target is PlaybackRouter.Target.Prompter)
        assertEquals("42", (target as PlaybackRouter.Target.Prompter).id)
    }

    @Test
    fun resolve_audio_returnsAudioTarget() {
        val target = PlaybackRouter.resolve("content://media/external/audio/123", "PL")

        assertTrue(target is PlaybackRouter.Target.Audio)
        target as PlaybackRouter.Target.Audio
        assertEquals("content://media/external/audio/123", target.uri)
        assertEquals("PL", target.playlist)
    }
}
