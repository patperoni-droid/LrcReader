package com.patrick.lrcreader.core

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class WorkspaceResolverTest {

    @Test
    fun evaluate_returnsUnconfigured_whenNoRootIsRecorded() {
        val snapshot = WorkspaceResolver.evaluate(
            input = WorkspaceResolver.ResolutionInput(
                mode = StorageModePrefs.Mode.SAF,
                setupTreeUri = null,
                storedRootUri = null,
                hasSetupTreePermission = false,
                storedRootDirectory = null,
                setupTreeDirectory = null,
                setupTreeWorkspaceRoot = null
            )
        )

        assertEquals(WorkspaceResolver.Status.UNCONFIGURED, snapshot.status)
    }

    @Test
    fun evaluate_returnsPermissionMissing_whenSafGrantIsMissing() {
        val setupTree = uri("content://workspace/tree/documents")
        val storedRoot = uri("content://workspace/tree/spl")
        val snapshot = WorkspaceResolver.evaluate(
            input = WorkspaceResolver.ResolutionInput(
                mode = StorageModePrefs.Mode.SAF,
                setupTreeUri = setupTree,
                storedRootUri = storedRoot,
                hasSetupTreePermission = false,
                storedRootDirectory = dir(
                    uri = storedRoot,
                    name = "SPL_Music",
                    readable = true,
                    childNames = listOf("BackingTracks", "Backups")
                ),
                setupTreeDirectory = dir(
                    uri = setupTree,
                    name = "Documents",
                    readable = true,
                    childNames = listOf("SPL_Music")
                ),
                setupTreeWorkspaceRoot = null
            )
        )

        assertEquals(WorkspaceResolver.Status.PERMISSION_MISSING, snapshot.status)
    }

    @Test
    fun evaluate_returnsReady_whenStoredRootIsDirectSplMusicFolder() {
        val rootUri = uri("content://workspace/tree/spl")
        val snapshot = WorkspaceResolver.evaluate(
            input = WorkspaceResolver.ResolutionInput(
                mode = StorageModePrefs.Mode.SAF,
                setupTreeUri = uri("content://workspace/tree/documents"),
                storedRootUri = rootUri,
                hasSetupTreePermission = true,
                storedRootDirectory = dir(
                    uri = rootUri,
                    name = "SPL_Music",
                    readable = true,
                    childNames = listOf("BackingTracks", "Backups", "DJ")
                ),
                setupTreeDirectory = null,
                setupTreeWorkspaceRoot = null
            )
        )

        assertEquals(WorkspaceResolver.Status.READY, snapshot.status)
        assertEquals(rootUri, snapshot.workspaceRootUri)
    }

    @Test
    fun evaluate_returnsReady_whenSetupTreeParentContainsSplMusicChild() {
        val setupTree = uri("content://workspace/tree/documents")
        val splRoot = uri("content://workspace/tree/documents/document/spl")
        val snapshot = WorkspaceResolver.evaluate(
            input = WorkspaceResolver.ResolutionInput(
                mode = StorageModePrefs.Mode.SAF,
                setupTreeUri = setupTree,
                storedRootUri = null,
                hasSetupTreePermission = true,
                storedRootDirectory = null,
                setupTreeDirectory = dir(
                    uri = setupTree,
                    name = "Documents",
                    readable = true,
                    childNames = listOf("SPL_Music", "Pictures")
                ),
                setupTreeWorkspaceRoot = dir(
                    uri = splRoot,
                    name = "SPL_Music",
                    readable = true,
                    childNames = listOf("BackingTracks", "Backups")
                )
            )
        )

        assertEquals(WorkspaceResolver.Status.READY, snapshot.status)
        assertEquals(splRoot, snapshot.workspaceRootUri)
    }

    @Test
    fun evaluate_returnsReady_whenStoredRootUsesWorkspaceFolderNameButHasBackingTracks() {
        val rootUri = uri("content://workspace/tree/stage")
        val snapshot = WorkspaceResolver.evaluate(
            input = WorkspaceResolver.ResolutionInput(
                mode = StorageModePrefs.Mode.SAF,
                setupTreeUri = uri("content://workspace/tree/documents"),
                storedRootUri = rootUri,
                hasSetupTreePermission = true,
                storedRootDirectory = dir(
                    uri = rootUri,
                    name = "StageMusicPlayer",
                    readable = true,
                    childNames = listOf("BackingTracks", "Backups")
                ),
                setupTreeDirectory = null,
                setupTreeWorkspaceRoot = null
            )
        )

        assertEquals(WorkspaceResolver.Status.READY, snapshot.status)
    }

    @Test
    fun evaluate_returnsRootInvalid_whenStoredRootIsReadableButDoesNotLookLikeWorkspace() {
        val setupTree = uri("content://workspace/tree/documents")
        val rootUri = uri("content://workspace/tree/random")
        val snapshot = WorkspaceResolver.evaluate(
            input = WorkspaceResolver.ResolutionInput(
                mode = StorageModePrefs.Mode.SAF,
                setupTreeUri = setupTree,
                storedRootUri = rootUri,
                hasSetupTreePermission = true,
                storedRootDirectory = dir(
                    uri = rootUri,
                    name = "Documents",
                    readable = true,
                    childNames = listOf("Pictures", "Movies")
                ),
                setupTreeDirectory = dir(
                    uri = setupTree,
                    name = "Documents",
                    readable = true,
                    childNames = listOf("Pictures", "Movies")
                ),
                setupTreeWorkspaceRoot = null
            )
        )

        assertEquals(WorkspaceResolver.Status.ROOT_INVALID, snapshot.status)
    }

    @Test
    fun evaluate_returnsInternalLegacy_whenInternalRootIsReadable() {
        val rootUri = uri("file:///storage/emulated/0/SPL_Music")
        val snapshot = WorkspaceResolver.evaluate(
            input = WorkspaceResolver.ResolutionInput(
                mode = StorageModePrefs.Mode.INTERNAL,
                setupTreeUri = null,
                storedRootUri = rootUri,
                hasSetupTreePermission = true,
                storedRootDirectory = dir(
                    uri = rootUri,
                    name = "SPL_Music",
                    readable = true,
                    childNames = listOf("BackingTracks", "Backups")
                ),
                setupTreeDirectory = null,
                setupTreeWorkspaceRoot = null
            )
        )

        assertEquals(WorkspaceResolver.Status.INTERNAL_LEGACY, snapshot.status)
        assertEquals(rootUri, snapshot.workspaceRootUri)
    }

    private fun uri(value: String): Uri {
        return mock(Uri::class.java).also { uri ->
            `when`(uri.toString()).thenReturn(value)
        }
    }

    private fun dir(
        uri: Uri,
        name: String,
        readable: Boolean,
        childNames: List<String>
    ): WorkspaceResolver.DirectorySnapshot {
        return WorkspaceResolver.DirectorySnapshot(
            uri = uri,
            name = name,
            isReadableDirectory = readable,
            childNames = childNames
        )
    }
}
