package com.patrick.lrcreader.core

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat

private val DJ_GLOBAL_ROOT_URI: Uri = Uri.parse("dj-global://media")

fun djGlobalRootUri(): Uri = DJ_GLOBAL_ROOT_URI

fun isDjGlobalRoot(uri: Uri?): Boolean {
    return uri?.scheme == DJ_GLOBAL_ROOT_URI.scheme && uri?.authority == DJ_GLOBAL_ROOT_URI.authority
}

fun hasDjGlobalAudioAccess(context: Context): Boolean {
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

fun buildDjGlobalAudioIndex(context: Context): List<DjIndexCache.Entry> {
    if (!hasDjGlobalAudioAccess(context)) return emptyList()

    val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.DISPLAY_NAME,
        MediaStore.Audio.Media.DATE_MODIFIED,
        MediaStore.Audio.Media.SIZE
    )
    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.SIZE} > 0"
    val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
    val rootString = DJ_GLOBAL_ROOT_URI.toString()

    return runCatching {
        context.contentResolver.query(
            collection,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
            val displayNameColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
            val dateModifiedColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)
            val sizeColumn = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)

            buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val itemUri = ContentUris.withAppendedId(collection, id)
                    val title = if (titleColumn >= 0) cursor.getString(titleColumn) else null
                    val displayName = if (displayNameColumn >= 0) cursor.getString(displayNameColumn) else null
                    val cleanName = title?.takeIf { it.isNotBlank() }
                        ?: sanitizeDjAudioName(displayName)
                        ?: itemUri.lastPathSegment
                        ?: "Track"
                    val dateModifiedMs = if (dateModifiedColumn >= 0) {
                        cursor.getLong(dateModifiedColumn) * 1000L
                    } else {
                        0L
                    }
                    val sizeBytes = if (sizeColumn >= 0) cursor.getLong(sizeColumn) else 0L

                    add(
                        DjIndexCache.Entry(
                            uriString = itemUri.toString(),
                            name = cleanName,
                            isDirectory = false,
                            parentUriString = rootString,
                            lastModifiedMs = dateModifiedMs,
                            sizeBytes = sizeBytes
                        )
                    )
                }
            }
        }.orEmpty()
    }.getOrDefault(emptyList())
}

private fun sanitizeDjAudioName(raw: String?): String? {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty()) return null

    return trimmed
        .removeSuffix(".mp3")
        .removeSuffix(".MP3")
        .removeSuffix(".wav")
        .removeSuffix(".WAV")
        .removeSuffix(".flac")
        .removeSuffix(".FLAC")
        .removeSuffix(".m4a")
        .removeSuffix(".M4A")
        .removeSuffix(".aac")
        .removeSuffix(".AAC")
        .removeSuffix(".ogg")
        .removeSuffix(".OGG")
        .ifBlank { null }
}
