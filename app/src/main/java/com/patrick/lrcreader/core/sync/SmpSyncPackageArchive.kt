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
import com.patrick.lrcreader.core.lyrics.LyricsMemoryCache
import com.patrick.lrcreader.smp.SmpExporter
import com.patrick.lrcreader.smp.SmpLibraryScanner
import com.patrick.lrcreader.smp.SmpRuntimeSongCache
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
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private const val SYNC_PACKAGE_ENTRY = "package.json"
private const val SYNC_SOURCE_ANALYSIS_ENTRY = "source_analysis.json"
private const val SYNC_PLAYLIST_STATE_ENTRY = "playlist_state.json"
private const val SYNC_SONGS_DIR = "songs"
private const val SYNC_BUFFER_SIZE = 128 * 1024
private const val SYNC_MAX_PACKAGE_BYTES = 2_000L * 1024L * 1024L
private const val SYNC_PACKAGE_DIAG_TAG = "SMP_SYNC_PACKAGE_DIAG"
private const val SYNC_IMPORT_DIAG_TAG = "SMP_SYNC_IMPORT_DIAG"
private const val SYNC_EDGE_DIAG_TAG = "SMP_SYNC_EDGE_DIAG"
private const val SYNC_SETTINGS_DIAG_TAG = "SMP_SYNC_SETTINGS_DIAG"
private const val SYNC_LYRICS_DIAG_TAG = "SMP_SYNC_LYRICS_DIAG"
private const val SYNC_ARRANGEMENT_DIAG_TAG = "SMP_SYNC_ARRANGEMENT_DIAG"
private const val SYNC_MANUAL_IMPORT_DIAG_TAG = "SMP_SYNC_MANUAL_IMPORT_DIAG"
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
    val importedSongIds: List<String> = emptyList(),
    val postImportDiagnostics: SmpSyncPostImportDiagnostics? = null,
    val failureReason: String? = null
) {
    val isSuccess: Boolean
        get() = failureReason == null
}

data class SmpSyncPostImportDiagnostics(
    val remainingPlan: SyncPlan,
    val planDiagnostics: SmpSyncPlanDiagnostics,
    val importedSongIds: List<String>,
    val missingRuntimeSongIds: List<String>
) {
    val remainingItemCount: Int
        get() = remainingPlan.items.count { it.action != SyncPlanAction.KEEP }

    val isUpToDate: Boolean
        get() = remainingItemCount == 0 && missingRuntimeSongIds.isEmpty()
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
        val importedIds = linkedSetOf<String>()
        var importedSongs = 0
        var replacedSongs = 0

        Log.i(
            SYNC_IMPORT_DIAG_TAG,
            "import_archive_start file=${receivedPackage.file.absolutePath} songs=${receivedPackage.fullSongCount} playlists=${receivedPackage.syncPackage.playlistStateCount}"
        )
        ZipFile(receivedPackage.file).use { zip ->
            receivedPackage.syncPackage.items
                .filter { it.kind == SmpSyncPackageKind.SONG_FULL }
                .forEach { item ->
                    Log.i(
                        SYNC_IMPORT_DIAG_TAG,
                        "import_item_start songId=${item.entityId} title=${item.title ?: item.entityId}"
                    )
                    val entryName = item.contentEntry
                        ?: return@withContext SmpSyncPackageImportResult(
                            importedSongCount = importedSongs,
                            replacedSongCount = replacedSongs,
                            playlistCount = 0,
                            familyCount = 0,
                            importedSongIds = importedIds.toList(),
                            failureReason = "contenu manquant pour ${item.title ?: item.entityId}"
                        )
                    val entry = zip.getEntry(entryName)
                        ?: return@withContext SmpSyncPackageImportResult(
                            importedSongCount = importedSongs,
                            replacedSongCount = replacedSongs,
                            playlistCount = 0,
                            familyCount = 0,
                            importedSongIds = importedIds.toList(),
                            failureReason = "fichier absent dans le contenu reçu: ${item.title ?: item.entityId}"
                        )
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
                        val runtimeDirBefore = File(context.filesDir, "tracks/${item.entityId}")
                        val lyricsBefore = File(runtimeDirBefore, "lyrics.lrc")
                        val beforeLyricsHash = lyricsBefore.takeIf { it.isFile }?.let(::sha256)
                        val packageHasLyrics = tempSmp.hasZipFileNamed("lyrics.lrc")
                        Log.i(
                            SYNC_MANUAL_IMPORT_DIAG_TAG,
                            "manual_import:start songId=${item.entityId} title=${item.title ?: item.entityId} existsBefore=$exists packageHasLyrics=$packageHasLyrics lyricsBeforeHash=${beforeLyricsHash ?: "null"} lyricsBeforePath=${lyricsBefore.absolutePath}"
                        )
                        Log.i(
                            SYNC_IMPORT_DIAG_TAG,
                            "smp_import_start songId=${item.entityId} title=${item.title ?: item.entityId} temp=${tempSmp.absolutePath}"
                        )
                        val result = SmpSecureImportPipeline(context).import(
                            uri = Uri.fromFile(tempSmp),
                            preserveExistingLyricsOnReplace = false
                        )
                        val importedSong = result.importedSong
                            ?: return@withContext SmpSyncPackageImportResult(
                                importedSongCount = importedSongs,
                                replacedSongCount = replacedSongs,
                                playlistCount = 0,
                                familyCount = 0,
                                importedSongIds = importedIds.toList(),
                                failureReason = result.failureReason ?: "import SMP impossible"
                            )
                        if (importedSong.id != item.entityId) {
                            Log.e(
                                SYNC_IMPORT_DIAG_TAG,
                                "post_import:song_id_mismatch expected=${item.entityId} actual=${importedSong.id} title=${item.title ?: importedSong.title}"
                            )
                            return@withContext SmpSyncPackageImportResult(
                                importedSongCount = importedSongs,
                                replacedSongCount = replacedSongs,
                                playlistCount = 0,
                                familyCount = 0,
                                importedSongIds = importedIds.toList() + importedSong.id,
                                failureReason = "songId importé différent pour ${item.title ?: item.entityId}"
                            )
                        }
                        val runtimeDir = File(context.filesDir, "tracks/${item.entityId}")
                        val lyricsAfter = File(runtimeDir, "lyrics.lrc")
                        val afterLyricsHash = lyricsAfter.takeIf { it.isFile }?.let(::sha256)
                        Log.i(
                            SYNC_IMPORT_DIAG_TAG,
                            "smp_import_done songId=${importedSong.id} path=${runtimeDir.absolutePath}"
                        )
                        Log.i(
                            SYNC_IMPORT_DIAG_TAG,
                            "post_import:runtime_check songId=${item.entityId} title=${item.title ?: importedSong.title} dir=${runtimeDir.absolutePath} exists=${runtimeDir.isDirectory}"
                        )
                        Log.i(
                            SYNC_IMPORT_DIAG_TAG,
                            "runtime_track_exists songId=${item.entityId} value=${runtimeDir.isDirectory}"
                        )
                        Log.i(
                            SYNC_MANUAL_IMPORT_DIAG_TAG,
                            "manual_import:done songId=${item.entityId} title=${item.title ?: importedSong.title} replaced=$exists runtimeExists=${runtimeDir.isDirectory} packageHasLyrics=$packageHasLyrics lyricsAfterHash=${afterLyricsHash ?: "null"} lyricsAfterPath=${lyricsAfter.absolutePath}"
                        )
                        if (!runtimeDir.isDirectory) {
                            return@withContext SmpSyncPackageImportResult(
                                importedSongCount = importedSongs,
                                replacedSongCount = replacedSongs,
                                playlistCount = 0,
                                familyCount = 0,
                                importedSongIds = importedIds.toList(),
                                failureReason = "morceau importé introuvable dans le runtime: ${item.title ?: item.entityId}"
                            )
                        }
                        LyricsMemoryCache.invalidate(buildSmpItem(importedSong.id))
                        importedSong.audioPath?.let(LyricsMemoryCache::invalidate)
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
        val postImportDiagnostics = buildPostImportDiagnostics(
            receivedPackage = receivedPackage,
            importedSongIds = importedIds.toList()
        )
        Log.i(
            SYNC_IMPORT_DIAG_TAG,
            "cache_refresh_start imported=${importedIds.size}"
        )
        val refreshedSongs = SmpLibraryScanner(context).listSongs()
        SmpRuntimeSongCache.save(context, refreshedSongs)
        Log.i(
            SYNC_IMPORT_DIAG_TAG,
            "cache_refresh_done count=${refreshedSongs.size} importedVisible=${importedIds.count { songId -> refreshedSongs.any { it.id == songId } }}"
        )

        SmpSyncPackageImportResult(
            importedSongCount = importedSongs,
            replacedSongCount = replacedSongs,
            playlistCount = playlistCount,
            familyCount = familyCount,
            importedSongIds = importedIds.toList(),
            postImportDiagnostics = postImportDiagnostics
        )
    }

    private suspend fun buildPostImportDiagnostics(
        receivedPackage: SmpSyncReceivedPackage,
        importedSongIds: List<String>
    ): SmpSyncPostImportDiagnostics {
        val localManifest = SmpSyncManifestGenerator().generateFromSources(
            appVersion = receivedPackage.sourceManifest.appVersion,
            deviceId = "post-import-local",
            songs = SmpLibraryScanner(context)
                .listSongs()
                .map { song ->
                    SmpSyncSongManifestSource(
                        songId = song.id,
                        title = song.title,
                        audioFile = song.audioPath.toFileOrNull(),
                        lyricsFile = song.lyricsPath.toFileOrNull(),
                        chordsFile = song.chordsPath.toFileOrNull(),
                        prompterFile = song.prompterPath.toFileOrNull(),
                        timelineFile = song.timelinePath.toFileOrNull(),
                        midiFile = song.midiPath.toFileOrNull(),
                        dmxFile = song.dmxPath.toFileOrNull(),
                        settingsFile = song.storageFolder.resolveExisting("config.json"),
                        arrangementFile = song.storageFolder.resolveExisting("arrangement.json"),
                        gridFile = song.storageFolder.resolveExisting("grid.json")
                    )
                },
            playlists = PlaylistRepository.getPlaylists().map { playlistName ->
                SmpSyncPlaylistManifestSource(
                    playlistName = playlistName,
                    items = PlaylistRepository.getAllItemsRaw(playlistName),
                    colorArgb = PlaylistRepository.getPlaylistColor(playlistName)
                )
            },
            families = SongVariantFamiliesStore.load(context).map { family ->
                SmpSyncFamilyManifestSource(
                    familyId = family.id,
                    title = family.title,
                    songIds = family.songIds.toList(),
                    parentSongId = family.parentSongId,
                    activeSongId = family.activeSongId
                )
            }
        )
        val remainingPlan = SmpSyncManifestComparator().compare(
            source = receivedPackage.sourceManifest,
            target = localManifest
        )
        val planDiagnostics = SmpSyncDiffDiagnosticsBuilder().build(
            source = receivedPackage.sourceManifest,
            target = localManifest,
            plan = remainingPlan,
            syncPackage = receivedPackage.syncPackage
        )
        logEdgeDiagnostics(
            sourceManifest = receivedPackage.sourceManifest,
            targetManifest = localManifest,
            remainingPlan = remainingPlan,
            syncPackage = receivedPackage.syncPackage,
            planDiagnostics = planDiagnostics
        )
        logSettingsHashDiagnostics(
            receivedPackage = receivedPackage,
            localManifest = localManifest,
            remainingPlan = remainingPlan
        )
        logLyricsHashDiagnostics(
            receivedPackage = receivedPackage,
            localManifest = localManifest,
            remainingPlan = remainingPlan
        )
        logArrangementHashDiagnostics(
            receivedPackage = receivedPackage,
            localManifest = localManifest,
            remainingPlan = remainingPlan
        )
        val localById = localManifest.songs.associateBy { it.songId }
        receivedPackage.syncPackage.items
            .filter { it.kind == SmpSyncPackageKind.SONG_FULL }
            .forEach { item ->
                val sourceSong = receivedPackage.sourceManifest.songs.firstOrNull { it.songId == item.entityId }
                val localSong = localById[item.entityId]
                Log.i(
                    SYNC_IMPORT_DIAG_TAG,
                    "post_import:hash_check songId=${item.entityId} title=${item.title ?: sourceSong?.title ?: item.entityId} sourceFull=${sourceSong?.fullSongHash ?: "null"} localFull=${localSong?.fullSongHash ?: "null"} sourceAudio=${sourceSong?.audioHash ?: "null"} localAudio=${localSong?.audioHash ?: "null"} sourceLyrics=${sourceSong?.lyricsHash ?: "null"} localLyrics=${localSong?.lyricsHash ?: "null"} sourceSettings=${sourceSong?.settingsHash ?: "null"} localSettings=${localSong?.settingsHash ?: "null"}"
                )
            }
        val missingRuntimeSongIds = importedSongIds.filterNot { songId ->
            File(context.filesDir, "tracks/$songId").isDirectory
        }
        Log.i(
            SYNC_IMPORT_DIAG_TAG,
            "post_import:summary imported=${importedSongIds.size} missingRuntime=${missingRuntimeSongIds.size} remainingItems=${remainingPlan.items.size} remainingFullSongs=${planDiagnostics.fullSongCount}"
        )
        return SmpSyncPostImportDiagnostics(
            remainingPlan = remainingPlan,
            planDiagnostics = planDiagnostics,
            importedSongIds = importedSongIds,
            missingRuntimeSongIds = missingRuntimeSongIds
        )
    }

    private fun logEdgeDiagnostics(
        sourceManifest: SmpSyncManifest,
        targetManifest: SmpSyncManifest,
        remainingPlan: SyncPlan,
        syncPackage: SmpSyncPackage,
        planDiagnostics: SmpSyncPlanDiagnostics
    ) {
        val actionableSongs = remainingPlan.items
            .withIndex()
            .filter { indexed ->
                indexed.value.diff.entityType == SyncEntityType.SONG &&
                    indexed.value.action != SyncPlanAction.KEEP
            }
        val edgeSongs = listOfNotNull(
            actionableSongs.firstOrNull(),
            actionableSongs.lastOrNull()
        ).distinctBy { it.value.diff.entityId }
        if (edgeSongs.isEmpty()) {
            Log.i(SYNC_EDGE_DIAG_TAG, "edge:no_actionable_song_after_import")
            return
        }

        val sourceById = sourceManifest.songs.associateBy { it.songId }
        val targetById = targetManifest.songs.associateBy { it.songId }
        val targetByTitle = targetManifest.songs.groupBy { it.title.normalizedTitleKey() }
        val packageById = syncPackage.items.associateBy { it.entityId }
        val diagnosticsById = planDiagnostics.modifiedSongs.associateBy { it.sourceSongId }
        val playlistDiagnosticsByName = planDiagnostics.modifiedPlaylists.associateBy { it.playlistName }

        edgeSongs.forEach { indexed ->
            val item = indexed.value
            val sourceSong = sourceById[item.diff.entityId]
            val targetSong = targetById[item.diff.entityId]
                ?: sourceSong?.let { targetByTitle[it.title.normalizedTitleKey()].orEmpty().firstOrNull() }
            val sourceIndex = sourceManifest.songs.indexOfFirst { it.songId == item.diff.entityId }
            val targetIndex = targetManifest.songs.indexOfFirst { it.songId == targetSong?.songId }
            val runtimeDir = File(context.filesDir, "tracks/${item.diff.entityId}")
            val diagnostic = diagnosticsById[item.diff.entityId]
            val packageKind = packageById[item.diff.entityId]?.kind ?: item.inferredEdgePackageKind()
            val components = if (sourceSong != null && targetSong != null) {
                sourceSong.componentDifferences(targetSong)
            } else {
                emptyList()
            }

            Log.i(
                SYNC_EDGE_DIAG_TAG,
                "edge:planIndex=${indexed.index} sourceIndex=$sourceIndex targetIndex=$targetIndex title=${item.diff.title ?: sourceSong?.title ?: targetSong?.title ?: item.diff.entityId} sourceSongId=${item.diff.entityId} targetSongId=${targetSong?.songId ?: "null"} status=${item.diff.status} action=${item.action} reason=${diagnostic?.primaryReason ?: item.diff.status.name} packageKind=${packageKind ?: "none"} runtimeExists=${runtimeDir.isDirectory} sourcePlaylists=${sourceManifest.playlistRefs(item.diff.entityId)} targetPlaylists=${targetManifest.playlistRefs(targetSong?.songId ?: item.diff.entityId)} components=${components.joinToString().ifBlank { "none" }} sourceFull=${sourceSong?.fullSongHash ?: "null"} targetFull=${targetSong?.fullSongHash ?: "null"} sourceAudio=${sourceSong?.audioHash ?: "null"} targetAudio=${targetSong?.audioHash ?: "null"} sourceLyrics=${sourceSong?.lyricsHash ?: "null"} targetLyrics=${targetSong?.lyricsHash ?: "null"} sourceChords=${sourceSong?.chordsHash ?: "null"} targetChords=${targetSong?.chordsHash ?: "null"} sourceNotes=${sourceSong?.notesHash ?: "null"} targetNotes=${targetSong?.notesHash ?: "null"} sourcePrompter=${sourceSong?.prompterHash ?: "null"} targetPrompter=${targetSong?.prompterHash ?: "null"} sourceTimeline=${sourceSong?.timelineHash ?: "null"} targetTimeline=${targetSong?.timelineHash ?: "null"} sourceMidi=${sourceSong?.midiHash ?: "null"} targetMidi=${targetSong?.midiHash ?: "null"} sourceDmx=${sourceSong?.dmxHash ?: "null"} targetDmx=${targetSong?.dmxHash ?: "null"} sourceSettings=${sourceSong?.settingsHash ?: "null"} targetSettings=${targetSong?.settingsHash ?: "null"} sourceArrangement=${sourceSong?.arrangementHash ?: "null"} targetArrangement=${targetSong?.arrangementHash ?: "null"} sourceGrid=${sourceSong?.gridHash ?: "null"} targetGrid=${targetSong?.gridHash ?: "null"}"
            )
        }

        remainingPlan.items
            .filter { item ->
                item.diff.entityType == SyncEntityType.PLAYLIST &&
                    item.action == SyncPlanAction.UPDATE_PLAYLIST_ON_B
            }
            .forEach { item ->
                val diagnostic = playlistDiagnosticsByName[item.diff.title ?: item.diff.entityId]
                Log.i(
                    SYNC_EDGE_DIAG_TAG,
                    "edge:playlist name=${item.diff.title ?: item.diff.entityId} status=${item.diff.status} action=${item.action} reason=${diagnostic?.primaryReason ?: item.diff.status.name} packageKind=${packageById[item.diff.entityId]?.kind ?: SmpSyncPackageKind.PLAYLIST_STATE} components=${diagnostic?.differentComponents?.joinToString()?.ifBlank { "none" } ?: "none"} sourceItems=${diagnostic?.sourceItemsHash ?: "null"} targetItems=${diagnostic?.targetItemsHash ?: "null"} sourceGroups=${diagnostic?.sourceGroupsHash ?: "null"} targetGroups=${diagnostic?.targetGroupsHash ?: "null"} sourceColors=${diagnostic?.sourceColorsHash ?: "null"} targetColors=${diagnostic?.targetColorsHash ?: "null"} sourceFull=${diagnostic?.sourceFullPlaylistHash ?: "null"} targetFull=${diagnostic?.targetFullPlaylistHash ?: "null"} sourceSongIds=${diagnostic?.sourceSongIds?.joinToString(prefix = "[", postfix = "]") ?: "[]"} targetSongIds=${diagnostic?.targetSongIds?.joinToString(prefix = "[", postfix = "]") ?: "[]"}"
                )
            }
    }

    private fun logSettingsHashDiagnostics(
        receivedPackage: SmpSyncReceivedPackage,
        localManifest: SmpSyncManifest,
        remainingPlan: SyncPlan
    ) {
        val sourceById = receivedPackage.sourceManifest.songs.associateBy { it.songId }
        val localById = localManifest.songs.associateBy { it.songId }
        val settingsItems = remainingPlan.items
            .filter { item ->
                item.diff.entityType == SyncEntityType.SONG &&
                    item.action == SyncPlanAction.COPY_TO_B
            }
            .mapNotNull { item ->
                val sourceSong = sourceById[item.diff.entityId] ?: return@mapNotNull null
                val localSong = localById[item.diff.entityId] ?: return@mapNotNull null
                if (sourceSong.settingsHash == localSong.settingsHash) return@mapNotNull null
                item to sourceSong
            }
            .take(5)
        if (settingsItems.isEmpty()) return

        ZipFile(receivedPackage.file).use { packageZip ->
            settingsItems.forEach { (item, sourceSong) ->
                val localConfig = File(context.filesDir, "tracks/${item.diff.entityId}/config.json")
                    .takeIf { it.isFile }
                    ?.readText(Charsets.UTF_8)
                val sourceConfig = readSourceConfigFromPackage(
                    packageZip = packageZip,
                    syncPackage = receivedPackage.syncPackage,
                    songId = item.diff.entityId
                )
                val sourceCanonical = sourceConfig
                    ?.let { SmpSyncHashing().syncSettingsCanonicalTextOrNull(it) }
                val localCanonical = localConfig
                    ?.let { SmpSyncHashing().syncSettingsCanonicalTextOrNull(it) }
                val diffs = diffCanonicalJsonFields(sourceCanonical, localCanonical)
                Log.i(
                    SYNC_SETTINGS_DIAG_TAG,
                    "settings:song title=${sourceSong.title} songId=${item.diff.entityId} status=${item.diff.status} sourceHash=${sourceSong.settingsHash ?: "null"} targetHash=${localById[item.diff.entityId]?.settingsHash ?: "null"} fields=${diffs.joinToString { diff -> "${diff.path}:A=${diff.sourceValue ?: "null"}|B=${diff.targetValue ?: "null"}" }.ifBlank { "canonical_unavailable_or_equal" }}"
                )
            }
        }
    }

    private fun readSourceConfigFromPackage(
        packageZip: ZipFile,
        syncPackage: SmpSyncPackage,
        songId: String
    ): String? {
        return readSourceSongEntryFromPackage(
            packageZip = packageZip,
            syncPackage = syncPackage,
            songId = songId,
            entryName = "config.json"
        )
    }

    private fun logLyricsHashDiagnostics(
        receivedPackage: SmpSyncReceivedPackage,
        localManifest: SmpSyncManifest,
        remainingPlan: SyncPlan
    ) {
        val sourceById = receivedPackage.sourceManifest.songs.associateBy { it.songId }
        val localById = localManifest.songs.associateBy { it.songId }
        val hashing = SmpSyncHashing()
        val lyricsItems = remainingPlan.items
            .filter { item ->
                item.diff.entityType == SyncEntityType.SONG &&
                    item.action == SyncPlanAction.COPY_TO_B
            }
            .mapNotNull { item ->
                val sourceSong = sourceById[item.diff.entityId] ?: return@mapNotNull null
                val localSong = localById[item.diff.entityId] ?: return@mapNotNull null
                if (sourceSong.lyricsHash == localSong.lyricsHash) return@mapNotNull null
                item to sourceSong
            }
            .take(5)
        if (lyricsItems.isEmpty()) return

        ZipFile(receivedPackage.file).use { packageZip ->
            lyricsItems.forEach { (item, sourceSong) ->
                val sourceLyrics = readSourceSongEntryFromPackage(
                    packageZip = packageZip,
                    syncPackage = receivedPackage.syncPackage,
                    songId = item.diff.entityId,
                    entryName = "lyrics.lrc"
                )
                val localLyrics = File(context.filesDir, "tracks/${item.diff.entityId}/lyrics.lrc")
                    .takeIf { it.isFile }
                    ?.readText(Charsets.UTF_8)
                val diff = diffLyricsText(sourceLyrics, localLyrics, hashing)
                Log.i(
                    SYNC_LYRICS_DIAG_TAG,
                    "lyrics:song title=${sourceSong.title} songId=${item.diff.entityId} status=${item.diff.status} sourceHash=${sourceSong.lyricsHash ?: "null"} targetHash=${localById[item.diff.entityId]?.lyricsHash ?: "null"} sourceLen=${sourceLyrics?.length ?: -1} targetLen=${localLyrics?.length ?: -1} sourceNormLen=${diff.sourceNormalizedLength} targetNormLen=${diff.targetNormalizedLength} sourceLines=${diff.sourceLineCount} targetLines=${diff.targetLineCount} firstDiffLine=${diff.firstDifferentLine ?: -1} invisible=${diff.invisibleFlags.joinToString().ifBlank { "none" }} sourceExcerpt=${diff.sourceExcerpt ?: "null"} targetExcerpt=${diff.targetExcerpt ?: "null"}"
                )
            }
        }
    }

    private fun logArrangementHashDiagnostics(
        receivedPackage: SmpSyncReceivedPackage,
        localManifest: SmpSyncManifest,
        remainingPlan: SyncPlan
    ) {
        val sourceById = receivedPackage.sourceManifest.songs.associateBy { it.songId }
        val localById = localManifest.songs.associateBy { it.songId }
        val hashing = SmpSyncHashing()
        val arrangementItems = remainingPlan.items
            .filter { item ->
                item.diff.entityType == SyncEntityType.SONG &&
                    item.action == SyncPlanAction.COPY_TO_B
            }
            .mapNotNull { item ->
                val sourceSong = sourceById[item.diff.entityId] ?: return@mapNotNull null
                val localSong = localById[item.diff.entityId] ?: return@mapNotNull null
                if (sourceSong.arrangementHash == localSong.arrangementHash) return@mapNotNull null
                item to sourceSong
            }
            .take(5)
        if (arrangementItems.isEmpty()) return

        ZipFile(receivedPackage.file).use { packageZip ->
            arrangementItems.forEach { (item, sourceSong) ->
                val sourceArrangement = readSourceSongEntryFromPackage(
                    packageZip = packageZip,
                    syncPackage = receivedPackage.syncPackage,
                    songId = item.diff.entityId,
                    entryName = "arrangement.json"
                )
                val localArrangement = File(context.filesDir, "tracks/${item.diff.entityId}/arrangement.json")
                    .takeIf { it.isFile }
                    ?.readText(Charsets.UTF_8)
                val sourceCanonical = sourceArrangement?.let(hashing::syncArrangementCanonicalTextOrNull)
                val localCanonical = localArrangement?.let(hashing::syncArrangementCanonicalTextOrNull)
                val diffs = diffCanonicalJsonFields(sourceCanonical, localCanonical, rootPath = "arrangement")
                Log.i(
                    SYNC_ARRANGEMENT_DIAG_TAG,
                    "arrangement:song title=${sourceSong.title} songId=${item.diff.entityId} status=${item.diff.status} sourceHash=${sourceSong.arrangementHash ?: "null"} targetHash=${localById[item.diff.entityId]?.arrangementHash ?: "null"} fields=${diffs.joinToString { diff -> "${diff.path}:A=${diff.sourceValue ?: "null"}|B=${diff.targetValue ?: "null"}" }.ifBlank { "canonical_unavailable_or_equal" }}"
                )
            }
        }
    }

    private fun readSourceSongEntryFromPackage(
        packageZip: ZipFile,
        syncPackage: SmpSyncPackage,
        songId: String,
        entryName: String
    ): String? {
        val contentEntry = syncPackage.items
            .firstOrNull { item ->
                item.kind == SmpSyncPackageKind.SONG_FULL &&
                    item.entityId == songId
            }
            ?.contentEntry
            ?: return null
        val smpEntry = packageZip.getEntry(contentEntry) ?: return null
        return packageZip.getInputStream(smpEntry).use { smpInput ->
            ZipInputStream(smpInput).use { smpZip ->
                while (true) {
                    val entry = smpZip.nextEntry ?: break
                    if (!entry.isDirectory && entry.name == entryName) {
                        return smpZip.bufferedReader(Charsets.UTF_8).readText()
                    }
                }
            }
            null
        }
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
            val appliedOrder = playlist.items.mapNotNull { item ->
                val cleanUri = item.uri.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val songId = item.songId?.trim()?.takeIf { it.isNotEmpty() } ?: getSmpSongId(cleanUri)
                when {
                    isVirtualPlaylistItem(cleanUri) -> cleanUri
                    songId != null && songId in availableSongIds -> buildSmpItem(songId)
                    else -> null
                }
            }
            PlaylistRepository.updatePlayListOrder(cleanName, appliedOrder)
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

private fun File.hasZipFileNamed(fileName: String): Boolean {
    if (!isFile) return false
    return runCatching {
        ZipFile(this).use { zip ->
            zip.entries().asSequence().any { entry ->
                !entry.isDirectory &&
                    entry.name.substringAfterLast('/').equals(fileName, ignoreCase = true)
            }
        }
    }.getOrDefault(false)
}

private fun JSONObject.optStringOrNull(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return optString(key).trim().takeIf { it.isNotEmpty() }
}

private data class SmpSyncJsonFieldDiff(
    val path: String,
    val sourceValue: String?,
    val targetValue: String?
)

private data class SmpSyncLyricsTextDiff(
    val sourceNormalizedLength: Int,
    val targetNormalizedLength: Int,
    val sourceLineCount: Int,
    val targetLineCount: Int,
    val firstDifferentLine: Int?,
    val sourceExcerpt: String?,
    val targetExcerpt: String?,
    val invisibleFlags: List<String>
)

private fun diffCanonicalJsonFields(
    sourceCanonical: String?,
    targetCanonical: String?,
    rootPath: String = "settings"
): List<SmpSyncJsonFieldDiff> {
    if (sourceCanonical == null || targetCanonical == null) {
        return listOf(
            SmpSyncJsonFieldDiff(
                path = rootPath,
                sourceValue = sourceCanonical?.truncateDiagValue(),
                targetValue = targetCanonical?.truncateDiagValue()
            )
        )
    }
    val sourceJson = runCatching { JSONObject(sourceCanonical) }.getOrNull()
    val targetJson = runCatching { JSONObject(targetCanonical) }.getOrNull()
    if (sourceJson == null || targetJson == null) {
        return listOf(
            SmpSyncJsonFieldDiff(
                path = rootPath,
                sourceValue = sourceCanonical.truncateDiagValue(),
                targetValue = targetCanonical.truncateDiagValue()
            )
        )
    }
    val sourceFields = flattenJsonFields(sourceJson)
    val targetFields = flattenJsonFields(targetJson)
    return (sourceFields.keys + targetFields.keys)
        .sorted()
        .mapNotNull { key ->
            val sourceValue = sourceFields[key]
            val targetValue = targetFields[key]
            if (sourceValue == targetValue) return@mapNotNull null
            SmpSyncJsonFieldDiff(
                path = key,
                sourceValue = sourceValue?.truncateDiagValue(),
                targetValue = targetValue?.truncateDiagValue()
            )
        }
        .take(12)
}

private fun diffLyricsText(
    sourceRaw: String?,
    targetRaw: String?,
    hashing: SmpSyncHashing
): SmpSyncLyricsTextDiff {
    val sourceNormalized = sourceRaw?.let(hashing::normalizeLyricsText)
    val targetNormalized = targetRaw?.let(hashing::normalizeLyricsText)
    val sourceLines = sourceNormalized?.split('\n').orEmpty()
    val targetLines = targetNormalized?.split('\n').orEmpty()
    val maxLines = maxOf(sourceLines.size, targetLines.size)
    val firstDiffIndex = (0 until maxLines).firstOrNull { index ->
        sourceLines.getOrNull(index) != targetLines.getOrNull(index)
    }
    return SmpSyncLyricsTextDiff(
        sourceNormalizedLength = sourceNormalized?.length ?: -1,
        targetNormalizedLength = targetNormalized?.length ?: -1,
        sourceLineCount = sourceLines.size.takeIf { sourceNormalized != null } ?: -1,
        targetLineCount = targetLines.size.takeIf { targetNormalized != null } ?: -1,
        firstDifferentLine = firstDiffIndex?.plus(1),
        sourceExcerpt = firstDiffIndex?.let { sourceLines.getOrNull(it)?.truncateDiagValue() },
        targetExcerpt = firstDiffIndex?.let { targetLines.getOrNull(it)?.truncateDiagValue() },
        invisibleFlags = lyricsInvisibleFlags(sourceRaw, targetRaw, sourceNormalized, targetNormalized)
    )
}

private fun lyricsInvisibleFlags(
    sourceRaw: String?,
    targetRaw: String?,
    sourceNormalized: String?,
    targetNormalized: String?
): List<String> {
    if (sourceRaw == null || targetRaw == null) return emptyList()
    return buildList {
        if (sourceRaw.contains("\r") || targetRaw.contains("\r")) add("lineEndings")
        if (sourceRaw.startsWith('\uFEFF') || targetRaw.startsWith('\uFEFF')) add("utf8Bom")
        if (sourceRaw.lines().any { it.endsWith(' ') || it.endsWith('\t') } ||
            targetRaw.lines().any { it.endsWith(' ') || it.endsWith('\t') }
        ) {
            add("trailingSpaces")
        }
        if (sourceRaw.hasFinalBlankLines() || targetRaw.hasFinalBlankLines()) add("finalBlankLines")
        if (sourceNormalized == targetNormalized && sourceRaw != targetRaw) add("normalizedEqual")
    }
}

private fun String.hasFinalBlankLines(): Boolean {
    val normalized = replace("\r\n", "\n").replace('\r', '\n')
    return normalized.endsWith("\n\n") || normalized.lines().dropLastWhile { it.isBlank() }.size < normalized.lines().size - 1
}

private fun flattenJsonFields(json: JSONObject, prefix: String = ""): Map<String, String> {
    val out = linkedMapOf<String, String>()
    json.keys().asSequence().toList().sorted().forEach { key ->
        val path = if (prefix.isBlank()) key else "$prefix.$key"
        when (val value = json.opt(key)) {
            is JSONObject -> out.putAll(flattenJsonFields(value, path))
            else -> out[path] = value?.toString() ?: "null"
        }
    }
    return out
}

private fun String.truncateDiagValue(maxLength: Int = 80): String {
    return if (length <= maxLength) this else take(maxLength) + "..."
}

private fun SyncPlanItem.inferredEdgePackageKind(): SmpSyncPackageKind? {
    if (action != SyncPlanAction.COPY_TO_B) return null
    return when (diff.status) {
        SyncDiffStatus.ABSENT_ON_B,
        SyncDiffStatus.MODIFIED_ON_A -> SmpSyncPackageKind.SONG_FULL
        else -> null
    }
}

private fun SmpSyncSongEntry.componentDifferences(other: SmpSyncSongEntry): List<String> {
    return buildList {
        if (title != other.title) add("title")
        if (audioHash != other.audioHash) add("audioHash")
        if (lyricsHash != other.lyricsHash) add("lyricsHash")
        if (chordsHash != other.chordsHash) add("chordsHash")
        if (notesHash != other.notesHash) add("notesHash")
        if (prompterHash != other.prompterHash) add("prompterHash")
        if (timelineHash != other.timelineHash) add("timelineHash")
        if (midiHash != other.midiHash) add("midiHash")
        if (dmxHash != other.dmxHash) add("dmxHash")
        if (settingsHash != other.settingsHash) add("settingsHash")
        if (arrangementHash != other.arrangementHash) add("arrangementHash")
        if (gridHash != other.gridHash) add("gridHash")
        if (fullSongHash != other.fullSongHash) add("fullSongHash")
    }
}

private fun SmpSyncManifest.playlistRefs(songId: String): List<String> {
    return playlists
        .filter { playlist -> songId in playlist.songIds }
        .map { it.playlistName }
}

private fun String.normalizedTitleKey(): String {
    return trim()
        .lowercase()
        .replace(Regex("\\s+"), " ")
}

private fun String?.toFileOrNull(): File? {
    return this
        ?.takeIf { it.isNotBlank() }
        ?.let(::File)
        ?.takeIf { it.isFile }
}

private fun String?.resolveExisting(fileName: String): File? {
    return this
        ?.takeIf { it.isNotBlank() }
        ?.let(::File)
        ?.let { File(it, fileName) }
        ?.takeIf { it.isFile }
}
