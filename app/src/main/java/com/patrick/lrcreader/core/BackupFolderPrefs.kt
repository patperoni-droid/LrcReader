package com.patrick.lrcreader.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile

/**
 * Stocke le dossier choisi via OpenDocumentTree.
 *
 * - KEY_URI : actuellement tu stockes l'URI du dossier Backups (…/SPL_Music/Backups)
 * - KEY_SETUP_TREE_URI : le TreeUri de setup (le vrai OpenDocumentTree), indispensable pour importer ensuite.
 * - KEY_DONE : flag setup_done
 */
object BackupFolderPrefs {

    private const val PREFS_NAME = "backup_folder_prefs"

    // actuellement : BackupsUri (…/SPL_Music/Backups)
    private const val KEY_URI = "folder_uri"
    private const val KEY_DONE = "setup_done"

    // ✅ NOUVEAU : TreeUri choisi au setup (OpenDocumentTree)
    private const val KEY_SETUP_TREE_URI = "setup_tree_uri"
    private const val KEY_LIBRARY_ROOT_URI = "library_root_uri"

    /** Sauve juste l'URI (sans tenter de prendre la permission). */
    fun save(context: Context, uri: Uri) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_URI, uri.toString())
            .apply()
    }

    /**
     * Variante "safe" : tente de prendre la permission persistante puis sauvegarde l'URI.
     * À appeler juste après OpenDocumentTree.
     */
    fun persistAndSave(context: Context, uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: Exception) {
            // si ça échoue, on sauvegarde quand même (mais l'accès pourra échouer plus tard)
        }
        save(context, uri)
    }

    fun get(context: Context): Uri? {
        val s = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_URI, null) ?: return null
        return Uri.parse(s)
    }

    // --------------------------------------------------------------------
    // ✅ SETUP TREE URI (OpenDocumentTree)
    // --------------------------------------------------------------------

    /**
     * Sauve le TreeUri de setup ET prend la permission persistante.
     * C'est indispensable pour importer ensuite des fichiers vers SPL_Music.
     */
    fun saveSetupTreeUri(context: Context, treeUri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: Exception) {
            // idem : on stocke quand même
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SETUP_TREE_URI, treeUri.toString())
            .apply()
    }

    fun getSetupTreeUri(context: Context): Uri? {
        val s = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SETUP_TREE_URI, null) ?: return null
        return Uri.parse(s)
    }
    fun saveLibraryRootUri(context: Context, uri: Uri) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LIBRARY_ROOT_URI, uri.toString())
            .apply()
    }

    fun getLibraryRootUri(context: Context): Uri? {
        val s = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LIBRARY_ROOT_URI, null) ?: return null
        return Uri.parse(s)
    }

    fun hasValidLibraryPermission(context: Context): Boolean {
        val lib = getLibraryRootUri(context) ?: return false
        return context.contentResolver.persistedUriPermissions.any { p ->
            p.uri == lib && p.isReadPermission
        }
    }
    /**
     * ✅ SAF : on considère un root "utilisable" seulement si :
     * - on a une permission persistée (au moins READ)
     * - et DocumentFile peut être résolu (tree ou single)
     */
    fun isLibraryRootUsable(context: Context, rootUri: Uri?): Boolean {
        if (rootUri == null) return false

        // 1) Permission persistée (au moins READ)
        val hasReadPersisted = context.contentResolver.persistedUriPermissions.any { p ->
            p.uri == rootUri && p.isReadPermission
        }
        if (!hasReadPersisted) return false

        // 2) Root résolvable
        val doc = DocumentFile.fromTreeUri(context, rootUri) ?: DocumentFile.fromSingleUri(context, rootUri)
        if (doc == null) return false

        // 3) Doit être un dossier (root)
        if (!doc.isDirectory) return false

        return true
    }
    /**
     * ✅ Normalise une Uri en "treeUri" stable (authority + treeDocId).
     * Beaucoup de providers SAF donnent des Uri différentes (tree vs doc) même pour le même dossier.
     */
    private fun normalizeAsTreeUri(uri: Uri): Uri? {
        val authority = uri.authority ?: return null

        val treeId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
            ?: runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
            ?: return null

        return DocumentsContract.buildTreeDocumentUri(authority, treeId)
    }

    /**
     * ✅ Setup SAF valide si on a AU MOINS la permission READ persistée
     * sur la même cible (comparaison sur treeUri normalisée).
     *
     * (Ancien comportement trop strict: match URI exact + READ+WRITE => boucle setup)
     */
    fun hasValidSetupTreePermission(context: Context): Boolean {
        val saved = getSetupTreeUri(context) ?: return false

        val savedNorm = normalizeAsTreeUri(saved) ?: saved

        return context.contentResolver.persistedUriPermissions.any { p ->
            val pNorm = normalizeAsTreeUri(p.uri) ?: p.uri
            (pNorm == savedNorm) && p.isReadPermission
        }
    }
    /**
     * ✅ Test robuste : est-ce que le TreeUri est dans "Documents" ?
     * On se base sur le TreeDocumentId (ex: "primary:Documents" ou "primary:Documents/...")
     * Beaucoup plus fiable que DocumentFile.name (qui peut varier selon ROM/langue).
     */
    fun isSetupTreeInDocuments(treeUri: Uri): Boolean {
        return try {
            val docId = DocumentsContract.getTreeDocumentId(treeUri) ?: return false
            docId.contains(":Documents", ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }
    // --------------------------------------------------------------------

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_URI)
            .remove(KEY_SETUP_TREE_URI) // ✅ important
            .apply()
    }

    fun setDone(context: Context, done: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DONE, done)
            .apply()
    }

    fun isDone(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_DONE, false)
    }

    fun clearAll(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

}