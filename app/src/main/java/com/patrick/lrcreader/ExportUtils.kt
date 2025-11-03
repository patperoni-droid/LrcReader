package com.patrick.lrcreader

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/* -------------------------------------------------------------------------- */
/*  EXPORT / IMPORT JSON                                                      */
/* -------------------------------------------------------------------------- */

@SuppressLint("InlinedApi")
fun saveJsonToDownloads(context: Context, fileName: String, json: String): Boolean {
    return try {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/json")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Downloads.EXTERNAL_CONTENT_URI
        }

        val itemUri = resolver.insert(collection, contentValues) ?: return false

        resolver.openOutputStream(itemUri)?.use { out ->
            out.write(json.toByteArray())
            out.flush()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(itemUri, contentValues, null, null)
        }

        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

fun shareJson(context: Context, fileName: String, json: String) {
    try {
        val cacheFile = File(context.cacheDir, fileName)
        cacheFile.writeText(json)

        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            cacheFile
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Partager la sauvegarde"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

/* -------------------------------------------------------------------------- */
/*  HELPERS IMPORT                                                           */
/* -------------------------------------------------------------------------- */

fun getDisplayName(context: Context, uri: Uri): String? {
    return try {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
    } catch (_: Exception) {
        null
    }
}

fun nowString(): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
    return sdf.format(Date())
}