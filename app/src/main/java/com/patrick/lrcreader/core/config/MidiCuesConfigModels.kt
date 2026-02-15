package com.patrick.lrcreader.core.config

import com.patrick.lrcreader.core.CueMidi
import org.json.JSONArray
import org.json.JSONObject

internal data class MidiCuesConfigState(
    val schemaVersion: Int = SCHEMA_VERSION,
    val tracks: Map<String, List<CueMidi>> = emptyMap()
) {
    fun toJson(): JSONObject {
        val root = JSONObject()
        root.put("schemaVersion", schemaVersion)

        val tracksObj = JSONObject()
        tracks.keys.sorted().forEach { relPath ->
            val cues = tracks[relPath].orEmpty()
            val arr = JSONArray()

            cues.forEach { cue ->
                arr.put(
                    JSONObject().apply {
                        put("lineIndex", cue.lineIndex)
                        put("channel", cue.channel)
                        put("program", cue.program)
                    }
                )
            }

            tracksObj.put(relPath, arr)
        }

        root.put("tracks", tracksObj)
        return root
    }

    companion object {
        const val SCHEMA_VERSION = 1

        fun empty(): MidiCuesConfigState = MidiCuesConfigState(
            schemaVersion = SCHEMA_VERSION,
            tracks = emptyMap()
        )

        fun fromJson(raw: String): MidiCuesConfigState {
            val root = JSONObject(raw)
            val schemaVersion = root.optInt("schemaVersion", SCHEMA_VERSION)

            // Compat: si "tracks" absent, on interprète root comme map directe track->[]
            val tracksObj = root.optJSONObject("tracks") ?: root

            val tracks = linkedMapOf<String, List<CueMidi>>()
            val keys = tracksObj.keys().asSequence().toList().sorted()
            keys.forEach { relPath ->
                if (relPath == "schemaVersion") return@forEach
                val arr = tracksObj.optJSONArray(relPath) ?: return@forEach
                val list = mutableListOf<CueMidi>()

                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val hasLine = o.has("lineIndex") && !o.isNull("lineIndex")
                    val hasChannel = o.has("channel") && !o.isNull("channel")
                    val hasProgram = o.has("program") && !o.isNull("program")
                    if (!hasLine || !hasChannel || !hasProgram) continue

                    list += CueMidi(
                        lineIndex = o.optInt("lineIndex"),
                        channel = o.optInt("channel"),
                        program = o.optInt("program")
                    )
                }

                if (list.isNotEmpty()) {
                    tracks[relPath] = list
                }
            }

            return MidiCuesConfigState(
                schemaVersion = schemaVersion,
                tracks = tracks
            )
        }
    }
}
