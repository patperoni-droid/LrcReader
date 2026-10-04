package com.patrick.lrcreader.core.soundpads

/** Prototype input. Identity and bank size never depend on the visible grid. */
data class SoundPad(
    val padId: String,
    val name: String,
    val audioPath: String = "",
    val volume: Float = 1f,
    val inMs: Long = 0L,
    val outMs: Long? = null,
    val pitchSemitones: Int = 0,
    val colorArgb: Long? = null
) {
    init {
        require(padId.isNotBlank() && name.isNotBlank())
        require(colorArgb == null || colorArgb in 0L..0xFFFFFFFFL)
        require(audioPath.isNotEmpty() || (inMs == 0L && outMs == null))
        require(volume.isFinite() && volume in 0f..1f)
        require(inMs >= 0 && (outMs == null || outMs > inMs))
        require(pitchSemitones == 0) { "Pitch is not supported by the prototype" }
    }
}

internal fun padsEffectiveGain(individual: Float, globalUi: Float): Float {
    require(individual.isFinite() && globalUi.isFinite())
    val u = globalUi.coerceIn(0f, 1f)
    return individual.coerceIn(0f, 1f) * u * u * u
}
