package com.patrick.lrcreader.smp

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object SmpRuntimeSongCache {
    private const val TAG = "SMP_LIBRARY_CACHE_DIAG"
    private const val FILE_NAME = "smp_runtime_songs_cache.json"

    fun load(context: Context): List<SongUnit> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.isFile) {
            Log.i(TAG, "runtime_song_cache_load result=missing file=${file.absolutePath}")
            return emptyList()
        }

        return runCatching {
            val raw = file.readText()
            if (raw.isBlank()) return@runCatching emptyList()
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val song = item.toSongUnit() ?: continue
                    val songDir = song.storageFolder?.let(::File)
                    if (songDir?.isDirectory == true) {
                        add(song)
                    }
                }
            }
        }.onFailure { error ->
            Log.w(TAG, "runtime_song_cache_load result=parse_failed file=${file.absolutePath}", error)
            runCatching { file.delete() }
        }.getOrDefault(emptyList()).also { songs ->
            Log.i(TAG, "runtime_song_cache_load result=hit count=${songs.size} file=${file.absolutePath}")
        }
    }

    fun save(context: Context, songs: Collection<SongUnit>) {
        val file = File(context.filesDir, FILE_NAME)
        runCatching {
            val array = JSONArray()
            songs.sortedBy { it.id }.forEach { song ->
                array.put(song.toJson())
            }
            file.writeText(array.toString())
        }.onSuccess {
            Log.i(TAG, "runtime_song_cache_save count=${songs.size} file=${file.absolutePath}")
        }.onFailure { error ->
            Log.w(TAG, "runtime_song_cache_save_failed file=${file.absolutePath}", error)
        }
    }

    private fun SongUnit.toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("title", title)
            put("storageFolder", storageFolder)
            put("audioPath", audioPath)
            put("lyricsPath", lyricsPath)
            put("chordsPath", chordsPath)
            put("timelinePath", timelinePath)
            put("waveformPath", waveformPath)
            put("annotationsPath", annotationsPath)
            put("midiPath", midiPath)
            put("dmxPath", dmxPath)
            put("prompterPath", prompterPath)
        }
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
            prompterPath = optNullableString("prompterPath")
        )
    }

    private fun JSONObject.optNullableString(name: String): String? {
        if (isNull(name)) return null
        return optString(name).trim().takeIf { it.isNotEmpty() }
    }
}
