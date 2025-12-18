package com.patrick.lrcreader.core.lyrics

import android.content.Context
import com.patrick.lrcreader.core.LrcLine
import com.patrick.lrcreader.core.LrcStorage
import com.patrick.lrcreader.core.parseLrc

object LyricsResolver {

    fun resolveLyrics(
        context: Context,
        trackUriString: String,
        embeddedLyrics: String?
    ): List<LrcLine> {

        // 1️⃣ Priorité absolue : paroles utilisateur (.lrc sauvegardé)
        val override = LrcStorage
            .loadForTrack(context, trackUriString)
            ?.takeIf { it.isNotBlank() }

        if (override != null) {
            return parseLrc(override)
        }

        // 2️⃣ Sinon : paroles embedded (USLT / SYLT)
        if (!embeddedLyrics.isNullOrBlank()) {
            return parseLrc(embeddedLyrics)
        }

        // 3️⃣ Rien du tout
        return emptyList()
    }
}
