package com.patrick.lrcreader.core

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Stocke le dossier choisi via OpenDocumentTree pour les sauvegardes.
 * IMPORTANT : on garde aussi des flags utiles (setup_done).
 */
object BackupFolderPrefs {

    private const val PREFS_NAME = "backup_folder_prefs"
    private const val KEY_URI = "folder_uri"
    private const val KEY_DONE = "setup_done"

    /**
     * Sauve juste l'URI (si la permission persistante a déjà été prise ailleurs).
     */
    fun save(context: Context, uri: Uri) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_URI, uri.toString())
            .apply()
    }

    /**
     * Variante "safe" : tente de prendre la permission persistante puis sauvegarde l'URI.
     * À appeler idéalement juste après le retour du picker DocumentTree.
     */
    fun persistAndSave(context: Context, uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: Exception) {
            // Si ça échoue, on sauvegarde quand même l'URI (mais l'écriture pourra échouer plus tard).
        }
        save(context, uri)
    }

    fun get(context: Context): Uri? {
        val s = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_URI, null) ?: return null
        return Uri.parse(s)
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_URI)
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