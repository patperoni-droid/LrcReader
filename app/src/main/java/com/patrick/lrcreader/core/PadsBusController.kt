package com.patrick.lrcreader.core

import android.content.Context
import android.os.Looper
import com.patrick.lrcreader.core.soundpads.SoundPadsPrototypeEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One persisted UI level for both bus surfaces and the dedicated one-voice Pads player. */
object PadsBusController {
    private val initialization = Mutex()
    private val mutableUiLevel = MutableStateFlow(PadsVolumePrefs.DEFAULT_UI_LEVEL)
    val uiLevel = mutableUiLevel.asStateFlow()
    private val mutableReady = MutableStateFlow(false)
    val ready = mutableReady.asStateFlow()
    private var engine: SoundPadsPrototypeEngine? = null

    suspend fun initialize(context: Context) = initialization.withLock {
        if (!mutableReady.value) {
            // Prepare the preference before exposing editable faders or playable pads.
            val restored = withContext(Dispatchers.IO) { PadsVolumePrefs.load(context.applicationContext) }
            withContext(Dispatchers.Main.immediate) {
                mutableUiLevel.value = restored
                mutableReady.value = true
                engine?.applyBusVolume()
            }
        }
    }

    fun setUiLevel(context: Context, level: Float) {
        checkMainThread()
        if (!mutableReady.value || !level.isFinite()) return
        val safe = level.coerceIn(0f, 1f)
        // SharedPreferences was prepared by initialize; apply() persists asynchronously.
        PadsVolumePrefs.save(context.applicationContext, safe)
        mutableUiLevel.value = safe
        engine?.applyBusVolume() // Synchronous, no prepare/seek/media replacement.
    }

    internal fun attachEngine(value: SoundPadsPrototypeEngine) {
        checkMainThread()
        engine = value
        value.applyBusVolume()
    }

    internal fun detachEngine(value: SoundPadsPrototypeEngine) {
        checkMainThread()
        if (engine === value) engine = null
    }

    fun stopAll() {
        checkMainThread()
        engine?.stopAll()
    }

    private fun checkMainThread() = check(Looper.myLooper() == Looper.getMainLooper())
}
