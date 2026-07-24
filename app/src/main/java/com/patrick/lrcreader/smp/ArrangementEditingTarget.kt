package com.patrick.lrcreader.smp

data class ArrangementEditingTarget(
    val ownerSong: SongUnit,
    val sourceSong: SongUnit
) {
    val ownerSongId: String get() = ownerSong.id
    val sourceSongId: String get() = sourceSong.id
    val variantSongId: String? get() = ownerSong.id.takeIf { ownerSong.arrangementSourceSongId != null }
}

fun resolveArrangementEditingTarget(
    selectedSong: SongUnit,
    songsById: Map<String, SongUnit>
): ArrangementEditingTarget? {
    val sourceSongId = selectedSong.arrangementSourceSongId?.trim().orEmpty()
    val sourceSong = if (sourceSongId.isEmpty()) {
        selectedSong
    } else {
        songsById[sourceSongId] ?: return null
    }
    if (sourceSong.audioPath.isNullOrBlank()) return null
    return ArrangementEditingTarget(
        ownerSong = selectedSong,
        sourceSong = sourceSong
    )
}
