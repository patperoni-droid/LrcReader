package com.patrick.lrcreader.ui.library

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.patrick.lrcreader.core.BackupFolderPrefs
import com.patrick.lrcreader.core.BackupFolderPrefsSaf
import com.patrick.lrcreader.core.ImportAudioManager
import com.patrick.lrcreader.core.LibraryIndexCache
import com.patrick.lrcreader.ui.LibraryEntry
import com.patrick.lrcreader.ui.MoveResult

class LibraryBackendSaf(
    private val context: Context
) : LibraryBackend {

    private val tag = "LIB_SAF"

    override fun getRootUri(): Uri? {
        val saved = BackupFolderPrefsSaf.getLibraryRootUri(context)
            ?: BackupFolderPrefs.getLibraryRootUri(context)

        if (saved != null && saved.scheme != "file") return saved

        val setupTree = BackupFolderPrefsSaf.getSetupTreeUri(context)
            ?: BackupFolderPrefs.getSetupTreeUri(context)
            ?: return null

        val baseTree = DocumentFile.fromTreeUri(context, setupTree) ?: return null
        val spl = ensureDirSmart(baseTree, "SPL_Music", aliases = listOf("spl_music")) ?: return null

        BackupFolderPrefsSaf.saveLibraryRootUri(context, spl.uri)
        BackupFolderPrefs.saveLibraryRootUri(context, spl.uri)
        return spl.uri
    }

    override fun ensureBaseFolders() {
        val rootUri = getRootUri() ?: run {
            Log.w(tag, "ensureBaseFolders: rootUri=null")
            return
        }

        val rootDoc = DocumentFile.fromTreeUri(context, rootUri) ?: DocumentFile.fromSingleUri(context, rootUri)
        if (rootDoc == null || !rootDoc.isDirectory) {
            Log.w(tag, "ensureBaseFolders: invalid root uri=$rootUri")
            return
        }

        Log.i(tag, "ensureBaseFolders root=${rootDoc.uri}")

        val backingTracks = ensureDirSmart(rootDoc, "BackingTracks", aliases = listOf("backingtracks", "backingtrack"))
        val backups = ensureDirSmart(rootDoc, "Backups", aliases = listOf("backups"))
        val dj = ensureDirSmart(rootDoc, "DJ", aliases = listOf("dj"))
        val exports = ensureDirSmart(rootDoc, "exports", aliases = listOf("Exports"))
        val imports = ensureDirSmart(rootDoc, "imports", aliases = listOf("Imports"))

        Log.i(tag, "root children=${rootDoc.listFiles().size} names=${rootDoc.listFiles().joinToString { it.name.orEmpty() }}")
        Log.i(tag, "root dirs backingTracks=${backingTracks?.uri} backups=${backups?.uri} dj=${dj?.uri} exports=${exports?.uri} imports=${imports?.uri}")

        if (backingTracks != null && backingTracks.isDirectory) {
            val audio = ensureDirSmart(backingTracks, "Audio", aliases = listOf("audio"))
            val lyrics = ensureDirSmart(backingTracks, "Lyrics", aliases = listOf("lyrics"))
            val midi = ensureDirSmart(backingTracks, "Midi", aliases = listOf("midi"))
            val videos = ensureDirSmart(backingTracks, "Videos", aliases = listOf("videos"))

            Log.i(tag, "BackingTracks children=${backingTracks.listFiles().size} names=${backingTracks.listFiles().joinToString { it.name.orEmpty() }}")
            Log.i(tag, "BackingTracks dirs audio=${audio?.uri} lyrics=${lyrics?.uri} midi=${midi?.uri} videos=${videos?.uri}")
        }

        BackupFolderPrefsSaf.saveLibraryRootUri(context, rootDoc.uri)
        BackupFolderPrefs.saveLibraryRootUri(context, rootDoc.uri)
    }

    override fun chooseInitialFolder(root: Uri, indexAll: List<LibraryIndexCache.CachedEntry>): Uri {
        val rootDoc = DocumentFile.fromTreeUri(context, root) ?: DocumentFile.fromSingleUri(context, root)
            ?: return root

        val backing = findDirIgnoreCase(rootDoc, listOf("BackingTracks", "backingtracks", "backingtrack"))
            ?: return root

        val audio = findDirIgnoreCase(backing, listOf("Audio", "audio"))
        val audioCount = audio?.listFiles()?.size ?: 0

        return if (audio != null && audioCount > 0) audio.uri else backing.uri
    }

    override fun loadIndex(): List<LibraryIndexCache.CachedEntry> {
        return LibraryIndexCache.load(context).orEmpty()
    }

    override fun saveIndex(index: List<LibraryIndexCache.CachedEntry>) {
        LibraryIndexCache.save(context, index)
    }

    override suspend fun scanAll(
        root: Uri,
        folderToShow: Uri,
        onIndexAll: (List<LibraryIndexCache.CachedEntry>) -> Unit,
        onEntries: (List<LibraryEntry>) -> Unit
    ) {
        libraryRescanAll(
            context = context,
            root = root,
            folderToShow = folderToShow,
            onIndexAll = onIndexAll,
            onEntries = onEntries
        )
    }

    override fun listFolder(
        folderUri: Uri,
        indexAll: List<LibraryIndexCache.CachedEntry>,
        djExcludedReason: String
    ): List<LibraryEntry> {
        val folderDoc = DocumentFile.fromTreeUri(context, folderUri)
            ?: DocumentFile.fromSingleUri(context, folderUri)

        Log.i(
            tag,
            "listFolder uri=$folderUri docExists=${folderDoc?.exists()} isDir=${folderDoc?.isDirectory} count=${runCatching { folderDoc?.listFiles()?.size }.getOrNull()}"
        )

        val fromIndex = LibraryIndexCache.childrenOf(indexAll, folderUri).map { e ->
            LibraryEntry(
                uri = Uri.parse(e.uriString),
                name = e.name,
                isDirectory = e.isDirectory
            )
        }.toMutableList()

        if (fromIndex.isEmpty()) {
            val real = folderDoc?.listFiles().orEmpty().mapNotNull { f ->
                val n = f.name ?: return@mapNotNull null
                LibraryEntry(
                    uri = f.uri,
                    name = n,
                    isDirectory = f.isDirectory
                )
            }
            fromIndex.addAll(real)
        }

        val djDoc = folderDoc?.listFiles()
            ?.firstOrNull { it.isDirectory && it.name.equals("DJ", ignoreCase = true) }

        if (djDoc != null) {
            val already = fromIndex.any { it.isDirectory && it.name.equals("DJ", ignoreCase = true) }
            if (!already) {
                fromIndex.add(
                    LibraryEntry(
                        uri = djDoc.uri,
                        name = djDoc.name ?: "DJ",
                        isDirectory = true,
                        disabled = true,
                        disabledReason = djExcludedReason
                    )
                )
            }
        }

        return fromIndex.sortedWith(
            compareByDescending<LibraryEntry> { it.isDirectory }
                .thenBy { it.name.lowercase() }
        )
    }

    override suspend fun importAudio(
        pickedUris: List<Uri>,
        destFolderUri: Uri?,
        currentFolderUri: Uri?
    ): Uri? {
        val root = getRootUri() ?: return null
        val setupTree = BackupFolderPrefsSaf.getSetupTreeUri(context)
            ?: BackupFolderPrefs.getSetupTreeUri(context)
            ?: root

        val rawDest = destFolderUri ?: currentFolderUri ?: root
        val destDoc = DocumentFile.fromTreeUri(context, rawDest) ?: DocumentFile.fromSingleUri(context, rawDest)
        val destFolder = if (destDoc != null && destDoc.isDirectory) rawDest else root

        persistTreePermIfPossible(context, destFolder)

        ImportAudioManager.importAudioFiles(
            context = context,
            appRootTreeUri = setupTree,
            sourceUris = pickedUris,
            destFolderName = "BackingTracks",
            overwriteIfExists = false,
            destFolderUri = destFolder
        )

        return destFolder
    }

    override suspend fun rename(
        folderUri: Uri,
        oldUri: Uri,
        oldName: String,
        newNameFinal: String
    ): Uri? {
        return libraryRenameFileDeviceSafe(
            context = context,
            folderUri = folderUri,
            oldUri = oldUri,
            oldName = oldName,
            newNameFinal = newNameFinal
        )
    }

    override suspend fun move(
        mainHandler: Handler,
        srcUri: Uri,
        destUri: Uri,
        indexAll: List<LibraryIndexCache.CachedEntry>,
        onProgress: (Float?, String?) -> Unit
    ): MoveResult {
        return libraryMoveOneFile(
            context = context,
            mainHandler = mainHandler,
            srcUri = srcUri,
            destUri = destUri,
            indexAll = indexAll,
            onProgress = onProgress
        )
    }

    override suspend fun delete(target: Uri): Boolean {
        return libraryDeleteFile(context, target)
    }

    private fun ensureDirSmart(
        parent: DocumentFile,
        expectedName: String,
        aliases: List<String> = emptyList()
    ): DocumentFile? {
        val existing = findDirIgnoreCase(parent, listOf(expectedName) + aliases)
        if (existing != null) {
            Log.i(tag, "ensureDirSmart hit parent=${parent.uri} name=${existing.name} uri=${existing.uri}")
            return existing
        }

        val created = parent.createDirectory(expectedName)
        Log.i(tag, "ensureDirSmart create parent=${parent.uri} expected=$expectedName createdUri=${created?.uri}")
        return created
    }

    private fun findDirIgnoreCase(parent: DocumentFile, candidates: List<String>): DocumentFile? {
        val wanted = candidates.map { normalizeName(it) }
        return parent.listFiles().firstOrNull { child ->
            child.isDirectory && normalizeName(child.name) in wanted
        }
    }

    private fun normalizeName(name: String?): String {
        return (name ?: "").trim().lowercase().replace(" ", "")
    }
}
