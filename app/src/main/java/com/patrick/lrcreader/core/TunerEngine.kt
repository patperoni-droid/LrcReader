package com.patrick.lrcreader.core

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class TunerState(
    val isListening: Boolean = false,
    val inputLevel: Float = 0f,      // 0..1
    val frequency: Float? = null,    // Hz
    val noteName: String = "—",
    val cents: Int? = null           // -50..+50 en gros
)

object TunerEngine {

    // Calibration du LA 440 (A4)
// Valeur sauvegardée ici (tu pourras la remplacer par SharedPrefs plus tard si tu veux)
    private var referenceA4 = 440f

    fun getReferenceA4(): Float = referenceA4

    fun setReferenceA4(value: Float) {
        referenceA4 = value
    }

    private const val SAMPLE_RATE = 44100
    private const val MIN_FREQ = 60f     // Hz
    private const val MAX_FREQ = 1000f   // Hz

    private val _state = MutableStateFlow(TunerState())
    val state: StateFlow<TunerState> = _state.asStateFlow()

    private var scope: CoroutineScope? = null
    private var job: Job? = null
    private var audioRecord: AudioRecord? = null

    // --- variables pour lisser et stabiliser l’affichage ---
    private var lastDisplayFreq: Float? = null
    private var lastDisplayNote: String = "—"
    private var lastDisplayCents: Int? = null
    private var lastUpdateTimeMs: Long = 0L
    private var lastValidFreqTimeMs: Long = 0L

    fun start() {
        if (job != null) return  // déjà en route

        // reset affichage
        lastDisplayFreq = null
        lastDisplayNote = "—"
        lastDisplayCents = null
        lastUpdateTimeMs = 0L
        lastValidFreqTimeMs = 0L

        scope = CoroutineScope(Dispatchers.Default)
        job = scope!!.launch {
            loop()
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        scope?.cancel()
        scope = null

        audioRecord?.let { rec ->
            try { rec.stop() } catch (_: Exception) {}
            try { rec.release() } catch (_: Exception) {}
        }
        audioRecord = null

        lastDisplayFreq = null
        lastDisplayNote = "—"
        lastDisplayCents = null
        lastUpdateTimeMs = 0L
        lastValidFreqTimeMs = 0L

        _state.update {
            TunerState() // revient à l’état neutre
        }
    }

    private fun ensureRecorder(): AudioRecord? {
        if (audioRecord != null) return audioRecord

        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf == AudioRecord.ERROR || minBuf == AudioRecord.ERROR_BAD_VALUE) {
            return null
        }
        val bufferSize = minBuf * 2

        val rec = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return null
        }

        audioRecord = rec
        return rec
    }

    private suspend fun loop() {
        val recorder = ensureRecorder() ?: return
        val bufferSize = 2048
        val buffer = ShortArray(bufferSize)

        try {
            recorder.startRecording()
        } catch (_: Exception) {
            return
        }

        _state.update { it.copy(isListening = true) }

        while (scope?.isActive == true && job?.isActive == true) {
            val read = try {
                recorder.read(buffer, 0, buffer.size)
            } catch (_: Exception) {
                break
            }

            if (read > 0) {
                // niveau d'entrée RMS
                var sum = 0.0
                for (i in 0 until read) {
                    val s = buffer[i].toDouble()
                    sum += s * s
                }
                val rms = sqrt(sum / read)
                val level = (rms / Short.MAX_VALUE.toDouble()).toFloat()
                    .coerceIn(0f, 1f)

                // détection fréquence brute
                val detectedFreq = detectFrequency(buffer, read, SAMPLE_RATE)
                val now = System.currentTimeMillis()

                var displayFreq = lastDisplayFreq
                var displayNote = lastDisplayNote
                var displayCents = lastDisplayCents

                if (detectedFreq != null) {
                    lastValidFreqTimeMs = now

                    val (note, rawCents) = mapFreqToNote(detectedFreq)

                    // --- lissage fréquence (low-pass) ---
                    val smoothedFreq = lastDisplayFreq?.let { prev ->
                        // mix 70% ancien / 30% nouvelle valeur
                        prev + 0.3f * (detectedFreq - prev)
                    } ?: detectedFreq

                    // --- lissage cents + petite zone morte ±2 cents ---
                    val smoothedCents = lastDisplayCents?.let { prev ->
                        val diff = rawCents - prev
                        if (abs(diff) <= 2) {
                            prev // ne bouge pas si très proche
                        } else {
                            // on se rapproche en 2 étapes
                            (prev + diff * 0.5f).toInt()
                        }
                    } ?: rawCents

                    displayFreq = smoothedFreq
                    displayNote = note
                    displayCents = smoothedCents
                } else {
                    // pas de détection : on garde l’affichage un petit moment
                    if (now - lastValidFreqTimeMs > 300L) {
                        displayFreq = null
                        displayNote = "—"
                        displayCents = null
                    }
                }

                // --- limite à ~20 FPS pour l’UI ---
                if (now - lastUpdateTimeMs >= 50L) {
                    lastUpdateTimeMs = now
                    lastDisplayFreq = displayFreq
                    lastDisplayNote = displayNote
                    lastDisplayCents = displayCents

                    _state.update {
                        it.copy(
                            isListening = true,
                            inputLevel = level,
                            frequency = displayFreq,
                            noteName = displayNote,
                            cents = displayCents
                        )
                    }
                }
            }
        }

        try { recorder.stop() } catch (_: Exception) {}
        try { recorder.release() } catch (_: Exception) {}
        if (audioRecord === recorder) {
            audioRecord = null
        }
    }

    /**
     * Auto-corrélation simple pour trouver la période principale.
     */
    private fun detectFrequency(
        buffer: ShortArray,
        size: Int,
        sampleRate: Int
    ): Float? {
        if (size <= 0) return null

        // passe en float centré
        val samples = FloatArray(size) { i ->
            buffer[i] / 32768f
        }

        // fenêtre de recherche des lags (périodes)
        val maxLag = (sampleRate / MIN_FREQ).toInt()
        val minLag = (sampleRate / MAX_FREQ).toInt()

        if (maxLag >= size || minLag >= size) return null

        var bestLag = -1
        var bestCorr = 0f

        for (lag in minLag until maxLag) {
            var sum = 0f
            for (i in 0 until size - lag) {
                sum += samples[i] * samples[i + lag]
            }
            if (sum > bestCorr) {
                bestCorr = sum
                bestLag = lag
            }
        }

        // seuil pour éviter le bruit
        if (bestLag <= 0 || bestCorr < 0.001f) return null

        val freq = sampleRate.toFloat() / bestLag.toFloat()
        if (freq < MIN_FREQ || freq > MAX_FREQ) return null
        return freq
    }

    /**
     * Map fréquence -> note + décalage en cents
     */
    private fun mapFreqToNote(freq: Float): Pair<String, Int> {

        val A4 = referenceA4   // << calibration dynamique

        // calcule le nombre de demi-tons depuis A4
        val n = (12 * log2(freq / A4)).roundToInt()

        val noteNames = arrayOf(
            "A", "A#", "B", "C", "C#", "D",
            "D#", "E", "F", "F#", "G", "G#"
        )

        val noteIndex = (n + 9).mod(12)
        val octave = 4 + ((n + 9) / 12)

        val exactN = 12 * log2(freq / A4)
        val cents = ((exactN - n) * 100).roundToInt()

        val name = noteNames[noteIndex] + octave.toString()
        return name to cents
    }
}