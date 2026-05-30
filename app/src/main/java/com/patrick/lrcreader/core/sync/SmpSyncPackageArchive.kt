package com.patrick.lrcreader.core.sync

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.patrick.lrcreader.core.PlaylistItem
import com.patrick.lrcreader.core.PlaylistRepository
import com.patrick.lrcreader.core.buildSmpItem
import com.patrick.lrcreader.core.getSmpSongId
import com.patrick.lrcreader.core.isVirtualPlaylistItem
import com.patrick.lrcreader.smp.SmpExporter
import com.patrick.lrcreader.smp.SmpLibraryScanner
import com.patrick.lrcreader.smp.SmpSecureImportPipeline
import com.patrick.lrcreader.ui.library.SongVariantFamily
import com.patrick.lrcreader.ui.library.SongVariantFamiliesStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

private const val SYNC_PACKAGE_ENTRY = "package.json"
private const val SYNC_SOURCE_ANALYSIS_ENTRY = "source_analysis.json"
private const val SYNC_PLAYLIST_STATE_ENTRY = "playlist_state.json"
private const val SYNC_SONGS_DIR = "songs"
private const val SYNC_BUFFER_SIZE = 128 * 1024
private const val SYNC_MAX_PACKAGE_BYTES = 2_000L * 1024L * 1024L
private const val SYNC_PACKAGE_DIAG_TAG = "SMP_SYNC_PACKAGE_DIAG"
private const val SYNC_PACKAGE_ITEM_TIMEOUT_MS = 120_000L

enum class SmpSyncPackageProgressPhase {
    STARTED,
    SCANNED_LIBRARY,
    BUILT_PLAN_ITEMS,
    ITEM_STARTED,
    ITEM_FINISHED,
    PLAYLISTS_STARTED,
    PLAYLISTS_FINISHED,
    HASH_STARTED,
    FINISHED
}

data class SmpSyncPackageProgress(
    val phase: SmpSyncPackageProgressPhase,
    val itemIndex: Int = 0,
    val itemCount: Int = 0,
    val entityId: String? = null,
    val title: String? = null,
    val elapsedMs: Long = 0L
)

class SmpSyncPackagePreparationException(
    message: String,
    val entityId: String? = null,
    val title: String? = null,
    cause: Throwable? = null
) : Exception(message, cause)

data class SmpSyncPreparedPackage(
    val syncPackage: SmpSyncPackage,
    val file: File,
    val sha256: String
) {
    val sizeBytes: Long
        get() = file.length().coerceAtLeast(0L)
}

data class SmpSyncReceivedPackage(
    val syncPackage: SmpSyncPackage,
    val sourceManifest: SmpSyncManifest,
    val file: File,
    val sha256: String
) {
    val sizeBytes: Long
        get() = file.length().coerceAtLeast(0L)

    val fullSongCount: Int
        get() = syncPackage.fullSongCount

    val replacementSongCount: Int
        get() = syncPackage.items.count {
            it.kind == SmpSyncPackageKind.SONG_FULL &&
                it.diffStatus == SyncDiffStatus.MODIFIED_ON_A
        }
}

data class SmpSyncPackageImportResult(
    val importedSongCount: Int,
    val replacedSongCount: Int,
    val playlistCount: Int,
    val familyCount: Int,
    val failureReason: String? = null
) {
    val isSuccess: Boolean
        get() = failureReason == null
}

class SmpSyncPackageArchiveBuilder(private val context: Context) {

    suspend fun build(
        sourceManifest: SmpSyncManifest,
        plan: SyncPlan,
        onProgress: (SmpSyncPackageProgress) -> Unit = {}
    ): SmpSyncPreparedPackage = withContext(Dispatchers.IO) {
        val startedAt = SystemClock.elapsedRealtime()
        fun elapsed(): Long = SystemClock.elapsedRealtime() - startedAt
        fun log(message: String) {
            Log.i(SYNC_PACKAGE_DIAG_TAG, message)
        }
        fun progress(
            phase: SmpSyncPackageProgressPhase,
            itemIndex: Int = 0,
            itemCount: Int = 0,
            item: SmpSyncPackageItem? = null
        ) {
            onProgress(
                SmpSyncPackageProgress(
                    phase = phase,
                    itemIndex = itemIndex,
                    itemCount = itemCount,
                    entityId = item?.entityId,
                    title = item?.title,
                    elapsedMs = elapsed()
                )
            )
        }

        log("prepare:start planItems=${plan.items.size} sourceSongs=${sourceManifest.songs.size} playlists=${sourceManifest.playlists.size} families=${sourceManifest.families.size}")
        progress(SmpSyncPackageProgressPhase.STARTED)
        val scanner = SmpLibraryScanner(context)
        val songsById = scanner.listSongs().associateBy { it.id }
        log("prepare:library_scanned songs=${songsById.size} elapsedMs=${elapsed()}")
        progress(SmpSyncPackageProgressPhase.SCANNED_LIBRARY)
        val basePackage = SyncPackageBuilder().build(
            sourceManifest = sourceManifest,
            plan = plan
        )
        log("prepare:package_items total=${basePackage.itemCount} fullSongs=${basePackage.fullSongCount} playlists=${basePackage.playlistStateCount} families=${basePackage.familyStateCount} elapsedMs=${elapsed()}")
        progress(SmpSyncPackageProgressPhase.BUILT_PLAN_ITEMS)
        val packageFile = createTempPackageFile(context)
        val exportedFiles = mutableListOf<File>()
        val updatedItems = mutableListOf<SmpSyncPackageItem>()

        try {
            ZipOutputStream(packageFile.outputStream().buffered()).use { zip ->
                basePackage.items.forEachIndexed { index, item ->
                    progress(
                        phase = SmpSyncPackageProgressPhase.ITEM_STARTED,
                        itemIndex = index + 1,
                        itemCount = basePackage.itemCount,
                        item = item
                    )
                    log("prepare:item_start index=${index + 1}/${basePackage.itemCount} kind=${item.kind} entityId=${item.entityId} title=${item.title ?: "null"} elapsedMs=${elapsed()}")
                    when (item.kind) {
                        SmpSyncPackageKind.SONG_FULL -> {
                            try {
                                withTimeout(SYNC_PACKAGE_ITEM_TIMEOUT_MS) {
                                    val song = songsById[item.entityId]
                                        ?: throw SmpSyncPackagePreparationException(
                                            message = "missing_song",
                                            entityId = item.entityId,
                                            title = item.title
                                        )
                                    log("prepare:export_song_start songId=${song.id} title=${song.title} elapsedMs=${elapsed()}")
                                    val smpFile = SmpExporter.exportSongUnitToCacheSmp(context, song)
                                        ?: throw SmpSyncPackagePreparationException(
                                            message = "export_failed",
                                            entityId = item.entityId,
                                            title = item.title
                                        )
                                    exportedFiles += smpFile
                                    log("prepare:export_song_done songId=${song.id} bytes=${smpFile.length()} elapsedMs=${elapsed()}")
                                    val entryName = "$SYNC_SONGS_DIR/${item.entityId}.smp"
                                    zip.putNextEntry(ZipEntry(entryName))
                                    smpFile.inputStream().buffered().use { input ->
                                        input.copyTo(zip, SYNC_BUFFER_SIZE)
                                    }
                                    zip.closeEntry()
                                    updatedItems += item.copy(
                                        estimatedBytes = smpFile.length().takeIf { it >= 0L },
                                        contentEntry = entryName
                                    )
                                }
                            } catch (error: TimeoutCancellationException) {
                                Log.e(SYNC_PACKAGE_DIAG_TAG, "prepare:item_timeout entityId=${item.entityId} title=${item.title ?: "null"} elapsedMs=${elapsed()}", error)
                                throw SmpSyncPackagePreparationException(
                                    message = "item_timeout",
                                    entityId = item.entityId,
                                    title = item.title,
                                    cause = error
                                )
                            }
                        }

                        else -> updatedItems += item
                    }
                    progress(
                        phase = SmpSyncPackageProgressPhase.ITEM_FINISHED,
                        itemIndex = index + 1,
                        itemCount = basePackage.itemCount,
                        item = item
                    )
                    log("prepare:item_done index=${index + 1}/${basePackage.itemCount} kind=${item.kind} entityId=${item.entityId} elapsedMs=${elapsed()}")
                }

                val finalPackage = basePackage.copy(items = updatedItems)
                writeStringEntry(zip, SYNC_SOURCE_ANALYSIS_ENTRY, sourceManifest.toJsonString(indentSpaces = 0))
                progress(SmpSyncPackageProgressPhase.PLAYLISTS_STARTED)
                log("prepare:playlist_family_state_start elapsedMs=${elapsed()}")
                buildPlaylistState(finalPackage)?.let { state ->
                    writeStringEntry(zip, SYNC_PLAYLIST_STATE_ENTRY, state.toJsonString())
                }
                progress(SmpSyncPackageProgressPhase.PLAYLISTS_FINISHED)
                log("prepare:playlist_family_state_done elapsedMs=${elapsed()}")
                writeStringEntry(zip, SYNC_PACKAGE_ENTRY, finalPackage.toJsonString(indentSpaces = 0))
            }

            val finalPackage = readPackageFromArchive(packageFile)
                ?: basePackage.copy(items = updatedItems)
            progress(SmpSyncPackageProgressPhase.HASH_STARTED)
            log("prepare:sha_start bytes=${packageFile.length()} elapsedMs=${elapsed()}")
            val packageSha = sha256(packageFile)
            log("prepare:finished bytes=${packageFile.length()} elapsedMs=${elapsed()}")
            progress(SmpSyncPackageProgressPhase.FINISHED)
            SmpSyncPreparedPackage(
                syncPackage = finalPackage,
                file = packageFile,
                sha256 = packageSha
            )
        } catch (error: SmpSyncPackagePreparationException) {
            Log.e(SYNC_PACKAGE_DIAG_TAG, "prepare:failed entityId=${error.entityId ?: "null"} title=${error.title ?: "null"} elapsedMs=${elapsed()} reason=${error.message}", error)
            runCatching { packageFile.delete() }
            throw error
        } catch (error: Exception) {
            Log.e(SYNC_PACKAGE_DIAG_TAG, "prepare:failed elapsedMs=${elapsed()}", error)
            runCatching { packageFile.delete() }
            throw error
        } finally {
            exportedFiles.forEach { file ->
                runCatching { file.delete() }
                runCatching { File(file.parentFile, "${file.name}.part").delete() }
            }
        }
    }
}

class SmpSyncPackageArchiveReader(private val context: Context) {

    suspend fun readReceivedPackage(file: File): SmpSyncReceivedPackage? = withContext(Dispatchers.IO) {
        if (!file.isFile || file.length() <= 0L || file.length() > SYNC_MAX_PACKAGE_BYTES) {
            return@withContext null
        }
        val syncPackage = readPackageFromArchive(file) ?: return@withContext null
        val sourceManifest = readSourceManifestFromArchive(file) ?: return@withContext null
        if (!validateSongEntries(file, syncPackage)) return@withContext null
        SmpSyncReceivedPackage(
            syncPackage = syncPackage,
            sourceManifest = sourceManifest,
            file = file,
            sha256 = sha256(file)
        )
    }

    suspend fun importReceivedPackage(
        receivedPackage: SmpSyncReceivedPackage,
        allowReplace: Boolean
    ): SmpSyncPackageImportResult = withContext(Dispatchers.IO) {
        val usableSpace = context.filesDir.usableSpace
        if (usableSpace > 0L && receivedPackage.sizeBytes > usableSpace) {
            return@withContext SmpSyncPackageImportResult(
                importedSongCount = 0,
                replacedSongCount = 0,
                playlistCount = 0,
                familyCount = 0,
                failureReason = "espace disque insuffisant"
            )
        }
        val existingSongIds = SmpLibraryScanner(context).listSongs().map { it.id }.toSet()
        val importedIds = mutableSetOf<String>()
        var importedSongs = 0
        var replacedSongs = 0

        ZipFile(receivedPackage.file).use { zip ->
            receivedPackage.syncPackage.items
                .filter { it.kind == SmpSyncPackageKind.SONG_FULL }
                .forEach { item ->
                    val entryName = item.contentEntry
                        ?: return@forEach
                    val entry = zip.getEntry(entryName)
                        ?: return@forEach
                    val exists = item.entityId in existingSongIds
                    if (exists && !allowReplace) {
                        return@forEach
                    }
                    val tempSmp = File.createTempFile(
                        "sync_${item.entityId.ifBlank { "song" }}_",
                        ".smp",
                        context.cacheDir
                    )
                    try {
                        zip.getInputStream(entry).use { input ->
                            tempSmp.outputStream().buffered().use { output ->
                                input.copyTo(output, SYNC_BUFFER_SIZE)
                            }
                        }
                        val result = SmpSecureImportPipeline(context).import(Uri.fromFile(tempSmp))
                        val importedSong = result.importedSong
                            ?: return@withContext SmpSyncPackageImportResult(
                                importedSongCount = importedSongs,
                                replacedSongCount = replacedSongs,
                                playlistCount = 0,
                                familyCount = 0,
                                failureReason = result.failureReason ?: "import SMP impossible"
                            )
                        importedIds += importedSong.id
                        importedSongs += 1
                        if (exists) replacedSongs += 1
                    } finally {
                        runCatching { tempSmp.delete() }
                    }
                }
        }

        val playlistCount = applyPlaylistState(receivedPackage)
        val familyCount = applyFamilyState(receivedPackage.sourceManifest)

        SmpSyncPackageImportResult(
            importedSongCount = importedSongs,
            replacedSongCount = replacedSongs,
            playlistCount = playlistCount,
            familyCount = familyCount
        )
    }

    private fun applyPlaylistState(receivedPackage: SmpSyncReceivedPackage): Int {
        readPlaylistStateFromArchive(receivedPackage.file)?.let { state ->
            return applyPlaylistSnapshots(state.playlists)
        }
        return applyPlaylistStateFromManifest(receivedPackage.sourceManifest)
    }

    private fun applyPlaylistSnapshots(playlists: List<SmpSyncPlaylistSnapshot>): Int {
        val availableSongIds = SmpLibraryScanner(context).listSongs().map { it.id }.toSet()
        var count = 0
        playlists.forEach { playlist ->
            val cleanName = playlist.name.trim().takeIf { it.isNotEmpty() } ?: return@forEach
            PlaylistRepository.addPlaylist(cleanName)
            PlaylistRepository.setPlaylistColor(cleanName, playlist.color)
            playlist.items.forEach { item ->
                val cleanUri = item.uri.trim().takeIf { it.isNotEmpty() } ?: return@forEach
                val songId = item.songId?.trim()?.takeIf { it.isNotEmpty() } ?: getSmpSongId(cleanUri)
                val playlistUri = when {
                    isVirtualPlaylistItem(cleanUri) -> cleanUri
                    songId != null && songId in availableSongIds -> buildSmpItem(songId)
                    else -> return@forEach
                }
                PlaylistRepository.assignSongToPlaylist(
                    playlistName = cleanName,
                    songUri = playlistUri,
                    songId = songId?.takeIf { it in availableSongIds }
                )
            }
            count += 1
        }
        return count
    }

    private fun applyPlaylistStateFromManifest(sourceManifest: SmpSyncManifest): Int {
        val availableSongIds = SmpLibraryScanner(context).listSongs().map { it.id }.toSet()
        var count = 0
        sourceManifest.playlists.forEach { playlist ->
            val cleanName = playlist.playlistName.trim().takeIf { it.isNotEmpty() } ?: return@forEach
            PlaylistRepository.addPlaylist(cleanName)
            playlist.songIds
                .filter { it in availableSongIds }
                .forEach { songId ->
                    PlaylistRepository.assignSongToPlaylist(
                        playlistName = cleanName,
                        songUri = buildSmpItem(songId),
                        songId = songId
                    )
                }
            count += 1
        }
        return count
    }

    private fun applyFamilyState(sourceManifest: SmpSyncManifest): Int {
        val availableSongIds = SmpLibraryScanner(context).listSongs().map { it.id }.toSet()
        var count = 0
        sourceManifest.families.forEach { family ->
            val cleanSongIds = family.songIds.filter { it in availableSongIds }.toSet()
            if (cleanSongIds.isEmpty()) return@forEach
            SongVariantFamiliesStore.upsertFamily(
                context = context,
                family = SongVariantFamily(
                    id = family.familyId,
                    title = family.title,
                    songIds = cleanSongIds,
                    parentSongId = family.parentSongId?.takeIf { it in cleanSongIds },
                    activeSongId = family.activeSongId?.takeIf { it in cleanSongIds }
                )
            )
            count += 1
        }
        return count
    }
}

private data class SmpSyncPlaylistState(
    val playlists: List<SmpSyncPlaylistSnapshot>
) {
    fun toJsonString(): String {
        return JSONObject()
            .put(
                "playlists",
                JSONArray().apply {
                    playlists.forEach { put(it.toJson()) }
                }
            )
            .toString(0)
    }

    companion object {
        fun fromJsonOrNull(rawJson: String?): SmpSyncPlaylistState? {
            if (rawJson.isNullOrBlank()) return null
            return runCatching {
                val json = JSONObject(rawJson)
                val playlistsJson = json.optJSONArray("playlists") ?: JSONArray()
                val playlists = buildList {
                    for (index in 0 until playlistsJson.length()) {
                        playlistsJson.optJSONObject(index)
                            ?.let(SmpSyncPlaylistSnapshot::fromJson)
                            ?.let(::add)
                    }
                }
                SmpSyncPlaylistState(playlists)
            }.getOrNull()
        }
    }
}

private data class SmpSyncPlaylistSnapshot(
    val name: String,
    val color: Long,
    val items: List<SmpSyncPlaylistItemSnapshot>
) {
    fun toJson(): JSONObject {
        return JSONObject()
            .put("name", name)
            .put("color", color)
            .put(
                "items",
                JSONArray().apply {
                    items.forEach { put(it.toJson()) }
                }
            )
    }

    companion object {
        fun fromJson(json: JSONObject): SmpSyncPlaylistSnapshot {
            val itemsJson = json.optJSONArray("items") ?: JSONArray()
            val items = buildList {
                for (index in 0 until itemsJson.length()) {
                    itemsJson.optJSONObject(index)
                        ?.let(SmpSyncPlaylistItemSnapshot::fromJson)
                        ?.let(::add)
                }
            }
            return SmpSyncPlaylistSnapshot(
                name = json.optString("name").trim(),
                color = json.optLong("color", PlaylistRepository.getPlaylistColor(json.optString("name"))),
                items = items
            )
        }
    }
}

private data class SmpSyncPlaylistItemSnapshot(
    val uri: String,
    val songId: String?
) {
    fun toJson(): JSONObject {
        return JSONObject()
            .put("uri", uri)
            .put("songId", songId ?: JSONObject.NULL)
    }

    companion object {
        fun fromJson(json: JSONObject): SmpSyncPlaylistItemSnapshot {
            return SmpSyncPlaylistItemSnapshot(
                uri = json.optString("uri").trim(),
                songId = json.optStringOrNull("songId")
            )
        }
    }
}

private fun buildPlaylistState(syncPackage: SmpSyncPackage): SmpSyncPlaylistState? {
    val playlistNames = syncPackage.items
        .filter { it.kind == SmpSyncPackageKind.PLAYLIST_STATE }
        .mapNotNull { it.title?.trim()?.takeIf(String::isNotEmpty) }
        .distinct()
    if (playlistNames.isEmpty()) return null
    return SmpSyncPlaylistState(
        playlists = playlistNames.map { name ->
            SmpSyncPlaylistSnapshot(
                name = name,
                color = PlaylistRepository.getPlaylistColor(name),
                items = PlaylistRepository.getAllItemsRaw(name).map { item ->
                    item.toSnapshot()
                }
            )
        }
    )
}

private fun PlaylistItem.toSnapshot(): SmpSyncPlaylistItemSnapshot {
    return SmpSyncPlaylistItemSnapshot(
        uri = uri,
        songId = songId?.trim()?.takeIf { it.isNotEmpty() } ?: getSmpSongId(uri)
    )
}

private fun createTempPackageFile(context: Context): File {
    val dir = File(context.cacheDir, "smp_sync_packages")
    if (!dir.exists()) dir.mkdirs()
    return File(dir, "sync_${System.currentTimeMillis()}_${UUID.randomUUID()}.smpsync")
}

private fun writeStringEntry(zip: ZipOutputStream, entryName: String, text: String) {
    zip.putNextEntry(ZipEntry(entryName))
    zip.write(text.toByteArray(Charsets.UTF_8))
    zip.closeEntry()
}

private fun readPackageFromArchive(file: File): SmpSyncPackage? {
    return ZipFile(file).use { zip ->
        val entry = zip.getEntry(SYNC_PACKAGE_ENTRY) ?: return@use null
        zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { reader ->
            SmpSyncPackage.fromJsonOrNull(reader.readText())
        }
    }
}

private fun readSourceManifestFromArchive(file: File): SmpSyncManifest? {
    return ZipFile(file).use { zip ->
        val entry = zip.getEntry(SYNC_SOURCE_ANALYSIS_ENTRY) ?: return@use null
        zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { reader ->
            SmpSyncManifest.fromJsonOrNull(reader.readText())
        }
    }
}

private fun readPlaylistStateFromArchive(file: File): SmpSyncPlaylistState? {
    return ZipFile(file).use { zip ->
        val entry = zip.getEntry(SYNC_PLAYLIST_STATE_ENTRY) ?: return@use null
        zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { reader ->
            SmpSyncPlaylistState.fromJsonOrNull(reader.readText())
        }
    }
}

private fun validateSongEntries(file: File, syncPackage: SmpSyncPackage): Boolean {
    return ZipFile(file).use { zip ->
        syncPackage.items
            .filter { it.kind == SmpSyncPackageKind.SONG_FULL }
            .all { item ->
                val entryName = item.contentEntry ?: return@all false
                val entry = zip.getEntry(entryName) ?: return@all false
                !entry.isDirectory && entry.size <= SYNC_MAX_PACKAGE_BYTES
            }
    }
}

private fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
        val buffer = ByteArray(SYNC_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read > 0) digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

private fun JSONObject.optStringOrNull(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return optString(key).trim().takeIf { it.isNotEmpty() }
}
