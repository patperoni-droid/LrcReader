package com.patrick.lrcreader.core.locallink

import com.patrick.lrcreader.core.sync.SmpSyncManifest
import org.json.JSONArray
import org.json.JSONObject

sealed interface LocalLinkMessage {
    val type: String
    val protocol: String
    val version: Int

    fun toJson(): JSONObject

    fun toJsonString(): String = toJson().toString()

    companion object {
        const val PROTOCOL = "local_link_live_lyrics"
        const val VERSION = 1

        const val TYPE_HELLO = "hello"
        const val TYPE_LYRICS_PACKET = "lyrics_packet"
        const val TYPE_CLOCK = "clock"
        const val TYPE_PING = "ping"
        const val TYPE_RECEIVER_STATUS = "receiver_status"
        const val TYPE_SYNC_MANIFEST_REQUEST = "sync_manifest_request"
        const val TYPE_SYNC_MANIFEST_PAYLOAD = "sync_manifest_payload"

        fun fromJsonString(rawJson: String): LocalLinkMessage {
            return runCatching {
                fromJson(JSONObject(rawJson))
            }.getOrElse { error ->
                UnknownMessage(
                    rawType = null,
                    rawJson = rawJson,
                    reason = "invalid_json:${error::class.simpleName}"
                )
            }
        }

        fun fromJson(json: JSONObject): LocalLinkMessage {
            val type = json.optStringOrNull("type")
            val protocol = json.optStringOrNull("protocol")
            val version = json.optIntOrNull("version")

            if (protocol != PROTOCOL) {
                return UnknownMessage(
                    rawType = type,
                    rawJson = json.toString(),
                    reason = "unsupported_protocol"
                )
            }
            if (version != VERSION) {
                return UnknownMessage(
                    rawType = type,
                    rawJson = json.toString(),
                    reason = "unsupported_version"
                )
            }

            return when (type) {
                TYPE_HELLO -> HelloMessage.fromJson(json)
                TYPE_LYRICS_PACKET -> LyricsPacketMessage.fromJson(json)
                TYPE_CLOCK -> ClockMessage.fromJson(json)
                TYPE_PING -> PingMessage.fromJson(json)
                TYPE_RECEIVER_STATUS -> ReceiverStatusMessage.fromJson(json)
                TYPE_SYNC_MANIFEST_REQUEST -> SyncManifestRequestMessage.fromJson(json)
                TYPE_SYNC_MANIFEST_PAYLOAD -> SyncManifestPayloadMessage.fromJson(json)
                else -> UnknownMessage(
                    rawType = type,
                    rawJson = json.toString(),
                    reason = "unknown_type"
                )
            }
        }
    }
}

data class SyncManifestRequestMessage(
    val requestId: String,
    val seq: Long,
    override val protocol: String = LocalLinkMessage.PROTOCOL,
    override val version: Int = LocalLinkMessage.VERSION
) : LocalLinkMessage {
    override val type: String = LocalLinkMessage.TYPE_SYNC_MANIFEST_REQUEST

    override fun toJson(): JSONObject = baseJson(type, protocol, version).apply {
        put("requestId", requestId)
        put("seq", seq.coerceAtLeast(0L))
    }

    companion object {
        internal fun fromJson(json: JSONObject): SyncManifestRequestMessage {
            return SyncManifestRequestMessage(
                requestId = json.optString("requestId"),
                seq = json.optLong("seq").coerceAtLeast(0L)
            )
        }
    }
}

data class SyncManifestPayloadMessage(
    val requestId: String,
    val manifestJson: String,
    val seq: Long,
    override val protocol: String = LocalLinkMessage.PROTOCOL,
    override val version: Int = LocalLinkMessage.VERSION
) : LocalLinkMessage {
    override val type: String = LocalLinkMessage.TYPE_SYNC_MANIFEST_PAYLOAD

    override fun toJson(): JSONObject = baseJson(type, protocol, version).apply {
        put("requestId", requestId)
        put("manifestJson", manifestJson)
        put("seq", seq.coerceAtLeast(0L))
    }

    fun parseManifestOrNull(): SmpSyncManifest? {
        if (manifestJson.length > MAX_MANIFEST_JSON_CHARS) return null
        return SmpSyncManifest.fromJsonOrNull(manifestJson)
    }

    companion object {
        private const val MAX_MANIFEST_JSON_CHARS = 2_000_000

        fun fromManifest(
            requestId: String,
            manifest: SmpSyncManifest,
            seq: Long
        ): SyncManifestPayloadMessage {
            return SyncManifestPayloadMessage(
                requestId = requestId,
                manifestJson = manifest.toJsonString(indentSpaces = 0),
                seq = seq
            )
        }

        internal fun fromJson(json: JSONObject): SyncManifestPayloadMessage {
            val manifestJson = json.optString("manifestJson")
            if (manifestJson.length > MAX_MANIFEST_JSON_CHARS) {
                error("sync_manifest_too_large")
            }
            return SyncManifestPayloadMessage(
                requestId = json.optString("requestId"),
                manifestJson = manifestJson,
                seq = json.optLong("seq").coerceAtLeast(0L)
            )
        }
    }
}

data class HelloMessage(
    val sessionId: String,
    val token: String,
    val deviceName: String,
    override val protocol: String = LocalLinkMessage.PROTOCOL,
    override val version: Int = LocalLinkMessage.VERSION
) : LocalLinkMessage {
    override val type: String = LocalLinkMessage.TYPE_HELLO

    override fun toJson(): JSONObject = baseJson(type, protocol, version).apply {
        put("sessionId", sessionId)
        put("token", token)
        put("deviceName", deviceName)
    }

    companion object {
        internal fun fromJson(json: JSONObject): HelloMessage {
            return HelloMessage(
                sessionId = json.optString("sessionId"),
                token = json.optString("token"),
                deviceName = json.optString("deviceName")
            )
        }
    }
}

data class LyricsLinePayload(
    val timeMs: Long,
    val text: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("timeMs", timeMs.coerceAtLeast(0L))
        put("text", text)
    }

    companion object {
        internal fun fromJson(json: JSONObject): LyricsLinePayload {
            return LyricsLinePayload(
                timeMs = json.optLong("timeMs").coerceAtLeast(0L),
                text = json.optString("text")
            )
        }
    }
}

data class LyricsPacketMessage(
    val songId: String,
    val title: String,
    val lines: List<LyricsLinePayload>,
    val durationMs: Long? = null,
    val seq: Long,
    override val protocol: String = LocalLinkMessage.PROTOCOL,
    override val version: Int = LocalLinkMessage.VERSION
) : LocalLinkMessage {
    override val type: String = LocalLinkMessage.TYPE_LYRICS_PACKET
    val format: String = FORMAT_PARSED_LRC

    override fun toJson(): JSONObject = baseJson(type, protocol, version).apply {
        put("songId", songId)
        put("title", title)
        put("format", format)
        put(
            "lines",
            JSONArray().apply {
                lines.forEach { line -> put(line.toJson()) }
            }
        )
        if (durationMs != null) {
            put("durationMs", durationMs.coerceAtLeast(0L))
        }
        put("seq", seq.coerceAtLeast(0L))
    }

    companion object {
        const val FORMAT_PARSED_LRC = "parsed_lrc"

        internal fun fromJson(json: JSONObject): LyricsPacketMessage {
            return LyricsPacketMessage(
                songId = json.optString("songId"),
                title = json.optString("title"),
                lines = json.optJSONArray("lines").toLyricsLines(),
                durationMs = json.optLongOrNull("durationMs")?.coerceAtLeast(0L),
                seq = json.optLong("seq").coerceAtLeast(0L)
            )
        }
    }
}

data class ClockMessage(
    val songId: String,
    val timeMs: Long,
    val isPlaying: Boolean,
    val seq: Long,
    val sentAtMs: Long,
    override val protocol: String = LocalLinkMessage.PROTOCOL,
    override val version: Int = LocalLinkMessage.VERSION
) : LocalLinkMessage {
    override val type: String = LocalLinkMessage.TYPE_CLOCK

    override fun toJson(): JSONObject = baseJson(type, protocol, version).apply {
        put("songId", songId)
        put("timeMs", timeMs.coerceAtLeast(0L))
        put("isPlaying", isPlaying)
        put("seq", seq.coerceAtLeast(0L))
        put("sentAtMs", sentAtMs.coerceAtLeast(0L))
    }

    companion object {
        internal fun fromJson(json: JSONObject): ClockMessage {
            return ClockMessage(
                songId = json.optString("songId"),
                timeMs = json.optLong("timeMs").coerceAtLeast(0L),
                isPlaying = json.optBoolean("isPlaying"),
                seq = json.optLong("seq").coerceAtLeast(0L),
                sentAtMs = json.optLong("sentAtMs").coerceAtLeast(0L)
            )
        }
    }
}

data class PingMessage(
    val seq: Long,
    override val protocol: String = LocalLinkMessage.PROTOCOL,
    override val version: Int = LocalLinkMessage.VERSION
) : LocalLinkMessage {
    override val type: String = LocalLinkMessage.TYPE_PING

    override fun toJson(): JSONObject = baseJson(type, protocol, version).apply {
        put("seq", seq.coerceAtLeast(0L))
    }

    companion object {
        internal fun fromJson(json: JSONObject): PingMessage {
            return PingMessage(
                seq = json.optLong("seq").coerceAtLeast(0L)
            )
        }
    }
}

data class ReceiverStatusMessage(
    val state: String,
    val activeSongId: String?,
    val seq: Long,
    override val protocol: String = LocalLinkMessage.PROTOCOL,
    override val version: Int = LocalLinkMessage.VERSION
) : LocalLinkMessage {
    override val type: String = LocalLinkMessage.TYPE_RECEIVER_STATUS

    override fun toJson(): JSONObject = baseJson(type, protocol, version).apply {
        put("state", state)
        activeSongId?.let { put("activeSongId", it) }
        put("seq", seq.coerceAtLeast(0L))
    }

    companion object {
        const val STATE_READY = "ready"
        const val STATE_OK = "ok"
        const val STATE_MISSING_PACKET = "missing_packet"
        const val STATE_DESYNCED = "desynced"
        const val STATE_DISCONNECTED = "disconnected"

        internal fun fromJson(json: JSONObject): ReceiverStatusMessage {
            return ReceiverStatusMessage(
                state = json.optString("state"),
                activeSongId = json.optStringOrNull("activeSongId"),
                seq = json.optLong("seq").coerceAtLeast(0L)
            )
        }
    }
}

data class UnknownMessage(
    val rawType: String?,
    val rawJson: String?,
    val reason: String,
    override val protocol: String = LocalLinkMessage.PROTOCOL,
    override val version: Int = LocalLinkMessage.VERSION
) : LocalLinkMessage {
    override val type: String = "unknown"

    override fun toJson(): JSONObject = baseJson(type, protocol, version).apply {
        rawType?.let { put("rawType", it) }
        rawJson?.let { put("rawJson", it) }
        put("reason", reason)
    }
}

private fun baseJson(type: String, protocol: String, version: Int): JSONObject {
    return JSONObject().apply {
        put("type", type)
        put("protocol", protocol)
        put("version", version)
    }
}

private fun JSONArray?.toLyricsLines(): List<LyricsLinePayload> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            optJSONObject(index)?.let { item ->
                add(LyricsLinePayload.fromJson(item))
            }
        }
    }
}

private fun JSONObject.optStringOrNull(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return optString(key).takeIf { it.isNotBlank() }
}

private fun JSONObject.optIntOrNull(key: String): Int? {
    if (!has(key) || isNull(key)) return null
    return runCatching { getInt(key) }.getOrNull()
}

private fun JSONObject.optLongOrNull(key: String): Long? {
    if (!has(key) || isNull(key)) return null
    return runCatching { getLong(key) }.getOrNull()
}
