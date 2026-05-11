package com.patrick.lrcreader.core.audio

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlin.math.pow

class ArrangementPreviewPlayer(
    context: Context
) {
    private val player = ExoPlayer.Builder(context.applicationContext).build().apply {
        playWhenReady = false
    }

    fun setTrackGainDb(gainDb: Float) {
        val safeDb = gainDb.coerceIn(-12f, 0f)
        val volumeLinear = if (safeDb >= 0f) {
            1f
        } else {
            10f.pow(safeDb / 20f).coerceIn(0f, 1f)
        }
        player.volume = volumeLinear
    }

    fun playLoop(sourceUri: Uri, startMs: Long, endMs: Long) {
        val loopStartMs = startMs.coerceAtLeast(0L)
        val loopEndMs = endMs.coerceAtLeast(loopStartMs + 1L)
        val mediaItem = MediaItem.Builder()
            .setUri(sourceUri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(loopStartMs)
                    .setEndPositionMs(loopEndMs)
                    .build()
            )
            .build()

        player.pause()
        player.clearMediaItems()
        player.repeatMode = Player.REPEAT_MODE_ONE
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun currentPositionMs(): Long = player.currentPosition.coerceAtLeast(0L)

    fun stop() {
        player.pause()
        player.stop()
        player.clearMediaItems()
        player.repeatMode = Player.REPEAT_MODE_OFF
    }

    fun release() {
        stop()
        player.release()
    }

    fun isPlaying(): Boolean = player.isPlaying
}
