package com.patrick.lrcreader.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLDecoder

/**
 * Gère l’export / import de l’état de l’appli
 * (playlists + chansons jouées + dernier morceau + fond sonore + réglages d’édition
 *  + titres à revoir + couleurs de playlists)
 */
object BackupManager {

    private const val IMPORT_LOG_TAG = "BACKUP_IMPORT"

    data class LastPlayed(
        val uri: String,
        val playlistName: String?,
        val positionMs: Long
    )

    fun exportState(
        context: Context,
        lastPlayer: LastPlayed?,          // peut être null
        libraryFolders: List<String>
    ): String {
        val root = JSONObject()

        // 1) playlists : ordre complet des titres
        val playlistsJson = JSONObject()
        PlaylistRepository.getPlaylists().forEach { plName ->
            val songs = PlaylistRepository.getAllSongsRaw(plName)
            playlistsJson.put(plName, JSONArray(songs))
        }
        root.put("playlists", playlistsJson)

        // 2) songs joués
        val playedJson = JSONObject()
        PlaylistRepository.getPlaylists().forEach { plName ->
            val played = PlaylistRepository.getPlayedRaw(plName)
            playedJson.put(plName, JSONArray(played))
        }
        root.put("played", playedJson)

        // 3) dossiers
        root.put("libraryFolders", JSONArray(libraryFolders))

        // 4) dernier morceau
        if (lastPlayer != null) {
            val lp = JSONObject().apply {
                put("uri", lastPlayer.uri)
                put("playlistName", lastPlayer.playlistName ?: JSONObject.NULL)
                put("positionMs", lastPlayer.positionMs)
            }
            root.put("lastPlayed", lp)
        }

        // 5) fond sonore
        run {
            val uri = FillerSoundPrefs.getFillerUri(context)
            val vol = FillerSoundPrefs.getFillerVolume(context)
            if (uri != null) {
                val fillerJson = JSONObject().apply {
                    put("uri", uri.toString())
                    put("volume", vol)
                }
                root.put("fillerSound", fillerJson)
            }
        }

        // 6) réglages d’édition
        run {
            val allEdits = EditPrefs.getAllEdits(context)
            if (allEdits.isNotEmpty()) {
                val editsJson = JSONObject()
                allEdits.forEach { (uriString, data) ->
                    val one = JSONObject().apply {
                        put("startMs", data.startMs)
                        put("endMs", data.endMs)
                    }
                    editsJson.put(uriString, one)
                }
                root.put("edits", editsJson)
            }
        }

        // 7) morceaux "à revoir"
        run {
            val reviewJson = JSONObject()
            PlaylistRepository.getPlaylists().forEach { plName ->
                val allSongs = PlaylistRepository.getAllSongsRaw(plName)
                val toReview = allSongs.filter { uri ->
                    PlaylistRepository.isSongToReview(plName, uri)
                }
                if (toReview.isNotEmpty()) {
                    reviewJson.put(plName, JSONArray(toReview))
                }
            }
            if (reviewJson.length() > 0) {
                root.put("review", reviewJson)
            }
        }

        // 8) couleurs de playlists
        run {
            val colorsJson = JSONObject()
            PlaylistRepository.getPlaylists().forEach { plName ->
                val colorLong = PlaylistRepository.getPlaylistColor(plName)
                colorsJson.put(plName, colorLong)
            }
            if (colorsJson.length() > 0) {
                root.put("colors", colorsJson)
            }
        }

        return root.toString(2)
    }

    // ------------------------------------------------------------
    // Helpers import : réparer les URIs de playlist si besoin
    // ------------------------------------------------------------

    private fun normalizeName(s: String): String {
        // Normalisation "souple" pour matcher des backups faits sur un autre device
        // - trim
        // - lowercase
        // - espaces multiples -> 1 espace
        // - on enlève les guillemets/espaces bizarres
        return s
            .trim()
            .lowercase()
            .replace('\u00A0', ' ') // espace insécable -> espace normal
            .replace(Regex("\\s+"), " ")
            .trim('"', '\'', ' ')
    }

    private fun nameWithoutExtension(name: String): String {
        val n = name.trim()
        val dot = n.lastIndexOf('.')
        return if (dot > 0) n.substring(0, dot) else n
    }

    private fun uriExists(context: Context, uriString: String): Boolean {
        if (uriString.isBlank()) return false
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return false

        return when (uri.scheme) {
            "file" -> {
                val path = uri.path ?: return false
                val f = File(path)
                f.exists() && f.isFile
            }
            "content" -> {
                // Plus fiable que openInputStream sur certains vieux Android
                runCatching {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
                }.getOrDefault(false)
            }
            else -> false
        }
    }

    private fun displayNameOf(context: Context, uriString: String): String? {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null

        // file://
        if (uri.scheme == "file") {
            return File(uri.path ?: return null).name
        }

        // content:// (SAF)
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )
        }.getOrNull()?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) return c.getString(idx)
        }

        // content:// fallback via documentId
        runCatching { DocumentsContract.getDocumentId(uri) }
            .getOrNull()
            ?.let { docId ->
                val fromDocId = docId.substringAfterLast('/').substringAfterLast(':').trim()
                if (fromDocId.isNotBlank()) return fromDocId
            }

        // fallback: dernier segment décodé
        val decoded = runCatching {
            URLDecoder.decode(uri.lastPathSegment ?: "", "UTF-8")
        }.getOrDefault(uri.lastPathSegment ?: "")
        val fromPath = decoded.substringAfterLast('/').substringAfterLast(':').trim()
        if (fromPath.isNotBlank()) return fromPath

        return uri.lastPathSegment
    }

    private fun buildLocalMapByName(context: Context): Map<String, String> {
        val map = LinkedHashMap<String, String>()

        fun addVariants(name: String?, uriStr: String) {
            val raw = name?.takeIf { it.isNotBlank() } ?: return

            // Variante 1 : nom complet
            val key1 = normalizeName(raw)
            if (key1.isNotBlank() && !map.containsKey(key1)) map[key1] = uriStr

            // Variante 2 : sans extension (très utile si backup stocke un titre sans ".mp3")
            val noExt = nameWithoutExtension(raw)
            val key2 = normalizeName(noExt)
            if (key2.isNotBlank() && !map.containsKey(key2)) map[key2] = uriStr
        }

        // ✅ table locale prioritaire: SPL_Music/BackingTracks/audio -> file://...
        run {
            val rootUri = BackupFolderPrefs.getLibraryRootUri(context)
            val rootFile = if (rootUri?.scheme == "file" && !rootUri.path.isNullOrBlank()) {
                File(rootUri.path!!)
            } else {
                File(context.getExternalFilesDir(null), "SPL_Music")
            }

            // ⚠️ chemin plus sûr que "BackingTracks/audio" en une string
            val audioDir = File(File(rootFile, "BackingTracks"), "audio")

            if (audioDir.exists() && audioDir.isDirectory) {
                audioDir.walkTopDown().forEach { f ->
                    if (f.isFile) addVariants(f.name, Uri.fromFile(f).toString())
                }
            }
            if (map.isNotEmpty()) return map
        }

        val rootUri = BackupFolderPrefs.getLibraryRootUri(context) ?: return emptyMap()

        // ✅ INTERNAL : scan filesystem
        if (rootUri.scheme == "file") {
            val rootDir = File(rootUri.path ?: return emptyMap())
            if (!rootDir.exists()) return emptyMap()

            fun walk(dir: File) {
                dir.listFiles()?.forEach { f ->
                    if (f.isDirectory) walk(f)
                    else addVariants(f.name, Uri.fromFile(f).toString())
                }
            }

            walk(rootDir)
            return map
        }

        // ✅ SAF : on essaye d’abord le cache (rapide)
        val cachedIndex = LibraryIndexCache.load(context) ?: emptyList()
        if (cachedIndex.isNotEmpty()) {
            cachedIndex.forEach { e ->
                if (!e.isDirectory) addVariants(e.name, e.uriString)
            }
            if (map.isNotEmpty()) return map
        }

        // ✅ SAF fallback : scan DocumentFile tree (plus lent, mais fiable)
        val rootDoc = DocumentFile.fromTreeUri(context, rootUri)
            ?: DocumentFile.fromSingleUri(context, rootUri)
            ?: return emptyMap()

        fun walk(doc: DocumentFile) {
            doc.listFiles().forEach { child ->
                if (child.isDirectory) walk(child)
                else addVariants(child.name, child.uri.toString())
            }
        }

        walk(rootDoc)
        return map
    }

    private data class UriFixStats(
        var kept: Int = 0,
        var remapped: Int = 0,
        var unresolved: Int = 0
    )

    private fun mapUriIfNeeded(
        context: Context,
        localByName: Map<String, String>,
        oldUri: String,
        backupName: String? = null,
        stats: UriFixStats? = null
    ): String {
        // 1) si l’uri marche déjà → on garde (téléphone concert = intouchable)
        if (oldUri.isNotBlank() && uriExists(context, oldUri)) {
            stats?.kept = (stats.kept + 1)
            Log.d(IMPORT_LOG_TAG, "URI kept old=$oldUri")
            return oldUri
        }

        // 2) sinon on tente par nom de fichier
        val candidates = linkedSetOf<String>()

        // a) nom fourni par le backup (si présent)
        if (!backupName.isNullOrBlank()) {
            candidates += backupName
            candidates += nameWithoutExtension(backupName)
        }

        // b) displayName déduit de l'ancienne URI (souvent un content:// mort d'un autre device)
        displayNameOf(context, oldUri)?.let { dn ->
            if (dn.isNotBlank()) {
                candidates += dn
                candidates += nameWithoutExtension(dn)
            }
        }

        for (candidate in candidates) {
            val resolved = localByName[normalizeName(candidate)]
            if (!resolved.isNullOrBlank() && uriExists(context, resolved)) {
                stats?.remapped = (stats.remapped + 1)
                Log.d(IMPORT_LOG_TAG, "URI remapped old=$oldUri new=$resolved by=$candidate")
                return resolved
            }
        }

        // 3) sinon on rend l’original (on ne casse pas le backup)
        stats?.unresolved = (stats.unresolved + 1)
        Log.w(IMPORT_LOG_TAG, "URI unresolved old=$oldUri candidates=$candidates")
        return oldUri
    }

    fun importState(
        context: Context,
        json: String,
        onLastPlayed: (LastPlayed?) -> Unit = {}
    ) {
        val root = JSONObject(json)

        // ✅ map locale une seule fois
        val localByName = buildLocalMapByName(context)
        val uriStats = UriFixStats()

        // 1) playlists
        val playlistsJson = root.optJSONObject("playlists")
        if (playlistsJson != null) {
            PlaylistRepository.clearAll()
            val names = playlistsJson.keys()
            while (names.hasNext()) {
                val name = names.next()
                PlaylistRepository.addPlaylist(name)
                val arr = playlistsJson.getJSONArray(name)
                for (i in 0 until arr.length()) {
                    val entry = arr.opt(i)
                    val oldUri: String
                    val backupName: String?
                    if (entry is JSONObject) {
                        oldUri = entry.optString("uri", "")
                        backupName = when {
                            entry.optString("displayName", "").isNotBlank() -> entry.optString("displayName")
                            entry.optString("name", "").isNotBlank() -> entry.optString("name")
                            entry.optString("title", "").isNotBlank() -> entry.optString("title")
                            else -> null
                        }
                    } else {
                        oldUri = arr.optString(i, "")
                        backupName = null
                    }
                    val fixedUri = mapUriIfNeeded(
                        context = context,
                        localByName = localByName,
                        oldUri = oldUri,
                        backupName = backupName,
                        stats = uriStats
                    )
                    PlaylistRepository.assignSongToPlaylist(name, fixedUri)
                }
            }
        }

        // 2) played
        val playedJson = root.optJSONObject("played")
        if (playedJson != null) {
            val names = playedJson.keys()
            while (names.hasNext()) {
                val name = names.next()
                val arr = playedJson.getJSONArray(name)
                for (i in 0 until arr.length()) {
                    val oldUri = arr.getString(i)
                    val fixedUri = mapUriIfNeeded(context, localByName, oldUri, stats = uriStats)
                    PlaylistRepository.markSongPlayed(name, fixedUri)
                }
            }
        }

        // 3) lastPlayed
        val lpJson = root.optJSONObject("lastPlayed")
        if (lpJson != null) {
            val oldUri = lpJson.optString("uri", "")
            val playlistName =
                if (lpJson.isNull("playlistName")) null else lpJson.optString("playlistName", "").ifBlank { null }
            val pos = lpJson.optLong("positionMs", 0L)

            if (oldUri.isNotBlank()) {
                val fixedUri = mapUriIfNeeded(context, localByName, oldUri, stats = uriStats)
                onLastPlayed(
                    LastPlayed(
                        uri = fixedUri,
                        playlistName = playlistName,
                        positionMs = pos
                    )
                )
            } else {
                onLastPlayed(null)
            }
        } else {
            onLastPlayed(null)
        }

        // 4) fond sonore
        val fillerJson = root.optJSONObject("fillerSound")
        if (fillerJson != null) {
            val uriStr = fillerJson.optString("uri", "")
            val volume = fillerJson.optDouble("volume", 0.25).toFloat()
            if (uriStr.isNotBlank()) {
                try {
                    val fixed = mapUriIfNeeded(context, localByName, uriStr, stats = uriStats)
                    val uri = Uri.parse(fixed)

                    FillerSoundPrefs.saveFillerUri(context, uri)
                    FillerSoundPrefs.saveFillerVolume(context, volume)

                    // ✅ IMPORTANT : persistable only for content://
                    if (uri.scheme == "content") {
                        try {
                            context.contentResolver.takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            )
                        } catch (_: Exception) { }
                    }
                } catch (_: Exception) { }
            }
        }

        // 5) réglages d’édition
        val editsJson = root.optJSONObject("edits")
        if (editsJson != null) {
            EditPrefs.clearAll(context)

            val keys = editsJson.keys()
            while (keys.hasNext()) {
                val oldUriString = keys.next()
                val one = editsJson.getJSONObject(oldUriString)
                val startMs = one.optLong("startMs", 0L)
                val endMs = one.optLong("endMs", 0L)

                val fixedUriString = mapUriIfNeeded(context, localByName, oldUriString, stats = uriStats)

                EditPrefs.saveEdit(
                    context,
                    fixedUriString,
                    EditPrefs.EditData(startMs, endMs)
                )

                // ✅ IMPORTANT : persistable only for content://
                val uri = runCatching { Uri.parse(fixedUriString) }.getOrNull()
                if (uri != null && uri.scheme == "content") {
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: Exception) { }
                }
            }
        }

        // 6) morceaux "à revoir"
        val reviewJson = root.optJSONObject("review")
        if (reviewJson != null) {
            val names = reviewJson.keys()
            while (names.hasNext()) {
                val name = names.next()
                val arr = reviewJson.getJSONArray(name)

                PlaylistRepository.clearReviewForPlaylist(name)

                for (i in 0 until arr.length()) {
                    val oldUri = arr.getString(i)
                    val fixedUri = mapUriIfNeeded(context, localByName, oldUri, stats = uriStats)
                    PlaylistRepository.setSongToReview(name, fixedUri, true)
                }
            }
        }

        // 7) couleurs de playlists
        val colorsJson = root.optJSONObject("colors")
        if (colorsJson != null) {
            val names = colorsJson.keys()
            while (names.hasNext()) {
                val name = names.next()
                val colorLong = colorsJson.optLong(name, 0xFFE86FFF)
                PlaylistRepository.setPlaylistColor(name, colorLong)
            }
        }

        Log.i(
            IMPORT_LOG_TAG,
            "Import done. localByName=${localByName.size} kept=${uriStats.kept} remapped=${uriStats.remapped} unresolved=${uriStats.unresolved}"
        )
    }

    private const val DEFAULT_BACKUP_FILE = "lrc_backup.json"

    /**
     * Renvoie le DocumentFile dossier de backup :
     * 1) dossier choisi par l'utilisateur via BackupFolderPrefs
     * 2) fallback : SplFolders.backupsDir(context)
     */
    private fun getBackupDir(context: Context): DocumentFile? {
        val folderUri = BackupFolderPrefs.get(context) ?: return SplFolders.backupsDir(context)

        // ✅ MODE INTERNE : pas de DocumentFile / pas de SAF
        if (folderUri.scheme == "file") {
            return null
        }

        // ✅ MODE SAF
        val tree = DocumentFile.fromTreeUri(context, folderUri)
        if (tree != null && tree.canWrite()) return tree

        return SplFolders.backupsDir(context)
    }

    private fun getBackupDirFile(context: Context): File {
        // /storage/emulated/0/Android/data/<package>/files/SPL_Music/Backups
        return SplFolders.backupsDirFile(context)
    }

    /**
     * Sauvegarde automatique dans un fichier fixe DEFAULT_BACKUP_FILE.
     */
    fun autoSaveToDefaultBackupFile(context: Context) {
        val json = exportState(
            context = context,
            lastPlayer = null,
            libraryFolders = emptyList()
        )

        // ✅ MODE INTERNAL : write with File()
        val root = BackupFolderPrefs.getLibraryRootUri(context)
        if (root != null && root.scheme == "file") {
            try {
                val dir = getBackupDirFile(context)
                val outFile = File(dir, DEFAULT_BACKUP_FILE)
                outFile.writeText(json, Charsets.UTF_8)
            } catch (e: Exception) {
                Log.e("BACKUP", "autoSave INTERNAL failed", e)
            }
            return
        }

        // ✅ MODE SAF
        val dir = getBackupDir(context) ?: return

        val existing = dir.findFile(DEFAULT_BACKUP_FILE)
        val target = when {
            existing != null && existing.isFile -> existing
            else -> dir.createFile("application/json", DEFAULT_BACKUP_FILE)
        } ?: return

        context.contentResolver.openOutputStream(target.uri, "w")?.use { out ->
            out.write(json.toByteArray(Charsets.UTF_8))
            out.flush()
        }
    }

    /**
     * Auto-restore : lit DEFAULT_BACKUP_FILE et applique importState.
     * Retourne true si un restore a été fait.
     */
    fun autoRestoreFromDefaultBackupFile(
        context: Context,
        onLastPlayed: (LastPlayed?) -> Unit = {}
    ): Boolean {

        // ✅ MODE INTERNAL : read with File()
        val root = BackupFolderPrefs.getLibraryRootUri(context)
        if (root != null && root.scheme == "file") {
            return try {
                val dir = getBackupDirFile(context)
                val f = File(dir, DEFAULT_BACKUP_FILE)
                if (!f.exists() || !f.isFile) return false
                val json = f.readText(Charsets.UTF_8)
                importState(context, json, onLastPlayed)
                true
            } catch (_: Exception) {
                false
            }
        }

        // ✅ MODE SAF : read DocumentFile
        val dir = getBackupDir(context) ?: return false
        val file = dir.findFile(DEFAULT_BACKUP_FILE) ?: return false
        if (!file.isFile) return false

        val json = try {
            context.contentResolver.openInputStream(file.uri)?.use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            }
        } catch (_: Exception) {
            null
        } ?: return false

        return try {
            importState(context, json, onLastPlayed)
            true
        } catch (_: Exception) {
            false
        }
    }
}
