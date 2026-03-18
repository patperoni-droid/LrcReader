package com.patrick.lrcreader.core.audio

import android.os.Build
import android.util.Log

/**
 * Bridge JNI : charge la lib native + expose des appels natifs.
 * Objectif : logs ultra clairs si la lib n'est pas chargée / absente / ABI incompatible.
 */
object SoundTouchBridge {

    private const val TAG = "AUDIO_TS"
    private const val TAG_HQ = "AUDIO_TS_HQ"
    private const val VERBOSE_PROCESS_LOGS = false

    @Volatile private var loadTried = false
    @Volatile private var loadedOk = false
    @Volatile private var availabilityChecked = false
    @Volatile private var availabilityCached = false
    @Volatile private var statusFlavor = "UNKNOWN"
    @Volatile private var statusSelfTest = false
    @Volatile private var statusNativeAvailable = false
    @Volatile private var statusLogged = false
    @Volatile private var laboUnavailableLogged = false

    data class HqStatus(
        val available: Boolean,
        val flavor: String,
        val selfTest: Boolean,
        val nativeAvailable: Boolean
    )

    /**
     * Tente de charger la lib native UNE fois.
     * Retourne true si OK.
     */
    fun ensureLoaded(): Boolean {
        if (loadTried) return loadedOk
        loadTried = true

        Log.d(TAG, "HQ_ABIS=${Build.SUPPORTED_ABIS.joinToString()}")

        // ⚠️ Noms possibles selon ton CMake / packaging.
        val candidates = listOf(
            "soundtouch_jni",
            "soundtouch_bridge",
            "soundtouch"
        )

        for (name in candidates) {
            try {
                System.loadLibrary(name)
                loadedOk = true
                Log.d(TAG, "HQ_JNI_LOADED_OK name=$name")
                break
            } catch (t: Throwable) {
                Log.e(TAG, "HQ_JNI_LOADED_KO name=$name", t)
            }
        }

        if (!loadedOk) {
            Log.e(TAG, "HQ_JNI_NOT_LOADED (no candidate worked)")
        }
        return loadedOk
    }

    // --- Natif (doit matcher soundtouch_bridge.cpp) ---
    private external fun nativeIsAvailable(): Boolean
    private external fun nativeGetBuildFlavor(): String
    private external fun nativeSelfTest(): Boolean

    external fun nativeCreate(): Long
    external fun nativeInit(handle: Long, sampleRate: Int, channels: Int): Boolean
    external fun nativeSetTempo(handle: Long, tempo: Float): Boolean
    external fun nativeSetPitchSemi(handle: Long, semi: Float): Boolean
    external fun nativeProcess(handle: Long, input: ByteArray): ByteArray?
    external fun nativeFlush(handle: Long): ByteArray?
    external fun nativeReset(handle: Long)
    external fun nativeRelease(handle: Long)

    /**
     * Vérifie que le natif répond.
     */
    fun isAvailable(): Boolean {
        if (!ensureLoaded()) return false
        if (availabilityChecked) return availabilityCached

        return synchronized(this) {
            if (availabilityChecked) return@synchronized availabilityCached

            val flavor = runCatching { nativeGetBuildFlavor() }
                .getOrElse {
                    Log.e(TAG, "HQ_NATIVE_CALL_FAILED nativeGetBuildFlavor()", it)
                    "UNKNOWN"
                }

            val nativeOk = runCatching { nativeIsAvailable() }
                .getOrElse {
                    Log.e(TAG, "HQ_NATIVE_CALL_FAILED nativeIsAvailable()", it)
                    false
                }

            val selfTest = if (nativeOk && flavor == "REAL") {
                runCatching { nativeSelfTest() }
                    .getOrElse {
                        Log.e(TAG, "HQ_NATIVE_CALL_FAILED nativeSelfTest()", it)
                        false
                    }
            } else {
                false
            }

            Log.d(TAG, "HQ_FLAVOR=$flavor")
            Log.d(TAG, "HQ_NATIVE_AVAILABLE=$nativeOk")
            Log.d(TAG, "HQ_SELFTEST=$selfTest")

            val ok = nativeOk && flavor == "REAL" && selfTest
            if (!ok) {
                Log.e(TAG, "HQ_NOT_READY flavor=$flavor native=$nativeOk selfTest=$selfTest")
            }

            statusFlavor = flavor
            statusNativeAvailable = nativeOk
            statusSelfTest = selfTest
            availabilityCached = ok
            availabilityChecked = true
            ok
        }
    }

    fun getStatus(): HqStatus {
        val available = isAvailable()
        return HqStatus(
            available = available,
            flavor = statusFlavor,
            selfTest = statusSelfTest,
            nativeAvailable = statusNativeAvailable
        )
    }

    fun logStatusOnce(reason: String = ""): HqStatus {
        val status = getStatus()
        if (!statusLogged) {
            synchronized(this) {
                if (!statusLogged) {
                    Log.i(
                        TAG,
                        "HQ_STATUS available=${status.available} flavor=${status.flavor} selftest=${status.selfTest} reason=$reason"
                    )
                    statusLogged = true
                }
            }
        }
        return status
    }

    fun logLaboUnavailableOnce(reason: String = "") {
        if (laboUnavailableLogged) return
        synchronized(this) {
            if (laboUnavailableLogged) return
            val status = getStatus()
            Log.e(
                TAG,
                "HQ OFF (STUB) => audio degrade, pitch/speed disabled; available=${status.available} flavor=${status.flavor} selftest=${status.selfTest} reason=$reason"
            )
            laboUnavailableLogged = true
        }
    }

    /**
     * Utilitaire: s'assure que JNI est chargé + disponible, et log clairement sinon.
     */
    fun ensureAvailableOrLog(reason: String = ""): Boolean {
        if (!ensureLoaded()) {
            Log.e(TAG, "HQ_NOT_READY ensureLoaded=false reason=$reason")
            return false
        }
        val ok = isAvailable()
        if (!ok) Log.e(TAG, "HQ_NOT_READY isAvailable=false reason=$reason")
        return ok
    }

    fun processWithLogs(handle: Long, input: ByteArray): ByteArray? {
        if (!ensureAvailableOrLog("processWithLogs")) return null

        if (!VERBOSE_PROCESS_LOGS) {
            return try {
                nativeProcess(handle, input)
            } catch (t: Throwable) {
                Log.e(TAG_HQ, "K!! nativeProcess CRASH", t)
                null
            }
        }

        Log.i(TAG_HQ, "K-> nativeProcess handle=$handle inBytes=${input.size}")

        val t0 = android.os.SystemClock.elapsedRealtime()
        val out = try {
            nativeProcess(handle, input)
        } catch (t: Throwable) {
            Log.e(TAG_HQ, "K!! nativeProcess CRASH", t)
            null
        }
        val dt = android.os.SystemClock.elapsedRealtime() - t0

        Log.i(TAG_HQ, "K<- nativeProcess outBytes=${out?.size ?: -1} dtMs=$dt")
        return out
    }

}
