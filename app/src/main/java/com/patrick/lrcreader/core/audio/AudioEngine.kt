package com.patrick.lrcreader.core.audio

import android.content.Context
import android.util.Log
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.patrick.lrcreader.exo.BuildConfig
import com.patrick.lrcreader.core.FillerSoundManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow

@UnstableApi
object AudioEngine {

    private const val TS_TAG = "AUDIO_TS"
    private const val PLAYER_SMOKE_TAG = "PLAYER_SMOKE"

    // -----------------------------
    // Time-stretch mode (sécurité)
    // -----------------------------
    enum class TimeStretchMode { EXO, HQ }
    private enum class PlayerPipeline { PURE_EXO, CUSTOM_ST_SINK }

    private val soundTouchProcessor = SoundTouchAudioProcessor()
    @Volatile private var timeStretchMode: TimeStretchMode = initialTimeStretchMode()
    @Volatile private var hqApplyPending = timeStretchMode == TimeStretchMode.HQ
    @Volatile private var activePlayerPipeline: PlayerPipeline? = null
    @Volatile private var playerAppContext: Context? = null
    private val _playerEpoch = MutableStateFlow(0)
    val playerEpoch: StateFlow<Int> = _playerEpoch.asStateFlow()

    // Dernières valeurs demandées (communes aux 2 modes)
    @Volatile private var currentSpeed: Float = 1f
    @Volatile private var currentPitchRatio: Float = 1f

    init {
        soundTouchProcessor.setEnabled(timeStretchMode == TimeStretchMode.HQ)
    }

    private fun initialTimeStretchMode(): TimeStretchMode {
        return if (SoundTouchBridge.isAvailable()) {
            TimeStretchMode.HQ
        } else {
            Log.w(TS_TAG, "HQ unavailable")
            TimeStretchMode.EXO
        }
    }

    fun setTimeStretchMode(mode: TimeStretchMode, reason: String = "") {
        val hqAvailable = SoundTouchBridge.isAvailable()
        val effective = when (mode) {
            TimeStretchMode.HQ -> {
                if (hqAvailable) {
                    TimeStretchMode.HQ
                } else {
                    Log.w(TS_TAG, "HQ unavailable")
                    TimeStretchMode.EXO
                }
            }
            TimeStretchMode.EXO -> TimeStretchMode.EXO
        }

        if (effective != mode) {
            Log.d(TS_TAG, "HQ_ONLY forced=$effective requested=$mode reason=$reason")
        }

        timeStretchMode = effective
        hqApplyPending = effective == TimeStretchMode.HQ
        soundTouchProcessor.setEnabled(effective == TimeStretchMode.HQ)
        Log.d(TS_TAG, "setTimeStretchMode requested=$mode effective=$effective reason=$reason")

        applySpeedPitchNow(reason = "modeSwitch:$reason")
    }

    fun getTimeStretchMode(): TimeStretchMode = timeStretchMode

    private fun resolveDesiredPlayerPipeline(
        speed: Float = currentSpeed,
        pitch: Float = currentPitchRatio
    ): PlayerPipeline {
        val s = speed.coerceIn(0.5f, 2.0f)
        val pi = pitch.coerceIn(0.5f, 2.0f)
        val isNeutral = abs(s - 1f) < 0.0005f && abs(pi - 1f) < 0.0005f
        if (isNeutral) return PlayerPipeline.PURE_EXO
        return if (timeStretchMode == TimeStretchMode.HQ) {
            PlayerPipeline.CUSTOM_ST_SINK
        } else {
            PlayerPipeline.PURE_EXO
        }
    }

    private fun publishPlayerEpoch() {
        _playerEpoch.value = _playerEpoch.value + 1
    }

    private fun buildPlayer(appCtx: Context, pipeline: PlayerPipeline): ExoPlayer {
        val player = when (pipeline) {
            PlayerPipeline.PURE_EXO -> {
                ExoPlayer.Builder(appCtx).build()
            }
            PlayerPipeline.CUSTOM_ST_SINK -> {
                val renderersFactory = object : DefaultRenderersFactory(appCtx) {
                    override fun buildAudioSink(
                        context: Context,
                        enableFloatOutput: Boolean,
                        enableAudioTrackPlaybackParams: Boolean
                    ): AudioSink {
                        return DefaultAudioSink.Builder(context)
                            .setAudioProcessors(arrayOf(soundTouchProcessor))
                            .setEnableFloatOutput(enableFloatOutput)
                            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                            .build()
                    }
                }
                    .setEnableAudioFloatOutput(false)
                    .setEnableAudioTrackPlaybackParams(false)

                ExoPlayer.Builder(appCtx, renderersFactory).build()
            }
        }

        val l = EmbeddedLyricsListener()
        player.addListener(l)
        embeddedLyricsListener = l
        exoPlayer = player
        activePlayerPipeline = pipeline
        endedListenerAdded = false
        smokeLastPlaybackState = Player.STATE_IDLE
        smokeLastIsPlaying = false

        if (BuildConfig.DEBUG) {
            Log.d(PLAYER_SMOKE_TAG, "BOOT")
        }

        return player
    }

    private data class PlayerSnapshot(
        val mediaUri: String?,
        val positionMs: Long,
        val playWhenReady: Boolean
    )

    private fun snapshotPlayer(player: ExoPlayer): PlayerSnapshot {
        return PlayerSnapshot(
            mediaUri = player.currentMediaItem?.localConfiguration?.uri?.toString(),
            positionMs = runCatching { player.currentPosition }.getOrDefault(0L),
            playWhenReady = runCatching { player.playWhenReady || player.isPlaying }.getOrDefault(false)
        )
    }

    private fun armHqAfterRebuildIfNeeded(reason: String) {
        val s = currentSpeed.coerceIn(0.5f, 2.0f)
        val pi = currentPitchRatio.coerceIn(0.5f, 2.0f)
        val isNeutral = abs(s - 1f) < 0.0005f && abs(pi - 1f) < 0.0005f
        if (timeStretchMode != TimeStretchMode.HQ || isNeutral) {
            hqApplyPending = false
            return
        }

        val pitchSemi = ratioToSemitones(pi)
        hqApplyPending = true
        soundTouchProcessor.setEnabled(true)
        soundTouchProcessor.setTempoRatioAndPitchSemi(
            tempoRatio = s,
            pitchSemi = pitchSemi
        )
        Log.i(
            TS_TAG,
            "hq arm after rebuild speed=$s pitch=$pi pitchSemi=$pitchSemi reason=$reason"
        )
    }

    private fun recreatePlayerForPipeline(targetPipeline: PlayerPipeline, reason: String): ExoPlayer? {
        val appCtx = playerAppContext ?: return null
        val current = exoPlayer ?: return null
        if (activePlayerPipeline == targetPipeline) return current

        val snapshot = snapshotPlayer(current)

        runCatching { current.stop() }
        runCatching { current.clearMediaItems() }
        runCatching { current.release() }

        exoPlayer = null
        embeddedLyricsListener = null
        endedListenerAdded = false

        val rebuilt = buildPlayer(appCtx, targetPipeline)
        ensureCorePlayerListener(rebuilt, appCtx)
        Log.i(TS_TAG, "pipeline switch -> $targetPipeline reason=$reason")

        if (targetPipeline == PlayerPipeline.CUSTOM_ST_SINK) {
            // Arm HQ before prepare/play so READY/TRACKS callbacks can latch
            // the requested tempo/pitch on the rebuilt custom pipeline.
            armHqAfterRebuildIfNeeded(reason = "rebuild:$reason")
        }

        snapshot.mediaUri?.let { uriString ->
            rebuilt.setMediaItem(androidx.media3.common.MediaItem.fromUri(uriString))
            rebuilt.prepare()
            if (snapshot.positionMs > 0L) {
                rebuilt.seekTo(snapshot.positionMs)
            }
            if (snapshot.playWhenReady) {
                rebuilt.playWhenReady = true
                rebuilt.play()
            }
        }

        publishPlayerEpoch()
        return rebuilt
    }

    private fun ensureCorePlayerListener(player: ExoPlayer, appCtx: Context) {
        if (endedListenerAdded) return
        endedListenerAdded = true
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (BuildConfig.DEBUG &&
                    state == Player.STATE_READY &&
                    smokeLastPlaybackState != Player.STATE_READY
                ) {
                    Log.d(PLAYER_SMOKE_TAG, "READY")
                }
                smokeLastPlaybackState = state
                if (state == Player.STATE_READY) {
                    retryPendingHqApply(reason = "listener:STATE_READY")
                }
                if (state == Player.STATE_ENDED) {
                    onNaturalEndCallback?.invoke()
                    FillerSoundManager.startIfConfigured(appCtx)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (BuildConfig.DEBUG && isPlaying && !smokeLastIsPlaying) {
                    Log.d(PLAYER_SMOKE_TAG, "PLAYING")
                }
                smokeLastIsPlaying = isPlaying
            }

            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                retryPendingHqApply(reason = "listener:onTracksChanged")
            }
        })
    }

    // -----------------------------
    // Player / listeners
    // -----------------------------
    private var exoPlayer: ExoPlayer? = null
    private var embeddedLyricsListener: EmbeddedLyricsListener? = null

    private var onNaturalEndCallback: (() -> Unit)? = null
    private var endedListenerAdded = false
    private var smokeLastPlaybackState: Int = Player.STATE_IDLE
    private var smokeLastIsPlaying: Boolean = false

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

            if (timeStretchMode == TimeStretchMode.EXO) {
                // Evite de spammer si identique (uniquement pertinent en EXO).
                val before = p.playbackParameters
                val sameSpeed = abs(before.speed - finalS) < 0.0005f
                val samePitch = abs(before.pitch - finalP) < 0.0005f
                if (sameSpeed && samePitch) return@launch
            }

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
        val s = speed.coerceIn(0.5f, 2.0f)
        val pi = pitch.coerceIn(0.5f, 2.0f)
        val isNeutral = abs(s - 1f) < 0.0005f && abs(pi - 1f) < 0.0005f
        val desiredPipeline = resolveDesiredPlayerPipeline(speed = s, pitch = pi)
        val p = when {
            exoPlayer == null -> return
            activePlayerPipeline != desiredPipeline -> recreatePlayerForPipeline(desiredPipeline, reason) ?: return
            else -> exoPlayer ?: return
        }
        val pipeline = activePlayerPipeline ?: desiredPipeline

        if (isNeutral) {
            hqApplyPending = false
            soundTouchProcessor.setEnabled(false)

            val before = p.playbackParameters
            if (abs(before.speed - 1f) > 0.0005f || abs(before.pitch - 1f) > 0.0005f) {
                p.playbackParameters = PlaybackParameters(1f, 1f)
            }

            Log.i(TS_TAG, "path=NEUTRAL_BYPASS pipeline=$pipeline mode=$timeStretchMode reason=$reason")
            return
        }

        if (pipeline == PlayerPipeline.PURE_EXO) {
            hqApplyPending = false
            soundTouchProcessor.setEnabled(false)

            val before = p.playbackParameters
            val sameSpeed = abs(before.speed - s) < 0.0005f
            val samePitch = abs(before.pitch - pi) < 0.0005f
            if (!sameSpeed || !samePitch) {
                p.playbackParameters = PlaybackParameters(s, pi)
            }

            Log.i(TS_TAG, "path=EXO pipeline=$pipeline mode=$timeStretchMode reason=$reason")
            return
        }

        when (timeStretchMode) {
            TimeStretchMode.EXO -> {
                hqApplyPending = false
                soundTouchProcessor.setEnabled(false)

                val before = p.playbackParameters
                val sameSpeed = abs(before.speed - s) < 0.0005f
                val samePitch = abs(before.pitch - pi) < 0.0005f
                if (!sameSpeed || !samePitch) {
                    p.playbackParameters = PlaybackParameters(s, pi)
                }
                Log.i(TS_TAG, "path=EXO pipeline=$pipeline mode=$timeStretchMode reason=$reason")
            }
            TimeStretchMode.HQ -> {
                val pitchSemi = ratioToSemitones(pi)
                val ok = runCatching {
                    soundTouchProcessor.setEnabled(true)

                    val before = p.playbackParameters
                    if (abs(before.speed - 1f) > 0.0005f || abs(before.pitch - 1f) > 0.0005f) {
                        p.playbackParameters = PlaybackParameters(1f, 1f)
                    }

                    soundTouchProcessor.setTempoRatioAndPitchSemi(
                        tempoRatio = s,
                        pitchSemi = pitchSemi
                    )

                    val initOk = soundTouchProcessor.ensureHqInit()
                    if (!initOk) {
                        false
                    } else {
                        soundTouchProcessor.setTempoRatioAndPitchSemi(
                            tempoRatio = s,
                            pitchSemi = pitchSemi
                        )
                        true
                    }
                }.getOrElse {
                    Log.e(TS_TAG, "HQ runtime failure reason=$reason", it)
                    false
                }

                if (!ok) {
                    hqApplyPending = true
                    Log.w(TS_TAG, "HQ_WAIT_INIT reason=$reason speed=$s pitch=$pi")
                    return
                }

                hqApplyPending = false
                Log.i(TS_TAG, "path=HQ pipeline=$pipeline mode=$timeStretchMode reason=$reason")
            }
        }
    }

    private fun ratioToSemitones(pitchRatio: Float): Float {
        if (abs(pitchRatio - 1f) < 0.00001f) return 0f
        return 12f * (ln(pitchRatio) / ln(2f))
    }

    private fun retryPendingHqApply(reason: String) {
        if (timeStretchMode != TimeStretchMode.HQ) return
        if (!hqApplyPending) return
        applySpeedPitchNow(reason = reason)
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
        playerAppContext = appCtx
        val desiredPipeline = resolveDesiredPlayerPipeline()

        val p = exoPlayer ?: run {
            buildPlayer(appCtx, desiredPipeline).also {
                Log.i(TS_TAG, "pipeline create -> $desiredPipeline")
                publishPlayerEpoch()
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

        hqApplyPending = timeStretchMode == TimeStretchMode.HQ
        applySpeedPitchNow(reason = "getPlayerInit")

        ensureCorePlayerListener(p, appCtx)

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

        runCatching { soundTouchProcessor.reset() }
        runCatching { soundTouchProcessor.setEnabled(false) }
        hqApplyPending = false
        activePlayerPipeline = null
        playerAppContext = null

        exoPlayer?.release()
        exoPlayer = null
        embeddedLyricsListener = null
        onNaturalEndCallback = null
        endedListenerAdded = false
        smokeLastPlaybackState = Player.STATE_IDLE
        smokeLastIsPlaying = false
    }
}
