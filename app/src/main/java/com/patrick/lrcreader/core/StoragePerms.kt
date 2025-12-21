package com.patrick.lrcreader.core

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Libère les permissions persistantes SAF liées à la racine Music (BackupFolderPrefs)
 * + toutes les permissions persistantes qui sont "sous" cette racine.
 *
 * Important : à appeler AVANT BackupFolderPrefs.clear(context)
 */
fun clearPersistedUris(context: Context) {
    val root: Uri = BackupFolderPrefs.get(context) ?: return

    val resolver = context.contentResolver
    val persisted = resolver.persistedUriPermissions

    // On retire READ/WRITE si possible
    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    // rootStr sert à filtrer les sous-dossiers persistés du même tree
    val rootStr = root.toString()

    persisted.forEach { p ->
        val u = p.uri.toString()

        // On libère :
        // - la racine exactement
        // - et les Uri persistées qui appartiennent au même tree (sous-dossiers)
        //   (souvent sous forme .../tree/.../document/...)
        val isSameTree = u.startsWith(rootStr) || (u.contains("/tree/") && rootStr.contains("/tree/") &&
                u.substringBefore("/document/") == rootStr.substringBefore("/document/"))

        if (isSameTree) {
            try {
                resolver.releasePersistableUriPermission(p.uri, flags)
            } catch (_: SecurityException) {
                // déjà libérée ou pas libérable -> on ignore
            } catch (_: Exception) {
            }
        }
    }
}