package com.patrick.lrcreader.core

import android.content.Context
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject

internal object CueMidiStorePersistence {

    private const val TAG = "CueMidiStore"
    private const val FILE_NAME = "midi_cues.json"

    /** Récupère le dossier SPL_Music/BackingTracks/midi via prefs (SAF). */
    private fun getMidiDir(context: Context): DocumentFile? {
        val dirUri = MidiCuesFolderPrefs.get(context) ?: return null
        return DocumentFile.fromTreeUri(context, dirUri)
    }

    private fun getOrCreateJsonFile(context: Context): DocumentFile? {
        val dir = getMidiDir(context) ?: return null
        if (!dir.isDirectory) return null

        // Cherche
        dir.findFile(FILE_NAME)?.let { existing ->
            if (existing.isFile) return existing
        }

        // Crée
        return dir.createFile("application/json", FILE_NAME)
    }

    fun load(context: Context): MutableMap<String, MutableList<CueMidi>> {
        val fileDoc = getOrCreateJsonFile(context)
        if (fileDoc == null) {
            Log.w(TAG, "load: midi folder not set -> empty")
            return mutableMapOf()
        }

        return runCatching {
            val text = context.contentResolver.openInputStream(fileDoc.uri)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                .orEmpty()

            if (text.isBlank()) return@runCatching mutableMapOf()

            val root = JSONObject(text)
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

            Log.d(TAG, "Cues MIDI chargés (${result.size} morceaux) from=${fileDoc.uri}")
            result
        }.getOrElse {
            Log.e(TAG, "Erreur load cues MIDI: ${it.message}", it)
            mutableMapOf()
        }
    }

    fun save(context: Context, cuesByTrack: Map<String, List<CueMidi>>) {
        val fileDoc = getOrCreateJsonFile(context)
        if (fileDoc == null) {
            Log.e(TAG, "save: midi folder not set -> ABORT")
            return
        }

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

            val bytes = root.toString().toByteArray(Charsets.UTF_8)
            val os = context.contentResolver.openOutputStream(fileDoc.uri, "wt")
            if (os == null) {
                Log.e(TAG, "save: openOutputStream null for uri=${fileDoc.uri}")
                return
            }
            os.use { it.write(bytes) }

            Log.d(TAG, "Cues MIDI sauvegardés (${cuesByTrack.size} morceaux) to=${fileDoc.uri}")
        }.onFailure {
            Log.e(TAG, "Erreur save cues MIDI: ${it.message}", it)
        }
    }
}