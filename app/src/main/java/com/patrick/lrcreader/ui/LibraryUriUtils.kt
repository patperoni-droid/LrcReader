package com.patrick.lrcreader.ui

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

fun findUriByNameInFolder(
    context: Context,
    folderUri: Uri,
    fileName: String
): Uri? {
    val folderDoc = DocumentFile.fromTreeUri(context, folderUri)
        ?: DocumentFile.fromSingleUri(context, folderUri)
        ?: return null

    return folderDoc.listFiles()
        .firstOrNull { it.isFile && it.name == fileName }
        ?.uri
}