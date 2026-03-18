package com.patrick.lrcreader.smp

import android.content.Context
import android.util.Log
import java.io.File

data class SmpImportedSongDetail(
    val song: SongUnit,
    val playback: SmpConfig.PlaybackConfig?
)

class SmpLibraryScanner(private val context: Context) {

    companion object {
        private const val TAG = "SMP"
        private const val TRACKS_DIR_NAME = "tracks"
        private const val CONFIG_FILE_NAME = "config.json"
        private val AUDIO_FILE_NAMES = listOf(
            "audio.mp3",
            "audio.wav",
            "audio.flac",
            "audio.m4a",
            "audio.aac",
            "audio.ogg"
        )
    }

    fun listSongs(): List<SongUnit> {
        val tracksRoot = File(context.filesDir, TRACKS_DIR_NAME)
        if (!tracksRoot.exists() || !tracksRoot.isDirectory) {
            Log.d(TAG, "Aucun dossier SMP importé: ${tracksRoot.absolutePath}")
            return emptyList()
        }

        return tracksRoot.listFiles()
            .orEmpty()
            .asSequence()
            .filter { it.isDirectory && !it.name.startsWith(".") }
            .mapNotNull { songDir -> readSongUnit(songDir) }
            .sortedBy { it.title.lowercase() }
            .toList()
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
        val config = readConfig(songDir) ?: return null

        val audioFile = AUDIO_FILE_NAMES
            .asSequence()
            .map { File(songDir, it) }
            .firstOrNull { it.isFile }

        return SongUnit(
            id = config.id ?: songDir.name,
            title = config.title ?: songDir.name,
            storageFolder = songDir.absolutePath,
            audioPath = audioFile?.absolutePath,
            lyricsPath = File(songDir, "lyrics.lrc").takeIf { it.isFile }?.absolutePath,
            chordsPath = File(songDir, "chords.lrc").takeIf { it.isFile }?.absolutePath,
            annotationsPath = File(songDir, "annotations.json").takeIf { it.isFile }?.absolutePath,
            midiPath = File(songDir, "midi_cues.json").takeIf { it.isFile }?.absolutePath,
            dmxPath = File(songDir, "dmx_cues.json").takeIf { it.isFile }?.absolutePath,
            prompterPath = findPrompterPath(songDir)
        )
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
            return null
        }

        return runCatching {
            SmpConfig.fromJsonOrNull(configFile.readText(Charsets.UTF_8))
        }.getOrElse { error ->
            Log.e(TAG, "Lecture config.json impossible: ${configFile.absolutePath}", error)
            null
        } ?: run {
            Log.w(TAG, "Dossier SMP ignoré avec config.json invalide: ${songDir.absolutePath}")
            null
        }
    }
}
