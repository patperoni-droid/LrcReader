package com.patrick.lrcreader.core

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.patrick.lrcreader.core.dj.DjEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * VU-meters non intrusifs:
 * - ne modifie pas l'audio
 * - se base sur l'etat "isPlaying" + niveaux de bus existants
 */
object MeterManager {

    private const val TAG = "METER"
    private const val TICK_MS = 80L
    private const val ATTACK = 0.45f
    private const val RELEASE = 0.12f
    private const val LOG_EVERY_MS = 1000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var meterJob: Job? = null
    @Volatile private var appContext: Context? = null
    @Volatile private var lastLogTs = 0L

    private val _playerMeter = MutableStateFlow(0f)
    private val _fillerMeter = MutableStateFlow(0f)
    private val _djMeter = MutableStateFlow(0f)

    val playerMeter: StateFlow<Float> = _playerMeter.asStateFlow()
    val fillerMeter: StateFlow<Float> = _fillerMeter.asStateFlow()
    val djMeter: StateFlow<Float> = _djMeter.asStateFlow()

    fun start(vararg ignored: Any?) {
        ignored.firstOrNull { it is Context }
            ?.let { appContext = (it as Context).applicationContext }

        if (meterJob?.isActive == true) return

        meterJob = scope.launch {
            Log.d(TAG, "MeterManager start ctx=${appContext != null}")
            while (isActive) {
                val ctx = appContext

                val playerActive = PlaybackCoordinator.isMainPlaying && !DjEngine.isPlaying()

                val pTarget = if (playerActive) {
                    loadPlayerBusLevel(ctx)
                } else {
                    0f
                }
                val fTarget = if (FillerSoundManager.isPlaying()) {
                    loadFillerBusLevel(ctx)
                } else {
                    0f
                }
                val dTarget = if (DjEngine.isPlaying()) {
                    DjBusController.getUiLevel().coerceIn(0f, 1f)
                } else {
                    0f
                }

                _playerMeter.value = smooth(_playerMeter.value, pTarget)
                _fillerMeter.value = smooth(_fillerMeter.value, fTarget)
                _djMeter.value = smooth(_djMeter.value, dTarget)

                logMetersIfNeeded(pTarget, dTarget, fTarget)
                delay(TICK_MS)
            }
        }
    }

    fun stop() {
        meterJob?.cancel()
        meterJob = null
        _playerMeter.value = 0f
        _fillerMeter.value = 0f
        _djMeter.value = 0f
        Log.d(TAG, "MeterManager stop")
    }

    fun setPlayer(v: Float) { _playerMeter.value = v.coerceIn(0f, 1f) }
    fun setFiller(v: Float) { _fillerMeter.value = v.coerceIn(0f, 1f) }
    fun setDj(v: Float) { _djMeter.value = v.coerceIn(0f, 1f) }

    private fun loadPlayerBusLevel(context: Context?): Float {
        if (context == null) return 1f
        return runCatching { PlayerVolumePrefs.load(context) }
            .getOrDefault(1f)
            .coerceIn(0f, 1f)
    }

    private fun loadFillerBusLevel(context: Context?): Float {
        if (context == null) return 0.25f
        return runCatching { FillerSoundPrefs.getFillerVolume(context) }
            .getOrDefault(0.25f)
            .coerceIn(0f, 1f)
    }

    private fun smooth(current: Float, target: Float): Float {
        val k = if (target > current) ATTACK else RELEASE
        return (current + (target - current) * k).coerceIn(0f, 1f)
    }

    private fun logMetersIfNeeded(pTarget: Float, dTarget: Float, fTarget: Float) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastLogTs < LOG_EVERY_MS) return
        lastLogTs = now

        Log.d(
            TAG,
            "VU p=${fmt(_playerMeter.value)} d=${fmt(_djMeter.value)} f=${fmt(_fillerMeter.value)} " +
                "targets p=${fmt(pTarget)} d=${fmt(dTarget)} f=${fmt(fTarget)}"
        )
    }

    private fun fmt(v: Float): String = String.format(Locale.US, "%.2f", v)
}
