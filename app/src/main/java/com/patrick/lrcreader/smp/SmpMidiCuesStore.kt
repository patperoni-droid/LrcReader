package com.patrick.lrcreader.smp

import android.content.Context
import android.util.Log
import java.io.File

object SmpMidiCuesStore {

    const val MIDI_CUES_FILE_NAME = "midi_cues.json"
    private const val TRACKS_DIR_NAME = "tracks"
    private const val TAG = "SmpMidiCuesStore"

    fun read(songDir: File): List<MidiCue> {
        val midiFile = File(songDir, MIDI_CUES_FILE_NAME)
        if (!midiFile.isFile) {
            return emptyList()
        }

        return runCatching {
            MidiCue.listFromJsonOrEmpty(midiFile.readText(Charsets.UTF_8))
        }.getOrElse { error ->
            Log.e(TAG, "Lecture midi_cues.json impossible: ${midiFile.absolutePath}", error)
            emptyList()
        }
    }

    fun read(context: Context, songId: String): List<MidiCue> {
        val tracksDir = File(context.filesDir, TRACKS_DIR_NAME)
        return read(File(tracksDir, songId))
    }

    fun read(songUnit: SongUnit): List<MidiCue> {
        val midiFile = resolveMidiFile(songUnit) ?: return emptyList()
        if (!midiFile.isFile) {
            return emptyList()
        }

        return runCatching {
            MidiCue.listFromJsonOrEmpty(midiFile.readText(Charsets.UTF_8))
        }.getOrElse { error ->
            Log.e(TAG, "Lecture midi_cues.json impossible: ${midiFile.absolutePath}", error)
            emptyList()
        }
    }

    fun write(songDir: File, cues: List<MidiCue>): Boolean {
        val midiFile = File(songDir, MIDI_CUES_FILE_NAME)
        return writeInternal(midiFile, cues).also { saved ->
            if (saved) {
                syncExistingMeta(songDir)
            }
        }
    }

    fun write(songUnit: SongUnit, cues: List<MidiCue>): Boolean {
        val midiFile = resolveMidiFile(songUnit) ?: return false
        val saved = writeInternal(midiFile, cues)
        if (!saved) {
            return false
        }

        val updatedSongUnit = songUnit.copy(midiPath = midiFile.absolutePath)
        if (!SmpMetaStore.write(updatedSongUnit)) {
            Log.w(TAG, "Ecriture meta.json impossible après sauvegarde midi songId=${songUnit.id}")
        }
        return true
    }

    private fun writeInternal(midiFile: File, cues: List<MidiCue>): Boolean {
        val songDir = midiFile.parentFile ?: return false
        val tmpFile = File(songDir, "$MIDI_CUES_FILE_NAME.tmp")
        val rawJson = MidiCue.toJsonString(cues)

        return runCatching {
            songDir.mkdirs()
            tmpFile.writeText(rawJson, Charsets.UTF_8)
            if (midiFile.exists() && !midiFile.delete()) {
                Log.w(TAG, "Suppression midi_cues.json impossible: ${midiFile.absolutePath}")
            }
            if (!tmpFile.renameTo(midiFile)) {
                tmpFile.writeText(rawJson, Charsets.UTF_8)
                midiFile.writeText(rawJson, Charsets.UTF_8)
                tmpFile.delete()
            }
            true
        }.getOrElse { error ->
            Log.e(TAG, "Ecriture midi_cues.json impossible: ${midiFile.absolutePath}", error)
            runCatching { tmpFile.delete() }
            false
        }
    }

    private fun syncExistingMeta(songDir: File) {
        val currentMeta = SmpMetaStore.read(songDir) ?: return
        val nextMeta = currentMeta.copy(
            midiCuesFile = MIDI_CUES_FILE_NAME,
            updatedAt = System.currentTimeMillis()
        )
        if (!SmpMetaStore.write(songDir, nextMeta)) {
            Log.w(TAG, "Synchronisation meta.json impossible après sauvegarde midi: ${songDir.absolutePath}")
        }
    }

    private fun resolveMidiFile(songUnit: SongUnit): File? {
        songUnit.storageFolder
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.let { return File(it, MIDI_CUES_FILE_NAME) }

        val midiPath = songUnit.midiPath?.takeIf { it.isNotBlank() } ?: return null
        val midiFile = File(midiPath)
        return midiFile.takeIf { it.name.equals(MIDI_CUES_FILE_NAME, ignoreCase = true) }
    }
}
