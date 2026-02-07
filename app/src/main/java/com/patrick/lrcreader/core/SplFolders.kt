package com.patrick.lrcreader.core

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File

object SplFolders {

    private const val SPL_ROOT_NAME = "SPL_Music"

    private fun treeRoot(context: Context): DocumentFile? {
        val treeUri = BackupFolderPrefs.get(context) ?: return null
        return DocumentFile.fromTreeUri(context, treeUri)
    }

    private fun splRoot(context: Context): DocumentFile? {
        val base = treeRoot(context) ?: return null
        return base.findFile(SPL_ROOT_NAME)
    }

    private fun ensureDir(parent: DocumentFile, name: String): DocumentFile? {
        return parent.findFile(name) ?: parent.createDirectory(name)
    }

    fun backupsDir(context: Context): DocumentFile? {
        val root = splRoot(context) ?: return null
        return ensureDir(root, "Backups")
    }

    fun exportsDir(context: Context): DocumentFile? {
        val root = splRoot(context) ?: return null
        return ensureDir(root, "Exports")
    }

    fun importsDir(context: Context): DocumentFile? {
        val root = splRoot(context) ?: return null
        return ensureDir(root, "Imports")
    }

    // -------------------------
    // ✅ MODE INTERNAL (File)
    // -------------------------
    private fun splRootFile(context: Context): File {
        // /storage/emulated/0/Android/data/<package>/files/SPL_Music
        val rootUri: Uri? = BackupFolderPrefs.getLibraryRootUri(context)
        val rootPath = rootUri?.path
        val root = if (rootUri?.scheme == "file" && !rootPath.isNullOrBlank()) {
            File(rootPath)
        } else {
            // fallback "sûr" si jamais : app-private
            File(context.filesDir, SPL_ROOT_NAME)
        }
        if (!root.exists()) root.mkdirs()
        return root
    }

    fun backupsDirFile(context: Context): File {
        val dir = File(splRootFile(context), "Backups")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun exportsDirFile(context: Context): File {
        val dir = File(splRootFile(context), "Exports")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun importsDirFile(context: Context): File {
        val dir = File(splRootFile(context), "Imports")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
}