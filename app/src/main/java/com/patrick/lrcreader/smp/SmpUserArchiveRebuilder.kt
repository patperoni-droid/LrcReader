package com.patrick.lrcreader.smp

import android.content.Context
import android.net.Uri
import android.util.Log
import com.patrick.lrcreader.core.WorkspaceResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
        val snapshot = WorkspaceResolver.resolve(context)
        if (!snapshot.isUsable || snapshot.workspaceRootUri == null) {
            traceInfo(
                "step=archive_workspace_unusable status=${snapshot.status} root=${snapshot.workspaceRootUri}"
            )
            Log.i(
                TAG,
                "step=resolve_archives backend=none count=0 workspaceStatus=${snapshot.status}"
            )
            return emptyList()
        }

        return when (
            val smpDir = SmpWorkspaceArchiveStore.resolveWorkspaceSmpDir(
                context = context,
                snapshot = snapshot,
                createIfMissing = false
            )
        ) {
            is SmpWorkspaceArchiveStore.WorkspaceSmpDir.SafDir -> {
                val archiveFiles = smpDir.directory.listFiles()
                    .orEmpty()
                    .filter { it.isFile && SmpWorkspaceArchiveStore.isSupportedArchiveFileName(it.name.orEmpty()) }
                    .sortedBy { it.name.orEmpty().lowercase() }
                archiveFiles.forEach { archive ->
                    traceInfo("step=archive_discovered backend=SAF name=${archive.name.orEmpty()} uri=${archive.uri}")
                }
                val archives = archiveFiles.map { it.uri }
                Log.i(
                    TAG,
                    "step=resolve_archives backend=SAF dir=${smpDir.directory.uri} count=${archives.size}"
                )
                archives
            }

            is SmpWorkspaceArchiveStore.WorkspaceSmpDir.FileDir -> {
                val archiveFiles = smpDir.directory.listFiles()
                    .orEmpty()
                    .filter { it.isFile && SmpWorkspaceArchiveStore.isSupportedArchiveFileName(it.name) }
                    .sortedBy { it.name.lowercase() }
                archiveFiles.forEach { archive ->
                    traceInfo("step=archive_discovered backend=file name=${archive.name} uri=${Uri.fromFile(archive)}")
                }
                val archives = archiveFiles.map { Uri.fromFile(it) }
                Log.i(
                    TAG,
                    "step=resolve_archives backend=file dir=${smpDir.directory.absolutePath} count=${archives.size}"
                )
                archives
            }

            null -> {
                traceInfo(
                    "step=archive_dir_missing workspaceStatus=${snapshot.status} workspaceRoot=${snapshot.workspaceRootUri}"
                )
                Log.i(
                    TAG,
                    "step=resolve_archives backend=none count=0 workspaceStatus=${snapshot.status}"
                )
                emptyList()
            }
        }
    }
}
