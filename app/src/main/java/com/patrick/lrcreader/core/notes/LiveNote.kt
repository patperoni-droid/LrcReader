package com.patrick.lrcreader.core.notes

data class LiveNote(
    val timeMs: Long,       // moment d’apparition
    val durationMs: Long,   // durée d’affichage
    val text: String
)