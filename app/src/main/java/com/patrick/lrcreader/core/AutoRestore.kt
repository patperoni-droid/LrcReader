package com.patrick.lrcreader.core

import android.content.Context
import android.net.Uri
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

    fun restoreIfNeeded(context: Context) {
        if (!ENABLED) return

        // 1) dossier configuré ?
        val folderUri: Uri = BackupFolderPrefs.get(context) ?: return

        val docTree = DocumentFile.fromTreeUri(context, folderUri) ?: return

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

        if (targetFile == null) return

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
    }
}