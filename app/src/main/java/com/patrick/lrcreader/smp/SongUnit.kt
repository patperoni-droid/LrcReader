package com.patrick.lrcreader.smp

data class SongUnit(
    val id: String,
    val title: String,
    val storageFolder: String? = null,
    val audioPath: String?,
    val lyricsPath: String?,
    val chordsPath: String?,
    val timelinePath: String? = null,
    val waveformPath: String? = null,
    val annotationsPath: String?,
    val midiPath: String?,
    val midiCues: List<MidiCue> = emptyList(),
    val dmxPath: String?,
    val prompterPath: String?
)
