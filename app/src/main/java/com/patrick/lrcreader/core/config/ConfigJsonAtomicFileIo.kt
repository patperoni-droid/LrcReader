package com.patrick.lrcreader.core.config

import android.content.Context
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.patrick.lrcreader.core.BackupFolderPrefs
import java.io.File

internal object ConfigJsonAtomicFileIo {

    private const val CONFIG_DIR_NAME = "Config"
    private const val FILE_MIME = "application/json"

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
                val target = findFileIgnoreCase(storage.configDir, fileName) ?: return null
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
        val tmpPrefix = fileName.substringBeforeLast('.', fileName)

        return runCatching {
            val tmp = dir.createFile(FILE_MIME, "$tmpPrefix.tmp.${System.currentTimeMillis()}")
            if (tmp == null) {
                Log.e(tag, "writeSafAtomic: createFile tmp failed dir=${dir.uri}")
                return false
            }

            val writeOk = runCatching {
                context.contentResolver.openOutputStream(tmp.uri, "w")?.use { out ->
                    out.write(bytes)
                    out.flush()
                } != null
            }.getOrElse {
                Log.e(tag, "writeSafAtomic: write tmp failed uri=${tmp.uri}", it)
                false
            }

            if (!writeOk) {
                runCatching { tmp.delete() }
                return false
            }

            val target = findFileIgnoreCase(dir, fileName)
            var backupRenamed = false
            if (target != null) {
                findFileIgnoreCase(dir, "$fileName.bak")?.delete()
                backupRenamed = runCatching { target.renameTo("$fileName.bak") }.getOrDefault(false)
            }

            val renamed = runCatching { tmp.renameTo(fileName) }.getOrDefault(false)
            if (renamed) {
                if (backupRenamed) {
                    findFileIgnoreCase(dir, "$fileName.bak")?.delete()
                }
                return true
            }

            Log.e(tag, "writeSafAtomic: tmp->rename failed dir=${dir.uri}")
            runCatching { tmp.delete() }

            if (backupRenamed) {
                val bakDoc = findFileIgnoreCase(dir, "$fileName.bak")
                runCatching { bakDoc?.renameTo(fileName) }
            }

            false
        }.getOrElse {
            Log.e(tag, "writeSafAtomic exception", it)
            false
        }
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
        val existing = findFileIgnoreCase(configDir, fileName)
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
                val rootDoc = DocumentFile.fromTreeUri(context, rootUri)
                    ?: DocumentFile.fromSingleUri(context, rootUri)
                    ?: return null
                if (!rootDoc.isDirectory) return null

                val configDir = findOrCreateDirIgnoreCase(rootDoc, CONFIG_DIR_NAME) ?: return null
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

    private sealed class Storage {
        data class FileStorage(val configDir: File) : Storage()
        data class SafStorage(val configDir: DocumentFile) : Storage()
    }
}
