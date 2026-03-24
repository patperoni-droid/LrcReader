@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.patrick.lrcreader.core

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import com.patrick.lrcreader.core.audio.AudioEngine

object PlayerBusController {

    fun attachPlayer(context: Context, _player: ExoPlayer) {
        applyCurrentVolume(context)
    }

    fun applyCurrentVolume(context: Context) {
        val uiLevel = PlayerVolumePrefs.load(context).coerceIn(0f, 1f)

        // Chef du mix
        AudioEngine.setPlayerBusLevel(uiLevel)
        AudioEngine.reapplyMixNow()
    }

    fun setUiLevelFromBusUi(context: Context, uiLevel: Float) {
        val clamped = uiLevel.coerceIn(0f, 1f)
        PlayerVolumePrefs.save(context, clamped)

        AudioEngine.setPlayerBusLevel(clamped)
        AudioEngine.reapplyMixNow()
    }

    fun setUiLevel(context: Context, uiLevel: Float) {
        setUiLevelFromBusUi(context, uiLevel)
    }
}
