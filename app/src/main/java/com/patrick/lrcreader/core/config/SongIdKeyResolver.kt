package com.patrick.lrcreader.core.config

import android.content.Context
import android.net.Uri
import com.patrick.lrcreader.core.getSmpSongId
import com.patrick.lrcreader.smp.SmpMetaStore
import java.io.File
import java.util.Locale

internal object SongIdKeyResolver {

    private const val SONG_ID_KEY_PREFIX = "songId::"
    private const val TRACKS_DIR_NAME = "tracks"
    private const val CONFIG_FILE_NAME = "config.json"
    private val AUDIO_FILE_NAMES = listOf(
        "audio.mp3",
        "audio.wav",
        "audio.wave",
        "audio.flac",
        "audio.m4a",
        "audio.aac",
        "audio.ogg"
    )

    fun normalizeSongId(songId: String?): String? =
        songId?.trim()?.takeIf { it.isNotEmpty() }

    fun songScopedKey(songId: String?): String? =
        normalizeSongId(songId)?.let { "$SONG_ID_KEY_PREFIX$it" }

    fun resolveSongIdFromUri(context: Context, trackUriString: String?): String? =
        songIdFromTrackUri(context, trackUriString)

    fun songIdFromTrackUri(context: Context, trackUriString: String?): String? {
        val rawUri = trackUriString?.trim().orEmpty()
        if (rawUri.isEmpty()) return null

        getSmpSongId(rawUri)?.let { return it }

        val uri = runCatching { Uri.parse(rawUri) }.getOrNull() ?: return null
        if (!uri.scheme.equals("file", ignoreCase = true)) {
            return null
        }

        val audioFile = uri.path?.let(::File)?.canonicalFile ?: return null
        val songDir = audioFile.parentFile?.canonicalFile ?: return null
        if (!songDir.isDirectory || !audioFile.name.lowercase(Locale.ROOT).startsWith("audio.")) {
            return null
        }

        val tracksDir = File(context.filesDir, TRACKS_DIR_NAME).canonicalFile
        if (songDir.parentFile?.canonicalFile != tracksDir) {
            return null
        }

        if (!File(songDir, CONFIG_FILE_NAME).isFile) {
            return null
        }

        return normalizeSongId(songDir.name)
    }

    fun resolveRuntimeTrackUri(context: Context, songId: String?): String? {
        val songDir = resolveSongDir(context, songId) ?: return null
        val audioFile = resolveAudioFile(songDir) ?: return null
        return Uri.fromFile(audioFile).toString()
    }

    fun resolveLegacyRelativePathBySongId(context: Context, songId: String?): String? {
        val runtimeTrackUri = resolveRuntimeTrackUri(context, songId) ?: return null
        return TrackSettingsPathResolver.resolveRelativeTrackPath(context, runtimeTrackUri)
    }

    private fun resolveSongDir(context: Context, songId: String?): File? {
        val cleanSongId = normalizeSongId(songId) ?: return null
        val tracksDir = File(context.filesDir, TRACKS_DIR_NAME)
        val songDir = File(tracksDir, cleanSongId)
        return songDir.takeIf { it.isDirectory && File(it, CONFIG_FILE_NAME).isFile }
    }

    private fun resolveAudioFile(songDir: File): File? {
        val metaAudioName = SmpMetaStore.read(songDir)?.audioFile
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        val candidates = buildList {
            if (metaAudioName != null) add(metaAudioName)
            addAll(AUDIO_FILE_NAMES)
        }

        return candidates
            .asSequence()
            .map { fileName -> File(songDir, fileName) }
            .firstOrNull { it.isFile }
    }
}
