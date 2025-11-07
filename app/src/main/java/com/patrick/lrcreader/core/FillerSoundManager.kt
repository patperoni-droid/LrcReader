package com.patrick.lrcreader.core

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Petit gestionnaire pour le "son de remplissage".
 * - lit en boucle
 * - volume réglable
 * - on peut faire un fadeOut avant de l’arrêter
 * - et on ne plante pas si le fichier n’est plus accessible
 */
object FillerSoundManager {

    private var player: MediaPlayer? = null
    private var fadeJob: Job? = null

    // volume qu’on garde en mémoire
    private var currentVolume: Float = DEFAULT_VOLUME

    /**
     * Démarre le son de remplissage si l’utilisateur en a choisi un.
     */
    fun startIfConfigured(context: Context) {
        val uri = FillerSoundPrefs.getFillerUri(context) ?: return
        // on récupère le volume sauvegardé
        currentVolume = FillerSoundPrefs.getFillerVolume(context)

        try {
            startFromUri(context, uri)
        } catch (e: Exception) {
            e.printStackTrace()
            // on efface pour ne pas replanter à chaque fois
            FillerSoundPrefs.clear(context)
            Toast.makeText(
                context,
                "Impossible de lire le fond sonore. Rechoisis le fichier.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * Pour l’UI : si ça joue → on stoppe, sinon on relance.
     */
    fun toggle(context: Context) {
        if (isPlaying()) {
            fadeOutAndStop(200)
        } else {
            startIfConfigured(context)
        }
    }

    /** Vrai si un fond sonore tourne. */
    fun isPlaying(): Boolean = player != null

    /**
     * Lance réellement la lecture du filler.
     */
    private fun startFromUri(context: Context, uri: Uri) {
        // si un player tourne déjà, on le coupe net
        stopNow()

        val mp = MediaPlayer()
        mp.setDataSource(context, uri)
        mp.isLooping = true
        mp.prepare()
        // on applique le volume courant
        mp.setVolume(currentVolume, currentVolume)
        mp.start()

        player = mp
    }

    /**
     * Appelé depuis l’écran "Plus" quand on bouge le slider.
     */
    fun setVolume(volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        currentVolume = v
        // on met à jour le player s’il tourne
        player?.setVolume(v, v)
    }

    /**
     * Arrêt avec petit fondu.
     */
    fun fadeOutAndStop(durationMs: Long = 200) {
        val p = player ?: return

        fadeJob?.cancel()
        fadeJob = CoroutineScope(Dispatchers.Main).launch {
            val startVol = currentVolume
            val steps = 16
            val stepTime = durationMs / steps
            for (i in 0..steps) {
                val factor = 1f - i / steps.toFloat()
                val v = (startVol * factor).coerceIn(0f, 1f)
                p.setVolume(v, v)
                delay(stepTime)
            }
            stopNow()
        }
    }

    /**
     * Arrêt immédiat.
     */
    private fun stopNow() {
        fadeJob?.cancel()
        fadeJob = null
        player?.let { mp ->
            try {
                mp.stop()
            } catch (_: Exception) {
            }
            mp.release()
        }
        player = null
    }

    private const val DEFAULT_VOLUME = 0.25f
}