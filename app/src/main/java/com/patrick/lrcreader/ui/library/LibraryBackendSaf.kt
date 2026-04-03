package com.patrick.lrcreader.ui.library

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.SystemClock
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.patrick.lrcreader.core.ImportAudioManager
import com.patrick.lrcreader.core.LibraryIndexCache
import com.patrick.lrcreader.core.StorageModePrefs
import com.patrick.lrcreader.core.WorkspaceResolver
import com.patrick.lrcreader.exo.BuildConfig
import com.patrick.lrcreader.exo.R
import com.patrick.lrcreader.ui.LibraryEntry
import com.patrick.lrcreader.ui.MoveResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

class LibraryBackendSaf(
    private val context: Context,
    private val resolvedWorkspaceSnapshot: WorkspaceResolver.Snapshot? = null
) : LibraryBackend {

    private val tag = "LIB_SAF"
    private val perfTag = "LIB_SAF_PERF_TRACE"

    companion object {
        @Volatile
        private var baseFoldersEnsuredForRoot: String? = null
        private val getRootCallCounter = AtomicInteger(0)
        private val ensureBaseFoldersCallCounter = AtomicInteger(0)
    }

    override fun getRootUri(): Uri? {
        val callId = getRootCallCounter.incrementAndGet()
        val startMs = SystemClock.elapsedRealtime()
        Log.i(perfTag, "step=get_root_start call=$callId timeMs=$startMs")
        fun finish(source: String, result: Uri?, detail: String? = null): Uri? {
            val durationMs = SystemClock.elapsedRealtime() - startMs
            Log.i(
                perfTag,
                "step=get_root_done call=$callId durationMs=$durationMs source=$source result=$result detail=$detail"
            )
            return result
        }

        val snapshot = resolveUsableWorkspaceSnapshot(
            context = context,
            providedSnapshot = resolvedWorkspaceSnapshot,
            expectedMode = StorageModePrefs.Mode.SAF,
            stage = "library_backend_saf:get_root"
        ) ?: return finish(
            source = "workspace_unavailable",
            result = null
        )
        val resolvedRoot = snapshot.workspaceRootUri
        Log.i(
            tag,
            "getRootUri: use_workspace_snapshot status=${snapshot.status} detail=${snapshot.detail} resolved=$resolvedRoot authority=${resolvedRoot?.authority} treeId=${safeTreeDocumentId(resolvedRoot)} docId=${safeDocumentId(resolvedRoot)}"
        )
        return finish(
            source = "workspace_snapshot",
            result = resolvedRoot,
            detail = "status=${snapshot.status}"
        )
    }

    override fun ensureBaseFolders() {
        val callId = ensureBaseFoldersCallCounter.incrementAndGet()
        val startMs = SystemClock.elapsedRealtime()
        Log.i(perfTag, "step=ensure_base_folders_start call=$callId timeMs=$startMs")
        val folders = ensureWorkspaceLibraryFolders(
            context = context,
            providedSnapshot = resolvedWorkspaceSnapshot,
            expectedMode = StorageModePrefs.Mode.SAF,
            stage = "library_backend_saf:ensure_base_folders",
            createLegacyAudioTextDirs = false
        ) ?: run {
            Log.w(tag, "ensureBaseFolders: rootUri=null")
            val durationMs = SystemClock.elapsedRealtime() - startMs
            Log.i(perfTag, "step=ensure_base_folders_end call=$callId durationMs=$durationMs root=null result=no_root")
            return
        }
        val rootUri = folders.rootUri
        val rootKey = rootUri.toString()
        if (baseFoldersEnsuredForRoot == rootKey) {
            Log.d(tag, "ensureBaseFolders: skip (already ensured) root=$rootKey")
            val durationMs = SystemClock.elapsedRealtime() - startMs
            Log.i(
                perfTag,
                "step=ensure_base_folders_end call=$callId durationMs=$durationMs root=$rootKey result=skip_already_ensured"
            )
            return
        }

        val rootDoc = DocumentFile.fromTreeUri(context, rootUri) ?: DocumentFile.fromSingleUri(context, rootUri)
        if (rootDoc == null || !rootDoc.isDirectory) {
            Log.w(tag, "ensureBaseFolders: invalid root uri=$rootUri")
            val durationMs = SystemClock.elapsedRealtime() - startMs
            Log.i(
                perfTag,
                "step=ensure_base_folders_end call=$callId durationMs=$durationMs root=$rootKey result=invalid_root"
            )
            return
        }

        Log.i(
            tag,
            "ensureBaseFolders root=${rootDoc.uri} authority=${rootUri.authority} treeId=${safeTreeDocumentId(rootUri)} rootDocId=${safeDocumentId(rootDoc.uri)} rootChildrenBefore=${listChildNames(rootDoc)}"
        )

        val backingTracks = ensureDirSmart(rootDoc, "BackingTracks", aliases = listOf("backingtracks", "backingtrack"))
        val backups = ensureDirSmart(rootDoc, "Backups", aliases = listOf("backups"))
        val dj = ensureDirSmart(rootDoc, "DJ", aliases = listOf("dj"))

        Log.i(tag, "root dirs backingTracks=${backingTracks?.uri} backups=${backups?.uri} dj=${dj?.uri}")

        if (backingTracks != null && backingTracks.isDirectory) {
            val audio = findDirIgnoreCase(backingTracks, listOf("Audio", "audio"))
            val smp = findDirIgnoreCase(backingTracks, listOf("SMP", "smp"))
            val lyrics = findDirIgnoreCase(backingTracks, listOf("Lyrics", "lyrics"))
            val accords = findDirIgnoreCase(backingTracks, listOf("Accords", "accords"))
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

        baseFoldersEnsuredForRoot = rootKey
        val durationMs = SystemClock.elapsedRealtime() - startMs
        Log.i(
            perfTag,
            "step=ensure_base_folders_end call=$callId durationMs=$durationMs root=$rootKey result=done"
        )
    }

    override fun chooseInitialFolder(root: Uri, indexAll: List<LibraryIndexCache.CachedEntry>): Uri {
        val resolvedRoot = getRootUri() ?: return root
        return if (isUsableLibraryRoot(root, resolvedRoot)) root else resolvedRoot
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

        val realChildren = folderDoc?.let { doc ->
            runCatching { doc.listFiles().toList() }.getOrNull()
        }
        val visibleFromFolder = realChildren
            ?.mapNotNull { f ->
                val n = f.name ?: return@mapNotNull null
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
            ?.toMutableList()

        val visibleEntries = visibleFromFolder ?: fromIndex

        val djDoc = realChildren
            ?.firstOrNull { it.isDirectory && it.name.equals("DJ", ignoreCase = true) }

        val rootUri = getRootUri()
        val isRootFolder = runCatching {
            val folderNorm = normalizeAsTreeUri(folderUri) ?: folderUri
            val rootNorm = rootUri?.let { normalizeAsTreeUri(it) ?: it }
            rootNorm != null && folderNorm == rootNorm
        }.getOrDefault(false)

        val alreadyHasDj = visibleEntries.any { it.isDirectory && it.name.equals("DJ", ignoreCase = true) }
        if (djDoc != null) {
            if (!alreadyHasDj) visibleEntries.add(asDjEntry(djDoc.uri, djDoc.name ?: "DJ"))
        } else if (isRootFolder && !alreadyHasDj) {
            // DJ reste hors scan bibliothèque pour les perfs, mais doit rester visible au root SPL_Music.
            val placeholderUri = folderUri.buildUpon().appendQueryParameter("dj_placeholder", "1").build()
            visibleEntries.add(asDjEntry(placeholderUri, "DJ"))
        }

        return visibleEntries.sortedWith(
            compareByDescending<LibraryEntry> { it.isDirectory }
                .thenBy { it.name.lowercase() }
        )
    }

    override suspend fun importAudio(
        pickedUris: List<Uri>,
        destFolderUri: Uri?,
        currentFolderUri: Uri?
    ): Uri? {
        val folders = ensureWorkspaceLibraryFolders(
            context = context,
            providedSnapshot = resolvedWorkspaceSnapshot,
            expectedMode = StorageModePrefs.Mode.SAF,
            stage = "library_backend_saf:import_audio"
        ) ?: return null
        val root = folders.rootUri
        val rawDest = destFolderUri ?: currentFolderUri ?: root
        val destFolder = resolveAudioImportTarget(
            root = root,
            requestedDestination = rawDest,
            defaultAudioDir = folders.audioUri
        ) ?: folders.audioUri

        ImportAudioManager.importAudioFilesToFolder(
            context = context,
            destFolderUri = destFolder,
            sourceUris = pickedUris,
            overwriteIfExists = false
        )

        return destFolder
    }

    private fun resolveAudioImportTarget(
        root: Uri,
        requestedDestination: Uri,
        defaultAudioDir: Uri
    ): Uri? {
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

        return defaultAudioDir
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

    override suspend fun copyFile(
        mainHandler: Handler,
        srcUri: Uri,
        destUri: Uri,
        indexAll: List<LibraryIndexCache.CachedEntry>,
        onProgress: (Float?, String?) -> Unit
    ): MoveResult {
        return libraryCopyOneFile(
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

    private fun isUsableLibraryRoot(rootUri: Uri?, workspaceRoot: Uri?): Boolean {
        if (!isReadableDirectory(rootUri)) return false
        val uri = rootUri ?: return false
        return isInsideTree(uri, workspaceRoot)
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
