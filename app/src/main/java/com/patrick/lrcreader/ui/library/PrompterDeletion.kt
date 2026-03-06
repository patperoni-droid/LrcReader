package com.patrick.lrcreader.ui.library

import android.content.Context
import com.patrick.lrcreader.core.PlaylistRepository
import com.patrick.lrcreader.core.TextSongRepository

internal fun deletePrompterAndRemoveFromAllPlaylists(context: Context, uriString: String): Boolean {
    val prompterId = extractPrompterIdFromUriString(uriString) ?: return false
    TextSongRepository.delete(context, prompterId)
    PlaylistRepository.getPlaylists().forEach { playlist ->
        PlaylistRepository.removeSongFromPlaylist(playlist, uriString)
    }
    return true
}

private fun extractPrompterIdFromUriString(uriString: String): String? {
    val raw = uriString.trim()
    if (!raw.startsWith("prompter://")) return null
    return raw.removePrefix("prompter://").ifBlank { null }
}
