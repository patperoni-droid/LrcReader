package com.patrick.lrcreader.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.patrick.lrcreader.core.LibraryIndexCache
import android.provider.DocumentsContract
import com.patrick.lrcreader.core.BackupFolderPrefs

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
/** Entrée affichée dans la bibliothèque (fichier ou dossier). */
data class LibraryEntry(
    val uri: Uri,
    val name: String,
    val isDirectory: Boolean,
    val disabled: Boolean = false,
    val disabledReason: String? = null
)

/* ------------------ utils principaux ------------------ */

/** ✅ Règle unique : est-ce que ce dossier s'appelle "DJ" ? */
private fun isDjFolderName(name: String?): Boolean {
    return name?.trim()?.equals("DJ", ignoreCase = true) == true
}

/** ✅ Variante DocumentFile (utile quand on est déjà dans la récursion SAF) */
private fun isDjFolderDoc(doc: DocumentFile): Boolean {
    val n = doc.name?.trim().orEmpty()
    if (n.equals("DJ", ignoreCase = true)) return true
    // fallback si le provider encode le nom dans le path
    val p = doc.uri.path.orEmpty()
    return p.contains("/DJ/", ignoreCase = true)
}

/** Liste les dossiers + fichiers audio + JSON dans un dossier racine. */
/** Liste les dossiers + fichiers audio + JSON dans un dossier racine. */
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
        .map {
            val isDj = isDjFolderName(it.name)
            LibraryEntry(
                uri = it.uri,
                name = it.name ?: "Dossier",
                isDirectory = true,
                disabled = isDj,
                disabledReason = if (isDj) "Exclu de la bibliothèque (utilisé en mode DJ)" else null
            )
        }

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

data class MoveResult(
    val ok: Boolean,
    val newUri: Uri? = null
)

fun moveLibraryFile(
    context: Context,
    sourceUri: Uri,
    sourceParentTreeUri: Uri,
    destFolderTreeUri: Uri
): MoveResult {
    val cr = context.contentResolver

    // ✅ MOVE NATIF (instant) si possible
    if (android.os.Build.VERSION.SDK_INT >= 24) {
        try {
            val rootTree = BackupFolderPrefs.get(context)

            if (rootTree != null) {
                val srcDocId = DocumentsContract.getDocumentId(sourceUri)

                val srcParentDocId = try {
                    DocumentsContract.getDocumentId(sourceParentTreeUri)
                } catch (_: Exception) {
                    DocumentsContract.getTreeDocumentId(sourceParentTreeUri)
                }

                val destDocId = try {
                    DocumentsContract.getDocumentId(destFolderTreeUri)
                } catch (_: Exception) {
                    DocumentsContract.getTreeDocumentId(destFolderTreeUri)
                }

                val srcDocUri = DocumentsContract.buildDocumentUriUsingTree(rootTree, srcDocId)
                val srcParentDocUri = DocumentsContract.buildDocumentUriUsingTree(rootTree, srcParentDocId)
                val destFolderDocUri = DocumentsContract.buildDocumentUriUsingTree(rootTree, destDocId)

                val movedUri = DocumentsContract.moveDocument(
                    cr,
                    srcDocUri,
                    srcParentDocUri,
                    destFolderDocUri
                )

                if (movedUri != null) {
                    android.util.Log.d("MOVE", "native move OK newUri=$movedUri")
                    return MoveResult(ok = true, newUri = movedUri)
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("MOVE", "native move failed -> fallback copy", e)
        }
    }

    // 🔁 FALLBACK : copie + delete (lent)
    return try {
        val srcDoc = DocumentFile.fromSingleUri(context, sourceUri) ?: return MoveResult(false)
        val destDir = DocumentFile.fromTreeUri(context, destFolderTreeUri) ?: return MoveResult(false)

        val mime = srcDoc.type ?: "application/octet-stream"
        val name = srcDoc.name ?: "file"
        val destFile = destDir.createFile(mime, name) ?: return MoveResult(false)

        cr.openInputStream(srcDoc.uri).use { input ->
            cr.openOutputStream(destFile.uri, "w").use { output ->
                if (input == null || output == null) return MoveResult(false)
                val buffer = ByteArray(256 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                }
                output.flush()
            }
        }

        val delOk = srcDoc.delete()
        MoveResult(ok = delOk, newUri = if (delOk) destFile.uri else null)

    } catch (e: Exception) {
        android.util.Log.e("MOVE", "moveLibraryFile exception", e)
        MoveResult(false)
    }
}

fun scanAllFoldersOnce(context: Context, rootUri: Uri) {
    // scan d'un dossier + récursion dans ses sous-dossiers
    fun scanFolder(folderUri: Uri) {
        val list = listEntriesInFolder(context, folderUri)

        // on met en cache la liste pour CE dossier
        LibraryFolderCache.put(folderUri, list)

        // 🔒 on descend dans les sous-dossiers, SAUF "DJ"
        list.filter { it.isDirectory && !isDjFolderName(it.name) }
            .forEach { dir ->
                scanFolder(dir.uri)
            }
    }

    scanFolder(rootUri)
}

private fun isAudioOrVideo(context: Context, uri: Uri, name: String): Boolean {
    // 1) MIME (le plus fiable via SAF)
    val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
    if (mime != null) return mime.startsWith("audio/") || mime.startsWith("video/")

    // 2) Fallback extension si getType == null
    val lower = name.lowercase()
    return lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".flac") ||
            lower.endsWith(".m4a") || lower.endsWith(".aac") || lower.endsWith(".ogg") ||
            lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".mov") ||
            lower.endsWith(".webm") || lower.endsWith(".avi")
}

/**
 * ✅ FICHIERS SPL AUTORISÉS DANS LA BIBLIOTHÈQUE
 * (médias + paroles + backups + exports/imports)
 */
private fun isSplIndexableFile(
    context: Context,
    uri: Uri,
    name: String
): Boolean {
    val lower = name.lowercase()

    // 1) médias
    if (isAudioOrVideo(context, uri, name)) return true

    // 2) fichiers SPL utiles
    return lower.endsWith(".lrc") ||
            lower.endsWith(".json") ||
            lower.endsWith(".zip") ||
            lower.endsWith(".lp-settings") ||
            lower.endsWith(".lp-backup")
}

/** Scan récursif COMPLET du dossier Music. 1 seule fois. */
fun buildFullIndex(context: Context, rootUri: Uri): List<LibraryIndexCache.CachedEntry> {
    val out = ArrayList<LibraryIndexCache.CachedEntry>()
    val rootDoc = DocumentFile.fromTreeUri(context, rootUri)
        ?: DocumentFile.fromSingleUri(context, rootUri)
        ?: return emptyList()
    fun recurse(folderDoc: DocumentFile, parentKey: String) {
        // 🔒 si ce dossier est DJ -> on coupe ici (pas indexé, pas récursé)
        if (folderDoc.isDirectory && isDjFolderDoc(folderDoc)) return

        folderDoc.listFiles().forEach { child ->
            val name = child.name ?: (if (child.isDirectory) "Dossier" else "Fichier")

            // 🔒 si c'est un dossier DJ -> on ne l'indexe pas et on ne descend pas dedans
            if (child.isDirectory && isDjFolderDoc(child)) return@forEach

            // ✅ filtre fichiers : on garde dossiers + fichiers SPL indexables
            if (!child.isDirectory) {
                if (!isSplIndexableFile(context, child.uri, name)) return@forEach
            }

            out.add(
                LibraryIndexCache.CachedEntry(
                    uriString = child.uri.toString(),
                    name = name,
                    isDirectory = child.isDirectory,
                    parentUriString = parentKey
                )
            )

            if (child.isDirectory) {
                recurse(child, child.uri.toString())
            }
        }
    }

    recurse(rootDoc, rootUri.toString())
    return out
}
// ------------------------------------------------------------
// ✅ SCAN DJ (sur demande) → index séparé (DJ seulement)
// ------------------------------------------------------------
fun buildDjFullIndex(
    context: Context,
    djRootUri: Uri
): List<com.patrick.lrcreader.core.DjIndexCache.Entry> {

    val out = ArrayList<com.patrick.lrcreader.core.DjIndexCache.Entry>()
    val rootDoc = DocumentFile.fromTreeUri(context, djRootUri) ?: return emptyList()

    fun recurse(folderDoc: DocumentFile, parentKey: String) {
        folderDoc.listFiles().forEach { child ->
            val name = child.name ?: (if (child.isDirectory) "Dossier" else "Fichier")

            // ✅ DJ: on garde uniquement dossiers + médias
            if (!child.isDirectory) {
                if (!isAudioOrVideo(context, child.uri, name)) return@forEach
            }

            out.add(
                com.patrick.lrcreader.core.DjIndexCache.Entry(
                    uriString = child.uri.toString(),
                    name = name,
                    isDirectory = child.isDirectory,
                    parentUriString = parentKey
                )
            )

            if (child.isDirectory) {
                recurse(child, child.uri.toString())
            }
        }
    }

    recurse(rootDoc, djRootUri.toString())
    return out
}

fun moveLibraryFileWithProgress(
    context: Context,
    sourceUri: Uri,
    sourceParentTreeUri: Uri,
    destFolderTreeUri: Uri,
    onProgress: (progress01: Float?, label: String) -> Unit
): MoveResult {
    val cr = context.contentResolver

    // ✅ MOVE NATIF (quasi instant)
    if (android.os.Build.VERSION.SDK_INT >= 24) {
        try {
            onProgress(null, "Déplacement…")
            val rootTree = BackupFolderPrefs.get(context)

            if (rootTree != null) {
                val srcDocId = DocumentsContract.getDocumentId(sourceUri)

                val srcParentDocId = try {
                    DocumentsContract.getDocumentId(sourceParentTreeUri)
                } catch (_: Exception) {
                    DocumentsContract.getTreeDocumentId(sourceParentTreeUri)
                }

                val destDocId = try {
                    DocumentsContract.getDocumentId(destFolderTreeUri)
                } catch (_: Exception) {
                    DocumentsContract.getTreeDocumentId(destFolderTreeUri)
                }

                val srcDocUri = DocumentsContract.buildDocumentUriUsingTree(rootTree, srcDocId)
                val srcParentDocUri = DocumentsContract.buildDocumentUriUsingTree(rootTree, srcParentDocId)
                val destFolderDocUri = DocumentsContract.buildDocumentUriUsingTree(rootTree, destDocId)

                val movedUri = DocumentsContract.moveDocument(
                    cr,
                    srcDocUri,
                    srcParentDocUri,
                    destFolderDocUri
                )

                if (movedUri != null) {
                    android.util.Log.d("MOVE", "native move OK newUri=$movedUri")
                    return MoveResult(ok = true, newUri = movedUri)
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("MOVE", "native move failed -> fallback copy", e)
        }
    }

    // 🔁 FALLBACK : copie + delete (lent) → progress réel
    return try {
        val srcDoc = DocumentFile.fromSingleUri(context, sourceUri) ?: return MoveResult(false)
        val destDir = DocumentFile.fromTreeUri(context, destFolderTreeUri) ?: return MoveResult(false)

        val total = runCatching { srcDoc.length() }.getOrNull()?.coerceAtLeast(0L) ?: 0L
        var copied = 0L

        val mime = srcDoc.type ?: "application/octet-stream"
        val name = srcDoc.name ?: "file"
        val destFile = destDir.createFile(mime, name) ?: return MoveResult(false)

        onProgress(0f, "Copie…")

        var lastEmitAt = 0L
        var lastPct = -1

        cr.openInputStream(srcDoc.uri).use { input ->
            cr.openOutputStream(destFile.uri, "w").use { output ->
                if (input == null || output == null) return MoveResult(false)
                val buffer = ByteArray(256 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    copied += read.toLong()

                    if (total > 0L) {
                        val p = (copied.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
                        val pct = (p * 100f).toInt()
                        val now = android.os.SystemClock.uptimeMillis()
                        if (pct != lastPct && (now - lastEmitAt) > 40) {
                            lastEmitAt = now
                            lastPct = pct
                            onProgress(p, "Copie…")
                        }
                    } else {
                        val now = android.os.SystemClock.uptimeMillis()
                        if ((now - lastEmitAt) > 200) {
                            lastEmitAt = now
                            onProgress(null, "Copie…")
                        }
                    }
                }
                output.flush()
            }
        }

        onProgress(1f, "Finalisation…")
        val delOk = srcDoc.delete()
        MoveResult(ok = delOk, newUri = if (delOk) destFile.uri else null)

    } catch (e: Exception) {
        android.util.Log.e("MOVE", "moveLibraryFileWithProgress exception", e)
        MoveResult(false)
    }
}


/**
 * Renomme le fichier en créant un nouveau fichier dans le même dossier
 * puis en supprimant l’original (copie + delete).
 */
fun renameLibraryFile(
    context: Context,
    folderUri: Uri,      // tu peux le garder, on ne s'en sert pas
    fileUri: Uri,
    newBaseName: String
): Boolean {
    return try {
        val doc = DocumentFile.fromSingleUri(context, fileUri) ?: return false
        val currentName = doc.name ?: return false

        val ext = currentName.substringAfterLast('.', missingDelimiterValue = "")
        val finalName = if (ext.isNotEmpty()) "$newBaseName.$ext" else newBaseName

        val newUri = DocumentsContract.renameDocument(
            context.contentResolver,
            fileUri,
            finalName
        ) ?: return false

        true
    } catch (e: Exception) {
        android.util.Log.e("LibraryRename", "rename exception", e)
        false
    }
}