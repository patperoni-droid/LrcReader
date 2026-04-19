package com.patrick.lrcreader.core

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

object WorkspaceResolver {

    private const val WORKSPACE_PERF_TRACE_TAG = "WORKSPACE_PERF_TRACE"
    private const val ANR_WORKSPACE_TAG = "ANR_WORKSPACE"
    private val resolveCallCounter = AtomicInteger(0)

    enum class Status {
        READY,
        UNCONFIGURED,
        PERMISSION_MISSING,
        ROOT_UNREADABLE,
        ROOT_INVALID,
        INTERNAL_LEGACY
    }

    data class Snapshot(
        val mode: StorageModePrefs.Mode,
        val setupTreeUri: Uri?,
        val workspaceRootUri: Uri?,
        val status: Status,
        val detail: String? = null
    ) {
        val isUsable: Boolean
            get() = status == Status.READY || status == Status.INTERNAL_LEGACY
    }

    internal data class DirectorySnapshot(
        val uri: Uri,
        val name: String?,
        val isReadableDirectory: Boolean,
        val childNames: List<String> = emptyList()
    )

    internal data class ResolutionInput(
        val mode: StorageModePrefs.Mode,
        val setupTreeUri: Uri?,
        val storedRootUri: Uri?,
        val hasSetupTreePermission: Boolean,
        val storedRootDirectory: DirectorySnapshot?,
        val setupTreeDirectory: DirectorySnapshot?,
        val setupTreeWorkspaceRoot: DirectorySnapshot?
    )

    fun resolve(context: Context): Snapshot {
        val callId = resolveCallCounter.incrementAndGet()
        val startMs = SystemClock.elapsedRealtime()
        val threadName = Thread.currentThread().name
        Log.i(
            WORKSPACE_PERF_TRACE_TAG,
            "step=resolve_start call=$callId timeMs=$startMs"
        )
        Log.e(
            ANR_WORKSPACE_TAG,
            "resolve:start call=$callId thread=$threadName"
        )
        val mode = StorageModePrefs.get(context)
        val setupTreeUri = resolveSetupTreeUri(context, mode)
        val storedRootUri = resolveStoredRootUri(context, mode)
        val setupTreePermission = when (mode) {
            StorageModePrefs.Mode.INTERNAL -> true
            StorageModePrefs.Mode.SAF -> setupTreeUri != null &&
                setupTreeUri.scheme == "content" &&
                BackupFolderPrefs.hasValidSetupTreePermission(context)
        }

        val storedRootDirectory = storedRootUri?.let { probeDirectory(context, it) }
        val setupTreeDirectory = setupTreeUri?.let { probeDirectory(context, it) }
        val setupTreeWorkspaceRoot = when (mode) {
            StorageModePrefs.Mode.INTERNAL -> null
            StorageModePrefs.Mode.SAF -> setupTreeUri?.let { resolveWorkspaceRootFromSetupTree(context, it) }
        }

        val snapshot = evaluate(
            ResolutionInput(
                mode = mode,
                setupTreeUri = setupTreeUri,
                storedRootUri = storedRootUri,
                hasSetupTreePermission = setupTreePermission,
                storedRootDirectory = storedRootDirectory,
                setupTreeDirectory = setupTreeDirectory,
                setupTreeWorkspaceRoot = setupTreeWorkspaceRoot
            )
        )
        val durationMs = SystemClock.elapsedRealtime() - startMs
        Log.i(
            WORKSPACE_PERF_TRACE_TAG,
            "step=resolve_done call=$callId durationMs=$durationMs mode=$mode status=${snapshot.status} root=${snapshot.workspaceRootUri} setupTree=${snapshot.setupTreeUri} detail=${snapshot.detail}"
        )
        Log.e(
            ANR_WORKSPACE_TAG,
            "resolve:end call=$callId durationMs=$durationMs thread=$threadName mode=$mode status=${snapshot.status} root=${snapshot.workspaceRootUri} setupTree=${snapshot.setupTreeUri}"
        )
        return snapshot
    }

    internal fun evaluate(input: ResolutionInput): Snapshot {
        return when (input.mode) {
            StorageModePrefs.Mode.INTERNAL -> evaluateInternal(input)
            StorageModePrefs.Mode.SAF -> evaluateSaf(input)
        }
    }

    internal fun shouldUseDirectoryAsWorkspaceRoot(
        folderName: String?,
        childNames: List<String>
    ): Boolean {
        if (normalizeWorkspaceToken(folderName) == "splmusic") {
            return true
        }

        val normalizedChildren = childNames.map(::normalizeWorkspaceToken)
        return normalizedChildren.any { child ->
            child == "backingtracks" || child == "backingtrack"
        }
    }

    private fun evaluateInternal(input: ResolutionInput): Snapshot {
        val rootUri = input.storedRootUri
        val rootDirectory = input.storedRootDirectory

        return when {
            rootUri == null -> Snapshot(
                mode = input.mode,
                setupTreeUri = null,
                workspaceRootUri = null,
                status = Status.UNCONFIGURED,
                detail = "mode=INTERNAL rootUri absent"
            )

            rootDirectory?.isReadableDirectory == true -> Snapshot(
                mode = input.mode,
                setupTreeUri = null,
                workspaceRootUri = rootUri,
                status = Status.INTERNAL_LEGACY,
                detail = "mode=INTERNAL root ready"
            )

            else -> Snapshot(
                mode = input.mode,
                setupTreeUri = null,
                workspaceRootUri = rootUri,
                status = Status.ROOT_UNREADABLE,
                detail = "mode=INTERNAL root unreadable"
            )
        }
    }

    private fun evaluateSaf(input: ResolutionInput): Snapshot {
        val storedReady = input.storedRootDirectory
            ?.takeIf { it.isReadableDirectory && shouldUseDirectoryAsWorkspaceRoot(it.name, it.childNames) }

        val setupReady = input.setupTreeWorkspaceRoot
            ?.takeIf { it.isReadableDirectory && shouldUseDirectoryAsWorkspaceRoot(it.name, it.childNames) }

        if (input.setupTreeUri == null && input.storedRootUri == null) {
            return Snapshot(
                mode = input.mode,
                setupTreeUri = null,
                workspaceRootUri = null,
                status = Status.UNCONFIGURED,
                detail = "mode=SAF no setup tree and no stored root"
            )
        }

        if (!input.hasSetupTreePermission) {
            return Snapshot(
                mode = input.mode,
                setupTreeUri = input.setupTreeUri,
                workspaceRootUri = input.storedRootUri ?: input.setupTreeWorkspaceRoot?.uri ?: input.setupTreeUri,
                status = Status.PERMISSION_MISSING,
                detail = "mode=SAF setup tree permission missing"
            )
        }

        if (storedReady != null) {
            return Snapshot(
                mode = input.mode,
                setupTreeUri = input.setupTreeUri,
                workspaceRootUri = storedReady.uri,
                status = Status.READY,
                detail = "mode=SAF stored root ready"
            )
        }

        if (setupReady != null) {
            return Snapshot(
                mode = input.mode,
                setupTreeUri = input.setupTreeUri,
                workspaceRootUri = setupReady.uri,
                status = Status.READY,
                detail = "mode=SAF resolved from setup tree"
            )
        }

        if (input.storedRootUri != null) {
            val status = if (input.storedRootDirectory?.isReadableDirectory == true) {
                Status.ROOT_INVALID
            } else {
                Status.ROOT_UNREADABLE
            }
            return Snapshot(
                mode = input.mode,
                setupTreeUri = input.setupTreeUri,
                workspaceRootUri = input.storedRootUri,
                status = status,
                detail = "mode=SAF stored root not usable"
            )
        }

        val setupTreeDirectory = input.setupTreeDirectory
        val status = if (setupTreeDirectory?.isReadableDirectory == true) {
            Status.ROOT_INVALID
        } else {
            Status.ROOT_UNREADABLE
        }
        return Snapshot(
            mode = input.mode,
            setupTreeUri = input.setupTreeUri,
            workspaceRootUri = input.setupTreeWorkspaceRoot?.uri ?: input.setupTreeUri,
            status = status,
            detail = "mode=SAF setup tree does not resolve to a usable workspace root"
        )
    }

    private fun resolveSetupTreeUri(
        context: Context,
        mode: StorageModePrefs.Mode
    ): Uri? {
        return when (mode) {
            StorageModePrefs.Mode.INTERNAL -> null
            StorageModePrefs.Mode.SAF -> sequenceOf(
                BackupFolderPrefsSaf.getSetupTreeUri(context),
                BackupFolderPrefs.getSetupTreeUri(context)
            ).filterNotNull().firstOrNull { it.scheme == "content" }
        }
    }

    private fun resolveStoredRootUri(
        context: Context,
        mode: StorageModePrefs.Mode
    ): Uri? {
        return when (mode) {
            StorageModePrefs.Mode.INTERNAL -> sequenceOf(
                BackupFolderPrefsInternal.getLibraryRootUri(context),
                BackupFolderPrefs.getLibraryRootUri(context)
            ).filterNotNull().firstOrNull { it.scheme == "file" }

            StorageModePrefs.Mode.SAF -> sequenceOf(
                BackupFolderPrefsSaf.getLibraryRootUri(context),
                BackupFolderPrefs.getLibraryRootUri(context)
            ).filterNotNull().firstOrNull { it.scheme == "content" }
        }
    }

    private fun resolveWorkspaceRootFromSetupTree(
        context: Context,
        setupTreeUri: Uri
    ): DirectorySnapshot? {
        val base = probeDirectory(context, setupTreeUri) ?: return null
        if (!base.isReadableDirectory) {
            return null
        }
        if (shouldUseDirectoryAsWorkspaceRoot(base.name, base.childNames)) {
            return base
        }

        val splChildUri = findChildDirectoryUri(
            context = context,
            parentUri = setupTreeUri,
            expectedNames = listOf("SPL_Music", "spl_music")
        ) ?: return null

        return probeDirectory(context, splChildUri)
    }

    private fun probeDirectory(context: Context, uri: Uri): DirectorySnapshot? {
        val startMs = SystemClock.elapsedRealtime()
        val threadName = Thread.currentThread().name
        Log.e(
            ANR_WORKSPACE_TAG,
            "probe:start uri=$uri scheme=${uri.scheme} thread=$threadName"
        )
        val result = when (uri.scheme) {
            "file" -> {
                val path = uri.path ?: return null
                val file = File(path)
                DirectorySnapshot(
                    uri = uri,
                    name = file.name,
                    isReadableDirectory = file.exists() && file.isDirectory,
                    childNames = file.list()?.sorted().orEmpty()
                )
            }

            "content" -> {
                val doc = DocumentFile.fromTreeUri(context, uri)
                    ?: DocumentFile.fromSingleUri(context, uri)
                    ?: return null
                DirectorySnapshot(
                    uri = uri,
                    name = doc.name,
                    isReadableDirectory = doc.exists() && doc.isDirectory,
                    childNames = runCatching {
                        doc.listFiles().mapNotNull { child -> child.name }.sorted()
                    }.getOrDefault(emptyList())
                )
            }

            else -> null
        }
        Log.e(
            ANR_WORKSPACE_TAG,
            "probe:end uri=$uri scheme=${uri.scheme} durationMs=${SystemClock.elapsedRealtime() - startMs} thread=$threadName readable=${result?.isReadableDirectory} childCount=${result?.childNames?.size ?: 0}"
        )
        return result
    }

    private fun findChildDirectoryUri(
        context: Context,
        parentUri: Uri,
        expectedNames: List<String>
    ): Uri? {
        val wanted = expectedNames.map(::normalizeWorkspaceToken).toSet()
        val parentDoc = DocumentFile.fromTreeUri(context, parentUri)
            ?: DocumentFile.fromSingleUri(context, parentUri)
            ?: return null

        return parentDoc.listFiles().firstOrNull { child ->
            child.isDirectory && normalizeWorkspaceToken(child.name) in wanted
        }?.uri
    }

    private fun normalizeWorkspaceToken(rawValue: String?): String {
        return rawValue
            .orEmpty()
            .trim()
            .lowercase()
            .replace("_", "")
            .replace(" ", "")
            .replace(Regex("\\(\\d+\\)$"), "")
    }

    @Suppress("unused")
    private fun normalizeAsTreeUri(uri: Uri): Uri? {
        val authority = uri.authority ?: return null
        val treeId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
            ?: runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
            ?: return null
        return DocumentsContract.buildTreeDocumentUri(authority, treeId)
    }
}
