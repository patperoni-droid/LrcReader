package com.patrick.lrcreader.core

import android.content.Context
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class BackupManagerAutoBackupTest {

    @Test
    fun autoSave_returnsFailedNoWorkspace_whenWorkspaceIsNotUsable() {
        val writer = RecordingWriter(
            result = BackupManager.AutoBackupResult(
                code = BackupManager.AutoBackupCode.SUCCESS,
                workspaceStatus = WorkspaceResolver.Status.READY,
                workspaceRootUri = uri("content://workspace/tree/spl")
            )
        )

        val result = BackupManager.autoSaveToDefaultBackupFile(
            context = mock(Context::class.java),
            snapshotOverride = snapshot(
                status = WorkspaceResolver.Status.UNCONFIGURED,
                rootUri = null
            ),
            writer = writer,
            jsonOverride = "{}"
        )

        assertEquals(BackupManager.AutoBackupCode.FAILED_NO_WORKSPACE, result.code)
        assertFalse(writer.wasCalled)
    }

    @Test
    fun autoSave_returnsSuccess_whenWriterPersistsDurableBackup() {
        val rootUri = uri("content://workspace/tree/spl")
        val targetFile = uri("content://workspace/tree/spl/document/backups/lrc_backup.json")
        val writer = RecordingWriter(
            result = BackupManager.AutoBackupResult(
                code = BackupManager.AutoBackupCode.SUCCESS,
                workspaceStatus = WorkspaceResolver.Status.READY,
                workspaceRootUri = rootUri,
                targetDirUri = uri("content://workspace/tree/spl/document/backups"),
                targetFileUri = targetFile,
                detail = "durable backup written"
            )
        )

        val result = BackupManager.autoSaveToDefaultBackupFile(
            context = mock(Context::class.java),
            snapshotOverride = snapshot(
                status = WorkspaceResolver.Status.READY,
                rootUri = rootUri
            ),
            writer = writer,
            jsonOverride = "{}"
        )

        assertTrue(writer.wasCalled)
        assertEquals(BackupManager.AutoBackupCode.SUCCESS, result.code)
        assertEquals(targetFile, result.targetFileUri)
    }

    @Test
    fun autoSave_returnsExplicitFailure_whenCreateFileFails() {
        val rootUri = uri("content://workspace/tree/spl")
        val writer = RecordingWriter(
            result = BackupManager.AutoBackupResult(
                code = BackupManager.AutoBackupCode.FAILED_CREATE_FILE,
                workspaceStatus = WorkspaceResolver.Status.READY,
                workspaceRootUri = rootUri,
                targetDirUri = uri("content://workspace/tree/spl/document/backups"),
                detail = "createFile returned null"
            )
        )

        val result = BackupManager.autoSaveToDefaultBackupFile(
            context = mock(Context::class.java),
            snapshotOverride = snapshot(
                status = WorkspaceResolver.Status.READY,
                rootUri = rootUri
            ),
            writer = writer,
            jsonOverride = "{}"
        )

        assertEquals(BackupManager.AutoBackupCode.FAILED_CREATE_FILE, result.code)
        assertTrue(writer.wasCalled)
    }

    @Test
    fun autoSave_returnsExplicitFailure_whenOpenOutputStreamFails() {
        val rootUri = uri("content://workspace/tree/spl")
        val writer = RecordingWriter(
            result = BackupManager.AutoBackupResult(
                code = BackupManager.AutoBackupCode.FAILED_OPEN_STREAM,
                workspaceStatus = WorkspaceResolver.Status.READY,
                workspaceRootUri = rootUri,
                targetDirUri = uri("content://workspace/tree/spl/document/backups"),
                targetFileUri = uri("content://workspace/tree/spl/document/backups/lrc_backup.json"),
                detail = "openOutputStream returned null"
            )
        )

        val result = BackupManager.autoSaveToDefaultBackupFile(
            context = mock(Context::class.java),
            snapshotOverride = snapshot(
                status = WorkspaceResolver.Status.READY,
                rootUri = rootUri
            ),
            writer = writer,
            jsonOverride = "{}"
        )

        assertEquals(BackupManager.AutoBackupCode.FAILED_OPEN_STREAM, result.code)
        assertTrue(writer.wasCalled)
    }

    @Test
    fun workerAction_returnsSuccessOnlyForTrueDurableSuccess() {
        assertEquals(
            BackupManager.AutoBackupWorkerAction.SUCCESS,
            BackupManager.workerActionForAutoBackup(
                BackupManager.AutoBackupResult(
                    code = BackupManager.AutoBackupCode.SUCCESS,
                    workspaceStatus = WorkspaceResolver.Status.READY,
                    workspaceRootUri = uri("content://workspace/tree/spl")
                )
            )
        )
        assertEquals(
            BackupManager.AutoBackupWorkerAction.FAILURE,
            BackupManager.workerActionForAutoBackup(
                BackupManager.AutoBackupResult(
                    code = BackupManager.AutoBackupCode.FAILED_NO_WORKSPACE,
                    workspaceStatus = WorkspaceResolver.Status.UNCONFIGURED,
                    workspaceRootUri = null
                )
            )
        )
        assertEquals(
            BackupManager.AutoBackupWorkerAction.RETRY,
            BackupManager.workerActionForAutoBackup(
                BackupManager.AutoBackupResult(
                    code = BackupManager.AutoBackupCode.FAILED_CREATE_FILE,
                    workspaceStatus = WorkspaceResolver.Status.READY,
                    workspaceRootUri = uri("content://workspace/tree/spl")
                )
            )
        )
        assertEquals(
            BackupManager.AutoBackupWorkerAction.RETRY,
            BackupManager.workerActionForAutoBackup(
                BackupManager.AutoBackupResult(
                    code = BackupManager.AutoBackupCode.FAILED_OPEN_STREAM,
                    workspaceStatus = WorkspaceResolver.Status.READY,
                    workspaceRootUri = uri("content://workspace/tree/spl")
                )
            )
        )
    }

    private fun snapshot(
        status: WorkspaceResolver.Status,
        rootUri: Uri?
    ): WorkspaceResolver.Snapshot {
        return WorkspaceResolver.Snapshot(
            mode = if (rootUri?.scheme == "file") StorageModePrefs.Mode.INTERNAL else StorageModePrefs.Mode.SAF,
            setupTreeUri = null,
            workspaceRootUri = rootUri,
            status = status,
            detail = "test"
        )
    }

    private fun uri(value: String): Uri {
        return mock(Uri::class.java).also { uri ->
            `when`(uri.toString()).thenReturn(value)
            `when`(uri.scheme).thenReturn(value.substringBefore("://"))
            `when`(uri.path).thenReturn("/mock")
        }
    }

    private class RecordingWriter(
        private val result: BackupManager.AutoBackupResult
    ) : BackupManager.AutoBackupWriter {
        var wasCalled: Boolean = false
            private set

        override fun writeSaf(
            context: Context,
            snapshot: WorkspaceResolver.Snapshot,
            rootUri: Uri,
            fileName: String,
            json: String
        ): BackupManager.AutoBackupResult {
            wasCalled = true
            return result
        }

        override fun writeFile(
            snapshot: WorkspaceResolver.Snapshot,
            rootUri: Uri,
            fileName: String,
            json: String
        ): BackupManager.AutoBackupResult {
            wasCalled = true
            return result
        }
    }
}
