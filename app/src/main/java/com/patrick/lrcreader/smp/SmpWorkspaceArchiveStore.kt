package com.patrick.lrcreader.smp

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.patrick.lrcreader.core.WorkspaceResolver
import java.io.File

object SmpWorkspaceArchiveStore {

    data class PersistResult(
        val archiveUri: Uri?,
        val failureReason: String? = null
    )

    data class DeleteArchivesResult(
        val deletedCount: Int,
        val failedCount: Int,
        val failureReason: String? = null
    ) {
        val isSuccess: Boolean
            get() = failureReason == null && failedCount == 0
    }

    internal data class FilePersistResult(
        val archiveFile: File?,
        val failureReason: String? = null
    )

    internal sealed interface WorkspaceSmpDir {
        data class FileDir(val directory: File) : WorkspaceSmpDir
        data class SafDir(val directory: DocumentFile) : WorkspaceSmpDir
    }

    private data class NormalizationScan<T>(
        val supersededArchives: List<T>,
        val unresolvedCount: Int
    )

    private const val TAG = "SMP_ARCHIVE"
    private const val SAF_TRACE_TAG = "SMP_SAF_CLEANUP_TRACE"
    private const val SAF_ARCHIVE_MIME = "application/octet-stream"
    private const val BACKING_TRACKS_DIR_NAME = "BackingTracks"
    private const val SMP_DIR_NAME = "SMP"
    private val FORBIDDEN_ARCHIVE_NAME_CHARS = Regex("[\\u0000-\\u001F<>:\"/\\\\|?*]+")

    fun persistNormalizedArchive(
        context: Context,
        songUnit: SongUnit,
        snapshotOverride: WorkspaceResolver.Snapshot? = null
    ): PersistResult {
        val songId = songUnit.id.trim()
        if (songId.isEmpty()) {
            return PersistResult(
                archiveUri = null,
                failureReason = "songId SMP manquant pour l'archive durable"
            )
        }

        val workspaceSnapshot = snapshotOverride ?: WorkspaceResolver.resolve(context)
        if (!workspaceSnapshot.isUsable || workspaceSnapshot.workspaceRootUri == null) {
            return PersistResult(
                archiveUri = null,
                failureReason = "workspace durable indisponible pour l'archive SMP"
            )
        }

        val tempArchive = SmpExporter.exportSongUnitToCacheSmp(context, songUnit)
            ?: return PersistResult(
                archiveUri = null,
                failureReason = "export SMP temporaire impossible"
            )

        return try {
            writeArchiveToWorkspace(
                context = context,
                snapshot = workspaceSnapshot,
                songUnit = songUnit,
                songId = songId,
                tempArchive = tempArchive
            )
        } finally {
            if (tempArchive.exists() && !tempArchive.delete()) {
                logWarn("Suppression du cache SMP impossible: ${tempArchive.absolutePath}")
            }
        }
    }

    fun deleteArchivesForSongId(
        context: Context,
        songId: String,
        snapshotOverride: WorkspaceResolver.Snapshot? = null
    ): DeleteArchivesResult {
        val cleanSongId = songId.trim()
        if (cleanSongId.isEmpty()) {
            return DeleteArchivesResult(
                deletedCount = 0,
                failedCount = 0,
                failureReason = "songId SMP manquant pour la suppression archive"
            )
        }

        val workspaceSnapshot = snapshotOverride ?: WorkspaceResolver.resolve(context)
        if (!workspaceSnapshot.isUsable || workspaceSnapshot.workspaceRootUri == null) {
            return DeleteArchivesResult(
                deletedCount = 0,
                failedCount = 0,
                failureReason = "workspace durable indisponible pour la suppression archive SMP"
            )
        }

        val targetDir = resolveWorkspaceSmpDir(
            context = context,
            snapshot = workspaceSnapshot,
            createIfMissing = false
        ) ?: return DeleteArchivesResult(deletedCount = 0, failedCount = 0)

        return when (targetDir) {
            is WorkspaceSmpDir.FileDir -> deleteFileArchivesForSongId(
                targetDir = targetDir.directory,
                songId = cleanSongId
            )

            is WorkspaceSmpDir.SafDir -> deleteSafArchivesForSongId(
                context = context,
                targetDir = targetDir.directory,
                songId = cleanSongId
            )
        }
    }

    private fun writeArchiveToWorkspace(
        context: Context,
        snapshot: WorkspaceResolver.Snapshot,
        songUnit: SongUnit,
        songId: String,
        tempArchive: File
    ): PersistResult {
        val targetName = buildDurableArchiveFileName(songUnit)
        val targetDir = resolveWorkspaceSmpDir(
            context = context,
            snapshot = snapshot,
            createIfMissing = true
        ) ?: return PersistResult(
                archiveUri = null,
                failureReason = "dossier workspace BackingTracks/SMP introuvable"
            )

        return when (targetDir) {
            is WorkspaceSmpDir.FileDir -> writeArchiveToFileDir(
                targetDir = targetDir.directory,
                songId = songId,
                targetName = targetName,
                tempArchive = tempArchive
            )

            is WorkspaceSmpDir.SafDir -> writeArchiveToSafDir(
                context = context,
                targetDir = targetDir.directory,
                songId = songId,
                targetName = targetName,
                tempArchive = tempArchive
            )
        }
    }

    internal fun buildDurableArchiveFileName(songUnit: SongUnit): String {
        val songId = songUnit.id.trim()
        val sanitizedTitle = sanitizeDurableArchiveTitle(songUnit.title)
        return if (sanitizedTitle.isNullOrBlank()) {
            "$songId.smp"
        } else {
            "$sanitizedTitle [$songId].smp"
        }
    }

    internal fun isSupportedArchiveFileName(fileName: String): Boolean {
        val normalized = fileName.trim().lowercase()
        return normalized.endsWith(".smp") || normalized.endsWith(".smp.zip")
    }

    internal fun writeArchiveToFileDir(
        targetDir: File,
        songId: String,
        targetName: String,
        tempArchive: File
    ): PersistResult {
        val result = writeArchiveToFileDirInternal(
            targetDir = targetDir,
            songId = songId,
            targetName = targetName,
            tempArchive = tempArchive
        )
        return PersistResult(
            archiveUri = result.archiveFile?.let { Uri.fromFile(it) },
            failureReason = result.failureReason
        )
    }

    internal fun writeArchiveToFileDirInternal(
        targetDir: File,
        songId: String,
        targetName: String,
        tempArchive: File
    ): FilePersistResult {
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            return FilePersistResult(
                archiveFile = null,
                failureReason = "création du dossier SMP impossible: ${targetDir.absolutePath}"
            )
        }

        val targetFile = File(targetDir, targetName)
        val partFile = File(targetDir, "$targetName.part")
        val normalizationScan = scanSupersededFileArchives(
            targetDir = targetDir,
            songId = songId,
            targetName = targetName
        )

        if (partFile.exists() && !partFile.delete()) {
            return FilePersistResult(
                archiveFile = null,
                failureReason = "suppression du fichier temporaire impossible: ${partFile.absolutePath}"
            )
        }

        return try {
            tempArchive.inputStream().buffered().use { input ->
                partFile.outputStream().buffered().use { output ->
                    input.copyTo(output)
                    output.flush()
                }
            }

            if (targetFile.exists() && !targetFile.isFile) {
                partFile.delete()
                return FilePersistResult(
                    archiveFile = null,
                    failureReason = "un dossier bloque l'archive SMP cible: ${targetFile.absolutePath}"
                )
            }

            val finalized = if (targetFile.exists()) {
                runCatching {
                    partFile.copyTo(targetFile, overwrite = true)
                    true
                }.getOrDefault(false)
            } else {
                partFile.renameTo(targetFile) || runCatching {
                    partFile.copyTo(targetFile, overwrite = true)
                    true
                }.getOrDefault(false)
            }

            if (!finalized) {
                partFile.delete()
                return FilePersistResult(
                    archiveFile = null,
                    failureReason = "finalisation de l'archive SMP impossible: ${targetFile.absolutePath}"
                )
            }

            if (partFile.exists() && !partFile.delete()) {
                logWarn("Suppression du .part SMP impossible: ${partFile.absolutePath}")
            }

            normalizationScan.supersededArchives.forEach { staleFile ->
                if (staleFile.exists() && !staleFile.delete()) {
                    logWarn("Suppression archive SMP obsolète impossible: ${staleFile.absolutePath}")
                }
            }

            logInfo(
                "step=normalize_done backend=file songId=$songId kept=${targetFile.absolutePath} removed=${normalizationScan.supersededArchives.size} skippedUnresolved=${normalizationScan.unresolvedCount}"
            )
            logInfo("Archive SMP durable écrite backend=file path=${targetFile.absolutePath}")
            FilePersistResult(archiveFile = targetFile)
        } catch (error: Exception) {
            logError("Ecriture archive SMP durable impossible backend=file dir=${targetDir.absolutePath}", error)
            partFile.delete()
            FilePersistResult(
                archiveFile = null,
                failureReason = "écriture de l'archive SMP impossible dans ${targetDir.absolutePath}"
            )
        }
    }

    private fun writeArchiveToSafDir(
        context: Context,
        targetDir: DocumentFile,
        songId: String,
        targetName: String,
        tempArchive: File
    ): PersistResult {
        traceSafDirectorySnapshot(
            context = context,
            targetDir = targetDir,
            step = "initial_scan_before_write",
            resolveSongIds = false
        )
        val existing = findChildIgnoreCase(targetDir, targetName)
        traceSafInfo(
            "step=write_start dir=${targetDir.uri} targetSongId=$songId targetName=$targetName existingName=${existing?.name.orEmpty()} existingUri=${existing?.uri} existingIsFile=${existing?.isFile}"
        )
        if (existing != null && !existing.isFile) {
            traceSafWarn(
                "step=write_blocked_by_directory dir=${targetDir.uri} targetSongId=$songId targetName=$targetName existingUri=${existing.uri}"
            )
            return PersistResult(
                archiveUri = null,
                failureReason = "un dossier bloque l'archive SMP cible: ${existing.uri}"
            )
        }

        val normalizationScan = scanSupersededSafArchives(
            context = context,
            targetDir = targetDir,
            songId = songId,
            targetName = targetName
        ).let { scan ->
            scan.copy(
                supersededArchives = scan.supersededArchives.filterNot { stale ->
                    existing != null && stale.uri == existing.uri
                }
            )
        }
        traceSafInfo(
            "step=cleanup_plan targetSongId=$songId targetName=$targetName duplicates=${summarizeDocuments(normalizationScan.supersededArchives)} duplicatesCount=${normalizationScan.supersededArchives.size} unresolvedCount=${normalizationScan.unresolvedCount}"
        )

        val targetFile = existing ?: createSafArchiveTarget(targetDir, targetName)
        if (targetFile == null) {
            traceSafWarn(
                "step=write_target_create_failed dir=${targetDir.uri} targetSongId=$songId targetName=$targetName"
            )
            return PersistResult(
                archiveUri = null,
                failureReason = "création de l'archive SMP impossible dans ${targetDir.uri}"
            )
        }
        traceSafInfo(
            "step=write_target_ready requestedName=$targetName actualName=${targetFile.name.orEmpty()} targetUri=${targetFile.uri} reusedExisting=${existing != null}"
        )

        return try {
            context.contentResolver.openOutputStream(targetFile.uri, "wt")?.use { output ->
                tempArchive.inputStream().buffered().use { input ->
                    input.copyTo(output)
                    output.flush()
                }
            } ?: return PersistResult(
                archiveUri = null,
                failureReason = "flux d'écriture SAF indisponible pour ${targetFile.uri}"
            )
            traceSafInfo(
                "step=write_success targetSongId=$songId requestedName=$targetName actualName=${targetFile.name.orEmpty()} targetUri=${targetFile.uri}"
            )

            normalizationScan.supersededArchives.forEach { staleFile ->
                traceSafInfo(
                    "step=delete_attempt targetSongId=$songId name=${staleFile.name.orEmpty()} uri=${staleFile.uri}"
                )
                val deleted = staleFile.delete()
                val stillListed = targetDir.listFiles().any { listed ->
                    listed.uri == staleFile.uri
                }
                traceSafInfo(
                    "step=delete_result targetSongId=$songId name=${staleFile.name.orEmpty()} uri=${staleFile.uri} deleteResult=$deleted stillListedAfterDelete=$stillListed"
                )
                if (!deleted) {
                    logWarn("Suppression archive SMP SAF obsolète impossible: ${staleFile.uri}")
                    traceSafWarn(
                        "step=delete_failed targetSongId=$songId name=${staleFile.name.orEmpty()} uri=${staleFile.uri} stillListedAfterDelete=$stillListed"
                    )
                }
            }

            traceSafDirectorySnapshot(
                context = context,
                targetDir = targetDir,
                step = "final_state_after_cleanup",
                resolveSongIds = true
            )

            logInfo(
                "step=normalize_done backend=saf songId=$songId kept=${targetFile.uri} removed=${normalizationScan.supersededArchives.size} skippedUnresolved=${normalizationScan.unresolvedCount}"
            )
            logInfo("Archive SMP durable écrite backend=saf uri=${targetFile.uri}")
            PersistResult(archiveUri = targetFile.uri)
        } catch (error: Exception) {
            traceSafWarn(
                "step=write_failed dir=${targetDir.uri} targetSongId=$songId targetName=$targetName message=${error.message ?: "unknown"} type=${error.javaClass.simpleName}",
                error
            )
            logError("Ecriture archive SMP durable impossible backend=saf dir=${targetDir.uri}", error)
            PersistResult(
                archiveUri = null,
                failureReason = "écriture SAF de l'archive SMP impossible dans ${targetDir.uri}"
            )
        }
    }

    internal fun resolveWorkspaceSmpDir(
        context: Context,
        snapshot: WorkspaceResolver.Snapshot,
        createIfMissing: Boolean
    ): WorkspaceSmpDir? {
        val rootUri = snapshot.workspaceRootUri ?: return null
        return when (rootUri.scheme) {
            "file" -> resolveWorkspaceFileSmpDir(rootUri, createIfMissing)
                ?.let(WorkspaceSmpDir::FileDir)

            "content" -> resolveWorkspaceSafSmpDir(context, rootUri, createIfMissing)
                ?.let(WorkspaceSmpDir::SafDir)

            else -> null
        }
    }

    private fun resolveWorkspaceFileSmpDir(
        rootUri: Uri,
        createIfMissing: Boolean
    ): File? {
        val rootPath = rootUri.path?.takeIf { it.isNotBlank() } ?: return null
        val rawRootDir = File(rootPath)
        val splRoot = normalizeWorkspaceFileRoot(rawRootDir)
        if (!splRoot.exists() || !splRoot.isDirectory) {
            if (!createIfMissing || !splRoot.mkdirs()) {
                logWarn("Workspace file root indisponible backend=file root=${splRoot.absolutePath}")
                return null
            }
        }

        val backingTracksDir = File(splRoot, BACKING_TRACKS_DIR_NAME)
        if (!backingTracksDir.exists()) {
            if (!createIfMissing || !backingTracksDir.mkdirs()) {
                logWarn("Création BackingTracks impossible backend=file root=${splRoot.absolutePath}")
                return null
            }
        }

        val smpDir = File(backingTracksDir, SMP_DIR_NAME)
        if (!smpDir.exists()) {
            if (!createIfMissing || !smpDir.mkdirs()) {
                logWarn("Création SMP impossible backend=file root=${splRoot.absolutePath}")
                return null
            }
        }

        return smpDir.takeIf { it.isDirectory }
    }

    private fun normalizeWorkspaceFileRoot(rootDir: File): File {
        return when {
            File(rootDir, BACKING_TRACKS_DIR_NAME).isDirectory -> rootDir
            File(File(rootDir, "SPL_Music"), BACKING_TRACKS_DIR_NAME).isDirectory -> File(rootDir, "SPL_Music")
            else -> rootDir
        }
    }

    private fun resolveWorkspaceSafSmpDir(
        context: Context,
        rootUri: Uri,
        createIfMissing: Boolean
    ): DocumentFile? {
        val rootDoc = resolveWritableSafDirectory(context, rootUri) ?: return null
        val workspaceRoot = normalizeWorkspaceSafRoot(rootDoc)
        val backingTracks = (
            findDirectoryIgnoreCase(workspaceRoot, listOf(BACKING_TRACKS_DIR_NAME, "BackingTrack"))
                ?: if (createIfMissing) workspaceRoot.createDirectory(BACKING_TRACKS_DIR_NAME) else null
            ) ?: return null

        return (
            findDirectoryIgnoreCase(backingTracks, listOf(SMP_DIR_NAME, "smp"))
                ?: if (createIfMissing) backingTracks.createDirectory(SMP_DIR_NAME) else null
            )?.takeIf { it.isDirectory }
    }

    private fun normalizeWorkspaceSafRoot(rootDoc: DocumentFile): DocumentFile {
        if (findDirectoryIgnoreCase(rootDoc, listOf(BACKING_TRACKS_DIR_NAME, "BackingTrack")) != null) {
            return rootDoc
        }
        return findDirectoryIgnoreCase(rootDoc, listOf("SPL_Music", "spl_music")) ?: rootDoc
    }

    private fun resolveWritableSafDirectory(context: Context, rootUri: Uri): DocumentFile? {
        val directTree = DocumentFile.fromTreeUri(context, rootUri)
        if (directTree?.isDirectory == true) return directTree

        val normalizedTree = normalizeAsTreeUri(rootUri)?.let { treeUri ->
            DocumentFile.fromTreeUri(context, treeUri)
        }
        if (normalizedTree?.isDirectory == true) return normalizedTree

        val single = DocumentFile.fromSingleUri(context, rootUri)
        if (single?.isDirectory == true) return single

        return null
    }

    private fun findDirectoryIgnoreCase(parent: DocumentFile, candidates: List<String>): DocumentFile? {
        val wanted = candidates.map { it.trim().lowercase() }.toSet()
        return parent.listFiles().firstOrNull { child ->
            child.isDirectory && child.name.orEmpty().trim().lowercase() in wanted
        }
    }

    private fun findChildIgnoreCase(parent: DocumentFile, targetName: String): DocumentFile? {
        val wanted = targetName.trim().lowercase()
        return parent.listFiles().firstOrNull { child ->
            child.name.orEmpty().trim().lowercase() == wanted
        }
    }

    private fun scanSupersededFileArchives(
        targetDir: File,
        songId: String,
        targetName: String
    ): NormalizationScan<File> {
        var unresolvedCount = 0
        val supersededArchives = targetDir.listFiles()
            .orEmpty()
            .filter { file ->
                file.isFile &&
                    isSupportedArchiveFileName(file.name) &&
                    !file.name.equals(targetName, ignoreCase = true)
            }
            .filter { file ->
                when (val resolvedSongId = readStableSongId(file)) {
                    null -> {
                        unresolvedCount += 1
                        logInfo("step=normalize_skip_unresolved backend=file file=${file.absolutePath}")
                        false
                    }

                    else -> resolvedSongId == songId
                }
            }
        return NormalizationScan(
            supersededArchives = supersededArchives,
            unresolvedCount = unresolvedCount
        )
    }

    private fun scanFileArchivesForSongId(
        targetDir: File,
        songId: String
    ): NormalizationScan<File> {
        var unresolvedCount = 0
        val matchingArchives = targetDir.listFiles()
            .orEmpty()
            .filter { file ->
                file.isFile && isSupportedArchiveFileName(file.name)
            }
            .filter { file ->
                when (val resolvedSongId = readStableSongId(file)) {
                    null -> {
                        unresolvedCount += 1
                        logInfo("step=delete_skip_unresolved backend=file file=${file.absolutePath}")
                        false
                    }

                    else -> resolvedSongId == songId
                }
            }
        return NormalizationScan(
            supersededArchives = matchingArchives,
            unresolvedCount = unresolvedCount
        )
    }

    private fun scanSupersededSafArchives(
        context: Context,
        targetDir: DocumentFile,
        songId: String,
        targetName: String
    ): NormalizationScan<DocumentFile> {
        var unresolvedCount = 0
        val unresolvedArchives = mutableListOf<DocumentFile>()
        val children = targetDir.listFiles().toList()
        traceSafInfo("step=initial_scan dir=${targetDir.uri} totalChildren=${children.size}")
        val supersededArchives = children.mapNotNull { child ->
            val childName = child.name.orEmpty()
            val extension = detectExtension(childName)
            val isCandidate = child.isFile &&
                isSupportedArchiveFileName(childName) &&
                !childName.equals(targetName, ignoreCase = true)
            traceSafInfo(
                "step=scan_child dir=${targetDir.uri} name=$childName uri=${child.uri} isFile=${child.isFile} extension=$extension candidate=$isCandidate reason=${scanDecisionReason(child, childName, targetName)}"
            )
            if (!isCandidate) {
                return@mapNotNull null
            }

            when (val resolvedSongId = readStableSongId(context, child)) {
                null -> {
                    unresolvedCount += 1
                    unresolvedArchives += child
                    logInfo("step=normalize_skip_unresolved backend=saf file=${child.uri}")
                    traceSafInfo(
                        "step=scan_song_id_unresolved dir=${targetDir.uri} name=$childName uri=${child.uri}"
                    )
                    null
                }

                else -> {
                    val duplicate = resolvedSongId == songId
                    traceSafInfo(
                        "step=scan_song_id_resolved dir=${targetDir.uri} name=$childName uri=${child.uri} resolvedSongId=$resolvedSongId duplicate=$duplicate"
                    )
                    child.takeIf { duplicate }
                }
            }
        }
        traceSafInfo(
            "step=scan_plan_result dir=${targetDir.uri} targetSongId=$songId targetName=$targetName duplicates=${summarizeDocuments(supersededArchives)} unresolved=${summarizeDocuments(unresolvedArchives)}"
        )
        return NormalizationScan(
            supersededArchives = supersededArchives,
            unresolvedCount = unresolvedCount
        )
    }

    private fun scanSafArchivesForSongId(
        context: Context,
        targetDir: DocumentFile,
        songId: String
    ): NormalizationScan<DocumentFile> {
        var unresolvedCount = 0
        val matchingArchives = targetDir.listFiles().mapNotNull { child ->
            val childName = child.name.orEmpty()
            if (!child.isFile || !isSupportedArchiveFileName(childName)) {
                return@mapNotNull null
            }
            when (val resolvedSongId = readStableSongId(context, child)) {
                null -> {
                    unresolvedCount += 1
                    logInfo("step=delete_skip_unresolved backend=saf file=${child.uri}")
                    null
                }

                else -> child.takeIf { resolvedSongId == songId }
            }
        }
        return NormalizationScan(
            supersededArchives = matchingArchives,
            unresolvedCount = unresolvedCount
        )
    }

    private fun deleteFileArchivesForSongId(
        targetDir: File,
        songId: String
    ): DeleteArchivesResult {
        if (!targetDir.exists() || !targetDir.isDirectory) {
            return DeleteArchivesResult(deletedCount = 0, failedCount = 0)
        }

        val fastPathMatches = findFastPathFileArchivesForSongId(
            targetDir = targetDir,
            songId = songId
        )
        if (fastPathMatches.isNotEmpty()) {
            logInfo(
                "step=delete_fast_path_hit backend=file dir=${targetDir.absolutePath} songId=$songId count=${fastPathMatches.size}"
            )
            return deleteFileArchives(
                archives = fastPathMatches,
                failureLocation = targetDir.absolutePath
            )
        }

        val matchingScan = scanFileArchivesForSongId(
            targetDir = targetDir,
            songId = songId
        )
        logInfo(
            "step=delete_fast_path_miss backend=file dir=${targetDir.absolutePath} songId=$songId scanFallbackCount=${matchingScan.supersededArchives.size}"
        )
        return deleteFileArchives(
            archives = matchingScan.supersededArchives,
            failureLocation = targetDir.absolutePath
        )
    }

    private fun deleteSafArchivesForSongId(
        context: Context,
        targetDir: DocumentFile,
        songId: String
    ): DeleteArchivesResult {
        val fastPathMatches = findFastPathSafArchivesForSongId(
            targetDir = targetDir,
            songId = songId
        )
        if (fastPathMatches.isNotEmpty()) {
            logInfo(
                "step=delete_fast_path_hit backend=saf dir=${targetDir.uri} songId=$songId count=${fastPathMatches.size}"
            )
            return deleteSafArchives(
                archives = fastPathMatches,
                failureLocation = targetDir.uri.toString()
            )
        }

        val matchingScan = scanSafArchivesForSongId(
            context = context,
            targetDir = targetDir,
            songId = songId
        )
        logInfo(
            "step=delete_fast_path_miss backend=saf dir=${targetDir.uri} songId=$songId scanFallbackCount=${matchingScan.supersededArchives.size}"
        )
        return deleteSafArchives(
            archives = matchingScan.supersededArchives,
            failureLocation = targetDir.uri.toString()
        )
    }

    private fun findFastPathFileArchivesForSongId(
        targetDir: File,
        songId: String
    ): List<File> {
        val matches = linkedMapOf<String, File>()
        listOf("$songId.smp", "$songId.smp.zip").forEach { exactName ->
            val candidate = File(targetDir, exactName)
            if (candidate.isFile && isSupportedArchiveFileName(candidate.name)) {
                matches[candidate.absolutePath] = candidate
            }
        }
        targetDir.listFiles()
            .orEmpty()
            .filter { file ->
                file.isFile &&
                    isSupportedArchiveFileName(file.name) &&
                    matchesFastPathArchiveName(file.name, songId)
            }
            .forEach { file ->
                matches.putIfAbsent(file.absolutePath, file)
            }
        return matches.values.toList()
    }

    private fun findFastPathSafArchivesForSongId(
        targetDir: DocumentFile,
        songId: String
    ): List<DocumentFile> {
        val matches = linkedMapOf<String, DocumentFile>()
        listOf("$songId.smp", "$songId.smp.zip").forEach { exactName ->
            targetDir.findFile(exactName)
                ?.takeIf { it.isFile && isSupportedArchiveFileName(it.name.orEmpty()) }
                ?.let { document ->
                    matches[document.uri.toString()] = document
                }
        }
        targetDir.listFiles()
            .filter { document ->
                document.isFile &&
                    isSupportedArchiveFileName(document.name.orEmpty()) &&
                    matchesFastPathArchiveName(document.name.orEmpty(), songId)
            }
            .forEach { document ->
                matches.putIfAbsent(document.uri.toString(), document)
            }
        return matches.values.toList()
    }

    private fun matchesFastPathArchiveName(fileName: String, songId: String): Boolean {
        val cleanName = fileName.trim()
        if (cleanName.isEmpty()) return false
        return cleanName.equals("$songId.smp", ignoreCase = true) ||
            cleanName.equals("$songId.smp.zip", ignoreCase = true) ||
            cleanName.endsWith("[$songId].smp", ignoreCase = true) ||
            cleanName.endsWith("[$songId].smp.zip", ignoreCase = true)
    }

    private fun deleteFileArchives(
        archives: List<File>,
        failureLocation: String
    ): DeleteArchivesResult {
        var failedCount = 0
        archives.forEach { archive ->
            if (archive.exists() && !archive.delete()) {
                failedCount += 1
                logWarn("Suppression archive SMP impossible backend=file path=${archive.absolutePath}")
            }
        }
        val deletedCount = archives.size - failedCount
        return DeleteArchivesResult(
            deletedCount = deletedCount,
            failedCount = failedCount,
            failureReason = if (failedCount > 0) {
                "suppression archive SMP impossible dans $failureLocation"
            } else {
                null
            }
        )
    }

    private fun deleteSafArchives(
        archives: List<DocumentFile>,
        failureLocation: String
    ): DeleteArchivesResult {
        var failedCount = 0
        archives.forEach { archive ->
            if (!archive.delete()) {
                failedCount += 1
                logWarn("Suppression archive SMP impossible backend=saf uri=${archive.uri}")
            }
        }
        val deletedCount = archives.size - failedCount
        return DeleteArchivesResult(
            deletedCount = deletedCount,
            failedCount = failedCount,
            failureReason = if (failedCount > 0) {
                "suppression archive SMP impossible dans $failureLocation"
            } else {
                null
            }
        )
    }

    private fun readStableSongId(file: File): String? {
        return runCatching {
            file.inputStream().buffered().use { input ->
                SmpArchiveSongIdResolver.readStableSongId(input)
            }
        }.getOrElse { error ->
            logWarn("Lecture songId archive SMP impossible backend=file path=${file.absolutePath}", error)
            null
        }
    }

    private fun readStableSongId(context: Context, document: DocumentFile): String? {
        val name = document.name.orEmpty()
        traceSafInfo("step=song_id_read_start name=$name uri=${document.uri}")
        val result = runCatching {
            context.contentResolver.openInputStream(document.uri)?.use { input ->
                SmpArchiveSongIdResolver.readStableSongId(input)
            } ?: run {
                traceSafWarn("step=song_id_read_failed name=$name uri=${document.uri} reason=input_stream_null")
                null
            }
        }.getOrElse { error ->
            traceSafWarn(
                "step=song_id_read_failed name=$name uri=${document.uri} reason=${error.javaClass.simpleName}:${error.message ?: "unknown"}",
                error
            )
            logWarn("Lecture songId archive SMP impossible backend=saf uri=${document.uri}", error)
            null
        }
        traceSafInfo(
            "step=song_id_read_done name=$name uri=${document.uri} songId=${result ?: "unresolved"}"
        )
        return result
    }

    private fun sanitizeDurableArchiveTitle(rawTitle: String?): String? {
        return rawTitle.orEmpty()
            .trim()
            .replace(FORBIDDEN_ARCHIVE_NAME_CHARS, "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('.', ' ')
            .takeIf { it.isNotBlank() }
    }

    private fun logInfo(message: String) {
        runCatching { Log.i(TAG, message) }
    }

    private fun logWarn(message: String) {
        runCatching { Log.w(TAG, message) }
    }

    private fun logWarn(message: String, error: Throwable) {
        runCatching { Log.w(TAG, message, error) }
    }

    private fun logError(message: String, error: Throwable? = null) {
        runCatching {
            if (error != null) {
                Log.e(TAG, message, error)
            } else {
                Log.e(TAG, message)
            }
        }
    }

    private fun normalizeAsTreeUri(uri: Uri): Uri? {
        val authority = uri.authority ?: return null
        val treeId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
            ?: runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
            ?: return null
        return DocumentsContract.buildTreeDocumentUri(authority, treeId)
    }

    private fun traceSafDirectorySnapshot(
        context: Context,
        targetDir: DocumentFile,
        step: String,
        resolveSongIds: Boolean
    ) {
        val children = runCatching { targetDir.listFiles().toList() }.getOrElse { error ->
            traceSafWarn("step=$step dir=${targetDir.uri} reason=list_failed:${error.message ?: "unknown"}", error)
            emptyList()
        }
        traceSafInfo("step=$step dir=${targetDir.uri} totalChildren=${children.size} resolveSongIds=$resolveSongIds")
        children.forEach { child ->
            val name = child.name.orEmpty()
            val extension = detectExtension(name)
            val resolvedSongId = if (resolveSongIds && child.isFile && isSupportedArchiveFileName(name)) {
                readStableSongId(context, child) ?: "unresolved"
            } else {
                "not_requested"
            }
            traceSafInfo(
                "step=${step}_child dir=${targetDir.uri} name=$name uri=${child.uri} isFile=${child.isFile} extension=$extension songId=$resolvedSongId"
            )
        }
    }

    private fun scanDecisionReason(
        child: DocumentFile,
        childName: String,
        targetName: String
    ): String {
        return when {
            !child.isFile -> "ignored_not_file"
            !isSupportedArchiveFileName(childName) -> "ignored_not_smp"
            childName.equals(targetName, ignoreCase = true) -> "ignored_target_name"
            else -> "candidate"
        }
    }

    private fun detectExtension(name: String): String {
        return name.substringAfterLast('.', "").ifBlank { "<none>" }
    }

    private fun summarizeDocuments(documents: List<DocumentFile>): String {
        if (documents.isEmpty()) return "[]"
        return documents.joinToString(
            prefix = "[",
            postfix = "]"
        ) { document ->
            "${document.name.orEmpty()}|${document.uri}"
        }
    }

    private fun traceSafInfo(message: String) {
        runCatching { Log.i(SAF_TRACE_TAG, message) }
    }

    private fun traceSafWarn(message: String, error: Throwable? = null) {
        runCatching {
            if (error != null) {
                Log.w(SAF_TRACE_TAG, message, error)
            } else {
                Log.w(SAF_TRACE_TAG, message)
            }
        }
    }

    private fun createSafArchiveTarget(targetDir: DocumentFile, targetName: String): DocumentFile? {
        val created = targetDir.createFile(SAF_ARCHIVE_MIME, targetName) ?: return null
        val createdName = created.name.orEmpty()
        traceSafInfo(
            "step=create_target requestedName=$targetName actualName=$createdName createdUri=${created.uri} mime=$SAF_ARCHIVE_MIME"
        )
        if (createdName.equals(targetName, ignoreCase = true)) {
            return created
        }

        traceSafWarn(
            "step=create_target_needs_rename requestedName=$targetName actualName=$createdName createdUri=${created.uri}"
        )
        val renamed = runCatching { created.renameTo(targetName) }.getOrDefault(false)
        val resolved = findChildIgnoreCase(targetDir, targetName)
        traceSafInfo(
            "step=create_target_rename_result requestedName=$targetName initialName=$createdName createdUri=${created.uri} renameResult=$renamed resolvedUri=${resolved?.uri} resolvedName=${resolved?.name.orEmpty()}"
        )
        if (resolved?.isFile == true) {
            return resolved
        }

        if (created.name.orEmpty().equals(targetName, ignoreCase = true) && created.isFile) {
            return created
        }

        runCatching {
            if (!created.delete()) {
                traceSafWarn(
                    "step=create_target_cleanup_failed requestedName=$targetName createdUri=${created.uri} createdName=${created.name.orEmpty()}"
                )
            }
        }
        return null
    }
}
