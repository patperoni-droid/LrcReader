package com.patrick.lrcreader.ui

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.patrick.lrcreader.smp.SmpArchiveSongIdResolver
import com.patrick.lrcreader.smp.SmpExporter
import com.patrick.lrcreader.smp.SmpLibraryScanner
import com.patrick.lrcreader.smp.SongUnit
import org.json.JSONObject

internal data class LibraryUpdateReference(
    val treeUri: String,
    val folderUri: String,
    val archivesBySongId: Map<String, String>
)

internal object LibraryUpdateReferenceCodec {
    fun encode(reference: LibraryUpdateReference): String = JSONObject().apply {
        put("treeUri", reference.treeUri)
        put("folderUri", reference.folderUri)
        put("archives", JSONObject(reference.archivesBySongId))
    }.toString()

    fun decode(raw: String?): LibraryUpdateReference? = runCatching {
        val root = JSONObject(raw.orEmpty())
        val treeUri = root.optString("treeUri").trim()
        val folderUri = root.optString("folderUri").trim()
        if (treeUri.isEmpty() || folderUri.isEmpty()) return null
        val archivesJson = root.optJSONObject("archives") ?: JSONObject()
        val archives = linkedMapOf<String, String>()
        archivesJson.keys().forEach { rawSongId ->
            val songId = rawSongId.trim()
            val archiveUri = archivesJson.optString(rawSongId).trim()
            if (songId.isNotEmpty() && archiveUri.isNotEmpty()) {
                archives[songId] = archiveUri
            }
        }
        LibraryUpdateReference(treeUri, folderUri, archives)
    }.getOrNull()
}

internal object LibraryUpdateReferenceStore {
    private const val PREFS_NAME = "more_live_songs_export_prefs"
    private const val KEY_REFERENCE = "library_update_reference_v0"

    fun load(context: Context): LibraryUpdateReference? = LibraryUpdateReferenceCodec.decode(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_REFERENCE, null)
    )

    fun save(context: Context, reference: LibraryUpdateReference): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_REFERENCE, LibraryUpdateReferenceCodec.encode(reference))
            .commit()
}

internal fun storedSafFolderDocumentId(
    documentId: String?,
    treeDocumentId: String?
): String? = documentId?.trim()?.takeIf(String::isNotEmpty)
    ?: treeDocumentId?.trim()?.takeIf(String::isNotEmpty)

internal fun safTreePermissionCoversFolder(
    recordedRootAuthority: String?,
    recordedRootDocumentId: String?,
    folderAuthority: String?,
    folderDocumentId: String?,
    permissionAuthority: String?,
    permissionTreeDocumentId: String?,
    permissionCanRead: Boolean,
    permissionCanWrite: Boolean
): Boolean {
    if (!permissionCanRead || !permissionCanWrite) return false
    if (folderDocumentId.isNullOrBlank() || permissionTreeDocumentId.isNullOrBlank()) return false
    if (folderAuthority != permissionAuthority) return false
    return (permissionTreeDocumentId == recordedRootDocumentId &&
        permissionAuthority == recordedRootAuthority) ||
        folderDocumentId == permissionTreeDocumentId ||
        folderDocumentId.startsWith("$permissionTreeDocumentId/")
}

internal fun isResolvedSafFolderUsable(
    hasWritableTreePermission: Boolean,
    folderExistsAndIsDirectory: Boolean
): Boolean = hasWritableTreePermission && folderExistsAndIsDirectory

internal fun registerSuccessfulLibraryBackupV0(
    treeUri: String,
    folderUri: String,
    expectedFamilyCount: Int,
    exportedArchivesBySongId: Map<String, String>,
    failureCount: Int,
    saveReference: (LibraryUpdateReference) -> Boolean
): LibraryUpdateReference? {
    if (failureCount != 0 || exportedArchivesBySongId.size != expectedFamilyCount) return null
    val reference = LibraryUpdateReference(
        treeUri = treeUri,
        folderUri = folderUri,
        archivesBySongId = exportedArchivesBySongId
    )
    return reference.takeIf { runCatching { saveReference(it) }.getOrDefault(false) }
}

internal fun libraryUpdateReferenceAfterBackup(
    currentReference: LibraryUpdateReference?,
    successfulBackupReference: LibraryUpdateReference?
): LibraryUpdateReference? = successfulBackupReference ?: currentReference

internal fun isLibraryUpdateAvailable(reference: LibraryUpdateReference?): Boolean =
    reference != null

internal data class LibraryUpdateResult(
    val reference: LibraryUpdateReference,
    val updatedCount: Int,
    val addedCount: Int,
    val failedCount: Int,
    val folderInaccessible: Boolean = false
)

internal interface LibraryUpdateArchiveGateway {
    fun isFolderWritable(reference: LibraryUpdateReference): Boolean
    fun publishFamily(reference: LibraryUpdateReference, song: SongUnit): String?
    fun deleteArchiveIfOwnedBySong(archiveUri: String, songId: String): Boolean
}

internal fun updateLibraryFamiliesV0(
    reference: LibraryUpdateReference,
    runtimeSongs: List<SongUnit>,
    gateway: LibraryUpdateArchiveGateway,
    saveReference: (LibraryUpdateReference) -> Boolean,
    onProgress: (completed: Int, total: Int, title: String) -> Unit = { _, _, _ -> }
): LibraryUpdateResult {
    if (!gateway.isFolderWritable(reference)) {
        return LibraryUpdateResult(reference, 0, 0, 0, folderInaccessible = true)
    }

    val families = runtimeSongs
        .asSequence()
        .filter { it.arrangementSourceSongId == null }
        .distinctBy { it.id.trim() }
        .filter { it.id.isNotBlank() }
        .toList()
    var currentReference = reference
    var updatedCount = 0
    var addedCount = 0
    var failedCount = 0

    families.forEachIndexed { index, song ->
        val songId = song.id.trim()
        val previousArchiveUri = currentReference.archivesBySongId[songId]
        val publishedArchiveUri = gateway.publishFamily(currentReference, song)
        if (publishedArchiveUri == null) {
            failedCount += 1
        } else {
            val nextArchives = LinkedHashMap(currentReference.archivesBySongId)
            nextArchives[songId] = publishedArchiveUri
            val nextReference = currentReference.copy(archivesBySongId = nextArchives)
            if (runCatching { saveReference(nextReference) }.getOrDefault(false)) {
                currentReference = nextReference
                if (previousArchiveUri == null) {
                    addedCount += 1
                } else {
                    val previousRemoved = previousArchiveUri == publishedArchiveUri ||
                        gateway.deleteArchiveIfOwnedBySong(previousArchiveUri, songId)
                    if (previousRemoved) {
                        updatedCount += 1
                    } else {
                        val rollbackReference = currentReference.copy(
                            archivesBySongId = LinkedHashMap(currentReference.archivesBySongId).apply {
                                this[songId] = previousArchiveUri
                            }
                        )
                        if (runCatching { saveReference(rollbackReference) }.getOrDefault(false)) {
                            currentReference = rollbackReference
                            gateway.deleteArchiveIfOwnedBySong(publishedArchiveUri, songId)
                        }
                        failedCount += 1
                    }
                }
            } else {
                failedCount += 1
                gateway.deleteArchiveIfOwnedBySong(publishedArchiveUri, songId)
            }
        }
        onProgress(index + 1, families.size, song.title)
    }

    return LibraryUpdateResult(
        reference = currentReference,
        updatedCount = updatedCount,
        addedCount = addedCount,
        failedCount = failedCount
    )
}

internal class SafLibraryUpdateArchiveGateway(
    private val context: Context
) : LibraryUpdateArchiveGateway {
    override fun isFolderWritable(reference: LibraryUpdateReference): Boolean = runCatching {
        val folder = resolveWorkingFolder(reference) ?: return@runCatching false
        isResolvedSafFolderUsable(
            hasWritableTreePermission = true,
            folderExistsAndIsDirectory = folder.exists() && folder.isDirectory
        )
    }.getOrDefault(false)

    override fun publishFamily(reference: LibraryUpdateReference, song: SongUnit): String? =
        runCatching { publishFamilySafely(reference, song) }.getOrNull()

    private fun publishFamilySafely(
        reference: LibraryUpdateReference,
        song: SongUnit
    ): String? {
        val songId = song.id.trim()
        val folder = resolveWorkingFolder(reference) ?: return null
        if (!folder.exists() || !folder.isDirectory) return null
        val currentSong = SmpLibraryScanner(context).findSongById(songId) ?: return null
        val cacheArchive = SmpExporter.exportSongUnitToCacheSmp(context, currentSong) ?: return null
        return try {
            val exportedSongId = cacheArchive.inputStream().use {
                SmpArchiveSongIdResolver.readStableSongId(it)
            }
            if (exportedSongId != songId) return null

            val targetName = resolveV0ArchiveName(folder, songId)
            val published = folder.createFile("application/octet-stream", targetName) ?: return null
            val copied = runCatching {
                context.contentResolver.openOutputStream(published.uri, "w")?.use { output ->
                    cacheArchive.inputStream().use { input -> input.copyTo(output) }
                    output.flush()
                } != null
            }.getOrDefault(false)
            if (!copied || SmpArchiveSongIdResolver.readStableSongId(context, published.uri) != songId) {
                runCatching { published.delete() }
                return null
            }
            published.uri.toString()
        } finally {
            runCatching { cacheArchive.delete() }
        }
    }

    override fun deleteArchiveIfOwnedBySong(archiveUri: String, songId: String): Boolean {
        val uri = Uri.parse(archiveUri)
        if (SmpArchiveSongIdResolver.readStableSongId(context, uri) != songId) return false
        return runCatching { DocumentFile.fromSingleUri(context, uri)?.delete() == true }
            .getOrDefault(false)
    }

    private fun resolveV0ArchiveName(folder: DocumentFile, songId: String): String {
        val stableId = SmpArchiveSongIdResolver.sanitizeSongId(songId) ?: "song"
        var attempt = 0
        while (true) {
            val suffix = if (attempt == 0) "" else "_${attempt + 1}"
            val candidate = "${stableId}_update$suffix.smp"
            if (folder.findFile(candidate) == null) return candidate
            attempt += 1
        }
    }

    private fun resolveWorkingFolder(reference: LibraryUpdateReference): DocumentFile? {
        val recordedRootUri = Uri.parse(reference.treeUri)
        val storedFolderUri = Uri.parse(reference.folderUri)
        val recordedRootDocumentId = runCatching {
            DocumentsContract.getTreeDocumentId(recordedRootUri)
        }.getOrNull()
        val folderDocumentId = storedSafFolderDocumentId(
            documentId = runCatching {
                DocumentsContract.getDocumentId(storedFolderUri)
            }.getOrNull(),
            treeDocumentId = runCatching {
                DocumentsContract.getTreeDocumentId(storedFolderUri)
            }.getOrNull()
        ) ?: return null
        val permission = context.contentResolver.persistedUriPermissions.firstOrNull { candidate ->
            safTreePermissionCoversFolder(
                recordedRootAuthority = recordedRootUri.authority,
                recordedRootDocumentId = recordedRootDocumentId,
                folderAuthority = storedFolderUri.authority,
                folderDocumentId = folderDocumentId,
                permissionAuthority = candidate.uri.authority,
                permissionTreeDocumentId = runCatching {
                    DocumentsContract.getTreeDocumentId(candidate.uri)
                }.getOrNull(),
                permissionCanRead = candidate.isReadPermission,
                permissionCanWrite = candidate.isWritePermission
            )
        } ?: return null
        val folderDocumentUri = DocumentsContract.buildDocumentUriUsingTree(
            permission.uri,
            folderDocumentId
        )
        return DocumentFile.fromTreeUri(context, folderDocumentUri)
    }
}
