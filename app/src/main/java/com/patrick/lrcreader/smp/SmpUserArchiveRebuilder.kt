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

class SmpUserArchiveRebuilder(private val context: Context) {

    companion object {
        private const val TAG = "SMP_REBUILD"
    }

    private val importer by lazy(LazyThreadSafetyMode.NONE) { SmpImporter(context) }

    suspend fun listUserArchiveUris(): List<Uri> = withContext(Dispatchers.IO) {
        resolveUserArchiveUris()
    }

    suspend fun rebuildFromUserArchives(archives: List<Uri>): SmpUserArchiveRebuildResult =
        withContext(Dispatchers.IO) {
            val discovered = archives.distinctBy { it.toString() }
            val importedSongs = mutableListOf<SongUnit>()
            val failedArchives = mutableListOf<Pair<Uri, String?>>()

            discovered.forEach { archiveUri ->
                val importedSong = importer.importSmp(archiveUri)
                if (importedSong != null) {
                    importedSongs += importedSong
                    Log.i(
                        TAG,
                        "step=rebuild_import_ok uri=$archiveUri songId=${importedSong.id} title=${importedSong.title}"
                    )
                } else {
                    val reason = importer.lastFailureReason
                    failedArchives += archiveUri to reason
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
            val archives = smpDir.listFiles()
                .orEmpty()
                .filter { it.isFile && it.name.orEmpty().endsWith(".smp", ignoreCase = true) }
                .sortedBy { it.name.orEmpty().lowercase() }
                .map { it.uri }
            Log.i(
                TAG,
                "step=resolve_archives backend=SAF dir=${smpDir.uri} count=${archives.size}"
            )
            return archives
        }

        resolveFileSmpDir()?.let { smpDir ->
            val archives = smpDir.listFiles()
                .orEmpty()
                .filter { it.isFile && it.name.endsWith(".smp", ignoreCase = true) }
                .sortedBy { it.name.lowercase() }
                .map { Uri.fromFile(it) }
            Log.i(
                TAG,
                "step=resolve_archives backend=file dir=${smpDir.absolutePath} count=${archives.size}"
            )
            return archives
        }

        Log.i(TAG, "step=resolve_archives backend=none count=0")
        return emptyList()
    }

    private fun resolveSafSmpDir(): DocumentFile? {
        val splRoot = resolveSafSplRootDir() ?: return null
        val backingTracks = findDirIgnoreCase(splRoot, listOf("BackingTracks", "BackingTrack")) ?: return null
        return findDirIgnoreCase(backingTracks, listOf("SMP", "smp"))
    }

    private fun resolveSafSplRootDir(): DocumentFile? {
        val candidates = listOfNotNull(
            BackupFolderPrefsSaf.getLibraryRootUri(context),
            BackupFolderPrefs.getLibraryRootUri(context),
            BackupFolderPrefsSaf.getSetupTreeUri(context),
            BackupFolderPrefs.getSetupTreeUri(context)
        )

        candidates.forEach { candidateUri ->
            val rootDir = resolveSafDirectory(candidateUri) ?: return@forEach

            if (findDirIgnoreCase(rootDir, listOf("BackingTracks", "BackingTrack")) != null) {
                return rootDir
            }

            val splMusic = findDirIgnoreCase(rootDir, listOf("SPL_Music", "spl_music"))
            if (splMusic != null) {
                return splMusic
            }
        }

        return null
    }

    private fun resolveSafDirectory(uri: Uri): DocumentFile? {
        val directTree = DocumentFile.fromTreeUri(context, uri)
        if (directTree?.isDirectory == true) return directTree

        val normalizedTree = normalizeAsTreeUri(uri)?.let { treeUri ->
            DocumentFile.fromTreeUri(context, treeUri)
        }
        if (normalizedTree?.isDirectory == true) return normalizedTree

        return null
    }

    private fun resolveFileSmpDir(): File? {
        val rootUri = BackupFolderPrefs.getLibraryRootUri(context)
            ?.takeIf { it.scheme == "file" }
            ?: return null
        val rootDir = File(rootUri.path ?: return null)
        val splRoot = when {
            File(rootDir, "BackingTracks").isDirectory -> rootDir
            File(rootDir, "SPL_Music").isDirectory -> File(rootDir, "SPL_Music")
            else -> null
        } ?: return null

        val backingTracks = listOf("BackingTracks", "backingtracks", "BackingTrack", "backingtrack")
            .asSequence()
            .map { File(splRoot, it) }
            .firstOrNull { it.isDirectory }
            ?: return null

        return listOf("SMP", "smp")
            .asSequence()
            .map { File(backingTracks, it) }
            .firstOrNull { it.isDirectory }
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
