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
    val playbackProfile: SmpConfig.PlaybackConfig?,
    val mediaItems: List<MediaItem>,
    val livePlan: LiveArrangementPlan
) {
    val occurrenceDurationsMs: List<Long> =
        livePlan.occurrences.map(LiveArrangementOccurrence::durationMs)
    val durationMs: Long = livePlan.durationMs
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
        val livePlan = LiveArrangementPlan(
            occurrences = occurrences.map { occurrence ->
                LiveArrangementOccurrence(
                    key = "${variantSong.id}:${occurrence.entryIndex}:${occurrence.repeatIndex}",
                    label = occurrence.segment.name,
                    durationMs = occurrence.durationMs,
                    color = occurrence.color
                )
            }
        )
        val mediaItems = occurrences.zip(livePlan.occurrences).map { (occurrence, liveOccurrence) ->
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
                .setMediaId(liveOccurrence.key)
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
            playbackProfile = SmpVariantPlayback.resolveProfile(
                context = context,
                variant = variantSong,
                parent = sourceSong
            ),
            mediaItems = mediaItems,
            livePlan = livePlan
        )
    }
}
