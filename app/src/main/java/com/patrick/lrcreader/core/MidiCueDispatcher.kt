// core/MidiCueDispatcher.kt
package com.patrick.lrcreader.core

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.patrick.lrcreader.smp.SmpMidiCueBridge
import com.patrick.lrcreader.smp.MidiCue
import com.patrick.lrcreader.core.LrcLine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object MidiCueDispatcher {

    private const val TAG = "MidiCueDispatcher"
    private const val TRACE_TAG = "MIDI_CUE_TRACE"

    // ✅ garde-fou début de morceau
    private const val START_GUARD_MS = 900L   // en dessous de 0.9s = on retarde
    private const val EXTRA_PAD_MS = 80L      // petite marge

    private val mainHandler = Handler(Looper.getMainLooper())

    private val lastLineByTrack: MutableMap<String, Int> = mutableMapOf()
    private val lastSmpPositionByTrack: MutableMap<String, Long> = mutableMapOf()
    private val _lastTriggeredProgramChange = MutableStateFlow<TriggeredProgramChange?>(null)
    val lastTriggeredProgramChange: StateFlow<TriggeredProgramChange?> =
        _lastTriggeredProgramChange.asStateFlow()

    data class TriggeredProgramChange(
        val trackUri: String,
        val channel: Int,
        val program: Int,
        val triggeredAtMs: Long
    )

    fun onResolvedCueChanged(trackUri: String?, lineIndex: Int, cue: CueMidi?, positionMs: Long) {
        val key = trackUri?.takeIf { it.isNotBlank() } ?: return

        val last = lastLineByTrack[key]
        val isFirstForTrack = (last == null)
        Log.d(
            TRACE_TAG,
            "DISPATCH_RESOLVE track=$key lineIndex=$lineIndex lastLine=$last isFirstForTrack=$isFirstForTrack positionMs=$positionMs cue=${formatCue(cue)}"
        )
        if (last == lineIndex) return
        lastLineByTrack[key] = lineIndex

        if (cue == null) {
            Log.w(TAG, "Aucun CUE pour lineIndex=$lineIndex")
            Log.d(
                TRACE_TAG,
                "DISPATCH_SKIP track=$key reason=no_cue lineIndex=$lineIndex lastLine=${lastLineByTrack[key]}"
            )
            return
        }

        fun doSend() {
            _lastTriggeredProgramChange.value = TriggeredProgramChange(
                trackUri = key,
                channel = cue.channel.coerceIn(1, 16),
                program = cue.program.coerceIn(1, 128),
                triggeredAtMs = SystemClock.elapsedRealtime()
            )
            Log.d(
                TRACE_TAG,
                "DISPATCH_TRIGGER track=$key lineIndex=$lineIndex cue=${formatCue(cue)} monitor=${formatTriggered(_lastTriggeredProgramChange.value)}"
            )
            MidiOutput.sendProgramChange(
                channel = cue.channel,
                program = cue.program,
                trackUri = key
            )
            Log.d(TAG, "PC envoyé: line=$lineIndex ch=${cue.channel} prog=${cue.program} pos=$positionMs")

            if (isFirstForTrack) {
                mainHandler.postDelayed({
                    MidiOutput.sendProgramChange(
                        channel = cue.channel,
                        program = cue.program,
                        trackUri = key
                    )
                    Log.d(TAG, "PC renvoyé (warmup): line=$lineIndex ch=${cue.channel} prog=${cue.program}")
                }, 200L)
            }
        }

        if (positionMs < START_GUARD_MS) {
            val delayMs = (START_GUARD_MS - positionMs + EXTRA_PAD_MS).coerceAtMost(1200L)
            Log.w(TAG, "PC trop proche du début (pos=$positionMs ms) → delay=${delayMs}ms")
            Log.d(
                TRACE_TAG,
                "DISPATCH_DELAY track=$key lineIndex=$lineIndex cue=${formatCue(cue)} positionMs=$positionMs delayMs=$delayMs"
            )
            mainHandler.postDelayed({ doSend() }, delayMs)
        } else {
            doSend()
        }
    }

    fun onSmpPlaybackPosition(
        context: Context,
        trackUri: String?,
        positionMs: Long,
        isPlaying: Boolean
    ) {
        val key = trackUri?.takeIf { it.isNotBlank() } ?: return
        val safePositionMs = positionMs.coerceAtLeast(0L)
        val runtimeCues = SmpMidiCueBridge.getRuntimeCues(context, key)
            ?.filter { cue -> cue.type.equals("PC", ignoreCase = true) }
            .orEmpty()

        val previousPositionMs = lastSmpPositionByTrack[key]
        val rewound = previousPositionMs != null && safePositionMs < previousPositionMs
        val effectivePreviousMs = when {
            previousPositionMs == null && safePositionMs <= 1_500L -> 0L
            previousPositionMs == null -> safePositionMs
            rewound -> safePositionMs
            else -> previousPositionMs
        }
        val dueCues = if (!isPlaying || rewound || runtimeCues.isEmpty()) {
            emptyList()
        } else {
            runtimeCues.filter { cue ->
                val cueTimeMs = cue.toTimeMs()
                cueTimeMs > effectivePreviousMs && cueTimeMs <= safePositionMs
            }
        }

        Log.d(
            TRACE_TAG,
            "SMP_RUNTIME track=$key positionMs=$safePositionMs previousMs=${previousPositionMs ?: "null"} effectivePreviousMs=$effectivePreviousMs rewound=$rewound isPlaying=$isPlaying cues=${formatMidiCueList(runtimeCues)} due=${formatMidiCueList(dueCues)}"
        )

        dueCues.forEachIndexed { index, cue ->
            triggerSmpCue(
                trackUri = key,
                cue = cue,
                positionMs = safePositionMs,
                isFirstForTrack = previousPositionMs == null && index == 0
            )
        }

        lastSmpPositionByTrack[key] = safePositionMs
    }

    fun onActiveLineChanged(trackUri: String?, lineIndex: Int, positionMs: Long) {
        val key = trackUri?.takeIf { it.isNotBlank() } ?: return

        val cuesForTrack = CueMidiStore.getCuesForTrack(key)
        val cue = cuesForTrack.firstOrNull { it.lineIndex == lineIndex }
        Log.d(
            TRACE_TAG,
            "DISPATCH_ACTIVE_LINE track=$key lineIndex=$lineIndex positionMs=$positionMs legacyCues=${formatCueList(cuesForTrack)} resolved=${formatCue(cue)}"
        )
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
        lastSmpPositionByTrack.remove(key)
        Log.d(TAG, "resetForTrack: $key")
        Log.d(TRACE_TAG, "DISPATCH_RESET track=$key")
    }

    private fun triggerSmpCue(
        trackUri: String,
        cue: MidiCue,
        positionMs: Long,
        isFirstForTrack: Boolean
    ) {
        val channel = cue.channel.coerceIn(1, 16)
        val program = cue.value.coerceIn(1, 128)
        _lastTriggeredProgramChange.value = TriggeredProgramChange(
            trackUri = trackUri,
            channel = channel,
            program = program,
            triggeredAtMs = SystemClock.elapsedRealtime()
        )
        Log.d(
            TRACE_TAG,
            "SMP_TRIGGER track=$trackUri cue=${formatMidiCue(cue)} positionMs=$positionMs monitor=${formatTriggered(_lastTriggeredProgramChange.value)}"
        )
        MidiOutput.sendProgramChange(
            channel = channel,
            program = program,
            trackUri = trackUri
        )

        if (isFirstForTrack) {
            mainHandler.postDelayed({
                MidiOutput.sendProgramChange(
                    channel = channel,
                    program = program,
                    trackUri = trackUri
                )
                Log.d(
                    TRACE_TAG,
                    "SMP_TRIGGER_WARMUP track=$trackUri cue=${formatMidiCue(cue)}"
                )
            }, 200L)
        }
    }

    private fun formatCue(cue: CueMidi?): String {
        return cue?.let {
            "{lineIndex=${it.lineIndex},channel=${it.channel},program=${it.program}}"
        } ?: "null"
    }

    private fun formatTriggered(event: TriggeredProgramChange?): String {
        return event?.let {
            "{track=${it.trackUri},channel=${it.channel},program=${it.program},triggeredAtMs=${it.triggeredAtMs}}"
        } ?: "null"
    }

    private fun formatCueList(cues: List<CueMidi>): String {
        if (cues.isEmpty()) return "[]"
        return cues.joinToString(prefix = "[", postfix = "]") { cue -> formatCue(cue) }
    }

    private fun formatMidiCue(cue: MidiCue): String {
        return "{timeMs=${cue.toTimeMs()},type=${cue.type},channel=${cue.channel},value=${cue.value}}"
    }

    private fun formatMidiCueList(cues: List<MidiCue>): String {
        if (cues.isEmpty()) return "[]"
        return cues.joinToString(prefix = "[", postfix = "]") { cue -> formatMidiCue(cue) }
    }

    private fun MidiCue.toTimeMs(): Long {
        return (time * 1000.0).toLong().coerceAtLeast(0L)
    }
}
