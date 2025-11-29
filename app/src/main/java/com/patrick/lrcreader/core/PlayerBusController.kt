package com.patrick.lrcreader.core

import android.content.Context
import android.media.MediaPlayer

/**
 * Bus de volume pour le LECTEUR principal.
 *
 * - stocke un MediaPlayer courant
 * - applique le niveau issu de PlayerVolumePrefs
 * - permet au BUS PRINCIPAL (fader LECTEUR) de rester synchro.
 */
object PlayerBusController {

    // MediaPlayer actuellement utilisé par PlayerScreen
    private var currentPlayer: MediaPlayer? = null

    /**
     * Appelé depuis PlayerScreen au démarrage de l'écran.
     * On garde une référence au player et on lui applique le volume courant.
     */
    fun attachPlayer(context: Context, player: MediaPlayer) {
        currentPlayer = player
        applyCurrentVolume(context)
    }

    /**
     * Applique le volume mémorisé (0f..1f) au MediaPlayer courant.
     * Utilisé quand on (re)lance la lecture.
     */
    fun applyCurrentVolume(context: Context) {
        val uiLevel = PlayerVolumePrefs.load(context).coerceIn(0f, 1f)
        val real = uiLevel            // si tu veux une courbe plus tard, tu modifies ici
        try {
            currentPlayer?.setVolume(real, real)
        } catch (_: Exception) {
            // on ne fait pas planter si le player n'est plus valide
        }
    }

    /**
     * Appelé depuis le BUS PRINCIPAL quand tu bouges le fader LECTEUR.
     * Mets à jour les prefs + le MediaPlayer s'il est attaché.
     */
    fun setUiLevelFromBusUi(context: Context, uiLevel: Float) {
        val clamped = uiLevel.coerceIn(0f, 1f)
        PlayerVolumePrefs.save(context, clamped)
        val real = clamped
        try {
            currentPlayer?.setVolume(real, real)
        } catch (_: Exception) {
            // sécurité
        }
    }

    // 🔁 Alias pour compatibilité si d’anciens écrans appellent setUiLevel(...)
    fun setUiLevel(context: Context, uiLevel: Float) {
        setUiLevelFromBusUi(context, uiLevel)
    }
}