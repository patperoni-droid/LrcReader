package com.patrick.lrcreader.ui.library

import android.net.Uri
import android.os.Handler
import com.patrick.lrcreader.core.LibraryIndexCache
import com.patrick.lrcreader.ui.LibraryEntry
import com.patrick.lrcreader.ui.MoveResult

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

    suspend fun delete(target: Uri): Boolean
}
