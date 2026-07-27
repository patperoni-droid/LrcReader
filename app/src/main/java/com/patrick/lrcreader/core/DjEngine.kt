package com.patrick.lrcreader.core.dj

import android.content.Context
import android.media.MediaPlayer
import android.media.audiofx.Visualizer
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.patrick.lrcreader.core.EditionConfig
import com.patrick.lrcreader.core.FillerSoundManager
import com.patrick.lrcreader.core.MeterManager
import com.patrick.lrcreader.core.PlaybackCoordinator
import com.patrick.lrcreader.core.history.HistoryRepository
import com.patrick.lrcreader.core.history.PlaySource
import com.patrick.lrcreader.exo.BuildConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

data class DjQueuedTrack(
    val uri: String,
    val title: String
)

data class DjUiState(
    val queueAutoPlay: Boolean = false,
    val autoPlayBlocked: Boolean = false,
    val showLiteAutoPlayLimitDialog: Boolean = false,
    val activeSlot: Int = 0,              // 0 = rien, 1 = A, 2 = B
    val playingUri: String? = null,

    // ✅ progression lecture
    val progress: Float = 0f,             // 0..1
    val currentPositionMs: Int = 0,       // ✅ NOUVEAU
    val currentDurationMs: Int = 0,

    val deckATitle: String = "A vide",
    val deckBTitle: String = "B vide",
    val deckAUri: String? = null,
    val deckBUri: String? = null,

    val crossfadePos: Float = 0.5f,
    val masterLevel: Float = 1f,
    val queue: List<DjQueuedTrack> = emptyList()
)

/**
 * Moteur DJ global.
 */
object DjEngine {
    private const val METER_TAG = "METER"
    private const val VIS_LOG_EVERY_MS = 1000L
    private const val LITE_DJ_AUTO_LIMIT_MS = 10 * 60 * 1000L

    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var mpA: MediaPlayer? = null
    private var mpB: MediaPlayer? = null
    private var djMeterVisualizerA: Visualizer? = null
    private var djMeterVisualizerB: Visualizer? = null
    private var djMeterSessionA: Int = 0
    private var djMeterSessionB: Int = 0
    private var djVisCallbacks: Int = 0
    private var djVisLastLogTs: Long = 0L

    // état interne
    private var activeSlot: Int = 0
    private var playingUri: String? = null
    private var currentDurationMs: Int = 0
    private var progress: Float = 0f

    private var deckATitle: String = "A vide"
    private var deckBTitle: String = "B vide"
    private var deckAUri: String? = null
    private var deckBUri: String? = null

    private var crossfadePos: Float = 0.5f

    // 🔊 volume MASTER DJ (0..1)
    private var masterLevel: Float = 1f

    // ✅ mode auto-play de la queue
    private var queueAutoPlay: Boolean = false
    private var liteDjSessionStartedAtMs: Long? = null
    private var liteDjAutoBlocked: Boolean = false
    private var liteDjAutoLimitDialogPending: Boolean = false

    private val queueInternal = mutableListOf<DjQueuedTrack>()

    private val _state = MutableStateFlow(
        DjUiState(
            queueAutoPlay = queueAutoPlay,
            autoPlayBlocked = liteDjAutoBlocked,
            showLiteAutoPlayLimitDialog = liteDjAutoLimitDialogPending,
            activeSlot = activeSlot,
            playingUri = playingUri,
            progress = progress,
            currentPositionMs = 0,
            currentDurationMs = currentDurationMs,
            deckATitle = deckATitle,
            deckBTitle = deckBTitle,
            deckAUri = deckAUri,
            deckBUri = deckBUri,
            crossfadePos = crossfadePos,
            masterLevel = masterLevel,
            queue = queueInternal.toList()
        )
    )
    val state: StateFlow<DjUiState> = _state.asStateFlow()

    private var timelineJobStarted = false
    private var xfadeAnimJob: Job? = null   // anim visuelle du slider
    private const val AUTO_MIX_BEFORE_END_MS = 20_000
    private const val AUTO_FADE_DURATION_MS = 1_500
    private var autoMixTriggeredForUri: String? = null
    private var autoMixJob: Job? = null
    private var historyRepository: HistoryRepository? = null
    fun init(context: Context) {
        if (!::appContext.isInitialized) {
            appContext = context.applicationContext
        }
        startTimelineIfNeeded()
    }

    private fun ensureContext() {
        if (!::appContext.isInitialized) {
            error("DjEngine.init(context) doit être appelé au démarrage.")
        }
    }

    private fun historyRepo(): HistoryRepository {
        return historyRepository ?: HistoryRepository.getInstance(appContext).also {
            historyRepository = it
        }
    }

    private fun logDjPlay(title: String, uri: String, artist: String? = null) {
        val safeTitle = title.ifBlank { HistoryRepository.UNTITLED_FALLBACK }
        scope.launch(Dispatchers.IO) {
            runCatching {
                historyRepo().logPlay(
                    source = PlaySource.DJ,
                    title = safeTitle,
                    artist = artist,
                    uri = uri
                )
            }
        }
    }

    private fun pushState(currentPositionMs: Int = 0) {
        _state.value = DjUiState(
            queueAutoPlay = queueAutoPlay,
            autoPlayBlocked = liteDjAutoBlocked,
            showLiteAutoPlayLimitDialog = liteDjAutoLimitDialogPending,
            activeSlot = activeSlot,
            playingUri = playingUri,
            progress = progress,
            currentPositionMs = currentPositionMs,
            currentDurationMs = currentDurationMs,
            deckATitle = deckATitle,
            deckBTitle = deckBTitle,
            deckAUri = deckAUri,
            deckBUri = deckBUri,
            crossfadePos = crossfadePos,
            masterLevel = masterLevel,
            queue = queueInternal.toList()
        )
    }

    private fun safeIsPlaying(player: MediaPlayer?, deck: String): Boolean {
        return runCatching { player?.isPlaying == true }
            .getOrElse {
                Log.w(METER_TAG, "DJ isPlaying($deck) failed: ${it.message}")
                false
            }
    }

    private fun safeSessionId(player: MediaPlayer?, deck: String): Int {
        return runCatching { player?.audioSessionId ?: 0 }
            .getOrElse {
                Log.w(METER_TAG, "DJ audioSessionId($deck) failed: ${it.message}")
                0
            }
    }

    private fun logMeterState(reason: String) {
        val aPlaying = safeIsPlaying(mpA, "A")
        val bPlaying = safeIsPlaying(mpB, "B")
        val aSession = safeSessionId(mpA, "A")
        val bSession = safeSessionId(mpB, "B")
        val anyPlaying = aPlaying || bPlaying
        val session = listOf(aSession, bSession).firstOrNull { it > 0 }
        Log.d(
            METER_TAG,
            "DJ $reason isPlayingA=$aPlaying isPlayingB=$bPlaying sessionA=$aSession sessionB=$bSession activeSlot=$activeSlot"
        )
        if (anyPlaying) {
            Log.d(
                METER_TAG,
                "PLAY_START engine=DjEngine sessionId=$session isPlaying=$anyPlaying reason=$reason"
            )
        }
    }

    private fun attachDjMeterTap(slot: Int, player: MediaPlayer?) {
        val sessionId = runCatching { player?.audioSessionId ?: 0 }.getOrElse { 0 }
        if (sessionId <= 0) {
            releaseDjMeterTap(slot)
            return
        }

        val existingSession = if (slot == 1) djMeterSessionA else djMeterSessionB
        val existingTap = if (slot == 1) djMeterVisualizerA else djMeterVisualizerB
        if (existingTap != null && existingSession == sessionId) return

        releaseDjMeterTap(slot)

        val tap = runCatching {
            Visualizer(sessionId).apply {
                val captureSizeRange = Visualizer.getCaptureSizeRange()
                captureSize = captureSizeRange[1]
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            visualizer: Visualizer?,
                            waveform: ByteArray?,
                            samplingRate: Int
                        ) {
                            if (waveform == null || waveform.isEmpty()) return
                            publishDjWaveformLevels(slot, waveform)
                        }

                        override fun onFftDataCapture(
                            visualizer: Visualizer?,
                            fft: ByteArray?,
                            samplingRate: Int
                        ) = Unit
                    },
                    Visualizer.getMaxCaptureRate() / 2,
                    true,
                    false
                )
                enabled = true
            }
        }.onFailure {
            if (BuildConfig.DEBUG) {
                Log.w(
                    METER_TAG,
                    "DJ_VIS session=$sessionId enabled=false callbacks=0 rms=0.00 peak=0.00 slot=$slot error=${it.message}"
                )
            }
        }.getOrNull()

        if (slot == 1) {
            djMeterVisualizerA = tap
            djMeterSessionA = if (tap != null) sessionId else 0
        } else {
            djMeterVisualizerB = tap
            djMeterSessionB = if (tap != null) sessionId else 0
        }
        djVisCallbacks = 0
        djVisLastLogTs = 0L
        if (BuildConfig.DEBUG) {
            Log.d(
                METER_TAG,
                "DJ_VIS session=$sessionId enabled=${tap != null} callbacks=0 rms=0.00 peak=0.00 slot=$slot"
            )
        }
    }

    private fun releaseDjMeterTap(slot: Int) {
        val session = if (slot == 1) djMeterSessionA else djMeterSessionB
        val callbacks = djVisCallbacks
        val tap = if (slot == 1) djMeterVisualizerA else djMeterVisualizerB
        if (tap != null) {
            runCatching { tap.enabled = false }
            runCatching { tap.release() }
        }
        if (slot == 1) {
            djMeterVisualizerA = null
            djMeterSessionA = 0
        } else {
            djMeterVisualizerB = null
            djMeterSessionB = 0
        }
        djVisCallbacks = 0
        djVisLastLogTs = 0L
        if (BuildConfig.DEBUG) {
            Log.d(
                METER_TAG,
                "DJ_VIS session=$session enabled=false callbacks=$callbacks rms=0.00 peak=0.00 slot=$slot"
            )
        }
    }

    private fun releaseDjMeterTaps() {
        releaseDjMeterTap(1)
        releaseDjMeterTap(2)
    }

    private fun publishDjWaveformLevels(slot: Int, waveform: ByteArray) {
        var peak = 0f
        var sumSq = 0.0
        var count = 0

        for (i in waveform.indices) {
            val sample = ((waveform[i].toInt() and 0xFF) - 128) / 128f
            val absSample = abs(sample)
            if (absSample > peak) peak = absSample
            sumSq += sample * sample
            count++
        }

        if (count == 0) return
        val rms = sqrt(sumSq / count).toFloat().coerceIn(0f, 1f)
        val peak01 = peak.coerceIn(0f, 1f)
        MeterManager.onDjPcm(rms = rms, peak = peak01)

        djVisCallbacks++
        if (BuildConfig.DEBUG) {
            val now = SystemClock.elapsedRealtime()
            if (now - djVisLastLogTs >= VIS_LOG_EVERY_MS) {
                djVisLastLogTs = now
                val session = if (slot == 1) djMeterSessionA else djMeterSessionB
                val enabled = if (slot == 1) djMeterVisualizerA != null else djMeterVisualizerB != null
                Log.d(
                    METER_TAG,
                    "DJ_VIS session=$session enabled=$enabled callbacks=$djVisCallbacks rms=${fmtLevel(rms)} peak=${fmtLevel(peak01)} slot=$slot"
                )
            }
        }
    }

    private fun fmtLevel(value: Float): String = String.format(Locale.US, "%.2f", value)

    fun setQueueAutoPlay(enabled: Boolean) {
        updateLiteDjAutoLimitIfNeeded()
        queueAutoPlay = enabled && !liteDjAutoBlocked
        pushState()
    }

    fun consumeLiteAutoLimitDialog() {
        if (!liteDjAutoLimitDialogPending) return
        liteDjAutoLimitDialogPending = false
        pushState()
    }

    private fun markLiteDjSessionStartedIfNeeded() {
        if (EditionConfig.isPro) return
        if (liteDjSessionStartedAtMs == null) {
            liteDjSessionStartedAtMs = SystemClock.elapsedRealtime()
        }
    }

    private fun updateLiteDjAutoLimitIfNeeded(nowMs: Long = SystemClock.elapsedRealtime()) {
        if (EditionConfig.isPro) return
        val startedAtMs = liteDjSessionStartedAtMs ?: return
        if (liteDjAutoBlocked) return
        if (nowMs - startedAtMs < LITE_DJ_AUTO_LIMIT_MS) return

        liteDjAutoBlocked = true
        liteDjAutoLimitDialogPending = true
        queueAutoPlay = false
        autoMixTriggeredForUri = null
        autoMixJob?.cancel()
        autoMixJob = null
        pushState()
    }

    private fun canAutoTransitionAfterCurrentTrack(
        remainingMs: Int,
        nowMs: Long = SystemClock.elapsedRealtime()
    ): Boolean {
        updateLiteDjAutoLimitIfNeeded(nowMs)
        if (liteDjAutoBlocked) return false
        if (EditionConfig.isPro) return true
        val startedAtMs = liteDjSessionStartedAtMs ?: return true
        val limitAtMs = startedAtMs + LITE_DJ_AUTO_LIMIT_MS
        return nowMs + remainingMs < limitAtMs
    }

    private fun startTimelineIfNeeded() {
        if (timelineJobStarted) return
        timelineJobStarted = true

        scope.launch {
            while (isActive) {
                delay(200)

                val posMs: Int =
                    if (playingUri != null && currentDurationMs > 0) {
                        try {
                            when (activeSlot) {
                                1 -> mpA?.currentPosition ?: 0
                                2 -> mpB?.currentPosition ?: 0
                                else -> 0
                            }
                        } catch (_: Exception) {
                            0
                        }
                    } else 0

                progress =
                    if (playingUri != null && currentDurationMs > 0) {
                        (posMs.toFloat() / currentDurationMs.toFloat()).coerceIn(0f, 1f)
                    } else 0f

                if (playingUri != null) {
                    updateLiteDjAutoLimitIfNeeded()
                }

                // ✅ AUTO-MIX : 10s avant la fin (mode danse)
                val curUri = playingUri
                if (queueAutoPlay && curUri != null && currentDurationMs > 0) {
                    val remaining = (currentDurationMs - posMs).coerceAtLeast(0)
                    val canTrigger =
                        queueInternal.isNotEmpty() &&
                                remaining <= AUTO_MIX_BEFORE_END_MS &&
                                canAutoTransitionAfterCurrentTrack(remaining) &&
                                autoMixTriggeredForUri != curUri

                    if (canTrigger) {
                        autoMixTriggeredForUri = curUri
                        scope.launch { autoMixNextFromQueueDance() }
                    }
                }

                pushState(currentPositionMs = posMs)
            }
        }
    }
    /* --------------------------- file d’attente --------------------------- */

    fun addToQueue(uriString: String, title: String) {
        if (queueInternal.none { it.uri == uriString }) {
            queueInternal.add(DjQueuedTrack(uriString, title))
            pushState()
        }
    }

    fun removeFromQueue(item: DjQueuedTrack) {
        queueInternal.remove(item)
        pushState()
    }

    fun playFromQueue(item: DjQueuedTrack) {
        selectTrackFromList(item.uri, item.title)
        queueInternal.remove(item)
        pushState()
    }

    /* ----------------- petite anim du slider (sans audio) ---------------- */

    private fun animateSliderTo(target: Float, durationMs: Int = 300) {
        xfadeAnimJob?.cancel()
        xfadeAnimJob = scope.launch {
            val start = crossfadePos
            val steps = (durationMs / 50).coerceAtLeast(1)
            for (i in 0 until steps) {
                val t = (i + 1) / steps.toFloat()
                crossfadePos = (start + (target - start) * t).coerceIn(0f, 1f)
                pushState()
                delay(50)
            }
            crossfadePos = target.coerceIn(0f, 1f)
            pushState()
        }
    }

    /* ------------------------------- SELECT ------------------------------- */

    fun selectTrackFromList(uriString: String, displayName: String) {
        ensureContext()

        scope.launch {
            markLiteDjSessionStartedIfNeeded()
            if (activeSlot == 0) {
                // 👉 Première mise en lecture DJ
                PlaybackCoordinator.onDjStart()
                runCatching { FillerSoundManager.fadeOutAndStop(400) }

                releaseDjMeterTap(1)
                mpA?.release()
                val p = MediaPlayer()
                mpA = p

                try {
                    withContext(Dispatchers.IO) {
                        p.setDataSource(appContext, Uri.parse(uriString))
                        p.prepare()
                    }
                    attachDjMeterTap(1, p)

                    // ✅ important : completion listener (auto-play)
                    attachOnComplete(1, p)

                    currentDurationMs = p.duration
                    p.setVolume(1f, 1f)
                    p.start()
                    logMeterState("start slot=A")
                    logDjPlay(displayName, uriString)

                    deckATitle = displayName
                    deckAUri = uriString

                    activeSlot = 1
                    playingUri = uriString
                    autoMixTriggeredForUri = null
                    autoMixJob?.cancel()
                    autoMixJob = null

                    animateSliderTo(0f, durationMs = 300)
                    applyCrossfader()
                } catch (e: Exception) {
                    e.printStackTrace()
                    mpA = null
                    activeSlot = 0
                    playingUri = null
                    currentDurationMs = 0
                    PlaybackCoordinator.onDjStop()
                }
            } else {
                // 👉 Une platine joue : on charge l'autre deck en muet
                val loadIntoA = (activeSlot == 2)

                if (loadIntoA) {
                    // B joue → on charge A
                    releaseDjMeterTap(1)
                    mpA?.release()
                    val p = MediaPlayer()
                    mpA = p
                    try {
                        withContext(Dispatchers.IO) {
                            p.setDataSource(appContext, Uri.parse(uriString))
                            p.prepare()
                        }
                        attachDjMeterTap(1, p)

                        // ✅ completion listener (même si A devient active ensuite)
                        attachOnComplete(1, p)

                        p.setVolume(0f, 0f)
                        deckATitle = displayName
                        deckAUri = uriString
                    } catch (e: Exception) {
                        e.printStackTrace()
                        mpA = null
                        deckATitle = "A vide"
                        deckAUri = null
                    }
                } else {
                    // A joue → on charge B
                    releaseDjMeterTap(2)
                    mpB?.release()
                    val p = MediaPlayer()
                    mpB = p
                    try {
                        withContext(Dispatchers.IO) {
                            p.setDataSource(appContext, Uri.parse(uriString))
                            p.prepare()
                        }
                        attachDjMeterTap(2, p)

                        // ✅ completion listener
                        attachOnComplete(2, p)

                        p.setVolume(0f, 0f)
                        deckBTitle = displayName
                        deckBUri = uriString
                    } catch (e: Exception) {
                        e.printStackTrace()
                        mpB = null
                        deckBTitle = "B vide"
                        deckBUri = null
                    }
                }
            }

            pushState()
            startTimelineIfNeeded()
        }
    }

    private fun attachOnComplete(slot: Int, p: MediaPlayer) {
        p.setOnCompletionListener {
            scope.launch { onDeckCompleted(slot) }
        }
    }

    private suspend fun onDeckCompleted(slot: Int) {
        // On ne réagit que si c’est bien la platine active
        if (slot != activeSlot) return

        updateLiteDjAutoLimitIfNeeded()

        // ✅ mode auto OFF ou queue vide => on laisse finir (comportement actuel)
        if (!queueAutoPlay || queueInternal.isEmpty()) {
            PlaybackCoordinator.onDjStop()
            return
        }

        val next = queueInternal.removeAt(0)
        val nextSlot = if (activeSlot == 1) 2 else 1
        val nextUri = next.uri
        val nextTitle = next.title

        if (nextSlot == 1) {
            releaseDjMeterTap(1)
            mpA?.release()
            val p = MediaPlayer()
            mpA = p

            withContext(Dispatchers.IO) {
                p.setDataSource(appContext, Uri.parse(nextUri))
                p.prepare()
            }
            attachDjMeterTap(1, p)
            attachOnComplete(1, p)

            p.setVolume(0f, 0f)
            deckATitle = nextTitle
            deckAUri = nextUri

            p.seekTo(0)
            p.start()
            logMeterState("queue start slot=A")
            logDjPlay(nextTitle, nextUri)

            activeSlot = 1
            playingUri = nextUri
            currentDurationMs = try { p.duration } catch (_: Exception) { 0 }
            animateSliderTo(0f, 200)
        } else {
            releaseDjMeterTap(2)
            mpB?.release()
            val p = MediaPlayer()
            mpB = p

            withContext(Dispatchers.IO) {
                p.setDataSource(appContext, Uri.parse(nextUri))
                p.prepare()
            }
            attachDjMeterTap(2, p)
            attachOnComplete(2, p)

            p.setVolume(0f, 0f)
            deckBTitle = nextTitle
            deckBUri = nextUri

            p.seekTo(0)
            p.start()
            logMeterState("queue start slot=B")
            logDjPlay(nextTitle, nextUri)

            activeSlot = 2
            playingUri = nextUri
            currentDurationMs = try { p.duration } catch (_: Exception) { 0 }
            animateSliderTo(1f, 200)
        }

        applyCrossfader()
        pushState()
    }
    private suspend fun autoMixNextFromQueueDance() {
        updateLiteDjAutoLimitIfNeeded()
        if (!queueAutoPlay) return
        if (liteDjAutoBlocked) return
        if (queueInternal.isEmpty()) return
        if (playingUri == null) return

        // évite double déclenchement
        autoMixJob?.cancel()
        autoMixJob = scope.launch {
            val next = queueInternal.removeAt(0)

            // 1) on charge la piste suivante dans l'autre deck (muet)
            val targetSlot = if (activeSlot == 1) 2 else 1
            val nextUri = next.uri
            val nextTitle = next.title

            if (targetSlot == 2) {
                // charger B
                releaseDjMeterTap(2)
                mpB?.release()
                val p = MediaPlayer()
                mpB = p
                withContext(Dispatchers.IO) {
                    p.setDataSource(appContext, Uri.parse(nextUri))
                    p.prepare()
                }
                attachDjMeterTap(2, p)
                attachOnComplete(2, p)
                p.setVolume(0f, 0f)
                deckBTitle = nextTitle
                deckBUri = nextUri
            } else {
                // charger A
                releaseDjMeterTap(1)
                mpA?.release()
                val p = MediaPlayer()
                mpA = p
                withContext(Dispatchers.IO) {
                    p.setDataSource(appContext, Uri.parse(nextUri))
                    p.prepare()
                }
                attachDjMeterTap(1, p)
                attachOnComplete(1, p)
                p.setVolume(0f, 0f)
                deckATitle = nextTitle
                deckAUri = nextUri
            }

            pushState()

            // 2) lancer le crossfade automatique (comme si tu appuyais GO)
            launchCrossfadeAuto(durationMs = AUTO_FADE_DURATION_MS)
        }
    }

    fun seekTo(positionMs: Int) {
        val dur = currentDurationMs
        val curUri = playingUri
        if (curUri == null || dur <= 0) return

        val safeMs = positionMs.coerceIn(0, dur)

        try {
            when (activeSlot) {
                1 -> mpA?.seekTo(safeMs)
                2 -> mpB?.seekTo(safeMs)
            }
        } catch (_: Exception) {
            // ignore
        }


        autoMixTriggeredForUri = null
    }
    /* ---------------------------- CROSSFADER ----------------------------- */

    fun setCrossfadePos(value: Float) {
        xfadeAnimJob?.cancel()
        crossfadePos = value.coerceIn(0f, 1f)
        applyCrossfader()
        pushState()
    }

    fun setMasterVolume(level: Float) {
        masterLevel = level.coerceIn(0f, 1f)
        applyCrossfader()
        pushState()
    }

    fun getAudioSessionIds(): List<Int> {
        val aId = safeSessionId(mpA, "A")
        val bId = safeSessionId(mpB, "B")
        return listOf(aId, bId).filter { it > 0 }.distinct()
    }

    fun isPlaying(): Boolean {
        val aPlaying = safeIsPlaying(mpA, "A")
        val bPlaying = safeIsPlaying(mpB, "B")
        val playing = aPlaying || bPlaying
        if (!playing && activeSlot != 0 && playingUri != null) {
            Log.d(METER_TAG, "DJ isPlaying=false but activeSlot=$activeSlot uri=$playingUri")
        }
        return playing
    }

    private fun applyCrossfaderInternal(level: Float) {
        val baseA = 1f - crossfadePos
        val baseB = crossfadePos
        val m = level.coerceIn(0f, 1f)

        val aVol = baseA * m
        val bVol = baseB * m

        try { mpA?.setVolume(aVol, aVol) } catch (_: Exception) {}
        try { mpB?.setVolume(bVol, bVol) } catch (_: Exception) {}
    }

    private fun applyCrossfader() {
        applyCrossfaderInternal(masterLevel)
    }
    fun launchCrossfade() {
        scope.launch {
            launchCrossfadeAuto(durationMs = AUTO_FADE_DURATION_MS)
        }
    }
    private suspend fun launchCrossfadeAuto(durationMs: Int) {
        // A joue -> B prêt
        if (activeSlot == 1 && mpA != null && mpB != null) {
            val playerA = mpA!!
            val playerB = mpB!!

            if (!playerB.isPlaying) {
                try { playerB.seekTo(0); playerB.start() } catch (_: Exception) {}
                logMeterState("crossfade auto start slot=B")
                deckBUri?.let { uri -> logDjPlay(deckBTitle, uri) }
            }

            val stepMs = 50
            val steps = (durationMs / stepMs).coerceAtLeast(1)
            val fromPos = crossfadePos
            val toPos = 1f

            repeat(steps) { i ->
                val t = (i + 1) / steps.toFloat()
                crossfadePos = (fromPos + (toPos - fromPos) * t).coerceIn(0f, 1f)
                applyCrossfader()
                pushState()
                delay(stepMs.toLong())
            }

            try { playerA.stop() } catch (_: Exception) {}
            releaseDjMeterTap(1)
            playerA.release()
            mpA = null

            activeSlot = 2
            playingUri = deckBUri

            autoMixTriggeredForUri = null
            autoMixJob?.cancel()
            autoMixJob = null

            currentDurationMs = try { playerB.duration } catch (_: Exception) { 0 }

            pushState()
            return
        }

        // B joue -> A prêt
        if (activeSlot == 2 && mpA != null && mpB != null) {
            val playerA = mpA!!
            val playerB = mpB!!

            if (!playerA.isPlaying) {
                try { playerA.seekTo(0); playerA.start() } catch (_: Exception) {}
                logMeterState("crossfade auto start slot=A")
                deckAUri?.let { uri -> logDjPlay(deckATitle, uri) }
            }

            val stepMs = 50
            val steps = (durationMs / stepMs).coerceAtLeast(1)
            val fromPos = crossfadePos
            val toPos = 0f

            repeat(steps) { i ->
                val t = (i + 1) / steps.toFloat()
                crossfadePos = (fromPos + (toPos - fromPos) * t).coerceIn(0f, 1f)
                applyCrossfader()
                pushState()
                delay(stepMs.toLong())
            }

            try { playerB.stop() } catch (_: Exception) {}
            releaseDjMeterTap(2)
            playerB.release()
            mpB = null

            activeSlot = 1
            playingUri = deckAUri

            autoMixTriggeredForUri = null
            autoMixJob?.cancel()
            autoMixJob = null

            currentDurationMs = try { playerA.duration } catch (_: Exception) { 0 }

            pushState()
            return
        }
    }

    /* ------------------------------ STOP DJ ------------------------------ */

    fun stopDj(fadeMs: Int = 600) {
        scope.launch {
            val localMpA = mpA
            val localMpB = mpB

            if (localMpA == null && localMpB == null) {
                releaseDjMeterTaps()
                resetState(clearQueue = false)
                PlaybackCoordinator.onDjStop()
                return@launch
            }

            val startMaster = masterLevel.coerceIn(0f, 1f)

            val steps = (fadeMs / 50).coerceAtLeast(1)
            for (i in 0 until steps) {
                val factor = 1f - (i + 1) / steps.toFloat()
                val level = startMaster * factor
                applyCrossfaderInternal(level)
                delay(50)
            }

            try { localMpA?.stop() } catch (_: Exception) {}
            try { localMpB?.stop() } catch (_: Exception) {}

            releaseDjMeterTaps()
            try { localMpA?.release() } catch (_: Exception) {}
            try { localMpB?.release() } catch (_: Exception) {}

            mpA = null
            mpB = null

            resetState(clearQueue = false)
            PlaybackCoordinator.onDjStop()
        }
    }

    private fun resetState(clearQueue: Boolean = false) {
        activeSlot = 0
        playingUri = null
        progress = 0f
        currentDurationMs = 0
        deckATitle = "A vide"
        deckBTitle = "B vide"
        deckAUri = null
        deckBUri = null
        crossfadePos = 0.5f
        autoMixTriggeredForUri = null
        autoMixJob?.cancel()
        autoMixJob = null
        liteDjSessionStartedAtMs = null
        liteDjAutoBlocked = false
        liteDjAutoLimitDialogPending = false
        // on garde masterLevel et queueAutoPlay tels quels

        if (clearQueue) {
            queueInternal.clear()
        }
        pushState()
    }

    fun release() {
        stopDj(0)
    }

    fun stopWithFade(durationMs: Long = 300) {
        try {
            stopDj(durationMs.toInt())
        } catch (_: Exception) {}
    }
    fun clearQueue() {
        queueInternal.clear()
        pushState()
    }
}
