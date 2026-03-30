package com.patrick.lrcreader.smp

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.patrick.lrcreader.core.BackupFolderPrefs
import com.patrick.lrcreader.core.BackupFolderPrefsSaf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class SmpUserArchiveRebuildResult(
    val discoveredArchives: List<Uri>,
    val importedSongs: List<SongUnit>,
    val failedArchives: List<Pair<Uri, String?>>
) {
    val discoveredCount: Int get() = discoveredArchives.size
    val importedCount: Int get() = importedSongs.size
    val failedCount: Int get() = failedArchives.size
    val lastImportedSongId: String? get() = importedSongs.lastOrNull()?.id
}

data class SmpUserArchiveCandidate(
    val archiveUri: Uri,
    val stableSongId: String?
)

data class SmpPartialArchiveSyncPlan(
    val archivesToImport: List<SmpUserArchiveCandidate>,
    val skippedInvalidArchives: List<Uri>,
    val skippedDuplicateSongIds: Set<String>
) {
    val importCount: Int get() = archivesToImport.size
}

class SmpUserArchiveRebuilder(private val context: Context) {

    companion object {
        private const val TAG = "SMP_REBUILD"
        private const val TRACE_TAG = "SMP_TRACE"

        internal fun buildPartialSyncPlan(
            runtimeSongIds: Set<String>,
            candidates: List<SmpUserArchiveCandidate>
        ): SmpPartialArchiveSyncPlan {
            val archivesToImport = mutableListOf<SmpUserArchiveCandidate>()
            val skippedInvalidArchives = mutableListOf<Uri>()
            val skippedDuplicateSongIds = linkedSetOf<String>()
            val selectedSongIds = linkedSetOf<String>()

            candidates.distinctBy { it.archiveUri.toString() }.forEach { candidate ->
                val stableSongId = candidate.stableSongId
                if (stableSongId.isNullOrBlank()) {
                    traceInfo("step=plan_skip_invalid_id uri=${candidate.archiveUri}")
                    skippedInvalidArchives += candidate.archiveUri
                    return@forEach
                }

                if (stableSongId in runtimeSongIds) {
                    traceInfo("step=plan_already_present songId=$stableSongId uri=${candidate.archiveUri}")
                    return@forEach
                }

                if (!selectedSongIds.add(stableSongId)) {
                    traceInfo("step=plan_skip_duplicate songId=$stableSongId uri=${candidate.archiveUri}")
                    skippedDuplicateSongIds += stableSongId
                    return@forEach
                }

                traceInfo("step=plan_missing songId=$stableSongId uri=${candidate.archiveUri}")
                archivesToImport += candidate
            }

            return SmpPartialArchiveSyncPlan(
                archivesToImport = archivesToImport,
                skippedInvalidArchives = skippedInvalidArchives,
                skippedDuplicateSongIds = skippedDuplicateSongIds
            )
        }

        private fun traceInfo(message: String) {
            runCatching { Log.i(TRACE_TAG, message) }
        }

        private fun traceWarn(message: String) {
            runCatching { Log.w(TRACE_TAG, message) }
        }
    }

    private val importer by lazy(LazyThreadSafetyMode.NONE) { SmpImporter(context) }

    suspend fun listUserArchiveUris(): List<Uri> = withContext(Dispatchers.IO) {
        resolveUserArchiveUris()
    }

    suspend fun listUserArchiveCandidates(): List<SmpUserArchiveCandidate> = withContext(Dispatchers.IO) {
        resolveUserArchiveUris().map { archiveUri ->
            val stableSongId = SmpArchiveSongIdResolver.readStableSongId(context, archiveUri)
            traceInfo("step=archive_candidate uri=$archiveUri songId=${stableSongId ?: "invalid_or_absent"}")
            SmpUserArchiveCandidate(
                archiveUri = archiveUri,
                stableSongId = stableSongId
            )
        }
    }

    suspend fun rebuildFromUserArchives(archives: List<Uri>): SmpUserArchiveRebuildResult =
        withContext(Dispatchers.IO) {
            val discovered = archives.distinctBy { it.toString() }
            val importedSongs = mutableListOf<SongUnit>()
            val failedArchives = mutableListOf<Pair<Uri, String?>>()

            discovered.forEach { archiveUri ->
                traceInfo("step=import_attempt uri=$archiveUri")
                val importedSong = importer.importSmp(archiveUri)
                if (importedSong != null) {
                    importedSongs += importedSong
                    traceInfo("step=import_ok uri=$archiveUri songId=${importedSong.id} title=${importedSong.title}")
                    Log.i(
                        TAG,
                        "step=rebuild_import_ok uri=$archiveUri songId=${importedSong.id} title=${importedSong.title}"
                    )
                } else {
                    val reason = importer.lastFailureReason
                    failedArchives += archiveUri to reason
                    traceWarn("step=import_failed uri=$archiveUri reason=${reason ?: "inconnue"}")
                    Log.w(
                        TAG,
                        "step=rebuild_import_failed uri=$archiveUri reason=${reason ?: "inconnue"}"
                    )
                }
            }

            SmpUserArchiveRebuildResult(
                discoveredArchives = discovered,
                importedSongs = importedSongs,
                failedArchives = failedArchives
            )
        }

    private fun resolveUserArchiveUris(): List<Uri> {
        resolveSafSmpDir()?.let { smpDir ->
            val archiveFiles = smpDir.listFiles()
                .orEmpty()
                .filter { it.isFile && SmpWorkspaceArchiveStore.isSupportedArchiveFileName(it.name.orEmpty()) }
                .sortedBy { it.name.orEmpty().lowercase() }
            archiveFiles.forEach { archive ->
                traceInfo("step=archive_discovered backend=SAF name=${archive.name.orEmpty()} uri=${archive.uri}")
            }
            val archives = archiveFiles.map { it.uri }
            Log.i(
                TAG,
                "step=resolve_archives backend=SAF dir=${smpDir.uri} count=${archives.size}"
            )
            return archives
        }

        resolveFileSmpDir()?.let { smpDir ->
            val archiveFiles = smpDir.listFiles()
                .orEmpty()
                .filter { it.isFile && SmpWorkspaceArchiveStore.isSupportedArchiveFileName(it.name) }
                .sortedBy { it.name.lowercase() }
            archiveFiles.forEach { archive ->
                traceInfo("step=archive_discovered backend=file name=${archive.name} uri=${Uri.fromFile(archive)}")
            }
            val archives = archiveFiles.map { Uri.fromFile(it) }
            Log.i(
                TAG,
                "step=resolve_archives backend=file dir=${smpDir.absolutePath} count=${archives.size}"
            )
            return archives
        }

        traceInfo("step=archive_dir_missing")
        Log.i(TAG, "step=resolve_archives backend=none count=0")
        return emptyList()
    }

    private fun resolveSafSmpDir(): DocumentFile? {
        val splRoot = resolveSafSplRootDir() ?: return null
        traceInfo("step=saf_root_resolved rootUri=${splRoot.uri}")
        val backingTracks = findDirIgnoreCase(splRoot, listOf("BackingTracks", "BackingTrack"))
        if (backingTracks == null) {
            traceInfo("step=backingtracks_missing rootUri=${splRoot.uri}")
            return null
        }
        traceInfo("step=backingtracks_found uri=${backingTracks.uri} name=${backingTracks.name.orEmpty()}")
        val smpDir = findDirIgnoreCase(backingTracks, listOf("SMP", "smp"))
        if (smpDir == null) {
            traceInfo("step=smp_dir_missing backingTracksUri=${backingTracks.uri}")
            return null
        }
        traceInfo("step=smp_dir_found backend=SAF uri=${smpDir.uri} name=${smpDir.name.orEmpty()}")
        return smpDir
    }

    private fun resolveSafSplRootDir(): DocumentFile? {
        val candidates = listOf(
            "BackupFolderPrefsSaf.libraryRoot" to BackupFolderPrefsSaf.getLibraryRootUri(context),
            "BackupFolderPrefs.libraryRoot" to BackupFolderPrefs.getLibraryRootUri(context),
            "BackupFolderPrefsSaf.setupTree" to BackupFolderPrefsSaf.getSetupTreeUri(context),
            "BackupFolderPrefs.setupTree" to BackupFolderPrefs.getSetupTreeUri(context)
        )
            .mapNotNull { (source, uri) -> uri?.let { source to it } }

        candidates.forEach { (source, candidateUri) ->
            traceInfo("step=root_candidate source=$source uri=$candidateUri")
            val rootDir = resolveSafDirectory(candidateUri) ?: return@forEach
            traceInfo("step=root_candidate_resolved source=$source uri=$candidateUri resolved=${rootDir.uri}")

            if (findDirIgnoreCase(rootDir, listOf("BackingTracks", "BackingTrack")) != null) {
                traceInfo("step=root_selected source=$source mode=direct_backingtracks uri=${rootDir.uri}")
                return rootDir
            }

            val splMusic = findDirIgnoreCase(rootDir, listOf("SPL_Music", "spl_music"))
            if (splMusic != null) {
                traceInfo("step=root_selected source=$source mode=child_spl_music uri=${splMusic.uri}")
                return splMusic
            }
        }

        traceInfo("step=root_unresolved backend=SAF")
        return null
    }

    private fun resolveSafDirectory(uri: Uri): DocumentFile? {
        val directTree = DocumentFile.fromTreeUri(context, uri)
        if (directTree?.isDirectory == true) {
            traceInfo("step=root_resolver branch=tree uri=$uri resolved=${directTree.uri}")
            return directTree
        }

        val normalizedTree = normalizeAsTreeUri(uri)?.let { treeUri ->
            DocumentFile.fromTreeUri(context, treeUri)
        }
        if (normalizedTree?.isDirectory == true) {
            traceInfo("step=root_resolver branch=normalized_tree uri=$uri resolved=${normalizedTree.uri}")
            return normalizedTree
        }

        val single = DocumentFile.fromSingleUri(context, uri)
        if (single?.isDirectory == true) {
            traceInfo("step=root_resolver branch=single uri=$uri resolved=${single.uri}")
            return single
        }

        traceInfo("step=root_resolver branch=none uri=$uri")
        return null
    }

    private fun resolveFileSmpDir(): File? {
        val rootUri = BackupFolderPrefs.getLibraryRootUri(context)
            ?.takeIf { it.scheme == "file" }
            ?: run {
                traceInfo("step=file_root_missing")
                return null
            }
        val rootDir = File(rootUri.path ?: return null)
        traceInfo("step=file_root_candidate uri=$rootUri path=${rootDir.absolutePath}")
        val splRoot = when {
            File(rootDir, "BackingTracks").isDirectory -> rootDir
            File(rootDir, "SPL_Music").isDirectory -> File(rootDir, "SPL_Music")
            else -> null
        } ?: run {
            traceInfo("step=file_root_unresolved path=${rootDir.absolutePath}")
            return null
        }
        traceInfo("step=file_root_resolved path=${splRoot.absolutePath}")

        val backingTracks = listOf("BackingTracks", "backingtracks", "BackingTrack", "backingtrack")
            .asSequence()
            .map { File(splRoot, it) }
            .firstOrNull { it.isDirectory }
            ?: run {
                traceInfo("step=backingtracks_missing path=${splRoot.absolutePath}")
                return null
            }
        traceInfo("step=backingtracks_found path=${backingTracks.absolutePath}")

        val smpDir = listOf("SMP", "smp")
            .asSequence()
            .map { File(backingTracks, it) }
            .firstOrNull { it.isDirectory }
        if (smpDir == null) {
            traceInfo("step=smp_dir_missing backingTracksPath=${backingTracks.absolutePath}")
            return null
        }
        traceInfo("step=smp_dir_found backend=file path=${smpDir.absolutePath}")
        return smpDir
    }

    private fun findDirIgnoreCase(parent: DocumentFile, candidates: List<String>): DocumentFile? {
        val wanted = candidates.map { it.trim().lowercase() }.toSet()
        return parent.listFiles().firstOrNull { child ->
            child.isDirectory && child.name.orEmpty().trim().lowercase() in wanted
        }
    }

    private fun normalizeAsTreeUri(uri: Uri): Uri? {
        val authority = uri.authority ?: return null
        val treeId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
            ?: runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
            ?: return null
        return DocumentsContract.buildTreeDocumentUri(authority, treeId)
    }
}
