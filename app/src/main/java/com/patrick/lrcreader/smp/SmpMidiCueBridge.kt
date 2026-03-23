package com.patrick.lrcreader.smp

import android.content.Context
import android.net.Uri
import android.util.Log
import com.patrick.lrcreader.core.CueMidi
import com.patrick.lrcreader.core.LrcLine
import java.io.File
import java.util.Locale
import kotlin.math.roundToLong

object SmpMidiCueBridge {

    private const val TRACKS_DIR_NAME = "tracks"
    private const val TRACE_TAG = "MIDI_CUE_TRACE"

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
        val rawCues = loadMidiCues(track)
        val projected = rawCues.mapNotNull { midiCue ->
            val projectedCue = midiCue.toCueMidiOrNull(lines) ?: return@mapNotNull null
            ProjectedCueTrace(
                timeMs = midiCue.toTimeMs(),
                lineIndex = projectedCue.lineIndex,
                channel = projectedCue.channel,
                program = projectedCue.program
            )
        }
        val distinct = projected
            .distinctBy { it.lineIndex }
            .map { cue ->
                CueMidi(
                    lineIndex = cue.lineIndex,
                    channel = cue.channel,
                    program = cue.program
                )
            }

        Log.d(
            TRACE_TAG,
            "BRIDGE_EDITOR_CUES track=${track.songDir.name} raw=${formatMidiCueList(rawCues)} projected=${formatProjectedCueList(projected)} distinct=${formatCueMidiList(distinct)}"
        )

        return distinct
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

    fun getCueAtTime(
        context: Context,
        trackUriString: String?,
        timeMs: Long
    ): MidiCue? {
        val track = resolveSmpTrack(context, trackUriString) ?: return null
        return loadMidiCues(track)
            .firstOrNull { midiCue ->
                midiCue.type.equals("PC", ignoreCase = true) &&
                    midiCue.matchesTimeMs(timeMs)
            }
    }

    fun upsertCueAtTime(
        context: Context,
        trackUriString: String?,
        cue: MidiCue
    ): Boolean? {
        val track = resolveSmpTrack(context, trackUriString) ?: return null
        val targetTimeMs = cue.toTimeMs()
        val replacement = cue.copy(type = "PC")
        val updated = loadMidiCues(track)
            .filterNot { existing ->
                existing.type.equals("PC", ignoreCase = true) &&
                    existing.matchesTimeMs(targetTimeMs)
            } + replacement

        return saveMidiCues(track, updated)
    }

    fun deleteCueAtTime(
        context: Context,
        trackUriString: String?,
        timeMs: Long
    ): Boolean? {
        val track = resolveSmpTrack(context, trackUriString) ?: return null
        val updated = loadMidiCues(track).filterNot { midiCue ->
            midiCue.type.equals("PC", ignoreCase = true) &&
                midiCue.matchesTimeMs(timeMs)
        }
        return saveMidiCues(track, updated)
    }

    private fun loadMidiCues(track: SmpTrack): List<MidiCue> {
        synchronized(cacheLock) {
            midiCuesBySongDir[track.songDir.absolutePath]?.let { cached ->
                Log.d(
                    TRACE_TAG,
                    "BRIDGE_CACHE_HIT songDir=${track.songDir.absolutePath} cues=${formatMidiCueList(cached)}"
                )
                return cached
            }
        }

        val loaded = SmpMidiCuesStore.read(track.songDir)
        synchronized(cacheLock) {
            midiCuesBySongDir[track.songDir.absolutePath] = loaded
        }
        Log.d(
            TRACE_TAG,
            "BRIDGE_CACHE_MISS songDir=${track.songDir.absolutePath} cues=${formatMidiCueList(loaded)}"
        )
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

    private fun MidiCue.matchesTimeMs(timeMs: Long): Boolean {
        return toTimeMs() == timeMs.coerceAtLeast(0L)
    }

    private fun MidiCue.toTimeMs(): Long {
        return (time * 1000.0).roundToLong().coerceAtLeast(0L)
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

    private data class ProjectedCueTrace(
        val timeMs: Long,
        val lineIndex: Int,
        val channel: Int,
        val program: Int
    )

    private fun formatMidiCueList(cues: List<MidiCue>): String {
        if (cues.isEmpty()) return "[]"
        return cues.joinToString(prefix = "[", postfix = "]") { cue ->
            "{timeMs=${cue.toTimeMs()},type=${cue.type},value=${cue.value},channel=${cue.channel}}"
        }
    }

    private fun formatProjectedCueList(cues: List<ProjectedCueTrace>): String {
        if (cues.isEmpty()) return "[]"
        return cues.joinToString(prefix = "[", postfix = "]") { cue ->
            "{timeMs=${cue.timeMs},lineIndex=${cue.lineIndex},channel=${cue.channel},program=${cue.program}}"
        }
    }

    private fun formatCueMidiList(cues: List<CueMidi>): String {
        if (cues.isEmpty()) return "[]"
        return cues.joinToString(prefix = "[", postfix = "]") { cue ->
            "{lineIndex=${cue.lineIndex},channel=${cue.channel},program=${cue.program}}"
        }
    }
}
