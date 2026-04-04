package com.patrick.lrcreader.core.config

import android.content.Context
import android.util.Log
import com.patrick.lrcreader.core.CueMidi
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal object MidiCuesConfigStore {

    private const val TAG = "MidiCuesConfigStore"

    private val lock = ReentrantLock()
    private var cachedState: MidiCuesConfigState? = null

    fun ensureInitialized(context: Context): Boolean {
        return MidiCuesConfigAtomicIo.ensureInitialized(context)
    }

    fun loadAll(context: Context): MutableMap<String, MutableList<CueMidi>> {
        return lock.withLock {
            val state = readStateLocked(context)
            val out = linkedMapOf<String, MutableList<CueMidi>>()
            state.tracks.forEach { (relPath, cues) ->
                val key = sanitizeRelativePath(relPath) ?: return@forEach
                out[key] = cues.toMutableList()
            }
            out
        }
    }

    fun saveAll(context: Context, tracks: Map<String, List<CueMidi>>): Boolean {
        return lock.withLock {
            val cleaned = linkedMapOf<String, List<CueMidi>>()
            tracks.forEach { (relPath, cues) ->
                val key = sanitizeRelativePath(relPath) ?: return@forEach
                if (cues.isNotEmpty()) {
                    cleaned[key] = cues
                }
            }

            val next = MidiCuesConfigState(
                schemaVersion = MidiCuesConfigState.SCHEMA_VERSION,
                tracks = cleaned
            )
            val raw = next.toJson().toString(2)
            val saved = MidiCuesConfigAtomicIo.writeRawAtomic(context, raw)
            if (saved) {
                cachedState = next
                true
            } else {
                Log.e(TAG, "saveAll: write failed")
                false
            }
        }
    }

    private fun readStateLocked(context: Context): MidiCuesConfigState {
        cachedState?.let { return it }

        val state = try {
            val raw = MidiCuesConfigAtomicIo.readRaw(context)
            if (raw.isNullOrBlank()) {
                MidiCuesConfigState.empty()
            } else {
                MidiCuesConfigState.fromJson(raw)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "readStateLocked: parse failed", t)
            MidiCuesConfigState.empty()
        }

        cachedState = state
        return state
    }

    private fun sanitizeRelativePath(raw: String?): String? {
        val normalized = raw
            ?.replace('\\', '/')
            ?.trim()
            ?.trim('/')
            ?: return null

        if (normalized.isBlank()) return null
        if (normalized.startsWith("../") || normalized == "..") return null
        if (normalized.contains("://")) return null
        if (normalized.startsWith('/')) return null
        if (normalized.matches(Regex("^[A-Za-z]:.*"))) return null

        val segments = normalized.split('/')
        if (segments.any { it.isBlank() || it == "." || it == ".." }) return null

        return normalized
    }
}
