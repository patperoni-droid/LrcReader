package com.patrick.lrcreader.ui.library

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.patrick.lrcreader.core.LibraryIndexCache
import com.patrick.lrcreader.core.LibrarySnapshot
import com.patrick.lrcreader.core.PlaylistRepository
import com.patrick.lrcreader.core.StorageModePrefs
import com.patrick.lrcreader.core.WorkspaceResolver
import com.patrick.lrcreader.exo.BuildConfig
import com.patrick.lrcreader.smp.SmpSecureImportPipeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private const val TAG = "SETUP_DEMO"
private const val DEMO_PLAYLIST_NAME = "SPL Demo"

data class DemoInstallResult(
    val playlistName: String,
    val audioFolderUri: Uri?,
    val importedAudioUris: List<String>
)

private data class DemoInstallPaths(
    val copyRootUri: Uri,
    val audioFolderUri: Uri?,
    val importedSongs: List<DemoImportedSong>
)

private data class DemoImportedSong(
    val playlistItemUri: String,
    val songId: String
)

suspend fun installDemoLibrary(context: Context): DemoInstallResult = withContext(Dispatchers.IO) {
    var stage = "start"
    try {
        val workspaceSnapshot = resolveDemoWorkspaceSnapshot(
            context = context,
            stage = "setup_demo:resolve_workspace"
        ) ?: error("library_root_missing")
        val rootUri = workspaceSnapshot.workspaceRootUri
            ?: error("library_root_missing")
        val backendType = if (workspaceSnapshot.mode == StorageModePrefs.Mode.INTERNAL) "INTERNAL" else "SAF"
        val secureImportPipeline = SmpSecureImportPipeline(context)
        Log.i(
            TAG,
            "installDemoLibrary:start flavor=${BuildConfig.FLAVOR} buildType=${BuildConfig.BUILD_TYPE} debug=${BuildConfig.DEBUG} backend=$backendType rootUri=$rootUri"
        )

        stage = "copy_demo_files"
        val installPaths = if (workspaceSnapshot.mode == StorageModePrefs.Mode.INTERNAL) {
            installDemoIntoInternalStorage(
                context = context,
                rootUri = rootUri,
                secureImportPipeline = secureImportPipeline
            )
        } else {
            installDemoIntoSafStorage(
                context = context,
                rootUri = rootUri,
                secureImportPipeline = secureImportPipeline
            )
        }
        val copyRootUri = installPaths.copyRootUri
        val audioFolderUri = installPaths.audioFolderUri
        val importedSongs = installPaths.importedSongs
        val importedAudioUris = importedSongs.map { it.playlistItemUri }
        Log.i(
            TAG,
            "copy:end requestedRoot=$rootUri copyRoot=$copyRootUri audioFolderUri=$audioFolderUri importedCount=${importedAudioUris.size}"
        )

        stage = "playlist_start"
        Log.i(
            TAG,
            "playlist:start name=$DEMO_PLAYLIST_NAME existingCount=${PlaylistRepository.getAllSongsRaw(DEMO_PLAYLIST_NAME).size} importedCount=${importedAudioUris.size}"
        )
        PlaylistRepository.createIfNotExists(DEMO_PLAYLIST_NAME)
        importedSongs.forEach { importedSong ->
            PlaylistRepository.assignSongToPlaylist(
                playlistName = DEMO_PLAYLIST_NAME,
                songUri = importedSong.playlistItemUri,
                songId = importedSong.songId
            )
        }
        PlaylistRepository.updatePlayListOrder(DEMO_PLAYLIST_NAME, importedAudioUris)
        Log.i(
            TAG,
            "playlist:end name=$DEMO_PLAYLIST_NAME finalCount=${PlaylistRepository.getAllSongsRaw(DEMO_PLAYLIST_NAME).size}"
        )
        Log.i(TAG, "backup:autoSave skipped playlist=$DEMO_PLAYLIST_NAME reason=keep_demo_install_responsive")

        stage = "refresh_start"
        val refreshRootUri = copyRootUri
        val folderToShow = audioFolderUri ?: refreshRootUri
        Log.i(
            TAG,
            "refresh:start requestedRoot=$rootUri refreshRoot=$refreshRootUri folderToShow=$folderToShow"
        )
        val backend: LibraryBackend = if (workspaceSnapshot.mode == StorageModePrefs.Mode.INTERNAL) {
            LibraryBackendInternal(context, workspaceSnapshot)
        } else {
            LibraryBackendSaf(context, workspaceSnapshot)
        }

        var refreshedIndex: List<LibraryIndexCache.CachedEntry> = emptyList()
        backend.scanAll(
            root = refreshRootUri,
            folderToShow = folderToShow,
            onIndexAll = { refreshedIndex = it },
            onEntries = { }
        )
        LibrarySnapshot.rootFolderUri = refreshRootUri
        LibrarySnapshot.entries = refreshedIndex.map { it.uriString }
        LibrarySnapshot.isReady = true
        Log.i(TAG, "refresh:end refreshRoot=$refreshRootUri indexCount=${refreshedIndex.size}")

        stage = "done"
        Log.i(
            TAG,
            "installDemoLibrary:end playlist=$DEMO_PLAYLIST_NAME copyRoot=$copyRootUri audioFolderUri=$audioFolderUri importedCount=${importedAudioUris.size}"
        )
        DemoInstallResult(
            playlistName = DEMO_PLAYLIST_NAME,
            audioFolderUri = audioFolderUri,
            importedAudioUris = importedAudioUris
        )
    } catch (t: Throwable) {
        Log.e(TAG, "installDemoLibrary:failed stage=$stage message=${t.message}", t)
        throw t
    }
}

private fun installDemoIntoInternalStorage(
    context: Context,
    rootUri: Uri,
    secureImportPipeline: SmpSecureImportPipeline
): DemoInstallPaths {
    val rootDir = File(rootUri.path ?: error("internal_root_path_missing"))
    val backingTracks = File(rootDir, "BackingTracks").apply { mkdirs() }

    val assetManager = context.assets
    val importedSongs = listDemoSmpAssetFiles(assetManager.list("demo/smp")).map { name ->
        val destination = File(backingTracks, name)
        copyAssetToFile(
            context = context,
            assetPath = "demo/smp/$name",
            destination = destination,
            logicalType = "smp",
            mime = mimeTypeForAsset(name),
            targetLabel = backingTracks.absolutePath
        )
        importCopiedDemoSmp(
            secureImportPipeline = secureImportPipeline,
            archiveUri = Uri.fromFile(destination),
            assetName = name
        )
    }

    return DemoInstallPaths(
        copyRootUri = rootUri,
        audioFolderUri = null,
        importedSongs = importedSongs
    )
}

private fun installDemoIntoSafStorage(
    context: Context,
    rootUri: Uri,
    secureImportPipeline: SmpSecureImportPipeline
): DemoInstallPaths {
    val parentDoc = resolveRootDocument(context, rootUri)
        ?: error("saf_root_missing")
    Log.i(
        TAG,
        "root:resolved requestUri=$rootUri resolvedUri=${parentDoc.uri} isDirectory=${parentDoc.isDirectory} children=${listChildNames(parentDoc)}"
    )

    val backingTracks = ensureDirSmart(parentDoc, "BackingTracks", aliases = listOf("backingtracks", "backingtrack"))
        ?: error("backingtracks_missing")
    Log.i(
        TAG,
        "resolve:BackingTracks root=${parentDoc.uri} result=${backingTracks.uri} children=${listChildNames(parentDoc)}"
    )

    val assetManager = context.assets
    val importedSongs = listDemoSmpAssetFiles(assetManager.list("demo/smp")).map { name ->
        val copiedUri = copyAssetToDocumentFile(
            context = context,
            assetPath = "demo/smp/$name",
            targetDir = backingTracks,
            fileName = name,
            logicalType = "smp"
        )
        importCopiedDemoSmp(
            secureImportPipeline = secureImportPipeline,
            archiveUri = Uri.parse(copiedUri),
            assetName = name
        )
    }

    return DemoInstallPaths(
        copyRootUri = parentDoc.uri,
        audioFolderUri = null,
        importedSongs = importedSongs
    )
}

private fun importCopiedDemoSmp(
    secureImportPipeline: SmpSecureImportPipeline,
    archiveUri: Uri,
    assetName: String
): DemoImportedSong {
    val importResult = secureImportPipeline.importRuntimeReady(archiveUri)
    val importedSong = importResult.importedSong
        ?: error("demo_smp_import_failed:$assetName:${importResult.failureReason ?: "unknown"}")
    importResult.archiveFailureReason?.let { archiveFailureReason ->
        Log.w(
            TAG,
            "demo import runtime-ready archive finalize pending issue asset=$assetName songId=${importedSong.id} requestId=${importResult.archiveRequestId} archiveState=${importResult.archiveState} failureReason=$archiveFailureReason"
        )
    }
    return DemoImportedSong(
        playlistItemUri = buildDemoSmpItem(importedSong.id),
        songId = importedSong.id
    )
}

private fun resolveDemoWorkspaceSnapshot(
    context: Context,
    stage: String
): WorkspaceResolver.Snapshot? {
    val snapshot = WorkspaceResolver.resolve(context)
    val rootUri = snapshot.workspaceRootUri
    if (!snapshot.isUsable || rootUri == null) {
        Log.e(
            TAG,
            "stage=$stage error=workspace_unavailable mode=${snapshot.mode} status=${snapshot.status} root=$rootUri detail=${snapshot.detail}"
        )
        return null
    }
    Log.i(
        TAG,
        "stage=$stage mode=${snapshot.mode} status=${snapshot.status} root=$rootUri detail=${snapshot.detail}"
    )
    return snapshot
}

private fun buildDemoSmpItem(songId: String): String {
    val cleanSongId = songId.trim().ifBlank { "demo_song" }
    val encodedSongId = URLEncoder.encode(cleanSongId, StandardCharsets.UTF_8.name())
    return "smp://$encodedSongId"
}

private fun copyAssetToFile(
    context: Context,
    assetPath: String,
    destination: File,
    logicalType: String,
    mime: String,
    targetLabel: String
) {
    try {
        Log.i(
            TAG,
            "copy:start backend=INTERNAL type=$logicalType fileName=${destination.name} mime=$mime target=$targetLabel asset=$assetPath"
        )
        destination.parentFile?.mkdirs()
        context.assets.open(assetPath).use { input ->
            destination.outputStream().use { output ->
                input.copyTo(output)
                output.flush()
            }
        }
        Log.i(
            TAG,
            "copy:success backend=INTERNAL type=$logicalType fileName=${destination.name} target=${destination.absolutePath}"
        )
    } catch (t: Throwable) {
        Log.e(
            TAG,
            "copy:failed backend=INTERNAL type=$logicalType fileName=${destination.name} mime=$mime target=$targetLabel message=${t.message}",
            t
        )
        throw t
    }
}

private fun copyAssetToDocumentFile(
    context: Context,
    assetPath: String,
    targetDir: DocumentFile,
    fileName: String,
    logicalType: String
): String {
    val mime = safMimeTypeForAsset(fileName, logicalType)
    try {
        Log.i(
            TAG,
            "copy:start backend=SAF type=$logicalType fileName=$fileName mime=$mime targetDir=${targetDir.uri} asset=$assetPath"
        )
        val existing = findFileIgnoreCase(targetDir, fileName)
        if (existing != null) {
            Log.i(
                TAG,
                "createFile:skip_existing backend=SAF type=$logicalType fileName=$fileName existingUri=${existing.uri}"
            )
        } else {
            Log.i(
                TAG,
                "createFile:before backend=SAF type=$logicalType fileName=$fileName mime=$mime targetDir=${targetDir.uri}"
            )
        }
        val target = existing ?: targetDir.createFile(mime, fileName)
        ?: error("create_file_failed:$fileName")

        context.assets.open(assetPath).use { input ->
            context.contentResolver.openOutputStream(target.uri, "w").use { output ->
                requireNotNull(output) { "open_output_failed:$fileName" }
                input.copyTo(output)
                output.flush()
            }
        }
        Log.i(
            TAG,
            "copy:success backend=SAF type=$logicalType fileName=$fileName mime=$mime targetUri=${target.uri}"
        )
        return target.uri.toString()
    } catch (t: Throwable) {
        Log.e(
            TAG,
            "copy:failed backend=SAF type=$logicalType fileName=$fileName mime=$mime targetDir=${targetDir.uri} message=${t.message}",
            t
        )
        throw t
    }
}

private fun mimeTypeForAsset(name: String): String {
    val lower = name.lowercase()
    return when {
        lower.endsWith(".mp3") -> "audio/mpeg"
        lower.endsWith(".lrc") -> "text/plain"
        else -> "application/octet-stream"
    }
}

internal fun safMimeTypeForAsset(name: String, logicalType: String): String {
    val lowerType = logicalType.lowercase()
    val lowerName = name.lowercase()
    return when {
        lowerType == "audio" && lowerName.endsWith(".mp3") -> "audio/mpeg"
        lowerName.endsWith(".lrc") -> "application/octet-stream"
        else -> mimeTypeForAsset(name)
    }
}

private fun ensureDirSmart(
    parent: DocumentFile,
    expectedName: String,
    aliases: List<String> = emptyList()
): DocumentFile? {
    val wanted = (listOf(expectedName) + aliases).map { normalizeDirName(it) }
    val children = runCatching { parent.listFiles().toList() }.getOrDefault(emptyList())
    Log.i(
        TAG,
        "ensureDirSmart:start parent=${parent.uri} expected=$expectedName aliases=$aliases childNames=${children.map { it.name.orEmpty() }}"
    )
    children
        .firstOrNull { it.isDirectory && wanted.contains(normalizeDirName(it.name.orEmpty())) }
        ?.let {
            Log.i(TAG, "ensureDirSmart:hit parent=${parent.uri} expected=$expectedName actual=${it.name} uri=${it.uri}")
            return it
        }
    val created = parent.createDirectory(expectedName)
    Log.i(TAG, "ensureDirSmart:create parent=${parent.uri} expected=$expectedName created=${created?.uri}")
    return created
}

private fun normalizeDirName(name: String): String {
    return name.trim().lowercase().replace(" ", "").replace(Regex("\\(\\d+\\)$"), "")
}

private fun findFileIgnoreCase(dir: DocumentFile, fileName: String): DocumentFile? {
    return dir.listFiles().firstOrNull { child ->
        child.isFile && child.name.orEmpty().equals(fileName, ignoreCase = true)
    }
}

private fun resolveRootDocument(context: Context, rootUri: Uri): DocumentFile? {
    val directTree = DocumentFile.fromTreeUri(context, rootUri)
    if (directTree?.isDirectory == true) return directTree

    val normalizedTreeUri = normalizeAsTreeUri(rootUri)
    val normalizedTree = normalizedTreeUri?.let { DocumentFile.fromTreeUri(context, it) }
    if (normalizedTree?.isDirectory == true) return normalizedTree

    val single = DocumentFile.fromSingleUri(context, rootUri)
    if (single?.isDirectory == true) return single

    Log.w(
        TAG,
        "root:resolve_failed requestUri=$rootUri directTree=${directTree?.uri} normalizedTree=$normalizedTreeUri single=${single?.uri}"
    )
    return directTree ?: normalizedTree ?: single
}

private fun normalizeAsTreeUri(uri: Uri): Uri? {
    val authority = uri.authority ?: return null
    val treeId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
        ?: runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
        ?: return null
    return DocumentsContract.buildTreeDocumentUri(authority, treeId)
}

private fun listChildNames(parent: DocumentFile): List<String> {
    return runCatching {
        parent.listFiles().map { child ->
            val type = if (child.isDirectory) "dir" else "file"
            "${child.name.orEmpty()}($type)"
        }
    }.getOrDefault(emptyList())
}

internal fun listDemoAssetFiles(names: Array<String>?): List<String> {
    return names.orEmpty()
        .filter { it.isNotBlank() && !it.startsWith(".") }
        .sorted()
}

private fun listDemoSmpAssetFiles(names: Array<String>?): List<String> {
    val smpFiles = listDemoAssetFiles(names)
        .filter { it.endsWith(".smp", ignoreCase = true) }
    require(smpFiles.isNotEmpty()) { "demo_smp_assets_missing" }
    return smpFiles
}

internal fun mergeDemoPlaylistOrder(demoUris: List<String>, existingUris: List<String>): List<String> {
    val ordered = LinkedHashSet<String>()
    demoUris.forEach { ordered.add(it) }
    existingUris.forEach { ordered.add(it) }
    return ordered.toList()
}
