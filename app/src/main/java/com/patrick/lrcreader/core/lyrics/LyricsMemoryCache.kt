package com.patrick.lrcreader.core.lyrics

import android.os.SystemClock
import com.patrick.lrcreader.core.LrcLine
import java.util.LinkedHashMap

data class LyricsCacheEntry(
    val parsedLines: List<LrcLine>,
    val resolvedLyricsFileName: String?,
    val source: String,
    val sourceType: String?,
    val debugPath: String?,
    val loadedAtMs: Long
)

object LyricsMemoryCache {

    private const val MAX_ENTRIES = 32

    private val entries = object : LinkedHashMap<String, LyricsCacheEntry>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LyricsCacheEntry>?): Boolean {
            return size > MAX_ENTRIES
        }
    }

    private var scopeKey: String? = null

    @Synchronized
    fun updateScope(newScopeKey: String?) {
        if (scopeKey == newScopeKey) return
        scopeKey = newScopeKey
        entries.clear()
    }

    @Synchronized
    fun get(trackUriString: String): LyricsCacheEntry? {
        if (trackUriString.isBlank()) return null
        return entries[trackUriString]
    }

    @Synchronized
    fun put(
        trackUriString: String,
        parsedLines: List<LrcLine>,
        resolvedLyricsFileName: String?,
        source: String,
        sourceType: String?,
        debugPath: String?
    ) {
        if (trackUriString.isBlank()) return
        if (parsedLines.isEmpty()) return
        entries[trackUriString] = LyricsCacheEntry(
            parsedLines = parsedLines.toList(),
            resolvedLyricsFileName = resolvedLyricsFileName,
            source = source,
            sourceType = sourceType,
            debugPath = debugPath,
            loadedAtMs = SystemClock.elapsedRealtime()
        )
    }

    @Synchronized
    fun invalidate(trackUriString: String) {
        if (trackUriString.isBlank()) return
        entries.remove(trackUriString)
    }
}
