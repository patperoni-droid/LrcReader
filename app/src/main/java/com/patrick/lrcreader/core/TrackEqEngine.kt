package com.patrick.lrcreader.core

import android.media.audiofx.Equalizer

/**
 * Petit moteur d'EQ 3 bandes branché sur la session audio du player.
 */
object TrackEqEngine {

    private var equalizer: Equalizer? = null

    /**
     * À appeler quand on a un nouvel audioSessionId (MediaPlayer créé / recréé).
     */
    fun attachToSession(audioSessionId: Int) {
        release()

        if (audioSessionId == 0) return

        runCatching {
            Equalizer(0, audioSessionId).apply {
                enabled = true
            }.also { eq ->
                equalizer = eq
            }
        }
    }

    /**
     * index = 0 -> graves, 1 -> médiums, 2 -> aigus
     * gainDb = -12..+12
     */
    fun setBandGain(index: Int, gainDb: Float) {
        val eq = equalizer ?: return

        val bandCount = eq.numberOfBands.toInt()
        if (bandCount <= 0) return

        // On mappe 0,1,2 sur les bandes dispo
        val clampedBand: Short = when {
            index <= 0 -> 0
            index >= bandCount - 1 -> (bandCount - 1).toShort()
            else -> index.toShort()
        }

        // range système en milli-dB (ex : [-1500, +1500])
        val range = eq.bandLevelRange
        val min = range[0].toFloat()
        val max = range[1].toFloat()

        val norm = ((gainDb.coerceIn(-12f, 12f) + 12f) / 24f) // 0..1
        val level = (min + (max - min) * norm).toInt().toShort()

        runCatching {
            eq.setBandLevel(clampedBand, level)
        }
    }

    fun resetAll() {
        val eq = equalizer ?: return
        val bands = eq.numberOfBands.toInt()
        val range = eq.bandLevelRange
        val mid = ((range[0] + range[1]) / 2).toShort()
        for (b in 0 until bands) {
            runCatching { eq.setBandLevel(b.toShort(), mid) }
        }
    }

    fun release() {
        runCatching { equalizer?.release() }
        equalizer = null
    }

    /**
     * Helper pour appliquer les 3 bandes d'un coup.
     */
    fun setBands(
        lowDb: Float,
        midDb: Float,
        highDb: Float
    ) {
        setBandGain(0, lowDb)   // Graves
        setBandGain(1, midDb)   // Médiums
        setBandGain(2, highDb)  // Aigus
    }
}