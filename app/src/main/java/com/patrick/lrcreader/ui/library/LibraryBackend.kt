package com.patrick.lrcreader.ui.library

import android.net.Uri
import android.os.Handler
import com.patrick.lrcreader.core.LibraryIndexCache
import com.patrick.lrcreader.ui.LibraryEntry
import com.patrick.lrcreader.ui.MoveResult

enum class LibraryDeleteRole {
    AUDIO,
    LYRICS,
    ACCORDS,
    FILE
}

data class LibraryDeleteItem(
    val uri: Uri,
    val role: LibraryDeleteRole,
    val displayName: String
)

data class LibraryDeletePlan(
    val target: LibraryDeleteItem,
    val associated: List<LibraryDeleteItem>
) {
    val isAudioTarget: Boolean get() = target.role == LibraryDeleteRole.AUDIO
    val hasAssociated: Boolean get() = associated.isNotEmpty()
}

enum class LibraryDeleteStatus {
    DELETED,
    ALREADY_MISSING,
    FAILED
}

data class LibraryDeleteItemResult(
    val item: LibraryDeleteItem,
    val status: LibraryDeleteStatus,
    val detail: String? = null
) {
    val success: Boolean get() = status != LibraryDeleteStatus.FAILED
}

data class LibraryDeleteResult(
    val results: List<LibraryDeleteItemResult>
) {
    val hasFailures: Boolean get() = results.any { !it.success }
}

interface LibraryBackend {
    fun getRootUri(): Uri?
    fun ensureBaseFolders()
    fun chooseInitialFolder(root: Uri, indexAll: List<LibraryIndexCache.CachedEntry>): Uri

    fun loadIndex(): List<LibraryIndexCache.CachedEntry>
    fun saveIndex(index: List<LibraryIndexCache.CachedEntry>)

    suspend fun scanAll(
        root: Uri,
        folderToShow: Uri,
        onIndexAll: (List<LibraryIndexCache.CachedEntry>) -> Unit,
        onEntries: (List<LibraryEntry>) -> Unit
    )

    fun listFolder(
        folderUri: Uri,
        indexAll: List<LibraryIndexCache.CachedEntry>,
        djExcludedReason: String
    ): List<LibraryEntry>

    suspend fun importAudio(
        pickedUris: List<Uri>,
        destFolderUri: Uri?,
        currentFolderUri: Uri?
    ): Uri?

    suspend fun rename(
        folderUri: Uri,
        oldUri: Uri,
        oldName: String,
        newNameFinal: String
    ): Uri?

    suspend fun move(
        mainHandler: Handler,
        srcUri: Uri,
        destUri: Uri,
        indexAll: List<LibraryIndexCache.CachedEntry>,
        onProgress: (Float?, String?) -> Unit
    ): MoveResult

    suspend fun planDelete(
        target: Uri,
        indexAll: List<LibraryIndexCache.CachedEntry>
    ): LibraryDeletePlan

    suspend fun deleteWithPlan(
        plan: LibraryDeletePlan,
        includeAssociated: Boolean
    ): LibraryDeleteResult

    suspend fun delete(target: Uri): Boolean
}
