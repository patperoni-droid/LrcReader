package com.patrick.lrcreader.ui

import android.content.Context
import android.util.Log
import com.patrick.lrcreader.core.PlaylistRepository
import com.patrick.lrcreader.core.TextSongRepository
import com.patrick.lrcreader.core.buildSmpItem
import com.patrick.lrcreader.core.getSmpSongId
import com.patrick.lrcreader.core.isGroupEnd
import com.patrick.lrcreader.core.isGroupHeader
import com.patrick.lrcreader.core.isPrompterItem
import com.patrick.lrcreader.core.config.PlaylistStateStore
import com.patrick.lrcreader.exo.R
import com.patrick.lrcreader.smp.SmpLibraryScanner
import org.json.JSONArray
import org.json.JSONObject

private const val RESTORE_DIAG_TAG = "RESTORE_DIAG"

internal data class PlaylistFileImportResult(
    val importedPlaylistCount: Int,
    val foundCount: Int,
    val missingCount: Int,
    val failed: Boolean
)

private data class PlaylistFileImportItem(
    val uri: String,
    val songId: String? = null,
    val customTitle: String? = null
)

private data class PlaylistFileImportSource(
    val name: String,
    val items: List<PlaylistFileImportItem>
)

internal fun importPlaylistFile(
    context: Context,
    rawJson: String
): PlaylistFileImportResult {
    val sources = parsePlaylistImportSources(context, rawJson)
    if (sources.isEmpty()) {
        return PlaylistFileImportResult(
            importedPlaylistCount = 0,
            foundCount = 0,
            missingCount = 0,
            failed = true
        )
    }

    val runtimeSongIds = SmpLibraryScanner(context).listSongs()
        .map { it.id.trim() }
        .filter { it.isNotEmpty() }
        .toSet()
    val runtimePrompterIds = TextSongRepository.exportAll(context).keys
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toSet()

    var importedPlaylistCount = 0
    var foundCount = 0
    var missingCount = 0

    sources.forEach { source ->
        val targetName = uniquePlaylistImportName(source.name)
        val importedOrder = mutableListOf<String>()
        Log.d(RESTORE_DIAG_TAG, "playlistRestore name=$targetName source=playlist_file")
        PlaylistRepository.addPlaylist(targetName)

        source.items.forEach { item ->
            val rawUri = item.uri.trim()
            when {
                rawUri.isBlank() -> Unit

                isGroupHeader(rawUri) || isGroupEnd(rawUri) -> {
                    PlaylistRepository.assignSongToPlaylist(targetName, rawUri)
                    importedOrder += rawUri
                }

                isPrompterItem(rawUri) -> {
                    val prompterId = rawUri.removePrefix("prompter://").trim()
                    if (prompterId.isNotEmpty() && prompterId in runtimePrompterIds) {
                        PlaylistRepository.assignSongToPlaylist(targetName, rawUri)
                        importedOrder += rawUri
                        foundCount += 1
                    } else {
                        missingCount += 1
                    }
                }

                else -> {
                    val songId = item.songId?.trim()?.takeIf { it.isNotEmpty() }
                        ?: getSmpSongId(rawUri)
                    Log.d(
                        RESTORE_DIAG_TAG,
                        "playlistItem songId=${songId ?: "null"} playlistItemExistsInLibrary=${songId != null && songId in runtimeSongIds}"
                    )
                    if (songId != null && songId in runtimeSongIds) {
                        val playlistUri = buildSmpItem(songId)
                        PlaylistRepository.assignSongToPlaylist(
                            playlistName = targetName,
                            songUri = playlistUri,
                            songId = songId
                        )
                        item.customTitle
                            ?.trim()
                            ?.takeIf { it.isNotEmpty() }
                            ?.let { title ->
                                PlaylistRepository.renameSongInPlaylist(targetName, playlistUri, title)
                            }
                        importedOrder += playlistUri
                        foundCount += 1
                    } else {
                        missingCount += 1
                    }
                }
            }
        }

        if (importedOrder.isNotEmpty()) {
            PlaylistRepository.updatePlayListOrder(targetName, importedOrder)
            importedPlaylistCount += 1
        } else {
            PlaylistRepository.deletePlaylist(targetName)
        }
    }

    if (importedPlaylistCount > 0) {
        val saved = PlaylistStateStore.savePlaylistsSnapshot(context)
        val restored = PlaylistStateStore.restorePlaylistsIntoRepository(context)
        Log.d(
            RESTORE_DIAG_TAG,
            "playlistImportPersist saved=$saved restored=${restored.success} restoredPlaylistCount=${restored.restoredPlaylistCount}"
        )
    }

    return PlaylistFileImportResult(
        importedPlaylistCount = importedPlaylistCount,
        foundCount = foundCount,
        missingCount = missingCount,
        failed = importedPlaylistCount == 0
    )
}

internal fun formatPlaylistImportResultMessage(
    context: Context,
    result: PlaylistFileImportResult
): String {
    if (result.failed) {
        return context.getString(R.string.playlist_import_failed)
    }
    return if (result.missingCount > 0) {
        context.getString(
            R.string.playlist_import_done_with_missing,
            result.foundCount,
            result.missingCount
        )
    } else {
        context.getString(
            R.string.playlist_import_done,
            result.foundCount
        )
    }
}

private fun parsePlaylistImportSources(
    context: Context,
    rawJson: String
): List<PlaylistFileImportSource> {
    if (rawJson.isBlank()) return emptyList()
    return runCatching {
        val root = JSONObject(rawJson)
        val singleName = root.optString("name", "").trim()
        val singleItems = root.optJSONArray("items")
        if (singleItems != null) {
            return@runCatching listOf(
                PlaylistFileImportSource(
                    name = singleName.ifBlank { context.getString(R.string.playlist_import_default_name) },
                    items = parsePlaylistImportItems(singleItems)
                )
            )
        }

        val playlists = root.optJSONObject("playlists") ?: return@runCatching emptyList()
        val names = playlists.keys()
        buildList {
            while (names.hasNext()) {
                val name = names.next()
                val arr = playlists.optJSONArray(name) ?: continue
                add(
                    PlaylistFileImportSource(
                        name = name.trim().ifBlank { context.getString(R.string.playlist_import_default_name) },
                        items = parsePlaylistImportItems(arr)
                    )
                )
            }
        }
    }.getOrDefault(emptyList())
}

private fun parsePlaylistImportItems(arr: JSONArray): List<PlaylistFileImportItem> {
    return buildList {
        for (index in 0 until arr.length()) {
            val value = arr.opt(index)
            if (value is JSONObject) {
                val uri = value.optString("uri", "").trim()
                if (uri.isBlank() || uri.equals("null", ignoreCase = true)) continue
                add(
                    PlaylistFileImportItem(
                        uri = uri,
                        songId = value.optString("songId", "").trim().ifBlank { null },
                        customTitle = value.optString("customTitle", "").trim().ifBlank {
                            value.optString("title", "").trim().ifBlank { null }
                        }
                    )
                )
            } else {
                val uri = arr.optString(index, "").trim()
                if (uri.isNotBlank() && !uri.equals("null", ignoreCase = true)) {
                    add(PlaylistFileImportItem(uri = uri))
                }
            }
        }
    }
}

private fun uniquePlaylistImportName(sourceName: String): String {
    val clean = sourceName.trim().ifBlank { "Playlist" }
    val existing = PlaylistRepository.getPlaylists().toSet()
    if (clean !in existing) return clean
    var index = 2
    while (true) {
        val candidate = "$clean ($index)"
        if (candidate !in existing) return candidate
        index += 1
    }
}
