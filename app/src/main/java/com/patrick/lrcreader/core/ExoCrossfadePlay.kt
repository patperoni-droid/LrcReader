package com.patrick.lrcreader.core

import com.patrick.lrcreader.core.audio.AudioEngine
import com.patrick.lrcreader.core.audio.EmbeddedLyricsListener
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

private var lastEndListener: Player.Listener? = null

@androidx.media3.common.util.UnstableApi
fun exoCrossfadePlay(
    context: Context,
    exoPlayer: ExoPlayer,
    embeddedLyricsListener: EmbeddedLyricsListener,
    uriString: String,
    playlistName: String?,
    playToken: Long,
    getCurrentToken: () -> Long,
    onLyricsLoaded: (String?) -> Unit,
    onStart: () -> Unit,
    onError: () -> Unit,
    onNaturalEnd: () -> Unit = {},
    fadeDurationMs: Long = 1000L
) {
    CoroutineScope(Dispatchers.Main).launch {

        if (getCurrentToken() != playToken) return@launch

        // ✅ Fade-out via AudioEngine (PAS via exoPlayer.volume)
        if (exoPlayer.isPlaying) {
            val steps = 24
            val stepDelay = (fadeDurationMs / steps).coerceAtLeast(1L)
            for (i in 1..steps) {
                val t = i.toFloat() / steps.toFloat()
                val curved = 1f - (t * t)
                AudioEngine.setFadeMultiplier(curved)   // ✅
                delay(stepDelay)
            }
        }

        if (getCurrentToken() != playToken) return@launch

        // reset fade pour repartir propre
        AudioEngine.setFadeMultiplier(1f)

        onLyricsLoaded(null)

        runCatching { embeddedLyricsListener.reset() }
        runCatching { exoPlayer.removeListener(embeddedLyricsListener) }
        exoPlayer.addListener(embeddedLyricsListener)

        lastEndListener?.let { old -> runCatching { exoPlayer.removeListener(old) } }

        val endListener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (getCurrentToken() != playToken) return
                if (state == Player.STATE_ENDED) onNaturalEnd()
            }

            override fun onPlayerError(error: PlaybackException) {
                if (getCurrentToken() != playToken) return
                onError()
            }
        }

        lastEndListener = endListener
        exoPlayer.addListener(endListener)

// reset propre
        runCatching { exoPlayer.stop() }
        runCatching { exoPlayer.clearMediaItems() }

        exoPlayer.setMediaItem(MediaItem.fromUri(uriString))
        exoPlayer.prepare()

// ✅ 1) on réapplique le mix (volume bus/titre/fade)
        AudioEngine.reapplyMixNow()
        AudioEngine.debugVolumeTag("after prepare")

// ✅ 2) on VERROUILLE la formule magique AVANT de jouer
        PlaybackCoordinator.requestStartPlayer()

// ✅ 3) seulement maintenant on lance le son
        exoPlayer.play()

// ✅ 4) UI / état
        onStart()

        val lyrics = embeddedLyricsListener.lyrics
            .filterNotNull()
            .firstOrNull()

        if (getCurrentToken() != playToken) return@launch
        onLyricsLoaded(lyrics)
    }
}
