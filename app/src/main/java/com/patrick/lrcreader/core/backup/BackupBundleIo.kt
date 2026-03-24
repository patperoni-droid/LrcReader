package com.patrick.lrcreader.core.backup

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupBundleSmpFile(
    val songId: String,
    val entryName: String,
    val bytes: ByteArray
)

data class BackupBundlePayload(
    val manifest: BackupBundleManifest = BackupBundleManifest(),
    val stateJson: String,
    val smpFiles: List<BackupBundleSmpFile> = emptyList()
)

object BackupBundleIo {

    fun write(outputStream: OutputStream, payload: BackupBundlePayload) {
        val normalizedManifest = normalizeManifest(payload)
        validatePayload(normalizedManifest, payload.stateJson, payload.smpFiles)

        ZipOutputStream(outputStream.buffered()).use { zipOutput ->
            writeEntry(
                zipOutput = zipOutput,
                entryName = BACKUP_BUNDLE_MANIFEST_ENTRY,
                bytes = normalizedManifest.toJsonString().toByteArray(Charsets.UTF_8)
            )
            writeEntry(
                zipOutput = zipOutput,
                entryName = normalizedManifest.stateEntry,
                bytes = payload.stateJson.toByteArray(Charsets.UTF_8)
            )
            payload.smpFiles.forEach { smpFile ->
                writeEntry(
                    zipOutput = zipOutput,
                    entryName = smpFile.entryName,
                    bytes = smpFile.bytes
                )
            }
        }
    }

    fun writeToFile(targetFile: File, payload: BackupBundlePayload): Boolean {
        val parentDir = targetFile.parentFile ?: return false
        if (!parentDir.exists() && !parentDir.mkdirs()) {
            return false
        }

        val tmpFile = File(parentDir, "${targetFile.name}.part")
        return runCatching {
            tmpFile.outputStream().use { output ->
                write(output, payload)
            }
            if (targetFile.exists() && !targetFile.delete()) {
                throw IllegalStateException("Cannot delete existing target ${targetFile.absolutePath}")
            }
            if (!tmpFile.renameTo(targetFile)) {
                targetFile.outputStream().use { output ->
                    tmpFile.inputStream().use { input -> input.copyTo(output) }
                }
                tmpFile.delete()
            }
            true
        }.getOrElse {
            runCatching { tmpFile.delete() }
            false
        }
    }

    fun readOrNull(inputStream: InputStream): BackupBundlePayload? {
        val entries = linkedMapOf<String, ByteArray>()

        runCatching {
            ZipInputStream(inputStream.buffered()).use { zipInput ->
                while (true) {
                    val entry = zipInput.nextEntry ?: break
                    if (!entry.isDirectory) {
                        entries[entry.name] = zipInput.readBytes()
                    }
                    zipInput.closeEntry()
                }
            }
        }.getOrElse {
            return null
        }

        val manifestRaw = entries[BACKUP_BUNDLE_MANIFEST_ENTRY]
            ?.toString(Charsets.UTF_8)
            ?: return null
        val manifest = BackupBundleManifest.fromJsonOrNull(manifestRaw) ?: return null
        if (manifest.format != BACKUP_BUNDLE_FORMAT || manifest.version != BACKUP_BUNDLE_VERSION) {
            return null
        }

        val stateJson = entries[manifest.stateEntry]
            ?.toString(Charsets.UTF_8)
            ?: return null

        val smpFiles = buildList {
            manifest.songs.forEach { song ->
                val bytes = entries[song.entry] ?: return null
                add(
                    BackupBundleSmpFile(
                        songId = song.songId,
                        entryName = song.entry,
                        bytes = bytes
                    )
                )
            }
        }

        return runCatching {
            validatePayload(manifest, stateJson, smpFiles)
            BackupBundlePayload(
                manifest = manifest,
                stateJson = stateJson,
                smpFiles = smpFiles
            )
        }.getOrNull()
    }

    fun readOrNull(bundleFile: File): BackupBundlePayload? {
        if (!bundleFile.isFile) return null
        return runCatching {
            bundleFile.inputStream().use(::readOrNull)
        }.getOrNull()
    }

    private fun normalizeManifest(payload: BackupBundlePayload): BackupBundleManifest {
        val normalizedSongs = payload.smpFiles
            .map { smpFile ->
                BackupBundleSongEntry(
                    songId = smpFile.songId.trim(),
                    entry = smpFile.entryName.trim()
                )
            }
            .sortedBy { it.songId }

        return payload.manifest.copy(
            format = BACKUP_BUNDLE_FORMAT,
            version = BACKUP_BUNDLE_VERSION,
            songs = normalizedSongs
        )
    }

    private fun validatePayload(
        manifest: BackupBundleManifest,
        stateJson: String,
        smpFiles: List<BackupBundleSmpFile>
    ) {
        require(manifest.format == BACKUP_BUNDLE_FORMAT) { "Unsupported bundle format" }
        require(manifest.version == BACKUP_BUNDLE_VERSION) { "Unsupported bundle version" }
        require(stateJson.isNotBlank()) { "stateJson must not be blank" }

        validateEntryName(BACKUP_BUNDLE_MANIFEST_ENTRY)
        validateEntryName(manifest.stateEntry)

        val songIds = mutableSetOf<String>()
        val entryNames = mutableSetOf<String>()
        val smpBySongId = smpFiles.associateBy { it.songId.trim() }

        manifest.songs.forEach { song ->
            require(song.songId.isNotBlank()) { "songId must not be blank" }
            require(song.entry.isNotBlank()) { "song entry must not be blank" }
            require(songIds.add(song.songId)) { "Duplicate songId ${song.songId}" }
            require(entryNames.add(song.entry)) { "Duplicate song entry ${song.entry}" }
            validateEntryName(song.entry)

            val smpFile = smpBySongId[song.songId]
                ?: throw IllegalArgumentException("Missing smp file for ${song.songId}")
            require(smpFile.entryName.trim() == song.entry) {
                "Manifest entry mismatch for ${song.songId}"
            }
            require(smpFile.bytes.isNotEmpty()) { "Empty smp bytes for ${song.songId}" }
        }

        require(manifest.stateEntry != BACKUP_BUNDLE_MANIFEST_ENTRY) {
            "stateEntry must differ from manifest entry"
        }
    }

    private fun validateEntryName(entryName: String) {
        require(entryName.isNotBlank()) { "entryName must not be blank" }
        require(!entryName.startsWith("/")) { "entryName must be relative" }
        require("\\" !in entryName) { "entryName must use forward slashes" }
        require(!entryName.endsWith("/")) { "entryName must target a file" }
        val segments = entryName.split('/')
        require(segments.none { it.isBlank() || it == "." || it == ".." }) {
            "entryName contains unsafe segments"
        }
    }

    private fun writeEntry(
        zipOutput: ZipOutputStream,
        entryName: String,
        bytes: ByteArray
    ) {
        zipOutput.putNextEntry(ZipEntry(entryName))
        zipOutput.write(bytes)
        zipOutput.closeEntry()
    }
}
