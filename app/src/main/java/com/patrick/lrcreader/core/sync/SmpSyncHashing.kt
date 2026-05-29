package com.patrick.lrcreader.core.sync

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

class SmpSyncHashing {

    enum class FileHashMode {
        BYTES,
        NORMALIZED_TEXT,
        CANONICAL_JSON
    }

    fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    fun hashNormalizedText(text: String): String {
        return sha256(normalizeText(text).toByteArray(Charsets.UTF_8))
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
            FileHashMode.CANONICAL_JSON -> hashCanonicalJsonText(file.readText(Charsets.UTF_8))
        }
    }

    fun hashCanonicalJson(json: JSONObject): String {
        return hashNormalizedText(canonicalJsonValue(json))
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

    private companion object {
        const val FILE_HASH_BUFFER_SIZE = 128 * 1024
    }
}
