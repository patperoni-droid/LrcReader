package com.patrick.lrcreader.smp

import android.net.Uri
import androidx.media3.common.MediaItem

internal enum class ArrangementAssetTimeDomain {
    SOURCE,
    ARRANGEMENT
}

internal data class ArrangementPlaybackOccurrenceId(
    val ownerSongId: String,
    val entryId: String,
    val repeatIndex: Int
)

internal data class ArrangementPlaybackGroupKey(
    val ownerSongId: String,
    val entryId: String
)

internal data class PreparedArrangementPlaybackOccurrence(
    val id: ArrangementPlaybackOccurrenceId,
    val groupKey: ArrangementPlaybackGroupKey,
    val entryId: String,
    val entryIndex: Int,
    val repeatIndex: Int,
    val repeatCount: Int,
    val sourceStartMs: Long,
    val sourceEndMs: Long,
    val arrangementStartMs: Long,
    val durationMs: Long,
    val label: String,
    val color: String?,
    val mediaId: String
) {
    fun sourcePositionMs(localPositionMs: Long): Long = saturatedTimeAdd(
        sourceStartMs,
        localPositionMs.coerceIn(0L, durationMs)
    )

    fun arrangementPositionMs(localPositionMs: Long): Long = saturatedTimeAdd(
        arrangementStartMs,
        localPositionMs.coerceIn(0L, durationMs)
    )

    fun localPositionFromSourceMs(sourcePositionMs: Long): Long? {
        if (sourcePositionMs !in sourceStartMs..sourceEndMs) return null
        return sourcePositionMs - sourceStartMs
    }

    fun localPositionFromArrangementMs(arrangementPositionMs: Long): Long? {
        val arrangementEndMs = saturatedTimeAdd(arrangementStartMs, durationMs)
        if (arrangementPositionMs !in arrangementStartMs..arrangementEndMs) return null
        return arrangementPositionMs - arrangementStartMs
    }

    fun arrangementPositionFromSourceMs(sourcePositionMs: Long): Long? =
        localPositionFromSourceMs(sourcePositionMs)?.let(::arrangementPositionMs)
}

internal data class PreparedArrangementPlayback(
    val ownerSongId: String,
    val audioSourceSongId: String,
    val title: String,
    val sourceAudioUri: Uri,
    val playbackProfile: SmpConfig.PlaybackConfig?,
    val assetTimeDomain: ArrangementAssetTimeDomain,
    val occurrences: List<PreparedArrangementPlaybackOccurrence>,
    val mediaItems: List<MediaItem>,
    val livePlan: LiveArrangementPlan,
    val navigationItems: List<ArrangementNavigationItem>
) {
    val occurrenceDurationsMs: List<Long> = occurrences.map { occurrence ->
        occurrence.durationMs
    }
    val durationMs: Long = livePlan.durationMs

    fun arrangementPositionFromSourceMs(
        occurrenceId: ArrangementPlaybackOccurrenceId,
        sourcePositionMs: Long
    ): Long? = occurrences
        .firstOrNull { occurrence -> occurrence.id == occurrenceId }
        ?.arrangementPositionFromSourceMs(sourcePositionMs)
}

internal object ArrangementPlaybackPreparer {
    fun prepare(
        ownerSongId: String,
        audioSourceSongId: String,
        title: String,
        sourceAudioUri: Uri,
        playbackProfile: SmpConfig.PlaybackConfig?,
        assetTimeDomain: ArrangementAssetTimeDomain,
        arrangement: ArrangementData
    ): PreparedArrangementPlayback? {
        if (ownerSongId.isBlank() || audioSourceSongId.isBlank()) return null
        if (arrangement.sourceSongId != audioSourceSongId) return null

        val projection = arrangement.toOccurrenceProjection()
        val preparedOccurrences = prepareArrangementOccurrences(
            segments = projection.segments,
            structureSegmentIds = projection.structureSegmentIds,
            entries = projection.entries,
            useOccurrenceModel = projection.entries.isNotEmpty()
        )
        if (preparedOccurrences.isEmpty()) return null

        val occurrences = preparedOccurrences.map { occurrence ->
            val sourceStartMs = minOf(
                occurrence.segment.startMs,
                occurrence.segment.endMs
            ).coerceAtLeast(0L)
            val sourceEndMs = maxOf(
                occurrence.segment.startMs,
                occurrence.segment.endMs
            ).coerceAtLeast(sourceStartMs + 1L)
            val entryId = occurrence.segment.id
            val mediaId = "$ownerSongId:${occurrence.entryIndex}:${occurrence.repeatIndex}"
            PreparedArrangementPlaybackOccurrence(
                id = ArrangementPlaybackOccurrenceId(
                    ownerSongId = ownerSongId,
                    entryId = entryId,
                    repeatIndex = occurrence.repeatIndex
                ),
                groupKey = ArrangementPlaybackGroupKey(
                    ownerSongId = ownerSongId,
                    entryId = entryId
                ),
                entryId = entryId,
                entryIndex = occurrence.entryIndex,
                repeatIndex = occurrence.repeatIndex,
                repeatCount = occurrence.repeatCount,
                sourceStartMs = sourceStartMs,
                sourceEndMs = sourceEndMs,
                arrangementStartMs = occurrence.arrangementStartMs,
                durationMs = occurrence.durationMs,
                label = occurrence.segment.name,
                color = occurrence.color,
                mediaId = mediaId
            )
        }
        val livePlan = LiveArrangementPlan(
            occurrences = occurrences.map { occurrence ->
                LiveArrangementOccurrence(
                    key = occurrence.mediaId,
                    label = occurrence.label,
                    durationMs = occurrence.durationMs,
                    color = occurrence.color
                )
            }
        )
        val mediaItems = occurrences.map { occurrence ->
            MediaItem.Builder()
                .setUri(sourceAudioUri)
                .setMediaId(occurrence.mediaId)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(occurrence.sourceStartMs)
                        .setEndPositionMs(occurrence.sourceEndMs)
                        .build()
                )
                .build()
        }

        return PreparedArrangementPlayback(
            ownerSongId = ownerSongId,
            audioSourceSongId = audioSourceSongId,
            title = title,
            sourceAudioUri = sourceAudioUri,
            playbackProfile = playbackProfile,
            assetTimeDomain = assetTimeDomain,
            occurrences = occurrences,
            mediaItems = mediaItems,
            livePlan = livePlan,
            navigationItems = preparedOccurrences.toVirtualNavigationItems(ownerSongId)
        )
    }
}

private fun saturatedTimeAdd(left: Long, right: Long): Long =
    if (right > Long.MAX_VALUE - left) Long.MAX_VALUE else left + right
