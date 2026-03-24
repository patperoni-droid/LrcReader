package com.patrick.lrcreader.core.backup

import android.content.Context
import com.patrick.lrcreader.core.getSmpSongId
import com.patrick.lrcreader.smp.SmpLibraryScanner
import com.patrick.lrcreader.smp.SongUnit

data class BackupBundleSmpExportPreflight(
    val referencedSongIds: List<String>,
    val resolvedSongs: List<SongUnit>,
    val missingSongIds: List<String>
) {
    val isExportAllowed: Boolean
        get() = missingSongIds.isEmpty()
}

object BackupBundlePlanner {

    fun collectReferencedSmpSongIds(playlists: Map<String, List<String>>): List<String> {
        val orderedSongIds = linkedSetOf<String>()
        playlists.values.forEach { items ->
            items.forEach { item ->
                val songId = getSmpSongId(item) ?: return@forEach
                orderedSongIds += songId
            }
        }
        return orderedSongIds.toList()
    }

    fun buildSmpExportPreflight(
        playlists: Map<String, List<String>>,
        resolveSongById: (String) -> SongUnit?
    ): BackupBundleSmpExportPreflight {
        val referencedSongIds = collectReferencedSmpSongIds(playlists)
        val resolvedSongs = mutableListOf<SongUnit>()
        val missingSongIds = mutableListOf<String>()

        referencedSongIds.forEach { songId ->
            val resolvedSong = resolveSongById(songId)
            if (resolvedSong?.id?.trim() == songId) {
                resolvedSongs += resolvedSong
            } else {
                missingSongIds += songId
            }
        }

        return BackupBundleSmpExportPreflight(
            referencedSongIds = referencedSongIds,
            resolvedSongs = resolvedSongs,
            missingSongIds = missingSongIds
        )
    }

    fun buildSmpExportPreflight(
        context: Context,
        playlists: Map<String, List<String>>
    ): BackupBundleSmpExportPreflight {
        val smpLibraryScanner = SmpLibraryScanner(context)
        return buildSmpExportPreflight(
            playlists = playlists,
            resolveSongById = smpLibraryScanner::findSongById
        )
    }
}
