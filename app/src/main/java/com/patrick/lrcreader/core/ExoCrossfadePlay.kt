package com.patrick.lrcreader.core.audio

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull

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
    fadeDurationMs: Long = 500
) {
    // 1) On efface les paroles à l’écran au démarrage d’un nouveau titre
    onLyricsLoaded(null)

    // 2) Reset listener paroles embedded + éviter empilement de listeners
    runCatching { embeddedLyricsListener.reset() }
    runCatching { exoPlayer.removeListener(embeddedLyricsListener) }
    exoPlayer.addListener(embeddedLyricsListener)

    // 3) Listener fin/erreur (on le recrée à chaque lecture)
    val endListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            if (getCurrentToken() != playToken) return
            if (state == Player.STATE_ENDED) {
                onNaturalEnd()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            if (getCurrentToken() != playToken) return
            onError()
        }
    }

    runCatching { exoPlayer.clearMediaItems() }
    exoPlayer.addListener(endListener)

    // 4) Charger le titre
    val item = MediaItem.fromUri(uriString)
    exoPlayer.setMediaItem(item)
    exoPlayer.prepare()
    exoPlayer.play()

    // 5) Démarrage OK
    onStart()

    // 6) Récupérer les paroles embedded quand elles arrivent (USLT)
    CoroutineScope(Dispatchers.Main).launch {
        val lyrics = embeddedLyricsListener.lyrics
            .filterNotNull()
            .firstOrNull()

        if (getCurrentToken() != playToken) return@launch
        onLyricsLoaded(lyrics)
    }
}
