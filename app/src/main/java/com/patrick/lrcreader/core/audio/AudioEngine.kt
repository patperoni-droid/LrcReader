package com.patrick.lrcreader.core.audio

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.patrick.lrcreader.core.FillerSoundManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@UnstableApi
object AudioEngine {

    private var exoPlayer: ExoPlayer? = null
    private var embeddedLyricsListener: EmbeddedLyricsListener? = null

    // ✅ callback remplaçable (évite le piège du "capturé une seule fois")
    private var onNaturalEndCallback: (() -> Unit)? = null
    private var endedListenerAdded = false

    // -----------------------------
    // Fade-out (Stop/Pause doux)
    // -----------------------------
    private var fadeJob: Job? = null
    private var normalVolume: Float = 1f
    private val audioScope = CoroutineScope(Dispatchers.Main.immediate)

    private fun exoFadeOutThen(
        durationMs: Long = 250L,
        endAction: (ExoPlayer) -> Unit
    ) {
        val p = exoPlayer ?: return

        fadeJob?.cancel()
        fadeJob = audioScope.launch {
            val steps = 12
            val start = p.volume.coerceIn(0f, 1f)
            if (start > 0f) normalVolume = start

            val stepDelay = (durationMs / steps).coerceAtLeast(1L)
            for (i in 1..steps) {
                val t = i.toFloat() / steps.toFloat()
                val v = (start * (1f - t)).coerceIn(0f, 1f)
                p.volume = v
                delay(stepDelay)
            }

            endAction(p)

            // Restore volume for next play
            p.volume = normalVolume
        }
    }

    /**
     * ✅ PAUSE DOUX (fade-out)
     * IMPORTANT: j’ai volontairement gardé le nom "pause()"
     * pour que ton UI existant continue de marcher sans modification.
     */
    fun pause(durationMs: Long = 250L) {
        exoFadeOutThen(durationMs) { player ->
            player.pause()
        }
    }

    /**
     * ✅ STOP DOUX (fade-out -> pause -> retour au début)
     * IMPORTANT: j’ai volontairement gardé le nom "stop()"
     * pour que ton UI existant continue de marcher sans modification.
     */
    fun stop(durationMs: Long = 250L) {
        exoFadeOutThen(durationMs) { player ->
            player.pause()
            player.seekTo(0)
        }
    }

    /**
     * Si tu veux un stop "sec" (debug / cas spécial)
     */
    fun stopImmediate() {
        fadeJob?.cancel()
        exoPlayer?.pause()
        exoPlayer?.seekTo(0)
    }

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
        fadeJob?.cancel()
        fadeJob = null

        exoPlayer?.release()
        exoPlayer = null
        embeddedLyricsListener = null
        onNaturalEndCallback = null
        endedListenerAdded = false
    }
}
