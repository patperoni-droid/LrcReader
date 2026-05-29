package com.patrick.lrcreader.core.locallink

import com.patrick.lrcreader.core.sync.SmpSyncManifest
import com.patrick.lrcreader.core.sync.SmpSyncSongEntry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalLinkMessageTest {

    @Test
    fun hello_roundTrip_preservesFields() {
        val message = HelloMessage(
            sessionId = "session-123",
            token = "short-token",
            deviceName = "SMP Pro"
        )

        val restored = LocalLinkMessage.fromJsonString(message.toJsonString())

        assertEquals(message, restored)
    }

    @Test
    fun lyricsPacket_roundTrip_preservesParsedLines() {
        val message = LyricsPacketMessage(
            songId = "song_123",
            title = "Song title",
            durationMs = 184_000L,
            seq = 42L,
            lines = listOf(
                LyricsLinePayload(timeMs = 1_200L, text = "First line"),
                LyricsLinePayload(timeMs = 4_500L, text = "Second line")
            )
        )

        val restored = LocalLinkMessage.fromJsonString(message.toJsonString())

        assertEquals(message, restored)
        restored as LyricsPacketMessage
        assertEquals("parsed_lrc", restored.format)
        assertEquals(2, restored.lines.size)
        assertEquals(4_500L, restored.lines[1].timeMs)
        assertEquals("Second line", restored.lines[1].text)
    }

    @Test
    fun clock_roundTrip_preservesLiveClockFields() {
        val message = ClockMessage(
            songId = "song_123",
            timeMs = 45_210L,
            isPlaying = true,
            seq = 43L,
            sentAtMs = 123_456_789L
        )

        val restored = LocalLinkMessage.fromJsonString(message.toJsonString())

        assertEquals(message, restored)
    }

    @Test
    fun syncManifestRequest_roundTrip_preservesFields() {
        val message = SyncManifestRequestMessage(
            requestId = "request-1",
            seq = 12L
        )

        val restored = LocalLinkMessage.fromJsonString(message.toJsonString())

        assertEquals(message, restored)
    }

    @Test
    fun syncManifestPayload_roundTrip_preservesManifest() {
        val manifest = SmpSyncManifest(
            appVersion = "1.0",
            generatedAt = 123L,
            songs = listOf(
                SmpSyncSongEntry(
                    songId = "song-1",
                    title = "Song one",
                    fullSongHash = "song-hash"
                )
            )
        )
        val message = SyncManifestPayloadMessage.fromManifest(
            requestId = "request-1",
            manifest = manifest,
            seq = 13L
        )

        val restored = LocalLinkMessage.fromJsonString(message.toJsonString())

        assertTrue(restored is SyncManifestPayloadMessage)
        restored as SyncManifestPayloadMessage
        assertEquals("request-1", restored.requestId)
        assertEquals(13L, restored.seq)
        assertEquals(manifest, restored.parseManifestOrNull())
    }

    @Test
    fun syncManifestPayload_invalidManifest_returnsNullWithoutThrowing() {
        val message = SyncManifestPayloadMessage(
            requestId = "request-1",
            manifestJson = "{not-json",
            seq = 14L
        )

        assertEquals(null, message.parseManifestOrNull())
    }

    @Test
    fun unknownMessage_returnsUnknownWithoutThrowing() {
        val raw = JSONObject()
            .put("type", "future_message")
            .put("protocol", LocalLinkMessage.PROTOCOL)
            .put("version", LocalLinkMessage.VERSION)
            .put("seq", 99L)
            .toString()

        val restored = LocalLinkMessage.fromJsonString(raw)

        assertTrue(restored is UnknownMessage)
        restored as UnknownMessage
        assertEquals("future_message", restored.rawType)
        assertEquals("unknown_type", restored.reason)
    }

    @Test
    fun unsupportedVersion_returnsUnknownWithoutThrowing() {
        val raw = HelloMessage(
            sessionId = "session-123",
            token = "short-token",
            deviceName = "SMP Pro"
        ).toJson()
            .put("version", LocalLinkMessage.VERSION + 1)
            .toString()

        val restored = LocalLinkMessage.fromJsonString(raw)

        assertTrue(restored is UnknownMessage)
        restored as UnknownMessage
        assertEquals("hello", restored.rawType)
        assertEquals("unsupported_version", restored.reason)
    }

    @Test
    fun unsupportedProtocol_returnsUnknownWithoutThrowing() {
        val raw = PingMessage(seq = 44L).toJson()
            .put("protocol", "other_protocol")
            .toString()

        val restored = LocalLinkMessage.fromJsonString(raw)

        assertTrue(restored is UnknownMessage)
        restored as UnknownMessage
        assertEquals("ping", restored.rawType)
        assertEquals("unsupported_protocol", restored.reason)
    }
}
