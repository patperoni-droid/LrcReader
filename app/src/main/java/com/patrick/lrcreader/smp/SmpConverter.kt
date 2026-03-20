package com.patrick.lrcreader.smp

import android.content.Context
import android.net.Uri
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.patrick.lrcreader.core.LrcStorage
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SmpConverter(private val context: Context) {

    companion object {
        private const val TAG = "SMP_CONVERTER"
        private const val AUDIO_ENTRY_NAME = "audio.mp3"
        private const val LYRICS_ENTRY_NAME = "lyrics.lrc"
        private const val CONFIG_ENTRY_NAME = "config.json"
        private const val OUTPUT_MIME = "application/octet-stream"
    }

    private data class SiblingEntry(
        val name: String,
        val uri: Uri
    )

    fun convertSingle(mp3Uri: Uri): Result<Uri> {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Log.w(TAG, "convertSingle appelé sur le thread principal uri=$mp3Uri")
        }

        return runCatching {
            val sourceName = resolveDisplayName(mp3Uri)
            require(isMp3FileName(sourceName)) {
                "Fichier non supporté pour conversion SMP: $sourceName"
            }

            val baseName = sourceName.substringBeforeLast('.').trim()
                .ifBlank { throw IOException("Nom MP3 invalide: $sourceName") }

            val siblings = listSiblingEntries(mp3Uri)
            val outputName = "$baseName.smp"
            val existingOutput = siblings.firstOrNull { it.name.equals(outputName, ignoreCase = true) }?.uri
            if (existingOutput != null) {
                Log.i(TAG, "Conversion SMP réutilise un package existant source=$mp3Uri output=$existingOutput")
                return@runCatching existingOutput
            }

            val resolvedLyricsText = LrcStorage.loadForTrack(context, mp3Uri.toString())
                ?.takeIf { it.isNotBlank() }
            val lyricsUri = if (resolvedLyricsText == null) {
                resolveLyricsSibling(baseName, siblings)
            } else {
                null
            }
            val tempDir = File(
                context.cacheDir,
                "smp_convert_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
            )
            if (!tempDir.mkdirs()) {
                throw IOException("Création du dossier temporaire impossible")
            }

            try {
                val tempAudio = File(tempDir, AUDIO_ENTRY_NAME)
                copyUriToFile(mp3Uri, tempAudio)
                val stableSongId = buildStableSongId(tempAudio)

                val tempLyrics = when {
                    resolvedLyricsText != null -> {
                        File(tempDir, LYRICS_ENTRY_NAME).also { file ->
                            file.writeText(resolvedLyricsText, Charsets.UTF_8)
                        }
                    }

                    lyricsUri != null -> {
                        File(tempDir, LYRICS_ENTRY_NAME).also { copyUriToFile(lyricsUri, it) }
                    }

                    else -> null
                }

                val tempConfig = File(tempDir, CONFIG_ENTRY_NAME).also { configFile ->
                    configFile.writeText(
                        buildMinimalConfigJson(
                            title = baseName,
                            songId = stableSongId,
                            hasLyrics = tempLyrics != null
                        ),
                        Charsets.UTF_8
                    )
                }

                val tempArchive = File(tempDir, outputName)
                createArchive(
                    targetFile = tempArchive,
                    audioFile = tempAudio,
                    lyricsFile = tempLyrics,
                    configFile = tempConfig
                )

                val resultUri = writeArchiveNextToSource(
                    sourceUri = mp3Uri,
                    outputName = outputName,
                    archiveFile = tempArchive
                )

                Log.i(
                    TAG,
                    "Conversion SMP terminée source=$mp3Uri output=$resultUri lyrics=${tempLyrics != null} songId=$stableSongId"
                )
                resultUri
            } finally {
                tempDir.deleteRecursively()
            }
        }.onFailure { error ->
            Log.e(TAG, "Conversion SMP échouée pour $mp3Uri", error)
        }
    }

    fun convertFolder(folderUri: Uri): List<Result<Uri>> {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Log.w(TAG, "convertFolder appelé sur le thread principal uri=$folderUri")
        }

        return runCatching {
            val children = listFolderChildren(folderUri)
            children
                .filter { !it.name.isBlank() && isMp3FileName(it.name) }
                .sortedBy { it.name.lowercase(Locale.ROOT) }
                .map { child -> convertSingle(child.uri) }
        }.getOrElse { error ->
            Log.e(TAG, "Conversion dossier SMP échouée pour $folderUri", error)
            listOf(Result.failure(error))
        }
    }

    private fun resolveLyricsSibling(baseName: String, siblings: List<SiblingEntry>): Uri? {
        val exactLyrics = siblings.firstOrNull { it.name.equals("$baseName.lrc", ignoreCase = true) }
        if (exactLyrics != null) {
            return exactLyrics.uri
        }

        val wildcardMatches = siblings.filter { entry ->
            entry.name.endsWith(".lrc", ignoreCase = true) &&
                entry.name.startsWith("$baseName-", ignoreCase = true)
        }

        return when (wildcardMatches.size) {
            0 -> null
            1 -> wildcardMatches.single().uri
            else -> {
                Log.w(
                    TAG,
                    "Paroles ambiguës ignorées base=$baseName matches=${wildcardMatches.joinToString { it.name }}"
                )
                null
            }
        }
    }

    private fun writeArchiveNextToSource(
        sourceUri: Uri,
        outputName: String,
        archiveFile: File
    ): Uri {
        return if (sourceUri.scheme == "file") {
            writeArchiveToFileParent(
                sourceUri = sourceUri,
                outputName = outputName,
                archiveFile = archiveFile
            )
        } else {
            writeArchiveToSafParent(
                sourceUri = sourceUri,
                outputName = outputName,
                archiveFile = archiveFile
            )
        }
    }

    private fun writeArchiveToFileParent(
        sourceUri: Uri,
        outputName: String,
        archiveFile: File
    ): Uri {
        val sourceFile = File(sourceUri.path ?: throw IOException("URI fichier invalide: $sourceUri"))
        val parentDir = sourceFile.parentFile ?: throw IOException("Dossier parent introuvable pour $sourceUri")
        val outputFile = File(parentDir, outputName)
        if (outputFile.exists()) {
            throw IOException("Le fichier ${outputFile.name} existe déjà")
        }

        archiveFile.inputStream().buffered().use { input ->
            outputFile.outputStream().buffered().use { output ->
                input.copyTo(output)
                output.flush()
            }
        }

        return Uri.fromFile(outputFile)
    }

    private fun writeArchiveToSafParent(
        sourceUri: Uri,
        outputName: String,
        archiveFile: File
    ): Uri {
        val sourceDocId = DocumentsContract.getDocumentId(sourceUri)
        val slash = sourceDocId.lastIndexOf('/')
        if (slash <= 0) {
            throw IOException("Parent SAF introuvable pour $sourceUri")
        }

        val parentDocId = sourceDocId.substring(0, slash)
        val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(sourceUri, parentDocId)

        val outputUri = DocumentsContract.createDocument(
            context.contentResolver,
            parentDocUri,
            OUTPUT_MIME,
            outputName
        ) ?: throw IOException("Création du fichier SMP impossible dans $parentDocUri")

        return try {
            archiveFile.inputStream().buffered().use { input ->
                context.contentResolver.openOutputStream(outputUri, "w")?.buffered()?.use { output ->
                    input.copyTo(output)
                    output.flush()
                } ?: throw IOException("Ouverture écriture impossible pour $outputUri")
            }
            outputUri
        } catch (error: Exception) {
            runCatching {
                DocumentFile.fromSingleUri(context, outputUri)?.delete()
                    ?: DocumentFile.fromTreeUri(context, outputUri)?.delete()
            }
            throw error
        }
    }

    private fun listFolderChildren(folderUri: Uri): List<SiblingEntry> {
        if (folderUri.scheme == "file") {
            val folder = File(folderUri.path ?: return emptyList())
            return folder.listFiles()
                ?.mapNotNull { file ->
                    file.name.takeIf { it.isNotBlank() }?.let { name ->
                        SiblingEntry(name = name, uri = Uri.fromFile(file))
                    }
                }
                .orEmpty()
        }

        val folderDoc = DocumentFile.fromTreeUri(context, folderUri)
            ?: DocumentFile.fromSingleUri(context, folderUri)
            ?: throw IOException("Dossier inaccessible: $folderUri")

        if (!folderDoc.isDirectory) {
            throw IOException("URI de dossier invalide: $folderUri")
        }

        return folderDoc.listFiles()
            .mapNotNull { child ->
                child.name?.takeIf { it.isNotBlank() }?.let { name ->
                    SiblingEntry(name = name, uri = child.uri)
                }
            }
    }

    private fun listSiblingEntries(fileUri: Uri): List<SiblingEntry> {
        if (fileUri.scheme == "file") {
            val file = File(fileUri.path ?: return emptyList())
            val parent = file.parentFile ?: return emptyList()
            return parent.listFiles()
                ?.mapNotNull { child ->
                    child.name.takeIf { it.isNotBlank() }?.let { name ->
                        SiblingEntry(name = name, uri = Uri.fromFile(child))
                    }
                }
                .orEmpty()
        }

        val docId = DocumentsContract.getDocumentId(fileUri)
        val slash = docId.lastIndexOf('/')
        if (slash <= 0) {
            return emptyList()
        }

        val parentDocId = docId.substring(0, slash)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(fileUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )

        val siblings = mutableListOf<SiblingEntry>()
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)

            while (cursor.moveToNext()) {
                val mime = cursor.getString(mimeCol) ?: ""
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    continue
                }

                val childId = cursor.getString(idCol) ?: continue
                val childName = cursor.getString(nameCol)?.trim().orEmpty()
                if (childName.isBlank()) {
                    continue
                }

                siblings += SiblingEntry(
                    name = childName,
                    uri = DocumentsContract.buildDocumentUriUsingTree(fileUri, childId)
                )
            }
        }

        return siblings
    }

    private fun copyUriToFile(sourceUri: Uri, destinationFile: File) {
        context.contentResolver.openInputStream(sourceUri)?.buffered()?.use { input ->
            destinationFile.outputStream().buffered().use { output ->
                input.copyTo(output)
                output.flush()
            }
        } ?: throw IOException("Lecture impossible pour $sourceUri")
    }

    private fun createArchive(
        targetFile: File,
        audioFile: File,
        lyricsFile: File?,
        configFile: File
    ) {
        ZipOutputStream(FileOutputStream(targetFile).buffered()).use { zipOutput ->
            writeFileEntry(zipOutput, audioFile, AUDIO_ENTRY_NAME)
            if (lyricsFile != null) {
                writeFileEntry(zipOutput, lyricsFile, LYRICS_ENTRY_NAME)
            }
            writeFileEntry(zipOutput, configFile, CONFIG_ENTRY_NAME)
        }
    }

    private fun writeFileEntry(
        zipOutput: ZipOutputStream,
        sourceFile: File,
        entryName: String
    ) {
        zipOutput.putNextEntry(ZipEntry(entryName))
        sourceFile.inputStream().buffered().use { input ->
            input.copyTo(zipOutput)
        }
        zipOutput.closeEntry()
    }

    private fun buildMinimalConfigJson(
        title: String,
        songId: String,
        hasLyrics: Boolean
    ): String {
        return JSONObject().apply {
            put("version", 1)
            put("id", songId)
            put("title", title)
            put("audio", AUDIO_ENTRY_NAME)
            if (hasLyrics) {
                put("lyrics", LYRICS_ENTRY_NAME)
            }
        }.toString(2)
    }

    private fun buildStableSongId(audioFile: File): String {
        return "song_${sha1Hex(audioFile).take(20)}"
    }

    private fun sha1Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-1")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private fun resolveDisplayName(uri: Uri): String {
        if (uri.scheme == "file") {
            return File(uri.path ?: "").name.ifBlank { "audio.mp3" }
        }

        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) {
                return cursor.getString(index)?.trim().orEmpty().ifBlank { "audio.mp3" }
            }
        }

        return uri.lastPathSegment?.trim().orEmpty().ifBlank { "audio.mp3" }
    }

    private fun isMp3FileName(name: String): Boolean {
        return name.endsWith(".mp3", ignoreCase = true)
    }
}
