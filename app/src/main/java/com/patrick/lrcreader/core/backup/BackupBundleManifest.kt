package com.patrick.lrcreader.core.backup

const val BACKUP_BUNDLE_FORMAT = "spl_backup_bundle"
const val BACKUP_BUNDLE_VERSION = 1
const val BACKUP_BUNDLE_EXTENSION = ".splbackup"
const val BACKUP_BUNDLE_MANIFEST_ENTRY = "manifest.json"
const val BACKUP_BUNDLE_DEFAULT_STATE_ENTRY = "state.json"
const val BACKUP_BUNDLE_DEFAULT_SMP_DIR = "smp"

data class BackupBundleSongEntry(
    val songId: String,
    val entry: String
)

data class BackupBundleManifest(
    val format: String = BACKUP_BUNDLE_FORMAT,
    val version: Int = BACKUP_BUNDLE_VERSION,
    val stateEntry: String = BACKUP_BUNDLE_DEFAULT_STATE_ENTRY,
    val smpDir: String = BACKUP_BUNDLE_DEFAULT_SMP_DIR,
    val songs: List<BackupBundleSongEntry> = emptyList()
) {
    fun toJsonString(indentSpaces: Int = 2): String {
        val indent = " ".repeat(indentSpaces.coerceAtLeast(0))
        val childIndent = indent + indent
        val songLines = songs.joinToString(",\n") { song ->
            buildString {
                append(childIndent)
                append("{\n")
                append(childIndent)
                append(indent)
                append("\"songId\": \"")
                append(song.songId.escapeJson())
                append("\",\n")
                append(childIndent)
                append(indent)
                append("\"entry\": \"")
                append(song.entry.escapeJson())
                append("\"\n")
                append(childIndent)
                append("}")
            }
        }

        return buildString {
            append("{\n")
            append(indent)
            append("\"format\": \"")
            append(format.escapeJson())
            append("\",\n")
            append(indent)
            append("\"version\": ")
            append(version)
            append(",\n")
            append(indent)
            append("\"stateEntry\": \"")
            append(stateEntry.escapeJson())
            append("\",\n")
            append(indent)
            append("\"smpDir\": \"")
            append(smpDir.escapeJson())
            append("\",\n")
            append(indent)
            append("\"songs\": [")
            if (songLines.isNotEmpty()) {
                append('\n')
                append(songLines)
                append('\n')
                append(indent)
            }
            append("]\n")
            append("}")
        }
    }

    companion object {
        fun fromJsonOrNull(rawJson: String?): BackupBundleManifest? {
            if (rawJson.isNullOrBlank()) return null

            return runCatching {
                val json = rawJson
                val songsBlock = extractJsonArrayContent(json, "songs").orEmpty()
                val songs = splitJsonObjects(songsBlock).mapNotNull { songJson ->
                    val songId = extractJsonString(songJson, "songId").orEmpty().trim()
                    val entry = extractJsonString(songJson, "entry").orEmpty().trim()
                    if (songId.isEmpty() || entry.isEmpty()) {
                        null
                    } else {
                        BackupBundleSongEntry(
                            songId = songId,
                            entry = entry
                        )
                    }
                }.toList()

                BackupBundleManifest(
                    format = extractJsonString(json, "format").orEmpty().trim().ifBlank {
                        BACKUP_BUNDLE_FORMAT
                    },
                    version = extractJsonInt(json, "version") ?: BACKUP_BUNDLE_VERSION,
                    stateEntry = extractJsonString(json, "stateEntry").orEmpty().trim().ifBlank {
                        BACKUP_BUNDLE_DEFAULT_STATE_ENTRY
                    },
                    smpDir = extractJsonString(json, "smpDir").orEmpty().trim().ifBlank {
                        BACKUP_BUNDLE_DEFAULT_SMP_DIR
                    },
                    songs = songs
                )
            }.getOrNull()
        }
        private fun extractJsonString(rawJson: String, key: String): String? {
            val valueStart = findJsonValueStart(rawJson, key) ?: return null
            if (valueStart >= rawJson.length || rawJson[valueStart] != '"') return null

            val decoded = StringBuilder()
            var index = valueStart + 1
            var escapeNext = false
            while (index < rawJson.length) {
                val char = rawJson[index]
                when {
                    escapeNext -> {
                        decoded.append(
                            when (char) {
                                '\\' -> '\\'
                                '"' -> '"'
                                'n' -> '\n'
                                'r' -> '\r'
                                't' -> '\t'
                                else -> char
                            }
                        )
                        escapeNext = false
                    }
                    char == '\\' -> escapeNext = true
                    char == '"' -> return decoded.toString()
                    else -> decoded.append(char)
                }
                index += 1
            }
            return null
        }

        private fun extractJsonInt(rawJson: String, key: String): Int? {
            val valueStart = findJsonValueStart(rawJson, key) ?: return null
            var index = valueStart
            while (index < rawJson.length && rawJson[index].isWhitespace()) {
                index += 1
            }
            val numberStart = index
            if (index < rawJson.length && rawJson[index] == '-') {
                index += 1
            }
            while (index < rawJson.length && rawJson[index].isDigit()) {
                index += 1
            }
            if (index == numberStart || (index == numberStart + 1 && rawJson[numberStart] == '-')) {
                return null
            }
            return rawJson.substring(numberStart, index).toIntOrNull()
        }

        private fun extractJsonArrayContent(rawJson: String, key: String): String? {
            val arrayStart = findJsonValueStart(rawJson, key)?.let { start ->
                rawJson.indexOf('[', startIndex = start)
            } ?: return null
            if (arrayStart < 0) return null

            var depth = 0
            var inString = false
            var escapeNext = false
            for (index in arrayStart until rawJson.length) {
                val char = rawJson[index]
                when {
                    escapeNext -> escapeNext = false
                    char == '\\' && inString -> escapeNext = true
                    char == '"' -> inString = !inString
                    !inString && char == '[' -> depth += 1
                    !inString && char == ']' -> {
                        depth -= 1
                        if (depth == 0) {
                            return rawJson.substring(arrayStart + 1, index)
                        }
                    }
                }
            }
            return null
        }

        private fun splitJsonObjects(arrayContent: String): List<String> {
            val objects = mutableListOf<String>()
            var objectStart = -1
            var depth = 0
            var inString = false
            var escapeNext = false

            arrayContent.forEachIndexed { index, char ->
                when {
                    escapeNext -> escapeNext = false
                    char == '\\' && inString -> escapeNext = true
                    char == '"' -> inString = !inString
                    !inString && char == '{' -> {
                        if (depth == 0) {
                            objectStart = index
                        }
                        depth += 1
                    }
                    !inString && char == '}' -> {
                        depth -= 1
                        if (depth == 0 && objectStart >= 0) {
                            objects += arrayContent.substring(objectStart, index + 1)
                            objectStart = -1
                        }
                    }
                }
            }

            return objects
        }

        private fun findJsonValueStart(rawJson: String, key: String): Int? {
            val keyToken = "\"$key\""
            val keyIndex = rawJson.indexOf(keyToken)
            if (keyIndex < 0) return null
            var index = keyIndex + keyToken.length
            while (index < rawJson.length && rawJson[index].isWhitespace()) {
                index += 1
            }
            if (index >= rawJson.length || rawJson[index] != ':') return null
            index += 1
            while (index < rawJson.length && rawJson[index].isWhitespace()) {
                index += 1
            }
            return index
        }
    }
}

private fun String.escapeJson(): String = buildString(length) {
    for (char in this@escapeJson) {
        when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(char)
        }
    }
}

private fun String.unescapeJson(): String = buildString(length) {
    var index = 0
    while (index < length) {
        val char = this@unescapeJson[index]
        if (char == '\\' && index + 1 < length) {
            when (val next = this@unescapeJson[index + 1]) {
                '\\' -> append('\\')
                '"' -> append('"')
                'n' -> append('\n')
                'r' -> append('\r')
                't' -> append('\t')
                else -> append(next)
            }
            index += 2
        } else {
            append(char)
            index += 1
        }
    }
}
