// core/MidiCueDispatcher.kt
package com.patrick.lrcreader.core

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.patrick.lrcreader.smp.SmpMidiCueBridge
import com.patrick.lrcreader.core.LrcLine

object MidiCueDispatcher {

    private const val TAG = "MidiCueDispatcher"

    // ✅ garde-fou début de morceau
    private const val START_GUARD_MS = 900L   // en dessous de 0.9s = on retarde
    private const val EXTRA_PAD_MS = 80L      // petite marge

    private val mainHandler = Handler(Looper.getMainLooper())

    private val lastLineByTrack: MutableMap<String, Int> = mutableMapOf()

    fun onResolvedCueChanged(trackUri: String?, lineIndex: Int, cue: CueMidi?, positionMs: Long) {
        val key = trackUri?.takeIf { it.isNotBlank() } ?: return

        val last = lastLineByTrack[key]
        val isFirstForTrack = (last == null)
        if (last == lineIndex) return
        lastLineByTrack[key] = lineIndex

        if (cue == null) {
            Log.w(TAG, "Aucun CUE pour lineIndex=$lineIndex")
            return
        }

        fun doSend() {
            MidiOutput.sendProgramChange(channel = cue.channel, program = cue.program)
            Log.d(TAG, "PC envoyé: line=$lineIndex ch=${cue.channel} prog=${cue.program} pos=$positionMs")

            if (isFirstForTrack) {
                mainHandler.postDelayed({
                    MidiOutput.sendProgramChange(channel = cue.channel, program = cue.program)
                    Log.d(TAG, "PC renvoyé (warmup): line=$lineIndex ch=${cue.channel} prog=${cue.program}")
                }, 200L)
            }
        }

        if (positionMs < START_GUARD_MS) {
            val delayMs = (START_GUARD_MS - positionMs + EXTRA_PAD_MS).coerceAtMost(1200L)
            Log.w(TAG, "PC trop proche du début (pos=$positionMs ms) → delay=${delayMs}ms")
            mainHandler.postDelayed({ doSend() }, delayMs)
        } else {
            doSend()
        }
    }

    fun onActiveLineChanged(trackUri: String?, lineIndex: Int, positionMs: Long) {
        val key = trackUri?.takeIf { it.isNotBlank() } ?: return

        val cuesForTrack = CueMidiStore.getCuesForTrack(key)
        val cue = cuesForTrack.firstOrNull { it.lineIndex == lineIndex }
        onResolvedCueChanged(
            trackUri = key,
            lineIndex = lineIndex,
            cue = cue,
            positionMs = positionMs
        )
    }

    fun resolveCueForTrack(
        context: Context,
        trackUri: String?,
        lines: List<LrcLine>,
        lineIndex: Int
    ): CueMidi? {
        if (SmpMidiCueBridge.isSmpTrack(context, trackUri)) {
            return SmpMidiCueBridge.getCueForLine(context, trackUri, lines, lineIndex)
        }
        return CueMidiStore.getCuesForTrack(trackUri).firstOrNull { it.lineIndex == lineIndex }
    }

    fun resetForTrack(trackUri: String?) {
        val key = trackUri?.takeIf { it.isNotBlank() } ?: return
        lastLineByTrack.remove(key)
        Log.d(TAG, "resetForTrack: $key")
    }
}
