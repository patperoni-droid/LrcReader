package com.patrick.lrcreader.smp

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class PreparedVirtualArrangementPlayback internal constructor(
    private val preparedPlayback: PreparedArrangementPlayback
) {
    val variantSongId: String = preparedPlayback.ownerSongId
    val title: String = preparedPlayback.title
    val sourceSongId: String = preparedPlayback.audioSourceSongId
    val sourceAudioUri: String = preparedPlayback.sourceAudioUri.toString()
    val playbackProfile: SmpConfig.PlaybackConfig? = preparedPlayback.playbackProfile
    val mediaItems: List<MediaItem> = preparedPlayback.mediaItems
    val livePlan: LiveArrangementPlan = preparedPlayback.livePlan
    val navigationItems: List<ArrangementNavigationItem> = preparedPlayback.navigationItems
    val occurrenceDurationsMs: List<Long> = preparedPlayback.occurrenceDurationsMs
    val durationMs: Long = preparedPlayback.durationMs
    internal val occurrences: List<PreparedArrangementPlaybackOccurrence> =
        preparedPlayback.occurrences
    internal val assetTimeDomain: ArrangementAssetTimeDomain = preparedPlayback.assetTimeDomain
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

        val sourceUri = Uri.fromFile(sourceAudioFile)
        prepareVirtualArrangementPlayback(
            variantSongId = variantSong.id,
            title = variantSong.title,
            sourceSongId = sourceSongId,
            sourceAudioUri = sourceUri,
            playbackProfile = SmpVariantPlayback.resolveProfile(
                context = context,
                variant = variantSong,
                parent = sourceSong
            ),
            arrangement = arrangement
        )
    }
}

internal fun prepareVirtualArrangementPlayback(
    variantSongId: String,
    title: String,
    sourceSongId: String,
    sourceAudioUri: Uri,
    playbackProfile: SmpConfig.PlaybackConfig?,
    arrangement: ArrangementData
): PreparedVirtualArrangementPlayback? = ArrangementPlaybackPreparer.prepare(
    ownerSongId = variantSongId,
    audioSourceSongId = sourceSongId,
    title = title,
    sourceAudioUri = sourceAudioUri,
    playbackProfile = playbackProfile,
    assetTimeDomain = ArrangementAssetTimeDomain.ARRANGEMENT,
    arrangement = arrangement
)?.let(::PreparedVirtualArrangementPlayback)
