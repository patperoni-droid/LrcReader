package com.patrick.lrcreader.core

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * Petit gestionnaire pour le "son de remplissage".
 * - lit le son choisi dans MoreScreen (stocké dans FillerSoundPrefs)
 * - en boucle
 * - volume réduit
 * - peut faire un fade-out puis s'arrêter
 */
object FillerSoundManager {

    // player interne, seulement pour le filler
    private var player: MediaPlayer? = null

    // job du fade-out en cours (pour l'annuler si on relance un titre)
    private var fadeJob: Job? = null

    /**
     * Démarre le son de remplissage si l'utilisateur en a choisi un.
     */
    fun startIfConfigured(context: Context) {
        val uri = FillerSoundPrefs.getFillerUri(context) ?: return
        startFromUri(context, uri)
    }

    /**
     * Lance réellement la lecture du filler.
     */
    private fun startFromUri(context: Context, uri: Uri) {
        stopNow()

        val p = MediaPlayer()
        try {
            p.setDataSource(context, uri)
            p.isLooping = true
            p.setVolume(FILLER_VOLUME, FILLER_VOLUME)
            p.prepare()
            p.start()
            player = p
        } catch (e: Exception) {
            e.printStackTrace()
            p.release()
            player = null
        }
    }

    /**
     * Fade-out sur [durationMs] puis stop et release.
     */
    fun fadeOutAndStop(durationMs: Long = 400) {
        val p = player ?: return

        // si un fade est déjà en cours, on l'annule
        fadeJob?.cancel()

        fadeJob = CoroutineScope(Dispatchers.Main).launch {
            val steps = 12
            val stepTime = durationMs / steps
            var currentVol = FILLER_VOLUME

            for (i in 0 until steps) {
                currentVol = max(0f, currentVol - (FILLER_VOLUME / steps))
                p.setVolume(currentVol, currentVol)
                delay(stepTime)
            }

            // on coupe et on libère
            try {
                p.stop()
            } catch (_: Exception) { }
            p.release()
            player = null
        }
    }

    /**
     * Coupe immédiatement le filler.
     */
    private fun stopNow() {
        fadeJob?.cancel()
        fadeJob = null
        player?.let { p ->
            try {
                p.stop()
            } catch (_: Exception) { }
            p.release()
        }
        player = null
    }

    private const val FILLER_VOLUME = 0.25f
}