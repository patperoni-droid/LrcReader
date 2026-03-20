package com.patrick.lrcreader.smp

import android.content.Context
import android.net.Uri
import android.os.Looper
import android.provider.OpenableColumns
import android.util.Log
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.zip.ZipInputStream

class SmpReader(private val context: Context) {

    companion object {
        private const val TAG = "SmpReader"
        private const val CONFIG_FILE_NAME = "config.json"
    }

    fun readSmp(uri: Uri): SongUnit? {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Log.w(TAG, "readSmp refusé sur le thread principal uri=$uri")
            return null
        }

        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zipInputStream ->
                    val finder = SongPathsFinder()
                    var config: SmpConfig? = null

                    var entry = zipInputStream.nextEntry
                    while (entry != null) {
                        try {
                            if (!entry.isDirectory) {
                                val entryName = entry.name
                                finder.accept(entryName)

                                if (entryName.substringAfterLast('/').equals(CONFIG_FILE_NAME, ignoreCase = true)) {
                                    val rawConfig = readEntryAsString(zipInputStream)
                                    config = SmpConfig.fromJson(rawConfig)
                                }
                            }
                        } catch (entryError: Exception) {
                            Log.e(TAG, "Erreur pendant la lecture d'une entrée du zip: ${entry.name}", entryError)
                        } finally {
                            runCatching { zipInputStream.closeEntry() }
                        }
                        entry = zipInputStream.nextEntry
                    }

                    val displayName = resolveDisplayName(uri)
                    buildSongUnit(
                        uri = uri,
                        displayName = displayName,
                        config = config ?: SmpConfig(title = null, id = null),
                        finder = finder
                    )
                }
            } ?: run {
                Log.e(TAG, "Impossible d'ouvrir le fichier .smp uri=$uri")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur pendant la lecture du fichier .smp uri=$uri", e)
            null
        }
    }

    private fun buildSongUnit(
        uri: Uri,
        displayName: String,
        config: SmpConfig,
        finder: SongPathsFinder
    ): SongUnit {
        val fallbackTitle = displayName.removeSuffix(".smp").ifBlank { "Untitled" }
        val title = config.title ?: fallbackTitle
        val id = config.id ?: buildFallbackId(displayName, uri)

        return SongUnit(
            id = id,
            title = title,
            audioPath = finder.audioPath,
            lyricsPath = finder.lyricsPath,
            chordsPath = finder.chordsPath,
            annotationsPath = finder.annotationsPath,
            midiPath = finder.midiPath,
            dmxPath = finder.dmxPath,
            prompterPath = finder.prompterPath
        )
    }

    private fun readEntryAsString(zipInputStream: ZipInputStream): String {
        val buffer = ByteArray(4 * 1024)
        val output = ByteArrayOutputStream()

        while (true) {
            val read = zipInputStream.read(buffer)
            if (read <= 0) break
            output.write(buffer, 0, read)
        }

        return output.toString(Charsets.UTF_8.name())
    }

    private fun buildFallbackId(displayName: String, uri: Uri): String {
        val baseName = displayName.removeSuffix(".smp").ifBlank { "song" }
        val safeBase = baseName
            .trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "song" }

        val suffix = uri.toString().hashCode().toUInt().toString(16)
        return "${safeBase}_$suffix"
    }

    private fun resolveDisplayName(uri: Uri): String {
        var name = uri.lastPathSegment ?: "unknown.smp"

        try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) {
                    name = cursor.getString(index)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Impossible de lire le nom du fichier uri=$uri", e)
        }

        return name
    }

    private class SongPathsFinder {
        var audioPath: String? = null
            private set
        var lyricsPath: String? = null
            private set
        var chordsPath: String? = null
            private set
        var annotationsPath: String? = null
            private set
        var midiPath: String? = null
            private set
        var dmxPath: String? = null
            private set
        var prompterPath: String? = null
            private set

        fun accept(entryName: String) {
            val fileName = entryName.substringAfterLast('/').lowercase(Locale.ROOT)

            when {
                audioPath == null && isAudioFile(fileName) -> audioPath = entryName
                lyricsPath == null && fileName == "lyrics.lrc" -> lyricsPath = entryName
                chordsPath == null && fileName == "chords.lrc" -> chordsPath = entryName
                annotationsPath == null && fileName == "annotations.json" -> annotationsPath = entryName
                midiPath == null && fileName == "midi_cues.json" -> midiPath = entryName
                dmxPath == null && fileName == "dmx_cues.json" -> dmxPath = entryName
                prompterPath == null && isPrompterFile(fileName) -> prompterPath = entryName
            }
        }

        private fun isAudioFile(fileName: String): Boolean {
            return fileName == "audio.mp3" ||
                fileName == "audio.wav" ||
                fileName == "audio.flac" ||
                fileName == "audio.m4a" ||
                fileName == "audio.aac" ||
                fileName == "audio.ogg"
        }

        private fun isPrompterFile(fileName: String): Boolean {
            return fileName == "prompteur.txt" ||
                fileName == "prompteur.json" ||
                fileName == "prompter.txt" ||
                fileName == "prompter.json"
        }
    }
}
