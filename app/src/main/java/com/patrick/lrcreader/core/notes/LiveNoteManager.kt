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

    fun clear() {
        notes.clear()
    }

    fun getActiveNote(positionMs: Long): LiveNote? {
        return notes.firstOrNull { note ->
            positionMs in note.timeMs..(note.timeMs + note.durationMs)
        }
    }
    fun remove(note: LiveNote) {
        notes.remove(note)
    }
}