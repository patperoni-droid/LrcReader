package com.patrick.lrcreader.core

import kotlin.math.pow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.patrick.lrcreader.core.dj.DjEngine

/**
 * Contrôleur centralisé pour le BUS DJ.
 * On garde un seul niveau global (0..1) partagé entre :
 * - l'écran DJ
 * - le bus principal (Mixer)
 * - DjEngine (master volume)
 */

object DjBusController {

    // Niveau UI global du bus DJ (0..1)
    private val _uiLevel = MutableStateFlow(1f)
    val uiLevel: StateFlow<Float> = _uiLevel.asStateFlow()

    // Pour éviter la boucle infinie UI -> moteur -> UI
    private var internalUpdate = false

    private fun uiToEngineLevel(uiLevel: Float): Float {
        val clamped = uiLevel.coerceIn(0f, 1f)
        return clamped.pow(3)
    }

    private fun engineToUiLevel(engineLevel: Float): Float {
        val clamped = engineLevel.coerceIn(0f, 1f)
        return kotlin.math.cbrt(clamped.toDouble()).toFloat()
    }

    /** Récupère le niveau actuel */
    fun getUiLevel(): Float = _uiLevel.value

    /** Définit le niveau global UI + moteur DJ */
    fun setUiLevel(u: Float) {
        val level = u.coerceIn(0f, 1f)

        if (!internalUpdate) {
            _uiLevel.value = level
            // on envoie au moteur DJ
            DjEngine.setMasterVolume(uiToEngineLevel(level))
        }
    }

    /** Appelé uniquement par DjEngine pour notifier l’UI (rarement utile) */
    fun syncFromEngine(level: Float) {
        val l = engineToUiLevel(level)
        internalUpdate = true
        _uiLevel.value = l
        internalUpdate = false
    }
}
