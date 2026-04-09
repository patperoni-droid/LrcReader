package com.patrick.lrcreader.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import java.io.File

object LibraryTransferFolderPrefs {
    private const val PREFS_NAME = "library_transfer_folder_prefs"
    private const val KEY_TREE_URIS = "authorized_tree_uris"
    private const val MAX_RECENT_FOLDERS = 6

    fun rememberAuthorizedFolder(context: Context, uri: Uri): Uri {
        val normalized = normalizeAsTreeUri(uri) ?: uri

        if (normalized.scheme == "content") {
            try {
                context.contentResolver.takePersistableUriPermission(
                    normalized,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {
                // The launcher may already have granted a persisted permission.
            }
        }

        val updated = buildList {
            add(normalized.toString())
            loadRaw(context).forEach { saved ->
                if (!urisMatch(saved, normalized.toString())) {
                    add(saved)
                }
            }
        }.take(MAX_RECENT_FOLDERS)

        saveRaw(context, updated)
        return normalized
    }

    fun getReusableFolders(context: Context): List<Uri> {
        val raw = loadRaw(context)
        if (raw.isEmpty()) return emptyList()

        val reusable = raw.mapNotNull { rawUri ->
            val parsed = runCatching { Uri.parse(rawUri) }.getOrNull() ?: return@mapNotNull null
            val normalized = normalizeAsTreeUri(parsed) ?: parsed
            if (hasReadableAccess(context, normalized) && isUsableDirectory(context, normalized)) {
                normalized
            } else {
                null
            }
        }.distinctBy { it.toString() }

        val reusableStrings = reusable.map(Uri::toString)
        if (reusableStrings != raw) {
            saveRaw(context, reusableStrings)
        }

        return reusable
    }

    private fun loadRaw(context: Context): List<String> {
        val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_TREE_URIS, null)
            ?: return emptyList()

        return try {
            val array = JSONArray(json)
            buildList {
                for (index in 0 until array.length()) {
                    val value = array.optString(index, null)
                    if (!value.isNullOrBlank()) {
                        add(value)
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveRaw(context: Context, values: List<String>) {
        val array = JSONArray()
        values.forEach(array::put)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TREE_URIS, array.toString())
            .apply()
    }

    private fun hasReadableAccess(context: Context, uri: Uri): Boolean {
        if (uri.scheme == "file") {
            val path = uri.path ?: return false
            return File(path).isDirectory
        }
        if (uri.scheme != "content") return false

        val targetAuthority = uri.authority ?: return false
        val targetTreeId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
            ?: runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
            ?: return false

        return context.contentResolver.persistedUriPermissions.any { permission ->
            if (!permission.isReadPermission) return@any false
            if (permission.uri.authority != targetAuthority) return@any false

            val permissionTreeId = runCatching {
                DocumentsContract.getTreeDocumentId(permission.uri)
            }.getOrNull() ?: return@any false

            targetTreeId == permissionTreeId || targetTreeId.startsWith("$permissionTreeId/")
        }
    }

    private fun isUsableDirectory(context: Context, uri: Uri): Boolean {
        if (uri.scheme == "file") {
            val path = uri.path ?: return false
            return File(path).isDirectory
        }

        val tree = DocumentFile.fromTreeUri(context, uri)
        if (tree != null) {
            return runCatching { tree.exists() && tree.isDirectory }.getOrDefault(false)
        }

        val single = DocumentFile.fromSingleUri(context, uri)
        return runCatching { single?.exists() == true && single.isDirectory }.getOrDefault(false)
    }

    private fun normalizeAsTreeUri(uri: Uri): Uri? {
        val authority = uri.authority ?: return null
        val treeId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
            ?: runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
            ?: return null
        return DocumentsContract.buildTreeDocumentUri(authority, treeId)
    }

    private fun urisMatch(left: String, right: String): Boolean {
        if (left == right) return true

        val leftUri = runCatching { Uri.parse(left) }.getOrNull()
        val rightUri = runCatching { Uri.parse(right) }.getOrNull()
        if (leftUri == null || rightUri == null) return false

        val normalizedLeft = normalizeAsTreeUri(leftUri) ?: leftUri
        val normalizedRight = normalizeAsTreeUri(rightUri) ?: rightUri
        return normalizedLeft == normalizedRight
    }
}
