package com.patrick.lrcreader.ui.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.patrick.lrcreader.core.BackupFolderPrefs
import com.patrick.lrcreader.core.LibraryIndexCache
import com.patrick.lrcreader.core.PlaylistRepair
import com.patrick.lrcreader.core.PlaylistRepository
import com.patrick.lrcreader.ui.LibraryEntry
import com.patrick.lrcreader.ui.MoveResult
import com.patrick.lrcreader.ui.asTreeDocumentUri
import com.patrick.lrcreader.ui.buildFullIndex
import com.patrick.lrcreader.ui.deleteLibraryFile
import com.patrick.lrcreader.ui.findUriByNameInFolder
import com.patrick.lrcreader.ui.isAudioOrVideo
import com.patrick.lrcreader.ui.moveLibraryFileWithProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ---------- LOAD / REFRESH ----------

suspend fun libraryLoadInitial(
    context: Context,
    currentFolderUri: Uri?,
    onIndexAll: (List<LibraryIndexCache.CachedEntry>) -> Unit,
    onEntries: (List<LibraryEntry>) -> Unit
) {
    val root = currentFolderUri ?: BackupFolderPrefs.get(context)
    if (root == null) {
        onEntries(emptyList())
        return
    }

    val cachedAll = LibraryIndexCache.load(context)
    if (!cachedAll.isNullOrEmpty()) {
        onIndexAll(cachedAll)
        onEntries(LibraryIndexCache.childrenOf(cachedAll, root).map { e ->
            LibraryEntry(Uri.parse(e.uriString), e.name, e.isDirectory)
        })
    } else {
        onEntries(emptyList())
    }
}

suspend fun libraryRescanAll(
    context: Context,
    root: Uri,
    folderToShow: Uri,
    onIndexAll: (List<LibraryIndexCache.CachedEntry>) -> Unit,
    onEntries: (List<LibraryEntry>) -> Unit
) {
    val newFull = withContext(Dispatchers.IO) { buildFullIndex(context, root) }
    LibraryIndexCache.save(context, newFull)
    onIndexAll(newFull)

    onEntries(
        LibraryIndexCache.childrenOf(newFull, folderToShow).map { e ->
            LibraryEntry(Uri.parse(e.uriString), e.name, e.isDirectory)
        }
    )

    PlaylistRepair.repairDeadUrisFromIndex(context = context, indexAll = newFull)
}

suspend fun libraryRefreshCurrentFolderOnly(
    context: Context,
    folderUri: Uri,
    onEntries: (List<LibraryEntry>) -> Unit
) {
    val newEntries: List<LibraryEntry> = withContext(Dispatchers.IO) {
        val folderDoc = DocumentFile.fromTreeUri(context, folderUri)
            ?: DocumentFile.fromSingleUri(context, folderUri)
            ?: return@withContext emptyList()

        val all = folderDoc.listFiles()

        val folders = all
            .filter { it.isDirectory }
            .sortedBy { it.name?.lowercase() ?: "" }
            .map { LibraryEntry(it.uri, it.name ?: "Dossier", true) }

        val jsonFiles = all
            .filter { it.isFile && it.name?.endsWith(".json", ignoreCase = true) == true }
            .sortedBy { it.name?.lowercase() ?: "" }
            .map { LibraryEntry(it.uri, it.name ?: "sauvegarde.json", false) }

        val mediaFiles = all
            .filter { it.isFile && isAudioOrVideo(it.name) }
            .sortedBy { it.name?.lowercase() ?: "" }
            .map { f -> LibraryEntry(f.uri, f.name ?: "media", false) }

        folders + jsonFiles + mediaFiles
    }

    onEntries(newEntries)
}

// ---------- MOVE ----------

suspend fun libraryMoveOneFile(
    context: Context,
    mainHandler: Handler,
    srcUri: Uri,
    destUri: Uri,
    indexAll: List<LibraryIndexCache.CachedEntry>,
    onProgress: (Float?, String?) -> Unit,
): MoveResult {

    val rootTree = BackupFolderPrefs.get(context)
        ?: return MoveResult(false, null)
    val srcParent = indexAll
        .firstOrNull { it.uriString == srcUri.toString() }
        ?.parentUriString
        ?.let { Uri.parse(it) }
        ?: rootTree

    val srcParentFixed = asTreeDocumentUri(rootTree, srcParent)
    val destFixed = asTreeDocumentUri(rootTree, destUri)

    return withContext(Dispatchers.IO) {
        moveLibraryFileWithProgress(
            context = context,
            sourceUri = srcUri,
            sourceParentTreeUri = srcParentFixed,
            destFolderTreeUri = destFixed
        ) { progress, label ->
            mainHandler.post {
                onProgress(progress, label)
            }
        }
    }
}

suspend fun libraryApplyMoveResult(
    context: Context,
    src: Uri,
    dest: Uri,
    result: MoveResult,
    entries: List<LibraryEntry>,
    indexAll: List<LibraryIndexCache.CachedEntry>,
    onEntries: (List<LibraryEntry>) -> Unit,
    onIndexAll: (List<LibraryIndexCache.CachedEntry>) -> Unit,
    onProgress: (Float?, String?) -> Unit,
    refreshFolderUri: Uri
) {
    if (!result.ok) return

    onProgress(null, "Finalisation…")

    val oldName = entries.firstOrNull { it.uri == src }?.name ?: "Fichier"
    val newUri = result.newUri ?: run {
        // Pas de nouvel URI => on se contente de refresh folder
        libraryRefreshCurrentFolderOnly(context, refreshFolderUri, onEntries)
        return
    }

    // UI : enlever l'ancien (refresh va refaire propre ensuite)
    onEntries(entries.filterNot { it.uri == src })

    // INDEX : enlever ancien + ajouter nouveau
    val srcStr = src.toString()
    val newStr = newUri.toString()
    val destParentStr = dest.toString()

    val newIndex = indexAll
        .filterNot { it.uriString == srcStr } +
            LibraryIndexCache.CachedEntry(
                uriString = newStr,
                name = oldName,
                isDirectory = false,
                parentUriString = destParentStr
            )

    LibraryIndexCache.save(context, newIndex)
    onIndexAll(newIndex)

    libraryRefreshCurrentFolderOnly(context, refreshFolderUri, onEntries)
}

// ---------- DELETE ----------

suspend fun libraryDeleteFile(
    context: Context,
    target: Uri
): Boolean = withContext(Dispatchers.IO) {
    deleteLibraryFile(context, target)
}

// ---------- RENAME (ton code, isolé) ----------

suspend fun libraryRenameFileDeviceSafe(
    context: Context,
    folderUri: Uri,
    oldUri: Uri,
    oldName: String,
    newNameFinal: String
): Uri? = withContext(Dispatchers.IO) {

    val parentDoc = DocumentFile.fromTreeUri(context, folderUri) ?: return@withContext null
    val fileDoc = parentDoc.findFile(oldName) ?: return@withContext null

    val renamedOk = try {
        fileDoc.renameTo(newNameFinal)
    } catch (e: Exception) {
        Log.e("LibraryRename", "renameTo failed: ${e.javaClass.simpleName}: ${e.message}")
        false
    }
    if (!renamedOk) return@withContext null

    parentDoc.findFile(newNameFinal)?.uri
        ?: findUriByNameInFolder(context, folderUri, newNameFinal)
}

fun persistTreePermIfPossible(context: Context, uri: Uri) {
    try {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
    } catch (_: Exception) {}
}

fun libraryLogMove(result: MoveResult) {
    Log.d("MOVE", "ok=${result.ok} newUri=${result.newUri}")
}