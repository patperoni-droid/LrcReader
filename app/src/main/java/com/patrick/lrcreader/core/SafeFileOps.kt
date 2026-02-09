package com.patrick.lrcreader.core

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.File

object SafeFileOps {

    private const val TAG = "SAFE_FILE_OPS"

    // ✅ compat : anciens appels inchangés
    fun rename(context: Context, uriString: String, newDisplayName: String): Boolean {
        return rename(context, uriString, newDisplayName, parentTreeUri = null)
    }

    /**
     * Rename robuste:
     * - file://     -> File.renameTo()
     * - content://  -> DocumentFile.renameTo()
     * - fallback content:// -> copy->delete dans parentTreeUri (si fourni)
     */
    fun rename(
        context: Context,
        uriString: String,
        newDisplayName: String,
        parentTreeUri: Uri? = null
    ): Boolean {
        val cleanName = newDisplayName.trim()
        if (cleanName.isBlank()) {
            Log.w(TAG, "rename aborted: blank target name")
            return false
        }

        val uri = runCatching { Uri.parse(uriString) }.getOrNull()
        if (uri == null) {
            Log.e(TAG, "rename aborted: invalid uri=$uriString")
            return false
        }

        return when (uri.scheme) {
            "file" -> renameFileUri(uri, cleanName)
            "content" -> renameContentUri(context, uri, cleanName, parentTreeUri)
            else -> {
                Log.w(TAG, "rename unsupported scheme=${uri.scheme} uri=$uri")
                false
            }
        }
    }

    // -----------------------------
    // file://
    // -----------------------------
    private fun renameFileUri(uri: Uri, newDisplayName: String): Boolean {
        return try {
            val src = File(uri.path ?: return false)
            if (!src.exists() || !src.isFile) {
                Log.w(TAG, "file rename source missing path=${src.absolutePath}")
                return false
            }

            val parent = src.parentFile ?: return false
            val dst = File(parent, newDisplayName)

            if (src.absolutePath == dst.absolutePath) return true
            if (dst.exists()) {
                Log.w(TAG, "file rename target exists path=${dst.absolutePath}")
                return false
            }

            val ok = src.renameTo(dst)
            Log.i(TAG, "file rename ${if (ok) "ok" else "failed"} ${src.absolutePath} -> ${dst.absolutePath}")
            ok
        } catch (e: Exception) {
            Log.e(TAG, "file rename exception", e)
            false
        }
    }

    // -----------------------------
    // content://
    // -----------------------------
    private fun renameContentUri(
        context: Context,
        uri: Uri,
        newDisplayName: String,
        parentTreeUri: Uri?
    ): Boolean {
        return try {
            val doc = DocumentFile.fromSingleUri(context, uri)
            if (doc == null || !doc.exists() || !doc.isFile) {
                Log.w(TAG, "content rename source invalid uri=$uri")
                return false
            }

            val current = doc.name
            if (!current.isNullOrBlank() && current == newDisplayName) return true

            // 1) tentative direct rename
            val direct = runCatching { doc.renameTo(newDisplayName) }.getOrDefault(false)
            if (direct) {
                Log.i(TAG, "content rename direct ok uri=$uri -> $newDisplayName")
                return true
            }

            // 2) fallback copy->delete (SEULEMENT si on a le vrai dossier parent tree)
            if (parentTreeUri == null) {
                Log.w(TAG, "content rename direct failed and no parentTreeUri; abort uri=$uri")
                return false
            }

            Log.w(TAG, "content rename direct failed, fallback copy->delete into folder=$parentTreeUri")
            renameByCopyDeleteIntoFolder(context, uri, doc, newDisplayName, parentTreeUri)
        } catch (e: Exception) {
            Log.e(TAG, "content rename exception uri=$uri", e)
            false
        }
    }

    private fun renameByCopyDeleteIntoFolder(
        context: Context,
        srcUri: Uri,
        srcDoc: DocumentFile,
        newDisplayName: String,
        folderTreeUri: Uri
    ): Boolean {
        return try {
            val parent = DocumentFile.fromTreeUri(context, folderTreeUri)
            if (parent == null || !parent.isDirectory) {
                Log.w(TAG, "fallback: folderTreeUri not a directory uri=$folderTreeUri")
                return false
            }

            // évite écrasement silencieux
            if (parent.findFile(newDisplayName) != null) {
                Log.w(TAG, "fallback: target already exists name=$newDisplayName")
                return false
            }

            val mime = srcDoc.type ?: "application/octet-stream"
            val target = parent.createFile(mime, newDisplayName) ?: return false

            context.contentResolver.openInputStream(srcUri)?.use { input ->
                context.contentResolver.openOutputStream(target.uri, "w")?.use { output ->
                    input.copyTo(output)
                    output.flush()
                } ?: return false
            } ?: return false

            val deleted = srcDoc.delete()
            if (!deleted) {
                Log.w(TAG, "fallback copy->delete: copy ok but delete failed src=$srcUri")
                return false
            }

            Log.i(TAG, "content rename fallback ok src=$srcUri dst=${target.uri}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "fallback copy->delete exception src=$srcUri", e)
            false
        }
    }
}