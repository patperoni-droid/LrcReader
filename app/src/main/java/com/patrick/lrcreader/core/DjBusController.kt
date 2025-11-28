package com.patrick.lrcreader.core

/**
 * Bus de volume global pour le DJ.
 *
 * - 0f = coupé
 * - 1f = plein pot
 *
 * Pour l’instant on ne fait que stocker la valeur en mémoire.
 * (On pourra plus tard la persister en prefs si tu veux.)
 */
object DjBusController {

    // Niveau UI (0f..1f), 1f par défaut
    private var uiLevel: Float = 1f

    /** Valeur brute 0f..1f utilisée par l’UI (faders, sliders) */
    fun getUiLevel(): Float = uiLevel

    /** Mise à jour depuis un fader UI */
    fun setUiLevel(value: Float) {
        uiLevel = value.coerceIn(0f, 1f)
    }

    /**
     * Applique le bus DJ sur un volume de base.
     * Exemple : base = 0.8f, bus = 0.5f → 0.4f
     */
    fun applyOn(baseVolume: Float): Float {
        val bus = uiLevel.coerceIn(0f, 1f)
        return (baseVolume.coerceIn(0f, 1f) * bus).coerceIn(0f, 1f)
    }
}