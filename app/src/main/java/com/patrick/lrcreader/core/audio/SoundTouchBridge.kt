package com.patrick.lrcreader.core.audio

import android.os.Build
import android.util.Log

/**
 * Bridge JNI : charge la lib native + expose des appels natifs.
 * Objectif : logs ultra clairs si la lib n'est pas chargée / absente / ABI incompatible.
 */
object SoundTouchBridge {

    private const val TAG = "AUDIO_TS"

    @Volatile private var loadTried = false
    @Volatile private var loadedOk = false

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
        return try {
            val ok = nativeIsAvailable()
            Log.d(TAG, "HQ_NATIVE_AVAILABLE=$ok")
            ok
        } catch (t: Throwable) {
            Log.e(TAG, "HQ_NATIVE_CALL_FAILED nativeIsAvailable()", t)
            false
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

    private const val TAG_HQ = "AUDIO_TS_HQ"

    fun processWithLogs(handle: Long, input: ByteArray): ByteArray? {
        if (!ensureAvailableOrLog("processWithLogs")) return null

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