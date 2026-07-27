package com.patrick.lrcreader.core.backup

import com.patrick.lrcreader.core.buildSmpItem
import com.patrick.lrcreader.core.getSmpSongId
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URI

data class BackupStateRemapWarning(
    val path: String,
    val value: String,
    val action: String
)

data class BackupStateRemapFailure(
    val path: String,
    val value: String,
    val reason: String
)

sealed interface BackupStateRemapResult {
    data class Success(
        val stateJson: String,
        val warnings: List<BackupStateRemapWarning> = emptyList()
    ) : BackupStateRemapResult

    data class Failure(
        val failures: List<BackupStateRemapFailure>
    ) : BackupStateRemapResult
}

object BackupStateRemapper {

    fun remapBundleStateJson(
        stateJson: String,
        importedSongs: List<BackupBundleImportedSong>
    ): BackupStateRemapResult {
        val root = runCatching { JSONObject(stateJson) }.getOrNull()
            ?: return BackupStateRemapResult.Failure(
                failures = listOf(
                    BackupStateRemapFailure(
                        path = "$",
                        value = "",
                        reason = "state.json invalide"
                    )
                )
            )

        val context = RemapContext(importedSongs)
        val warnings = mutableListOf<BackupStateRemapWarning>()
        val failures = mutableListOf<BackupStateRemapFailure>()

        remapPlaylistLikeSection(
            root = root,
            sectionName = "playlists",
            context = context,
            failures = failures
        )
        remapPlaylistLikeSection(
            root = root,
            sectionName = "played",
            context = context,
            failures = failures
        )
        remapPlaylistLikeSection(
            root = root,
            sectionName = "review",
            context = context,
            failures = failures
        )
        remapLastPlayed(root, context, warnings)
        remapFillerSound(root, context, warnings)
        remapEdits(root, context, warnings)

        if (failures.isNotEmpty()) {
            return BackupStateRemapResult.Failure(
                failures = failures
            )
        }

        return BackupStateRemapResult.Success(
            stateJson = root.toString(2),
            warnings = warnings
        )
    }

    private fun remapPlaylistLikeSection(
        root: JSONObject,
        sectionName: String,
        context: RemapContext,
        failures: MutableList<BackupStateRemapFailure>
    ) {
        val sectionJson = root.optJSONObject(sectionName) ?: return
        val playlistNames = sectionJson.keys()
        while (playlistNames.hasNext()) {
            val playlistName = playlistNames.next()
            val sourceArray = sectionJson.optJSONArray(playlistName) ?: continue
            val remappedArray = JSONArray()
            for (index in 0 until sourceArray.length()) {
                val entryValue = sourceArray.opt(index)
                val remappedValue = remapPlaylistLikeEntry(
                    entryValue = entryValue,
                    path = "$sectionName.$playlistName[$index]",
                    context = context,
                    failures = failures
                ) ?: continue
                remappedArray.put(remappedValue)
            }
            sectionJson.put(playlistName, remappedArray)
        }
    }

    private fun remapPlaylistLikeEntry(
        entryValue: Any?,
        path: String,
        context: RemapContext,
        failures: MutableList<BackupStateRemapFailure>
    ): Any? {
        return when (entryValue) {
            is JSONObject -> {
                val oldUri = entryValue.optString("uri", "")
                if (oldUri.isBlank()) {
                    JSONObject(entryValue.toString())
                } else {
                    val remappedUri = remapMandatoryReference(
                        oldValue = oldUri,
                        path = "$path.uri",
                        context = context,
                        failures = failures
                    ) ?: return null
                    val remappedSongId = context.resolveImportedSongId(oldUri)
                        ?: getSmpSongId(remappedUri)
                    JSONObject(entryValue.toString()).apply {
                        put("uri", remappedUri)
                        if (remappedSongId != null) {
                            put("songId", remappedSongId)
                        } else if (
                            isNull("songId") ||
                            optString("songId", "").trim().equals("null", ignoreCase = true)
                        ) {
                            remove("songId")
                        }
                    }
                }
            }

            else -> {
                val oldValue = entryValue?.toString().orEmpty()
                if (oldValue.isBlank()) {
                    oldValue
                } else {
                    remapMandatoryReference(
                        oldValue = oldValue,
                        path = path,
                        context = context,
                        failures = failures
                    )
                }
            }
        }
    }

    private fun remapMandatoryReference(
        oldValue: String,
        path: String,
        context: RemapContext,
        failures: MutableList<BackupStateRemapFailure>
    ): String? {
        val smpSongId = getSmpSongId(oldValue)
        if (smpSongId != null) {
            return context.remapSmpPlaylistItem(oldValue) ?: run {
                failures += BackupStateRemapFailure(
                    path = path,
                    value = oldValue,
                    reason = "songId SMP non importé: $smpSongId"
                )
                null
            }
        }

        return when (val runtimeResult = context.remapRuntimeUri(oldValue)) {
            is RuntimeUriRemapResult.NotSmpRuntime -> oldValue
            is RuntimeUriRemapResult.Remapped -> runtimeResult.uriString
            is RuntimeUriRemapResult.Unresolved -> {
                failures += BackupStateRemapFailure(
                    path = path,
                    value = oldValue,
                    reason = runtimeResult.reason
                )
                null
            }
        }
    }

    private fun remapLastPlayed(
        root: JSONObject,
        context: RemapContext,
        warnings: MutableList<BackupStateRemapWarning>
    ) {
        val lastPlayed = root.optJSONObject("lastPlayed") ?: return
        val oldUri = lastPlayed.optString("uri", "")
        if (oldUri.isBlank()) return

        when (val runtimeResult = context.remapRuntimeUri(oldUri)) {
            is RuntimeUriRemapResult.NotSmpRuntime -> Unit
            is RuntimeUriRemapResult.Remapped -> lastPlayed.put("uri", runtimeResult.uriString)
            is RuntimeUriRemapResult.Unresolved -> {
                root.remove("lastPlayed")
                warnings += BackupStateRemapWarning(
                    path = "lastPlayed.uri",
                    value = oldUri,
                    action = "lastPlayed supprimé (${runtimeResult.reason})"
                )
            }
        }
    }

    private fun remapFillerSound(
        root: JSONObject,
        context: RemapContext,
        warnings: MutableList<BackupStateRemapWarning>
    ) {
        val fillerSound = root.optJSONObject("fillerSound") ?: return
        val oldUri = fillerSound.optString("uri", "")
        if (oldUri.isBlank()) return

        when (val runtimeResult = context.remapRuntimeUri(oldUri)) {
            is RuntimeUriRemapResult.NotSmpRuntime -> Unit
            is RuntimeUriRemapResult.Remapped -> fillerSound.put("uri", runtimeResult.uriString)
            is RuntimeUriRemapResult.Unresolved -> {
                root.remove("fillerSound")
                warnings += BackupStateRemapWarning(
                    path = "fillerSound.uri",
                    value = oldUri,
                    action = "fillerSound supprimé (${runtimeResult.reason})"
                )
            }
        }
    }

    private fun remapEdits(
        root: JSONObject,
        context: RemapContext,
        warnings: MutableList<BackupStateRemapWarning>
    ) {
        val edits = root.optJSONObject("edits") ?: return
        val remappedEdits = JSONObject()
        val editKeys = edits.keys()

        while (editKeys.hasNext()) {
            val oldUri = editKeys.next()
            val editPayload = edits.optJSONObject(oldUri) ?: continue

            when (val runtimeResult = context.remapRuntimeUri(oldUri)) {
                is RuntimeUriRemapResult.NotSmpRuntime -> remappedEdits.put(oldUri, editPayload)
                is RuntimeUriRemapResult.Remapped -> remappedEdits.put(runtimeResult.uriString, editPayload)
                is RuntimeUriRemapResult.Unresolved -> {
                    warnings += BackupStateRemapWarning(
                        path = "edits.$oldUri",
                        value = oldUri,
                        action = "edit supprimé (${runtimeResult.reason})"
                    )
                }
            }
        }

        if (remappedEdits.length() == 0) {
            root.remove("edits")
        } else {
            root.put("edits", remappedEdits)
        }
    }

    private class RemapContext(importedSongs: List<BackupBundleImportedSong>) {
        private val importedByBundleSongId = importedSongs.associateBy { it.bundleSongId }

        fun remapSmpPlaylistItem(value: String): String? {
            val bundleSongId = getSmpSongId(value) ?: return null
            val importedSong = importedByBundleSongId[bundleSongId] ?: return null
            return if (importedSong.importedSongId == bundleSongId) {
                value
            } else {
                buildSmpItem(importedSong.importedSongId)
            }
        }

        fun remapRuntimeUri(uriString: String): RuntimeUriRemapResult {
            val runtimeRef = parseRuntimeRef(uriString) ?: return RuntimeUriRemapResult.NotSmpRuntime
            val importedSong = importedByBundleSongId[runtimeRef.bundleSongId]
                ?: return RuntimeUriRemapResult.Unresolved(
                    reason = "songId SMP non importé: ${runtimeRef.bundleSongId}"
                )

            val storageFolder = importedSong.storageFolder?.takeIf { it.isNotBlank() }
                ?: return RuntimeUriRemapResult.Unresolved(
                    reason = "storageFolder absent pour ${runtimeRef.bundleSongId}"
                )

            val targetFile = File(storageFolder, runtimeRef.relativePath)
            return RuntimeUriRemapResult.Remapped(
                uriString = targetFile.toURI().toString()
            )
        }

        fun resolveImportedSongId(value: String): String? {
            getSmpSongId(value)?.let { bundleSongId ->
                return importedByBundleSongId[bundleSongId]?.importedSongId
            }
            val runtimeRef = parseRuntimeRef(value) ?: return null
            return importedByBundleSongId[runtimeRef.bundleSongId]?.importedSongId
        }

        private fun parseRuntimeRef(uriString: String): RuntimeRef? {
            val uri = runCatching { URI(uriString) }.getOrNull() ?: return null
            if (uri.scheme != "file") return null

            val rawPath = uri.path?.takeIf { it.isNotBlank() } ?: return null
            val normalizedPath = rawPath.replace('\\', '/')
            val marker = "/tracks/"
            val markerIndex = normalizedPath.lastIndexOf(marker)
            if (markerIndex < 0) return null

            val remainder = normalizedPath.substring(markerIndex + marker.length)
            val separatorIndex = remainder.indexOf('/')
            if (separatorIndex <= 0 || separatorIndex >= remainder.lastIndex) return null

            val bundleSongId = remainder.substring(0, separatorIndex).trim()
            val relativePath = remainder.substring(separatorIndex + 1).trim()
            if (bundleSongId.isBlank() || relativePath.isBlank()) return null

            return RuntimeRef(
                bundleSongId = bundleSongId,
                relativePath = relativePath
            )
        }
    }

    private data class RuntimeRef(
        val bundleSongId: String,
        val relativePath: String
    )

    private sealed interface RuntimeUriRemapResult {
        data object NotSmpRuntime : RuntimeUriRemapResult

        data class Remapped(
            val uriString: String
        ) : RuntimeUriRemapResult

        data class Unresolved(
            val reason: String
        ) : RuntimeUriRemapResult
    }
}
