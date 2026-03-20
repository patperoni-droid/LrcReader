package com.patrick.lrcreader.smp

import android.content.Context
import java.io.File

object SmpStorage {

    fun resetAllTracks(context: Context) {
        val tracksDir = File(context.filesDir, "tracks")

        if (tracksDir.exists()) {
            tracksDir.deleteRecursively()
        }

        tracksDir.mkdirs()
    }
}
