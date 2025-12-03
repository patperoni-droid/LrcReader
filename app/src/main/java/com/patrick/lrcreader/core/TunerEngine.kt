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
import kotlin.math.log2
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class TunerState(
    val isListening: Boolean = false,
    val inputLevel: Float = 0f,      // 0..1
    val frequency: Float? = null,    // Hz affichée
    val noteName: String = "—",      // ex: A4
    val cents: Int? = null           // -50..+50 en gros
)

object TunerEngine {

    // Paramètres basiques
    private const val SAMPLE_RATE = 44100
    private const val MIN_FREQ = 60f     // Hz
    private const val MAX_FREQ = 1000f   // Hz

    // Combien de fois la même note doit être détectée avant de l'afficher
    private const val REQUIRED_STABLE_COUNT = 4

    // Décalage global en demi-tons (ici -2 pour corriger A qui sort en B)
    private const val SEMITONE_OFFSET = -2

    private val _state = MutableStateFlow(TunerState())
    val state: StateFlow<TunerState> = _state.asStateFlow()

    private var scope: CoroutineScope? = null
    private var job: Job? = null
    private var audioRecord: AudioRecord? = null

    // --- variables pour stabiliser l'affichage ---
    private var lastDisplayedFreq: Float? = null
    private var lastDisplayedNote: String = "—"
    private var lastDisplayedCents: Int? = null

    private var lastMidiCandidate: Int? = null
    private var stableCount: Int = 0

    fun start() {
        if (job != null) return  // déjà en route

        // reset de l'affichage stabilisé
        lastDisplayedFreq = null
        lastDisplayedNote = "—"
        lastDisplayedCents = null
        lastMidiCandidate = null
        stableCount = 0

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

        lastDisplayedFreq = null
        lastDisplayedNote = "—"
        lastDisplayedCents = null
        lastMidiCandidate = null
        stableCount = 0

        _state.value = TunerState()
    }

    // Création / réutilisation d’un AudioRecord correct
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
                // Niveau RMS pour l’affichage
                var sum = 0.0
                for (i in 0 until read) {
                    val s = buffer[i].toDouble()
                    sum += s * s
                }
                val rms = sqrt(sum / read)
                val level = (rms / Short.MAX_VALUE.toDouble())
                    .toFloat()
                    .coerceIn(0f, 1f)

                // Détection de fréquence brute
                val freq = detectFrequency(buffer, read, SAMPLE_RATE)

                if (freq != null) {
                    // On convertit en note "candidate"
                    val (candidateName, candidateCents, candidateMidi) = mapFreqToNote(freq)

                    if (candidateMidi != null) {
                        // Si c'est la même note MIDI que la frame précédente → on augmente le compteur
                        if (candidateMidi == lastMidiCandidate) {
                            stableCount++
                        } else {
                            // Nouvelle note candidate → on reset le compteur
                            lastMidiCandidate = candidateMidi
                            stableCount = 1
                        }

                        // Si la note est stable assez longtemps, on met à jour l'affichage
                        if (stableCount >= REQUIRED_STABLE_COUNT) {
                            lastDisplayedFreq = freq
                            lastDisplayedNote = candidateName
                            lastDisplayedCents = candidateCents
                        }
                    }
                }
                // Si freq == null, on NE RÉINITIALISE PAS tout de suite l'affichage
                // → on garde la dernière note stable pour éviter les clignotements

                _state.update {
                    it.copy(
                        isListening = true,
                        inputLevel = level,
                        frequency = lastDisplayedFreq,
                        noteName = lastDisplayedNote,
                        cents = lastDisplayedCents
                    )
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

        val samples = FloatArray(size) { i ->
            buffer[i] / 32768f
        }

        val maxLag = (sampleRate / MIN_FREQ).toInt()
        val minLag = (sampleRate / MAX_FREQ).toInt()

        if (maxLag >= size || minLag >= size) return null

        var bestLag = -1
        var bestCorr = 0f

        for (lag in minLag until maxLag) {
            var s = 0f
            for (i in 0 until size - lag) {
                s += samples[i] * samples[i + lag]
            }
            if (s > bestCorr) {
                bestCorr = s
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
     * Conversion fréquence -> (note, cents, midi)
     * Basé sur A4 = 440 Hz (MIDI 69) + décalage global SEMITONE_OFFSET.
     */
    private fun mapFreqToNote(freq: Float): Triple<String, Int?, Int?> {
        if (freq <= 0f) return Triple("—", null, null)

        // Position MIDI théorique (sans décalage)
        val midiFloatRaw = 69f + 12f * log2(freq / 440f)
        val midiRaw = midiFloatRaw.roundToInt()

        // On applique le décalage global en demi-tons
        val midiCorrected = midiRaw + SEMITONE_OFFSET

        val noteNames = arrayOf(
            "C", "C#", "D", "D#", "E", "F",
            "F#", "G", "G#", "A", "A#", "B"
        )

        val noteIndex = ((midiCorrected % 12) + 12) % 12
        val octave = midiCorrected / 12 - 1    // MIDI 60 = C4

        // Cents par rapport à la note corrigée
        val midiFloatCorrected = midiFloatRaw + SEMITONE_OFFSET
        val cents = ((midiFloatCorrected - midiCorrected) * 100f).roundToInt()

        val name = noteNames[noteIndex] + octave.toString()

        android.util.Log.d(
            "TUNER_DEBUG",
            "freq=${freq}Hz midiRaw=$midiRaw midiCorrected=$midiCorrected cents=$cents name=$name"
        )
        return Triple(name, cents, midiCorrected)
    }
}