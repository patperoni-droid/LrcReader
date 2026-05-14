package com.patrick.lrcreader.core.config

import android.content.Context
import android.net.Uri
import android.util.Log
import com.patrick.lrcreader.core.TrackEqSettings
import com.patrick.lrcreader.smp.SmpConfig
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object TrackSettingsStore {

    private const val TAG = "TrackSettingsStore"
    private const val LYRICS_COLOR_SAVE_DIAG_TAG = "LYRICS_COLOR_SAVE_DIAG"
    private const val LYRICS_LINE_COLORS_URI_KEY_PREFIX = "lyricsLineColorsUri:"
    private const val TRACKS_DIR_NAME = "tracks"
    private const val CONFIG_FILE_NAME = "config.json"

    private val lock = ReentrantLock()
    private var cachedState: TrackSettingsState? = null

    private data class ReadKeys(
        val songScopedKey: String?,
        val relativePath: String?
    )

    fun ensureInitialized(context: Context): Boolean {
        return TrackSettingsAtomicIo.ensureInitialized(context)
    }

    fun getVolumeDbByUri(context: Context, uriString: String): Int? {
        return readEntryByUri(context, uriString)?.volumeDb
    }

    fun saveVolumeDbByUri(context: Context, uriString: String, volumeDb: Int): Boolean {
        val keys = resolveReadKeysForUri(context, uriString) ?: return false
        return updateTrackLocked(context, keys) { entry ->
            entry.copy(volumeDb = volumeDb)
        }
    }

    fun getTempoByUri(context: Context, uriString: String): Float? {
        return readEntryByUri(context, uriString)?.tempo
    }

    fun saveTempoByUri(context: Context, uriString: String, tempo: Float): Boolean {
        val keys = resolveReadKeysForUri(context, uriString) ?: return false
        return updateTrackLocked(context, keys) { entry ->
            entry.copy(tempo = tempo)
        }
    }

    fun getTimelineTempoBpmByUri(context: Context, uriString: String): Int? {
        return readEntryByUri(context, uriString)?.timelineTempoBpm
    }

    fun saveTimelineTempoBpmByUri(context: Context, uriString: String, tempoBpm: Int): Boolean {
        val keys = resolveReadKeysForUri(context, uriString) ?: return false
        return updateTrackLocked(context, keys) { entry ->
            entry.copy(timelineTempoBpm = tempoBpm)
        }
    }

    fun getPitchSemiByUri(context: Context, uriString: String): Int? {
        return readEntryByUri(context, uriString)?.pitchSemi
    }

    fun savePitchSemiByUri(context: Context, uriString: String, pitchSemi: Int): Boolean {
        val keys = resolveReadKeysForUri(context, uriString) ?: return false
        return updateTrackLocked(context, keys) { entry ->
            entry.copy(pitchSemi = pitchSemi)
        }
    }

    fun clearPitchSemiByUri(context: Context, uriString: String): Boolean {
        val keys = resolveReadKeysForUri(context, uriString) ?: return false
        return updateTrackLocked(context, keys) { entry ->
            entry.copy(pitchSemi = null)
        }
    }

    fun getEqByUri(context: Context, uriString: String): TrackEqSettings? {
        val eq = readEntryByUri(context, uriString)?.eq ?: return null
        return TrackEqSettings(low = eq.low, mid = eq.mid, high = eq.high)
    }

    fun saveEqByUri(context: Context, uriString: String, eq: TrackEqSettings): Boolean {
        val keys = resolveReadKeysForUri(context, uriString) ?: return false
        return updateTrackLocked(context, keys) { entry ->
            entry.copy(eq = TrackSettingsEq(low = eq.low, mid = eq.mid, high = eq.high))
        }
    }

    fun getTitleColorArgbByUri(context: Context, playlistName: String, uriString: String): Int? {
        return readEntryByUri(context, uriString)?.titleColorByPlaylist?.get(playlistName)
    }

    internal fun getBySongId(context: Context, songId: String): TrackSettingsEntry? {
        val keys = resolveReadKeysForSongId(context, songId) ?: return null
        return lock.withLock {
            readEntryLocked(context, keys)
        }
    }

    fun saveTitleColorArgbByUri(
        context: Context,
        playlistName: String,
        uriString: String,
        colorArgb: Int
    ): Boolean {
        val keys = resolveReadKeysForUri(context, uriString) ?: return false
        return updateTrackLocked(context, keys) { entry ->
            val updated = entry.titleColorByPlaylist.toMutableMap()
            updated[playlistName] = colorArgb
            entry.copy(titleColorByPlaylist = updated)
        }
    }

    fun clearTitleColorByUri(context: Context, playlistName: String, uriString: String): Boolean {
        val keys = resolveReadKeysForUri(context, uriString) ?: return false
        return updateTrackLocked(context, keys) { entry ->
            val updated = entry.titleColorByPlaylist.toMutableMap()
            updated.remove(playlistName)
            entry.copy(titleColorByPlaylist = updated)
        }
    }

    fun getLyricsLineColorsByUri(context: Context, uriString: String): Map<String, Int> {
        resolveSmpConfigFile(context, songId = null, uriString = uriString)?.let { configFile ->
            val storedColors = readSmpLyricsLineColors(configFile)
            Log.d(
                LYRICS_COLOR_SAVE_DIAG_TAG,
                "COLOR_LOAD_CALL songId=null uri=$uriString keyResolved=smp_config:${configFile.absolutePath}"
            )
            Log.d(
                LYRICS_COLOR_SAVE_DIAG_TAG,
                "COLOR_LOAD_RESULT colorCount=${storedColors?.size ?: 0}"
            )
            if (storedColors != null) return storedColors
        }
        val keys = resolveLyricsLineColorKeys(context, songId = null, uriString = uriString)
            ?: return emptyMap()
        Log.d(
            LYRICS_COLOR_SAVE_DIAG_TAG,
            "COLOR_LOAD_CALL songId=null uri=$uriString keyResolved=${describeKeys(keys)}"
        )
        return lock.withLock {
            readEntryLocked(context, keys)?.lyricsLineColors.orEmpty().also { colors ->
                Log.d(LYRICS_COLOR_SAVE_DIAG_TAG, "COLOR_LOAD_RESULT colorCount=${colors.size}")
            }
        }
    }

    fun getLyricsLineColors(
        context: Context,
        songId: String?,
        uriString: String
    ): Map<String, Int> {
        resolveSmpConfigFile(context, songId, uriString)?.let { configFile ->
            val storedColors = readSmpLyricsLineColors(configFile)
            Log.d(
                LYRICS_COLOR_SAVE_DIAG_TAG,
                "COLOR_LOAD_CALL songId=${songId.orEmpty()} uri=$uriString keyResolved=smp_config:${configFile.absolutePath}"
            )
            Log.d(
                LYRICS_COLOR_SAVE_DIAG_TAG,
                "COLOR_LOAD_RESULT colorCount=${storedColors?.size ?: 0}"
            )
            if (storedColors != null) return storedColors
        }
        val keys = resolveLyricsLineColorKeys(context, songId, uriString)
            ?: return emptyMap()
        Log.d(
            LYRICS_COLOR_SAVE_DIAG_TAG,
            "COLOR_LOAD_CALL songId=${songId.orEmpty()} uri=$uriString keyResolved=${describeKeys(keys)}"
        )
        return lock.withLock {
            readEntryLocked(context, keys)?.lyricsLineColors.orEmpty().also { colors ->
                Log.d(LYRICS_COLOR_SAVE_DIAG_TAG, "COLOR_LOAD_RESULT colorCount=${colors.size}")
            }
        }
    }

    fun saveLyricsLineColorsByUri(
        context: Context,
        uriString: String,
        lyricsLineColors: Map<String, Int>
    ): Boolean {
        return saveLyricsLineColors(
            context = context,
            songId = null,
            uriString = uriString,
            lyricsLineColors = lyricsLineColors
        )
    }

    fun saveLyricsLineColors(
        context: Context,
        songId: String?,
        uriString: String,
        lyricsLineColors: Map<String, Int>
    ): Boolean {
        val sanitized = lyricsLineColors
            .filterKeys { it.isNotBlank() }
            .toSortedMap()
        resolveSmpConfigFile(context, songId, uriString)?.let { configFile ->
            Log.d(
                LYRICS_COLOR_SAVE_DIAG_TAG,
                "COLOR_SAVE_CALL songId=${songId.orEmpty()} uri=$uriString keyResolved=smp_config:${configFile.absolutePath} colorCount=${sanitized.size}"
            )
            val saved = writeSmpLyricsLineColors(configFile, sanitized)
            Log.d(
                LYRICS_COLOR_SAVE_DIAG_TAG,
                "COLOR_SAVE_RESULT success=$saved reason=smp_config"
            )
            if (saved) return true
        }
        val keys = resolveLyricsLineColorKeys(context, songId, uriString)
            ?: run {
                Log.e(
                    LYRICS_COLOR_SAVE_DIAG_TAG,
                    "COLOR_SAVE_RESULT success=false reason=no_key songId=${songId.orEmpty()} uri=$uriString"
                )
                return false
            }
        Log.d(
            LYRICS_COLOR_SAVE_DIAG_TAG,
            "COLOR_SAVE_CALL songId=${songId.orEmpty()} uri=$uriString keyResolved=${describeKeys(keys)} colorCount=${sanitized.size}"
        )
        return updateTrackLocked(context, keys) { entry ->
            entry.copy(lyricsLineColors = sanitized)
        }.also { saved ->
            Log.d(
                LYRICS_COLOR_SAVE_DIAG_TAG,
                "COLOR_SAVE_RESULT success=$saved reason=track_settings"
            )
        }
    }

    fun clearLyricsLineColorsByUri(context: Context, uriString: String): Boolean {
        resolveSmpConfigFile(context, songId = null, uriString = uriString)?.let { configFile ->
            Log.d(
                LYRICS_COLOR_SAVE_DIAG_TAG,
                "COLOR_SAVE_CALL songId=null uri=$uriString keyResolved=smp_config:${configFile.absolutePath} colorCount=0"
            )
            val saved = writeSmpLyricsLineColors(configFile, emptyMap())
            Log.d(
                LYRICS_COLOR_SAVE_DIAG_TAG,
                "COLOR_SAVE_RESULT success=$saved reason=smp_config"
            )
            if (saved) return true
        }
        val keys = resolveLyricsLineColorKeys(context, songId = null, uriString = uriString)
            ?: return false
        return updateTrackLocked(context, keys) { entry ->
            entry.copy(lyricsLineColors = emptyMap())
        }.also { saved ->
            Log.d(
                LYRICS_COLOR_SAVE_DIAG_TAG,
                "COLOR_SAVE_RESULT success=$saved reason=track_settings_clear"
            )
        }
    }

    private fun updateTrackLocked(
        context: Context,
        keys: ReadKeys,
        mutate: (TrackSettingsEntry) -> TrackSettingsEntry
    ): Boolean {
        return lock.withLock {
            val current = readStateLocked(context)
            val currentEntry = readEntryLocked(context, keys) ?: TrackSettingsEntry()
            val nextEntry = mutate(currentEntry)
            val writeKeys = linkedSetOf<String>().apply {
                if (keys.songScopedKey != null) {
                    add(keys.songScopedKey)
                } else {
                    keys.relativePath?.let(::add)
                }
            }
            val cleanupKeys = linkedSetOf<String>().apply {
                if (keys.songScopedKey != null) {
                    keys.relativePath?.let(::add)
                }
            }
            if (writeKeys.isEmpty()) {
                Log.e(TAG, "updateTrackLocked: no writable key")
                return@withLock false
            }

            val nextTracks = current.tracks.toMutableMap()
            if (nextEntry.isEmpty()) {
                (writeKeys + cleanupKeys).forEach(nextTracks::remove)
            } else {
                writeKeys.forEach { key ->
                    nextTracks[key] = nextEntry
                }
                cleanupKeys.forEach(nextTracks::remove)
            }

            val nextState = current.copy(
                schemaVersion = TrackSettingsState.SCHEMA_VERSION,
                tracks = nextTracks
            )

            val raw = nextState.toJson().toString(2)
            val saved = TrackSettingsAtomicIo.writeRawAtomic(context, raw)
            if (saved) {
                cachedState = nextState
                true
            } else {
                Log.e(TAG, "updateTrackLocked: write failed keys=$writeKeys cleanupKeys=$cleanupKeys")
                false
            }
        }
    }

    private fun readStateLocked(context: Context): TrackSettingsState {
        cachedState?.let { return it }

        val state = try {
            val raw = TrackSettingsAtomicIo.readRaw(context)
            if (raw.isNullOrBlank()) {
                TrackSettingsState.empty()
            } else {
                TrackSettingsState.fromJson(raw)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "readStateLocked: parse failed", t)
            TrackSettingsState.empty()
        }

        cachedState = state
        return state
    }

    private fun readEntryByUri(context: Context, uriString: String): TrackSettingsEntry? {
        val keys = resolveReadKeysForUri(context, uriString) ?: return null
        return lock.withLock {
            readEntryLocked(context, keys)
        }
    }

    private fun readEntryLocked(context: Context, keys: ReadKeys): TrackSettingsEntry? {
        val state = readStateLocked(context)
        keys.songScopedKey?.let { songKey ->
            state.tracks[songKey]?.let { return it }
        }
        val relPath = keys.relativePath ?: return null
        return state.tracks[relPath]
    }

    private fun resolveReadKeysForUri(context: Context, uriString: String): ReadKeys? {
        val songScopedKey = SongIdKeyResolver.songScopedKey(
            SongIdKeyResolver.resolveSongIdFromUri(context, uriString)
        )
        val relativePath = TrackSettingsPathResolver.resolveRelativeTrackPath(context, uriString)
        if (songScopedKey == null && relativePath == null) return null
        return ReadKeys(
            songScopedKey = songScopedKey,
            relativePath = relativePath
        )
    }

    private fun resolveReadKeysForSongId(context: Context, songId: String): ReadKeys? {
        val songScopedKey = SongIdKeyResolver.songScopedKey(songId) ?: return null
        return ReadKeys(
            songScopedKey = songScopedKey,
            relativePath = SongIdKeyResolver.resolveLegacyRelativePathBySongId(context, songId)
        )
    }

    private fun resolveLyricsLineColorKeys(
        context: Context,
        songId: String?,
        uriString: String
    ): ReadKeys? {
        songId
            ?.let { resolveReadKeysForSongId(context, it) }
            ?.let { return it }

        resolveReadKeysForUri(context, uriString)?.let { return it }

        val trimmedUri = uriString.trim()
        if (trimmedUri.isBlank()) return null
        return ReadKeys(
            songScopedKey = null,
            relativePath = LYRICS_LINE_COLORS_URI_KEY_PREFIX + stableKeyHash(trimmedUri)
        )
    }

    private fun resolveSmpConfigFile(
        context: Context,
        songId: String?,
        uriString: String
    ): File? {
        val resolvedSongId = SongIdKeyResolver.normalizeSongId(songId)
            ?: SongIdKeyResolver.resolveSongIdFromUri(context, uriString)
        resolvedSongId?.let { cleanSongId ->
            val configFile = File(File(File(context.filesDir, TRACKS_DIR_NAME), cleanSongId), CONFIG_FILE_NAME)
            if (configFile.isFile) return configFile
        }

        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null
        if (!uri.scheme.equals("file", ignoreCase = true)) return null
        val audioFile = uri.path
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.takeIf { it.isFile }
            ?: return null
        val songDir = runCatching { audioFile.parentFile?.canonicalFile }.getOrNull() ?: return null
        val tracksDir = runCatching { File(context.filesDir, TRACKS_DIR_NAME).canonicalFile }.getOrNull()
            ?: return null
        if (songDir.parentFile?.canonicalFile != tracksDir) return null
        return File(songDir, CONFIG_FILE_NAME).takeIf { it.isFile }
    }

    private fun readSmpLyricsLineColors(configFile: File): Map<String, Int>? {
        if (!configFile.isFile) return null
        return runCatching {
            SmpConfig.fromJsonOrNull(configFile.readText(Charsets.UTF_8))?.lyricsLineColors
        }.getOrElse { error ->
            Log.w(TAG, "readSmpLyricsLineColors failed path=${configFile.absolutePath}", error)
            null
        }
    }

    private fun writeSmpLyricsLineColors(configFile: File, lyricsLineColors: Map<String, Int>): Boolean {
        val songDir = configFile.parentFile ?: return false
        val tmpFile = File(songDir, "$CONFIG_FILE_NAME.tmp")
        return runCatching {
            val currentConfig = SmpConfig.fromJsonOrNull(configFile.readText(Charsets.UTF_8))
                ?: return false
            val nextConfig = currentConfig.copy(lyricsLineColors = lyricsLineColors)
            val rawJson = nextConfig.toJsonString()
            tmpFile.writeText(rawJson, Charsets.UTF_8)
            if (configFile.exists() && !configFile.delete()) {
                Log.w(TAG, "writeSmpLyricsLineColors delete failed path=${configFile.absolutePath}")
            }
            if (!tmpFile.renameTo(configFile)) {
                configFile.writeText(rawJson, Charsets.UTF_8)
                tmpFile.delete()
            }
            true
        }.getOrElse { error ->
            Log.w(TAG, "writeSmpLyricsLineColors failed path=${configFile.absolutePath}", error)
            runCatching { tmpFile.delete() }
            false
        }
    }

    private fun describeKeys(keys: ReadKeys): String {
        return listOfNotNull(
            keys.songScopedKey?.let { "songScopedKey:$it" },
            keys.relativePath?.let { "relativePath:$it" }
        ).joinToString(separator = "|").ifBlank { "none" }
    }

    private fun stableKeyHash(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { "%02x".format(it.toInt() and 0xff) }
    }
}
