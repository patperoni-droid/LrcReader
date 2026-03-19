package com.patrick.lrcreader.smp

import android.content.Context
import android.net.Uri
import com.patrick.lrcreader.core.CueMidi
import com.patrick.lrcreader.core.LrcLine
import java.io.File
import java.util.Locale

object SmpMidiCueBridge {

    private const val TRACKS_DIR_NAME = "tracks"

    private val cacheLock = Any()
    private val midiCuesBySongDir: MutableMap<String, List<MidiCue>> = mutableMapOf()

    fun isSmpTrack(context: Context, trackUriString: String?): Boolean {
        return resolveSmpTrack(context, trackUriString) != null
    }

    fun getEditorCues(
        context: Context,
        trackUriString: String?,
        lines: List<LrcLine>
    ): List<CueMidi>? {
        val track = resolveSmpTrack(context, trackUriString) ?: return null
        return loadMidiCues(track)
            .mapNotNull { midiCue -> midiCue.toCueMidiOrNull(lines) }
            .distinctBy { it.lineIndex }
    }

    fun upsertCue(
        context: Context,
        trackUriString: String?,
        lines: List<LrcLine>,
        cue: CueMidi
    ): Boolean? {
        val track = resolveSmpTrack(context, trackUriString) ?: return null
        val replacement = cue.toSmpMidiCueOrNull(lines) ?: return false
        val updated = loadMidiCues(track)
            .filterNot { existing ->
                existing.type.equals("PC", ignoreCase = true) &&
                    existing.toCueMidiOrNull(lines)?.lineIndex == cue.lineIndex
            } + replacement

        return saveMidiCues(track, updated)
    }

    fun deleteCue(
        context: Context,
        trackUriString: String?,
        lines: List<LrcLine>,
        lineIndex: Int
    ): Boolean? {
        val track = resolveSmpTrack(context, trackUriString) ?: return null
        val existing = loadMidiCues(track)
        val updated = existing.filterNot { midiCue ->
            midiCue.type.equals("PC", ignoreCase = true) &&
                midiCue.toCueMidiOrNull(lines)?.lineIndex == lineIndex
        }
        return saveMidiCues(track, updated)
    }

    fun getCueForLine(
        context: Context,
        trackUriString: String?,
        lines: List<LrcLine>,
        lineIndex: Int
    ): CueMidi? {
        return getEditorCues(context, trackUriString, lines)
            ?.firstOrNull { it.lineIndex == lineIndex }
    }

    private fun loadMidiCues(track: SmpTrack): List<MidiCue> {
        synchronized(cacheLock) {
            midiCuesBySongDir[track.songDir.absolutePath]?.let { return it }
        }

        val loaded = SmpMidiCuesStore.read(track.songDir)
        synchronized(cacheLock) {
            midiCuesBySongDir[track.songDir.absolutePath] = loaded
        }
        return loaded
    }

    private fun saveMidiCues(track: SmpTrack, cues: List<MidiCue>): Boolean {
        val normalized = cues.sortedWith(
            compareBy<MidiCue> { it.time }
                .thenBy { it.channel }
                .thenBy { it.type }
                .thenBy { it.value }
        )

        val saved = SmpMidiCuesStore.write(track.songDir, normalized)
        if (saved) {
            synchronized(cacheLock) {
                midiCuesBySongDir[track.songDir.absolutePath] = normalized
            }
        }
        return saved
    }

    private fun resolveSmpTrack(context: Context, trackUriString: String?): SmpTrack? {
        val rawUri = trackUriString?.trim().orEmpty()
        if (rawUri.isBlank()) {
            return null
        }

        val uri = runCatching { Uri.parse(rawUri) }.getOrNull() ?: return null
        if (!uri.scheme.equals("file", ignoreCase = true)) {
            return null
        }

        val audioFile = uri.path?.let(::File)?.canonicalFile ?: return null
        val parentDir = audioFile.parentFile ?: return null
        val fileName = audioFile.name.lowercase(Locale.ROOT)
        if (!parentDir.isDirectory || !fileName.startsWith("audio.")) {
            return null
        }

        val tracksDir = File(context.filesDir, TRACKS_DIR_NAME).canonicalFile
        val songDir = parentDir.canonicalFile
        if (songDir.parentFile?.canonicalFile != tracksDir) {
            return null
        }

        return SmpTrack(songDir = songDir)
    }

    private fun MidiCue.toCueMidiOrNull(lines: List<LrcLine>): CueMidi? {
        if (!type.equals("PC", ignoreCase = true)) {
            return null
        }

        val lineIndex = findNearestLineIndex(lines, (time * 1000.0).toLong()) ?: return null
        return CueMidi(
            lineIndex = lineIndex,
            channel = channel,
            program = value
        )
    }

    private fun CueMidi.toSmpMidiCueOrNull(lines: List<LrcLine>): MidiCue? {
        val targetLine = lines.getOrNull(lineIndex) ?: return null
        if (targetLine.timeMs <= 0L) {
            return null
        }

        return MidiCue(
            time = targetLine.timeMs / 1000.0,
            type = "PC",
            value = program,
            channel = channel
        )
    }

    private fun findNearestLineIndex(lines: List<LrcLine>, targetTimeMs: Long): Int? {
        return lines
            .withIndex()
            .filter { it.value.timeMs > 0L }
            .minByOrNull { (_, line) -> kotlin.math.abs(line.timeMs - targetTimeMs) }
            ?.index
    }

    private data class SmpTrack(
        val songDir: File
    )
}
