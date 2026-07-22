package com.patrick.lrcreader.smp

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class PreparedVirtualArrangementPlayback(
    val variantSongId: String,
    val title: String,
    val sourceSongId: String,
    val sourceAudioUri: String,
    val mediaItems: List<MediaItem>,
    val occurrenceDurationsMs: List<Long>
) {
    val durationMs: Long = occurrenceDurationsMs.fold(0L) { total, duration ->
        if (duration > Long.MAX_VALUE - total) Long.MAX_VALUE else total + duration
    }
}

object VirtualArrangementPlaybackResolver {
    suspend fun resolve(
        context: Context,
        variantSong: SongUnit,
        songsById: Map<String, SongUnit>
    ): PreparedVirtualArrangementPlayback? = withContext(Dispatchers.IO) {
        val sourceSongId = variantSong.arrangementSourceSongId?.trim().orEmpty()
        if (sourceSongId.isEmpty()) return@withContext null
        val sourceSong = songsById[sourceSongId] ?: return@withContext null
        val sourceAudioFile = sourceSong.audioPath
            ?.takeIf(String::isNotBlank)
            ?.let(::File)
            ?.takeIf(File::isFile)
            ?: return@withContext null
        val arrangement = ArrangementStore.load(context, variantSong.id) ?: return@withContext null
        if (arrangement.sourceSongId != sourceSongId) return@withContext null

        val projection = arrangement.toOccurrenceProjection()
        val occurrences = prepareArrangementOccurrences(
            segments = projection.segments,
            structureSegmentIds = projection.structureSegmentIds,
            entries = projection.entries,
            useOccurrenceModel = projection.entries.isNotEmpty()
        )
        if (occurrences.isEmpty()) return@withContext null

        val sourceUri = Uri.fromFile(sourceAudioFile)
        val mediaItems = occurrences.map { occurrence ->
            val startMs = minOf(
                occurrence.segment.startMs,
                occurrence.segment.endMs
            ).coerceAtLeast(0L)
            val endMs = maxOf(
                occurrence.segment.startMs,
                occurrence.segment.endMs
            ).coerceAtLeast(startMs + 1L)
            MediaItem.Builder()
                .setUri(sourceUri)
                .setMediaId("${variantSong.id}:${occurrence.entryIndex}:${occurrence.repeatIndex}")
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(startMs)
                        .setEndPositionMs(endMs)
                        .build()
                )
                .build()
        }

        PreparedVirtualArrangementPlayback(
            variantSongId = variantSong.id,
            title = variantSong.title,
            sourceSongId = sourceSongId,
            sourceAudioUri = sourceUri.toString(),
            mediaItems = mediaItems,
            occurrenceDurationsMs = occurrences.map { it.durationMs }
        )
    }
}
