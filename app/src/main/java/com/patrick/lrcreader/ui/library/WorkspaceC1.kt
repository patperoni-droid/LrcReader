package com.patrick.lrcreader.ui.library

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.patrick.lrcreader.core.BackupFolderPrefs
import com.patrick.lrcreader.core.BackupFolderPrefsInternal
import com.patrick.lrcreader.core.BackupFolderPrefsSaf
import com.patrick.lrcreader.core.DjFolderPrefs
import com.patrick.lrcreader.core.InternalStoragePaths
import com.patrick.lrcreader.core.StorageModePrefs
import com.patrick.lrcreader.core.WorkspaceResolver
import java.io.File

private const val WORKSPACE_C1_TAG = "WORKSPACE_C1"

internal data class WorkspaceLibraryFolders(
    val snapshot: WorkspaceResolver.Snapshot,
    val rootUri: Uri,
    val backingTracksUri: Uri,
    val audioUri: Uri,
    val smpUri: Uri,
    val djUri: Uri,
    val backupsUri: Uri
)

internal fun resolveUsableWorkspaceSnapshot(
    context: Context,
    providedSnapshot: WorkspaceResolver.Snapshot? = null,
    expectedMode: StorageModePrefs.Mode? = null,
    stage: String
): WorkspaceResolver.Snapshot? {
    val snapshot = providedSnapshot ?: WorkspaceResolver.resolve(context)
    val rootUri = snapshot.workspaceRootUri
    if (expectedMode != null && snapshot.mode != expectedMode) {
        Log.e(
            WORKSPACE_C1_TAG,
            "stage=$stage error=mode_mismatch expected=$expectedMode actual=${snapshot.mode} status=${snapshot.status} root=$rootUri detail=${snapshot.detail}"
        )
        return null
    }
    if (!snapshot.isUsable || rootUri == null) {
        Log.e(
            WORKSPACE_C1_TAG,
            "stage=$stage error=workspace_unavailable expected=$expectedMode actual=${snapshot.mode} status=${snapshot.status} root=$rootUri detail=${snapshot.detail}"
        )
        return null
    }
    Log.i(
        WORKSPACE_C1_TAG,
        "stage=$stage mode=${snapshot.mode} status=${snapshot.status} root=$rootUri detail=${snapshot.detail}"
    )
    return snapshot
}

internal fun ensureWorkspaceLibraryFolders(
    context: Context,
    providedSnapshot: WorkspaceResolver.Snapshot? = null,
    expectedMode: StorageModePrefs.Mode? = null,
    stage: String
): WorkspaceLibraryFolders? {
    val snapshot = resolveUsableWorkspaceSnapshot(
        context = context,
        providedSnapshot = providedSnapshot,
        expectedMode = expectedMode,
        stage = "$stage:resolve"
    ) ?: return null
    val rootUri = snapshot.workspaceRootUri ?: return null

    return when (snapshot.mode) {
        StorageModePrefs.Mode.INTERNAL -> {
            val rootPath = rootUri.path
            if (rootPath.isNullOrBlank()) {
                Log.e(
                    WORKSPACE_C1_TAG,
                    "stage=$stage error=internal_root_path_missing root=$rootUri"
                )
                null
            } else {
                val rootDir = File(rootPath)
                if (!rootDir.exists() && !rootDir.mkdirs()) {
                    Log.e(
                        WORKSPACE_C1_TAG,
                        "stage=$stage error=internal_root_create_failed root=${rootDir.absolutePath}"
                    )
                    return null
                }

                val backingTracksDir = ensureFileDir(File(rootDir, "BackingTracks"))
                val folders = WorkspaceLibraryFolders(
                    snapshot = snapshot,
                    rootUri = rootUri,
                    backingTracksUri = Uri.fromFile(backingTracksDir),
                    audioUri = Uri.fromFile(ensureFileDir(File(backingTracksDir, "Audio"))),
                    smpUri = Uri.fromFile(ensureFileDir(File(backingTracksDir, "SMP"))),
                    djUri = Uri.fromFile(ensureFileDir(File(rootDir, "DJ"))),
                    backupsUri = Uri.fromFile(ensureFileDir(File(rootDir, "Backups")))
                )
                ensureFileDir(File(backingTracksDir, "Lyrics"))
                ensureFileDir(File(backingTracksDir, "Accords"))
                ensureFileDir(File(backingTracksDir, "Midi"))
                ensureFileDir(File(backingTracksDir, "Videos"))
                Log.i(
                    WORKSPACE_C1_TAG,
                    "stage=$stage mode=INTERNAL root=${folders.rootUri} backing=${folders.backingTracksUri} audio=${folders.audioUri} smp=${folders.smpUri} dj=${folders.djUri} backups=${folders.backupsUri}"
                )
                folders
            }
        }

        StorageModePrefs.Mode.SAF -> {
            val rootDoc = DocumentFile.fromTreeUri(context, rootUri)
                ?: DocumentFile.fromSingleUri(context, rootUri)
            if (rootDoc == null || !rootDoc.isDirectory) {
                Log.e(
                    WORKSPACE_C1_TAG,
                    "stage=$stage error=saf_root_unresolved root=$rootUri"
                )
                return null
            }
            val backingTracks = ensureDocumentDir(rootDoc, "BackingTracks", aliases = listOf("backingtracks", "backingtrack"))
            val audio = backingTracks?.let { ensureDocumentDir(it, "Audio", aliases = listOf("audio")) }
            val smp = backingTracks?.let { ensureDocumentDir(it, "SMP", aliases = listOf("smp")) }
            val lyrics = backingTracks?.let { ensureDocumentDir(it, "Lyrics", aliases = listOf("lyrics")) }
            val accords = backingTracks?.let { ensureDocumentDir(it, "Accords", aliases = listOf("accords")) }
            val midi = backingTracks?.let { ensureDocumentDir(it, "Midi", aliases = listOf("midi")) }
            val videos = backingTracks?.let { ensureDocumentDir(it, "Videos", aliases = listOf("videos")) }
            val dj = ensureDocumentDir(rootDoc, "DJ", aliases = listOf("dj"))
            val backups = ensureDocumentDir(rootDoc, "Backups", aliases = listOf("backups"))

            if (backingTracks == null || audio == null || smp == null || dj == null || backups == null) {
                Log.e(
                    WORKSPACE_C1_TAG,
                    "stage=$stage error=saf_folder_resolution_failed root=$rootUri backing=${backingTracks?.uri} audio=${audio?.uri} smp=${smp?.uri} dj=${dj?.uri} backups=${backups?.uri} lyrics=${lyrics?.uri} accords=${accords?.uri} midi=${midi?.uri} videos=${videos?.uri}"
                )
                return null
            }

            val folders = WorkspaceLibraryFolders(
                snapshot = snapshot,
                rootUri = rootDoc.uri,
                backingTracksUri = backingTracks.uri,
                audioUri = audio.uri,
                smpUri = smp.uri,
                djUri = dj.uri,
                backupsUri = backups.uri
            )
            Log.i(
                WORKSPACE_C1_TAG,
                "stage=$stage mode=SAF root=${folders.rootUri} backing=${folders.backingTracksUri} audio=${folders.audioUri} smp=${folders.smpUri} dj=${folders.djUri} backups=${folders.backupsUri}"
            )
            folders
        }
    }
}

internal fun initializeSafWorkspaceFromPickedTree(
    context: Context,
    pickedTreeUri: Uri,
    stage: String = "setup_saf"
): WorkspaceLibraryFolders? {
    persistTreePermIfPossible(context, pickedTreeUri)
    BackupFolderPrefs.saveSetupTreeUri(context, pickedTreeUri)
    BackupFolderPrefsSaf.saveSetupTreeUri(context, pickedTreeUri)

    val baseTree = DocumentFile.fromTreeUri(context, pickedTreeUri)
    if (baseTree == null || !baseTree.isDirectory) {
        Log.e(
            WORKSPACE_C1_TAG,
            "stage=$stage error=picked_tree_unresolved picked=$pickedTreeUri"
        )
        return null
    }

    val splRoot = if (shouldUsePickedFolderAsSplRoot(baseTree.name, listChildNames(baseTree))) {
        baseTree
    } else {
        ensureDocumentDir(baseTree, "SPL_Music", aliases = listOf("spl_music"))
    }
    if (splRoot == null || !splRoot.isDirectory) {
        Log.e(
            WORKSPACE_C1_TAG,
            "stage=$stage error=spl_root_unresolved picked=$pickedTreeUri base=${baseTree.uri}"
        )
        return null
    }

    val workspaceRoot = splToTreeUri(splRoot.uri)
    BackupFolderPrefs.saveLibraryRootUri(context, workspaceRoot)
    BackupFolderPrefsSaf.saveLibraryRootUri(context, workspaceRoot)

    val folders = ensureWorkspaceLibraryFolders(
        context = context,
        providedSnapshot = WorkspaceResolver.resolve(context),
        expectedMode = StorageModePrefs.Mode.SAF,
        stage = "$stage:folders"
    ) ?: return null

    DjFolderPrefs.save(context, folders.djUri)
    return folders
}

internal fun initializeInternalWorkspace(
    context: Context,
    stage: String = "setup_internal"
): WorkspaceLibraryFolders? {
    val rootFile = InternalStoragePaths.ensureSplRoot(context)
    val rootUri = Uri.fromFile(rootFile)
    BackupFolderPrefsInternal.saveLibraryRootUri(context, rootUri)
    BackupFolderPrefs.saveLibraryRootUri(context, rootUri)

    val folders = ensureWorkspaceLibraryFolders(
        context = context,
        providedSnapshot = WorkspaceResolver.resolve(context),
        expectedMode = StorageModePrefs.Mode.INTERNAL,
        stage = "$stage:folders"
    ) ?: return null

    DjFolderPrefs.save(context, folders.djUri)
    return folders
}

private fun ensureDocumentDir(
    parent: DocumentFile,
    expectedName: String,
    aliases: List<String> = emptyList()
): DocumentFile? {
    val wanted = (listOf(expectedName) + aliases).map(::normalizeWorkspaceFolderName)
    parent.listFiles()
        .firstOrNull { child ->
            child.isDirectory && normalizeWorkspaceFolderName(child.name.orEmpty()) in wanted
        }
        ?.let { return it }
    return parent.createDirectory(expectedName)
}

private fun ensureFileDir(dir: File): File {
    if (!dir.exists()) {
        dir.mkdirs()
    }
    return dir
}

private fun normalizeWorkspaceFolderName(name: String): String {
    return name.trim().lowercase().replace(" ", "").replace(Regex("\\(\\d+\\)$"), "")
}
