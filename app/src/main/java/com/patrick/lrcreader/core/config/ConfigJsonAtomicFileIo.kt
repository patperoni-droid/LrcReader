package com.patrick.lrcreader.core.config

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.patrick.lrcreader.core.BackupFolderPrefs
import java.io.File

internal object ConfigJsonAtomicFileIo {

    private const val CONFIG_DIR_NAME = "Config"
    private const val FILE_MIME = "application/json"
    private const val SESSION_FILE_SINGULAR = "session_state.json"
    private const val SESSION_FILE_PLURAL = "session_states.json"
    @Volatile
    private var lastSafRenameFallbackLogMs = 0L

    fun ensureInitialized(
        context: Context,
        fileName: String,
        defaultRawJson: String,
        tag: String
    ): Boolean {
        val storage = resolveStorage(context) ?: return false
        return when (storage) {
            is Storage.FileStorage -> ensureFileInitialized(storage.configDir, fileName, defaultRawJson, tag)
            is Storage.SafStorage -> ensureSafInitialized(context, storage.configDir, fileName, defaultRawJson, tag)
        }
    }

    fun readRaw(
        context: Context,
        fileName: String,
        tag: String,
        defaultRawJson: String
    ): String? {
        val storage = resolveStorage(context) ?: return null
        if (!ensureInitialized(context, fileName, defaultRawJson, tag)) return null

        return when (storage) {
            is Storage.FileStorage -> runCatching {
                File(storage.configDir, fileName).readText(Charsets.UTF_8)
            }.getOrNull()

            is Storage.SafStorage -> {
                val target = findConfigFileForName(storage.configDir, fileName) ?: return null
                runCatching {
                    context.contentResolver.openInputStream(target.uri)
                        ?.bufferedReader(Charsets.UTF_8)
                        ?.use { it.readText() }
                }.getOrNull()
            }
        }
    }

    fun writeRawAtomic(
        context: Context,
        fileName: String,
        rawJson: String,
        tag: String,
        defaultRawJson: String
    ): Boolean {
        val storage = resolveStorage(context) ?: return false
        if (!ensureInitialized(context, fileName, defaultRawJson, tag)) return false

        return when (storage) {
            is Storage.FileStorage -> writeFileAtomic(storage.configDir, fileName, rawJson, tag)
            is Storage.SafStorage -> writeSafAtomic(context, storage.configDir, fileName, rawJson, tag)
        }
    }

    private fun writeFileAtomic(dir: File, fileName: String, rawJson: String, tag: String): Boolean {
        val target = File(dir, fileName)
        val tmp = File(dir, "$fileName.tmp")
        val bak = File(dir, "$fileName.bak")

        return runCatching {
            tmp.writeText(rawJson, Charsets.UTF_8)

            if (bak.exists() && !bak.delete()) {
                Log.w(tag, "writeFileAtomic: unable to delete stale backup path=${bak.absolutePath}")
            }

            if (target.exists()) {
                if (!target.renameTo(bak)) {
                    tmp.delete()
                    Log.e(tag, "writeFileAtomic: backup rename failed path=${target.absolutePath}")
                    return false
                }
            }

            val renamed = tmp.renameTo(target)
            if (renamed) {
                if (bak.exists() && !bak.delete()) {
                    Log.w(tag, "writeFileAtomic: unable to delete backup path=${bak.absolutePath}")
                }
                return true
            }

            Log.e(tag, "writeFileAtomic: tmp rename failed path=${target.absolutePath}")
            tmp.delete()

            if (bak.exists() && !bak.renameTo(target)) {
                Log.e(tag, "writeFileAtomic: rollback failed path=${target.absolutePath}")
            }

            false
        }.getOrElse {
            Log.e(tag, "writeFileAtomic exception", it)
            runCatching { tmp.delete() }
            false
        }
    }

    private fun writeSafAtomic(
        context: Context,
        dir: DocumentFile,
        fileName: String,
        rawJson: String,
        tag: String
    ): Boolean {
        val bytes = rawJson.toByteArray(Charsets.UTF_8)

        return runCatching {
            // 1) Reuse existing file if present
            val existingTarget = findConfigFileForName(dir, fileName)
            if (existingTarget != null && existingTarget.isFile) {
                val ok = writeSafDirect(context, existingTarget, bytes, tag)
                if (!ok) {
                    Log.d(tag, "writeSafAtomic: direct overwrite failed file=$fileName uri=${existingTarget.uri}")
                }
                return ok
            }

            // 2) Create the FINAL file directly (no tmp, no rename)
            val created = dir.createFile(FILE_MIME, fileName)
            if (created == null) {
                Log.e(tag, "writeSafAtomic: createFile final failed dir=${dir.uri} file=$fileName")
                return false
            }

            // 3) Write directly to final
            val ok = writeSafDirect(context, created, bytes, tag)
            if (!ok) {
                Log.d(tag, "writeSafAtomic: direct write failed file=$fileName uri=${created.uri}")
            }
            ok
        }.getOrElse {
            Log.e(tag, "writeSafAtomic exception file=$fileName", it)
            false
        }
    }

    private fun writeSafDirect(
        context: Context,
        target: DocumentFile,
        bytes: ByteArray,
        tag: String
    ): Boolean {
        return runCatching {
            val wrote = (
                context.contentResolver.openOutputStream(target.uri, "wt")
                    ?: context.contentResolver.openOutputStream(target.uri, "w")
                )?.use { out ->
                out.write(bytes)
                out.flush()
            } != null
            wrote
        }.getOrElse {
            Log.e(tag, "writeSafDirect: write failed uri=${target.uri}", it)
            false
        }
    }

    private fun isSessionStateFamilyFile(fileName: String): Boolean {
        val lower = fileName.lowercase()
        return lower == SESSION_FILE_SINGULAR || lower == SESSION_FILE_PLURAL
    }

    private fun findConfigFileForName(parent: DocumentFile, fileName: String): DocumentFile? {
        val files = runCatching { parent.listFiles().toList() }.getOrDefault(emptyList())
            .filter { it.isFile }
        if (files.isEmpty()) return null

        if (isSessionStateFamilyFile(fileName)) {
            val sessionMatches = files.filter { doc ->
                val lower = (doc.name ?: "").lowercase()
                lower == SESSION_FILE_SINGULAR || lower == SESSION_FILE_PLURAL
            }
            if (sessionMatches.isNotEmpty()) return pickNewestDocument(sessionMatches)
        }

        val exactMatches = files.filter { doc ->
            (doc.name ?: "").equals(fileName, ignoreCase = true)
        }
        return pickNewestDocument(exactMatches)
    }

    private fun pickNewestDocument(candidates: List<DocumentFile>): DocumentFile? {
        if (candidates.isEmpty()) return null
        return candidates.maxWithOrNull(compareBy<DocumentFile>({ safeLastModified(it) }, { it.uri.toString() }))
    }

    private fun safeLastModified(doc: DocumentFile): Long {
        return runCatching { doc.lastModified() }.getOrDefault(0L)
    }

    private fun shouldLogSafRenameFallback(): Boolean {
        val now = SystemClock.elapsedRealtime()
        val last = lastSafRenameFallbackLogMs
        if (now - last < 30_000L) return false
        lastSafRenameFallbackLogMs = now
        return true
    }

    private fun ensureFileInitialized(
        configDir: File,
        fileName: String,
        defaultRawJson: String,
        tag: String
    ): Boolean {
        if (!configDir.exists() && !configDir.mkdirs()) {
            Log.e(tag, "ensureFileInitialized: mkdir failed path=${configDir.absolutePath}")
            return false
        }

        val target = File(configDir, fileName)
        if (target.exists()) return true

        return runCatching {
            target.writeText(defaultRawJson, Charsets.UTF_8)
            true
        }.getOrElse {
            Log.e(tag, "ensureFileInitialized: create file failed path=${target.absolutePath}", it)
            false
        }
    }

    private fun ensureSafInitialized(
        context: Context,
        configDir: DocumentFile,
        fileName: String,
        defaultRawJson: String,
        tag: String
    ): Boolean {
        val existing = findConfigFileForName(configDir, fileName)
        if (existing != null && existing.isFile) return true

        val created = configDir.createFile(FILE_MIME, fileName)
        if (created == null) {
            Log.e(tag, "ensureSafInitialized: createFile failed dir=${configDir.uri}")
            return false
        }

        return runCatching {
            context.contentResolver.openOutputStream(created.uri, "w")?.use { out ->
                out.write(defaultRawJson.toByteArray(Charsets.UTF_8))
                out.flush()
            } != null
        }.getOrElse {
            Log.e(tag, "ensureSafInitialized: write default failed uri=${created.uri}", it)
            false
        }
    }

    private fun resolveStorage(context: Context): Storage? {
        val rootUri = BackupFolderPrefs.getLibraryRootUri(context) ?: return null

        return when (rootUri.scheme) {
            "file" -> {
                val rootPath = rootUri.path ?: return null
                Storage.FileStorage(File(File(rootPath), CONFIG_DIR_NAME))
            }

            "content" -> {
                val normalizedRootUri = normalizeRootTreeUri(rootUri)
                val rootDoc = DocumentFile.fromTreeUri(context, normalizedRootUri)
                    ?: DocumentFile.fromTreeUri(context, rootUri)
                    ?: DocumentFile.fromSingleUri(context, rootUri)
                    ?: return null
                if (!rootDoc.isDirectory) return null

                val configDir = runCatching { findOrCreateDirIgnoreCase(rootDoc, CONFIG_DIR_NAME) }.getOrNull()
                    ?: return null
                Storage.SafStorage(configDir)
            }

            else -> null
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

    private fun normalizeRootTreeUri(rootUri: Uri): Uri {
        if (rootUri.scheme != "content") return rootUri
        val authority = rootUri.authority ?: return rootUri
        val docId = runCatching { DocumentsContract.getDocumentId(rootUri) }.getOrNull() ?: return rootUri
        return runCatching {
            DocumentsContract.buildTreeDocumentUri(authority, docId)
        }.getOrDefault(rootUri)
    }

    private sealed class Storage {
        data class FileStorage(val configDir: File) : Storage()
        data class SafStorage(val configDir: DocumentFile) : Storage()
    }
}
