package com.patrick.lrcreader.core.audio

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.patrick.lrcreader.core.FillerSoundManager

object AudioEngine {

    private var exoPlayer: ExoPlayer? = null
    private var embeddedLyricsListener: EmbeddedLyricsListener? = null

    // ✅ callback remplaçable (évite le piège du "capturé une seule fois")
    private var onNaturalEndCallback: (() -> Unit)? = null
    private var endedListenerAdded = false

    fun getPlayer(context: Context, onNaturalEnd: () -> Unit): ExoPlayer {
        val appCtx = context.applicationContext

        // met à jour le callback à chaque appel (MainActivity peut changer)
        onNaturalEndCallback = onNaturalEnd

        val p = exoPlayer ?: ExoPlayer.Builder(appCtx).build().also { player ->
            val l = EmbeddedLyricsListener()
            player.addListener(l)
            embeddedLyricsListener = l
            exoPlayer = player
        }

        // Ajout du listener de fin UNE SEULE FOIS
        if (!endedListenerAdded) {
            endedListenerAdded = true
            p.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) {
                        // callback actuel (toujours à jour)
                        onNaturalEndCallback?.invoke()
                        FillerSoundManager.startIfConfigured(appCtx)
                    }
                }
            })
        }

        return p
    }

    fun getLyricsListener(): EmbeddedLyricsListener {
        return embeddedLyricsListener
            ?: error("AudioEngine not initialized. Call getPlayer(context, ...) first.")
    }

    fun release() {
        exoPlayer?.release()
        exoPlayer = null
        embeddedLyricsListener = null
        onNaturalEndCallback = null
        endedListenerAdded = false
    }
}
