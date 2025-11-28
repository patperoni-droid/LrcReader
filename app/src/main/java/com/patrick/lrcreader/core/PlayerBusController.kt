package com.patrick.lrcreader.core

import android.content.Context
import android.media.MediaPlayer
import androidx.core.content.edit
import kotlin.math.pow

/**
 * Contrôleur global du bus LECTEUR.
 * Permet de régler le volume du MediaPlayer depuis n'importe quel écran
 * (console, player, etc.).
 */
object PlayerBusController {

    private const val PREFS_NAME = "player_volume_prefs"
    private const val KEY_VOLUME = "player_volume_ui" // 0f..1f

    // MediaPlayer actuellement utilisé par le lecteur principal
    private var mediaPlayer: MediaPlayer? = null

    /** Courbe douce :  u → u³  */
    private fun uiToReal(u: Float): Float {
        val clamped = u.coerceIn(0f, 1f)
        return clamped.pow(3)
    }

    /** Sauve le volume UI (0f..1f) dans les prefs. */
    private fun saveUiVolume(context: Context, uiLevel: Float) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            putFloat(KEY_VOLUME, uiLevel.coerceIn(0f, 1f))
        }
    }

    /** Charge le volume UI (0f..1f) depuis les prefs. */
    fun loadUiVolume(context: Context): Float {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getFloat(KEY_VOLUME, 1f).coerceIn(0f, 1f)
    }

    /** Appelé depuis PlayerScreen pour brancher le MediaPlayer sur le bus. */
    fun attachPlayer(context: Context, player: MediaPlayer) {
        mediaPlayer = player
        // On applique immédiatement le volume sauvegardé
        applyCurrentVolume(context)
    }

    /** Optionnel, si un jour tu veux détacher. */
    fun detachPlayer() {
        mediaPlayer = null
    }

    /**
     * Appelé depuis la console (fader LECTEUR).
     * - Sauve uiLevel dans les prefs
     * - Applique immédiatement au MediaPlayer si attaché.
     */
    fun setUiVolumeFromMixer(context: Context, uiLevel: Float) {
        val clamped = uiLevel.coerceIn(0f, 1f)
        saveUiVolume(context, clamped)

        val real = uiToReal(clamped)
        mediaPlayer?.let { mp ->
            runCatching {
                mp.setVolume(real, real)
            }.onFailure { it.printStackTrace() }
        }
    }

    /**
     * Appelé côté Player (au démarrage ou resume).
     * Lit les prefs et applique au MediaPlayer.
     */
    fun applyCurrentVolume(context: Context) {
        val ui = loadUiVolume(context)
        val real = uiToReal(ui)
        mediaPlayer?.let { mp ->
            runCatching {
                mp.setVolume(real, real)
            }.onFailure { it.printStackTrace() }
        }
    }
}