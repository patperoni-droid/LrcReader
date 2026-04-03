package com.patrick.lrcreader.ui.library

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.provider.OpenableColumns
import android.util.Log
import com.patrick.lrcreader.core.LibraryIndexCache
import com.patrick.lrcreader.core.StorageModePrefs
import com.patrick.lrcreader.core.WorkspaceResolver
import com.patrick.lrcreader.ui.LibraryEntry
import com.patrick.lrcreader.ui.MoveResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LibraryBackendInternal(
    private val context: Context,
    private val resolvedWorkspaceSnapshot: WorkspaceResolver.Snapshot? = null
) : LibraryBackend {
    private val tag = "LIB_INTERNAL"

    override fun getRootUri(): Uri? {
        val snapshot = resolveUsableWorkspaceSnapshot(
            context = context,
            providedSnapshot = resolvedWorkspaceSnapshot,
            expectedMode = StorageModePrefs.Mode.INTERNAL,
            stage = "library_backend_internal:get_root"
        ) ?: return null
        val root = snapshot.workspaceRootUri ?: return null
        Log.i(tag, "getRootUri: use_workspace_snapshot uri=$root status=${snapshot.status}")
        return root
    }

    override fun ensureBaseFolders() {
        val folders = ensureWorkspaceLibraryFolders(
            context = context,
            providedSnapshot = resolvedWorkspaceSnapshot,
            expectedMode = StorageModePrefs.Mode.INTERNAL,
            stage = "library_backend_internal:ensure_base_folders",
            createLegacyAudioTextDirs = false
        ) ?: return
        val rootDir = File(folders.rootUri.path ?: return)
        val backingTracks = File(folders.backingTracksUri.path ?: return)
        val audio = File(folders.audioUri.path ?: return)
        val smp = File(folders.smpUri.path ?: return)
        val lyrics = File(backingTracks, "Lyrics")
        Log.i(tag, "ensureBaseFolders rootPath=${rootDir.absolutePath}")
        Log.i(tag, "LIST root=${names(rootDir)}")
        Log.i(tag, "LIST BackingTracks=${names(backingTracks)}")
        Log.i(tag, "LIST Audio=${names(audio)}")
        Log.i(tag, "LIST SMP=${names(smp)}")
        Log.i(tag, "LIST Lyrics=${names(lyrics)}")
    }

    override fun chooseInitialFolder(root: Uri, indexAll: List<LibraryIndexCache.CachedEntry>): Uri {
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
        val rootDir = File(root.path ?: return)
        val index = withContext(Dispatchers.IO) { buildInternalIndex(rootDir) }
        saveIndex(index)
        onIndexAll(index)
        onEntries(listFolder(folderToShow, index, djExcludedReason = "Exclu de la bibliothèque (utilisé en mode DJ)"))
    }

    override fun listFolder(
        folderUri: Uri,
        indexAll: List<LibraryIndexCache.CachedEntry>,
        djExcludedReason: String
    ): List<LibraryEntry> {
        if (folderUri.scheme != "file") return emptyList()
        val dir = File(folderUri.path ?: return emptyList())
        val children = dir.listFiles().orEmpty()

        return children
            .map { f ->
                LibraryEntry(
                    uri = Uri.fromFile(f),
                    name = f.name,
                    isDirectory = f.isDirectory
                )
            }
            .sortedWith(compareByDescending<LibraryEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    override suspend fun importAudio(
        pickedUris: List<Uri>,
        destFolderUri: Uri?,
        currentFolderUri: Uri?
    ): Uri? = withContext(Dispatchers.IO) {
        val folders = ensureWorkspaceLibraryFolders(
            context = context,
            providedSnapshot = resolvedWorkspaceSnapshot,
            expectedMode = StorageModePrefs.Mode.INTERNAL,
            stage = "library_backend_internal:import_audio"
        ) ?: return@withContext null
        val audioDir = File(folders.audioUri.path ?: return@withContext null)

        pickedUris.forEach { src ->
            val name = queryDisplayName(src) ?: "import_${System.currentTimeMillis()}.mp3"
            val dest = File(audioDir, name)
            runCatching {
                context.contentResolver.openInputStream(src)?.use { input ->
                    dest.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }

        Uri.fromFile(audioDir)
    }

    override suspend fun rename(
        folderUri: Uri,
        oldUri: Uri,
        oldName: String,
        newNameFinal: String
    ): Uri? = withContext(Dispatchers.IO) {
        if (oldUri.scheme != "file") return@withContext null
        val oldFile = File(oldUri.path ?: return@withContext null)
        val newFile = File(oldFile.parentFile ?: return@withContext null, newNameFinal)
        if (!oldFile.exists()) return@withContext null
        if (!oldFile.renameTo(newFile)) return@withContext null
        Uri.fromFile(newFile)
    }

    override suspend fun move(
        mainHandler: Handler,
        srcUri: Uri,
        destUri: Uri,
        indexAll: List<LibraryIndexCache.CachedEntry>,
        onProgress: (Float?, String?) -> Unit
    ): MoveResult = withContext(Dispatchers.IO) {
        if (srcUri.scheme != "file" || destUri.scheme != "file") return@withContext MoveResult(false)
        val srcFile = File(srcUri.path ?: return@withContext MoveResult(false))
        val destDir = File(destUri.path ?: return@withContext MoveResult(false))
        if (!srcFile.exists() || !destDir.exists() || !destDir.isDirectory) return@withContext MoveResult(false)
        if (srcFile.isDirectory && isSameOrDescendantDirectory(srcFile, destDir)) {
            return@withContext MoveResult(false)
        }

        mainHandler.post { onProgress(null, "Déplacement…") }

        val out = File(destDir, srcFile.name)
        if (out.exists()) return@withContext MoveResult(false)
        if (srcFile.renameTo(out)) {
            return@withContext MoveResult(ok = true, newUri = Uri.fromFile(out))
        }

        if (srcFile.isDirectory) {
            val copied = copyEntryRecursively(srcFile, out)
            val deleted = copied && srcFile.deleteRecursively()
            if (copied && deleted) {
                return@withContext MoveResult(ok = true, newUri = Uri.fromFile(out))
            }
            if (out.exists()) {
                out.deleteRecursively()
            }
            return@withContext MoveResult(false)
        }

        runCatching {
            srcFile.inputStream().use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            }
            srcFile.delete()
        }.fold(
            onSuccess = { MoveResult(ok = true, newUri = Uri.fromFile(out)) },
            onFailure = { MoveResult(ok = false) }
        )
    }

    override suspend fun copyFile(
        mainHandler: Handler,
        srcUri: Uri,
        destUri: Uri,
        indexAll: List<LibraryIndexCache.CachedEntry>,
        onProgress: (Float?, String?) -> Unit
    ): MoveResult = withContext(Dispatchers.IO) {
        if (srcUri.scheme != "file" || destUri.scheme != "file") return@withContext MoveResult(false)
        val srcFile = File(srcUri.path ?: return@withContext MoveResult(false))
        val destDir = File(destUri.path ?: return@withContext MoveResult(false))
        if (!srcFile.exists() || !destDir.exists() || !destDir.isDirectory) {
            return@withContext MoveResult(false)
        }
        if (srcFile.isDirectory && isSameOrDescendantDirectory(srcFile, destDir)) {
            return@withContext MoveResult(false)
        }

        val out = File(destDir, srcFile.name)
        if (out.exists()) return@withContext MoveResult(false)

        mainHandler.post { onProgress(null, "Copie…") }

        if (srcFile.isDirectory) {
            return@withContext if (copyEntryRecursively(srcFile, out)) {
                MoveResult(ok = true, newUri = Uri.fromFile(out))
            } else {
                if (out.exists()) {
                    out.deleteRecursively()
                }
                MoveResult(ok = false)
            }
        }

        runCatching {
            srcFile.inputStream().use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            }
        }.fold(
            onSuccess = { MoveResult(ok = true, newUri = Uri.fromFile(out)) },
            onFailure = { MoveResult(ok = false) }
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

    override suspend fun delete(target: Uri): Boolean = withContext(Dispatchers.IO) {
        val item = LibraryDeleteItem(
            uri = target,
            role = LibraryDeleteRole.FILE,
            displayName = target.lastPathSegment ?: "file"
        )
        deleteSingleDetailed(item).success
    }

    private fun deleteSingleDetailed(item: LibraryDeleteItem): LibraryDeleteItemResult {
        if (item.uri.scheme != "file") {
            Log.e(tag, "delete failed invalid scheme uri=${item.uri} role=${item.role}")
            return LibraryDeleteItemResult(
                item = item,
                status = LibraryDeleteStatus.FAILED,
                detail = "invalid_scheme"
            )
        }

        val file = File(item.uri.path ?: "")
        if (!file.exists()) {
            return LibraryDeleteItemResult(
                item = item,
                status = LibraryDeleteStatus.ALREADY_MISSING,
                detail = "already_missing"
            )
        }

        val ok = runCatching {
            if (file.isDirectory) file.deleteRecursively() else file.delete()
        }.getOrDefault(false)
        if (ok) {
            return LibraryDeleteItemResult(
                item = item,
                status = LibraryDeleteStatus.DELETED
            )
        }

        Log.e(tag, "delete failed path=${file.absolutePath} role=${item.role}")
        return LibraryDeleteItemResult(
            item = item,
            status = LibraryDeleteStatus.FAILED,
            detail = "delete_returned_false"
        )
    }

    private fun copyEntryRecursively(source: File, destination: File): Boolean {
        if (source.isDirectory) {
            if (!destination.mkdir()) return false
            source.listFiles().orEmpty().forEach { child ->
                if (!copyEntryRecursively(child, File(destination, child.name))) return false
            }
            return true
        }

        return runCatching {
            source.inputStream().use { input ->
                destination.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            true
        }.getOrDefault(false)
    }

    private fun isSameOrDescendantDirectory(sourceDir: File, candidateDir: File): Boolean {
        return runCatching {
            val sourcePath = sourceDir.canonicalFile.toPath()
            val candidatePath = candidateDir.canonicalFile.toPath()
            candidatePath.startsWith(sourcePath)
        }.getOrDefault(false)
    }

    private fun buildInternalIndex(rootDir: File): List<LibraryIndexCache.CachedEntry> {
        val out = mutableListOf<LibraryIndexCache.CachedEntry>()

        fun walk(dir: File, parentUri: String?) {
            val children = dir.listFiles()?.toList().orEmpty()
            children.forEach { f ->
                val uriStr = Uri.fromFile(f).toString()
                out += LibraryIndexCache.CachedEntry(
                    uriString = uriStr,
                    name = f.name,
                    isDirectory = f.isDirectory,
                    parentUriString = parentUri
                )
                if (f.isDirectory) walk(f, uriStr)
            }
        }

        val rootUriStr = Uri.fromFile(rootDir).toString()
        out += LibraryIndexCache.CachedEntry(
            uriString = rootUriStr,
            name = rootDir.name.ifBlank { "SPL_Music" },
            isDirectory = true,
            parentUriString = null
        )

        walk(rootDir, rootUriStr)
        return out
    }

    private fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c: Cursor ->
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
                }
        }.getOrNull()
    }

    private fun names(dir: File): String {
        return dir.listFiles()?.joinToString(prefix = "[", postfix = "]") { it.name } ?: "null"
    }
}
