package com.patrick.lrcreader.core.lyrics

import android.content.Context
import android.os.SystemClock
import com.patrick.lrcreader.core.LrcLine
import com.patrick.lrcreader.core.LyricsPerf
import com.patrick.lrcreader.core.LrcStorage
import com.patrick.lrcreader.core.parseLrc

object LyricsResolver {

    fun resolveLyrics(
        context: Context,
        trackUriString: String,
        embeddedLyrics: String?
    ): List<LrcLine> {
        val resolveStartMs = SystemClock.elapsedRealtime()
        LyricsPerf.mark(
            trackUriString,
            "embedded_resolver_start",
            "embeddedLen=${embeddedLyrics?.length ?: -1}"
        )

        android.util.Log.d("LrcDebug", "RESOLVE uri=$trackUriString")

        val overrideLoadStartMs = SystemClock.elapsedRealtime()
        val override = LrcStorage.loadForTrack(context, trackUriString)
        LyricsPerf.mark(
            trackUriString,
            "embedded_override_load_done",
            "ms=${SystemClock.elapsedRealtime() - overrideLoadStartMs} len=${override?.length ?: -1}"
        )
        android.util.Log.d(
            "LrcDebug",
            "RESOLVE override null=${override == null} len=${override?.length}"
        )



        if (override != null) {
            val parseStartMs = SystemClock.elapsedRealtime()
            val parsed = parseLrc(override)
            LyricsPerf.mark(
                trackUriString,
                "embedded_override_parse_done",
                "ms=${SystemClock.elapsedRealtime() - parseStartMs} lines=${parsed.size}"
            )
            LyricsPerf.mark(
                trackUriString,
                "embedded_resolver_done",
                "ms=${SystemClock.elapsedRealtime() - resolveStartMs} source=override lines=${parsed.size}"
            )
            return parsed
        }

        // 2️⃣ Sinon : paroles embedded (USLT / SYLT)
        if (!embeddedLyrics.isNullOrBlank()) {
            val parseStartMs = SystemClock.elapsedRealtime()
            val parsed = parseLrc(embeddedLyrics)
            LyricsPerf.mark(
                trackUriString,
                "embedded_parse_done",
                "ms=${SystemClock.elapsedRealtime() - parseStartMs} lines=${parsed.size}"
            )
            LyricsPerf.mark(
                trackUriString,
                "embedded_resolver_done",
                "ms=${SystemClock.elapsedRealtime() - resolveStartMs} source=embedded lines=${parsed.size}"
            )
            return parsed
        }

        // 3️⃣ Rien du tout
        LyricsPerf.mark(
            trackUriString,
            "embedded_resolver_done",
            "ms=${SystemClock.elapsedRealtime() - resolveStartMs} source=none lines=0"
        )
        return emptyList()
    }
}
