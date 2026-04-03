package com.patrick.lrcreader.smp

import android.content.Context
import android.net.Uri
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.patrick.lrcreader.core.BackupFolderPrefs
import com.patrick.lrcreader.core.BackupFolderPrefsInternal
import com.patrick.lrcreader.core.BackupFolderPrefsSaf
import com.patrick.lrcreader.core.InternalStoragePaths
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
        private const val LYRICS_ENTRY_NAME = "lyrics.lrc"
        private const val CONFIG_ENTRY_NAME = "config.json"
        private const val OUTPUT_MIME = "application/octet-stream"
    }

    private data class SiblingEntry(
        val name: String,
        val uri: Uri
    )

    private data class PreparedArchive(
        val outputName: String,
        val archiveFile: File,
        val tempDir: File,
        val stableSongId: String,
        val hasLyrics: Boolean
    )

    private sealed interface OutputParent {
        data class FileParent(val directory: File) : OutputParent
        data class SafParent(val directory: DocumentFile) : OutputParent
    }

    private fun backendLabel(uri: Uri): String {
        return if (uri.scheme == "file") "file" else "SAF"
    }

    private fun describeOutputParent(outputParent: OutputParent): String {
        return when (outputParent) {
            is OutputParent.FileParent -> {
                "type=file path=${outputParent.directory.absolutePath}"
            }

            is OutputParent.SafParent -> {
                "type=SAF name=${resolveDocumentName(outputParent.directory)} uri=${outputParent.directory.uri}"
            }
        }
    }

    private fun resolveDocumentName(document: DocumentFile?): String? {
        if (document == null) return null
        return document.name?.takeIf { it.isNotBlank() } ?: resolveDocumentName(document.uri)
    }

    private fun resolveDocumentName(uri: Uri?): String? {
        if (uri == null) return null
        val docId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
            ?: runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
            ?: return null
        val slashName = docId.substringAfterLast('/')
        val colonName = slashName.substringAfterLast(':')
        return colonName.ifBlank { slashName }.takeIf { it.isNotBlank() }
    }

    fun convertSingle(mp3Uri: Uri): Result<Uri> {
        return convertSingleInternal(
            mp3Uri = mp3Uri,
            outputParentResolver = { uri -> resolveOutputParent(uri) }
        )
    }

    fun convertSingleToLibrarySmp(mp3Uri: Uri): Result<Uri> {
        return convertSingleInternal(
            mp3Uri = mp3Uri,
            outputParentResolver = { resolveManagedOutputParent() }
        )
    }

    private fun convertSingleInternal(
        mp3Uri: Uri,
        outputParentResolver: (Uri) -> OutputParent
    ): Result<Uri> {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Log.w(TAG, "convertSingle appelé sur le thread principal uri=$mp3Uri")
        }

        return runCatching {
            val sourceName = resolveDisplayName(mp3Uri)
            require(isSupportedAudioFileName(sourceName)) {
                "Fichier non supporté pour conversion SMP: $sourceName"
            }
            val baseName = sourceName.substringBeforeLast('.').trim()
                .ifBlank { throw IOException("Nom audio invalide: $sourceName") }
            val outputName = "$baseName.smp"
            val outputParent = outputParentResolver(mp3Uri)
            Log.i(
                TAG,
                "step=start_conversion sourceUri=$mp3Uri backend=${backendLabel(mp3Uri)} sourceName=$sourceName outputName=$outputName outputParent=${describeOutputParent(outputParent)}"
            )
            val existingOutput = findExistingOutput(outputParent, outputName)
            if (existingOutput != null) {
                Log.i(
                    TAG,
                    "step=reuse_existing_output sourceUri=$mp3Uri outputName=$outputName outputUri=$existingOutput outputParent=${describeOutputParent(outputParent)}"
                )
                return@runCatching existingOutput
            }

            val prepared = prepareArchive(mp3Uri)
            Log.i(
                TAG,
                "step=archive_prepared sourceUri=$mp3Uri outputName=${prepared.outputName} tempArchive=${prepared.archiveFile.absolutePath} lyrics=${prepared.hasLyrics} songId=${prepared.stableSongId}"
            )
            try {
                val resultUri = writeArchiveToOutputParent(
                    outputParent = outputParent,
                    outputName = prepared.outputName,
                    archiveFile = prepared.archiveFile
                )

                Log.i(
                    TAG,
                    "step=write_output_ok sourceUri=$mp3Uri outputName=${prepared.outputName} outputUri=$resultUri outputParent=${describeOutputParent(outputParent)} lyrics=${prepared.hasLyrics} songId=${prepared.stableSongId}"
                )
                resultUri
            } finally {
                prepared.tempDir.deleteRecursively()
            }
        }.onFailure { error ->
            Log.e(
                TAG,
                "step=conversion_failed sourceUri=$mp3Uri backend=${backendLabel(mp3Uri)}",
                error
            )
        }
    }

    fun convertSingleToTempArchive(mp3Uri: Uri): Result<File> {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Log.w(TAG, "convertSingleToTempArchive appelé sur le thread principal uri=$mp3Uri")
        }

        return runCatching {
            val prepared = prepareArchive(mp3Uri)
            try {
                val archiveFile = File(
                    context.cacheDir,
                    "smp_import_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.smp"
                )
                prepared.archiveFile.inputStream().buffered().use { input ->
                    archiveFile.outputStream().buffered().use { output ->
                        input.copyTo(output)
                        output.flush()
                    }
                }
                Log.i(
                    TAG,
                    "Conversion SMP temporaire terminée source=$mp3Uri output=${archiveFile.absolutePath} lyrics=${prepared.hasLyrics} songId=${prepared.stableSongId}"
                )
                archiveFile
            } finally {
                prepared.tempDir.deleteRecursively()
            }
        }.onFailure { error ->
            Log.e(TAG, "Conversion SMP temporaire échouée pour $mp3Uri", error)
        }
    }

    fun convertFolder(folderUri: Uri): List<Result<Uri>> {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Log.w(TAG, "convertFolder appelé sur le thread principal uri=$folderUri")
        }

        return runCatching {
            val children = listFolderChildren(folderUri)
            children
                .filter { !it.name.isBlank() && isSupportedAudioFileName(it.name) }
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

    private fun prepareArchive(sourceUri: Uri): PreparedArchive {
        val sourceName = resolveDisplayName(sourceUri)
        require(isSupportedAudioFileName(sourceName)) {
            "Fichier non supporté pour conversion SMP: $sourceName"
        }
        val audioEntryName = buildAudioEntryName(sourceName)
        val baseName = sourceName.substringBeforeLast('.').trim()
            .ifBlank { throw IOException("Nom audio invalide: $sourceName") }

        val siblings = listSiblingEntries(sourceUri)
        val resolvedLyricsText = LrcStorage.loadForTrack(context, sourceUri.toString())
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

        val tempAudio = File(tempDir, audioEntryName)
        copyUriToFile(sourceUri, tempAudio)
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
                    audioEntryName = audioEntryName,
                    hasLyrics = tempLyrics != null
                ),
                Charsets.UTF_8
            )
        }

        val outputName = "$baseName.smp"
        val tempArchive = File(tempDir, outputName)
        createArchive(
            targetFile = tempArchive,
            audioFile = tempAudio,
            audioEntryName = audioEntryName,
            lyricsFile = tempLyrics,
            configFile = tempConfig
        )

        return PreparedArchive(
            outputName = outputName,
            archiveFile = tempArchive,
            tempDir = tempDir,
            stableSongId = stableSongId,
            hasLyrics = tempLyrics != null
        )
    }

    private fun writeArchiveToOutputParent(
        outputParent: OutputParent,
        outputName: String,
        archiveFile: File
    ): Uri {
        return when (outputParent) {
            is OutputParent.FileParent -> {
                writeArchiveToFileParent(
                    outputDir = outputParent.directory,
                    outputName = outputName,
                    archiveFile = archiveFile
                )
            }

            is OutputParent.SafParent -> {
                writeArchiveToSafParent(
                    outputDir = outputParent.directory,
                    outputName = outputName,
                    archiveFile = archiveFile
                )
            }
        }
    }

    private fun resolveOutputParent(sourceUri: Uri): OutputParent {
        return if (sourceUri.scheme == "file") {
            val sourceFile = File(sourceUri.path ?: throw IOException("URI fichier invalide: $sourceUri"))
            OutputParent.FileParent(resolvePreferredFileOutputDir(sourceFile))
        } else {
            OutputParent.SafParent(resolvePreferredSafOutputDir(sourceUri))
        }
    }

    private fun resolveManagedOutputParent(): OutputParent {
        val fileRoot = listOfNotNull(
            BackupFolderPrefsInternal.getLibraryRootUri(context),
            BackupFolderPrefs.getLibraryRootUri(context)
        ).firstOrNull { it.scheme == "file" }

        if (fileRoot != null) {
            val rootDir = File(fileRoot.path ?: "").takeIf { it.path.isNotBlank() }
                ?: InternalStoragePaths.ensureSplRoot(context)
            val backingTracksDir = File(rootDir, "BackingTracks").apply { mkdirs() }
            return OutputParent.FileParent(backingTracksDir)
        }

        val backingTracksDir = resolveWritableSafBackingTracksDir()
            ?: throw IOException("BackingTracks SAF introuvable pour écriture SMP")
        return OutputParent.SafParent(backingTracksDir)
    }

    private fun resolvePreferredFileOutputDir(sourceFile: File): File {
        val parentDir = sourceFile.parentFile ?: throw IOException("Dossier parent introuvable pour $sourceFile")
        val backingTracksDir = parentDir.parentFile
        val isAudioFolder =
            parentDir.name.equals("Audio", ignoreCase = true) ||
                parentDir.name.equals("audio", ignoreCase = true)

        if (isAudioFolder && backingTracksDir != null &&
            (backingTracksDir.name.equals("BackingTracks", ignoreCase = true) ||
                backingTracksDir.name.equals("BackingTrack", ignoreCase = true))
        ) {
            if (!backingTracksDir.exists() && !backingTracksDir.mkdirs()) {
                throw IOException("Création du dossier BackingTracks impossible: ${backingTracksDir.absolutePath}")
            }
            return backingTracksDir
        }

        return parentDir
    }

    private fun resolvePreferredSafOutputDir(sourceUri: Uri): DocumentFile {
        val parentDir = resolveSafAncestorDirectory(sourceUri, 1)
            ?: throw IOException("Parent SAF introuvable pour $sourceUri")
        val grandParentDir = resolveSafAncestorDirectory(sourceUri, 2)
        val parentName = resolveDocumentName(parentDir)
        val grandParentName = resolveDocumentName(grandParentDir)

        val isAudioFolder =
            parentName.equals("Audio", ignoreCase = true) ||
                parentName.equals("audio", ignoreCase = true)
        val isBackingTracksFolder =
            grandParentName.equals("BackingTracks", ignoreCase = true) ||
                grandParentName.equals("BackingTrack", ignoreCase = true)
        var writableBackingTracksUri: Uri? = null

        val chosenOutputParent = if (isAudioFolder && grandParentDir != null && isBackingTracksFolder) {
            val writableBackingTracks = resolveWritableSafBackingTracksDir()
            writableBackingTracksUri = writableBackingTracks?.uri
            writableBackingTracks ?: grandParentDir
        } else {
            parentDir
        }

        Log.i(
            TAG,
            "step=resolve_saf_output sourceUri=$sourceUri parentUri=${parentDir.uri} parentName=$parentName parentIsDir=${parentDir.isDirectory} grandParentUri=${grandParentDir?.uri} grandParentName=$grandParentName grandParentIsDir=${grandParentDir?.isDirectory} isAudioFolder=$isAudioFolder isBackingTracksFolder=$isBackingTracksFolder writableBackingTracks=$writableBackingTracksUri chosenOutputParent=${chosenOutputParent.uri} chosenOutputParentName=${resolveDocumentName(chosenOutputParent)}"
        )

        return chosenOutputParent
    }

    private fun resolveWritableSafBackingTracksDir(): DocumentFile? {
        val splRoot = resolveWritableSafSplRootDir() ?: return null
        return splRoot.findFile("BackingTracks")
            ?: splRoot.findFile("BackingTrack")
            ?: splRoot.createDirectory("BackingTracks")
    }

    private fun resolveWritableSafSplRootDir(): DocumentFile? {
        val candidates = listOfNotNull(
            BackupFolderPrefsSaf.getLibraryRootUri(context),
            BackupFolderPrefs.getLibraryRootUri(context),
            BackupFolderPrefsSaf.getSetupTreeUri(context),
            BackupFolderPrefs.getSetupTreeUri(context)
        )

        candidates.forEach { candidateUri ->
            val rootDoc = resolveWritableSafDirectory(candidateUri) ?: return@forEach
            if (resolveDocumentName(rootDoc).equals("SPL_Music", ignoreCase = true)) {
                return rootDoc
            }

            val splMusic = rootDoc.findFile("SPL_Music")
                ?: rootDoc.findFile("spl_music")
            if (splMusic?.isDirectory == true) {
                return splMusic
            }
        }

        return null
    }

    private fun resolveWritableSafDirectory(rootUri: Uri): DocumentFile? {
        val directTree = DocumentFile.fromTreeUri(context, rootUri)
        if (directTree?.isDirectory == true) return directTree

        val normalizedTreeUri = normalizeAsTreeUri(rootUri)
        val normalizedTree = normalizedTreeUri?.let { DocumentFile.fromTreeUri(context, it) }
        if (normalizedTree?.isDirectory == true) return normalizedTree

        val single = DocumentFile.fromSingleUri(context, rootUri)
        if (single?.isDirectory == true) return single

        return null
    }

    private fun normalizeAsTreeUri(uri: Uri): Uri? {
        val authority = uri.authority ?: return null
        val treeId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
            ?: runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
            ?: return null
        return DocumentsContract.buildTreeDocumentUri(authority, treeId)
    }

    private fun resolveSafAncestorDirectory(sourceUri: Uri, levelsUp: Int): DocumentFile? {
        val docId = DocumentsContract.getDocumentId(sourceUri)
        var currentDocId = docId
        repeat(levelsUp) {
            val slash = currentDocId.lastIndexOf('/')
            if (slash <= 0) {
                return null
            }
            currentDocId = currentDocId.substring(0, slash)
        }
        val authority = sourceUri.authority ?: return null
        val ancestorTreeUri = DocumentsContract.buildTreeDocumentUri(authority, currentDocId)
        return DocumentFile.fromTreeUri(context, ancestorTreeUri)
    }

    private fun findExistingOutput(outputParent: OutputParent, outputName: String): Uri? {
        return when (outputParent) {
            is OutputParent.FileParent -> {
                File(outputParent.directory, outputName)
                    .takeIf { it.exists() }
                    ?.let(Uri::fromFile)
            }

            is OutputParent.SafParent -> {
                outputParent.directory.listFiles()
                    .firstOrNull { it.isFile && it.name.equals(outputName, ignoreCase = true) }
                    ?.uri
            }
        }
    }

    private fun writeArchiveToFileParent(
        outputDir: File,
        outputName: String,
        archiveFile: File
    ): Uri {
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw IOException("Dossier sortie introuvable: ${outputDir.absolutePath}")
        }
        val outputFile = File(outputDir, outputName)
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
        outputDir: DocumentFile,
        outputName: String,
        archiveFile: File
    ): Uri {
        val outputUri = DocumentsContract.createDocument(
            context.contentResolver,
            outputDir.uri,
            OUTPUT_MIME,
            outputName
        ) ?: throw IOException("Création du fichier SMP impossible dans ${outputDir.uri}")

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

        val treeDocId = runCatching { DocumentsContract.getTreeDocumentId(fileUri) }.getOrNull()
        if (treeDocId == null) {
            Log.i(TAG, "Sibling lookup ignoré: URI SAF non-tree uri=$fileUri")
            return emptyList()
        }

        val docId = runCatching { DocumentsContract.getDocumentId(fileUri) }.getOrNull()
            ?: return emptyList()
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
        audioEntryName: String,
        lyricsFile: File?,
        configFile: File
    ) {
        ZipOutputStream(FileOutputStream(targetFile).buffered()).use { zipOutput ->
            writeFileEntry(zipOutput, audioFile, audioEntryName)
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
        audioEntryName: String,
        hasLyrics: Boolean
    ): String {
        return JSONObject().apply {
            put("version", 1)
            put("id", songId)
            put("title", title)
            put("audio", audioEntryName)
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

    private fun isSupportedAudioFileName(name: String): Boolean {
        return audioExtensionFor(name) != null
    }

    private fun buildAudioEntryName(sourceName: String): String {
        val extension = audioExtensionFor(sourceName)
            ?: throw IOException("Extension audio non supportée: $sourceName")
        return "audio.$extension"
    }

    private fun audioExtensionFor(name: String): String? {
        val lower = name.trim().lowercase(Locale.ROOT)
        return when {
            lower.endsWith(".mp3") -> "mp3"
            lower.endsWith(".wav") -> "wav"
            lower.endsWith(".wave") -> "wave"
            else -> null
        }
    }
}
