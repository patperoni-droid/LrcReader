package com.patrick.lrcreader.core.sync

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.text.Normalizer

class SmpSyncHashing {

    enum class FileHashMode {
        BYTES,
        NORMALIZED_TEXT,
        SYNC_LYRICS_TEXT,
        CANONICAL_JSON,
        SYNC_SETTINGS_JSON,
        SYNC_ARRANGEMENT_JSON
    }

    fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    fun hashNormalizedText(text: String): String {
        return sha256(normalizeText(text).toByteArray(Charsets.UTF_8))
    }

    fun hashSyncLyricsText(text: String): String {
        return sha256(normalizeLyricsText(text).toByteArray(Charsets.UTF_8))
    }

    fun hashCanonicalJsonText(rawJson: String): String {
        val canonical = canonicalJsonOrNull(rawJson)
        // TODO SMP Sync: invalid JSON currently falls back to normalized text hashing.
        return hashNormalizedText(canonical ?: rawJson)
    }

    fun hashFileOrNull(file: File?, mode: FileHashMode): String? {
        if (file == null || !file.isFile) return null
        return when (mode) {
            FileHashMode.BYTES -> hashFileBytesStreaming(file)
            FileHashMode.NORMALIZED_TEXT -> hashNormalizedText(file.readText(Charsets.UTF_8))
            FileHashMode.SYNC_LYRICS_TEXT -> hashSyncLyricsText(file.readText(Charsets.UTF_8))
            FileHashMode.CANONICAL_JSON -> hashCanonicalJsonText(file.readText(Charsets.UTF_8))
            FileHashMode.SYNC_SETTINGS_JSON -> hashSyncSettingsJsonTextOrNull(file.readText(Charsets.UTF_8))
            FileHashMode.SYNC_ARRANGEMENT_JSON -> hashSyncArrangementJsonTextOrNull(file.readText(Charsets.UTF_8))
        }
    }

    fun hashCanonicalJson(json: JSONObject): String {
        return hashNormalizedText(canonicalJsonValue(json))
    }

    fun hashSyncSettingsJsonText(rawJson: String): String {
        val canonical = syncSettingsCanonicalTextOrNull(rawJson)
        // TODO SMP Sync: invalid JSON currently falls back to normalized text hashing.
        return hashNormalizedText(canonical ?: rawJson)
    }

    fun hashSyncArrangementJsonText(rawJson: String): String {
        val canonical = syncArrangementCanonicalTextOrNull(rawJson)
        // TODO SMP Sync: invalid JSON currently falls back to normalized text hashing.
        return hashNormalizedText(canonical ?: rawJson)
    }

    fun hashSyncSettingsJsonTextOrNull(rawJson: String): String? {
        return syncSettingsCanonicalTextOrNull(rawJson)?.let(::hashNormalizedText)
    }

    fun hashSyncArrangementJsonTextOrNull(rawJson: String): String? {
        return syncArrangementCanonicalTextOrNull(rawJson)?.let(::hashNormalizedText)
    }

    fun syncSettingsCanonicalTextOrNull(rawJson: String): String? {
        return syncSettingsJsonOrNull(rawJson)?.let(::canonicalJsonValue)
    }

    fun syncArrangementCanonicalTextOrNull(rawJson: String): String? {
        return syncArrangementJsonOrNull(rawJson)?.let(::canonicalJsonValue)
    }

    fun canonicalJsonOrNull(rawJson: String): String? {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty()) return null
        return runCatching {
            when {
                trimmed.startsWith("{") -> canonicalJsonValue(JSONObject(trimmed))
                trimmed.startsWith("[") -> canonicalJsonValue(JSONArray(trimmed))
                else -> null
            }
        }.getOrNull()
    }

    fun normalizeText(text: String): String {
        return text
            .replace("\r\n", "\n")
            .replace('\r', '\n')
    }

    fun normalizeLyricsText(text: String): String {
        val normalizedLines = Normalizer.normalize(stripUtf8Bom(text), Normalizer.Form.NFC)
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .split('\n')
            .map { line -> line.trimEnd(' ', '\t') }
            .dropLastWhile { line -> line.isEmpty() }
        return normalizedLines.joinToString("\n")
    }

    private fun hashFileBytesStreaming(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(FILE_HASH_BUFFER_SIZE)
        file.inputStream().use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) {
                    digest.update(buffer, 0, read)
                }
            }
        }
        return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private fun syncSettingsJsonOrNull(rawJson: String): JSONObject? {
        val root = runCatching { JSONObject(rawJson.trim()) }.getOrNull() ?: return null
        val out = JSONObject()
        root.optJSONObject("playback")?.let { out.put("playback", it) }
        root.optJSONObject("lyricsLineColors")?.let { out.put("lyricsLineColors", it) }
        return out.takeIf { it.length() > 0 }
    }

    private fun syncArrangementJsonOrNull(rawJson: String): JSONObject? {
        val root = runCatching { JSONObject(rawJson.trim()) }.getOrNull() ?: return null
        val out = JSONObject()
        out.copyIfPresent(root, "name")
        root.optJSONArray("segments")?.let { segments ->
            out.put("segments", syncArrangementSegments(segments))
        }
        root.optJSONArray("structureSegmentIds")?.let { structureIds ->
            out.put("structureSegmentIds", syncArrangementStructureIds(structureIds))
        }
        root.optJSONArray("entries")?.let { entries ->
            out.put("entries", syncArrangementEntries(entries))
        }
        return out.takeIf { it.length() > 0 }
    }

    private fun syncArrangementSegments(segments: JSONArray): JSONArray {
        return JSONArray().apply {
            for (index in 0 until segments.length()) {
                val segment = segments.optJSONObject(index) ?: continue
                val id = segment.optString("id").trim().takeIf { it.isNotEmpty() } ?: continue
                val name = segment.optString("name").trim().takeIf { it.isNotEmpty() } ?: continue
                val startMs = segment.optLong("startMs", -1L)
                val endMs = segment.optLong("endMs", -1L)
                if (startMs < 0L || endMs <= startMs) continue
                put(
                    JSONObject()
                        .put("id", id)
                        .put("name", name)
                        .put("startMs", startMs)
                        .put("endMs", endMs)
                )
            }
        }
    }

    private fun syncArrangementStructureIds(structureIds: JSONArray): JSONArray {
        return JSONArray().apply {
            for (index in 0 until structureIds.length()) {
                structureIds.optString(index).trim().takeIf { it.isNotEmpty() }?.let(::put)
            }
        }
    }

    private fun syncArrangementEntries(entries: JSONArray): JSONArray {
        return JSONArray().apply {
            for (index in 0 until entries.length()) {
                val entry = entries.optJSONObject(index) ?: continue
                val entryId = entry.optString("entryId").trim().takeIf { it.isNotEmpty() } ?: continue
                val name = entry.optString("name").trim().takeIf { it.isNotEmpty() } ?: continue
                val startMs = entry.optLong("startMs", -1L)
                val endMs = entry.optLong("endMs", -1L)
                if (startMs < 0L || endMs <= startMs) continue
                put(
                    JSONObject()
                        .put("entryId", entryId)
                        .put("name", name)
                        .put("startMs", startMs)
                        .put("endMs", endMs)
                        .put("repeatCount", entry.optInt("repeatCount", 1).coerceAtLeast(1))
                        .put("muted", entry.optBoolean("muted", false))
                        .apply {
                            if (entry.has("color") && !entry.isNull("color")) {
                                entry.optString("color").trim().takeIf { it.isNotEmpty() }?.let { color ->
                                    put("color", color)
                                }
                            }
                        }
                )
            }
        }
    }

    private fun JSONObject.copyIfPresent(source: JSONObject, key: String) {
        if (source.has(key) && !source.isNull(key)) {
            put(key, source.opt(key))
        }
    }

    private fun canonicalJsonValue(value: Any?): String {
        return when (value) {
            null, JSONObject.NULL -> "null"
            is JSONObject -> {
                val keys = value.keys().asSequence().toList().sorted()
                keys.joinToString(prefix = "{", postfix = "}") { key ->
                    "${JSONObject.quote(key)}:${canonicalJsonValue(value.opt(key))}"
                }
            }
            is JSONArray -> {
                (0 until value.length()).joinToString(prefix = "[", postfix = "]") { index ->
                    canonicalJsonValue(value.opt(index))
                }
            }
            is String -> JSONObject.quote(value)
            is Number, is Boolean -> value.toString()
            else -> JSONObject.quote(value.toString())
        }
    }

    private fun stripUtf8Bom(text: String): String {
        return if (text.startsWith('\uFEFF')) text.drop(1) else text
    }

    private companion object {
        const val FILE_HASH_BUFFER_SIZE = 128 * 1024
    }
}
