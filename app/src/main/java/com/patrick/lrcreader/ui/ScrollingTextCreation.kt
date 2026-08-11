package com.patrick.lrcreader.ui

import android.content.Context
import com.patrick.lrcreader.core.PlaylistRepository
import com.patrick.lrcreader.core.TextSongRepository

internal data class ScrollingTextCreationResult(
    val id: String,
    val uri: String
)

internal fun createScrollingText(
    context: Context,
    title: String,
    content: String,
    playlistName: String? = null
): ScrollingTextCreationResult? {
    val cleanTitle = title.trim()
    val cleanContent = content.trim()
    if (cleanTitle.isEmpty() || cleanContent.isEmpty()) return null

    val id = TextSongRepository.create(context, cleanTitle, cleanContent)
    val uri = TextSongRepository.resolvePrompterUri(id)
    playlistName
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.let { PlaylistRepository.assignSongToPlaylist(it, uri) }

    return ScrollingTextCreationResult(id = id, uri = uri)
}
