package com.patrick.lrcreader.core.audio

import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import com.patrick.lrcreader.core.MeterManager
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * AudioProcessor Media3: applique SoundTouch (tempo + pitch) sur PCM 16-bit.
 *
 * IMPORTANT: isActive() retourne TOUJOURS true pour que Media3 garde ce processor
 * dans la chaîne audio, même quand enabled=false (passthrough).
 */
@UnstableApi
class SoundTouchAudioProcessor : AudioProcessor {

    companion object {
        private const val TAG = "AUDIO_TS"
        private const val TAG_HQ = "AUDIO_TS_HQ"
        private const val METER_SAMPLE_STRIDE = 2
        private const val VERBOSE_HQ_PROCESS_LOGS = false
        private val EMPTY_BUFFER: ByteBuffer =
            ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
    }

    // --- State ---
    @Volatile private var enabled = false
    @Volatile private var pendingTempoRatio = 1f
    @Volatile private var pendingPitchSemi = 0f
    @Volatile private var tempoPitchDirty = true

    private var configured = false
    private var inputFormat: AudioProcessor.AudioFormat = AudioProcessor.AudioFormat.NOT_SET
    private var outputFormat: AudioProcessor.AudioFormat = AudioProcessor.AudioFormat.NOT_SET

    private var handle: Long = 0L
    private var outputBuffer: ByteBuffer = EMPTY_BUFFER
    private var inputEnded: Boolean = false
    private var initializedNative = false

    // Debug: éviter 2000 logs/sec
    private var dbgCount = 0
    private var dbgZeros = 0

    // ------------------------------------------------------------
    // Public controls
    // ------------------------------------------------------------

    fun setEnabled(value: Boolean) {
        if (enabled == value) return
        enabled = value
        Log.d(TAG, "HQ_ENABLED=$enabled")
        flush()
    }

    fun ensureHqInit(): Boolean {
        if (!enabled) return false

        // déjà OK
        if (handle != 0L && initializedNative) return true

        val available = SoundTouchBridge.isAvailable()
        if (!available) {
            Log.e(TAG, "HQ_INIT_KO (SoundTouchBridge.isAvailable=false)")
            releaseNative()
            initializedNative = false
            return false
        }

        if (handle == 0L) {
            handle = SoundTouchBridge.nativeCreate()
            if (handle == 0L) {
                Log.e(TAG, "HQ_INIT_KO (nativeCreate returned 0)")
                initializedNative = false
                return false
            }
        }

        if (inputFormat == AudioProcessor.AudioFormat.NOT_SET) {
            Log.w(TAG, "HQ_INIT_WAIT (inputFormat NOT_SET)")
            initializedNative = false
            return false
        }

        val ok = SoundTouchBridge.nativeInit(handle, inputFormat.sampleRate, inputFormat.channelCount)
        if (!ok) {
            Log.e(TAG, "HQ_INIT_KO (nativeInit=false)")
            releaseNative()
            initializedNative = false
            return false
        }

        initializedNative = true
        tempoPitchDirty = true

        Log.d(TAG, "HQ_INIT_OK (bridge available)")
        return true
    }

    fun setTempoRatioAndPitchSemi(tempoRatio: Float, pitchSemi: Float) {
        pendingTempoRatio = tempoRatio.coerceIn(0.5f, 2.0f)
        pendingPitchSemi = pitchSemi.coerceIn(-12f, 12f)
        tempoPitchDirty = true
        Log.d(
            TAG,
            "HQ_SET tempo=${fmtTempo(pendingTempoRatio)} pitchSemi=${fmtPitchSemi(pendingPitchSemi)} ready=${isNativeReady()} dirty=$tempoPitchDirty"
        )
    }

    fun setTempoAndPitch(speed: Float, pitchRatio: Float) {
        val safePitch = pitchRatio.coerceIn(0.5f, 2.0f)
        val semi = if (abs(safePitch - 1f) < 0.00001f) 0f else 12f * (ln(safePitch) / ln(2f))
        setTempoRatioAndPitchSemi(speed, semi)
    }

    // ------------------------------------------------------------
    // AudioProcessor (Media3)
    // ------------------------------------------------------------

    // IMPORTANT: toujours actif => le processor reste dans la chaîne audio
    override fun isActive(): Boolean = true

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        inputFormat = inputAudioFormat

        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }

        outputFormat = inputAudioFormat
        configured = true
        inputEnded = false
        outputBuffer = EMPTY_BUFFER

        Log.d(TAG, "HQ_CONFIGURE enabled=$enabled format=$inputAudioFormat")

        // Si HQ ON, on tente l'init (maintenant le format est connu)
        if (enabled) {
            ensureHqInit()
        }

        return outputFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        inputEnded = false
        publishPcmMeter(inputBuffer)

        val h = handle
        dbgCount++

        // Logs chauds désactivés par défaut : trop coûteux sur appareil réel.
        if (VERBOSE_HQ_PROCESS_LOGS && dbgCount % 30 == 0) {
            Log.i(
                TAG_HQ,
                "queueInput remaining=${inputBuffer.remaining()} enabled=$enabled h=$h fmt=${inputFormat.sampleRate}Hz/${inputFormat.channelCount}ch"
            )
        }

        // PASSTHROUGH si pas en HQ
        if (!enabled || h == 0L || !initializedNative || inputFormat == AudioProcessor.AudioFormat.NOT_SET) {
            outputBuffer = inputBuffer.slice()
            inputBuffer.position(inputBuffer.limit())
            return
        }

        applyTempoPitchIfDirty(h)

        val inBytes = ByteArray(inputBuffer.remaining())
        inputBuffer.get(inBytes)

        val outBytes = SoundTouchBridge.processWithLogs(h, inBytes) ?: run {
            outputBuffer = EMPTY_BUFFER
            return
        }

        if (outBytes.isEmpty()) {
            dbgZeros++
            if (VERBOSE_HQ_PROCESS_LOGS && (dbgZeros <= 10 || dbgZeros % 30 == 0)) {
                Log.w(TAG_HQ, "nativeProcess outBytes=0 (zeros=$dbgZeros)")
            }
            outputBuffer = EMPTY_BUFFER
            return
        }

        outputBuffer = ByteBuffer
            .allocateDirect(outBytes.size)
            .order(ByteOrder.nativeOrder())
            .apply {
                put(outBytes)
                flip()
            }
    }

    override fun queueEndOfStream() {
        inputEnded = true

        val h = handle
        if (!enabled || h == 0L || !initializedNative) return

        val outBytes = SoundTouchBridge.nativeFlush(h) ?: ByteArray(0)
        outputBuffer = if (outBytes.isEmpty()) {
            EMPTY_BUFFER
        } else {
            ByteBuffer.allocateDirect(outBytes.size)
                .order(ByteOrder.nativeOrder())
                .apply {
                    put(outBytes)
                    flip()
                }
        }
    }

    override fun getOutput(): ByteBuffer {
        val out = outputBuffer
        outputBuffer = EMPTY_BUFFER
        return out
    }

    override fun isEnded(): Boolean = inputEnded && outputBuffer === EMPTY_BUFFER

    override fun flush() {
        outputBuffer = EMPTY_BUFFER
        inputEnded = false
        dbgCount = 0
        dbgZeros = 0

        if (enabled && handle != 0L) {
            SoundTouchBridge.nativeReset(handle)
            initializedNative = false
            tempoPitchDirty = true

            if (configured && inputFormat != AudioProcessor.AudioFormat.NOT_SET) {
                val ok = SoundTouchBridge.nativeInit(handle, inputFormat.sampleRate, inputFormat.channelCount)
                initializedNative = ok
                if (ok) tempoPitchDirty = true
            }
        }
    }

    override fun reset() {
        outputBuffer = EMPTY_BUFFER
        inputEnded = false
        configured = false
        inputFormat = AudioProcessor.AudioFormat.NOT_SET
        outputFormat = AudioProcessor.AudioFormat.NOT_SET
        initializedNative = false
        tempoPitchDirty = true
        releaseNative()
    }

    // ------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------

    private fun applyTempoPitchIfDirty(h: Long) {
        if (!enabled) return
        if (h == 0L) return
        if (!initializedNative) return
        if (!tempoPitchDirty) return

        val tempo = pendingTempoRatio
        val semi = pendingPitchSemi

        val okTempo = SoundTouchBridge.nativeSetTempo(h, tempo)
        val okPitch = SoundTouchBridge.nativeSetPitchSemi(h, semi)
        if (okTempo && okPitch) {
            tempoPitchDirty = false
        }

        Log.d(
            TAG,
            "HQ_APPLIED tempo=${fmtTempo(tempo)} pitchSemi=${fmtPitchSemi(semi)} ready=true dirty=$tempoPitchDirty okTempo=$okTempo okPitch=$okPitch"
        )
    }

    private fun isNativeReady(): Boolean {
        return enabled && handle != 0L && initializedNative && inputFormat != AudioProcessor.AudioFormat.NOT_SET
    }

    private fun fmtTempo(value: Float): String = String.format(Locale.US, "%.2f", value)

    private fun fmtPitchSemi(value: Float): String = String.format(Locale.US, "%+.1f", value)

    private fun releaseNative() {
        if (handle != 0L) {
            SoundTouchBridge.nativeRelease(handle)
            handle = 0L
        }
        tempoPitchDirty = true
    }

    private fun publishPcmMeter(buffer: ByteBuffer) {
        val levels = computePcm16Levels(buffer) ?: return
        MeterManager.onMasterPcm(rms = levels.rms, peak = levels.peak)
    }

    private fun computePcm16Levels(buffer: ByteBuffer): Levels? {
        val start = buffer.position()
        val end = buffer.limit()
        if (end - start < 2) return null

        val view = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        var i = start
        var peakAbs = 0
        var sumSq = 0.0
        var count = 0
        val stepBytes = (METER_SAMPLE_STRIDE * 2).coerceAtLeast(2)

        while (i + 1 < end) {
            val sample = view.getShort(i).toInt()
            val absSample = abs(sample)
            if (absSample > peakAbs) peakAbs = absSample

            val normalized = sample / 32768.0
            sumSq += normalized * normalized
            count++
            i += stepBytes
        }

        if (count == 0) return null

        return Levels(
            rms = sqrt(sumSq / count).toFloat().coerceIn(0f, 1f),
            peak = (peakAbs / 32768f).coerceIn(0f, 1f)
        )
    }

    private data class Levels(val rms: Float, val peak: Float)
}
