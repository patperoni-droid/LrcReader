package com.patrick.lrcreader.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.os.SystemClock
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
    private const val RESTORE_DIAG_TAG = "RESTORE_DIAG"
    private const val BOOT_TAG = "BOOTSTEP"
    private const val AUTO_BACKUP_TAG = "AUTO_BACKUP"
    private const val ANR_BACKUP_TAG = "ANR_BACKUP"

    data class LastPlayed(
        val uri: String,
        val playlistName: String?,
        val positionMs: Long
    )

    private fun logAutoBackupInfo(message: String) {
        runCatching { Log.i(AUTO_BACKUP_TAG, message) }
    }

    private fun logAutoBackupWarn(message: String) {
        runCatching { Log.w(AUTO_BACKUP_TAG, message) }
    }

    enum class AutoBackupCode {
        SUCCESS,
        FAILED_NO_WORKSPACE,
        FAILED_ROOT_UNRESOLVED,
        FAILED_NOT_WRITABLE,
        FAILED_CREATE_DIRECTORY,
        FAILED_CREATE_FILE,
        FAILED_OPEN_STREAM,
        FAILED_EXCEPTION
    }

    enum class AutoBackupWorkerAction {
        SUCCESS,
        FAILURE,
        RETRY
    }

    data class AutoBackupResult(
        val code: AutoBackupCode,
        val workspaceStatus: WorkspaceResolver.Status?,
        val workspaceRootUri: Uri?,
        val targetDirUri: Uri? = null,
        val targetFileUri: Uri? = null,
        val detail: String? = null
    ) {
        val isSuccess: Boolean
            get() = code == AutoBackupCode.SUCCESS
    }

    private sealed interface WorkspaceRootHandle {
        data class FileRoot(val directory: File) : WorkspaceRootHandle
        data class SafRoot(val directory: DocumentFile) : WorkspaceRootHandle
    }

    private sealed interface WorkspaceBackupsDir {
        data class FileDir(val directory: File) : WorkspaceBackupsDir
        data class SafDir(val directory: DocumentFile) : WorkspaceBackupsDir
    }

    internal interface AutoBackupWriter {
        fun writeSaf(
            context: Context,
            snapshot: WorkspaceResolver.Snapshot,
            rootUri: Uri,
            fileName: String,
            json: String
        ): AutoBackupResult

        fun writeFile(
            snapshot: WorkspaceResolver.Snapshot,
            rootUri: Uri,
            fileName: String,
            json: String
        ): AutoBackupResult
    }

    private object WorkspaceAutoBackupWriter : AutoBackupWriter {
        override fun writeSaf(
            context: Context,
            snapshot: WorkspaceResolver.Snapshot,
            rootUri: Uri,
            fileName: String,
            json: String
        ): AutoBackupResult {
            val rootDoc = DocumentFile.fromTreeUri(context, rootUri)
                ?: DocumentFile.fromSingleUri(context, rootUri)
                ?: return autoBackupFailure(
                    code = AutoBackupCode.FAILED_ROOT_UNRESOLVED,
                    snapshot = snapshot,
                    detail = "root DocumentFile unresolved"
                )

            if (!rootDoc.exists() || !rootDoc.isDirectory) {
                return autoBackupFailure(
                    code = AutoBackupCode.FAILED_ROOT_UNRESOLVED,
                    snapshot = snapshot,
                    detail = "root is not a readable directory"
                )
            }

            val backupsDir = if (rootDoc.name.orEmpty().trim().equals("Backups", ignoreCase = true)) {
                rootDoc
            } else {
                findDirectoryIgnoreCase(rootDoc, listOf("Backups", "backups"))
                    ?: rootDoc.createDirectory("Backups")
                    ?: return autoBackupFailure(
                        code = AutoBackupCode.FAILED_CREATE_DIRECTORY,
                        snapshot = snapshot,
                        detail = "Backups directory create returned null"
                    )
            }

            if (!backupsDir.isDirectory || !backupsDir.canWrite()) {
                return autoBackupFailure(
                    code = AutoBackupCode.FAILED_NOT_WRITABLE,
                    snapshot = snapshot,
                    targetDirUri = backupsDir.uri,
                    detail = "Backups directory not writable"
                )
            }

            val existing = backupsDir.findFile(fileName)
            val target = when {
                existing != null && existing.isFile -> existing
                else -> backupsDir.createFile("application/json", fileName)
            } ?: return autoBackupFailure(
                code = AutoBackupCode.FAILED_CREATE_FILE,
                snapshot = snapshot,
                targetDirUri = backupsDir.uri,
                detail = "createFile returned null for $fileName"
            )

            val output = try {
                context.contentResolver.openOutputStream(target.uri, "w")
            } catch (error: Exception) {
                return autoBackupFailure(
                    code = AutoBackupCode.FAILED_EXCEPTION,
                    snapshot = snapshot,
                    targetDirUri = backupsDir.uri,
                    targetFileUri = target.uri,
                    detail = "openOutputStream exception: ${error.javaClass.simpleName}: ${error.message}"
                )
            }

            if (output == null) {
                return autoBackupFailure(
                    code = AutoBackupCode.FAILED_OPEN_STREAM,
                    snapshot = snapshot,
                    targetDirUri = backupsDir.uri,
                    targetFileUri = target.uri,
                    detail = "openOutputStream returned null"
                )
            }

            return try {
                output.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                    out.flush()
                }
                autoBackupSuccess(
                    snapshot = snapshot,
                    targetDirUri = backupsDir.uri,
                    targetFileUri = target.uri,
                    detail = "durable SAF backup written"
                )
            } catch (error: Exception) {
                autoBackupFailure(
                    code = AutoBackupCode.FAILED_EXCEPTION,
                    snapshot = snapshot,
                    targetDirUri = backupsDir.uri,
                    targetFileUri = target.uri,
                    detail = "write exception: ${error.javaClass.simpleName}: ${error.message}"
                )
            }
        }

        override fun writeFile(
            snapshot: WorkspaceResolver.Snapshot,
            rootUri: Uri,
            fileName: String,
            json: String
        ): AutoBackupResult {
            val rootPath = rootUri.path
            if (rootPath.isNullOrBlank()) {
                return autoBackupFailure(
                    code = AutoBackupCode.FAILED_ROOT_UNRESOLVED,
                    snapshot = snapshot,
                    detail = "file root path missing"
                )
            }

            val rootDir = File(rootPath)
            if (!rootDir.exists() || !rootDir.isDirectory) {
                return autoBackupFailure(
                    code = AutoBackupCode.FAILED_ROOT_UNRESOLVED,
                    snapshot = snapshot,
                    detail = "file root missing or not directory"
                )
            }

            val backupsDir = if (rootDir.name.equals("Backups", ignoreCase = true)) {
                rootDir
            } else {
                File(rootDir, "Backups").also { dir ->
                    if (!dir.exists() && !dir.mkdirs()) {
                        return autoBackupFailure(
                            code = AutoBackupCode.FAILED_CREATE_DIRECTORY,
                            snapshot = snapshot,
                            targetDirUri = Uri.fromFile(dir),
                            detail = "Backups directory mkdirs failed"
                        )
                    }
                }
            }

            if (!backupsDir.isDirectory || !backupsDir.canWrite()) {
                return autoBackupFailure(
                    code = AutoBackupCode.FAILED_NOT_WRITABLE,
                    snapshot = snapshot,
                    targetDirUri = Uri.fromFile(backupsDir),
                    detail = "file Backups directory not writable"
                )
            }

            return try {
                val outFile = File(backupsDir, fileName)
                outFile.writeText(json, Charsets.UTF_8)
                autoBackupSuccess(
                    snapshot = snapshot,
                    targetDirUri = Uri.fromFile(backupsDir),
                    targetFileUri = Uri.fromFile(outFile),
                    detail = "durable file backup written"
                )
            } catch (error: Exception) {
                autoBackupFailure(
                    code = AutoBackupCode.FAILED_EXCEPTION,
                    snapshot = snapshot,
                    targetDirUri = Uri.fromFile(backupsDir),
                    targetFileUri = Uri.fromFile(File(backupsDir, fileName)),
                    detail = "file write exception: ${error.javaClass.simpleName}: ${error.message}"
                )
            }
        }
    }

    fun exportState(
        context: Context,
        lastPlayer: LastPlayed?,          // peut être null
        libraryFolders: List<String>
    ): String {
        val root = JSONObject()

        // 1) playlists : ordre complet des titres
        val playlistsJson = JSONObject()
        PlaylistRepository.getPlaylists().forEach { plName ->
            val songs = PlaylistRepository.getAllItemsRaw(plName)
            val entries = JSONArray()
            songs.forEach { item ->
                val cleanSongId = item.songId?.trim()?.takeIf { it.isNotEmpty() }
                if (cleanSongId != null) {
                    entries.put(
                        JSONObject().apply {
                            put("uri", item.uri)
                            put("songId", cleanSongId)
                        }
                    )
                } else {
                    entries.put(item.uri)
                }
            }
            playlistsJson.put(plName, entries)
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

    private fun resolveWorkspaceRootHandle(
        context: Context,
        snapshot: WorkspaceResolver.Snapshot
    ): WorkspaceRootHandle? {
        val rootUri = snapshot.workspaceRootUri ?: return null
        return when (rootUri.scheme) {
            "file" -> {
                val rootPath = rootUri.path?.takeIf { it.isNotBlank() } ?: return null
                WorkspaceRootHandle.FileRoot(normalizeWorkspaceFileRoot(File(rootPath)))
            }

            "content" -> {
                val rootDoc = DocumentFile.fromTreeUri(context, rootUri)
                    ?: DocumentFile.fromSingleUri(context, rootUri)
                    ?: return null
                if (!rootDoc.exists() || !rootDoc.isDirectory) {
                    return null
                }
                WorkspaceRootHandle.SafRoot(normalizeWorkspaceSafRoot(rootDoc))
            }

            else -> null
        }
    }

    private fun resolveWorkspaceBackupsDir(
        context: Context,
        snapshot: WorkspaceResolver.Snapshot,
        createIfMissing: Boolean
    ): WorkspaceBackupsDir? {
        return when (val rootHandle = resolveWorkspaceRootHandle(context, snapshot)) {
            is WorkspaceRootHandle.FileRoot -> {
                val rootDir = rootHandle.directory
                if (!rootDir.exists() || !rootDir.isDirectory) {
                    if (!createIfMissing || !rootDir.mkdirs()) return null
                }

                val backupsDir = if (rootDir.name.equals("Backups", ignoreCase = true)) {
                    rootDir
                } else {
                    File(rootDir, "Backups").also { dir ->
                        if (!dir.exists() && (!createIfMissing || !dir.mkdirs())) {
                            return null
                        }
                    }
                }

                backupsDir.takeIf { it.exists() && it.isDirectory }
                    ?.let(WorkspaceBackupsDir::FileDir)
            }

            is WorkspaceRootHandle.SafRoot -> {
                val rootDoc = rootHandle.directory
                val backupsDir = if (rootDoc.name.orEmpty().trim().equals("Backups", ignoreCase = true)) {
                    rootDoc
                } else {
                    findDirectoryIgnoreCase(rootDoc, listOf("Backups", "backups"))
                        ?: if (createIfMissing) rootDoc.createDirectory("Backups") else null
                }

                backupsDir
                    ?.takeIf { it.exists() && it.isDirectory }
                    ?.let(WorkspaceBackupsDir::SafDir)
            }

            null -> null
        }
    }

    private fun normalizeWorkspaceFileRoot(rootDir: File): File {
        return when {
            File(rootDir, "BackingTracks").isDirectory -> rootDir
            File(File(rootDir, "SPL_Music"), "BackingTracks").isDirectory -> File(rootDir, "SPL_Music")
            else -> rootDir
        }
    }

    private fun normalizeWorkspaceSafRoot(rootDoc: DocumentFile): DocumentFile {
        if (findDirectoryIgnoreCase(rootDoc, listOf("BackingTracks", "BackingTrack")) != null) {
            return rootDoc
        }
        return findDirectoryIgnoreCase(rootDoc, listOf("SPL_Music", "spl_music")) ?: rootDoc
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

        val snapshot = WorkspaceResolver.resolve(context)
        val rootHandle = resolveWorkspaceRootHandle(context, snapshot)
        if (rootHandle == null) {
            Log.w(
                IMPORT_LOG_TAG,
                "Workspace introuvable pour remap backup workspaceStatus=${snapshot.status} workspaceRoot=${snapshot.workspaceRootUri}"
            )
            return emptyMap()
        }

        return when (rootHandle) {
            is WorkspaceRootHandle.FileRoot -> {
                val rootDir = rootHandle.directory
                if (!rootDir.exists() || !rootDir.isDirectory) {
                    emptyMap()
                } else {
                    val backingTracksDir = File(rootDir, "BackingTracks")
                    val audioDirs = linkedSetOf(
                        File(backingTracksDir, "audio"),
                        File(backingTracksDir, "Audio")
                    )

                    audioDirs.forEach { audioDir ->
                        if (audioDir.exists() && audioDir.isDirectory) {
                            audioDir.walkTopDown().forEach { f ->
                                if (f.isFile) addVariants(f.name, Uri.fromFile(f).toString())
                            }
                        }
                    }
                    if (map.isNotEmpty()) {
                        map
                    } else {
                        fun walk(dir: File) {
                            dir.listFiles()?.forEach { f ->
                                if (f.isDirectory) walk(f)
                                else addVariants(f.name, Uri.fromFile(f).toString())
                            }
                        }

                        walk(rootDir)
                        map
                    }
                }
            }

            is WorkspaceRootHandle.SafRoot -> {
                val cachedIndex = LibraryIndexCache.load(context) ?: emptyList()
                if (cachedIndex.isNotEmpty()) {
                    cachedIndex.forEach { e ->
                        if (!e.isDirectory) addVariants(e.name, e.uriString)
                    }
                }

                if (map.isNotEmpty()) {
                    map
                } else {
                    fun walk(doc: DocumentFile) {
                        doc.listFiles().forEach { child ->
                            if (child.isDirectory) walk(child)
                            else addVariants(child.name, child.uri.toString())
                        }
                    }

                    walk(rootHandle.directory)
                    map
                }
            }
        }
    }

    private data class UriFixStats(
        var kept: Int = 0,
        var remapped: Int = 0,
        var unresolved: Int = 0
    )

    private fun mapUriIfNeeded(
        context: Context,
        localByNameProvider: () -> Map<String, String>,
        oldUri: String,
        backupName: String? = null,
        stats: UriFixStats? = null
    ): String {
        // Fast path import: on truste les URI content:// pour éviter toute validation lourde
        // (pas de exists(), pas de DocumentFile, pas de openFileDescriptor).
        if (oldUri.startsWith("content://")) {
            stats?.kept = (stats?.kept ?: 0) + 1
            Log.d(IMPORT_LOG_TAG, "URI trusted, skipping validation")
            return oldUri
        }

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

        var localByName: Map<String, String>? = null
        for (candidate in candidates) {
            if (localByName == null) localByName = localByNameProvider()
            val resolved = localByName!![normalizeName(candidate)]
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

    private fun extractRuntimeSongId(uriString: String): String? {
        val path = runCatching { Uri.parse(uriString).path }.getOrNull()
            ?.replace('\\', '/')
            ?: return null
        val marker = "/tracks/"
        val markerIndex = path.lastIndexOf(marker)
        if (markerIndex < 0) return null
        val remainder = path.substring(markerIndex + marker.length)
        val separatorIndex = remainder.indexOf('/')
        if (separatorIndex <= 0) return null
        return remainder.substring(0, separatorIndex)
            .trim()
            .takeIf { it.isNotEmpty() }
    }

    fun importState(
        context: Context,
        json: String,
        mergePlaylists: Boolean = false,
        onLastPlayed: (LastPlayed?) -> Unit = {}
    ) {
        PlaylistRepository.withRestoreInProgress {
        val root = JSONObject(json)
        val importedPlaylistNameBySource = linkedMapOf<String, String>()

        fun uniqueRestoredPlaylistName(sourceName: String): String {
            val clean = sourceName.trim().ifBlank { "Playlist" }
            val existing = PlaylistRepository.getPlaylists().toSet()
            if (clean !in existing) return clean
            val restored = "$clean (restaurée)"
            if (restored !in existing) return restored
            var index = 2
            while (true) {
                val candidate = "$clean ($index)"
                if (candidate !in existing) return candidate
                index += 1
            }
        }

        // map locale lazy: on ne scanne que si une URI non-content a besoin de résolution.
        var localByName: Map<String, String>? = null
        fun localByNameProvider(): Map<String, String> {
            val cached = localByName
            if (cached != null) return cached
            val built = buildLocalMapByName(context)
            localByName = built
            return built
        }
        val uriStats = UriFixStats()

        // 1) playlists
        val playlistsJson = root.optJSONObject("playlists")
        if (playlistsJson != null) {
            if (!mergePlaylists) {
                PlaylistRepository.clearAll()
            }
            val names = playlistsJson.keys()
            while (names.hasNext()) {
                val sourceName = names.next()
                val name = if (mergePlaylists) uniqueRestoredPlaylistName(sourceName) else sourceName
                importedPlaylistNameBySource[sourceName] = name
                PlaylistRepository.addPlaylist(name)
                val arr = playlistsJson.getJSONArray(sourceName)
                for (i in 0 until arr.length()) {
                    val entry = arr.opt(i)
                    val oldUri: String
                    val backupName: String?
                    val backupSongId: String?
                    if (entry is JSONObject) {
                        oldUri = entry.optString("uri", "")
                        backupName = when {
                            entry.optString("displayName", "").isNotBlank() -> entry.optString("displayName")
                            entry.optString("name", "").isNotBlank() -> entry.optString("name")
                            entry.optString("title", "").isNotBlank() -> entry.optString("title")
                            else -> null
                        }
                        backupSongId = entry.optString("songId", "").trim().ifBlank { null }
                    } else {
                        oldUri = arr.optString(i, "")
                        backupName = null
                        backupSongId = null
                    }
                    val entrySongId = backupSongId
                        ?: getSmpSongId(oldUri)
                        ?: extractRuntimeSongId(oldUri)
                    val fixedUri = if (entrySongId != null) {
                        buildSmpItem(entrySongId)
                    } else {
                        mapUriIfNeeded(
                            context = context,
                            localByNameProvider = ::localByNameProvider,
                            oldUri = oldUri,
                            backupName = backupName,
                            stats = uriStats
                        )
                    }
                    val restoredSongId = getSmpSongId(fixedUri)
                        ?: entrySongId
                        ?: extractRuntimeSongId(fixedUri)
                    val playlistUri = restoredSongId?.let { buildSmpItem(it) } ?: fixedUri
                    if (playlistUri.isBlank() || playlistUri.equals("null", ignoreCase = true)) {
                        Log.w(
                            RESTORE_DIAG_TAG,
                            "playlistItem skippedInvalidUri originalUri=$oldUri"
                        )
                        continue
                    }
                    Log.d(
                        RESTORE_DIAG_TAG,
                        "playlistItem songId=${restoredSongId ?: "null"} uri=$playlistUri originalUri=$oldUri"
                    )
                    PlaylistRepository.assignSongToPlaylist(
                        playlistName = name,
                        songUri = playlistUri,
                        songId = restoredSongId
                    )
                }
            }
        }

        // 2) played
        val playedJson = root.optJSONObject("played")
        if (playedJson != null) {
            val names = playedJson.keys()
            while (names.hasNext()) {
                val sourceName = names.next()
                val name = importedPlaylistNameBySource[sourceName] ?: sourceName
                val arr = playedJson.getJSONArray(sourceName)
                for (i in 0 until arr.length()) {
                    val oldUri = arr.getString(i)
                    val fixedUri = mapUriIfNeeded(context, ::localByNameProvider, oldUri, stats = uriStats)
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
                val fixedUri = mapUriIfNeeded(context, ::localByNameProvider, oldUri, stats = uriStats)
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
                    val fixed = mapUriIfNeeded(context, ::localByNameProvider, uriStr, stats = uriStats)
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

                val fixedUriString = mapUriIfNeeded(context, ::localByNameProvider, oldUriString, stats = uriStats)

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
                val sourceName = names.next()
                val name = importedPlaylistNameBySource[sourceName] ?: sourceName
                val arr = reviewJson.getJSONArray(sourceName)

                PlaylistRepository.clearReviewForPlaylist(name)

                for (i in 0 until arr.length()) {
                    val oldUri = arr.getString(i)
                    val fixedUri = mapUriIfNeeded(context, ::localByNameProvider, oldUri, stats = uriStats)
                    PlaylistRepository.setSongToReview(name, fixedUri, true)
                }
            }
        }

        // 7) couleurs de playlists
        val colorsJson = root.optJSONObject("colors")
        if (colorsJson != null) {
            val names = colorsJson.keys()
            while (names.hasNext()) {
                val sourceName = names.next()
                val name = importedPlaylistNameBySource[sourceName] ?: sourceName
                val colorLong = colorsJson.optLong(sourceName, 0xFFE86FFF)
                PlaylistRepository.setPlaylistColor(name, colorLong)
            }
        }

        Log.i(
            IMPORT_LOG_TAG,
            "Import done. localByName=${localByName?.size ?: 0} localMapBuilt=${localByName != null} kept=${uriStats.kept} remapped=${uriStats.remapped} unresolved=${uriStats.unresolved}"
        )
        }
    }

    private const val DEFAULT_BACKUP_FILE = "lrc_backup.json"

    private fun findDirectoryIgnoreCase(parent: DocumentFile, names: List<String>): DocumentFile? {
        val wanted = names.map { it.trim().lowercase() }.toSet()
        return parent.listFiles().firstOrNull { child ->
            child.isDirectory && child.name.orEmpty().trim().lowercase() in wanted
        }
    }

    /**
     * Sauvegarde automatique dans un fichier fixe DEFAULT_BACKUP_FILE.
     */
    fun autoSaveToDefaultBackupFile(context: Context): AutoBackupResult {
        return autoSaveToDefaultBackupFile(
            context = context,
            snapshotOverride = null,
            writer = WorkspaceAutoBackupWriter
        )
    }

    internal fun autoSaveToDefaultBackupFile(
        context: Context,
        snapshotOverride: WorkspaceResolver.Snapshot?,
        writer: AutoBackupWriter,
        jsonOverride: String? = null
    ): AutoBackupResult {
        val startMs = SystemClock.elapsedRealtime()
        val threadName = Thread.currentThread().name
        Log.e(ANR_BACKUP_TAG, "autosave:start thread=$threadName")
        val resolveStartMs = SystemClock.elapsedRealtime()
        val snapshot = snapshotOverride ?: WorkspaceResolver.resolve(context)
        val resolveDurationMs = SystemClock.elapsedRealtime() - resolveStartMs
        logAutoBackupInfo(
            "step=start workspaceStatus=${snapshot.status} workspaceRoot=${snapshot.workspaceRootUri} setupTree=${snapshot.setupTreeUri}"
        )
        Log.e(
            ANR_BACKUP_TAG,
            "autosave:resolve_done durationMs=$resolveDurationMs thread=$threadName status=${snapshot.status} root=${snapshot.workspaceRootUri}"
        )

        if (!snapshot.isUsable || snapshot.workspaceRootUri == null) {
            val failure = autoBackupFailure(
                code = AutoBackupCode.FAILED_NO_WORKSPACE,
                snapshot = snapshot,
                detail = "workspace not usable"
            )
            Log.e(
                ANR_BACKUP_TAG,
                "autosave:end durationMs=${SystemClock.elapsedRealtime() - startMs} thread=$threadName result=${failure.code}"
            )
            return failure
        }

        val exportStartMs = SystemClock.elapsedRealtime()
        val json = jsonOverride ?: exportState(
            context = context,
            lastPlayer = null,
            libraryFolders = emptyList()
        )
        val exportDurationMs = SystemClock.elapsedRealtime() - exportStartMs
        Log.e(
            ANR_BACKUP_TAG,
            "autosave:export_done durationMs=$exportDurationMs thread=$threadName jsonLength=${json.length}"
        )

        val rootUri = snapshot.workspaceRootUri
        val writeStartMs = SystemClock.elapsedRealtime()
        val result = when (rootUri.scheme) {
            "content" -> writer.writeSaf(
                context = context,
                snapshot = snapshot,
                rootUri = rootUri,
                fileName = DEFAULT_BACKUP_FILE,
                json = json
            )

            "file" -> writer.writeFile(
                snapshot = snapshot,
                rootUri = rootUri,
                fileName = DEFAULT_BACKUP_FILE,
                json = json
            )

            else -> autoBackupFailure(
                code = AutoBackupCode.FAILED_ROOT_UNRESOLVED,
                snapshot = snapshot,
                detail = "unsupported root scheme=${rootUri.scheme}"
            )
        }
        Log.e(
            ANR_BACKUP_TAG,
            "autosave:end durationMs=${SystemClock.elapsedRealtime() - startMs} resolveDurationMs=$resolveDurationMs exportDurationMs=$exportDurationMs writeDurationMs=${SystemClock.elapsedRealtime() - writeStartMs} thread=$threadName result=${result.code}"
        )
        return result
    }

    internal fun workerActionForAutoBackup(result: AutoBackupResult): AutoBackupWorkerAction {
        return when (result.code) {
            AutoBackupCode.SUCCESS -> AutoBackupWorkerAction.SUCCESS
            AutoBackupCode.FAILED_NO_WORKSPACE,
            AutoBackupCode.FAILED_ROOT_UNRESOLVED,
            AutoBackupCode.FAILED_NOT_WRITABLE -> AutoBackupWorkerAction.FAILURE

            AutoBackupCode.FAILED_CREATE_DIRECTORY,
            AutoBackupCode.FAILED_CREATE_FILE,
            AutoBackupCode.FAILED_OPEN_STREAM,
            AutoBackupCode.FAILED_EXCEPTION -> AutoBackupWorkerAction.RETRY
        }
    }

    private fun autoBackupSuccess(
        snapshot: WorkspaceResolver.Snapshot,
        targetDirUri: Uri?,
        targetFileUri: Uri?,
        detail: String
    ): AutoBackupResult {
        val result = AutoBackupResult(
            code = AutoBackupCode.SUCCESS,
            workspaceStatus = snapshot.status,
            workspaceRootUri = snapshot.workspaceRootUri,
            targetDirUri = targetDirUri,
            targetFileUri = targetFileUri,
            detail = detail
        )
        logAutoBackupInfo(
            "step=success workspaceStatus=${result.workspaceStatus} workspaceRoot=${result.workspaceRootUri} targetDir=${result.targetDirUri} targetFile=${result.targetFileUri} detail=${result.detail}"
        )
        return result
    }

    private fun autoBackupFailure(
        code: AutoBackupCode,
        snapshot: WorkspaceResolver.Snapshot,
        targetDirUri: Uri? = null,
        targetFileUri: Uri? = null,
        detail: String
    ): AutoBackupResult {
        val result = AutoBackupResult(
            code = code,
            workspaceStatus = snapshot.status,
            workspaceRootUri = snapshot.workspaceRootUri,
            targetDirUri = targetDirUri,
            targetFileUri = targetFileUri,
            detail = detail
        )
        logAutoBackupWarn(
            "step=failure code=${result.code} workspaceStatus=${result.workspaceStatus} workspaceRoot=${result.workspaceRootUri} targetDir=${result.targetDirUri} targetFile=${result.targetFileUri} detail=${result.detail}"
        )
        return result
    }

    /**
     * Auto-restore : lit DEFAULT_BACKUP_FILE et applique importState.
     * Retourne true si un restore a été fait.
     */
    fun autoRestoreFromDefaultBackupFile(
        context: Context,
        onLastPlayed: (LastPlayed?) -> Unit = {}
    ): Boolean {
        val snapshot = WorkspaceResolver.resolve(context)
        if (!snapshot.isUsable || snapshot.workspaceRootUri == null) {
            logAutoBackupWarn(
                "step=auto_restore_skip workspaceStatus=${snapshot.status} workspaceRoot=${snapshot.workspaceRootUri}"
            )
            return false
        }

        val backupsDir = resolveWorkspaceBackupsDir(
            context = context,
            snapshot = snapshot,
            createIfMissing = false
        ) ?: run {
            logAutoBackupWarn(
                "step=auto_restore_skip workspaceStatus=${snapshot.status} workspaceRoot=${snapshot.workspaceRootUri} detail=backups_dir_unresolved"
            )
            return false
        }

        val json = when (backupsDir) {
            is WorkspaceBackupsDir.SafDir -> {
                val file = backupsDir.directory.findFile(DEFAULT_BACKUP_FILE)
                    ?.takeIf { it.isFile }
                    ?: return false
                try {
                    context.contentResolver.openInputStream(file.uri)?.use { input ->
                        input.readBytes().toString(Charsets.UTF_8)
                    }
                } catch (_: Exception) {
                    null
                }
            }

            is WorkspaceBackupsDir.FileDir -> {
                try {
                    val file = File(backupsDir.directory, DEFAULT_BACKUP_FILE)
                    if (!file.exists() || !file.isFile) return false
                    file.readText(Charsets.UTF_8)
                } catch (_: Exception) {
                    null
                }
            }
        } ?: return false

        return try {
            importState(context = context, json = json, onLastPlayed = onLastPlayed)
            true
        } catch (_: Exception) {
            false
        }
    }
}
