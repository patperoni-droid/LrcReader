package com.patrick.lrcreader.core.audio

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

// ✅ Pour éviter d'empiler des listeners de fin/erreur à chaque lecture
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
    fadeDurationMs: Long = 1000
) {
    // ⚠️ Tout est asynchrone pour pouvoir faire un fade + attendre
    CoroutineScope(Dispatchers.Main).launch {

        // Si une autre demande est arrivée entre-temps → on annule proprement
        if (getCurrentToken() != playToken) return@launch

        // ✅ On mémorise le volume AVANT fade (c’est lui qui doit porter le "niveau du titre")
        var restoreVolume = exoPlayer.volume.coerceIn(0f, 1f)

        // 🔥 Fade-out du titre en cours SANS pause
        if (exoPlayer.isPlaying) {
            val steps = 24
            val startVol = restoreVolume
            val stepDelay = (fadeDurationMs / steps).coerceAtLeast(1L)

            for (i in 1..steps) {
                val t = i.toFloat() / steps.toFloat()
                val curved = 1f - (t * t) // plus naturel à l’oreille
                exoPlayer.volume = (startVol * curved).coerceIn(0f, 1f)
                delay(stepDelay)
            }
        }

        // Si une autre demande est arrivée pendant le fade → on annule
        if (getCurrentToken() != playToken) return@launch

        // 1) On efface les paroles à l’écran au démarrage d’un nouveau titre
        onLyricsLoaded(null)

        // 2) Reset listener paroles embedded + éviter empilement de listeners
        runCatching { embeddedLyricsListener.reset() }
        runCatching { exoPlayer.removeListener(embeddedLyricsListener) }
        exoPlayer.addListener(embeddedLyricsListener)

        // 3) Listener fin/erreur (on remplace l'ancien)
        lastEndListener?.let { old -> runCatching { exoPlayer.removeListener(old) } }

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

        lastEndListener = endListener
        exoPlayer.addListener(endListener)

        // 4) Charger le titre
        runCatching { exoPlayer.clearMediaItems() }
        val item = MediaItem.fromUri(uriString)
        exoPlayer.setMediaItem(item)
        exoPlayer.prepare()

        // ✅ IMPORTANT : on restaure le volume d'avant (donc ton niveau du titre),
        // au lieu de forcer 1f (sinon le gain par titre ne marche jamais).
        exoPlayer.volume = restoreVolume

        exoPlayer.play()

        // 5) Démarrage OK
        onStart()

        // 6) Récupérer les paroles embedded quand elles arrivent (USLT)
        val lyrics = embeddedLyricsListener.lyrics
            .filterNotNull()
            .firstOrNull()

        if (getCurrentToken() != playToken) return@launch
        onLyricsLoaded(lyrics)
    }
}
