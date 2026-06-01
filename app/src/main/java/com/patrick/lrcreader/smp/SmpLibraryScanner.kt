package com.patrick.lrcreader.smp

import android.content.Context
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.io.File

data class SmpImportedSongDetail(
    val song: SongUnit,
    val playback: SmpConfig.PlaybackConfig?
)

class SmpLibraryScanner(private val context: Context) {

    companion object {
        private const val TAG = "SMP"
        private const val TRACE_TAG = "SMP_TRACE"
        private const val SCAN_TRACE_TAG = "SMP_LIBRARY_SCAN_TRACE"
        private const val TRACKS_DIR_NAME = "tracks"
        private const val CONFIG_FILE_NAME = "config.json"
        private const val WAVEFORM_FILE_NAME = "waveform.json"
        private val AUDIO_FILE_NAMES = listOf(
            "audio.mp3",
            "audio.wav",
            "audio.wave",
            "audio.flac",
            "audio.m4a",
            "audio.aac",
            "audio.ogg"
        )
    }

    fun listSongs(): List<SongUnit> {
        val startMs = SystemClock.elapsedRealtime()
        val mainThread = Looper.myLooper() == Looper.getMainLooper()
        val caller = shortCallerStack()
        val tracksRoot = File(context.filesDir, TRACKS_DIR_NAME)
        if (!tracksRoot.exists() || !tracksRoot.isDirectory) {
            Log.d(TAG, "Aucun dossier SMP importé: ${tracksRoot.absolutePath}")
            Log.i(TRACE_TAG, "step=runtime_scan_empty_root path=${tracksRoot.absolutePath}")
            Log.i(
                SCAN_TRACE_TAG,
                "event=listSongs_empty caller=$caller mainThread=$mainThread durationMs=${SystemClock.elapsedRealtime() - startMs} path=${tracksRoot.absolutePath}"
            )
            return emptyList()
        }

        val songDirs = tracksRoot.listFiles()
            .orEmpty()
            .asSequence()
            .filter { it.isDirectory && !it.name.startsWith(".") }
            .toList()

        Log.i(
            TRACE_TAG,
            "step=runtime_scan_start path=${tracksRoot.absolutePath} dirNames=${songDirs.map { it.name }.sorted().joinToString(prefix = "[", postfix = "]", limit = 20, truncated = "...")}"
        )
        Log.i(
            SCAN_TRACE_TAG,
            "event=listSongs_start caller=$caller mainThread=$mainThread dirs=${songDirs.size} path=${tracksRoot.absolutePath}"
        )

        val songs = songDirs
            .mapNotNull { songDir -> readSongUnit(songDir) }
            .sortedBy { it.title.lowercase() }
            .toList()

        Log.i(
            TRACE_TAG,
            "step=runtime_scan_done path=${tracksRoot.absolutePath} count=${songs.size} songIds=${songs.map { it.id }.sorted().joinToString(prefix = "[", postfix = "]", limit = 20, truncated = "...")}"
        )
        Log.i(
            SCAN_TRACE_TAG,
            "event=listSongs_done caller=$caller mainThread=$mainThread durationMs=${SystemClock.elapsedRealtime() - startMs} dirs=${songDirs.size} songs=${songs.size}"
        )
        return songs
    }

    private fun shortCallerStack(): String {
        return Throwable().stackTrace
            .asSequence()
            .dropWhile { it.className == SmpLibraryScanner::class.java.name || it.methodName == "shortCallerStack" }
            .filterNot { it.className.startsWith("java.lang.") }
            .take(6)
            .joinToString(" <- ") { frame ->
                "${frame.className.substringAfterLast('.')}.${frame.methodName}:${frame.lineNumber}"
            }
            .ifBlank { "unknown" }
    }

    fun findSongById(songId: String): SongUnit? {
        val cleanSongId = songId.trim()
        if (cleanSongId.isEmpty()) {
            return null
        }

        return listSongs().firstOrNull { it.id == cleanSongId }
    }

    fun readSongDetail(song: SongUnit): SmpImportedSongDetail? {
        val storageFolder = song.storageFolder
        if (storageFolder.isNullOrBlank()) {
            Log.w(TAG, "Lecture détail SMP impossible sans storageFolder: songId=${song.id}")
            return null
        }

        val songDir = File(storageFolder)
        if (!songDir.isDirectory) {
            Log.w(TAG, "Lecture détail SMP impossible: dossier absent ${songDir.absolutePath}")
            return null
        }

        val config = readConfig(songDir) ?: return null
        return SmpImportedSongDetail(
            song = song,
            playback = config.playback
        )
    }

    private fun readSongUnit(songDir: File): SongUnit? {
        val meta = SmpMetaStore.read(songDir)
        val config = readConfig(songDir)
        if (meta == null && config == null) {
            Log.i(TRACE_TAG, "step=runtime_song_skip dir=${songDir.absolutePath} reason=no_meta_and_no_config")
            return null
        }

        val audioFile = resolveAudioFile(songDir, meta, config)
        val midiPath = resolveOptionalPath(songDir, meta?.midiCuesFile, "midi_cues.json")
        val midiCues = if (midiPath != null) {
            SmpMidiCuesStore.read(songDir)
        } else {
            emptyList()
        }

        val title = firstNonBlankTitle(
            meta?.title,
            config?.title,
            songDir.name
        )
        val configFile = File(songDir, CONFIG_FILE_NAME)
        Log.d(
            "LIBRARY_INDEX_DIAG",
            "songId=${songDir.name} title=$title displayTitle=$title audioPath=${audioFile?.absolutePath ?: "null"} configPath=${configFile.absolutePath} configExists=${configFile.isFile}"
        )

        val songUnit = SongUnit(
            id = songDir.name,
            title = title,
            storageFolder = songDir.absolutePath,
            audioPath = audioFile?.absolutePath,
            lyricsPath = resolveOptionalPath(songDir, meta?.lyricsFile, "lyrics.lrc"),
            chordsPath = resolveOptionalPath(songDir, meta?.chordsFile, "chords.lrc"),
            timelinePath = resolveOptionalPath(
                songDir,
                meta?.timelineFile ?: config?.files?.timeline,
                SmpTimelineStore.TIMELINE_FILE_NAME
            ),
            waveformPath = resolveOptionalPath(songDir, meta?.waveformFile, WAVEFORM_FILE_NAME),
            annotationsPath = resolveOptionalPath(songDir, meta?.annotationsFile, "annotations.json"),
            midiPath = midiPath,
            midiCues = midiCues,
            dmxPath = resolveDmxPath(songDir, meta?.dmxFile),
            prompterPath = findPrompterPath(songDir)
        )
        Log.i(
            TRACE_TAG,
            "step=runtime_song_accept dir=${songDir.absolutePath} songId=${songUnit.id} title=${songUnit.title}"
        )
        return songUnit
    }

    private fun resolveAudioFile(songDir: File, meta: SmpMeta?, config: SmpConfig?): File? {
        meta?.audioFile
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { File(songDir, it) }
            ?.takeIf { it.isFile }
            ?.let { return it }

        config?.files?.audio
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { File(songDir, it) }
            ?.takeIf { it.isFile }
            ?.let { return it }

        return AUDIO_FILE_NAMES
            .asSequence()
            .map { File(songDir, it) }
            .firstOrNull { it.isFile }
    }

    private fun resolveOptionalPath(songDir: File, fileNameFromMeta: String?, fallbackName: String): String? {
        fileNameFromMeta
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { File(songDir, it) }
            ?.takeIf { it.isFile }
            ?.let { return it.absolutePath }

        return File(songDir, fallbackName).takeIf { it.isFile }?.absolutePath
    }

    private fun resolveDmxPath(songDir: File, fileNameFromMeta: String?): String? {
        fileNameFromMeta
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { File(songDir, it) }
            ?.takeIf { it.isFile }
            ?.let { return it.absolutePath }

        return listOf(
            SmpLightCueStore.LIGHT_CUES_FILE_NAME,
            SmpLightCueStore.LEGACY_LIGHT_CUES_FILE_NAME
        ).asSequence()
            .map { fileName -> File(songDir, fileName) }
            .firstOrNull { file -> file.isFile }
            ?.absolutePath
    }

    private fun findPrompterPath(songDir: File): String? {
        val candidates = listOf(
            "prompteur.txt",
            "prompteur.json",
            "prompter.txt",
            "prompter.json"
        )

        return candidates
            .asSequence()
            .map { File(songDir, it) }
            .firstOrNull { it.isFile }
            ?.absolutePath
    }

    private fun readConfig(songDir: File): SmpConfig? {
        val configFile = File(songDir, CONFIG_FILE_NAME)
        if (!configFile.isFile) {
            Log.w(TAG, "Dossier SMP ignoré sans config.json: ${songDir.absolutePath}")
            Log.i(TRACE_TAG, "step=runtime_config_missing dir=${songDir.absolutePath}")
            return null
        }

        return runCatching {
            SmpConfig.fromJsonOrNull(configFile.readText(Charsets.UTF_8))
        }.getOrElse { error ->
            Log.e(TAG, "Lecture config.json impossible: ${configFile.absolutePath}", error)
            Log.e(TRACE_TAG, "step=runtime_config_read_failed path=${configFile.absolutePath}", error)
            null
        } ?: run {
            Log.w(TAG, "Dossier SMP ignoré avec config.json invalide: ${songDir.absolutePath}")
            Log.i(TRACE_TAG, "step=runtime_config_invalid dir=${songDir.absolutePath}")
            null
        }
    }

    private fun firstNonBlankTitle(vararg candidates: String?): String {
        return candidates
            .asSequence()
            .mapNotNull { it?.trim() }
            .firstOrNull { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
            ?: "Titre sans nom"
    }
}
