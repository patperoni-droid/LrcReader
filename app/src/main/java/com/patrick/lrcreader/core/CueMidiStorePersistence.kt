package com.patrick.lrcreader.core

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal object CueMidiStorePersistence {

    private const val TAG = "CueMidiStore"
    private const val FILE_NAME = "midi_cues.json"

    fun load(context: Context): MutableMap<String, MutableList<CueMidi>> {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return mutableMapOf()

        return runCatching {
            val root = JSONObject(file.readText())
            val result: MutableMap<String, MutableList<CueMidi>> = mutableMapOf()

            val tracks = root.keys()
            while (tracks.hasNext()) {
                val trackKey = tracks.next()
                val arr = root.optJSONArray(trackKey) ?: JSONArray()
                val list = mutableListOf<CueMidi>()

                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list += CueMidi(
                        lineIndex = o.getInt("lineIndex"),
                        channel = o.getInt("channel"),
                        program = o.getInt("program")
                    )
                }
                result[trackKey] = list
            }

            Log.d(TAG, "Cues MIDI chargés (${result.size} morceaux)")
            result
        }.getOrElse {
            Log.e(TAG, "Erreur load cues MIDI: ${it.message}", it)
            mutableMapOf()
        }
    }

    fun save(context: Context, cuesByTrack: Map<String, List<CueMidi>>) {
        val file = File(context.filesDir, FILE_NAME)

        runCatching {
            val root = JSONObject()
            cuesByTrack.forEach { (trackKey, cues) ->
                val arr = JSONArray()
                cues.forEach { cue ->
                    val o = JSONObject()
                    o.put("lineIndex", cue.lineIndex)
                    o.put("channel", cue.channel)
                    o.put("program", cue.program)
                    arr.put(o)
                }
                root.put(trackKey, arr)
            }
            file.writeText(root.toString())
        }.onFailure {
            Log.e(TAG, "Erreur save cues MIDI: ${it.message}", it)
        }
    }
}