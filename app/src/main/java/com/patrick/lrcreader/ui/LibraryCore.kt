package com.patrick.lrcreader.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.patrick.lrcreader.core.LibraryIndexCache
/* -----------------------------------------------------------
   MOTEUR BIBLIOTHÈQUE : cache + utilitaires fichiers
   ----------------------------------------------------------- */

/** Cache global pour les contenus de dossiers. */
object LibraryFolderCache {
    private val cache = mutableMapOf<String, List<LibraryEntry>>()

    fun get(uri: Uri): List<LibraryEntry>? = cache[uri.toString()]

    fun put(uri: Uri, list: List<LibraryEntry>) {
        cache[uri.toString()] = list
    }

    fun clear() = cache.clear()
}

/** Entrée affichée dans la bibliothèque (fichier ou dossier). */
data class LibraryEntry(
    val uri: Uri,
    val name: String,
    val isDirectory: Boolean
)

/* ------------------ utils principaux ------------------ */

/** Liste les dossiers + fichiers audio + JSON dans un dossier racine. */
fun listEntriesInFolder(context: Context, folderUri: Uri): List<LibraryEntry> {
    val docFile =
        DocumentFile.fromTreeUri(context, folderUri)
            ?: DocumentFile.fromSingleUri(context, folderUri)
            ?: return emptyList()

    val all = docFile.listFiles()

    val folders = all
        .filter { it.isDirectory }
        .sortedBy { it.name?.lowercase() ?: "" }
        .map { LibraryEntry(it.uri, it.name ?: "Dossier", true) }

    val jsonFiles = all
        .filter { it.isFile && it.name?.endsWith(".json", ignoreCase = true) == true }
        .sortedBy { it.name?.lowercase() ?: "" }
        .map { LibraryEntry(it.uri, it.name ?: "sauvegarde.json", false) }

    val audioFiles = all
        .filter { file ->
            file.isFile && file.name?.let { name ->
                name.endsWith(".mp3", true) || name.endsWith(".wav", true)
            } == true
        }
        .sortedBy { it.name?.lowercase() ?: "" }
        .map {
            val cleanName = (it.name ?: "inconnu")
                .replace(".mp3", "", true)
                .replace(".wav", "", true)
            LibraryEntry(it.uri, cleanName, false)
        }

    return folders + jsonFiles + audioFiles
}


/** Supprime toutes les permissions persistantes (quand on “oublie” le dossier). */
fun clearPersistedUris(context: Context) {
    val cr = context.contentResolver
    cr.persistedUriPermissions.forEach { perm ->
        try {
            cr.releasePersistableUriPermission(
                perm.uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: Exception) {}
    }
}

/** Supprime réellement un fichier de la bibliothèque (SAF). */
fun deleteLibraryFile(context: Context, uri: Uri): Boolean {
    return try {
        val doc =
            DocumentFile.fromSingleUri(context, uri)
                ?: DocumentFile.fromTreeUri(context, uri)
                ?: return false
        doc.delete()
    } catch (_: Exception) {
        false
    }
}

/** Copie le fichier vers un autre dossier puis supprime l’original. */
fun moveLibraryFile(
    context: Context,
    sourceUri: Uri,
    destFolderTreeUri: Uri
): Boolean {
    return try {
        val srcDoc = DocumentFile.fromSingleUri(context, sourceUri) ?: return false
        val destDir = DocumentFile.fromTreeUri(context, destFolderTreeUri) ?: return false

        val mime = srcDoc.type ?: "application/octet-stream"
        val name = srcDoc.name ?: "audio"

        val destDoc = destDir.createFile(mime, name) ?: return false

        val cr = context.contentResolver
        cr.openInputStream(srcDoc.uri).use { input ->
            cr.openOutputStream(destDoc.uri).use { output ->
                if (input == null || output == null) return false
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                }
            }
        }

        // on supprime l’original
        srcDoc.delete()
    } catch (_: Exception) {
        false
    }
}

fun scanAllFoldersOnce(context: Context, rootUri: Uri) {
    // scan d'un dossier + récursion dans ses sous-dossiers
    fun scanFolder(folderUri: Uri) {
        val list = listEntriesInFolder(context, folderUri)

        // on met en cache la liste pour CE dossier
        LibraryFolderCache.put(folderUri, list)

        // on descend dans les sous-dossiers
        list.filter { it.isDirectory }.forEach { dir ->
            scanFolder(dir.uri)
        }
    }

    scanFolder(rootUri)
}

/** Scan récursif COMPLET du dossier Music. 1 seule fois. */
fun buildFullIndex(context: Context, rootUri: Uri): List<LibraryIndexCache.CachedEntry> {
    val out = ArrayList<LibraryIndexCache.CachedEntry>()

    val rootDoc = DocumentFile.fromTreeUri(context, rootUri) ?: return emptyList()

    fun recurse(folderDoc: DocumentFile) {
        val children = folderDoc.listFiles()

        children.forEach { child ->
            val name = child.name ?: (if (child.isDirectory) "Dossier" else "Fichier")

            out.add(
                LibraryIndexCache.CachedEntry(
                    uriString = child.uri.toString(),
                    name = name,
                    isDirectory = child.isDirectory,
                    parentUriString = folderDoc.uri.toString()
                )
            )

            if (child.isDirectory) {
                recurse(child)
            }
        }
    }

    recurse(rootDoc)
    return out
}


/**
 * Renomme le fichier en créant un nouveau fichier dans le même dossier
 * puis en supprimant l’original (copie + delete).
 */
fun renameLibraryFile(
    context: Context,
    folderUri: Uri,
    fileUri: Uri,
    newBaseName: String
): Boolean {
    return try {
        val folderDoc = DocumentFile.fromTreeUri(context, folderUri) ?: return false

        // On retrouve le DocumentFile correspondant au fileUri dans ce dossier
        val srcDoc = folderDoc.listFiles().firstOrNull { it.uri == fileUri } ?: return false

        val currentName = srcDoc.name ?: return false
        val ext = currentName.substringAfterLast('.', missingDelimiterValue = "")
        val finalName = if (ext.isNotEmpty()) "$newBaseName.$ext" else newBaseName
        val mime = srcDoc.type ?: "application/octet-stream"

        // On crée le nouveau fichier
        val destDoc = folderDoc.createFile(mime, finalName) ?: return false

        // Copie du contenu
        val cr = context.contentResolver
        cr.openInputStream(srcDoc.uri).use { input ->
            cr.openOutputStream(destDoc.uri).use { output ->
                if (input == null || output == null) return false
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                }
            }
        }

        // Suppression de l’original
        srcDoc.delete()
    } catch (_: Exception) {
        false
    }

}
