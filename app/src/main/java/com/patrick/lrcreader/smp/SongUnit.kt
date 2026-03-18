package com.patrick.lrcreader.smp

data class SongUnit(
    val id: String,
    val title: String,
    val storageFolder: String? = null,
    val audioPath: String?,
    val lyricsPath: String?,
    val chordsPath: String?,
    val annotationsPath: String?,
    val midiPath: String?,
    val dmxPath: String?,
    val prompterPath: String?
)
