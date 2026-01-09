package com.patrick.lrcreader.ui

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile

fun isAudioOrVideo(name: String?): Boolean {
    val n = name?.lowercase() ?: return false
    return n.endsWith(".mp3") || n.endsWith(".wav") ||
            n.endsWith(".m4a") || n.endsWith(".aac") ||
            n.endsWith(".flac") || n.endsWith(".ogg") ||
            n.endsWith(".mp4") || n.endsWith(".mkv") ||
            n.endsWith(".webm") || n.endsWith(".mov")
}



fun asTreeDocumentUri(rootTreeUri: Uri, docUriOrTreeUri: Uri): Uri {
    return try {
        DocumentsContract.getTreeDocumentId(docUriOrTreeUri) // si déjà treeUri → OK
        docUriOrTreeUri
    } catch (_: Exception) {
        val docId = DocumentsContract.getDocumentId(docUriOrTreeUri)
        DocumentsContract.buildDocumentUriUsingTree(rootTreeUri, docId)
    }
}