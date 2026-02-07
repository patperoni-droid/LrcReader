package com.patrick.lrcreader.core

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

fun openInputStreamSmart(context: Context, uri: Uri): InputStream? {
    return when (uri.scheme) {
        "file" -> {
            val path = uri.path ?: return null
            FileInputStream(File(path))
        }
        else -> context.contentResolver.openInputStream(uri)
    }
}