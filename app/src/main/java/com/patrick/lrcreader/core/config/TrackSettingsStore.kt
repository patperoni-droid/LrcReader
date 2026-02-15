package com.patrick.lrcreader.core.config

import android.content.Context
import android.util.Log
import com.patrick.lrcreader.core.TrackEqSettings
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object TrackSettingsStore {

    private const val TAG = "TrackSettingsStore"

    private val lock = ReentrantLock()
    private var cachedState: TrackSettingsState? = null

    fun ensureInitialized(context: Context): Boolean {
        return TrackSettingsAtomicIo.ensureInitialized(context)
    }

    fun getVolumeDbByUri(context: Context, uriString: String): Int? {
        val relPath = TrackSettingsPathResolver.resolveRelativeTrackPath(context, uriString) ?: return null
        return lock.withLock {
            val state = readStateLocked(context)
            state.tracks[relPath]?.volumeDb
        }
    }

    fun saveVolumeDbByUri(context: Context, uriString: String, volumeDb: Int): Boolean {
        val relPath = TrackSettingsPathResolver.resolveRelativeTrackPath(context, uriString) ?: return false
        return updateTrackLocked(context, relPath) { entry ->
            entry.copy(volumeDb = volumeDb)
        }
    }

    fun getTempoByUri(context: Context, uriString: String): Float? {
        val relPath = TrackSettingsPathResolver.resolveRelativeTrackPath(context, uriString) ?: return null
        return lock.withLock {
            val state = readStateLocked(context)
            state.tracks[relPath]?.tempo
        }
    }

    fun saveTempoByUri(context: Context, uriString: String, tempo: Float): Boolean {
        val relPath = TrackSettingsPathResolver.resolveRelativeTrackPath(context, uriString) ?: return false
        return updateTrackLocked(context, relPath) { entry ->
            entry.copy(tempo = tempo)
        }
    }

    fun getPitchSemiByUri(context: Context, uriString: String): Int? {
        val relPath = TrackSettingsPathResolver.resolveRelativeTrackPath(context, uriString) ?: return null
        return lock.withLock {
            val state = readStateLocked(context)
            state.tracks[relPath]?.pitchSemi
        }
    }

    fun savePitchSemiByUri(context: Context, uriString: String, pitchSemi: Int): Boolean {
        val relPath = TrackSettingsPathResolver.resolveRelativeTrackPath(context, uriString) ?: return false
        return updateTrackLocked(context, relPath) { entry ->
            entry.copy(pitchSemi = pitchSemi)
        }
    }

    fun clearPitchSemiByUri(context: Context, uriString: String): Boolean {
        val relPath = TrackSettingsPathResolver.resolveRelativeTrackPath(context, uriString) ?: return false
        return updateTrackLocked(context, relPath) { entry ->
            entry.copy(pitchSemi = null)
        }
    }

    fun getEqByUri(context: Context, uriString: String): TrackEqSettings? {
        val relPath = TrackSettingsPathResolver.resolveRelativeTrackPath(context, uriString) ?: return null
        return lock.withLock {
            val state = readStateLocked(context)
            val eq = state.tracks[relPath]?.eq ?: return null
            TrackEqSettings(low = eq.low, mid = eq.mid, high = eq.high)
        }
    }

    fun saveEqByUri(context: Context, uriString: String, eq: TrackEqSettings): Boolean {
        val relPath = TrackSettingsPathResolver.resolveRelativeTrackPath(context, uriString) ?: return false
        return updateTrackLocked(context, relPath) { entry ->
            entry.copy(eq = TrackSettingsEq(low = eq.low, mid = eq.mid, high = eq.high))
        }
    }

    fun getTitleColorArgbByUri(context: Context, playlistName: String, uriString: String): Int? {
        val relPath = TrackSettingsPathResolver.resolveRelativeTrackPath(context, uriString) ?: return null
        return lock.withLock {
            val state = readStateLocked(context)
            state.tracks[relPath]?.titleColorByPlaylist?.get(playlistName)
        }
    }

    fun saveTitleColorArgbByUri(
        context: Context,
        playlistName: String,
        uriString: String,
        colorArgb: Int
    ): Boolean {
        val relPath = TrackSettingsPathResolver.resolveRelativeTrackPath(context, uriString) ?: return false
        return updateTrackLocked(context, relPath) { entry ->
            val updated = entry.titleColorByPlaylist.toMutableMap()
            updated[playlistName] = colorArgb
            entry.copy(titleColorByPlaylist = updated)
        }
    }

    fun clearTitleColorByUri(context: Context, playlistName: String, uriString: String): Boolean {
        val relPath = TrackSettingsPathResolver.resolveRelativeTrackPath(context, uriString) ?: return false
        return updateTrackLocked(context, relPath) { entry ->
            val updated = entry.titleColorByPlaylist.toMutableMap()
            updated.remove(playlistName)
            entry.copy(titleColorByPlaylist = updated)
        }
    }

    private fun updateTrackLocked(
        context: Context,
        relPath: String,
        mutate: (TrackSettingsEntry) -> TrackSettingsEntry
    ): Boolean {
        return lock.withLock {
            val current = readStateLocked(context)
            val currentEntry = current.tracks[relPath] ?: TrackSettingsEntry()
            val nextEntry = mutate(currentEntry)

            val nextTracks = current.tracks.toMutableMap()
            if (nextEntry.isEmpty()) {
                nextTracks.remove(relPath)
            } else {
                nextTracks[relPath] = nextEntry
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
                Log.e(TAG, "updateTrackLocked: write failed relPath=$relPath")
                false
            }
        }
    }

    private fun readStateLocked(context: Context): TrackSettingsState {
        cachedState?.let { return it }

        val state = try {
            if (!TrackSettingsAtomicIo.ensureInitialized(context)) {
                TrackSettingsState.empty()
            } else {
                val raw = TrackSettingsAtomicIo.readRaw(context)
                if (raw.isNullOrBlank()) {
                    TrackSettingsState.empty()
                } else {
                    TrackSettingsState.fromJson(raw)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "readStateLocked: parse failed", t)
            TrackSettingsState.empty()
        }

        cachedState = state
        return state
    }
}
