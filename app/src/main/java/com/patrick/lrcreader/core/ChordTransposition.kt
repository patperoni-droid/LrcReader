package com.patrick.lrcreader.core

private val SHARP_NOTE_NAMES = listOf(
    "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"
)

private val FLAT_NOTE_NAMES = listOf(
    "C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B"
)

private val NATURAL_NOTE_PITCH_CLASSES = mapOf(
    'C' to 0,
    'D' to 2,
    'E' to 4,
    'F' to 5,
    'G' to 7,
    'A' to 9,
    'B' to 11
)

/**
 * Returns a display-only transposition of a chord already recognized by [parseChordPro].
 *
 * The suffix is preserved verbatim. Each note keeps its explicit accidental family:
 * flats remain flat-oriented, sharps remain sharp-oriented, and natural notes use sharps
 * as the deterministic default. A transposition equivalent to zero returns [ChordSymbol.raw]
 * exactly, so the source spelling is never normalized unnecessarily.
 */
fun transposeChord(symbol: ChordSymbol, semitones: Int): String {
    val normalizedSemitones = normalizePitchClass(semitones)
    if (normalizedSemitones == 0) return symbol.raw

    val transposedRoot = transposeNote(
        root = symbol.root,
        accidental = symbol.accidental,
        semitones = normalizedSemitones
    )
    val transposedBass = symbol.bassRoot?.let { bassRoot ->
        transposeNote(
            root = bassRoot,
            accidental = symbol.bassAccidental,
            semitones = normalizedSemitones
        )
    }

    return buildString {
        append(transposedRoot)
        append(symbol.suffix)
        if (transposedBass != null) {
            append('/')
            append(transposedBass)
        }
    }
}

private fun transposeNote(
    root: Char,
    accidental: Char?,
    semitones: Int
): String {
    val accidentalOffset = when (accidental) {
        '#' -> 1
        'b' -> -1
        else -> 0
    }
    val sourcePitchClass = normalizePitchClass(
        NATURAL_NOTE_PITCH_CLASSES.getValue(root) + accidentalOffset,
    )
    val transposedPitchClass = normalizePitchClass(sourcePitchClass + semitones)
    val noteNames = if (accidental == 'b') FLAT_NOTE_NAMES else SHARP_NOTE_NAMES
    return noteNames[transposedPitchClass]
}

private fun normalizePitchClass(value: Int): Int = ((value % 12) + 12) % 12
