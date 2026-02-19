package com.patrick.lrcreader.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * STUB de compatibilité pour recompiler.
 * MixerHomePreviewScreen attend:
 * - start(...) (appelé avec args)
 * - playerMeter / fillerMeter / djMeter en Float
 */
object MeterManager {

    private val _playerMeter = MutableStateFlow(0f)
    private val _fillerMeter = MutableStateFlow(0f)
    private val _djMeter = MutableStateFlow(0f)

    val playerMeter: StateFlow<Float> = _playerMeter
    val fillerMeter: StateFlow<Float> = _fillerMeter
    val djMeter: StateFlow<Float> = _djMeter

    // L'écran appelle start(...) avec des paramètres → on accepte tout
    fun start(vararg ignored: Any?) { /* no-op */ }
    fun stop() { /* no-op */ }

    // Optionnel: pousser une valeur (0f..1f)
    fun setPlayer(v: Float) { _playerMeter.value = v }
    fun setFiller(v: Float) { _fillerMeter.value = v }
    fun setDj(v: Float) { _djMeter.value = v }
}
