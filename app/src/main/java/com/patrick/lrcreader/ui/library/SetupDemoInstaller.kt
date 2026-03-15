package com.patrick.lrcreader.ui.library

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.patrick.lrcreader.core.BackupFolderPrefs
import com.patrick.lrcreader.core.BackupFolderPrefsSaf
import com.patrick.lrcreader.core.LibraryIndexCache
import com.patrick.lrcreader.core.LibrarySnapshot
import com.patrick.lrcreader.core.PlaylistRepository
import com.patrick.lrcreader.exo.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "SETUP_DEMO"
private const val DEMO_PLAYLIST_NAME = "SPL Demo"

data class DemoInstallResult(
    val playlistName: String,
    val audioFolderUri: Uri?,
    val importedAudioUris: List<String>
)

suspend fun installDemoLibrary(context: Context): DemoInstallResult = withContext(Dispatchers.IO) {
    var stage = "start"
    try {
        val rootUri = BackupFolderPrefs.getLibraryRootUri(context)
            ?: error("library_root_missing")
        val backendType = if (rootUri.scheme == "file") "INTERNAL" else "SAF"
        Log.i(
            TAG,
            "installDemoLibrary:start flavor=${BuildConfig.FLAVOR} buildType=${BuildConfig.BUILD_TYPE} debug=${BuildConfig.DEBUG} backend=$backendType rootUri=$rootUri"
        )

        stage = "copy_demo_files"
        val (audioFolderUri, importedAudioUris) = if (rootUri.scheme == "file") {
            installDemoIntoInternalStorage(context, rootUri)
        } else {
            installDemoIntoSafStorage(context, rootUri)
        }

        stage = "playlist_start"
        Log.i(
            TAG,
            "playlist:start name=$DEMO_PLAYLIST_NAME existingCount=${PlaylistRepository.getAllSongsRaw(DEMO_PLAYLIST_NAME).size} importedCount=${importedAudioUris.size}"
        )
        val existingOrder = PlaylistRepository.getAllSongsRaw(DEMO_PLAYLIST_NAME)
        PlaylistRepository.createIfNotExists(DEMO_PLAYLIST_NAME)
        importedAudioUris.forEach { uri -> PlaylistRepository.assignSongToPlaylist(DEMO_PLAYLIST_NAME, uri) }
        PlaylistRepository.updatePlayListOrder(
            DEMO_PLAYLIST_NAME,
            mergeDemoPlaylistOrder(importedAudioUris, existingOrder)
        )
        Log.i(
            TAG,
            "playlist:end name=$DEMO_PLAYLIST_NAME finalCount=${PlaylistRepository.getAllSongsRaw(DEMO_PLAYLIST_NAME).size}"
        )

        stage = "refresh_start"
        Log.i(TAG, "refresh:start root=$rootUri folderToShow=${audioFolderUri ?: rootUri}")
        val backend: LibraryBackend = if (rootUri.scheme == "file") {
            LibraryBackendInternal(context)
        } else {
            LibraryBackendSaf(context)
        }

        var refreshedIndex: List<LibraryIndexCache.CachedEntry> = emptyList()
        backend.scanAll(
            root = rootUri,
            folderToShow = audioFolderUri ?: rootUri,
            onIndexAll = { refreshedIndex = it },
            onEntries = { }
        )
        LibrarySnapshot.rootFolderUri = rootUri
        LibrarySnapshot.entries = refreshedIndex.map { it.uriString }
        LibrarySnapshot.isReady = true
        Log.i(TAG, "refresh:end indexCount=${refreshedIndex.size}")

        stage = "done"
        Log.i(
            TAG,
            "installDemoLibrary:end playlist=$DEMO_PLAYLIST_NAME audioFolderUri=$audioFolderUri importedCount=${importedAudioUris.size}"
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
    rootUri: Uri
): Pair<Uri, List<String>> {
    val rootDir = File(rootUri.path ?: error("internal_root_path_missing"))
    val backingTracks = File(rootDir, "BackingTracks").apply { mkdirs() }
    val audioDir = File(backingTracks, "audio").apply { mkdirs() }
    val lyricsDir = File(backingTracks, "Lyrics").apply { mkdirs() }
    val accordsDir = File(backingTracks, "Accords").apply { mkdirs() }

    val assetManager = context.assets
    val audioNames = listDemoAssetFiles(assetManager.list("demo/audio"))
    audioNames.forEach { name ->
        copyAssetToFile(
            context = context,
            assetPath = "demo/audio/$name",
            destination = File(audioDir, name),
            logicalType = "audio",
            mime = mimeTypeForAsset(name),
            targetLabel = audioDir.absolutePath
        )
    }

    listDemoAssetFiles(assetManager.list("demo/lyrics")).forEach { name ->
        copyAssetToFile(
            context = context,
            assetPath = "demo/lyrics/$name",
            destination = File(lyricsDir, name),
            logicalType = "lyrics",
            mime = mimeTypeForAsset(name),
            targetLabel = lyricsDir.absolutePath
        )
    }

    listDemoAssetFiles(assetManager.list("demo/Accords")).forEach { name ->
        copyAssetToFile(
            context = context,
            assetPath = "demo/Accords/$name",
            destination = File(accordsDir, name),
            logicalType = "accords",
            mime = mimeTypeForAsset(name),
            targetLabel = accordsDir.absolutePath
        )
    }

    return Uri.fromFile(audioDir) to audioNames.map { name ->
        Uri.fromFile(File(audioDir, name)).toString()
    }
}

private fun installDemoIntoSafStorage(
    context: Context,
    rootUri: Uri
): Pair<Uri?, List<String>> {
    val setupTreeUri = BackupFolderPrefsSaf.getSetupTreeUri(context)
        ?: BackupFolderPrefs.getSetupTreeUri(context)
        ?: rootUri
    val parentDoc = resolveRootDocument(context, setupTreeUri)
        ?: error("saf_root_missing")
    Log.i(
        TAG,
        "root:resolved requestUri=$rootUri setupTreeUri=$setupTreeUri resolvedUri=${parentDoc.uri} isDirectory=${parentDoc.isDirectory} children=${listChildNames(parentDoc)}"
    )

    val splRoot = ensureDirSmart(parentDoc, "SPL_Music", aliases = listOf("spl_music"))
        ?: error("spl_music_missing")
    Log.i(
        TAG,
        "resolve:SPL_Music parent=${parentDoc.uri} result=${splRoot.uri} children=${listChildNames(parentDoc)}"
    )

    val backingTracks = ensureDirSmart(splRoot, "BackingTracks", aliases = listOf("backingtracks", "backingtrack"))
        ?: error("backingtracks_missing")
    Log.i(
        TAG,
        "resolve:BackingTracks root=${splRoot.uri} result=${backingTracks.uri} children=${listChildNames(splRoot)}"
    )
    val audioDir = ensureDirSmart(backingTracks, "audio", aliases = listOf("Audio"))
        ?: error("audio_dir_missing")
    val lyricsDir = ensureDirSmart(backingTracks, "Lyrics", aliases = listOf("lyrics"))
        ?: error("lyrics_dir_missing")
    val accordsDir = ensureDirSmart(backingTracks, "Accords", aliases = listOf("accords"))
        ?: error("accords_dir_missing")

    val assetManager = context.assets
    val audioNames = listDemoAssetFiles(assetManager.list("demo/audio"))
    val importedAudioUris = audioNames.map { name ->
        copyAssetToDocumentFile(
            context = context,
            assetPath = "demo/audio/$name",
            targetDir = audioDir,
            fileName = name,
            logicalType = "audio"
        )
    }

    listDemoAssetFiles(assetManager.list("demo/lyrics")).forEach { name ->
        copyAssetToDocumentFile(
            context = context,
            assetPath = "demo/lyrics/$name",
            targetDir = lyricsDir,
            fileName = name,
            logicalType = "lyrics"
        )
    }

    listDemoAssetFiles(assetManager.list("demo/Accords")).forEach { name ->
        copyAssetToDocumentFile(
            context = context,
            assetPath = "demo/Accords/$name",
            targetDir = accordsDir,
            fileName = name,
            logicalType = "accords"
        )
    }

    return audioDir.uri to importedAudioUris
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

internal fun mergeDemoPlaylistOrder(demoUris: List<String>, existingUris: List<String>): List<String> {
    val ordered = LinkedHashSet<String>()
    demoUris.forEach { ordered.add(it) }
    existingUris.forEach { ordered.add(it) }
    return ordered.toList()
}
