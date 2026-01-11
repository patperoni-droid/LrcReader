package com.patrick.lrcreader.core.dj

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import com.patrick.lrcreader.core.FillerSoundManager
import com.patrick.lrcreader.core.PlaybackCoordinator
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DjQueuedTrack(
    val uri: String,
    val title: String
)

data class DjUiState(
    val queueAutoPlay: Boolean = false,
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

    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var mpA: MediaPlayer? = null
    private var mpB: MediaPlayer? = null

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

    private val queueInternal = mutableListOf<DjQueuedTrack>()

    private val _state = MutableStateFlow(
        DjUiState(
            queueAutoPlay = queueAutoPlay,
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

    private fun pushState(currentPositionMs: Int = 0) {
        _state.value = DjUiState(
            queueAutoPlay = queueAutoPlay,
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

    fun setQueueAutoPlay(enabled: Boolean) {
        queueAutoPlay = enabled
        pushState()
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

                // ✅ AUTO-MIX : 10s avant la fin (mode danse)
                val curUri = playingUri
                if (queueAutoPlay && curUri != null && currentDurationMs > 0) {
                    val remaining = (currentDurationMs - posMs).coerceAtLeast(0)
                    val canTrigger =
                        queueInternal.isNotEmpty() &&
                                remaining <= AUTO_MIX_BEFORE_END_MS &&
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
            if (activeSlot == 0) {
                // 👉 Première mise en lecture DJ
                PlaybackCoordinator.onDjStart()
                runCatching { FillerSoundManager.fadeOutAndStop(400) }

                mpA?.release()
                val p = MediaPlayer()
                mpA = p

                try {
                    withContext(Dispatchers.IO) {
                        p.setDataSource(appContext, Uri.parse(uriString))
                        p.prepare()
                    }

                    // ✅ important : completion listener (auto-play)
                    attachOnComplete(1, p)

                    currentDurationMs = p.duration
                    p.setVolume(1f, 1f)
                    p.start()

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
                }
            } else {
                // 👉 Une platine joue : on charge l'autre deck en muet
                val loadIntoA = (activeSlot == 2)

                if (loadIntoA) {
                    // B joue → on charge A
                    mpA?.release()
                    val p = MediaPlayer()
                    mpA = p
                    try {
                        withContext(Dispatchers.IO) {
                            p.setDataSource(appContext, Uri.parse(uriString))
                            p.prepare()
                        }

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
                    mpB?.release()
                    val p = MediaPlayer()
                    mpB = p
                    try {
                        withContext(Dispatchers.IO) {
                            p.setDataSource(appContext, Uri.parse(uriString))
                            p.prepare()
                        }

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

        // ✅ mode auto OFF ou queue vide => on laisse finir (comportement actuel)
        if (!queueAutoPlay || queueInternal.isEmpty()) return

        val next = queueInternal.removeAt(0)
        val nextSlot = if (activeSlot == 1) 2 else 1
        val nextUri = next.uri
        val nextTitle = next.title

        if (nextSlot == 1) {
            mpA?.release()
            val p = MediaPlayer()
            mpA = p

            withContext(Dispatchers.IO) {
                p.setDataSource(appContext, Uri.parse(nextUri))
                p.prepare()
            }
            attachOnComplete(1, p)

            p.setVolume(0f, 0f)
            deckATitle = nextTitle
            deckAUri = nextUri

            p.seekTo(0)
            p.start()

            activeSlot = 1
            playingUri = nextUri
            currentDurationMs = try { p.duration } catch (_: Exception) { 0 }
            animateSliderTo(0f, 200)
        } else {
            mpB?.release()
            val p = MediaPlayer()
            mpB = p

            withContext(Dispatchers.IO) {
                p.setDataSource(appContext, Uri.parse(nextUri))
                p.prepare()
            }
            attachOnComplete(2, p)

            p.setVolume(0f, 0f)
            deckBTitle = nextTitle
            deckBUri = nextUri

            p.seekTo(0)
            p.start()

            activeSlot = 2
            playingUri = nextUri
            currentDurationMs = try { p.duration } catch (_: Exception) { 0 }
            animateSliderTo(1f, 200)
        }

        applyCrossfader()
        pushState()
    }
    private suspend fun autoMixNextFromQueueDance() {
        if (!queueAutoPlay) return
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
                mpB?.release()
                val p = MediaPlayer()
                mpB = p
                withContext(Dispatchers.IO) {
                    p.setDataSource(appContext, Uri.parse(nextUri))
                    p.prepare()
                }
                attachOnComplete(2, p)
                p.setVolume(0f, 0f)
                deckBTitle = nextTitle
                deckBUri = nextUri
            } else {
                // charger A
                mpA?.release()
                val p = MediaPlayer()
                mpA = p
                withContext(Dispatchers.IO) {
                    p.setDataSource(appContext, Uri.parse(nextUri))
                    p.prepare()
                }
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
                resetState(clearQueue = false)
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