package com.patrick.lrcreader.smp

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class ArrangementSegmentData(
    val id: String,
    val name: String,
    val startMs: Long,
    val endMs: Long
)

data class ArrangementEntryData(
    val entryId: String,
    val name: String,
    val startMs: Long,
    val endMs: Long,
    val repeatCount: Int = 1,
    val muted: Boolean = false,
    val color: String? = null
)

data class ArrangementData(
    val version: Int = 1,
    val name: String = "Arrangement 1",
    val sourceSongId: String,
    val updatedAt: Long = System.currentTimeMillis(),
    val segments: List<ArrangementSegmentData>,
    val structureSegmentIds: List<String>,
    val entries: List<ArrangementEntryData> = emptyList()
)

object ArrangementStore {

    private const val TAG = "ArrangementStore"
    private const val TRACKS_DIR_NAME = "tracks"
    private const val FILE_NAME = "arrangement.json"

    suspend fun load(context: Context, songId: String): ArrangementData? = withContext(Dispatchers.IO) {
        val songDir = resolveSongDir(context, songId) ?: return@withContext null
        val arrangementFile = File(songDir, FILE_NAME)
        if (!arrangementFile.isFile) {
            return@withContext null
        }

        runCatching {
            ArrangementJsonCodec.decode(JSONObject(arrangementFile.readText(Charsets.UTF_8)))
        }.getOrElse { error ->
            Log.w(TAG, "load failed songId=${songId.trim()} path=${arrangementFile.absolutePath}", error)
            null
        }
    }

    suspend fun save(context: Context, songId: String, data: ArrangementData): Boolean = withContext(Dispatchers.IO) {
        val songDir = resolveSongDir(context, songId) ?: return@withContext false
        if (!songDir.exists() && !songDir.mkdirs()) {
            Log.w(TAG, "save mkdir failed songId=${songId.trim()} path=${songDir.absolutePath}")
            return@withContext false
        }

        val targetFile = File(songDir, FILE_NAME)
        val tmpFile = File(songDir, "$FILE_NAME.tmp")
        val backupFile = File(songDir, "$FILE_NAME.bak")
        val existingData = targetFile
            .takeIf(File::isFile)
            ?.let { file ->
                runCatching {
                    ArrangementJsonCodec.decode(JSONObject(file.readText(Charsets.UTF_8)))
                }.getOrNull()
            }
        val dataToWrite = ArrangementJsonCodec.preserveVersion2Metadata(
            existingData = existingData,
            incomingData = data
        ).copy(updatedAt = System.currentTimeMillis())
        val rawJson = ArrangementJsonCodec.encode(dataToWrite).toString(2)

        runCatching {
            tmpFile.writeText(rawJson, Charsets.UTF_8)

            if (backupFile.exists() && !backupFile.delete()) {
                Log.w(TAG, "save delete stale backup failed path=${backupFile.absolutePath}")
            }

            if (targetFile.exists() && !targetFile.renameTo(backupFile)) {
                tmpFile.delete()
                Log.w(TAG, "save backup rename failed path=${targetFile.absolutePath}")
                return@runCatching false
            }

            val renamed = tmpFile.renameTo(targetFile)
            if (renamed) {
                if (backupFile.exists() && !backupFile.delete()) {
                    Log.w(TAG, "save delete backup failed path=${backupFile.absolutePath}")
                }
                return@runCatching true
            }

            Log.w(TAG, "save tmp rename failed path=${targetFile.absolutePath}")
            tmpFile.delete()
            if (backupFile.exists() && !backupFile.renameTo(targetFile)) {
                Log.w(TAG, "save rollback failed path=${targetFile.absolutePath}")
            }
            false
        }.getOrElse { error ->
            Log.w(TAG, "save failed songId=${songId.trim()} path=${targetFile.absolutePath}", error)
            runCatching { tmpFile.delete() }
            false
        }
    }

    private fun resolveSongDir(context: Context, songId: String): File? {
        val cleanSongId = songId.trim().takeIf { it.isNotEmpty() } ?: return null
        return File(File(context.filesDir, TRACKS_DIR_NAME), cleanSongId)
    }
}

internal object ArrangementJsonCodec {

    private const val VERSION_WITH_ENTRIES = 2

    fun decode(json: JSONObject): ArrangementData {
        val sourceSongId = json.optString("sourceSongId").trim().ifBlank {
            throw IllegalArgumentException("Missing sourceSongId")
        }
        val storedSegments = json.optJSONArray("segments")
            ?.let(::segmentsFromJson)
            .orEmpty()
        val validSegmentIds = storedSegments.map { it.id }.toSet()
        val storedStructureSegmentIds = json.optJSONArray("structureSegmentIds")
            ?.let(::structureFromJson)
            .orEmpty()
            .filter { it in validSegmentIds }
        val version = json.optInt("version", 1).coerceAtLeast(1)
        val hasEntryModel = version >= VERSION_WITH_ENTRIES && json.has("entries")
        val entries = if (hasEntryModel) {
            json.optJSONArray("entries")?.let(::entriesFromJson).orEmpty()
        } else {
            entriesFromLegacy(storedSegments, storedStructureSegmentIds)
        }
        val entrySegments = entries.map { entry -> entry.toLegacySegment() }
        val entryIds = entrySegments.mapTo(linkedSetOf()) { segment -> segment.id }
        val segments = if (hasEntryModel) {
            entrySegments + storedSegments.filterNot { segment -> segment.id in entryIds }
        } else {
            storedSegments
        }
        val structureSegmentIds = if (hasEntryModel) {
            entries.map(ArrangementEntryData::entryId)
        } else {
            storedStructureSegmentIds
        }

        return ArrangementData(
            version = version,
            name = json.optString("name").trim().ifBlank { "Arrangement 1" },
            sourceSongId = sourceSongId,
            updatedAt = json.optLong("updatedAt", System.currentTimeMillis()),
            segments = segments,
            structureSegmentIds = structureSegmentIds,
            entries = entries
        )
    }

    fun preserveVersion2Metadata(
        existingData: ArrangementData?,
        incomingData: ArrangementData
    ): ArrangementData {
        if (
            incomingData.version >= VERSION_WITH_ENTRIES ||
            existingData == null ||
            existingData.version < VERSION_WITH_ENTRIES
        ) {
            return incomingData
        }

        val existingEntriesById = existingData.entries.associateBy { entry -> entry.entryId }
        val incomingSegmentsById = incomingData.segments.associateBy { segment -> segment.id }
        val usedEntryIds = linkedSetOf<String>()
        val occurrenceCounts = mutableMapOf<String, Int>()
        val reconciledEntries = incomingData.structureSegmentIds.mapNotNull { segmentId ->
            val segment = incomingSegmentsById[segmentId] ?: return@mapNotNull null
            val entryId = nextEntryId(
                baseId = segment.id,
                usedEntryIds = usedEntryIds,
                occurrenceCounts = occurrenceCounts
            )
            val existingEntry = existingEntriesById[entryId]
            ArrangementEntryData(
                entryId = entryId,
                name = segment.name,
                startMs = segment.startMs,
                endMs = segment.endMs,
                repeatCount = existingEntry?.repeatCount ?: 1,
                muted = existingEntry?.muted ?: false,
                color = existingEntry?.color
            )
        }

        return incomingData.copy(
            version = VERSION_WITH_ENTRIES,
            entries = reconciledEntries
        )
    }

    private fun segmentsFromJson(array: JSONArray): List<ArrangementSegmentData> {
        val out = ArrayList<ArrangementSegmentData>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val id = item.optString("id").trim()
            val name = item.optString("name").trim()
            val startMs = item.optLong("startMs", -1L)
            val endMs = item.optLong("endMs", -1L)
            if (id.isBlank() || name.isBlank() || startMs < 0L || endMs <= startMs) continue
            out += ArrangementSegmentData(
                id = id,
                name = name,
                startMs = startMs,
                endMs = endMs
            )
        }
        return out
    }

    private fun structureFromJson(array: JSONArray): List<String> {
        val out = ArrayList<String>(array.length())
        for (index in 0 until array.length()) {
            val value = array.optString(index).trim()
            if (value.isNotEmpty()) {
                out += value
            }
        }
        return out
    }

    private fun entriesFromJson(array: JSONArray): List<ArrangementEntryData> {
        val rawEntries = ArrayList<ArrangementEntryData>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val entryId = item.optString("entryId").trim()
            val name = item.optString("name").trim()
            val startMs = item.optLong("startMs", -1L)
            val endMs = item.optLong("endMs", -1L)
            if (entryId.isBlank() || name.isBlank() || startMs < 0L || endMs <= startMs) continue
            rawEntries += ArrangementEntryData(
                entryId = entryId,
                name = name,
                startMs = startMs,
                endMs = endMs,
                repeatCount = item.optInt("repeatCount", 1).coerceAtLeast(1),
                muted = item.optBoolean("muted", false),
                color = item.optNullableTrimmedString("color")
            )
        }
        return ensureUniqueEntryIds(rawEntries)
    }

    private fun entriesFromLegacy(
        segments: List<ArrangementSegmentData>,
        structureSegmentIds: List<String>
    ): List<ArrangementEntryData> {
        val segmentsById = segments.associateBy { segment -> segment.id }
        val usedEntryIds = linkedSetOf<String>()
        val occurrenceCounts = mutableMapOf<String, Int>()
        return structureSegmentIds.mapNotNull { segmentId ->
            val segment = segmentsById[segmentId] ?: return@mapNotNull null
            ArrangementEntryData(
                entryId = nextEntryId(segment.id, usedEntryIds, occurrenceCounts),
                name = segment.name,
                startMs = segment.startMs,
                endMs = segment.endMs
            )
        }
    }

    private fun ensureUniqueEntryIds(entries: List<ArrangementEntryData>): List<ArrangementEntryData> {
        val usedEntryIds = linkedSetOf<String>()
        val occurrenceCounts = mutableMapOf<String, Int>()
        return entries.map { entry ->
            val uniqueId = nextEntryId(entry.entryId, usedEntryIds, occurrenceCounts)
            if (uniqueId == entry.entryId) entry else entry.copy(entryId = uniqueId)
        }
    }

    private fun nextEntryId(
        baseId: String,
        usedEntryIds: MutableSet<String>,
        occurrenceCounts: MutableMap<String, Int>
    ): String {
        var occurrence = (occurrenceCounts[baseId] ?: 0) + 1
        var candidate = if (occurrence == 1) baseId else "${baseId}__occurrence_$occurrence"
        while (!usedEntryIds.add(candidate)) {
            occurrence += 1
            candidate = "${baseId}__occurrence_$occurrence"
        }
        occurrenceCounts[baseId] = occurrence
        return candidate
    }

    fun encode(data: ArrangementData): JSONObject {
        val usesEntryModel = data.version >= VERSION_WITH_ENTRIES
        val entries = if (usesEntryModel) {
            ensureUniqueEntryIds(data.entries.filter { entry -> entry.isValid() })
        } else {
            emptyList()
        }
        val entrySegments = entries.map { entry -> entry.toLegacySegment() }
        val entryIds = entrySegments.mapTo(linkedSetOf()) { segment -> segment.id }
        val compatibleSegments = if (usesEntryModel) {
            entrySegments + data.segments.filter { segment -> segment.isValid() && segment.id !in entryIds }
        } else {
            data.segments.filter { segment -> segment.isValid() }
        }
        val compatibleStructureIds = if (usesEntryModel) {
            entries.map(ArrangementEntryData::entryId)
        } else {
            val validSegmentIds = compatibleSegments.mapTo(hashSetOf()) { segment -> segment.id }
            data.structureSegmentIds.filter { segmentId -> segmentId in validSegmentIds }
        }

        return JSONObject().apply {
            put("version", data.version.coerceAtLeast(1))
            put("name", data.name.ifBlank { "Arrangement 1" })
            put("sourceSongId", data.sourceSongId)
            put("updatedAt", data.updatedAt)
            put(
                "segments",
                JSONArray().apply {
                    compatibleSegments.forEach { segment ->
                        put(
                            JSONObject().apply {
                                put("id", segment.id)
                                put("name", segment.name)
                                put("startMs", segment.startMs.coerceAtLeast(0L))
                                put("endMs", segment.endMs.coerceAtLeast(segment.startMs + 1L))
                            }
                        )
                    }
                }
            )
            put(
                "structureSegmentIds",
                JSONArray().apply {
                    compatibleStructureIds.forEach { segmentId ->
                        put(segmentId)
                    }
                }
            )
            if (usesEntryModel) {
                put(
                    "entries",
                    JSONArray().apply {
                        entries.forEach { entry ->
                            put(
                                JSONObject().apply {
                                    put("entryId", entry.entryId)
                                    put("name", entry.name)
                                    put("startMs", entry.startMs.coerceAtLeast(0L))
                                    put("endMs", entry.endMs.coerceAtLeast(entry.startMs + 1L))
                                    put("repeatCount", entry.repeatCount.coerceAtLeast(1))
                                    put("muted", entry.muted)
                                    entry.color?.trim()?.takeIf(String::isNotEmpty)?.let { color ->
                                        put("color", color)
                                    }
                                }
                            )
                        }
                    }
                )
            }
        }
    }

    private fun ArrangementEntryData.isValid(): Boolean =
        entryId.isNotBlank() && name.isNotBlank() && startMs >= 0L && endMs > startMs

    private fun ArrangementSegmentData.isValid(): Boolean =
        id.isNotBlank() && name.isNotBlank() && startMs >= 0L && endMs > startMs

    private fun ArrangementEntryData.toLegacySegment(): ArrangementSegmentData =
        ArrangementSegmentData(
            id = entryId,
            name = name,
            startMs = startMs,
            endMs = endMs
        )

    private fun JSONObject.optNullableTrimmedString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).trim().takeIf(String::isNotEmpty)
    }
}
