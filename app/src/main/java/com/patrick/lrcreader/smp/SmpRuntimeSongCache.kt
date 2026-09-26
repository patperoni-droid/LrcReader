package com.patrick.lrcreader.smp

import android.content.Context
import android.util.Log
import com.patrick.lrcreader.core.PlaylistRepository
import com.patrick.lrcreader.core.buildSmpItem
import com.patrick.lrcreader.core.config.TitleAliasesStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

object SmpRuntimeSongCache {
    private const val TAG = "SMP_LIBRARY_CACHE_DIAG"
    private const val FILE_NAME = "smp_runtime_songs_cache.json"
    private const val CACHE_VERSION = 2

    data class CachedSong(
        val song: SongUnit,
        val displayTitle: String,
        val volumeSource: String = SmpConfig.PlaybackConfig.VOLUME_SOURCE_MANUAL,
        val volumeDb: Int? = null,
        val lufsMeasured: Float? = null,
        val lufsTarget: Float? = null,
        val lufsAutoDb: Float? = null,
        val lufsManualDb: Int? = null,
        val arrangementPlayable: Boolean = false
    )

    data class Snapshot(
        val version: Int,
        val workspaceKey: String?,
        val songs: List<CachedSong>,
        val isLegacy: Boolean = false
    ) {
        fun songsById(): Map<String, SongUnit> = songs.associate { it.song.id to it.song }
        fun isForWorkspace(expectedWorkspaceKey: String?): Boolean =
            expectedWorkspaceKey != null && workspaceKey == expectedWorkspaceKey
    }

    fun load(context: Context): List<SongUnit> =
        loadSnapshot(context, expectedWorkspaceKey = null)?.songs?.map(CachedSong::song).orEmpty()

    fun loadSnapshot(context: Context, expectedWorkspaceKey: String?): Snapshot? {
        val file = File(context.filesDir, FILE_NAME)
        val snapshot = loadSnapshotFromFile(file) ?: run {
            Log.i(TAG, "runtime_song_cache_load result=missing_or_invalid file=${file.absolutePath}")
            return null
        }
        if (expectedWorkspaceKey != null && !snapshot.isForWorkspace(expectedWorkspaceKey)) {
            Log.i(
                TAG,
                "runtime_song_cache_load result=workspace_mismatch expected=$expectedWorkspaceKey actual=${snapshot.workspaceKey} legacy=${snapshot.isLegacy}"
            )
            return null
        }
        val usableSongs = snapshot.songs.filter { cached ->
            cached.song.storageFolder?.let(::File)?.isDirectory == true
        }
        return snapshot.copy(songs = usableSongs).also {
            Log.i(
                TAG,
                "runtime_song_cache_load result=hit version=${it.version} count=${it.songs.size} file=${file.absolutePath}"
            )
        }
    }

    fun save(context: Context, songs: Collection<SongUnit>, workspaceKey: String? = null) {
        val file = File(context.filesDir, FILE_NAME)
        runCatching {
            val retainedWorkspaceKey = workspaceKey ?: loadSnapshotFromFile(file)?.workspaceKey
            val songsById = songs.associateBy(SongUnit::id)
            val cachedSongs = songs.sortedBy { it.id }.map { song ->
                val fallbackTitle = song.title.ifBlank { song.id }
                val playbackItem = buildSmpItem(song.id)
                val aliasTitle = TitleAliasesStore.getTitleForTrack(context, playbackItem)
                    ?.cleanDisplayTitle()
                val customTitle = PlaylistRepository.getAnyCustomTitleForUri(playbackItem)
                    ?.cleanDisplayTitle()
                val playback = SmpConfig.readPlaybackFromSongUnit(song)
                CachedSong(
                    song = song,
                    displayTitle = aliasTitle ?: customTitle ?: fallbackTitle,
                    volumeSource = playback?.volumeSource
                        ?: SmpConfig.PlaybackConfig.VOLUME_SOURCE_MANUAL,
                    volumeDb = playback?.volumeDb,
                    lufsMeasured = playback?.lufsMeasured,
                    lufsTarget = playback?.lufsTarget,
                    lufsAutoDb = playback?.lufsAutoDb,
                    lufsManualDb = playback?.lufsManualDb,
                    arrangementPlayable = song.arrangementSourceSongId
                        ?.let(songsById::get)
                        ?.audioPath
                        ?.let(::File)
                        ?.isFile == true
                )
            }
            writeSnapshotAtomic(
                file,
                Snapshot(
                    version = CACHE_VERSION,
                    workspaceKey = retainedWorkspaceKey,
                    songs = cachedSongs
                )
            )
        }.onSuccess {
            Log.i(TAG, "runtime_song_cache_save count=${songs.size} workspace=$workspaceKey file=${file.absolutePath}")
        }.onFailure { error ->
            Log.w(TAG, "runtime_song_cache_save_failed file=${file.absolutePath}", error)
        }
    }

    internal fun loadSnapshotFromFile(file: File): Snapshot? {
        val backup = backupFileFor(file)
        return parseSnapshotFile(file) ?: parseSnapshotFile(backup)
    }

    internal fun writeSnapshotAtomic(file: File, snapshot: Snapshot) {
        file.parentFile?.mkdirs()
        val temp = tempFileFor(file)
        val backup = backupFileFor(file)
        val raw = snapshot.toJson().toString()

        FileOutputStream(temp, false).use { output ->
            output.write(raw.toByteArray(Charsets.UTF_8))
            output.flush()
            output.fd.sync()
        }

        check(parseSnapshotFile(temp) != null) { "Invalid runtime song cache temporary file" }
        if (file.isFile) {
            if (backup.exists()) check(backup.delete()) { "Unable to replace runtime song cache backup" }
            check(file.renameTo(backup)) { "Unable to preserve runtime song cache backup" }
        }
        if (!temp.renameTo(file)) {
            if (!file.exists() && backup.isFile) backup.renameTo(file)
            throw IllegalStateException("Unable to publish runtime song cache")
        }
    }

    private fun parseSnapshotFile(file: File): Snapshot? {
        if (!file.isFile) return null
        return runCatching {
            val raw = file.readText()
            if (raw.isBlank()) return@runCatching null
            val first = raw.firstOrNull { !it.isWhitespace() }
            if (first == '[') {
                Snapshot(
                    version = 1,
                    workspaceKey = null,
                    songs = parseSongs(JSONArray(raw), legacy = true),
                    isLegacy = true
                )
            } else {
                val root = JSONObject(raw)
                val version = root.optInt("version", -1)
                if (version !in 1..CACHE_VERSION) return@runCatching null
                Snapshot(
                    version = version,
                    workspaceKey = root.optNullableString("workspaceKey"),
                    songs = parseSongs(root.optJSONArray("songs") ?: JSONArray(), legacy = version == 1),
                    isLegacy = version == 1
                )
            }
        }.getOrNull()
    }

    private fun parseSongs(array: JSONArray, legacy: Boolean): List<CachedSong> = buildList {
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val song = item.toSongUnit() ?: continue
            add(
                CachedSong(
                    song = song,
                    displayTitle = item.optString("displayTitle")
                        .cleanDisplayTitle()
                        ?: song.title.ifBlank { song.id },
                    volumeSource = item.optString("volumeSource")
                        .takeIf { it == SmpConfig.PlaybackConfig.VOLUME_SOURCE_LUFS }
                        ?: SmpConfig.PlaybackConfig.VOLUME_SOURCE_MANUAL,
                    volumeDb = item.optIntOrNull("volumeDb"),
                    lufsMeasured = item.optFloatOrNull("lufsMeasured"),
                    lufsTarget = item.optFloatOrNull("lufsTarget"),
                    lufsAutoDb = item.optFloatOrNull("lufsAutoDb"),
                    lufsManualDb = item.optIntOrNull("lufsManualDb"),
                    arrangementPlayable = !legacy && item.optBoolean("arrangementPlayable", false)
                )
            )
        }
    }

    private fun Snapshot.toJson(): JSONObject = JSONObject().apply {
        put("version", CACHE_VERSION)
        put("workspaceKey", workspaceKey ?: JSONObject.NULL)
        put("songs", JSONArray().apply {
            songs.forEach { cached ->
                put(cached.song.toJson().apply {
                    put("displayTitle", cached.displayTitle)
                    put("volumeSource", cached.volumeSource)
                    putNullable("volumeDb", cached.volumeDb)
                    putNullable("lufsMeasured", cached.lufsMeasured)
                    putNullable("lufsTarget", cached.lufsTarget)
                    putNullable("lufsAutoDb", cached.lufsAutoDb)
                    putNullable("lufsManualDb", cached.lufsManualDb)
                    put("arrangementPlayable", cached.arrangementPlayable)
                })
            }
        })
    }

    private fun SongUnit.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        putNullable("storageFolder", storageFolder)
        putNullable("audioPath", audioPath)
        putNullable("lyricsPath", lyricsPath)
        putNullable("chordsPath", chordsPath)
        putNullable("timelinePath", timelinePath)
        putNullable("waveformPath", waveformPath)
        putNullable("annotationsPath", annotationsPath)
        putNullable("midiPath", midiPath)
        putNullable("dmxPath", dmxPath)
        putNullable("prompterPath", prompterPath)
        putNullable("arrangementSourceSongId", arrangementSourceSongId)
    }

    private fun JSONObject.toSongUnit(): SongUnit? {
        val id = optString("id").trim().takeIf { it.isNotEmpty() } ?: return null
        val title = optString("title").trim().takeIf { it.isNotEmpty() } ?: id
        return SongUnit(
            id = id,
            title = title,
            storageFolder = optNullableString("storageFolder"),
            audioPath = optNullableString("audioPath"),
            lyricsPath = optNullableString("lyricsPath"),
            chordsPath = optNullableString("chordsPath"),
            timelinePath = optNullableString("timelinePath"),
            waveformPath = optNullableString("waveformPath"),
            annotationsPath = optNullableString("annotationsPath"),
            midiPath = optNullableString("midiPath"),
            midiCues = emptyList(),
            dmxPath = optNullableString("dmxPath"),
            prompterPath = optNullableString("prompterPath"),
            arrangementSourceSongId = optNullableString("arrangementSourceSongId")
        )
    }

    private fun JSONObject.putNullable(name: String, value: Any?) {
        put(name, value ?: JSONObject.NULL)
    }

    private fun JSONObject.optNullableString(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return optString(name).trim().takeIf { it.isNotEmpty() }
    }

    private fun JSONObject.optIntOrNull(name: String): Int? {
        if (!has(name) || isNull(name)) return null
        return opt(name)?.toString()?.toIntOrNull()
    }

    private fun JSONObject.optFloatOrNull(name: String): Float? {
        if (!has(name) || isNull(name)) return null
        return opt(name)?.toString()?.toFloatOrNull()
    }

    private fun String?.cleanDisplayTitle(): String? =
        this?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }

    private fun tempFileFor(file: File): File = File(file.parentFile, "${file.name}.tmp")
    private fun backupFileFor(file: File): File = File(file.parentFile, "${file.name}.bak")
}
