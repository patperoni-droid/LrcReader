// core/MidiCueDispatcher.kt
package com.patrick.lrcreader.core

import android.util.Log

object MidiCueDispatcher {

    private const val TAG = "MidiCueDispatcher"

    private val lastLineByTrack: MutableMap<String, Int> = mutableMapOf()

    fun onActiveLineChanged(trackUri: String?, lineIndex: Int) {
        val key = trackUri?.takeIf { it.isNotBlank() } ?: return

        val last = lastLineByTrack[key]
        if (last == lineIndex) return
        lastLineByTrack[key] = lineIndex

        val cuesForTrack = CueMidiStore.getCuesForTrack(trackUri)
        val cue = cuesForTrack.firstOrNull { it.lineIndex == lineIndex } ?: return

        MidiOutput.sendProgramChange(
            channel = cue.channel,
            program = cue.program
        )
        // plus tard : vrai envoi MIDI ici
    }

    fun resetForTrack(trackUri: String?) {
        val key = trackUri?.takeIf { it.isNotBlank() } ?: return
        lastLineByTrack.remove(key)
    }
}