package com.patrick.lrcreader.smp

data class SmpSongDeletionPlan(
    val requestedSongId: String,
    val songs: List<SongUnit>
) {
    val songIds: Set<String> = songs.mapTo(linkedSetOf(), SongUnit::id)
    val variantCount: Int = songs.count { it.arrangementSourceSongId != null }
}

fun buildSmpSongDeletionPlan(
    requestedSongId: String,
    songsById: Map<String, SongUnit>
): SmpSongDeletionPlan? {
    val cleanSongId = requestedSongId.trim().takeIf(String::isNotEmpty) ?: return null
    val requestedSong = songsById[cleanSongId] ?: return null
    val songsToDelete = if (requestedSong.arrangementSourceSongId != null) {
        listOf(requestedSong)
    } else {
        buildList {
            add(requestedSong)
            songsById.values
                .filter { song -> song.arrangementSourceSongId == cleanSongId }
                .sortedBy(SongUnit::id)
                .forEach(::add)
        }
    }
    return SmpSongDeletionPlan(
        requestedSongId = cleanSongId,
        songs = songsToDelete
    )
}
