package com.patrick.lrcreader.core.dj

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import com.patrick.lrcreader.core.FillerSoundManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

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
 * - Garde mpA/mpB et tout l'état en mémoire, au-dessus de l'UI.
 * - La musique continue si tu changes de page.
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

    fun init(context: Context) {
        if (!::appContext.isInitialized) {
            appContext = context.applicationContext
        }
        startTimelineIfNeeded()
    }

    private fun ensureContext() {
        if (!::appContext.isInitialized) {
            error("DjEngine.init(context) doit être appelé au démarrage (MainActivity ou Application).")
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
                    p.setVolume(1f, 1f)
                    p.start()

                    deckATitle = displayName
                    deckAUri = uriString

                    activeSlot = 1
                    playingUri = uriString
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
        crossfadePos = value.coerceIn(0f, 1f)
        applyCrossfader()
        pushState()
    }

    private fun applyCrossfader() {
        val aVol = 1f - crossfadePos
        val bVol = crossfadePos
        mpA?.setVolume(aVol, aVol)
        mpB?.setVolume(bVol, bVol)
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
                    } catch (_: Exception) {
                    }
                }

                val fadeSteps = 20
                val targetA = 1f - crossfadePos
                val targetB = crossfadePos

                repeat(fadeSteps) { i ->
                    val t = (i + 1) / fadeSteps.toFloat()
                    val volA = targetA * (1f - t)
                    val volB = targetB * t
                    playerA.setVolume(max(0f, volA), max(0f, volA))
                    playerB.setVolume(max(0f, volB), max(0f, volB))
                    delay(50)
                }

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
                    } catch (_: Exception) {
                    }
                }

                val fadeSteps = 20
                val targetA = 1f - crossfadePos
                val targetB = crossfadePos

                repeat(fadeSteps) { i ->
                    val t = (i + 1) / fadeSteps.toFloat()
                    val volB = targetB * (1f - t)
                    val volA = targetA * t
                    playerB.setVolume(max(0f, volB), max(0f, volB))
                    playerA.setVolume(max(0f, volA), max(0f, volA))
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

    fun stopDj() {
        try {
            mpA?.stop()
        } catch (_: Exception) {}
        mpA?.release()
        mpA = null

        try {
            mpB?.stop()
        } catch (_: Exception) {}
        mpB?.release()
        mpB = null

        activeSlot = 0
        playingUri = null
        progress = 0f
        currentDurationMs = 0
        deckATitle = "A vide"
        deckBTitle = "B vide"
        deckAUri = null
        deckBUri = null
        queueInternal.clear()

        pushState()
    }

    fun release() {
        stopDj()
    }
}
