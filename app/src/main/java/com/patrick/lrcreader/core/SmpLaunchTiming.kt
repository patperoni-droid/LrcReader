package com.patrick.lrcreader.core

import android.os.SystemClock
import android.util.Log
import java.util.concurrent.atomic.AtomicLong

object SmpLaunchTiming {
    private const val TAG = "SMP_LAUNCH_TIMING"

    private data class Session(
        val id: Long,
        val source: String,
        val requestedItem: String,
        val playlistName: String?,
        val startedAtMs: Long,
        val requestedSongId: String?,
        val resolvedSongId: String? = null,
        val resolvedUri: String? = null,
        val exoPrepareStartedAtMs: Long? = null,
        val readyLogged: Boolean = false,
        val playingLogged: Boolean = false
    )

    private val nextSessionId = AtomicLong(0L)

    @Volatile
    private var activeSession: Session? = null

    fun start(
        source: String,
        requestedItem: String,
        playlistName: String? = null,
        songId: String? = null
    ) {
        activeSession = Session(
            id = nextSessionId.incrementAndGet(),
            source = source,
            requestedItem = requestedItem,
            playlistName = playlistName,
            startedAtMs = now(),
            requestedSongId = songId?.takeIf { it.isNotBlank() }
        )
        Log.i(
            TAG,
            "start click source=$source item=$requestedItem playlist=${playlistName ?: "-"} songId=${songId ?: "-"}"
        )
    }

    fun markResolveSongStart(songId: String, playlistName: String?) {
        mark("resolveSong start", "songId=$songId playlist=${playlistName ?: "-"}")
    }

    fun markResolveSongDone(songId: String, audioPath: String?) {
        updateSession { it.copy(resolvedSongId = songId) }
        mark("resolveSong done", "songId=$songId audioPath=${audioPath ?: "-"}")
    }

    fun markResolvedAudioTarget(uri: String, playlistName: String?, songId: String? = null) {
        updateSession {
            it.copy(
                resolvedUri = uri,
                resolvedSongId = songId?.takeIf { clean -> clean.isNotBlank() } ?: it.resolvedSongId
            )
        }
        mark(
            "resolveAudio done",
            "uri=$uri playlist=${playlistName ?: "-"} songId=${songId ?: activeSession?.resolvedSongId ?: "-"}"
        )
    }

    fun markConfigLoadStart(uri: String) {
        if (!matches(uri)) return
        mark("loadConfig start", "uri=$uri thread=${Thread.currentThread().name}")
    }

    fun markConfigLoadDone(uri: String, volumeSource: String, tempo: Float, pitchSemi: Int, gainDb: Int) {
        if (!matches(uri)) return
        mark(
            "loadConfig done",
            "uri=$uri gainDb=$gainDb volumeSource=$volumeSource tempo=$tempo pitchSemi=$pitchSemi thread=${Thread.currentThread().name}"
        )
    }

    fun markExoPrepareStart(uri: String) {
        updateSessionIfMatches(uri) {
            it.copy(resolvedUri = uri, exoPrepareStartedAtMs = now())
        }
        if (!matches(uri)) return
        mark("exoPrepare start", "uri=$uri")
    }

    fun markExoReady(uri: String) {
        val session = activeSession ?: return
        if (!sessionMatches(session, uri) || session.readyLogged) return
        val prepareDelta = session.exoPrepareStartedAtMs?.let { now() - it }
        activeSession = session.copy(readyLogged = true)
        mark(
            "exoReady",
            "uri=$uri prepareMs=${prepareDelta ?: -1} totalToReady=${now() - session.startedAtMs}ms"
        )
    }

    fun markPlaybackStart(uri: String) {
        val session = activeSession ?: return
        if (!sessionMatches(session, uri) || session.playingLogged) return
        activeSession = session.copy(playingLogged = true)
        mark("playStart", "uri=$uri totalToPlay=${now() - session.startedAtMs}ms")
    }

    fun markTimelineStart(uri: String) {
        if (!matches(uri)) return
        mark("loadTimeline start", "uri=$uri")
    }

    fun markTimelineDone(uri: String, markerCount: Int, lightCueCount: Int) {
        if (!matches(uri)) return
        mark("loadTimeline done", "uri=$uri markers=$markerCount lightCues=$lightCueCount")
    }

    fun markLyricsStart(uri: String) {
        if (!matches(uri)) return
        mark("loadLyrics start", "uri=$uri")
    }

    fun markLyricsDone(uri: String, source: String) {
        if (!matches(uri)) return
        mark("loadLyrics done", "uri=$uri source=$source")
    }

    fun markChordsStart(uri: String) {
        if (!matches(uri)) return
        mark("loadChords start", "uri=$uri")
    }

    fun markChordsDone(uri: String, lineCount: Int, hasSource: Boolean) {
        if (!matches(uri)) return
        mark("loadChords done", "uri=$uri lines=$lineCount hasSource=$hasSource")
    }

    fun markFailure(step: String, detail: String) {
        mark(step, detail)
    }

    private fun mark(step: String, detail: String) {
        val session = activeSession ?: return
        Log.i(
            TAG,
            "$step +${now() - session.startedAtMs}ms source=${session.source} item=${session.requestedItem} detail={$detail}"
        )
    }

    private fun matches(uri: String?): Boolean {
        val session = activeSession ?: return false
        return sessionMatches(session, uri)
    }

    private fun sessionMatches(session: Session, uri: String?): Boolean {
        val cleanUri = uri?.takeIf { it.isNotBlank() } ?: return false
        return cleanUri == session.resolvedUri || cleanUri == session.requestedItem
    }

    private fun updateSession(transform: (Session) -> Session) {
        val session = activeSession ?: return
        activeSession = transform(session)
    }

    private fun updateSessionIfMatches(uri: String?, transform: (Session) -> Session) {
        val session = activeSession ?: return
        if (!sessionMatches(session, uri)) return
        activeSession = transform(session)
    }

    private fun now(): Long = SystemClock.elapsedRealtime()
}
