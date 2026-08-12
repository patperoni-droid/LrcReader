package com.patrick.lrcreader.smp

import android.content.Context
import com.patrick.lrcreader.core.LrcStorage
import com.patrick.lrcreader.core.sync.SmpSyncHashing
import org.json.JSONObject
import java.io.File

data class SmpFamilyAudioHashCache(
    val fileIdentity: String,
    val size: Long,
    val lastModified: Long,
    val sha256: String
)

data class SmpFamilyFingerprintResult(
    val fingerprint: String,
    val audioHashCache: SmpFamilyAudioHashCache?
)

class SmpFamilyFingerprint(
    private val hashing: SmpSyncHashing = SmpSyncHashing()
) {
    fun calculate(
        context: Context,
        sourceSong: SongUnit,
        cachedAudio: SmpFamilyAudioHashCache? = null
    ): SmpFamilyFingerprintResult? = runCatching {
        val refreshedSource = SmpExporter.refreshSongUnitForExport(context, sourceSong)
        val request = SmpExporter.resolveExportRequest(
            requestedSong = refreshedSource,
            findSongById = SmpLibraryScanner(context)::findSongById
        )
        val parent = SmpExporter.refreshSongUnitForExport(context, request.packageSong)
        require(request.selectedVariantId == null) {
            "A Family fingerprint must be calculated from its parent SongUnit"
        }
        val config = SmpConfig.fromSongUnit(context, parent)
        val variants = SmpExporter.resolveArrangementVariantsForExport(
            context = context,
            sourceSong = parent,
            selectedVariantId = null
        )
        val parentDir = parent.storageFolder
            ?.takeIf(String::isNotBlank)
            ?.let(::File)
            ?.takeIf(File::isDirectory)
            ?: return null
        val audio = resolveAudioHash(parent.audioPath?.let(::File), cachedAudio)
        val components = JSONObject()
            .put("version", FINGERPRINT_VERSION)
            .put("songId", parent.id)
            .put("config", hashing.hashCanonicalJsonText(config.toJsonString()))
            .putNullableHash("audio", audio.hash)
            .putNullableHash("lyrics", hashBytes(parent.lyricsPath))
            .putNullableHash(
                "lyricsEditorRaw",
                hashBytes(File(parentDir, LrcStorage.LYRICS_EDITOR_RAW_FILE_NAME))
            )
            .putNullableHash("chords", hashBytes(parent.chordsPath))
            .putNullableHash("timeline", hashBytes(parent.timelinePath))
            .putNullableHash("annotations", hashBytes(parent.annotationsPath))
            .putNullableHash("midi", hashBytes(parent.midiPath))
            .putNullableHash("dmx", hashBytes(parent.dmxPath))
            .putNullableHash("grid", hashBytes(File(parentDir, "grid.json")))
            .putNullableHash("prompter", hashBytes(parent.prompterPath))
            .putNullableHash("arrangement", hashBytes(File(parentDir, "arrangement.json")))
            .putNullableHash(
                "variants",
                variants.variants.takeIf(List<*>::isNotEmpty)?.let {
                    hashing.hashCanonicalJson(
                        ArrangementVariantsArchiveCodec.encode(variants)
                    )
                }
            )
        SmpFamilyFingerprintResult(
            fingerprint = hashing.hashCanonicalJson(components),
            audioHashCache = audio.cache
        )
    }.getOrNull()

    private fun hashBytes(path: String?): String? = path
        ?.takeIf(String::isNotBlank)
        ?.let(::File)
        ?.let(::hashBytes)

    private fun hashBytes(file: File): String? = hashing.hashFileOrNull(
        file,
        SmpSyncHashing.FileHashMode.BYTES
    )

    private fun resolveAudioHash(
        audioFile: File?,
        cached: SmpFamilyAudioHashCache?
    ): AudioHashResult {
        if (audioFile == null || !audioFile.isFile) return AudioHashResult(null, null)
        val identity = runCatching { audioFile.canonicalPath }.getOrElse { audioFile.absolutePath }
        val size = audioFile.length()
        val lastModified = audioFile.lastModified()
        val cachedHash = cached?.takeIf {
            it.fileIdentity == identity &&
                it.size == size &&
                it.lastModified == lastModified &&
                it.lastModified > 0L &&
                SHA_256.matches(it.sha256)
        }?.sha256
        val hash = cachedHash ?: requireNotNull(hashBytes(audioFile))
        return AudioHashResult(
            hash = hash,
            cache = SmpFamilyAudioHashCache(identity, size, lastModified, hash)
        )
    }

    private fun JSONObject.putNullableHash(key: String, value: String?): JSONObject {
        put(key, value ?: JSONObject.NULL)
        return this
    }

    private data class AudioHashResult(
        val hash: String?,
        val cache: SmpFamilyAudioHashCache?
    )

    private companion object {
        const val FINGERPRINT_VERSION = 1
        val SHA_256 = Regex("[0-9a-f]{64}")
    }
}
