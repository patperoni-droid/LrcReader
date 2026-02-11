package com.patrick.lrcreader.core.audio

import android.content.Context
import android.util.Log
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.patrick.lrcreader.core.FillerSoundManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.pow

@UnstableApi
object AudioEngine {

    // -----------------------------
    // Time-stretch mode (sécurité)
    // -----------------------------
    enum class TimeStretchMode { EXO, HQ }

    // ✅ Par défaut : comportement actuel (sécurisé, rien ne change tant que tu n'actives pas HQ)
    @Volatile private var timeStretchMode: TimeStretchMode = TimeStretchMode.EXO

    // Dernières valeurs demandées (communes aux 2 modes)
    @Volatile private var currentSpeed: Float = 1f
    @Volatile private var currentPitchRatio: Float = 1f

    /**
     * ✅ API SAFE : change le mode sans casser le reste.
     * Pour l'instant HQ n'est pas branché => ça reste en EXO même si tu forces HQ.
     */
    fun setTimeStretchMode(mode: TimeStretchMode, reason: String = "") {
        val effective = when (mode) {
            TimeStretchMode.EXO -> TimeStretchMode.EXO
            TimeStretchMode.HQ -> {
                // ⚠️ HQ pas encore implémenté (SoundTouch viendra après).
                // Sécurité : on ne casse rien, on log et on reste en EXO.
                Log.w("AUDIO_TS", "HQ demandé mais non disponible => fallback EXO (reason=$reason)")
                TimeStretchMode.EXO
            }
        }
        timeStretchMode = effective
        Log.d("AUDIO_TS", "setTimeStretchMode($reason) requested=$mode effective=$effective")

        // ré-applique les paramètres existants dans le mode effectif
        applySpeedPitchNow(reason = "modeSwitch:$reason")
    }

    fun getTimeStretchMode(): TimeStretchMode = timeStretchMode

    // -----------------------------
    // Player / listeners
    // -----------------------------
    private var exoPlayer: ExoPlayer? = null
    private var embeddedLyricsListener: EmbeddedLyricsListener? = null

    private var onNaturalEndCallback: (() -> Unit)? = null
    private var endedListenerAdded = false

    // -----------------------------
    // Fade-out (Stop/Pause doux)
    // -----------------------------
    private var fadeJob: Job? = null
    private val audioScope = CoroutineScope(Dispatchers.Main.immediate)

    // -----------------------------
    // SPEED / PITCH anti-rafale (évite reconfig en continu)
    // -----------------------------
    private var speedPitchJob: Job? = null
    private var pendingSpeed: Float = 1f
    private var pendingPitch: Float = 1f

    // -----------------------------
    // Mix propre : TrackGain × PlayerBus × Fade
    // -----------------------------
    private var trackGainLinear: Float = 1f
    private var playerBusLevel: Float = 1f
    private var fadeMultiplier: Float = 1f

    // Valeurs en attente si le player n'est pas prêt
    private var pendingTrackGainDb: Int? = null
    private var pendingPlayerBus: Float? = null

    // -----------------------------
    // Conversions / Applis volume
    // -----------------------------
    private fun dbToLinearAttenuation(db: Int): Float {
        if (db >= 0) return 1f
        return (10f.pow(db / 20f)).coerceIn(0f, 1f)
    }

    private fun applyFinalVolume() {
        val p = exoPlayer ?: return

        val v = (trackGainLinear * playerBusLevel * fadeMultiplier).coerceIn(0f, 1f)
        p.volume = v

        Log.d("BUS", "applyFinalVolume exo.volume=$v track=$trackGainLinear bus=$playerBusLevel fade=$fadeMultiplier")
    }

    fun reapplyMixNow() = applyFinalVolume()

    fun debugVolumeTag(tag: String) {
        val p = exoPlayer ?: return
        Log.d("BUS", "$tag exo.volume=${p.volume} track=$trackGainLinear bus=$playerBusLevel fade=$fadeMultiplier")
    }

    fun setFadeMultiplier(value: Float) {
        fadeMultiplier = value.coerceIn(0f, 1f)
        applyFinalVolume()
    }

    fun setPlayerBusLevel(level: Float) {
        val safe = level.coerceIn(0f, 1f)

        if (exoPlayer == null) {
            pendingPlayerBus = safe
            Log.d("BUS", "setPlayerBusLevel PENDING=$safe (exoPlayer=null)")
            return
        }

        playerBusLevel = safe
        Log.d("BUS", "setPlayerBusLevel ACTIVE=$safe (exoPlayer!=null)")
        applyFinalVolume()
    }

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
    // SPEED / PITCH (API publique)
    // -----------------------------
    fun setSpeedPitch(speed: Float, pitch: Float, reason: String = "") {
        val p = exoPlayer ?: run {
            Log.w("AUDIO_PATH", "setSpeedPitch ignored (exoPlayer=null) reason=$reason")
            // On mémorise quand même, pour appliquer au prochain getPlayer()
            currentSpeed = speed.coerceIn(0.5f, 2.0f)
            currentPitchRatio = pitch.coerceIn(0.5f, 2.0f)
            return
        }

        val s = speed.coerceIn(0.5f, 2.0f)
        val pi = pitch.coerceIn(0.5f, 2.0f)

        // Mémorise la "vérité" des paramètres (communs aux modes)
        currentSpeed = s
        currentPitchRatio = pi

        // Anti-rafale : on coalesce
        pendingSpeed = s
        pendingPitch = pi

        speedPitchJob?.cancel()
        speedPitchJob = audioScope.launch {
            delay(60L)

            val finalS = pendingSpeed
            val finalP = pendingPitch

            // Evite de spammer si identique
            val before = p.playbackParameters
            val sameSpeed = abs(before.speed - finalS) < 0.0005f
            val samePitch = abs(before.pitch - finalP) < 0.0005f
            if (sameSpeed && samePitch) return@launch

            applySpeedPitchNow(finalS, finalP, reason = reason)
        }
    }

    // -----------------------------
    // SPEED / PITCH (impl interne, selon mode)
    // -----------------------------
    private fun applySpeedPitchNow(
        speed: Float = currentSpeed,
        pitch: Float = currentPitchRatio,
        reason: String = ""
    ) {
        val p = exoPlayer ?: return

        val s = speed.coerceIn(0.5f, 2.0f)
        val pi = pitch.coerceIn(0.5f, 2.0f)

        when (timeStretchMode) {
            TimeStretchMode.EXO -> {
                val before = p.playbackParameters
                p.playbackParameters = PlaybackParameters(s, pi)
                val after = p.playbackParameters
                Log.d("AUDIO_TS", "apply(EXO,$reason) before=$before after=$after")
            }
            TimeStretchMode.HQ -> {
                // ⚠️ Pas encore branché => on ne fait rien ici.
                // La sécurité est gérée par setTimeStretchMode() qui ne laisse pas passer HQ tant qu'il n'existe pas.
                Log.w("AUDIO_TS", "apply(HQ,$reason) called but HQ not available -> should not happen")
            }
        }
    }

    // -----------------------------
    // FADE OUT
    // -----------------------------
    private fun exoFadeOutThen(durationMs: Long = 600L, endAction: (ExoPlayer) -> Unit) {
        val p = exoPlayer ?: return

        fadeJob?.cancel()
        fadeJob = audioScope.launch {
            val steps = 24
            val startFade = fadeMultiplier.coerceIn(0f, 1f)
            val stepDelay = (durationMs / steps).coerceAtLeast(1L)

            for (i in 1..steps) {
                val t = i.toFloat() / steps.toFloat()
                fadeMultiplier = (startFade * (1f - t)).coerceIn(0f, 1f)
                applyFinalVolume()
                delay(stepDelay)
            }

            endAction(p)

            fadeMultiplier = 1f
            applyFinalVolume()
        }
    }

    fun pause(durationMs: Long = 600L) {
        exoFadeOutThen(durationMs) { it.pause() }
    }

    fun stop(durationMs: Long = 600L) {
        exoFadeOutThen(durationMs) {
            it.pause()
            it.seekTo(0)
        }
    }

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
        onNaturalEndCallback = onNaturalEnd

        val p = exoPlayer ?: run {
            val renderersFactory = androidx.media3.exoplayer.DefaultRenderersFactory(appCtx)
                .setEnableAudioFloatOutput(false)
                .setEnableAudioTrackPlaybackParams(false)

            ExoPlayer.Builder(appCtx, renderersFactory)
                .build()
                .also { player ->
                    val l = EmbeddedLyricsListener()
                    player.addListener(l)
                    embeddedLyricsListener = l
                    exoPlayer = player
                }
        }

        // Offload désactivé
        p.trackSelectionParameters =
            p.trackSelectionParameters
                .buildUpon()
                .setAudioOffloadPreferences(
                    TrackSelectionParameters.AudioOffloadPreferences.Builder()
                        .setAudioOffloadMode(
                            TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
                        )
                        .build()
                )
                .build()

        // Appliquer mix + paramètres mémorisés
        pendingPlayerBus?.let { playerBusLevel = it.coerceIn(0f, 1f) }
        pendingTrackGainDb?.let { trackGainLinear = dbToLinearAttenuation(it.coerceIn(-12, 0)) }
        fadeMultiplier = 1f
        applyFinalVolume()

        // ✅ Réapplique speed/pitch (une fois) au moment où le player est prêt
        applySpeedPitchNow(reason = "getPlayerInit")

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

        speedPitchJob?.cancel()
        speedPitchJob = null

        pendingTrackGainDb = null
        pendingPlayerBus = null

        exoPlayer?.release()
        exoPlayer = null
        embeddedLyricsListener = null
        onNaturalEndCallback = null
        endedListenerAdded = false
    }
}