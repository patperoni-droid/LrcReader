package com.patrick.lrcreader.core.config

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.patrick.lrcreader.core.BackupFolderPrefs
import com.patrick.lrcreader.core.PlaylistRepository
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

internal object WorkspacePlaylistFilesStore {

    private const val TAG = "WorkspacePlaylistFiles"
    private const val PERSIST_LOG_TAG = "PLAYLIST_PERSIST"
    private const val PLAYLISTS_DIR_NAME = "Playlists"
    private const val FILE_MIME = "application/json"
    private const val FILE_EXTENSION = ".json"
    private const val SCHEMA_VERSION = 1

    internal data class WorkspacePlaylistFile(
        val name: String,
        val items: List<PlaylistStateItem>,
        val updatedAt: Long = 0L
    )

    internal data class ReadResult(
        val hasPlaylistFiles: Boolean = false,
        val playlists: Map<String, WorkspacePlaylistFile> = emptyMap()
    )

    fun readAll(context: Context): ReadResult {
        val storage = resolveReadStorage(context) ?: return ReadResult()
        return when (storage) {
            is Storage.FileStorage -> readFromFileStorage(storage)
            is Storage.SafStorage -> readFromSafStorage(context, storage)
        }
    }

    fun syncFromRepository(context: Context): Boolean {
        val now = System.currentTimeMillis()
        val playlists = PlaylistRepository.getPlaylists().sorted().associateWith { playlistName ->
            WorkspacePlaylistFile(
                name = playlistName,
                items = PlaylistRepository.getAllItemsRaw(playlistName).map { item ->
                    PlaylistStateItem(
                        uri = item.uri,
                        songId = item.songId?.trim()?.ifBlank { null }
                    )
                },
                updatedAt = now
            )
        }
        return writeAll(context, playlists)
    }

    private fun writeAll(
        context: Context,
        playlists: Map<String, WorkspacePlaylistFile>
    ): Boolean {
        val storage = resolveWriteStorage(
            context = context,
            createIfMissing = playlists.isNotEmpty()
        ) ?: return playlists.isEmpty()

        return when (storage) {
            is Storage.FileStorage -> writeAllToFileStorage(storage, playlists)
            is Storage.SafStorage -> writeAllToSafStorage(context, storage, playlists)
        }
    }

    private fun readFromFileStorage(storage: Storage.FileStorage): ReadResult {
        if (!storage.playlistsDir.isDirectory) return ReadResult()
        val jsonFiles = storage.playlistsDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(FILE_EXTENSION, ignoreCase = true) }
            .orEmpty()
            .sortedBy { it.name.lowercase() }

        if (jsonFiles.isEmpty()) return ReadResult(hasPlaylistFiles = false, playlists = emptyMap())

        val playlists = linkedMapOf<String, WorkspacePlaylistFile>()
        jsonFiles.forEach { file ->
            val parsed = runCatching {
                parsePlaylistFile(file.readText(Charsets.UTF_8))
            }.onFailure {
                Log.e(TAG, "readFromFileStorage: parse failed file=${file.absolutePath}", it)
            }.getOrNull() ?: return@forEach
            playlists[parsed.name] = parsed
        }

        Log.d(
            PERSIST_LOG_TAG,
            "workspace.read.file dir=${storage.playlistsDir.absolutePath} files=${jsonFiles.size} playlists=${playlists.keys.sorted()}"
        )
        return ReadResult(hasPlaylistFiles = jsonFiles.isNotEmpty(), playlists = playlists.toSortedMap())
    }

    private fun readFromSafStorage(
        context: Context,
        storage: Storage.SafStorage
    ): ReadResult {
        val jsonFiles = storage.playlistsDir.listFiles()
            .filter { it.isFile && (it.name ?: "").endsWith(FILE_EXTENSION, ignoreCase = true) }
            .sortedBy { (it.name ?: "").lowercase() }

        if (jsonFiles.isEmpty()) return ReadResult(hasPlaylistFiles = false, playlists = emptyMap())

        val playlists = linkedMapOf<String, WorkspacePlaylistFile>()
        jsonFiles.forEach { file ->
            val parsed = runCatching {
                context.contentResolver.openInputStream(file.uri)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { reader -> parsePlaylistFile(reader.readText()) }
            }.onFailure {
                Log.e(TAG, "readFromSafStorage: parse failed uri=${file.uri}", it)
            }.getOrNull() ?: return@forEach
            playlists[parsed.name] = parsed
        }

        Log.d(
            PERSIST_LOG_TAG,
            "workspace.read.saf dir=${storage.playlistsDir.uri} files=${jsonFiles.size} playlists=${playlists.keys.sorted()}"
        )
        return ReadResult(hasPlaylistFiles = jsonFiles.isNotEmpty(), playlists = playlists.toSortedMap())
    }

    private fun writeAllToFileStorage(
        storage: Storage.FileStorage,
        playlists: Map<String, WorkspacePlaylistFile>
    ): Boolean {
        val dir = storage.playlistsDir
        if (!dir.exists() && !dir.mkdirs()) {
            Log.e(TAG, "writeAllToFileStorage: mkdir failed path=${dir.absolutePath}")
            return false
        }
        if (!dir.isDirectory) {
            Log.e(TAG, "writeAllToFileStorage: not a directory path=${dir.absolutePath}")
            return false
        }

        var success = true
        val desiredByFileName = playlists.values.associateBy { fileNameForPlaylist(it.name) }
        val existingJsonFiles = dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(FILE_EXTENSION, ignoreCase = true) }
            .orEmpty()

        existingJsonFiles.forEach { file ->
            if (file.name !in desiredByFileName && !file.delete()) {
                Log.e(TAG, "writeAllToFileStorage: delete failed path=${file.absolutePath}")
                success = false
            }
        }

        desiredByFileName.forEach { (fileName, playlistFile) ->
            val target = File(dir, fileName)
            if (!writeFileAtomic(target, playlistFile.toJson().toString(2))) {
                success = false
            }
        }

        Log.d(
            PERSIST_LOG_TAG,
            "workspace.write.file dir=${dir.absolutePath} playlists=${playlists.keys.sorted()} success=$success"
        )
        return success
    }

    private fun writeAllToSafStorage(
        context: Context,
        storage: Storage.SafStorage,
        playlists: Map<String, WorkspacePlaylistFile>
    ): Boolean {
        val dir = storage.playlistsDir
        var success = true
        val desiredByFileName = playlists.values.associateBy { fileNameForPlaylist(it.name) }
        val existingJsonFiles = dir.listFiles()
            .filter { it.isFile && (it.name ?: "").endsWith(FILE_EXTENSION, ignoreCase = true) }

        existingJsonFiles.forEach { file ->
            val fileName = file.name ?: return@forEach
            if (fileName !in desiredByFileName) {
                val deleted = runCatching { file.delete() }.getOrDefault(false)
                if (!deleted) {
                    Log.e(TAG, "writeAllToSafStorage: delete failed uri=${file.uri}")
                    success = false
                }
            }
        }

        desiredByFileName.forEach { (fileName, playlistFile) ->
            val target = findFileIgnoreCase(dir, fileName) ?: dir.createFile(FILE_MIME, fileName)
            if (target == null) {
                Log.e(TAG, "writeAllToSafStorage: createFile failed dir=${dir.uri} file=$fileName")
                success = false
                return@forEach
            }
            val bytes = playlistFile.toJson().toString(2).toByteArray(Charsets.UTF_8)
            val written = runCatching {
                (
                    context.contentResolver.openOutputStream(target.uri, "wt")
                        ?: context.contentResolver.openOutputStream(target.uri, "w")
                    )?.use { out ->
                    out.write(bytes)
                    out.flush()
                } != null
            }.getOrElse {
                Log.e(TAG, "writeAllToSafStorage: write failed uri=${target.uri}", it)
                false
            }
            if (!written) success = false
        }

        Log.d(
            PERSIST_LOG_TAG,
            "workspace.write.saf dir=${dir.uri} playlists=${playlists.keys.sorted()} success=$success"
        )
        return success
    }

    private fun writeFileAtomic(target: File, rawJson: String): Boolean {
        val parent = target.parentFile ?: return false
        val tmp = File(parent, "${target.name}.tmp")
        val bak = File(parent, "${target.name}.bak")

        return runCatching {
            tmp.writeText(rawJson, Charsets.UTF_8)
            if (bak.exists() && !bak.delete()) {
                Log.w(TAG, "writeFileAtomic: unable to delete backup path=${bak.absolutePath}")
            }
            if (target.exists() && !target.renameTo(bak)) {
                tmp.delete()
                Log.e(TAG, "writeFileAtomic: backup rename failed path=${target.absolutePath}")
                return false
            }
            if (tmp.renameTo(target)) {
                if (bak.exists() && !bak.delete()) {
                    Log.w(TAG, "writeFileAtomic: unable to delete backup path=${bak.absolutePath}")
                }
                return true
            }
            tmp.delete()
            if (bak.exists() && !bak.renameTo(target)) {
                Log.e(TAG, "writeFileAtomic: rollback failed path=${target.absolutePath}")
            }
            Log.e(TAG, "writeFileAtomic: tmp rename failed path=${target.absolutePath}")
            false
        }.getOrElse {
            Log.e(TAG, "writeFileAtomic exception path=${target.absolutePath}", it)
            runCatching { tmp.delete() }
            false
        }
    }

    private fun parsePlaylistFile(raw: String): WorkspacePlaylistFile? {
        val root = JSONObject(raw)
        val name = root.optString("name", "").trim()
        if (name.isEmpty()) return null
        val updatedAt = root.optLong("updatedAt", 0L)
        return WorkspacePlaylistFile(
            name = name,
            items = parseItemsArray(root.optJSONArray("items")),
            updatedAt = updatedAt
        )
    }

    private fun WorkspacePlaylistFile.toJson(): JSONObject {
        return JSONObject().apply {
            put("schemaVersion", SCHEMA_VERSION)
            put("name", name)
            put("updatedAt", updatedAt)
            put(
                "items",
                JSONArray().apply {
                    items.forEach { item ->
                        put(
                            JSONObject().apply {
                                put("uri", item.uri)
                                put("songId", item.songId ?: JSONObject.NULL)
                            }
                        )
                    }
                }
            )
        }
    }

    private fun parseItemsArray(arr: JSONArray?): List<PlaylistStateItem> {
        if (arr == null) return emptyList()
        val out = mutableListOf<PlaylistStateItem>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val uri = obj.optString("uri", "").trim()
            if (uri.isEmpty()) continue
            out += PlaylistStateItem(
                uri = uri,
                songId = obj.optString("songId", "").trim().ifBlank { null }
            )
        }
        return out
    }

    private fun fileNameForPlaylist(name: String): String {
        val cleanName = name.trim().ifBlank { "playlist" }
        val encoded = URLEncoder.encode(cleanName, StandardCharsets.UTF_8.name())
            .replace("+", "%20")
        val compact = encoded.take(80).trim('_', '.', ' ')
            .ifBlank { "playlist" }
        val digest = sha1Hex(cleanName).take(10)
        return "${compact}_$digest$FILE_EXTENSION"
    }

    private fun sha1Hex(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
        return buildString(bytes.size * 2) {
            bytes.forEach { byte -> append("%02x".format(byte)) }
        }
    }

    private fun resolveReadStorage(context: Context): Storage? {
        val rootUri = BackupFolderPrefs.getLibraryRootUri(context) ?: return null
        return when (rootUri.scheme) {
            "file" -> {
                val rootPath = rootUri.path ?: return null
                val playlistsDir = File(rootPath, PLAYLISTS_DIR_NAME)
                if (!playlistsDir.isDirectory) return null
                Storage.FileStorage(playlistsDir)
            }

            "content" -> {
                val rootDoc = resolveWorkspaceRootDoc(context, rootUri) ?: return null
                val playlistsDir = findExistingDirIgnoreCase(rootDoc, PLAYLISTS_DIR_NAME) ?: return null
                Storage.SafStorage(playlistsDir)
            }

            else -> null
        }
    }

    private fun resolveWriteStorage(
        context: Context,
        createIfMissing: Boolean
    ): Storage? {
        val rootUri = BackupFolderPrefs.getLibraryRootUri(context) ?: return null
        return when (rootUri.scheme) {
            "file" -> {
                val rootPath = rootUri.path ?: return null
                val playlistsDir = File(rootPath, PLAYLISTS_DIR_NAME)
                if (playlistsDir.exists()) {
                    if (!playlistsDir.isDirectory) return null
                    Storage.FileStorage(playlistsDir)
                } else {
                    if (!createIfMissing) return null
                    if (!playlistsDir.mkdirs()) return null
                    Storage.FileStorage(playlistsDir)
                }
            }

            "content" -> {
                val rootDoc = resolveWorkspaceRootDoc(context, rootUri) ?: return null
                val playlistsDir = if (createIfMissing) {
                    findOrCreateDirIgnoreCase(rootDoc, PLAYLISTS_DIR_NAME)
                } else {
                    findExistingDirIgnoreCase(rootDoc, PLAYLISTS_DIR_NAME)
                } ?: return null
                Storage.SafStorage(playlistsDir)
            }

            else -> null
        }
    }

    private fun findExistingDirIgnoreCase(parent: DocumentFile, name: String): DocumentFile? {
        return parent.listFiles().firstOrNull {
            it.isDirectory && (it.name ?: "").equals(name, ignoreCase = true)
        }
    }

    private fun findOrCreateDirIgnoreCase(parent: DocumentFile, name: String): DocumentFile? {
        val existing = findExistingDirIgnoreCase(parent, name)
        if (existing != null) return existing
        return parent.createDirectory(name)
    }

    private fun findFileIgnoreCase(parent: DocumentFile, name: String): DocumentFile? {
        return parent.listFiles().firstOrNull {
            it.isFile && (it.name ?: "").equals(name, ignoreCase = true)
        }
    }

    private fun resolveWorkspaceRootDoc(context: Context, rootUri: Uri): DocumentFile? {
        resolveWorkspaceRootFromSetupTree(context)?.let { resolved ->
            Log.d(PERSIST_LOG_TAG, "workspace.root.resolve via=setupTree root=${resolved.uri}")
            return resolved
        }
        val normalizedRootUri = normalizeRootTreeUri(rootUri)
        val direct = DocumentFile.fromTreeUri(context, normalizedRootUri)
            ?: DocumentFile.fromTreeUri(context, rootUri)
            ?: DocumentFile.fromSingleUri(context, rootUri)
        if (direct == null) {
            Log.e(PERSIST_LOG_TAG, "workspace.root.resolve failed root=$rootUri normalized=$normalizedRootUri")
            return null
        }
        Log.d(PERSIST_LOG_TAG, "workspace.root.resolve via=storedRoot root=${direct.uri}")
        return direct
    }

    private fun resolveWorkspaceRootFromSetupTree(context: Context): DocumentFile? {
        val setupTreeUri = BackupFolderPrefs.getSetupTreeUri(context) ?: return null
        val baseDoc = DocumentFile.fromTreeUri(context, setupTreeUri) ?: return null
        if (!baseDoc.isDirectory) return null
        if (isWorkspaceRootDirectory(baseDoc)) return baseDoc
        return baseDoc.listFiles().firstOrNull { child ->
            child.isDirectory && (child.name ?: "").equals("SPL_Music", ignoreCase = true)
        }
    }

    private fun isWorkspaceRootDirectory(doc: DocumentFile): Boolean {
        val cleanName = doc.name?.trim()
        if (cleanName.equals("SPL_Music", ignoreCase = true)) {
            return true
        }
        return runCatching {
            doc.listFiles().any { child ->
                child.isDirectory && (
                    (child.name ?: "").equals("BackingTracks", ignoreCase = true) ||
                        (child.name ?: "").equals("BackingTrack", ignoreCase = true)
                    )
            }
        }.getOrDefault(false)
    }

    private fun normalizeRootTreeUri(rootUri: Uri): Uri {
        if (rootUri.scheme != "content") return rootUri
        val authority = rootUri.authority ?: return rootUri
        val docId = runCatching { DocumentsContract.getDocumentId(rootUri) }.getOrNull()
            ?: return rootUri
        return runCatching {
            DocumentsContract.buildTreeDocumentUri(authority, docId)
        }.getOrDefault(rootUri)
    }

    private sealed class Storage {
        data class FileStorage(
            val playlistsDir: File
        ) : Storage()

        data class SafStorage(
            val playlistsDir: DocumentFile
        ) : Storage()
    }
}
