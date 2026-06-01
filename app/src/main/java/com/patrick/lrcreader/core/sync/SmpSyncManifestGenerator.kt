package com.patrick.lrcreader.core.sync

import android.content.Context
import com.patrick.lrcreader.core.PlaylistItem
import com.patrick.lrcreader.core.PlaylistRepository
import com.patrick.lrcreader.core.TextSongRepository
import com.patrick.lrcreader.core.getGroupColorArgb
import com.patrick.lrcreader.core.getGroupTitle
import com.patrick.lrcreader.core.getSmpSongId
import com.patrick.lrcreader.core.getVariantFamilyId
import com.patrick.lrcreader.core.getVariantFamilySongIds
import com.patrick.lrcreader.core.getVariantFamilyTitle
import com.patrick.lrcreader.core.isGroupHeader
import com.patrick.lrcreader.core.isVariantFamilyItem
import com.patrick.lrcreader.core.config.NotesConfigStore
import com.patrick.lrcreader.smp.SmpLibraryScanner
import com.patrick.lrcreader.smp.SongUnit
import com.patrick.lrcreader.ui.library.SongVariantFamiliesStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class SmpSyncSongManifestSource(
    val songId: String,
    val title: String,
    val updatedAt: Long? = null,
    val audioFile: File? = null,
    val lyricsFile: File? = null,
    val chordsFile: File? = null,
    val notesHash: String? = null,
    val prompterFile: File? = null,
    val timelineFile: File? = null,
    val midiFile: File? = null,
    val dmxFile: File? = null,
    val settingsFile: File? = null,
    val arrangementFile: File? = null,
    val gridFile: File? = null
)

data class SmpSyncPlaylistManifestSource(
    val playlistId: String? = null,
    val playlistName: String,
    val items: List<PlaylistItem> = emptyList(),
    val songIds: List<String> = emptyList(),
    val groupMarkers: List<String> = emptyList(),
    val colorArgb: Long? = null
)

data class SmpSyncFamilyManifestSource(
    val familyId: String,
    val title: String,
    val songIds: List<String>,
    val parentSongId: String? = null,
    val activeSongId: String? = null
)

class SmpSyncManifestGenerator(
    private val hashing: SmpSyncHashing = SmpSyncHashing()
) {

    suspend fun generate(
        context: Context,
        appVersion: String,
        deviceId: String? = null,
        generatedAt: Long = System.currentTimeMillis(),
        libraryVersion: Long? = null
    ): SmpSyncManifest = withContext(Dispatchers.IO) {
        val songs = SmpLibraryScanner(context)
            .listSongs()
            .map { song -> song.toManifestSource(context) }

        val playlists = PlaylistRepository.getPlaylists()
            .map { playlistName -> playlistName.toPlaylistSource() }

        val families = collectFamilySources(context, playlists)

        buildManifestFromSources(
            appVersion = appVersion,
            deviceId = deviceId,
            generatedAt = generatedAt,
            libraryVersion = libraryVersion,
            songs = songs,
            playlists = playlists,
            families = families,
            globalStateHash = readGlobalStateHash(context)
        )
    }

    suspend fun generateFromSources(
        appVersion: String,
        deviceId: String? = null,
        generatedAt: Long = System.currentTimeMillis(),
        libraryVersion: Long? = null,
        songs: List<SmpSyncSongManifestSource> = emptyList(),
        playlists: List<SmpSyncPlaylistManifestSource> = emptyList(),
        families: List<SmpSyncFamilyManifestSource> = emptyList(),
        globalStateHash: String? = null
    ): SmpSyncManifest = withContext(Dispatchers.IO) {
        buildManifestFromSources(
            appVersion = appVersion,
            deviceId = deviceId,
            generatedAt = generatedAt,
            libraryVersion = libraryVersion,
            songs = songs,
            playlists = playlists,
            families = families,
            globalStateHash = globalStateHash
        )
    }

    private fun buildManifestFromSources(
        appVersion: String,
        deviceId: String? = null,
        generatedAt: Long = System.currentTimeMillis(),
        libraryVersion: Long? = null,
        songs: List<SmpSyncSongManifestSource> = emptyList(),
        playlists: List<SmpSyncPlaylistManifestSource> = emptyList(),
        families: List<SmpSyncFamilyManifestSource> = emptyList(),
        globalStateHash: String? = null
    ): SmpSyncManifest {
        return SmpSyncManifest(
            appVersion = appVersion,
            deviceId = deviceId,
            generatedAt = generatedAt,
            libraryVersion = libraryVersion,
            songs = songs
                .map(::buildSongEntry)
                .sortedWith(compareBy<SmpSyncSongEntry> { it.title.lowercase() }.thenBy { it.songId }),
            playlists = playlists
                .map(::buildPlaylistEntry)
                .sortedBy { it.playlistName.lowercase() },
            families = families
                .map(::buildFamilyEntry)
                .sortedWith(compareBy<SmpSyncFamilyEntry> { it.title.lowercase() }.thenBy { it.familyId }),
            globalState = globalStateHash?.let { SmpSyncGlobalStateEntry(stateHash = it) }
        )
    }

    private fun buildSongEntry(source: SmpSyncSongManifestSource): SmpSyncSongEntry {
        val audioHash = hashing.hashFileOrNull(source.audioFile, SmpSyncHashing.FileHashMode.BYTES)
        val lyricsHash = hashing.hashFileOrNull(source.lyricsFile, SmpSyncHashing.FileHashMode.SYNC_LYRICS_TEXT)
        val chordsHash = hashing.hashFileOrNull(source.chordsFile, SmpSyncHashing.FileHashMode.NORMALIZED_TEXT)
        val prompterHash = hashing.hashFileOrNull(source.prompterFile, SmpSyncHashing.FileHashMode.NORMALIZED_TEXT)
        val timelineHash = hashing.hashFileOrNull(source.timelineFile, SmpSyncHashing.FileHashMode.CANONICAL_JSON)
        val midiHash = hashing.hashFileOrNull(source.midiFile, SmpSyncHashing.FileHashMode.CANONICAL_JSON)
        val dmxHash = hashing.hashFileOrNull(source.dmxFile, SmpSyncHashing.FileHashMode.CANONICAL_JSON)
        val settingsHash = hashing.hashFileOrNull(source.settingsFile, SmpSyncHashing.FileHashMode.SYNC_SETTINGS_JSON)
        val arrangementHash = hashing.hashFileOrNull(source.arrangementFile, SmpSyncHashing.FileHashMode.SYNC_ARRANGEMENT_JSON)
        val gridHash = hashing.hashFileOrNull(source.gridFile, SmpSyncHashing.FileHashMode.CANONICAL_JSON)

        val fullSongHash = hashing.hashCanonicalJson(
            JSONObject()
                .put("songId", source.songId)
                .put("title", source.title)
                .putNullable("audioHash", audioHash)
                .putNullable("lyricsHash", lyricsHash)
                .putNullable("chordsHash", chordsHash)
                .putNullable("notesHash", source.notesHash)
                .putNullable("prompterHash", prompterHash)
                .putNullable("timelineHash", timelineHash)
                .putNullable("midiHash", midiHash)
                .putNullable("dmxHash", dmxHash)
                .putNullable("settingsHash", settingsHash)
                .putNullable("arrangementHash", arrangementHash)
                .putNullable("gridHash", gridHash)
        )

        return SmpSyncSongEntry(
            songId = source.songId,
            title = source.title,
            updatedAt = source.updatedAt ?: source.maxComponentLastModified(),
            audioHash = audioHash,
            lyricsHash = lyricsHash,
            chordsHash = chordsHash,
            notesHash = source.notesHash,
            prompterHash = prompterHash,
            timelineHash = timelineHash,
            midiHash = midiHash,
            dmxHash = dmxHash,
            settingsHash = settingsHash,
            arrangementHash = arrangementHash,
            gridHash = gridHash,
            fullSongHash = fullSongHash
        )
    }

    private fun buildPlaylistEntry(source: SmpSyncPlaylistManifestSource): SmpSyncPlaylistEntry {
        val itemsJson = JSONArray().apply {
            source.items.forEach { item ->
                put(
                    JSONObject()
                        .put("uri", item.uri)
                        .putNullable("songId", item.songId)
                )
            }
        }
        val groupsJson = JSONArray().apply {
            source.groupMarkers.forEach(::put)
        }
        val colorsJson = JSONObject()
            .putNullable("playlistColorArgb", source.colorArgb)

        val itemsHash = hashing.hashNormalizedText(itemsJson.toString())
        val itemKeys = source.items.map { item -> item.syncDiagnosticKey() }
        val songIds = (source.songIds + source.items.flatMap { item -> item.referencedSongIds() })
            .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
            .distinct()
        val groupsHash = source.groupMarkers.takeIf { it.isNotEmpty() }
            ?.let { hashing.hashNormalizedText(groupsJson.toString()) }
        val colorsHash = source.colorArgb?.let { hashing.hashCanonicalJson(colorsJson) }
        val fullPlaylistHash = hashing.hashCanonicalJson(
            JSONObject()
                .put("playlistName", source.playlistName)
                .put("items", itemsJson)
                .put("groups", groupsJson)
                .put("colors", colorsJson)
        )

        return SmpSyncPlaylistEntry(
            playlistId = source.playlistId,
            playlistName = source.playlistName,
            songIds = songIds,
            itemCount = source.items.size.takeIf { it > 0 } ?: source.songIds.size.takeIf { it > 0 },
            itemKeys = itemKeys.takeIf { it.isNotEmpty() } ?: songIds,
            itemsHash = itemsHash,
            groupsHash = groupsHash,
            colorsHash = colorsHash,
            fullPlaylistHash = fullPlaylistHash
        )
    }

    private fun buildFamilyEntry(source: SmpSyncFamilyManifestSource): SmpSyncFamilyEntry {
        val cleanSongIds = source.songIds
            .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
            .distinct()
        val hash = hashing.hashCanonicalJson(
            JSONObject()
                .put("familyId", source.familyId)
                .put("title", source.title)
                .put("songIds", JSONArray().apply { cleanSongIds.forEach(::put) })
                .putNullable("parentSongId", source.parentSongId)
                .putNullable("activeSongId", source.activeSongId)
        )
        return SmpSyncFamilyEntry(
            familyId = source.familyId,
            title = source.title,
            songIds = cleanSongIds,
            parentSongId = source.parentSongId,
            activeSongId = source.activeSongId,
            hash = hash
        )
    }

    private fun SongUnit.toManifestSource(context: Context): SmpSyncSongManifestSource {
        val songDir = storageFolder?.takeIf { it.isNotBlank() }?.let(::File)
        return SmpSyncSongManifestSource(
            songId = id,
            title = title,
            audioFile = audioPath.toFileOrNull(),
            lyricsFile = lyricsPath.toFileOrNull(),
            chordsFile = chordsPath.toFileOrNull(),
            notesHash = readNotesHash(context, id),
            prompterFile = prompterPath.toFileOrNull(),
            timelineFile = timelinePath.toFileOrNull() ?: songDir.resolveExisting("timeline.json"),
            midiFile = midiPath.toFileOrNull() ?: songDir.resolveExisting("midi_cues.json"),
            dmxFile = dmxPath.toFileOrNull() ?: songDir.resolveExisting("dmx_cues.json"),
            settingsFile = songDir.resolveExisting("config.json"),
            arrangementFile = songDir.resolveExisting("arrangement.json"),
            gridFile = songDir.resolveExisting("grid.json")
        )
    }

    private fun readNotesHash(context: Context, songId: String): String? {
        val notes = runCatching { NotesConfigStore.getBySongId(context, songId) }
            .getOrDefault(emptyList())
        if (notes.isEmpty()) return null

        val json = JSONArray().apply {
            notes.sortedWith(compareBy({ it.scopeKey }, { it.note.id })).forEach { scoped ->
                put(
                    JSONObject()
                        .put("scopeKey", scoped.scopeKey)
                        .put("id", scoped.note.id)
                        .put("title", scoped.note.title)
                        .put("content", scoped.note.content)
                        .put("updatedAt", scoped.note.updatedAt)
                )
            }
        }
        return hashing.hashNormalizedText(json.toString())
    }

    private fun readGlobalStateHash(context: Context): String? {
        val prompters = runCatching { TextSongRepository.exportAll(context) }
            .getOrDefault(emptyMap())
        val notes = runCatching { NotesConfigStore.getAll(context) }
            .getOrDefault(emptyList())
        if (prompters.isEmpty() && notes.isEmpty()) return null

        val root = JSONObject()
        root.put(
            "prompters",
            JSONObject().apply {
                prompters.keys.sorted().forEach { id ->
                    val data = prompters[id] ?: return@forEach
                    put(
                        id,
                        JSONObject()
                            .put("title", data.title)
                            .put("content", data.content)
                    )
                }
            }
        )
        root.put(
            "notes",
            JSONArray().apply {
                notes.sortedWith(compareBy({ it.scopeKey }, { it.note.id })).forEach { scoped ->
                    put(
                        JSONObject()
                            .put("scopeKey", scoped.scopeKey)
                            .put("id", scoped.note.id)
                            .put("title", scoped.note.title)
                            .put("content", scoped.note.content)
                            .put("updatedAt", scoped.note.updatedAt)
                    )
                }
            }
        )
        return hashing.hashCanonicalJson(root)
    }

    private fun String.toPlaylistSource(): SmpSyncPlaylistManifestSource {
        val rawItems = PlaylistRepository.getAllItemsRaw(this)
        val groupMarkers = PlaylistRepository.getAllSongsRaw(this)
            .filter { item -> isGroupHeader(item) }
            .map { item ->
                JSONObject()
                    .put("title", getGroupTitle(item))
                    .putNullable("colorArgb", getGroupColorArgb(item))
                    .toString()
            }
        return SmpSyncPlaylistManifestSource(
            playlistName = this,
            items = rawItems,
            songIds = rawItems.flatMap { it.referencedSongIds() },
            groupMarkers = groupMarkers,
            colorArgb = PlaylistRepository.getPlaylistColor(this)
        )
    }

    private fun collectFamilySources(
        context: Context,
        playlists: List<SmpSyncPlaylistManifestSource>
    ): List<SmpSyncFamilyManifestSource> {
        val out = linkedMapOf<String, SmpSyncFamilyManifestSource>()

        SongVariantFamiliesStore.load(context).forEach { family ->
            out[family.id] = SmpSyncFamilyManifestSource(
                familyId = family.id,
                title = family.title,
                songIds = family.songIds.toList(),
                parentSongId = family.parentSongId,
                activeSongId = family.activeSongId
            )
        }

        playlists.flatMap { it.items }.forEach { item ->
            if (!isVariantFamilyItem(item.uri)) return@forEach
            val familyId = getVariantFamilyId(item.uri) ?: return@forEach
            if (out.containsKey(familyId)) return@forEach

            val songIds = getVariantFamilySongIds(item.uri).toList()
            val title = getVariantFamilyTitle(item.uri) ?: familyId
            out[familyId] = SmpSyncFamilyManifestSource(
                familyId = familyId,
                title = title,
                songIds = songIds,
                parentSongId = songIds.firstOrNull(),
                activeSongId = songIds.firstOrNull()
            )
        }

        return out.values.toList()
    }

    private fun String?.toFileOrNull(): File? {
        return this
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.takeIf { it.isFile }
    }

    private fun File?.resolveExisting(fileName: String): File? {
        return this
            ?.let { File(it, fileName) }
            ?.takeIf { it.isFile }
    }

    private fun SmpSyncSongManifestSource.maxComponentLastModified(): Long? {
        return listOfNotNull(
            audioFile,
            lyricsFile,
            chordsFile,
            prompterFile,
            timelineFile,
            midiFile,
            dmxFile,
            settingsFile,
            arrangementFile,
            gridFile
        )
            .filter { it.isFile }
            .maxOfOrNull { it.lastModified() }
            ?.takeIf { it > 0L }
    }

    private fun PlaylistItem.referencedSongIds(): List<String> {
        if (isVariantFamilyItem(uri)) {
            return getVariantFamilySongIds(uri).toList()
        }
        return listOfNotNull(songId?.trim()?.takeIf(String::isNotEmpty) ?: getSmpSongId(uri))
    }

    private fun PlaylistItem.syncDiagnosticKey(): String {
        val cleanUri = uri.trim()
        val resolvedSongId = songId?.trim()?.takeIf(String::isNotEmpty) ?: getSmpSongId(cleanUri)
        return when {
            isGroupHeader(cleanUri) -> "group:${getGroupTitle(cleanUri)}"
            isVariantFamilyItem(cleanUri) -> {
                val familyId = getVariantFamilyId(cleanUri) ?: cleanUri
                "family:$familyId:${resolvedSongId.orEmpty()}"
            }
            resolvedSongId != null -> "song:$resolvedSongId"
            else -> "item:$cleanUri"
        }
    }
}

private fun JSONObject.putNullable(key: String, value: String?): JSONObject {
    put(key, value ?: JSONObject.NULL)
    return this
}

private fun JSONObject.putNullable(key: String, value: Long?): JSONObject {
    put(key, value ?: JSONObject.NULL)
    return this
}
