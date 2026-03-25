package com.patrick.lrcreader.core.backup

sealed interface BackupBundleRestorePreparationResult {
    data object NotBundle : BackupBundleRestorePreparationResult
    data object InvalidBundle : BackupBundleRestorePreparationResult

    data class SmpImportFailed(
        val songId: String,
        val reason: String? = null
    ) : BackupBundleRestorePreparationResult

    data class RemapFailed(
        val failures: List<BackupStateRemapFailure>
    ) : BackupBundleRestorePreparationResult

    data class Success(
        val stateJson: String,
        val warnings: List<BackupStateRemapWarning> = emptyList()
    ) : BackupBundleRestorePreparationResult
}

object BackupBundleRestorePreparer {

    fun prepareStateJsonForRestore(
        importResult: BackupBundleImportResult
    ): BackupBundleRestorePreparationResult {
        return when (importResult) {
            BackupBundleImportResult.NotBundle -> BackupBundleRestorePreparationResult.NotBundle
            BackupBundleImportResult.InvalidBundle -> BackupBundleRestorePreparationResult.InvalidBundle
            is BackupBundleImportResult.SmpImportFailed -> {
                BackupBundleRestorePreparationResult.SmpImportFailed(
                    songId = importResult.songId,
                    reason = importResult.reason
                )
            }

            is BackupBundleImportResult.Success -> {
                when (
                    val remapResult = BackupStateRemapper.remapBundleStateJson(
                        stateJson = importResult.stateJson,
                        importedSongs = importResult.importedSongs
                    )
                ) {
                    is BackupStateRemapResult.Success -> {
                        BackupBundleRestorePreparationResult.Success(
                            stateJson = remapResult.stateJson,
                            warnings = remapResult.warnings
                        )
                    }

                    is BackupStateRemapResult.Failure -> {
                        BackupBundleRestorePreparationResult.RemapFailed(
                            failures = remapResult.failures
                        )
                    }
                }
            }
        }
    }
}
