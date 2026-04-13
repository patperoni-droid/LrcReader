package com.patrick.lrcreader.smp

import android.util.Log
import com.patrick.lrcreader.core.notes.LiveNote
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object SmpAnnotationsStore {

    const val ANNOTATIONS_FILE_NAME = "annotations.json"
    private const val TAG = "SmpAnnotationsStore"
    private val fileLocks = ConcurrentHashMap<String, ReentrantLock>()

    fun read(annotationsFile: File): List<LiveNote> {
        if (!annotationsFile.isFile) {
            return emptyList()
        }

        return runCatching {
            val rawJson = annotationsFile.readText(Charsets.UTF_8)
            liveNotesFromJson(rawJson)
        }.getOrElse { error ->
            Log.e(TAG, "Lecture annotations.json impossible: ${annotationsFile.absolutePath}", error)
            emptyList()
        }
    }

    fun awaitIdle(annotationsFile: File) {
        lockFor(annotationsFile).withLock {
            // Wait for any in-flight write on this annotations file to complete.
        }
    }

    fun write(annotationsFile: File, notes: List<LiveNote>): Boolean {
        return lockFor(annotationsFile).withLock {
            val songDir = annotationsFile.parentFile ?: return false
            val tmpFile = File(songDir, "${annotationsFile.name}.tmp")
            val normalized = notes.sortedWith(
                compareBy<LiveNote> { it.timeMs }
                    .thenBy { it.durationMs }
                    .thenBy { it.text }
            )
            val rawJson = liveNotesToJsonString(normalized)

            runCatching {
                songDir.mkdirs()
                tmpFile.writeText(rawJson, Charsets.UTF_8)
                if (annotationsFile.exists() && !annotationsFile.delete()) {
                    Log.w(TAG, "Suppression annotations.json impossible: ${annotationsFile.absolutePath}")
                }
                if (!tmpFile.renameTo(annotationsFile)) {
                    tmpFile.writeText(rawJson, Charsets.UTF_8)
                    annotationsFile.writeText(rawJson, Charsets.UTF_8)
                    tmpFile.delete()
                }
                syncExistingMeta(songDir, annotationsFile.name)
                true
            }.getOrElse { error ->
                Log.e(TAG, "Ecriture annotations.json impossible: ${annotationsFile.absolutePath}", error)
                runCatching { tmpFile.delete() }
                false
            }
        }
    }

    private fun liveNotesToJsonString(notes: List<LiveNote>, indentSpaces: Int = 2): String {
        return JSONArray().apply {
            notes.forEach { note ->
                put(
                    JSONObject().apply {
                        put("timeMs", note.timeMs)
                        put("durationMs", note.durationMs)
                        put("text", note.text)
                    }
                )
            }
        }.toString(indentSpaces)
    }

    private fun liveNotesFromJson(rawJson: String?): List<LiveNote> {
        if (rawJson.isNullOrBlank()) {
            return emptyList()
        }

        return runCatching {
            val jsonArray = JSONArray(rawJson)
            buildList {
                for (index in 0 until jsonArray.length()) {
                    val obj = jsonArray.optJSONObject(index) ?: continue
                    val hasTime = obj.has("timeMs") && !obj.isNull("timeMs")
                    val hasDuration = obj.has("durationMs") && !obj.isNull("durationMs")
                    val hasText = obj.has("text") && !obj.isNull("text")
                    if (!hasTime || !hasDuration || !hasText) {
                        continue
                    }

                    add(
                        LiveNote(
                            timeMs = obj.optLong("timeMs"),
                            durationMs = obj.optLong("durationMs"),
                            text = obj.optString("text")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun syncExistingMeta(songDir: File, fileName: String) {
        val currentMeta = SmpMetaStore.read(songDir) ?: return
        val nextMeta = currentMeta.copy(
            annotationsFile = fileName,
            updatedAt = System.currentTimeMillis()
        )
        if (!SmpMetaStore.write(songDir, nextMeta)) {
            Log.w(TAG, "Synchronisation meta.json impossible après sauvegarde annotations: ${songDir.absolutePath}")
        }
    }

    private fun lockFor(annotationsFile: File): ReentrantLock {
        val key = runCatching { annotationsFile.canonicalPath }.getOrElse { annotationsFile.absolutePath }
        return fileLocks.getOrPut(key) { ReentrantLock() }
    }
}
