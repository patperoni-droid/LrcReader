package com.patrick.lrcreader.core

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import org.json.JSONArray

object DjFolderPrefs {
    private const val PREF = "dj_folder_prefs"

    // ancien champ (on le garde pour compat)
    private const val KEY_URI = "dj_folder_uri"

    // nouveaux champs
    private const val KEY_URIS = "dj_folder_uris"       // JSON array de strings
    private const val KEY_CURRENT = "dj_folder_current" // string

    // ✅ nouveau flag : scan DJ déjà fait ?
    private const val KEY_DJ_SCANNED = "dj_scanned"

    /**
     * Ajoute (ou remplace) un dossier DJ et le met courant.
     * Compatible avec ton ancien code qui faisait juste save(context, uri)
     */
    fun save(context: Context, uri: Uri) {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val all = getAllInternal(prefs).toMutableList()

        // on évite les doublons
        val asString = uri.toString()
        if (all.none { it == asString }) {
            all.add(asString)
        }

        prefs.edit()
            // on garde l’ancien champ pour ne rien casser
            .putString(KEY_URI, asString)
            // nouveaux champs
            .putString(KEY_URIS, toJsonArray(all).toString())
            .putString(KEY_CURRENT, asString)
            // ✅ nouveau dossier => scan requis
            .putBoolean(KEY_DJ_SCANNED, false)
            .apply()
    }

    /**
     * Ancienne méthode : renvoie le dossier courant si on en a un.
     * Ça évite de casser le code existant.
     */
    fun get(context: Context): Uri? {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

        // priorité au nouveau champ courant
        val current = prefs.getString(KEY_CURRENT, null)
        if (current != null) return Uri.parse(current)

        // sinon on retombe sur l’ancien champ
        val old = prefs.getString(KEY_URI, null)
        return old?.let { Uri.parse(it) }
    }

    /**
     * URI à utiliser pour les écrans picker:
     * - préfère un treeUri lisible si disponible
     * - sinon garde l'URI stockée telle quelle (documentUri inclus)
     */
    fun getResolvedUriForPicker(context: Context): Uri? {
        val raw = get(context) ?: return null
        if (raw.scheme != "content") return raw

        val isTree = isTreeUriCompat(raw)
        if (isTree && hasReadableTreeAccess(context, raw)) return raw

        val candidateTree = runCatching {
            val authority = raw.authority ?: return@runCatching null
            val docId = DocumentsContract.getDocumentId(raw)
            DocumentsContract.buildTreeDocumentUri(authority, docId)
        }.getOrNull()

        return if (candidateTree != null && hasReadableTreeAccess(context, candidateTree)) {
            candidateTree
        } else {
            raw
        }
    }

    /**
     * Renvoie tous les dossiers DJ déjà autorisés.
     */
    fun getAll(context: Context): List<Uri> {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        return getAllInternal(prefs).map { Uri.parse(it) }
    }

    /**
     * Change juste le dossier courant parmi ceux déjà enregistrés.
     * ✅ si on change de dossier : on force "scan requis"
     */
    fun setCurrent(context: Context, uri: Uri) {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_CURRENT, uri.toString())
            .putBoolean(KEY_DJ_SCANNED, false)
            .apply()
    }

    /** ✅ Le dossier DJ courant a déjà été scanné ? */
    fun isScanned(context: Context): Boolean {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getBoolean(KEY_DJ_SCANNED, false)
    }

    /** ✅ Marque “scanné” (après un scan réussi) */
    fun setScanned(context: Context, scanned: Boolean) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DJ_SCANNED, scanned)
            .apply()
    }

    /**
     * Oublie tout.
     */
    fun clear(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_URI)
            .remove(KEY_URIS)
            .remove(KEY_CURRENT)
            .remove(KEY_DJ_SCANNED)
            .apply()
    }

    // ----------------- helpers privés -----------------

    private fun getAllInternal(prefs: android.content.SharedPreferences): List<String> {
        val json = prefs.getString(KEY_URIS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val s = arr.optString(i, null)
                    if (!s.isNullOrEmpty()) add(s)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun toJsonArray(list: List<String>): JSONArray {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        return arr
    }

    private fun isTreeUriCompat(uri: Uri): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            DocumentsContract.isTreeUri(uri)
        } else {
            (uri.path ?: "").contains("/tree/")
        }
    }

    private fun hasReadableTreeAccess(context: Context, targetTreeUri: Uri): Boolean {
        val targetAuthority = targetTreeUri.authority ?: return false
        val targetTreeId = runCatching { DocumentsContract.getTreeDocumentId(targetTreeUri) }.getOrNull()
            ?: return false

        return context.contentResolver.persistedUriPermissions.any { p ->
            if (!p.isReadPermission) return@any false
            if (p.uri.authority != targetAuthority) return@any false
            val permTreeId = runCatching { DocumentsContract.getTreeDocumentId(p.uri) }.getOrNull()
                ?: return@any false
            targetTreeId == permTreeId || targetTreeId.startsWith("$permTreeId/")
        }
    }
}
