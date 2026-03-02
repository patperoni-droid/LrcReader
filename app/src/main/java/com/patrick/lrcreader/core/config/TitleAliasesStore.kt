package com.patrick.lrcreader.core.config

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.compose.runtime.mutableIntStateOf
import androidx.documentfile.provider.DocumentFile
import com.patrick.lrcreader.core.BackupFolderPrefs
import com.patrick.lrcreader.core.StorageModePrefs
import com.patrick.lrcreader.exo.BuildConfig
import org.json.JSONObject
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object TitleAliasesStore {

    private const val TAG = "TitleAliasesStore"
    private const val FILE_NAME = "title_aliases.json"

    private const val FALLBACK_PREFS = "title_aliases_fallback"
    private const val FALLBACK_KEY_JSON = "aliases_json"

    private val lock = ReentrantLock()
    private var cachedState: TitleAliasesState? = null

    var version = mutableIntStateOf(0)
        private set

    fun ensureInitialized(context: Context): Boolean {
        return ConfigJsonAtomicFileIo.ensureInitialized(
            context = context,
            fileName = FILE_NAME,
            defaultRawJson = TitleAliasesState.empty().toJson().toString(2),
            tag = TAG
        )
    }

    fun getTitleForTrack(context: Context, trackUriString: String): String? {
        val uriKey = trackUriString.trim()
        if (uriKey.isEmpty()) return null
        val preferredKey = resolvePreferredKey(context, uriKey)

        return lock.withLock {
            syncFallbackToPrimaryIfPossibleLocked(context)

            val state = readStateLocked(context)
            findAlias(state.aliases, preferredKey, uriKey)
                ?: findAlias(readFallbackAliasesLocked(context), preferredKey, uriKey)
        }
    }

    fun setTitleForTrack(context: Context, trackUriString: String, newTitle: String): Boolean {
        val cleanTitle = newTitle.trim()
        if (cleanTitle.isEmpty()) return clearTitleForTrack(context, trackUriString)

        val uriKey = trackUriString.trim()
        if (uriKey.isEmpty()) return false
        val preferredKey = resolvePreferredKey(context, uriKey) ?: uriKey

        return lock.withLock {
            val debug = buildStorageDebugSnapshot(context)
            val initOk = runCatching { ensureInitialized(context) }.getOrElse { t ->
                if (BuildConfig.DEBUG) {
                    Log.w("ALIAS_STORE", "ensureInitialized exception", t)
                }
                false
            }
            logStorageDebug(debug, initOk, cause = if (initOk) "ensureOk" else "ensureFailed")

            if (BuildConfig.DEBUG) {
                Log.d("ALIAS_STORE", "set key=$preferredKey title='$cleanTitle'")
            }

            if (initOk) {
                syncFallbackToPrimaryIfPossibleLocked(context)

                val state = readStateLocked(context)
                val nextAliases = state.aliases.toMutableMap()

                var changed = false
                if (nextAliases[preferredKey] != cleanTitle) {
                    nextAliases[preferredKey] = cleanTitle
                    changed = true
                }
                if (preferredKey != uriKey && nextAliases.remove(uriKey) != null) {
                    changed = true
                }

                if (!changed) {
                    clearFallbackAliasKeysLocked(context, setOf(preferredKey, uriKey))
                    return@withLock true
                }

                val nextState = state.copy(
                    schemaVersion = TitleAliasesState.SCHEMA_VERSION,
                    aliases = nextAliases
                )
                val savedPrimary = writePrimaryStateLocked(context, nextState)
                if (savedPrimary) {
                    clearFallbackAliasKeysLocked(context, setOf(preferredKey, uriKey))
                    bumpVersion()
                    return@withLock true
                }

                if (BuildConfig.DEBUG) {
                    Log.w("ALIAS_STORE", "primaryWriteFailed -> fallback")
                }
            }

            val fallbackSavedA = saveFallbackAliasLocked(context, preferredKey, cleanTitle)
            val fallbackSavedB = if (preferredKey != uriKey) saveFallbackAliasLocked(context, uriKey, cleanTitle) else true
            val fallbackSaved = fallbackSavedA && fallbackSavedB

            if (BuildConfig.DEBUG) {
                val fileDebug = resolveDebugFileLocation(context)
                if (fallbackSaved) {
                    Log.d("ALIAS_STORE", "fallbackWriteOk file=$fileDebug")
                } else {
                    Log.w("ALIAS_STORE", "fallbackWriteFail file=$fileDebug")
                }
            }

            if (fallbackSaved) bumpVersion()
            fallbackSaved
        }
    }

    fun clearTitleForTrack(context: Context, trackUriString: String): Boolean {
        val uriKey = trackUriString.trim()
        if (uriKey.isEmpty()) return false
        val preferredKey = resolvePreferredKey(context, uriKey)

        return lock.withLock {
            var changedFallback = false
            changedFallback = clearFallbackAliasKeysLocked(
                context,
                buildSet {
                    add(uriKey)
                    if (!preferredKey.isNullOrBlank()) add(preferredKey)
                }
            ) || changedFallback

            val initOk = runCatching { ensureInitialized(context) }.getOrDefault(false)
            if (!initOk) {
                if (changedFallback) bumpVersion()
                return@withLock changedFallback || true
            }

            val state = readStateLocked(context)
            val nextAliases = state.aliases.toMutableMap()

            var changedPrimary = false
            if (!preferredKey.isNullOrBlank() && nextAliases.remove(preferredKey) != null) {
                changedPrimary = true
            }
            if (preferredKey != uriKey && nextAliases.remove(uriKey) != null) {
                changedPrimary = true
            }

            if (!changedPrimary) {
                if (changedFallback) bumpVersion()
                return@withLock true
            }

            val saved = writePrimaryStateLocked(
                context,
                state.copy(
                    schemaVersion = TitleAliasesState.SCHEMA_VERSION,
                    aliases = nextAliases
                )
            )
            if (saved) {
                bumpVersion()
            }
            saved
        }
    }

    fun getAll(context: Context): Map<String, String> {
        return lock.withLock {
            syncFallbackToPrimaryIfPossibleLocked(context)
            val primary = readStateLocked(context).aliases
            if (primary.isNotEmpty()) return@withLock primary.toMap()
            readFallbackAliasesLocked(context).toMap()
        }
    }

    fun resolveKey(context: Context, trackUriString: String): String? {
        val uriKey = trackUriString.trim()
        if (uriKey.isEmpty()) return null
        return resolvePreferredKey(context, uriKey) ?: uriKey
    }

    fun migrateFromLegacyTitlesIfMissing(
        context: Context,
        legacyTitlesByUri: Map<String, String>
    ): Int {
        if (legacyTitlesByUri.isEmpty()) return 0

        var migrated = 0
        legacyTitlesByUri.forEach { (uri, title) ->
            val cleanUri = uri.trim()
            val cleanTitle = title.trim()
            if (cleanUri.isEmpty() || cleanTitle.isEmpty()) return@forEach

            val existing = getTitleForTrack(context, cleanUri)
            if (!existing.isNullOrBlank()) return@forEach

            if (setTitleForTrack(context, cleanUri, cleanTitle)) {
                migrated++
            }
        }

        return migrated
    }

    private fun resolvePreferredKey(context: Context, uriKey: String): String? {
        val relPath = TrackSettingsPathResolver.resolveRelativeTrackPath(context, uriKey)
        val cleanRel = relPath?.trim()?.trim('/')
        if (!cleanRel.isNullOrEmpty()) return cleanRel
        return uriKey
    }

    private fun findAlias(map: Map<String, String>, preferredKey: String?, uriKey: String): String? {
        val byPreferred = preferredKey?.let { map[it] }.orEmpty().trim()
        if (byPreferred.isNotEmpty()) return byPreferred

        if (preferredKey != null && preferredKey != uriKey) {
            val byUri = map[uriKey].orEmpty().trim()
            if (byUri.isNotEmpty()) return byUri
        }

        return null
    }

    private fun writePrimaryStateLocked(context: Context, next: TitleAliasesState): Boolean {
        val raw = next.toJson().toString(2)
        val bytes = raw.toByteArray(Charsets.UTF_8).size
        val fileDebug = resolveDebugFileLocation(context)

        val saved = runCatching {
            ConfigJsonAtomicFileIo.writeRawAtomic(
                context = context,
                fileName = FILE_NAME,
                rawJson = raw,
                tag = TAG,
                defaultRawJson = TitleAliasesState.empty().toJson().toString(2)
            )
        }.getOrElse { t ->
            if (BuildConfig.DEBUG) {
                Log.w("ALIAS_STORE", "writeFail file=$fileDebug", t)
            }
            false
        }

        if (saved) {
            cachedState = next
            if (BuildConfig.DEBUG) {
                Log.d("ALIAS_STORE", "writeOk file=$fileDebug bytes=$bytes")
            }
            return true
        }

        if (BuildConfig.DEBUG) {
            Log.w("ALIAS_STORE", "writeFail file=$fileDebug")
        }
        return false
    }

    private fun syncFallbackToPrimaryIfPossibleLocked(context: Context): Boolean {
        val fallback = readFallbackAliasesLocked(context)
        if (fallback.isEmpty()) return true

        val initOk = runCatching { ensureInitialized(context) }.getOrDefault(false)
        if (!initOk) return false

        val state = readStateLocked(context)
        val nextAliases = state.aliases.toMutableMap()

        var changed = false
        fallback.forEach { (key, value) ->
            val cleanKey = key.trim()
            val cleanValue = value.trim()
            if (cleanKey.isEmpty() || cleanValue.isEmpty()) return@forEach
            if (nextAliases[cleanKey] != cleanValue) {
                nextAliases[cleanKey] = cleanValue
                changed = true
            }
        }

        if (!changed) {
            clearAllFallbackAliasesLocked(context)
            return true
        }

        val saved = writePrimaryStateLocked(
            context,
            state.copy(
                schemaVersion = TitleAliasesState.SCHEMA_VERSION,
                aliases = nextAliases
            )
        )

        if (saved) {
            clearAllFallbackAliasesLocked(context)
            bumpVersion()
            if (BuildConfig.DEBUG) {
                Log.d("ALIAS_STORE", "fallbackSyncOk count=${fallback.size}")
            }
        }
        return saved
    }

    private fun readStateLocked(context: Context): TitleAliasesState {
        cachedState?.let { return it }

        val state = try {
            if (!ensureInitialized(context)) {
                TitleAliasesState.empty()
            } else {
                val raw = ConfigJsonAtomicFileIo.readRaw(
                    context = context,
                    fileName = FILE_NAME,
                    tag = TAG,
                    defaultRawJson = TitleAliasesState.empty().toJson().toString(2)
                )
                if (raw.isNullOrBlank()) {
                    TitleAliasesState.empty()
                } else {
                    TitleAliasesState.fromJson(raw)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "readStateLocked: parse failed", t)
            TitleAliasesState.empty()
        }

        cachedState = state
        return state
    }

    private fun readFallbackAliasesLocked(context: Context): MutableMap<String, String> {
        val prefs = context.getSharedPreferences(FALLBACK_PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(FALLBACK_KEY_JSON, null).orEmpty().trim()
        if (raw.isEmpty()) return linkedMapOf()

        return runCatching {
            val obj = JSONObject(raw)
            val out = linkedMapOf<String, String>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next().trim()
                val value = obj.optString(key, "").trim()
                if (key.isNotEmpty() && value.isNotEmpty()) {
                    out[key] = value
                }
            }
            out
        }.getOrElse {
            linkedMapOf()
        }
    }

    private fun writeFallbackAliasesLocked(context: Context, aliases: Map<String, String>): Boolean {
        val clean = linkedMapOf<String, String>()
        aliases.forEach { (k, v) ->
            val key = k.trim()
            val value = v.trim()
            if (key.isNotEmpty() && value.isNotEmpty()) {
                clean[key] = value
            }
        }

        return runCatching {
            val json = JSONObject()
            clean.forEach { (k, v) -> json.put(k, v) }
            context.getSharedPreferences(FALLBACK_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(FALLBACK_KEY_JSON, if (clean.isEmpty()) null else json.toString())
                .apply()
            true
        }.getOrDefault(false)
    }

    private fun saveFallbackAliasLocked(context: Context, key: String, value: String): Boolean {
        val cleanKey = key.trim()
        val cleanValue = value.trim()
        if (cleanKey.isEmpty() || cleanValue.isEmpty()) return false

        val map = readFallbackAliasesLocked(context)
        if (map[cleanKey] == cleanValue) return true
        map[cleanKey] = cleanValue
        return writeFallbackAliasesLocked(context, map)
    }

    private fun clearFallbackAliasKeysLocked(context: Context, keys: Set<String>): Boolean {
        if (keys.isEmpty()) return false
        val map = readFallbackAliasesLocked(context)
        if (map.isEmpty()) return false

        var changed = false
        keys.forEach { key ->
            if (map.remove(key) != null) changed = true
        }

        if (!changed) return false
        return writeFallbackAliasesLocked(context, map)
    }

    private fun clearAllFallbackAliasesLocked(context: Context): Boolean {
        return context.getSharedPreferences(FALLBACK_PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(FALLBACK_KEY_JSON)
            .commit()
    }

    private fun bumpVersion() {
        version.intValue = version.intValue + 1
    }

    private data class StorageDebugSnapshot(
        val root: String,
        val mode: String,
        val rootKind: String,
        val normalizedRoot: String,
        val perms: String,
        val resolveStorage: String
    )

    private fun buildStorageDebugSnapshot(context: Context): StorageDebugSnapshot {
        val mode = runCatching { StorageModePrefs.get(context).name }.getOrDefault("UNKNOWN")
        val rootUri = BackupFolderPrefs.getLibraryRootUri(context)
        val root = rootUri?.toString() ?: "null"

        if (rootUri == null) {
            return StorageDebugSnapshot(
                root = root,
                mode = mode,
                rootKind = "none",
                normalizedRoot = "none",
                perms = "none",
                resolveStorage = "fail:rootNull"
            )
        }

        val rootKind = classifyRootUri(rootUri)
        val normalized = if (rootUri.scheme == "content") normalizeRootTreeUri(rootUri).toString() else rootUri.toString()
        val perms = summarizePersistedPermissions(context, rootUri)
        val resolveStorage = probeResolveStorage(context, rootUri)

        return StorageDebugSnapshot(
            root = root,
            mode = mode,
            rootKind = rootKind,
            normalizedRoot = normalized,
            perms = perms,
            resolveStorage = resolveStorage
        )
    }

    private fun logStorageDebug(snapshot: StorageDebugSnapshot, initOk: Boolean, cause: String) {
        if (!BuildConfig.DEBUG) return
        Log.d(
            "ALIAS_STORE",
            "root=${snapshot.root} mode=${snapshot.mode} rootKind=${snapshot.rootKind} normalized=${snapshot.normalizedRoot} perms=${snapshot.perms} resolveStorage=${snapshot.resolveStorage} ok=$initOk cause=$cause"
        )
    }

    private fun classifyRootUri(rootUri: Uri): String {
        if (rootUri.scheme != "content") return rootUri.scheme ?: "unknown"
        val hasTree = runCatching { DocumentsContract.getTreeDocumentId(rootUri) }.getOrNull() != null
        val hasDoc = runCatching { DocumentsContract.getDocumentId(rootUri) }.getOrNull() != null
        return when {
            hasTree && hasDoc -> "tree+document"
            hasTree -> "tree"
            hasDoc -> "document"
            else -> "content:unknown"
        }
    }

    private fun summarizePersistedPermissions(context: Context, rootUri: Uri): String {
        if (rootUri.scheme != "content") return "n/a"

        val normalized = normalizeRootTreeUri(rootUri)
        val matching = context.contentResolver.persistedUriPermissions.filter { perm ->
            val pNorm = normalizeRootTreeUri(perm.uri)
            pNorm == normalized || perm.uri == rootUri
        }

        if (matching.isEmpty()) return "none"

        val read = matching.any { it.isReadPermission }
        val write = matching.any { it.isWritePermission }
        return "count=${matching.size},read=$read,write=$write"
    }

    private fun probeResolveStorage(context: Context, rootUri: Uri): String {
        return when (rootUri.scheme) {
            "file" -> {
                val rootPath = rootUri.path
                if (rootPath.isNullOrBlank()) {
                    "FILE fail:missingRootPath"
                } else {
                    val target = File(File(rootPath, "Config"), FILE_NAME)
                    "FILE target=${target.absolutePath}"
                }
            }

            "content" -> {
                val normalized = normalizeRootTreeUri(rootUri)
                val fromNormalized = DocumentFile.fromTreeUri(context, normalized)
                val fromRawTree = if (fromNormalized == null) DocumentFile.fromTreeUri(context, rootUri) else null
                val fromSingle = if (fromNormalized == null && fromRawTree == null) {
                    DocumentFile.fromSingleUri(context, rootUri)
                } else null

                val via = when {
                    fromNormalized != null -> "tree(normalized)"
                    fromRawTree != null -> "tree(raw)"
                    fromSingle != null -> "single"
                    else -> "none"
                }

                val rootDoc = fromNormalized ?: fromRawTree ?: fromSingle
                    ?: return "SAF fail:rootDocNull via=$via"

                if (!rootDoc.isDirectory) {
                    return "SAF fail:rootNotDirectory via=$via uri=${rootDoc.uri}"
                }

                val configDir = runCatching {
                    rootDoc.listFiles().firstOrNull {
                        it.isDirectory && (it.name ?: "").equals("Config", ignoreCase = true)
                    }
                }.getOrNull()

                if (configDir == null) {
                    "SAF via=$via root=${rootDoc.uri} config=(missing,willCreate) target=$FILE_NAME"
                } else {
                    "SAF via=$via root=${rootDoc.uri} config=${configDir.uri} target=${configDir.uri}/$FILE_NAME"
                }
            }

            else -> "fail:unsupportedScheme(${rootUri.scheme})"
        }
    }

    private fun resolveDebugFileLocation(context: Context): String {
        val rootUri = BackupFolderPrefs.getLibraryRootUri(context) ?: return "root=null"
        return when (rootUri.scheme) {
            "file" -> {
                val rootPath = rootUri.path
                if (rootPath.isNullOrBlank()) {
                    "file://(missing-root-path)/Config/$FILE_NAME"
                } else {
                    File(File(rootPath, "Config"), FILE_NAME).absolutePath
                }
            }

            "content" -> {
                val normalizedRootUri = normalizeRootTreeUri(rootUri)
                val rootDoc = DocumentFile.fromTreeUri(context, normalizedRootUri)
                    ?: DocumentFile.fromTreeUri(context, rootUri)
                    ?: DocumentFile.fromSingleUri(context, rootUri)
                    ?: return "content://(root-doc-null uri=$rootUri)"

                val configDir = runCatching {
                    rootDoc.listFiles().firstOrNull {
                        it.isDirectory && (it.name ?: "").equals("Config", ignoreCase = true)
                    }
                }.getOrNull()

                if (configDir == null) {
                    "root=$rootUri configDir=(missing) file=$FILE_NAME"
                } else {
                    val fileDoc = runCatching {
                        configDir.listFiles().firstOrNull {
                            it.isFile && (it.name ?: "").equals(FILE_NAME, ignoreCase = true)
                        }
                    }.getOrNull()
                    "root=$rootUri configDir=${configDir.uri} file=${fileDoc?.uri ?: "${configDir.uri}/$FILE_NAME"}"
                }
            }

            else -> "unsupportedRoot=$rootUri"
        }
    }

    private fun normalizeRootTreeUri(rootUri: Uri): Uri {
        if (rootUri.scheme != "content") return rootUri
        val authority = rootUri.authority ?: return rootUri
        val docId = runCatching { DocumentsContract.getDocumentId(rootUri) }.getOrNull() ?: return rootUri
        return runCatching {
            DocumentsContract.buildTreeDocumentUri(authority, docId)
        }.getOrDefault(rootUri)
    }
}

private data class TitleAliasesState(
    val schemaVersion: Int = SCHEMA_VERSION,
    val aliases: Map<String, String> = emptyMap()
) {
    fun toJson(): JSONObject {
        val root = JSONObject()
        root.put("schemaVersion", SCHEMA_VERSION)

        val aliasesJson = JSONObject()
        aliases.toSortedMap().forEach { (key, value) ->
            val cleanKey = key.trim()
            val cleanValue = value.trim()
            if (cleanKey.isNotEmpty() && cleanValue.isNotEmpty()) {
                aliasesJson.put(cleanKey, cleanValue)
            }
        }
        root.put("aliases", aliasesJson)
        return root
    }

    companion object {
        const val SCHEMA_VERSION = 1

        fun empty(): TitleAliasesState = TitleAliasesState(
            schemaVersion = SCHEMA_VERSION,
            aliases = emptyMap()
        )

        fun fromJson(raw: String): TitleAliasesState {
            val root = JSONObject(raw)
            val aliasesObj = when {
                root.optJSONObject("aliases") != null -> root.optJSONObject("aliases")
                root.has("schemaVersion") -> JSONObject()
                else -> root
            } ?: JSONObject()

            val aliases = linkedMapOf<String, String>()
            val keys = aliasesObj.keys()
            while (keys.hasNext()) {
                val key = keys.next().trim()
                val value = aliasesObj.optString(key, "").trim()
                if (key.isNotEmpty() && value.isNotEmpty()) {
                    aliases[key] = value
                }
            }

            return TitleAliasesState(
                schemaVersion = root.optInt("schemaVersion", SCHEMA_VERSION),
                aliases = aliases
            )
        }
    }
}
