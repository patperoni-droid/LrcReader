package com.patrick.lrcreader.core.audio

import android.content.Context
import android.media.audiofx.LoudnessEnhancer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.patrick.lrcreader.core.FillerSoundManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.pow

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

    // -----------------------------
    // Niveau du titre (gain par piste)
    // -----------------------------
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var pendingGainDb: Int? = null

    private fun ensureLoudnessEnhancerReady(player: ExoPlayer) {
        val sessionId = player.audioSessionId
        if (sessionId == 0) return // pas prêt

        val existing = loudnessEnhancer
        if (existing == null) {
            loudnessEnhancer = LoudnessEnhancer(sessionId).apply { enabled = true }
        }
    }

    private fun dbToLinear(db: Int): Float {
        // db -> amplitude, ex: -6 dB ~ 0.501
        return (10.0.pow(db / 20.0)).toFloat()
    }

    /**
     * ✅ À appeler quand tu changes le slider "Niveau du titre"
     * - db < 0 : baisse via exoPlayer.volume
     * - db > 0 : boost via LoudnessEnhancer (ExoPlayer.volume ne peut pas amplifier > 1f)
     */
    fun applyTrackGainDb(gainDb: Int) {
        val player = exoPlayer ?: run {
            pendingGainDb = gainDb
            return
        }

        // audioSessionId pas prêt => on garde en attente
        if (player.audioSessionId == 0) {
            pendingGainDb = gainDb
            return
        }

        ensureLoudnessEnhancerReady(player)

        // ✅ Réglages de sécurité
        val minDb = -12
        val maxDb = 12
        val safeDb = gainDb.coerceIn(minDb, maxDb)

        // ✅ Headroom anti-clipping :
        // Quand on boost, on baisse le volume Exo avant (marge),
        // puis on remonte avec LoudnessEnhancer.
        // Net = gain demandé, mais avec de l'air pour éviter la saturation immédiate.
        val headroomDb = 6 // si tu veux encore plus "anti-saturation", mets 9

        if (safeDb <= 0) {
            // Atténuation simple
            runCatching { loudnessEnhancer?.setTargetGain(0) }
            val v = dbToLinear(safeDb).coerceIn(0f, 1f)
            player.volume = v
            normalVolume = v
        } else {
            // Boost avec marge
            val preAtten = -headroomDb
            val v = dbToLinear(preAtten).coerceIn(0f, 1f)
            player.volume = v
            normalVolume = v

            val enhancerMb = ((safeDb + headroomDb) * 1000).coerceIn(0, 24000) // 24 dB max côté enhancer
            runCatching { loudnessEnhancer?.setTargetGain(enhancerMb) }
        }

        pendingGainDb = null
    }



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

            // Restore volume for next play (et donc pour le niveau du titre)
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

            // ✅ Applique un gain en attente dès que possible
            pendingGainDb?.let { applyTrackGainDb(it) }
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

        runCatching { loudnessEnhancer?.release() }
        loudnessEnhancer = null
        pendingGainDb = null

        exoPlayer?.release()
        exoPlayer = null
        embeddedLyricsListener = null
        onNaturalEndCallback = null
        endedListenerAdded = false
    }
}
