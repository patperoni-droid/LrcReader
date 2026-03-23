package com.patrick.lrcreader.core.notes

object LiveNoteManager {

    private val notes = mutableListOf<LiveNote>()

    fun setNotes(list: List<LiveNote>) {
        notes.clear()
        notes.addAll(list)
    }

    fun addNote(note: LiveNote) {
        notes.add(note)
    }

    fun snapshot(): List<LiveNote> {
        return notes.toList()
    }

    fun clear() {
        notes.clear()
    }

    fun getActiveNote(positionMs: Long): LiveNote? {
        return notes
            .asSequence()
            .filter { note ->
                positionMs >= note.timeMs &&
                    positionMs < (note.timeMs + note.durationMs)
            }
            .maxByOrNull { note -> note.timeMs }
    }
    fun remove(note: LiveNote) {
        notes.remove(note)
    }
}
