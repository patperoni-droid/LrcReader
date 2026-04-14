package com.patrick.lrcreader.ui.library

import com.patrick.lrcreader.smp.SongUnit

data class LibrarySongItem(
    val song: SongUnit,
    val playbackItem: String,
    val displayTitle: String,
    val fallbackTitle: String,
    val volumeSource: String = "manual"
) {
    val songId: String get() = song.id
    val isLufsActive: Boolean get() = volumeSource == "lufs"
    val audioAvailable: Boolean get() = song.audioPath != null
    val hasLyrics: Boolean get() = song.lyricsPath != null
    val hasChords: Boolean get() = song.chordsPath != null
    val hasNotes: Boolean get() = song.annotationsPath != null
    val hasMidi: Boolean get() = song.midiPath != null || song.midiCues.isNotEmpty()
    val hasLight: Boolean get() = song.dmxPath != null
    val hasPrompter: Boolean get() = song.prompterPath != null
    val storageFolder: String? get() = song.storageFolder
}
