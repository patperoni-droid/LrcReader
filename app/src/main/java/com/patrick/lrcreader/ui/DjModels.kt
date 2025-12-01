package com.patrick.lrcreader.ui

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/**
 * Entrée générique pour la liste DJ :
 * - dossier (isDirectory = true)
 * - fichier audio (isDirectory = false)
 */
data class DjEntry(
    val uri: Uri,
    val name: String,
    val isDirectory: Boolean
)

/**
 * Cache mémoire pour accélérer la navigation dans l’arborescence.
 */
object DjFolderCache {
    private val map = mutableMapOf<String, List<DjEntry>>()

    fun get(uri: Uri): List<DjEntry>? = map[uri.toString()]

    fun put(uri: Uri, list: List<DjEntry>) {
        map[uri.toString()] = list
    }

    fun clear() = map.clear()
}

/* -------------------------------------------------------------------------- */
/*  Lecture d’un dossier (non récursive)                                      */
/* -------------------------------------------------------------------------- */
fun loadDjEntries(context: Context, folderUri: Uri): List<DjEntry> {
    val doc = DocumentFile.fromTreeUri(context, folderUri) ?: return emptyList()
    val all = try {
        doc.listFiles()
    } catch (_: Exception) {
        emptyArray()
    }

    val folders = all
        .filter { it.isDirectory }
        .sortedBy { it.name?.lowercase() ?: "" }
        .map { DjEntry(uri = it.uri, name = it.name ?: "Dossier", isDirectory = true) }

    val audio = all
        .filter {
            it.isFile && (
                    it.name?.endsWith(".mp3", true) == true ||
                            it.name?.endsWith(".wav", true) == true
                    )
        }
        .sortedBy { it.name?.lowercase() ?: "" }
        .map {
            val clean = (it.name ?: "titre")
                .removeSuffix(".mp3")
                .removeSuffix(".MP3")
                .removeSuffix(".wav")
                .removeSuffix(".WAV")
            DjEntry(uri = it.uri, name = clean, isDirectory = false)
        }

    return folders + audio
}

/* -------------------------------------------------------------------------- */
/*  Scan récursif de tous les sous-dossiers                                   */
/* -------------------------------------------------------------------------- */
fun scanAllAudioEntries(context: Context, rootUri: Uri): List<DjEntry> {
    val root = DocumentFile.fromTreeUri(context, rootUri) ?: return emptyList()
    val result = mutableListOf<DjEntry>()

    fun walk(dir: DocumentFile) {
        val children = try {
            dir.listFiles()
        } catch (_: Exception) {
            emptyArray()
        }

        children.forEach { f ->
            if (f.isDirectory) {
                walk(f)
            } else if (
                f.isFile && (
                        f.name?.endsWith(".mp3", true) == true ||
                                f.name?.endsWith(".wav", true) == true
                        )
            ) {
                val clean = (f.name ?: "titre")
                    .removeSuffix(".mp3")
                    .removeSuffix(".MP3")
                    .removeSuffix(".wav")
                    .removeSuffix(".WAV")
                result += DjEntry(uri = f.uri, name = clean, isDirectory = false)
            }
        }
    }

    walk(root)
    return result.sortedBy { it.name.lowercase() }
}