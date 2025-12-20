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
    private val audioScope = CoroutineScope(Dispatchers.Main.immediate)

    // -----------------------------
    // Mix propre : TrackGain × PlayerBus × Fade
    // -----------------------------
    private var trackGainLinear: Float = 1f          // gain par titre (0..1)
    private var playerBusLevel: Float = 1f           // bus principal lecteur (0..1)
    private var fadeMultiplier: Float = 1f           // multiplicateur temporaire de fade (0..1)

    // Valeurs en attente si le player n'est pas prêt
    private var pendingTrackGainDb: Int? = null
    private var pendingPlayerBus: Float? = null

    // -----------------------------
    // Conversions / Applis volume
    // -----------------------------

    private fun dbToLinearAttenuation(db: Int): Float {
        // db négatif -> amplitude, ex: -6 dB ~ 0.501
        // IMPORTANT : on ne booste jamais (>0 dB interdit ici)
        if (db >= 0) return 1f
        return (10f.pow(db / 20f)).coerceIn(0f, 1f)
    }

    private fun applyFinalVolume() {
        val p = exoPlayer ?: return

        val v = (trackGainLinear * playerBusLevel * fadeMultiplier)
            .coerceIn(0f, 1f)

        p.volume = v

        android.util.Log.d(
            "BUS",
            "applyFinalVolume exo.volume=$v track=$trackGainLinear bus=$playerBusLevel fade=$fadeMultiplier"
        )
    }

    /** 🔁 À appeler quand Exo remet le volume à sa sauce (prepare / setMediaItem / etc.) */
    fun reapplyMixNow() {
        applyFinalVolume()
    }
    fun debugVolumeTag(tag: String) {
        val p = exoPlayer ?: return
        android.util.Log.d("BUS", "$tag exo.volume=${p.volume} track=$trackGainLinear bus=$playerBusLevel fade=$fadeMultiplier")
    }

    // ✅ Pour le crossfade : on fade via le mix, pas en écrivant exoPlayer.volume ailleurs
    fun setFadeMultiplier(value: Float) {
        fadeMultiplier = value.coerceIn(0f, 1f)
        applyFinalVolume()
    }
    // -----------------------------
    // BUS PRINCIPAL LECTEUR
    // -----------------------------

    /**
     * 🎚️ Bus principal du lecteur (0..1).
     * À appeler depuis GlobalMixScreen (slider "Player").
     */
    fun setPlayerBusLevel(level: Float) {
        val safe = level.coerceIn(0f, 1f)

        if (exoPlayer == null) {
            pendingPlayerBus = safe
            android.util.Log.d("BUS", "setPlayerBusLevel PENDING=$safe (exoPlayer=null)")
            return
        }

        playerBusLevel = safe
        android.util.Log.d("BUS", "setPlayerBusLevel ACTIVE=$safe (exoPlayer!=null)")

        applyFinalVolume()
    }

    // -----------------------------
    // NIVEAU DU TITRE (gain par piste)
    // -----------------------------

    /**
     * ✅ À appeler quand tu changes le slider "Niveau du titre"
     * Règle MUSICIENNE :
     * - on autorise uniquement [-12 dB .. 0 dB]
     * - aucun boost, donc pas de saturation/compression dégueu
     */
    fun applyTrackGainDb(gainDb: Int) {
        val p = exoPlayer
        val safeDb = gainDb.coerceIn(-12, 0)

        if (p == null) {
            pendingTrackGainDb = safeDb
            return
        }

        trackGainLinear = dbToLinearAttenuation(safeDb)
        applyFinalVolume()

        pendingTrackGainDb = null
    }

    // -----------------------------
    // FADE OUT (propre, sans casser le mix)
    // -----------------------------

    private fun exoFadeOutThen(
        durationMs: Long = 600L,
        endAction: (ExoPlayer) -> Unit
    ) {
        val p = exoPlayer ?: return

        fadeJob?.cancel()
        fadeJob = audioScope.launch {
            val steps = 24
            val startFade = fadeMultiplier.coerceIn(0f, 1f)
            val stepDelay = (durationMs / steps).coerceAtLeast(1L)

            // Descente progressive du multiplicateur de fade
            for (i in 1..steps) {
                val t = i.toFloat() / steps.toFloat()
                fadeMultiplier = (startFade * (1f - t)).coerceIn(0f, 1f)
                applyFinalVolume()
                delay(stepDelay)
            }

            endAction(p)

            // Restore fade pour la lecture suivante
            fadeMultiplier = 1f
            applyFinalVolume()
        }
    }

    /**
     * ✅ PAUSE DOUX (fade-out)
     * IMPORTANT: nom conservé pour que ton UI ne change pas.
     */
    fun pause(durationMs: Long = 600L) {
        exoFadeOutThen(durationMs) { player ->
            player.pause()
        }
    }

    /**
     * ✅ STOP DOUX (fade-out -> pause -> retour au début)
     * IMPORTANT: nom conservé pour que ton UI ne change pas.
     */
    fun stop(durationMs: Long = 600L) {
        exoFadeOutThen(durationMs) { player ->
            player.pause()
            player.seekTo(0)
        }
    }

    /**
     * Stop "sec" (debug / cas spécial)
     */
    fun stopImmediate() {
        fadeJob?.cancel()
        fadeMultiplier = 1f
        exoPlayer?.pause()
        exoPlayer?.seekTo(0)
        applyFinalVolume()
    }

    // -----------------------------
    // EXOPLAYER (singleton)
    // -----------------------------

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

        // Appliquer les valeurs en attente (gain/ bus) + volume final
        pendingPlayerBus?.let { playerBusLevel = it.coerceIn(0f, 1f) }
        pendingTrackGainDb?.let { trackGainLinear = dbToLinearAttenuation(it.coerceIn(-12, 0)) }
        fadeMultiplier = 1f
        applyFinalVolume()



        // Ajout du listener de fin UNE SEULE FOIS
        if (!endedListenerAdded) {
            endedListenerAdded = true
            p.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) {
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

        pendingTrackGainDb = null
        pendingPlayerBus = null

        exoPlayer?.release()
        exoPlayer = null
        embeddedLyricsListener = null
        onNaturalEndCallback = null
        endedListenerAdded = false
    }
}
