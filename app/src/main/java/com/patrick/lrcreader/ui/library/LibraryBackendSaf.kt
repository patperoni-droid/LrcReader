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
import com.patrick.lrcreader.exo.BuildConfig
import com.patrick.lrcreader.exo.R
import com.patrick.lrcreader.ui.LibraryEntry
import com.patrick.lrcreader.ui.MoveResult
import com.patrick.lrcreader.ui.isHiddenLibraryTransportFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LibraryBackendSaf(
    private val context: Context
) : LibraryBackend {

    private val tag = "LIB_SAF"

    companion object {
        @Volatile
        private var baseFoldersEnsured = false
    }

    override fun getRootUri(): Uri? {
        val savedSaf = BackupFolderPrefsSaf.getLibraryRootUri(context)
        val savedCompat = BackupFolderPrefs.getLibraryRootUri(context)
        val saved = savedSaf ?: savedCompat

        if (saved != null && saved.scheme != "file") {
            Log.i(
                tag,
                "getRootUri: use_saved savedSaf=$savedSaf savedCompat=$savedCompat resolved=$saved authority=${saved.authority} treeId=${safeTreeDocumentId(saved)} docId=${safeDocumentId(saved)}"
            )
            return saved
        }

        val setupTreeSaf = BackupFolderPrefsSaf.getSetupTreeUri(context)
        val setupTreeCompat = BackupFolderPrefs.getSetupTreeUri(context)
        val setupTree = setupTreeSaf ?: setupTreeCompat ?: return null

        val baseTree = DocumentFile.fromTreeUri(context, setupTree) ?: return null
        val spl = ensureDirSmart(baseTree, "SPL_Music", aliases = listOf("spl_music")) ?: return null

        Log.i(
            tag,
            "getRootUri: resolve_from_setup setupTreeSaf=$setupTreeSaf setupTreeCompat=$setupTreeCompat setupAuthority=${setupTree.authority} setupTreeId=${safeTreeDocumentId(setupTree)} baseTree=${baseTree.uri} baseDocId=${safeDocumentId(baseTree.uri)} spl=${spl.uri} splDocId=${safeDocumentId(spl.uri)} baseChildren=${listChildNames(baseTree)}"
        )

        BackupFolderPrefsSaf.saveLibraryRootUri(context, spl.uri)
        BackupFolderPrefs.saveLibraryRootUri(context, spl.uri)
        return spl.uri
    }

    override fun ensureBaseFolders() {
        if (baseFoldersEnsured) {
            Log.d(tag, "ensureBaseFolders: skip (already ensured)")
            return
        }

        val rootUri = getRootUri() ?: run {
            Log.w(tag, "ensureBaseFolders: rootUri=null")
            return
        }

        val rootDoc = DocumentFile.fromTreeUri(context, rootUri) ?: DocumentFile.fromSingleUri(context, rootUri)
        if (rootDoc == null || !rootDoc.isDirectory) {
            Log.w(tag, "ensureBaseFolders: invalid root uri=$rootUri")
            return
        }

        baseFoldersEnsured = true
        Log.i(
            tag,
            "ensureBaseFolders root=${rootDoc.uri} authority=${rootUri.authority} treeId=${safeTreeDocumentId(rootUri)} rootDocId=${safeDocumentId(rootDoc.uri)} rootChildrenBefore=${listChildNames(rootDoc)}"
        )

        val backingTracks = ensureDirSmart(rootDoc, "BackingTracks", aliases = listOf("backingtracks", "backingtrack"))
        val backups = ensureDirSmart(rootDoc, "Backups", aliases = listOf("backups"))
        val dj = ensureDirSmart(rootDoc, "DJ", aliases = listOf("dj"))

        Log.i(tag, "root dirs backingTracks=${backingTracks?.uri} backups=${backups?.uri} dj=${dj?.uri}")

        if (backingTracks != null && backingTracks.isDirectory) {
            val audio = ensureDirSmart(backingTracks, "Audio", aliases = listOf("audio"))
            val smp = ensureDirSmart(backingTracks, "SMP", aliases = listOf("smp"))
            val lyrics = ensureDirSmart(backingTracks, "Lyrics", aliases = listOf("lyrics"))
            val accords = ensureDirSmart(backingTracks, "Accords", aliases = listOf("accords"))
            val midi = ensureDirSmart(backingTracks, "Midi", aliases = listOf("midi"))
            val videos = ensureDirSmart(backingTracks, "Videos", aliases = listOf("videos"))

            Log.i(
                tag,
                "BackingTracks dirs backingUri=${backingTracks.uri} backingDocId=${safeDocumentId(backingTracks.uri)} audio=${audio?.uri} audioDocId=${audio?.uri?.let(::safeDocumentId)} smp=${smp?.uri} smpDocId=${smp?.uri?.let(::safeDocumentId)} lyrics=${lyrics?.uri} accords=${accords?.uri} midi=${midi?.uri} videos=${videos?.uri} backingChildren=${listChildNames(backingTracks)}"
            )
        }

        if (BuildConfig.DEBUG) {
            Log.d(tag, "ensureBaseFolders: done root=${rootDoc.uri} rootChildrenAfter=${listChildNames(rootDoc)}")
        }

        BackupFolderPrefsSaf.saveLibraryRootUri(context, rootDoc.uri)
        BackupFolderPrefs.saveLibraryRootUri(context, rootDoc.uri)
    }

    override fun chooseInitialFolder(root: Uri, indexAll: List<LibraryIndexCache.CachedEntry>): Uri {
        val setupTree = BackupFolderPrefsSaf.getSetupTreeUri(context)
            ?: BackupFolderPrefs.getSetupTreeUri(context)

        if (isUsableLibraryRoot(root, setupTree)) {
            return root
        }

        if (setupTree != null && isUsableSetupTree(setupTree)) {
            return setupTree
        }

        return root
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
        var latestIndex: List<LibraryIndexCache.CachedEntry> = emptyList()
        val djReason = runCatching {
            context.getString(R.string.library_dj_excluded_reason)
        }.getOrDefault("Exclu de la bibliothèque (utilisé en mode DJ)")

        libraryRescanAll(
            context = context,
            root = root,
            folderToShow = folderToShow,
            onIndexAll = { idx ->
                latestIndex = idx
                onIndexAll(idx)
            },
            onEntries = {
                // DJ reste hors index bibliothèque, donc on reconstruit l'affichage dossier via listFolder.
                onEntries(listFolder(folderToShow, latestIndex, djReason))
            }
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
            "listFolder uri=$folderUri name=${folderDoc?.name} docExists=${folderDoc?.exists()} isDir=${folderDoc?.isDirectory} count=${runCatching { folderDoc?.listFiles()?.size }.getOrNull()} children=${listChildNames(folderDoc)}"
        )

        fun asDjEntry(uri: Uri, name: String): LibraryEntry {
            return LibraryEntry(
                uri = uri,
                name = name,
                isDirectory = true,
                disabled = true,
                disabledReason = djExcludedReason
            )
        }

        val fromIndex = LibraryIndexCache.childrenOf(indexAll, folderUri)
            .asSequence()
            .filterNot { entry -> !entry.isDirectory && isHiddenLibraryTransportFile(entry.name) }
            .map { e ->
            val isDj = e.isDirectory && e.name.equals("DJ", ignoreCase = true)
            if (isDj) {
                asDjEntry(Uri.parse(e.uriString), e.name)
            } else {
                LibraryEntry(
                    uri = Uri.parse(e.uriString),
                    name = e.name,
                    isDirectory = e.isDirectory
                )
            }
        }
            .toMutableList()

        if (fromIndex.isEmpty()) {
            val real = folderDoc?.listFiles().orEmpty().mapNotNull { f ->
                val n = f.name ?: return@mapNotNull null
                if (!f.isDirectory && isHiddenLibraryTransportFile(n)) {
                    return@mapNotNull null
                }
                if (f.isDirectory && n.equals("DJ", ignoreCase = true)) {
                    asDjEntry(f.uri, n)
                } else {
                    LibraryEntry(
                        uri = f.uri,
                        name = n,
                        isDirectory = f.isDirectory
                    )
                }
            }
            fromIndex.addAll(real)
        }

        val djDoc = folderDoc?.listFiles()
            ?.firstOrNull { it.isDirectory && it.name.equals("DJ", ignoreCase = true) }

        val rootUri = getRootUri()
        val isRootFolder = runCatching {
            val folderNorm = normalizeAsTreeUri(folderUri) ?: folderUri
            val rootNorm = rootUri?.let { normalizeAsTreeUri(it) ?: it }
            rootNorm != null && folderNorm == rootNorm
        }.getOrDefault(false)

        val alreadyHasDj = fromIndex.any { it.isDirectory && it.name.equals("DJ", ignoreCase = true) }
        if (djDoc != null) {
            if (!alreadyHasDj) fromIndex.add(asDjEntry(djDoc.uri, djDoc.name ?: "DJ"))
        } else if (isRootFolder && !alreadyHasDj) {
            // DJ reste hors scan bibliothèque pour les perfs, mais doit rester visible au root SPL_Music.
            val placeholderUri = folderUri.buildUpon().appendQueryParameter("dj_placeholder", "1").build()
            fromIndex.add(asDjEntry(placeholderUri, "DJ"))
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
        val destFolder = resolveAudioImportTarget(root = root, requestedDestination = rawDest) ?: root

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

    private fun resolveAudioImportTarget(root: Uri, requestedDestination: Uri): Uri? {
        fun asDoc(uri: Uri): DocumentFile? {
            return DocumentFile.fromTreeUri(context, uri) ?: DocumentFile.fromSingleUri(context, uri)
        }

        val rootDoc = asDoc(root) ?: return requestedDestination
        val requestedDoc = asDoc(requestedDestination)
        val requestedName = requestedDoc?.name.orEmpty()
        val shouldUseDefaultAudioFolder =
            requestedDoc == null ||
                !requestedDoc.isDirectory ||
                requestedDestination.toString() == root.toString() ||
                requestedName.equals("BackingTracks", ignoreCase = true) ||
                requestedName.equals("BackingTrack", ignoreCase = true)

        if (!shouldUseDefaultAudioFolder) {
            return requestedDoc.uri
        }

        val backingTracks = ensureDirSmart(rootDoc, "BackingTracks", aliases = listOf("backingtracks", "backingtrack"))
            ?: return requestedDestination
        val audioDir = ensureDirSmart(backingTracks, "Audio", aliases = listOf("audio"))
            ?: return requestedDestination
        return audioDir.uri
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

    override suspend fun planDelete(
        target: Uri,
        indexAll: List<LibraryIndexCache.CachedEntry>
    ): LibraryDeletePlan = withContext(Dispatchers.Default) {
        LibraryDeletePlanner.buildPlan(context = context, target = target, indexAll = indexAll)
    }

    override suspend fun deleteWithPlan(
        plan: LibraryDeletePlan,
        includeAssociated: Boolean
    ): LibraryDeleteResult = withContext(Dispatchers.IO) {
        val items = LinkedHashMap<String, LibraryDeleteItem>()
        items[plan.target.uri.toString()] = plan.target
        if (includeAssociated) {
            plan.associated.forEach { item ->
                items.putIfAbsent(item.uri.toString(), item)
            }
        }

        val results = items.values.map { item -> deleteSingleDetailed(item) }
        LibraryDeleteResult(results = results)
    }

    override suspend fun delete(target: Uri): Boolean {
        val item = LibraryDeleteItem(
            uri = target,
            role = LibraryDeleteRole.FILE,
            displayName = target.lastPathSegment ?: "file"
        )
        return withContext(Dispatchers.IO) {
            deleteSingleDetailed(item).success
        }
    }

    private fun deleteSingleDetailed(item: LibraryDeleteItem): LibraryDeleteItemResult {
        return try {
            val doc = DocumentFile.fromSingleUri(context, item.uri)
                ?: DocumentFile.fromTreeUri(context, item.uri)
            if (doc == null) {
                Log.e(tag, "delete failed unresolved uri=${item.uri}")
                return LibraryDeleteItemResult(
                    item = item,
                    status = LibraryDeleteStatus.FAILED,
                    detail = "unresolved_uri"
                )
            }

            val existed = runCatching { doc.exists() }.getOrDefault(false)
            if (!existed) {
                return LibraryDeleteItemResult(
                    item = item,
                    status = LibraryDeleteStatus.ALREADY_MISSING,
                    detail = "already_missing"
                )
            }

            val ok = runCatching { doc.delete() }.getOrDefault(false)
            if (ok) {
                return LibraryDeleteItemResult(
                    item = item,
                    status = LibraryDeleteStatus.DELETED
                )
            }

            Log.e(tag, "delete failed uri=${item.uri} role=${item.role} detail=delete_returned_false")
            LibraryDeleteItemResult(
                item = item,
                status = LibraryDeleteStatus.FAILED,
                detail = "delete_returned_false"
            )
        } catch (t: Throwable) {
            Log.e(tag, "delete exception uri=${item.uri} role=${item.role}", t)
            LibraryDeleteItemResult(
                item = item,
                status = LibraryDeleteStatus.FAILED,
                detail = "exception:${t::class.simpleName ?: "Unknown"}"
            )
        }
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

    private fun isReadableDirectory(uri: Uri?): Boolean {
        if (uri == null) return false
        val doc = DocumentFile.fromTreeUri(context, uri) ?: DocumentFile.fromSingleUri(context, uri)
        return doc?.exists() == true && doc.isDirectory
    }

    private fun isUsableLibraryRoot(rootUri: Uri?, setupTree: Uri?): Boolean {
        if (!isReadableDirectory(rootUri)) return false
        val uri = rootUri ?: return false
        return hasReadAccess(uri, setupTree)
    }

    private fun isUsableSetupTree(setupTree: Uri): Boolean {
        if (!isReadableDirectory(setupTree)) return false
        return BackupFolderPrefs.hasValidSetupTreePermission(context)
    }

    private fun hasReadAccess(uri: Uri, setupTree: Uri?): Boolean {
        val targetNorm = normalizeAsTreeUri(uri) ?: uri
        val hasDirectPersisted = context.contentResolver.persistedUriPermissions.any { p ->
            if (!p.isReadPermission) return@any false
            val permNorm = normalizeAsTreeUri(p.uri) ?: p.uri
            permNorm == targetNorm
        }
        if (hasDirectPersisted) return true

        if (!BackupFolderPrefs.hasValidSetupTreePermission(context)) return false
        return isInsideTree(uri, setupTree)
    }

    private fun isInsideTree(candidate: Uri, treeUri: Uri?): Boolean {
        if (treeUri == null) return false
        if (candidate.authority != treeUri.authority) return false

        val treeDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: runCatching { DocumentsContract.getDocumentId(treeUri) }.getOrNull()
            ?: return false

        val candidateDocId = runCatching { DocumentsContract.getTreeDocumentId(candidate) }.getOrNull()
            ?: runCatching { DocumentsContract.getDocumentId(candidate) }.getOrNull()
            ?: return false

        return candidateDocId.equals(treeDocId, ignoreCase = true) ||
                candidateDocId.startsWith("$treeDocId/", ignoreCase = true)
    }

    private fun normalizeAsTreeUri(uri: Uri): Uri? {
        val authority = uri.authority ?: return null
        val treeId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
            ?: runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
            ?: return null
        return DocumentsContract.buildTreeDocumentUri(authority, treeId)
    }

    private fun normalizeName(name: String?): String {
        return (name ?: "").trim().lowercase().replace(" ", "")
    }

    private fun safeTreeDocumentId(uri: Uri?): String? {
        if (uri == null) return null
        return runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
    }

    private fun safeDocumentId(uri: Uri?): String? {
        if (uri == null) return null
        return runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
    }

    private fun listChildNames(parent: DocumentFile?): List<String> {
        return runCatching {
            parent?.listFiles()
                ?.mapNotNull { child -> child.name }
                ?.sorted()
                .orEmpty()
        }.getOrDefault(emptyList())
    }
}
