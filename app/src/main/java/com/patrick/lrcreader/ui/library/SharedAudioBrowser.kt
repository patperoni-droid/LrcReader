package com.patrick.lrcreader.ui.library

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.patrick.lrcreader.core.hasDjGlobalAudioAccess
import com.patrick.lrcreader.ui.LibraryEntry
import java.io.File
import java.util.LinkedHashMap

private const val SHARED_AUDIO_SCHEME = "spl-shared-audio"
private const val SHARED_AUDIO_ROOT_AUTHORITY = "root"
private const val SHARED_AUDIO_FOLDER_AUTHORITY = "folder"

internal val SHARED_AUDIO_ROOT_URI: Uri =
    Uri.parse("$SHARED_AUDIO_SCHEME://$SHARED_AUDIO_ROOT_AUTHORITY")

private data class SharedAudioItem(
    val uri: Uri,
    val displayName: String,
    val folderPath: String
)

internal fun isSharedAudioFolderUri(uri: Uri?): Boolean {
    return uri?.scheme == SHARED_AUDIO_SCHEME &&
        (uri.authority == SHARED_AUDIO_ROOT_AUTHORITY || uri.authority == SHARED_AUDIO_FOLDER_AUTHORITY)
}

internal fun sharedAudioFolderDisplayName(
    uri: Uri?,
    rootLabel: String
): String {
    if (!isSharedAudioFolderUri(uri)) return rootLabel
    val path = sharedAudioFolderPath(uri)
    if (path.isBlank()) return rootLabel
    return path.substringAfterLast('/').ifBlank { rootLabel }
}

internal fun buildSharedAudioEntriesForFolder(
    context: Context,
    folderUri: Uri,
    liveTracksUri: Uri,
    liveTracksLabel: String
): List<LibraryEntry> {
    if (!isSharedAudioFolderUri(folderUri) || !hasDjGlobalAudioAccess(context)) return emptyList()

    val currentPath = sharedAudioFolderPath(folderUri)
    val folders = LinkedHashMap<String, LibraryEntry>()
    val files = ArrayList<LibraryEntry>()

    querySharedAudioItems(context).forEach { item ->
        val itemFolderPath = item.folderPath
        when {
            currentPath.isBlank() -> {
                if (itemFolderPath.isBlank()) {
                    files += LibraryEntry(
                        uri = item.uri,
                        name = item.displayName,
                        isDirectory = false
                    )
                } else {
                    val childName = itemFolderPath.substringBefore('/')
                    val childPath = childName
                    folders.putIfAbsent(
                        childPath,
                        buildSharedAudioFolderEntry(
                            fullPath = childPath,
                            folderName = childName,
                            liveTracksUri = liveTracksUri,
                            liveTracksLabel = liveTracksLabel
                        )
                    )
                }
            }

            itemFolderPath == currentPath -> {
                files += LibraryEntry(
                    uri = item.uri,
                    name = item.displayName,
                    isDirectory = false
                )
            }

            itemFolderPath.startsWith("$currentPath/") -> {
                val remainder = itemFolderPath.removePrefix("$currentPath/").trim('/')
                if (remainder.isBlank()) return@forEach
                val childName = remainder.substringBefore('/')
                val childPath = "$currentPath/$childName"
                folders.putIfAbsent(
                    childPath,
                    buildSharedAudioFolderEntry(
                        fullPath = childPath,
                        folderName = childName,
                        liveTracksUri = liveTracksUri,
                        liveTracksLabel = liveTracksLabel
                    )
                )
            }
        }
    }

    return buildList {
        addAll(folders.values.sortedBy { it.name.lowercase() })
        addAll(files.sortedBy { it.name.lowercase() })
    }
}

private fun buildSharedAudioFolderEntry(
    fullPath: String,
    folderName: String,
    liveTracksUri: Uri,
    liveTracksLabel: String
): LibraryEntry {
    return if (isLiveTracksAudioPath(fullPath)) {
        LibraryEntry(
            uri = liveTracksUri,
            name = liveTracksLabel,
            isDirectory = true
        )
    } else {
        LibraryEntry(
            uri = buildSharedAudioFolderUri(fullPath),
            name = folderName,
            isDirectory = true
        )
    }
}

private fun buildSharedAudioFolderUri(path: String): Uri {
    return Uri.Builder()
        .scheme(SHARED_AUDIO_SCHEME)
        .authority(SHARED_AUDIO_FOLDER_AUTHORITY)
        .appendQueryParameter("path", path)
        .build()
}

private fun sharedAudioFolderPath(uri: Uri?): String {
    if (!isSharedAudioFolderUri(uri)) return ""
    return uri?.getQueryParameter("path")
        .orEmpty()
        .replace('\\', '/')
        .trim('/')
}

private fun isLiveTracksAudioPath(path: String): Boolean {
    val segments = path
        .split('/')
        .mapNotNull { raw ->
            raw.trim().takeIf { it.isNotEmpty() }
        }
        .map(::normalizeAudioFolderToken)

    val liveIndex = segments.indexOfFirst { it == "backingtracks" || it == "backingtrack" }
    if (liveIndex < 0) return false
    return segments.take(liveIndex).any { it == "splmusic" }
}

private fun normalizeAudioFolderToken(raw: String): String {
    return raw
        .trim()
        .lowercase()
        .replace("_", "")
        .replace(" ", "")
}

private fun querySharedAudioItems(context: Context): List<SharedAudioItem> {
    val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val projection = mutableListOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.DISPLAY_NAME,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.SIZE
    )
    val relativePathColumnName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        projection += MediaStore.Audio.Media.RELATIVE_PATH
        MediaStore.Audio.Media.RELATIVE_PATH
    } else {
        projection += MediaStore.Audio.Media.DATA
        MediaStore.Audio.Media.DATA
    }
    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.SIZE} > 0"
    val sortOrder = "$relativePathColumnName COLLATE NOCASE ASC, ${MediaStore.Audio.Media.DISPLAY_NAME} COLLATE NOCASE ASC"

    return runCatching {
        context.contentResolver.query(
            collection,
            projection.toTypedArray(),
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val displayNameColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
            val titleColumn = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
            val relativePathColumn = cursor.getColumnIndex(relativePathColumnName)

            buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val itemUri = ContentUris.withAppendedId(collection, id)
                    val displayName = if (displayNameColumn >= 0) {
                        cursor.getString(displayNameColumn)
                    } else {
                        null
                    }?.takeIf { it.isNotBlank() }
                        ?: if (titleColumn >= 0) cursor.getString(titleColumn) else null
                        ?: itemUri.lastPathSegment
                        ?: "audio"

                    val rawFolderPath = if (relativePathColumn >= 0) {
                        cursor.getString(relativePathColumn)
                    } else {
                        null
                    }
                    val folderPath = normalizeFolderPath(rawFolderPath)

                    add(
                        SharedAudioItem(
                            uri = itemUri,
                            displayName = displayName,
                            folderPath = folderPath
                        )
                    )
                }
            }
        }.orEmpty()
    }.getOrDefault(emptyList())
}

private fun normalizeFolderPath(rawPath: String?): String {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val trimmed = rawPath
            ?.replace('\\', '/')
            ?.trim()
            ?.trim('/')
            .orEmpty()
        if (trimmed.isBlank()) return ""
        return trimmed
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
        if (normalized.startsWith(prefix.trim('/'), ignoreCase = true)) {
            return normalized.removePrefix(prefix.trim('/')).trim('/')
        }
    }
    return normalized
}
