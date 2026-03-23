package com.patrick.lrcreader.smp

import android.content.Context
import android.util.Log
import java.io.File
import java.security.MessageDigest

object SmpMidiCuesStore {

    const val MIDI_CUES_FILE_NAME = "midi_cues.json"
    private const val TRACKS_DIR_NAME = "tracks"
    private const val TAG = "SmpMidiCuesStore"
    private const val TRACE_TAG = "SMP_MIDI_TRACE"
    private const val DEBUG_TRACE_TAG = "MIDI_CUE_TRACE"

    fun read(songDir: File): List<MidiCue> {
        val midiFile = File(songDir, MIDI_CUES_FILE_NAME)
        if (!midiFile.isFile) {
            Log.d(
                TRACE_TAG,
                "LOAD songDir=${songDir.absolutePath} path=${midiFile.absolutePath} exists=false count=0 source=MISSING hash=null"
            )
            return emptyList()
        }

        return runCatching {
            val rawJson = midiFile.readText(Charsets.UTF_8)
            val cues = MidiCue.listFromJsonOrEmpty(rawJson)
            Log.d(
                TRACE_TAG,
                "LOAD songDir=${songDir.absolutePath} path=${midiFile.absolutePath} exists=true count=${cues.size} source=FILE hash=${contentHash(rawJson)}"
            )
            Log.d(
                DEBUG_TRACE_TAG,
                "STORE_LOAD songDir=${songDir.absolutePath} path=${midiFile.absolutePath} cues=${formatCueList(cues)}"
            )
            cues
        }.getOrElse { error ->
            Log.e(TAG, "Lecture midi_cues.json impossible: ${midiFile.absolutePath}", error)
            Log.d(
                TRACE_TAG,
                "LOAD songDir=${songDir.absolutePath} path=${midiFile.absolutePath} exists=true count=0 source=FILE_ERROR hash=null"
            )
            emptyList()
        }
    }

    fun read(context: Context, songId: String): List<MidiCue> {
        val tracksDir = File(context.filesDir, TRACKS_DIR_NAME)
        return read(File(tracksDir, songId))
    }

    fun read(songUnit: SongUnit): List<MidiCue> {
        val midiFile = resolveMidiFile(songUnit)
        if (midiFile == null) {
            Log.d(
                TRACE_TAG,
                "LOAD songDir=${songUnit.storageFolder ?: "null"} path=null exists=false count=0 source=MISSING hash=null"
            )
            return emptyList()
        }
        if (!midiFile.isFile) {
            Log.d(
                TRACE_TAG,
                "LOAD songDir=${midiFile.parentFile?.absolutePath ?: "null"} path=${midiFile.absolutePath} exists=false count=0 source=MISSING hash=null"
            )
            return emptyList()
        }

        return runCatching {
            val rawJson = midiFile.readText(Charsets.UTF_8)
            val cues = MidiCue.listFromJsonOrEmpty(rawJson)
            Log.d(
                TRACE_TAG,
                "LOAD songDir=${midiFile.parentFile?.absolutePath ?: "null"} path=${midiFile.absolutePath} exists=true count=${cues.size} source=FILE hash=${contentHash(rawJson)}"
            )
            Log.d(
                DEBUG_TRACE_TAG,
                "STORE_LOAD songDir=${midiFile.parentFile?.absolutePath ?: "null"} path=${midiFile.absolutePath} cues=${formatCueList(cues)}"
            )
            cues
        }.getOrElse { error ->
            Log.e(TAG, "Lecture midi_cues.json impossible: ${midiFile.absolutePath}", error)
            Log.d(
                TRACE_TAG,
                "LOAD songDir=${midiFile.parentFile?.absolutePath ?: "null"} path=${midiFile.absolutePath} exists=true count=0 source=FILE_ERROR hash=null"
            )
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
        val existsBefore = midiFile.exists()

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
            Log.d(
                TRACE_TAG,
                "SAVE songDir=${songDir.absolutePath} path=${midiFile.absolutePath} existsBefore=$existsBefore existsAfter=${midiFile.exists()} size=${midiFile.length()} count=${cues.size} hash=${contentHash(rawJson)}"
            )
            Log.d(
                DEBUG_TRACE_TAG,
                "STORE_SAVE songDir=${songDir.absolutePath} path=${midiFile.absolutePath} cues=${formatCueList(cues)}"
            )
            true
        }.getOrElse { error ->
            Log.e(TAG, "Ecriture midi_cues.json impossible: ${midiFile.absolutePath}", error)
            runCatching { tmpFile.delete() }
            Log.d(
                TRACE_TAG,
                "SAVE songDir=${songDir.absolutePath} path=${midiFile.absolutePath} existsBefore=$existsBefore existsAfter=${midiFile.exists()} size=${midiFile.takeIf { it.exists() }?.length() ?: 0L} count=${cues.size} hash=${contentHash(rawJson)}"
            )
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

    private fun contentHash(raw: String): String {
        return runCatching {
            MessageDigest.getInstance("MD5")
                .digest(raw.toByteArray(Charsets.UTF_8))
                .joinToString(separator = "") { byte -> "%02x".format(byte) }
        }.getOrElse {
            raw.hashCode().toString()
        }
    }

    private fun formatCueList(cues: List<MidiCue>): String {
        if (cues.isEmpty()) return "[]"
        return cues.joinToString(
            prefix = "[",
            postfix = "]"
        ) { cue ->
            "{time=${cue.time},type=${cue.type},value=${cue.value},channel=${cue.channel}}"
        }
    }
}
