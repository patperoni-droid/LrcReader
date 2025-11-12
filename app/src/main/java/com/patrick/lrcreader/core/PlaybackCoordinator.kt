package com.patrick.lrcreader.core

import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Coordonne le lecteur principal et le fond sonore :
 * - Quand un titre principal démarre → stoppe le filler
 * - Quand il s’arrête → relance le filler si activé
 */
object PlaybackCoordinator {

    private val _isMainPlaying = AtomicBoolean(false)
    val isMainPlaying: Boolean get() = _isMainPlaying.get()

    /** Appelé juste avant de lancer un titre principal */
    fun onMainWillStart(context: Context) {
        _isMainPlaying.set(true)
        // coupe immédiatement le fond sonore
        FillerSoundManager.fadeOutAndStop(150)
    }

    /** Appelé quand le titre principal est fini ou stoppé */
    fun onMainStopped(context: Context) {
        _isMainPlaying.set(false)
        // relance le fond sonore s'il est activé
        FillerSoundManager.startIfConfigured(context)
    }
}