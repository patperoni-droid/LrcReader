package com.patrick.lrcreader.core

import android.content.Context
import androidx.documentfile.provider.DocumentFile

object SplFolders {

    private const val SPL_ROOT_NAME = "SPL_Music"

    private fun treeRoot(context: Context): DocumentFile? {
        val treeUri = BackupFolderPrefs.get(context) ?: return null
        return DocumentFile.fromTreeUri(context, treeUri)
    }

    private fun splRoot(context: Context): DocumentFile? {
        val base = treeRoot(context) ?: return null
        // SPL_Music est créé pendant le setup
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
}
