package com.patrick.lrcreader.core

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.documentfile.provider.DocumentFile

/**
 * Restauration automatique au démarrage.
 *
 * Principe :
 * - on regarde si un dossier de sauvegarde a été choisi (BackupFolderPrefs)
 * - on cherche d'abord "lrc_backup.json"
 * - sinon on prend le premier .json trouvé dans ce dossier
 * - si on trouve, on lit et on envoie à BackupManager.importState(...)
 */
object AutoRestore {

    // si tu veux désactiver facilement plus tard
    private const val ENABLED = true
    private const val TAG = "BOOTSTEP"

    private fun resolveBackupDir(context: Context): DocumentFile? {
        val picked = BackupFolderPrefs.get(context)
        if (picked != null) {
            val pickedTree = DocumentFile.fromTreeUri(context, picked)
                ?: DocumentFile.fromSingleUri(context, picked)
            if (pickedTree != null) return pickedTree
        }

        val root = BackupFolderPrefs.getLibraryRootUri(context) ?: return null
        val rootDoc = DocumentFile.fromTreeUri(context, root)
            ?: DocumentFile.fromSingleUri(context, root)
            ?: return null

        val backups = rootDoc.findFile("Backups")
            ?: rootDoc.findFile("backups")

        return backups?.takeIf { it.isDirectory } ?: rootDoc
    }

    fun restoreIfNeeded(context: Context) {
        if (!ENABLED) return
        val t0 = SystemClock.elapsedRealtime()

        // 1) dossier configuré ? (folder_uri prioritaire, fallback libraryRoot/Backups)
        val docTree = resolveBackupDir(context)
        if (docTree == null) {
            Log.d(TAG, "AutoRestore.resolveBackupDir:null")
            return
        }

        // 2) on essaie d'abord le nom “officiel”
        val preferredName = "lrc_backup.json"
        val preferredFile = docTree.findFile(preferredName)

        val targetFile: DocumentFile? = when {
            preferredFile != null && preferredFile.isFile -> preferredFile
            else -> {
                // 3) sinon on prend le premier .json
                docTree.listFiles()
                    .firstOrNull { it.isFile && (it.name ?: "").endsWith(".json", ignoreCase = true) }
            }
        }

        if (targetFile == null) {
            Log.d(TAG, "AutoRestore.noBackupJson dir=${docTree.uri}")
            return
        }

        // 4) on lit le contenu
        val json = context.contentResolver.openInputStream(targetFile.uri)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: return

        if (json.isBlank()) return

        // 5) on importe
        BackupManager.importState(context, json) {
            // ici tu peux loguer ou afficher un toast si tu veux
            // mais tu avais demandé que ce soit silencieux
        }
        Log.d(
            TAG,
            "AutoRestore.done file=${targetFile.name} ms=${SystemClock.elapsedRealtime() - t0}"
        )
    }
}
