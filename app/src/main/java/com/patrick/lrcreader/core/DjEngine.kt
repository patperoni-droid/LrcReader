package com.patrick.lrcreader.core.dj

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import com.patrick.lrcreader.core.FillerSoundManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DjQueuedTrack(
    val uri: String,
    val title: String
)

data class DjUiState(
    val activeSlot: Int = 0,            // 0 = rien, 1 = A, 2 = B
    val playingUri: String? = null,
    val progress: Float = 0f,
    val currentDurationMs: Int = 0,
    val deckATitle: String = "A vide",
    val deckBTitle: String = "B vide",
    val deckAUri: String? = null,
    val deckBUri: String? = null,
    val crossfadePos: Float = 0.5f,
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

    // 🔊 volume MASTER DJ (0..1) appliqué à A et B
    private var masterLevel: Float = 1f

    private val queueInternal = mutableListOf<DjQueuedTrack>()

    private val _state = MutableStateFlow(
        DjUiState(
            activeSlot = activeSlot,
            playingUri = playingUri,
            progress = progress,
            currentDurationMs = currentDurationMs,
            deckATitle = deckATitle,
            deckBTitle = deckBTitle,
            deckAUri = deckAUri,
            deckBUri = deckBUri,
            crossfadePos = crossfadePos,
            queue = queueInternal.toList()
        )
    )
    val state: StateFlow<DjUiState> = _state.asStateFlow()

    private var timelineJobStarted = false
    private var xfadeAnimJob: Job? = null   // anim visuelle du slider

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

    private fun pushState() {
        _state.value = DjUiState(
            activeSlot = activeSlot,
            playingUri = playingUri,
            progress = progress,
            currentDurationMs = currentDurationMs,
            deckATitle = deckATitle,
            deckBTitle = deckBTitle,
            deckAUri = deckAUri,
            deckBUri = deckBUri,
            crossfadePos = crossfadePos,
            queue = queueInternal.toList()
        )
    }

    private fun startTimelineIfNeeded() {
        if (timelineJobStarted) return
        timelineJobStarted = true

        scope.launch {
            while (isActive) {
                delay(200)
                if (playingUri != null && currentDurationMs > 0) {
                    val pos = try {
                        when (activeSlot) {
                            1 -> mpA?.currentPosition ?: 0
                            2 -> mpB?.currentPosition ?: 0
                            else -> 0
                        }
                    } catch (_: Exception) {
                        0
                    }
                    progress = pos.toFloat() / currentDurationMs.toFloat()
                } else {
                    progress = 0f
                }
                pushState()
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
                // pas d'applyCrossfader ici → uniquement visuel si une seule platine joue
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
                // rien ne joue → on démarre sur A
                FillerSoundManager.fadeOutAndStop(400)
                mpA?.release()

                val p = MediaPlayer()
                mpA = p

                try {
                    withContext(Dispatchers.IO) {
                        p.setDataSource(appContext, Uri.parse(uriString))
                        p.prepare()
                    }
                    currentDurationMs = p.duration
                    // volume appliqué via master + crossfader
                    p.setVolume(1f, 1f)
                    p.start()

                    deckATitle = displayName
                    deckAUri = uriString

                    activeSlot = 1
                    playingUri = uriString

                    // 👉 visuellement, on ramène le slider côté A
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
                // quelque chose joue déjà → on prépare l’autre deck à 0 de volume
                val loadIntoA = (activeSlot == 2)
                if (loadIntoA) {
                    mpA?.release()
                    val p = MediaPlayer()
                    mpA = p
                    try {
                        withContext(Dispatchers.IO) {
                            p.setDataSource(appContext, Uri.parse(uriString))
                            p.prepare()
                        }
                        p.setVolume(0f, 0f)
                        deckATitle = displayName
                        deckAUri = uriString
                    } catch (e: Exception) {
                        e.printStackTrace()
                        mpA = null
                    }
                } else {
                    mpB?.release()
                    val p = MediaPlayer()
                    mpB = p
                    try {
                        withContext(Dispatchers.IO) {
                            p.setDataSource(appContext, Uri.parse(uriString))
                            p.prepare()
                        }
                        p.setVolume(0f, 0f)
                        deckBTitle = displayName
                        deckBUri = uriString
                    } catch (e: Exception) {
                        e.printStackTrace()
                        mpB = null
                    }
                }
            }

            pushState()
            startTimelineIfNeeded()
        }
    }

    /* ---------------------------- CROSSFADER ----------------------------- */

    fun setCrossfadePos(value: Float) {
        // si l'utilisateur touche le slider, on annule une anim éventuelle
        xfadeAnimJob?.cancel()
        crossfadePos = value.coerceIn(0f, 1f)
        applyCrossfader()
        pushState()
    }

    // 🔊 Volume MASTER DJ (0..1) appliqué à A et B
    fun setMasterVolume(level: Float) {
        masterLevel = level.coerceIn(0f, 1f)
        applyCrossfader()   // réapplique tout de suite aux deux platines
    }

    private fun applyCrossfader() {
        val baseA = 1f - crossfadePos
        val baseB = crossfadePos
        val m = masterLevel.coerceIn(0f, 1f)

        val aVol = baseA * m
        val bVol = baseB * m

        try { mpA?.setVolume(aVol, aVol) } catch (_: Exception) {}
        try { mpB?.setVolume(bVol, bVol) } catch (_: Exception) {}
    }

    fun launchCrossfade() {
        scope.launch {
            // cas 1 : A joue, B prêt → A -> B
            if (activeSlot == 1 && mpA != null && mpB != null) {
                val playerA = mpA!!
                val playerB = mpB!!

                if (!playerB.isPlaying) {
                    try {
                        playerB.seekTo(0)
                        playerB.start()
                    } catch (_: Exception) {}
                }

                val fadeSteps = 20
                val fromPos = crossfadePos
                val toPos = 1f   // on va vers B

                repeat(fadeSteps) { i ->
                    val t = (i + 1) / fadeSteps.toFloat()
                    crossfadePos = (fromPos + (toPos - fromPos) * t).coerceIn(0f, 1f)
                    applyCrossfader()
                    pushState()
                    delay(50)
                }

                // fin : B devient la platine active
                try { playerA.stop() } catch (_: Exception) {}
                playerA.release()
                mpA = null

                activeSlot = 2
                playingUri = deckBUri
                currentDurationMs = try { playerB.duration } catch (_: Exception) { 0 }

                pushState()
                return@launch
            }

            // cas 2 : B joue, A prêt → B -> A
            if (activeSlot == 2 && mpA != null && mpB != null) {
                val playerA = mpA!!
                val playerB = mpB!!

                if (!playerA.isPlaying) {
                    try {
                        playerA.seekTo(0)
                        playerA.start()
                    } catch (_: Exception) {}
                }

                val fadeSteps = 20
                val fromPos = crossfadePos
                val toPos = 0f   // on va vers A

                repeat(fadeSteps) { i ->
                    val t = (i + 1) / fadeSteps.toFloat()
                    crossfadePos = (fromPos + (toPos - fromPos) * t).coerceIn(0f, 1f)
                    applyCrossfader()
                    pushState()
                    delay(50)
                }

                try { playerB.stop() } catch (_: Exception) {}
                playerB.release()
                mpB = null

                activeSlot = 1
                playingUri = deckAUri
                currentDurationMs = try { playerA.duration } catch (_: Exception) { 0 }

                pushState()
            }
        }
    }

    /* ------------------------------ STOP DJ ------------------------------ */

    fun stopDj(fadeMs: Int = 600) {
        scope.launch {
            val localMpA = mpA
            val localMpB = mpB

            // Rien ne joue → reset simple
            if (localMpA == null && localMpB == null) {
                resetState()
                return@launch
            }

            // Petit fade-out
            val steps = (fadeMs / 50).coerceAtLeast(1)
            for (i in 0 until steps) {
                val factor = 1f - (i + 1) / steps.toFloat()
                try { localMpA?.setVolume(factor, factor) } catch (_: Exception) {}
                try { localMpB?.setVolume(factor, factor) } catch (_: Exception) {}
                delay(50)
            }

            // Stop réel
            try { localMpA?.stop() } catch (_: Exception) {}
            try { localMpB?.stop() } catch (_: Exception) {}

            try { localMpA?.release() } catch (_: Exception) {}
            try { localMpB?.release() } catch (_: Exception) {}

            mpA = null
            mpB = null

            resetState()
        }
    }

    private fun resetState() {
        activeSlot = 0
        playingUri = null
        progress = 0f
        currentDurationMs = 0
        deckATitle = "A vide"
        deckBTitle = "B vide"
        deckAUri = null
        deckBUri = null
        crossfadePos = 0.5f
        queueInternal.clear()
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
}