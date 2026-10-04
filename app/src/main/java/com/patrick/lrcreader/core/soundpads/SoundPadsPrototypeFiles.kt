package com.patrick.lrcreader.core.soundpads

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Small test bank, separate from the future production bank. Originals never use cacheDir. */
object SoundPadsPrototypeFiles {
    suspend fun defaults(context: Context): List<SoundPad> = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "soundpads-prototype").apply { check(exists() || mkdirs()) }
        listOf("FS_Lounge_Warm.mp3", "FS_Lounge_Warm_Alt.mp3").mapIndexed { index, name ->
            val file = File(dir, name)
            if (!file.isFile) {
                val tmp = File(dir, "$name.tmp")
                try {
                    context.assets.open("fond_sonore/$name").use { input -> tmp.outputStream().use { input.copyTo(it) } }
                    check(tmp.renameTo(file))
                } finally { tmp.delete() }
            }
            SoundPad("prototype-${index + 1}", name, file.absolutePath,
                volume = 0.7f, inMs = 0L, outMs = audioDurationMs(file))
        }
    }

    suspend fun import(context: Context, uri: Uri, previous: SoundPad): SoundPad = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "soundpads-prototype").apply { check(exists() || mkdirs()) }
        val file = File(dir, "${UUID.randomUUID()}.audio")
        val tmp = File(dir, "${file.name}.tmp")
        try {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input)
                tmp.outputStream().use { input.copyTo(it) }
            }
            val duration = audioDurationMs(tmp)
            check(tmp.renameTo(file))
            previous.copy(audioPath = file.absolutePath, inMs = 0, outMs = duration)
        } finally { tmp.delete() }
    }

    internal fun audioDurationMs(file: File): Long {
        val duration = MediaMetadataRetriever().let { retriever ->
            try {
                retriever.setDataSource(file.absolutePath)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            } finally { retriever.release() }
        }
        require(duration != null && duration > 0)
        return duration
    }
}
