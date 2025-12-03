package com.patrick.lrcreader.core

/**
 * CUE MIDI très simple pour la V1 :
 * - lineIndex : index de la ligne de paroles
 * - channel   : canal MIDI (1–16)
 * - program   : numéro de Program Change (1–128)
 */
data class CueMidi(
    val lineIndex: Int,
    val channel: Int,
    val program: Int
)