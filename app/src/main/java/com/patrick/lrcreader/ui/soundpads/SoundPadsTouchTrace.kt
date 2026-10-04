package com.patrick.lrcreader.ui.soundpads

import android.util.Log
import android.os.SystemClock
import com.patrick.lrcreader.exo.BuildConfig

/** Temporary, opt-in Logcat diagnostics; never consumes input or writes pad/bus data. */
internal object SoundPadsTouchTrace {
    const val TAG = "SoundPadsTouch"
    val enabled get() = BuildConfig.DEBUG && Log.isLoggable(TAG, Log.DEBUG)
    fun event(event: String, padId: String?, slot: Int, detail: String = "") {
        if (enabled) Log.d(TAG, "$event pad=$padId slot=$slot uptimeMs=${SystemClock.uptimeMillis()} $detail")
    }
}
