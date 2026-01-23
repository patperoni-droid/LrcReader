package com.patrick.lrcreader.core

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import java.io.FileNotFoundException

object ImportAudioManager {

    data class Result(
        val copiedCount: Int,
        val skippedCount: Int,
        val errors: List<String>
    )

    /**
     * Importe des fichiers audio (URIs SAF) vers :
     *   <appRootParent>/SPL_Music/<destFolderName>/
     *
     * ✅ appRootTreeUri doit être le TreeUri choisi au setup (celui stocké dans BackupFolderPrefs),
     * c.-à-d. le DOSSIER PARENT dans lequel on crée SPL_Music.
     */
    fun importAudioFiles(
        context: Context,
        appRootTreeUri: Uri,
        sourceUris: List<Uri>,
        destFolderName: String = "BackingTracks", // ou "DJ"
        overwriteIfExists: Boolean = false,
        destFolderUri: Uri? = null               // ✅ NOUVEAU
    ): Result {

        val errors = mutableListOf<String>()
        var copied = 0
        var skipped = 0

        // 1) Racine parent (celle choisie au setup)
        val rootParent = DocumentFile.fromTreeUri(context, appRootTreeUri)
        if (rootParent == null || !rootParent.isDirectory) {
            return Result(0, 0, listOf("Dossier racine invalide (permission manquante ?)"))
        }
        // ✅ Mode "import ici" : si un dossier cible est fourni, on copie directement dedans
        if (destFolderUri != null) {
            val destDir = (DocumentFile.fromTreeUri(context, destFolderUri)
                ?: DocumentFile.fromSingleUri(context, destFolderUri))

            if (destDir == null || !destDir.isDirectory) {
                return Result(0, 0, listOf("Dossier destination invalide"))
            }

            return importIntoDir(
                context = context,
                destDir = destDir,
                sourceUris = sourceUris,
                overwriteIfExists = overwriteIfExists
            )
        }

        // 2) SPL_Music sous la racine
        // 2) SPL_Music sous la racine (⚠️ si rootParent est déjà SPL_Music, on ne recrée pas)
        val splMusicDir = if ((rootParent.name ?: "").equals("SPL_Music", ignoreCase = true)) {
            rootParent
        } else {
            ensureDir(rootParent, "SPL_Music")
        } ?: return Result(0, 0, listOf("Impossible de créer/ouvrir SPL_Music"))

        // 3) Dossier destination (backingtracks / dj) sous SPL_Music
        val destDir = when (destFolderName.lowercase()) {
            "backingtracks" -> (
                    ensureDir(splMusicDir, "BackingTracks")
                        ?: ensureDir(splMusicDir, "BackingTrack")
                    )
            else -> ensureDir(splMusicDir, destFolderName)
        } ?: return Result(0, 0, listOf("Impossible de créer/ouvrir $destFolderName"))
        sourceUris.forEach { srcUri ->
            try {
                val srcName = guessDisplayName(context, srcUri)
                if (srcName.isNullOrBlank()) {
                    skipped++
                    errors.add("Nom de fichier introuvable pour: $srcUri")
                    return@forEach
                }

                // Filtre audio basique par extension (simple + fiable)
                if (!looksLikeAudio(srcName)) {
                    skipped++
                    return@forEach
                }

                val finalName = if (overwriteIfExists) {
                    srcName
                } else {
                    uniqueName(destDir, srcName)
                }

                // Si overwrite=true et fichier existe : on le supprime d'abord (SAF)
                if (overwriteIfExists) {
                    destDir.findFile(srcName)?.delete()
                }

                val mime = context.contentResolver.getType(srcUri) ?: mimeFromName(srcName)
                val outFile = destDir.createFile(mime, finalName)
                    ?: throw FileNotFoundException("createFile a échoué pour: $finalName")

                context.contentResolver.openInputStream(srcUri).use { input ->
                    if (input == null) throw FileNotFoundException("openInputStream null: $srcUri")

                    context.contentResolver.openOutputStream(outFile.uri, "w").use { output ->
                        if (output == null) throw FileNotFoundException("openOutputStream null: ${outFile.uri}")

                        val buffer = ByteArray(256 * 1024)
                        while (true) {
                            val r = input.read(buffer)
                            if (r <= 0) break
                            output.write(buffer, 0, r)
                        }
                        output.flush()
                    }
                }

                copied++
            } catch (e: Exception) {
                skipped++
                errors.add("Erreur import ${srcUri}: ${e.javaClass.simpleName} ${e.message ?: ""}".trim())
            }
        }

        return Result(copied, skipped, errors)
    }
    private fun importIntoDir(
        context: Context,
        destDir: DocumentFile,
        sourceUris: List<Uri>,
        overwriteIfExists: Boolean
    ): Result {
        val errors = mutableListOf<String>()
        var copied = 0
        var skipped = 0

        sourceUris.forEach { srcUri ->
            try {
                val srcName = guessDisplayName(context, srcUri)
                if (srcName.isNullOrBlank()) {
                    skipped++
                    errors.add("Nom de fichier introuvable pour: $srcUri")
                    return@forEach
                }

                if (!looksLikeAudio(srcName)) {
                    skipped++
                    return@forEach
                }

                val finalName = if (overwriteIfExists) srcName else uniqueName(destDir, srcName)

                if (overwriteIfExists) {
                    destDir.findFile(srcName)?.delete()
                }

                val mime = context.contentResolver.getType(srcUri) ?: mimeFromName(srcName)
                val outFile = destDir.createFile(mime, finalName)
                    ?: throw FileNotFoundException("createFile a échoué pour: $finalName")

                context.contentResolver.openInputStream(srcUri).use { input ->
                    if (input == null) throw FileNotFoundException("openInputStream null: $srcUri")

                    context.contentResolver.openOutputStream(outFile.uri, "w").use { output ->
                        if (output == null) throw FileNotFoundException("openOutputStream null: ${outFile.uri}")

                        val buffer = ByteArray(256 * 1024)
                        while (true) {
                            val r = input.read(buffer)
                            if (r <= 0) break
                            output.write(buffer, 0, r)
                        }
                        output.flush()
                    }
                }

                copied++
            } catch (e: Exception) {
                skipped++
                errors.add("Erreur import ${srcUri}: ${e.javaClass.simpleName} ${e.message ?: ""}".trim())
            }
        }

        return Result(copied, skipped, errors)
    }
    // ----------------- Helpers -----------------

    private fun ensureDir(parent: DocumentFile, name: String): DocumentFile? {
        val parts = name.split("/").filter { it.isNotBlank() }
        var cur: DocumentFile = parent

        for (p in parts) {

            // Cherche enfant (fichier ou dossier) en ignorant la casse
            val existingAny = cur.listFiles()
                .firstOrNull { (it.name ?: "").equals(p, ignoreCase = true) }

            cur = when {
                existingAny != null && existingAny.isDirectory -> existingAny
                existingAny != null && !existingAny.isDirectory -> return null
                else -> cur.createDirectory(p) ?: return null
            }
        }

        // ✅ IL MANQUAIT CETTE LIGNE
        return cur
    }

    private fun looksLikeAudio(name: String): Boolean {
        val n = name.lowercase()
        return n.endsWith(".mp3") || n.endsWith(".wav") || n.endsWith(".flac") ||
                n.endsWith(".m4a") || n.endsWith(".aac") || n.endsWith(".ogg")
    }

    private fun mimeFromName(name: String): String {
        val n = name.lowercase()
        return when {
            n.endsWith(".mp3") -> "audio/mpeg"
            n.endsWith(".wav") -> "audio/wav"
            n.endsWith(".flac") -> "audio/flac"
            n.endsWith(".m4a") -> "audio/mp4"
            n.endsWith(".aac") -> "audio/aac"
            n.endsWith(".ogg") -> "audio/ogg"
            else -> "application/octet-stream"
        }
    }

    private fun uniqueName(destDir: DocumentFile, desired: String): String {
        if (destDir.findFile(desired) == null) return desired

        val dot = desired.lastIndexOf('.')
        val base = if (dot > 0) desired.substring(0, dot) else desired
        val ext = if (dot > 0) desired.substring(dot) else ""

        var i = 1
        while (true) {
            val candidate = "$base ($i)$ext"
            if (destDir.findFile(candidate) == null) return candidate
            i++
        }
    }

    private fun guessDisplayName(context: Context, uri: Uri): String? {
        // 1) DocumentFile (souvent OK)
        val df = runCatching { DocumentFile.fromSingleUri(context, uri) }.getOrNull()
        val name1 = df?.name
        if (!name1.isNullOrBlank()) return name1

        // 2) ContentResolver query (OpenableColumns)
        var cursor: Cursor? = null
        return try {
            cursor = context.contentResolver.query(uri, null, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) cursor.getString(idx) else null
            } else null
        } catch (_: Exception) {
            null
        } finally {
            try { cursor?.close() } catch (_: Exception) {}
        }
    }
    fun ensureSplSubFolder(
        context: Context,
        appRootTreeUri: Uri,
        subPath: String
    ): Uri? {
        val rootParent = DocumentFile.fromTreeUri(context, appRootTreeUri) ?: return null
        if (!rootParent.isDirectory) return null

        // ✅ on réutilise LE ensureDir() DU HAUT DU FICHIER
        val dir = ensureDir(rootParent, subPath) ?: return null
        return dir.uri

    }
    fun importAudioFilesToFolder(
        context: Context,
        destFolderUri: Uri,
        sourceUris: List<Uri>,
        overwriteIfExists: Boolean = false
    ): Result {
        val destDir = DocumentFile.fromTreeUri(context, destFolderUri)
            ?: DocumentFile.fromSingleUri(context, destFolderUri)
            ?: return Result(0, 0, listOf("Dossier destination invalide"))

        if (!destDir.isDirectory) return Result(0, 0, listOf("Dossier destination invalide"))

        return importIntoDir(
            context = context,
            destDir = destDir,
            sourceUris = sourceUris,
            overwriteIfExists = overwriteIfExists
        )
    }
}

