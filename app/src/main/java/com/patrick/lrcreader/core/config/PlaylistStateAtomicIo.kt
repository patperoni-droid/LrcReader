package com.patrick.lrcreader.core.config

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.patrick.lrcreader.core.BackupFolderPrefs
import java.io.File

internal object PlaylistStateAtomicIo {

    private const val TAG = "PlaylistStateAtomicIo"
    private const val PERSIST_LOG_TAG = "PLAYLIST_PERSIST"
    private const val CONFIG_DIR_NAME = "Config"
    private const val FILE_NAME = "playlist_state.json"
    private const val FILE_MIME = "application/json"

    fun ensureInitialized(context: Context): Boolean {
        val storage = resolveWriteStorage(context) ?: return false
        return when (storage) {
            is Storage.FileStorage -> ensureFileInitialized(storage)
            is Storage.SafStorage -> ensureSafInitialized(context, storage)
        }
    }

    fun readRaw(context: Context): String? {
        val storage = resolveReadStorage(context) ?: return null

        return when (storage) {
            is Storage.FileStorage -> {
                if (!storage.targetFile.isFile) return null
                runCatching {
                    storage.targetFile.readText(Charsets.UTF_8)
                }.getOrNull()
            }

            is Storage.SafStorage -> {
                val target = findFileIgnoreCase(storage.configDir, FILE_NAME) ?: return null
                runCatching {
                    context.contentResolver.openInputStream(target.uri)
                        ?.bufferedReader(Charsets.UTF_8)
                        ?.use { it.readText() }
                }.onSuccess {
                    Log.d(
                        PERSIST_LOG_TAG,
                        "io.read.success file=$FILE_NAME dir=${storage.configDir.uri} len=${it?.length ?: 0}"
                    )
                }.getOrNull()
            }
        }
    }

    fun writeRawAtomic(context: Context, rawJson: String): Boolean {
        val storage = resolveWriteStorage(context) ?: return false
        if (!ensureInitialized(context)) return false

        return when (storage) {
            is Storage.FileStorage -> writeFileAtomic(storage, rawJson)
            is Storage.SafStorage -> writeSafAtomic(context, storage, rawJson)
        }
    }

    private fun writeFileAtomic(storage: Storage.FileStorage, rawJson: String): Boolean {
        val dir = storage.configDir
        val target = storage.targetFile
        val tmp = File(dir, "$FILE_NAME.tmp")
        val bak = File(dir, "$FILE_NAME.bak")

        return runCatching {
            tmp.writeText(rawJson, Charsets.UTF_8)

            if (bak.exists() && !bak.delete()) {
                Log.w(TAG, "writeFileAtomic: unable to delete stale backup path=${bak.absolutePath}")
            }

            if (target.exists()) {
                if (!target.renameTo(bak)) {
                    tmp.delete()
                    Log.e(TAG, "writeFileAtomic: backup rename failed path=${target.absolutePath}")
                    return false
                }
            }

            val renamed = tmp.renameTo(target)
            if (renamed) {
                if (bak.exists() && !bak.delete()) {
                    Log.w(TAG, "writeFileAtomic: unable to delete backup path=${bak.absolutePath}")
                }
                return true
            }

            Log.e(TAG, "writeFileAtomic: tmp rename failed path=${target.absolutePath}")
            tmp.delete()

            if (bak.exists() && !bak.renameTo(target)) {
                Log.e(TAG, "writeFileAtomic: rollback failed path=${target.absolutePath}")
            }

            false
        }.getOrElse {
            Log.e(TAG, "writeFileAtomic exception", it)
            runCatching { tmp.delete() }
            false
        }
    }

    private fun writeSafAtomic(context: Context, storage: Storage.SafStorage, rawJson: String): Boolean {
        val dir = storage.configDir
        val bytes = rawJson.toByteArray(Charsets.UTF_8)

        return runCatching {
            val target = findFileIgnoreCase(dir, FILE_NAME) ?: dir.createFile(FILE_MIME, FILE_NAME)
            if (target == null) {
                Log.e(TAG, "writeSafAtomic: createFile final failed dir=${dir.uri}")
                Log.e(PERSIST_LOG_TAG, "io.write.createFile.failed dir=${dir.uri}")
                return false
            }
            val ok = writeSafDirect(context, target, bytes)
            if (!ok) {
                Log.d(TAG, "writeSafAtomic: direct write failed dir=${dir.uri} file=$FILE_NAME uri=${target.uri}")
                Log.e(PERSIST_LOG_TAG, "io.write.direct.failed dir=${dir.uri} file=$FILE_NAME uri=${target.uri}")
            } else {
                Log.d(PERSIST_LOG_TAG, "io.write.success dir=${dir.uri} file=$FILE_NAME uri=${target.uri} len=${rawJson.length}")
            }
            ok
        }.getOrElse {
            Log.e(TAG, "writeSafAtomic exception", it)
            Log.e(PERSIST_LOG_TAG, "io.write.exception dir=${dir.uri} file=$FILE_NAME", it)
            false
        }
    }

    private fun writeSafDirect(context: Context, target: DocumentFile, bytes: ByteArray): Boolean {
        return runCatching {
            (
                context.contentResolver.openOutputStream(target.uri, "wt")
                    ?: context.contentResolver.openOutputStream(target.uri, "w")
                )?.use { out ->
                out.write(bytes)
                out.flush()
            } != null
        }.getOrElse {
            Log.e(TAG, "writeSafDirect: write failed uri=${target.uri}", it)
            false
        }
    }

    private fun ensureFileInitialized(storage: Storage.FileStorage): Boolean {
        val dir = storage.configDir
        if (!dir.exists() && !dir.mkdirs()) {
            Log.e(TAG, "ensureFileInitialized: mkdir failed path=${dir.absolutePath}")
            return false
        }

        val target = storage.targetFile
        if (target.exists()) return true

        return runCatching {
            target.writeText(PlaylistState.empty().toJson().toString(2), Charsets.UTF_8)
            true
        }.getOrElse {
            Log.e(TAG, "ensureFileInitialized: create file failed path=${target.absolutePath}", it)
            false
        }
    }

    private fun ensureSafInitialized(context: Context, storage: Storage.SafStorage): Boolean {
        val existing = findFileIgnoreCase(storage.configDir, FILE_NAME)
        if (existing != null && existing.isFile) return true

        val created = storage.configDir.createFile(FILE_MIME, FILE_NAME)
        if (created == null) {
            Log.e(TAG, "ensureSafInitialized: createFile failed dir=${storage.configDir.uri}")
            return false
        }

        val raw = PlaylistState.empty().toJson().toString(2)
        return runCatching {
            context.contentResolver.openOutputStream(created.uri, "w")?.use { out ->
                out.write(raw.toByteArray(Charsets.UTF_8))
                out.flush()
            } != null
        }.getOrElse {
            Log.e(TAG, "ensureSafInitialized: write default failed uri=${created.uri}", it)
            false
        }
    }

    private fun resolveReadStorage(context: Context): Storage? {
        val rootUri = BackupFolderPrefs.getLibraryRootUri(context) ?: return null

        return when (rootUri.scheme) {
            "file" -> {
                val rootPath = rootUri.path ?: return null
                val rootDir = File(rootPath)
                val configDir = File(rootDir, CONFIG_DIR_NAME)
                Storage.FileStorage(
                    configDir = configDir,
                    targetFile = File(configDir, FILE_NAME)
                )
            }

            "content" -> {
                val rootDoc = resolveWorkspaceRootDoc(context, rootUri) ?: return null
                if (!rootDoc.isDirectory) return null

                val configDir = findExistingDirIgnoreCase(rootDoc, CONFIG_DIR_NAME) ?: return null
                Storage.SafStorage(configDir)
            }

            else -> null
        }
    }

    private fun resolveWriteStorage(context: Context): Storage? {
        val rootUri = BackupFolderPrefs.getLibraryRootUri(context) ?: return null

        return when (rootUri.scheme) {
            "file" -> {
                val rootPath = rootUri.path ?: return null
                val rootDir = File(rootPath)
                val configDir = File(rootDir, CONFIG_DIR_NAME)
                Storage.FileStorage(
                    configDir = configDir,
                    targetFile = File(configDir, FILE_NAME)
                )
            }

            "content" -> {
                val rootDoc = resolveWorkspaceRootDoc(context, rootUri) ?: return null
                if (!rootDoc.isDirectory) return null

                val configDir = findOrCreateDirIgnoreCase(rootDoc, CONFIG_DIR_NAME) ?: return null
                Storage.SafStorage(configDir)
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
        val existing = parent.listFiles().firstOrNull {
            it.isDirectory && (it.name ?: "").equals(name, ignoreCase = true)
        }
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
            Log.d(PERSIST_LOG_TAG, "io.root.resolve via=setupTree root=${resolved.uri}")
            return resolved
        }
        val normalizedRootUri = normalizeRootTreeUri(rootUri)
        val direct = DocumentFile.fromTreeUri(context, normalizedRootUri)
            ?: DocumentFile.fromTreeUri(context, rootUri)
            ?: DocumentFile.fromSingleUri(context, rootUri)
        if (direct == null) {
            Log.e(PERSIST_LOG_TAG, "io.root.resolve failed root=$rootUri normalized=$normalizedRootUri")
            return null
        }
        Log.d(PERSIST_LOG_TAG, "io.root.resolve via=storedRoot root=${direct.uri}")
        return direct
    }

    private fun resolveWorkspaceRootFromSetupTree(context: Context): DocumentFile? {
        val setupTreeUri = BackupFolderPrefs.getSetupTreeUri(context) ?: return null
        val baseDoc = DocumentFile.fromTreeUri(context, setupTreeUri) ?: return null
        if (!baseDoc.isDirectory) return null
        if (isWorkspaceRootDirectory(baseDoc)) {
            return baseDoc
        }
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
        val docId = runCatching { DocumentsContract.getDocumentId(rootUri) }.getOrNull() ?: return rootUri
        return runCatching {
            DocumentsContract.buildTreeDocumentUri(authority, docId)
        }.getOrDefault(rootUri)
    }

    private sealed class Storage {
        data class FileStorage(
            val configDir: File,
            val targetFile: File
        ) : Storage()

        data class SafStorage(
            val configDir: DocumentFile
        ) : Storage()
    }
}
