package com.patrick.lrcreader.smp

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.util.zip.ZipInputStream

class SmpManager(private val context: Context) {
    companion object {
        private const val TAG = "SmpManager"
    }

    fun listFilesInSmp(uri: Uri): List<String> {
        val fileNames = mutableListOf<String>()
        val displayName = resolveDisplayName(uri)

        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zipInputStream ->
                    var entry = zipInputStream.nextEntry

                    while (entry != null) {
                        if (!entry.isDirectory) {
                            fileNames.add(entry.name)
                        }
                        zipInputStream.closeEntry()
                        entry = zipInputStream.nextEntry
                    }
                }
            } ?: throw IllegalStateException("Impossible d'ouvrir le fichier sélectionné")

            if (fileNames.isEmpty()) {
                Log.d(TAG, "SMP lu mais aucune entrée trouvée: name=$displayName uri=$uri")
            } else {
                Log.d(
                    TAG,
                    "SMP contenu: name=$displayName entries=${fileNames.size} files=${fileNames.joinToString()}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur pendant la lecture du SMP: name=$displayName uri=$uri", e)
        }

        return fileNames
    }

    private fun resolveDisplayName(uri: Uri): String {
        var name = uri.lastPathSegment ?: "unknown.smp"
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }
}
