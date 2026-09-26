package com.patrick.lrcreader.ui.library

import com.patrick.lrcreader.smp.SongUnit
import com.patrick.lrcreader.smp.SmpRuntimeSongCache
import com.patrick.lrcreader.core.buildSmpItem

data class LibrarySongItem(
    val song: SongUnit,
    val playbackItem: String,
    val displayTitle: String,
    val fallbackTitle: String,
    val volumeSource: String = "manual",
    val volumeDb: Int? = null,
    val lufsMeasured: Float? = null,
    val lufsTarget: Float? = null,
    val lufsAutoDb: Float? = null,
    val lufsManualDb: Int? = null,
    val arrangementPlayable: Boolean = false
) {
    val songId: String get() = song.id
    val isArrangementVariant: Boolean get() = song.arrangementSourceSongId != null
    val isLufsActive: Boolean get() = volumeSource == "lufs"
    val audioAvailable: Boolean get() = song.audioPath != null
    val playbackAvailable: Boolean get() = audioAvailable || arrangementPlayable
    val hasLyrics: Boolean get() = song.lyricsPath != null
    val hasChords: Boolean get() = song.chordsPath != null
    val hasNotes: Boolean get() = song.annotationsPath != null
    val hasMidi: Boolean get() = song.midiPath != null || song.midiCues.isNotEmpty()
    val hasLight: Boolean get() = song.dmxPath != null
    val hasPrompter: Boolean get() = song.prompterPath != null
    val storageFolder: String? get() = song.storageFolder
}

internal fun buildLibrarySongItemsFromCache(
    cachedSongs: Collection<SmpRuntimeSongCache.CachedSong>
): List<LibrarySongItem> = cachedSongs
    .map { cached ->
        val song = cached.song
        LibrarySongItem(
            song = song,
            playbackItem = buildSmpItem(song.id),
            displayTitle = cached.displayTitle.ifBlank { song.title.ifBlank { song.id } },
            fallbackTitle = song.title.ifBlank { song.id },
            volumeSource = cached.volumeSource,
            volumeDb = cached.volumeDb,
            lufsMeasured = cached.lufsMeasured,
            lufsTarget = cached.lufsTarget,
            lufsAutoDb = cached.lufsAutoDb,
            lufsManualDb = cached.lufsManualDb,
            arrangementPlayable = cached.arrangementPlayable
        )
    }
    .sortedBy { it.displayTitle.lowercase() }
