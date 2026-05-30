package com.patrick.lrcreader.core.sync

const val SMP_SYNC_PACKAGE_SCHEMA_VERSION = 1

data class SmpSyncPackage(
    val schemaVersion: Int = SMP_SYNC_PACKAGE_SCHEMA_VERSION,
    val generatedAt: Long,
    val sourceDeviceId: String? = null,
    val items: List<SmpSyncPackageItem> = emptyList()
) {
    val itemCount: Int
        get() = items.size

    val estimatedBytes: Long?
        get() {
            var total = 0L
            items.forEach { item ->
                val size = item.estimatedBytes ?: return null
                total += size
            }
            return total
        }

    val knownEstimatedBytes: Long
        get() = items.sumOf { it.estimatedBytes ?: 0L }

    val hasCompleteSizeEstimate: Boolean
        get() = items.all { it.estimatedBytes != null }

    val fullSongCount: Int
        get() = countKind(SmpSyncPackageKind.SONG_FULL)

    val songComponentCount: Int
        get() = countKind(SmpSyncPackageKind.SONG_COMPONENT)

    val playlistStateCount: Int
        get() = countKind(SmpSyncPackageKind.PLAYLIST_STATE)

    val familyStateCount: Int
        get() = countKind(SmpSyncPackageKind.FAMILY_STATE)

    val globalStateCount: Int
        get() = countKind(SmpSyncPackageKind.GLOBAL_STATE)

    private fun countKind(kind: SmpSyncPackageKind): Int {
        return items.count { it.kind == kind }
    }
}

data class SmpSyncPackageItem(
    val kind: SmpSyncPackageKind,
    val entityId: String,
    val title: String? = null,
    val sourceHash: String? = null,
    val estimatedBytes: Long? = null,
    val componentName: String? = null
)

enum class SmpSyncPackageKind {
    SONG_FULL,
    SONG_COMPONENT,
    PLAYLIST_STATE,
    FAMILY_STATE,
    GLOBAL_STATE
}
