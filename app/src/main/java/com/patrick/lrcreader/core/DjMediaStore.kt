package com.patrick.lrcreader.core

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import java.io.File

private val DJ_GLOBAL_ROOT_URI: Uri = Uri.parse("dj-global://media")
private const val DJ_GLOBAL_FOLDER_AUTHORITY = "folder"

fun djGlobalRootUri(): Uri = DJ_GLOBAL_ROOT_URI

fun isDjGlobalRoot(uri: Uri?): Boolean {
    return uri?.scheme == DJ_GLOBAL_ROOT_URI.scheme && uri?.authority == DJ_GLOBAL_ROOT_URI.authority
}

fun isDjGlobalFolder(uri: Uri?): Boolean {
    return uri?.scheme == DJ_GLOBAL_ROOT_URI.scheme && uri?.authority == DJ_GLOBAL_FOLDER_AUTHORITY
}

fun djGlobalFolderDisplayName(uri: Uri?): String? {
    if (!isDjGlobalFolder(uri)) return null
    return uri?.pathSegments?.lastOrNull()?.takeIf { it.isNotBlank() }
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
    val projection = mutableListOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.DISPLAY_NAME,
        MediaStore.Audio.Media.DATE_MODIFIED,
        MediaStore.Audio.Media.SIZE
    )
    val folderPathColumnName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        projection += MediaStore.Audio.Media.RELATIVE_PATH
        MediaStore.Audio.Media.RELATIVE_PATH
    } else {
        projection += MediaStore.Audio.Media.DATA
        MediaStore.Audio.Media.DATA
    }
    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.SIZE} > 0"
    val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
    val rootString = DJ_GLOBAL_ROOT_URI.toString()

    return runCatching {
        context.contentResolver.query(
            collection,
            projection.toTypedArray(),
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
            val displayNameColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
            val dateModifiedColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)
            val sizeColumn = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
            val folderPathColumn = cursor.getColumnIndex(folderPathColumnName)

            val directoryEntries = linkedMapOf<String, DjIndexCache.Entry>()
            val fileEntries = ArrayList<DjIndexCache.Entry>()

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
                val rawFolderPath = if (folderPathColumn >= 0) cursor.getString(folderPathColumn) else null
                val normalizedFolderPath = normalizeDjFolderPath(rawFolderPath)
                val parentUriString = ensureDjGlobalFolderEntries(
                    normalizedFolderPath = normalizedFolderPath,
                    rootUriString = rootString,
                    directoryEntries = directoryEntries
                )

                fileEntries += DjIndexCache.Entry(
                    uriString = itemUri.toString(),
                    name = cleanName,
                    isDirectory = false,
                    parentUriString = parentUriString,
                    lastModifiedMs = dateModifiedMs,
                    sizeBytes = sizeBytes
                )
            }

            buildList {
                addAll(directoryEntries.values)
                addAll(fileEntries)
            }
        }.orEmpty()
    }.getOrDefault(emptyList())
}

private fun ensureDjGlobalFolderEntries(
    normalizedFolderPath: String,
    rootUriString: String,
    directoryEntries: MutableMap<String, DjIndexCache.Entry>
): String {
    if (normalizedFolderPath.isBlank()) return rootUriString

    var parentUriString = rootUriString
    val segments = normalizedFolderPath
        .split('/')
        .mapNotNull { segment ->
            segment.trim().takeIf { it.isNotEmpty() }
        }

    val currentPathSegments = ArrayList<String>(segments.size)
    segments.forEach { segment ->
        currentPathSegments += segment
        val folderUri = djGlobalFolderUri(currentPathSegments)
        val folderUriString = folderUri.toString()

        if (directoryEntries[folderUriString] == null) {
            directoryEntries[folderUriString] = DjIndexCache.Entry(
                uriString = folderUriString,
                name = segment,
                isDirectory = true,
                parentUriString = parentUriString
            )
        }

        parentUriString = folderUriString
    }

    return parentUriString
}

private fun djGlobalFolderUri(pathSegments: List<String>): Uri {
    val builder = Uri.Builder()
        .scheme(DJ_GLOBAL_ROOT_URI.scheme)
        .authority(DJ_GLOBAL_FOLDER_AUTHORITY)
    pathSegments.forEach { segment ->
        builder.appendPath(segment)
    }
    return builder.build()
}

private fun normalizeDjFolderPath(rawPath: String?): String {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        return rawPath
            ?.replace('\\', '/')
            ?.trim()
            ?.trim('/')
            .orEmpty()
    }

    val absolutePath = rawPath
        ?.replace('\\', '/')
        ?.trim()
        .orEmpty()
    if (absolutePath.isBlank()) return ""

    val folder = File(absolutePath).parentFile ?: return ""
    val normalized = folder.path.replace('\\', '/').trim('/')
    val storagePrefixes = listOf(
        "/storage/emulated/0/",
        "/storage/self/primary/",
        "/sdcard/"
    )
    storagePrefixes.forEach { prefix ->
        val normalizedPrefix = prefix.trim('/')
        if (normalized.startsWith(normalizedPrefix, ignoreCase = true)) {
            return normalized.removePrefix(normalizedPrefix).trim('/')
        }
    }

    return normalized
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
