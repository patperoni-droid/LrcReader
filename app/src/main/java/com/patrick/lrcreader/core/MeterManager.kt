package com.patrick.lrcreader.core

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.patrick.lrcreader.exo.BuildConfig
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
import kotlin.math.log10
import kotlin.math.max

/**
 * VU-meters non intrusifs:
 * - ne modifie pas l'audio
 * - se base sur l'etat "isPlaying" + niveaux de bus existants
 */
object MeterManager {

    private const val TAG = "METER"
    private const val TICK_MS = 80L
    private const val ATTACK = 0.55f
    private const val RELEASE = 0.12f
    private const val LOG_EVERY_MS = 1000L
    private const val PCM_STALE_MS = 500L
    private const val PCM_UI_FLOOR = 1e-4f
    private const val PCM_DB_FLOOR = -60f
    private const val PCM_DB_SPAN = 60f

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var meterJob: Job? = null
    @Volatile private var appContext: Context? = null
    @Volatile private var lastLogTs = 0L
    @Volatile private var playerPcmRms = 0f
    @Volatile private var playerPcmPeak = 0f
    @Volatile private var playerPcmTs = 0L
    @Volatile private var fillerPcmRms = 0f
    @Volatile private var fillerPcmPeak = 0f
    @Volatile private var fillerPcmTs = 0L
    @Volatile private var djPcmRms = 0f
    @Volatile private var djPcmPeak = 0f
    @Volatile private var djPcmTs = 0L

    private val _playerMeter = MutableStateFlow(0f)
    private val _fillerMeter = MutableStateFlow(0f)
    private val _djMeter = MutableStateFlow(0f)
    private val _hasFreshPcm = MutableStateFlow(false)

    val playerMeter: StateFlow<Float> = _playerMeter.asStateFlow()
    val fillerMeter: StateFlow<Float> = _fillerMeter.asStateFlow()
    val djMeter: StateFlow<Float> = _djMeter.asStateFlow()
    val hasFreshPcm: StateFlow<Boolean> = _hasFreshPcm.asStateFlow()

    fun onMasterPcm(rms: Float, peak: Float) {
        playerPcmRms = rms.coerceIn(0f, 1f)
        playerPcmPeak = peak.coerceIn(0f, 1f)
        playerPcmTs = SystemClock.elapsedRealtime()
    }

    fun onFillerPcm(rms: Float, peak: Float) {
        fillerPcmRms = rms.coerceIn(0f, 1f)
        fillerPcmPeak = peak.coerceIn(0f, 1f)
        fillerPcmTs = SystemClock.elapsedRealtime()
    }

    fun onDjPcm(rms: Float, peak: Float) {
        djPcmRms = rms.coerceIn(0f, 1f)
        djPcmPeak = peak.coerceIn(0f, 1f)
        djPcmTs = SystemClock.elapsedRealtime()
    }

    fun start(vararg ignored: Any?) {
        ignored.firstOrNull { it is Context }
            ?.let { appContext = (it as Context).applicationContext }

        if (meterJob?.isActive == true) return

        meterJob = scope.launch {
            Log.d(TAG, "MeterManager start ctx=${appContext != null}")
            while (isActive) {
                val now = SystemClock.elapsedRealtime()

                val playerActive = PlaybackCoordinator.isMainPlaying && !DjEngine.isPlaying()

                val pTarget = if (playerActive) {
                    loadPcmUiLevel(now, playerPcmRms, playerPcmPeak, playerPcmTs)
                } else {
                    0f
                }
                val fTarget = if (FillerSoundManager.isPlaying()) {
                    loadPcmUiLevel(now, fillerPcmRms, fillerPcmPeak, fillerPcmTs)
                } else {
                    0f
                }
                val dTarget = if (DjEngine.isPlaying()) {
                    loadPcmUiLevel(now, djPcmRms, djPcmPeak, djPcmTs)
                } else {
                    0f
                }

                _hasFreshPcm.value = hasFreshTap(now, playerPcmTs) ||
                    hasFreshTap(now, fillerPcmTs) ||
                    hasFreshTap(now, djPcmTs)

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
        _hasFreshPcm.value = false
        playerPcmRms = 0f
        playerPcmPeak = 0f
        playerPcmTs = 0L
        fillerPcmRms = 0f
        fillerPcmPeak = 0f
        fillerPcmTs = 0L
        djPcmRms = 0f
        djPcmPeak = 0f
        djPcmTs = 0L
        Log.d(TAG, "MeterManager stop")
    }

    fun setPlayer(v: Float) { _playerMeter.value = v.coerceIn(0f, 1f) }
    fun setFiller(v: Float) { _fillerMeter.value = v.coerceIn(0f, 1f) }
    fun setDj(v: Float) { _djMeter.value = v.coerceIn(0f, 1f) }

    private fun loadPcmUiLevel(now: Long, rms: Float, peak: Float, ts: Long): Float {
        if (!hasFreshTap(now, ts)) return 0f
        val linear = max(rms, peak).coerceIn(PCM_UI_FLOOR, 1f)
        val db = 20f * log10(linear)
        return ((db - PCM_DB_FLOOR) / PCM_DB_SPAN).coerceIn(0f, 1f)
    }

    private fun hasFreshTap(now: Long, ts: Long): Boolean {
        return ts > 0L && (now - ts) <= PCM_STALE_MS
    }

    private fun smooth(current: Float, target: Float): Float {
        val k = if (target > current) ATTACK else RELEASE
        return (current + (target - current) * k).coerceIn(0f, 1f)
    }

    private fun logMetersIfNeeded(pTarget: Float, dTarget: Float, fTarget: Float) {
        if (!BuildConfig.DEBUG) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastLogTs < LOG_EVERY_MS) return
        lastLogTs = now

        Log.d(
            TAG,
            "VU p=${fmt(_playerMeter.value)} d=${fmt(_djMeter.value)} f=${fmt(_fillerMeter.value)} " +
                "targets p=${fmt(pTarget)} d=${fmt(dTarget)} f=${fmt(fTarget)} " +
                "pcmP rms=${fmt(playerPcmRms)} peak=${fmt(playerPcmPeak)} " +
                "pcmD rms=${fmt(djPcmRms)} peak=${fmt(djPcmPeak)} " +
                "pcmF rms=${fmt(fillerPcmRms)} peak=${fmt(fillerPcmPeak)} fresh=${_hasFreshPcm.value}"
        )
    }

    private fun fmt(v: Float): String = String.format(Locale.US, "%.2f", v)
}
