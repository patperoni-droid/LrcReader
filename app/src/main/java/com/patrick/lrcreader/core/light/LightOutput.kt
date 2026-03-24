package com.patrick.lrcreader.core.light

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface LightOutput {
    fun apply(sceneState: LightSceneState)
    fun reset()
}

object SimulatorLightOutput : LightOutput {

    private val lock = Any()
    private val _sceneState = MutableStateFlow(LightSceneState.off())
    private var runtimeSceneState: LightSceneState = LightSceneState.off()
    private var previewOverrideSceneState: LightSceneState? = null
    val sceneState: StateFlow<LightSceneState> = _sceneState.asStateFlow()

    override fun apply(sceneState: LightSceneState) {
        synchronized(lock) {
            runtimeSceneState = sceneState
            if (previewOverrideSceneState == null) {
                _sceneState.value = sceneState
            }
        }
    }

    override fun reset() {
        synchronized(lock) {
            runtimeSceneState = LightSceneState.off()
            if (previewOverrideSceneState == null) {
                _sceneState.value = runtimeSceneState
            }
        }
    }

    fun showPreviewOverride(sceneState: LightSceneState) {
        synchronized(lock) {
            previewOverrideSceneState = sceneState
            _sceneState.value = sceneState
        }
    }

    fun clearPreviewOverride() {
        synchronized(lock) {
            previewOverrideSceneState = null
            _sceneState.value = runtimeSceneState
        }
    }
}
