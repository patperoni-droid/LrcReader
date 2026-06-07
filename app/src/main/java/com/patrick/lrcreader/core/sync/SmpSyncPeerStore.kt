package com.patrick.lrcreader.core.sync

import android.content.Context
import android.os.Build
import java.util.UUID

enum class SmpSyncDeviceRole {
    MAIN,
    BACKUP,
    UNKNOWN;

    companion object {
        fun fromStoredValue(value: String?): SmpSyncDeviceRole {
            return values().firstOrNull { it.name == value } ?: UNKNOWN
        }
    }
}

data class SmpSyncPeerInfo(
    val pairedDeviceId: String? = null,
    val pairedDeviceName: String? = null,
    val lastHost: String? = null,
    val lastPort: Int? = null,
    val pairingToken: String? = null,
    val lastSyncAt: Long? = null
) {
    val hasKnownEndpoint: Boolean
        get() = !lastHost.isNullOrBlank() && lastPort != null

    val hasPairedDevice: Boolean
        get() = !pairedDeviceId.isNullOrBlank() ||
            !pairedDeviceName.isNullOrBlank() ||
            hasKnownEndpoint
}

data class SmpSyncPairingState(
    val localDeviceId: String,
    val localDeviceName: String,
    val preferredRole: SmpSyncDeviceRole,
    val peer: SmpSyncPeerInfo
)

object SmpSyncPeerStore {
    private const val PREFS_NAME = "smp_sync_peer_store"
    private const val KEY_LOCAL_DEVICE_ID = "localDeviceId"
    private const val KEY_LOCAL_DEVICE_NAME = "localDeviceName"
    private const val KEY_PREFERRED_ROLE = "preferredRole"
    private const val KEY_PAIRED_DEVICE_ID = "pairedDeviceId"
    private const val KEY_PAIRED_DEVICE_NAME = "pairedDeviceName"
    private const val KEY_LAST_HOST = "lastHost"
    private const val KEY_LAST_PORT = "lastPort"
    private const val KEY_PAIRING_TOKEN = "pairingToken"
    private const val KEY_LAST_SYNC_AT = "lastSyncAt"

    fun get(context: Context): SmpSyncPairingState {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val localDeviceId = prefs.getString(KEY_LOCAL_DEVICE_ID, null)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: createLocalDeviceId().also { generated ->
                prefs.edit().putString(KEY_LOCAL_DEVICE_ID, generated).apply()
            }
        val localDeviceName = prefs.getString(KEY_LOCAL_DEVICE_NAME, null)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: defaultDeviceName(localDeviceId).also { generated ->
                prefs.edit().putString(KEY_LOCAL_DEVICE_NAME, generated).apply()
            }
        val lastPort = prefs.getInt(KEY_LAST_PORT, -1).takeIf { it > 0 }
        val lastSyncAt = prefs.getLong(KEY_LAST_SYNC_AT, -1L).takeIf { it > 0L }

        return SmpSyncPairingState(
            localDeviceId = localDeviceId,
            localDeviceName = localDeviceName,
            preferredRole = SmpSyncDeviceRole.fromStoredValue(
                prefs.getString(KEY_PREFERRED_ROLE, null)
            ),
            peer = SmpSyncPeerInfo(
                pairedDeviceId = prefs.getString(KEY_PAIRED_DEVICE_ID, null)
                    ?.trim()
                    ?.takeIf { it.isNotBlank() },
                pairedDeviceName = prefs.getString(KEY_PAIRED_DEVICE_NAME, null)
                    ?.trim()
                    ?.takeIf { it.isNotBlank() },
                lastHost = prefs.getString(KEY_LAST_HOST, null)
                    ?.trim()
                    ?.takeIf { it.isNotBlank() },
                lastPort = lastPort,
                pairingToken = prefs.getString(KEY_PAIRING_TOKEN, null)
                    ?.trim()
                    ?.takeIf { it.isNotBlank() },
                lastSyncAt = lastSyncAt
            )
        )
    }

    fun setPreferredRole(context: Context, role: SmpSyncDeviceRole): SmpSyncPairingState {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PREFERRED_ROLE, role.name)
            .apply()
        return get(context)
    }

    fun rememberPairedDevice(
        context: Context,
        pairedDeviceName: String?,
        pairedDeviceId: String? = null
    ): SmpSyncPairingState {
        val cleanName = pairedDeviceName?.trim()?.takeIf { it.isNotBlank() }
        val cleanId = pairedDeviceId?.trim()?.takeIf { it.isNotBlank() }
        if (cleanName == null && cleanId == null) return get(context)

        val editor = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
        cleanName?.let { editor.putString(KEY_PAIRED_DEVICE_NAME, it) }
        cleanId?.let { editor.putString(KEY_PAIRED_DEVICE_ID, it) }
        editor.apply()
        return get(context)
    }

    fun rememberEndpoint(
        context: Context,
        host: String,
        port: Int,
        pairedDeviceName: String? = null,
        pairedDeviceId: String? = null
    ): SmpSyncPairingState {
        val cleanHost = host.trim().takeIf { it.isNotBlank() } ?: return get(context)
        val safePort = port.takeIf { it in 1..65535 } ?: return get(context)
        val cleanName = pairedDeviceName?.trim()?.takeIf { it.isNotBlank() }
        val cleanId = pairedDeviceId?.trim()?.takeIf { it.isNotBlank() }

        val editor = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_HOST, cleanHost)
            .putInt(KEY_LAST_PORT, safePort)
        cleanName?.let { editor.putString(KEY_PAIRED_DEVICE_NAME, it) }
        cleanId?.let { editor.putString(KEY_PAIRED_DEVICE_ID, it) }
        editor.apply()
        return get(context)
    }

    fun markSyncCompleted(context: Context, timestampMs: Long = System.currentTimeMillis()): SmpSyncPairingState {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_SYNC_AT, timestampMs.coerceAtLeast(1L))
            .apply()
        return get(context)
    }

    fun forgetPairedDevice(context: Context): SmpSyncPairingState {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_PAIRED_DEVICE_ID)
            .remove(KEY_PAIRED_DEVICE_NAME)
            .remove(KEY_LAST_HOST)
            .remove(KEY_LAST_PORT)
            .remove(KEY_PAIRING_TOKEN)
            .remove(KEY_LAST_SYNC_AT)
            .apply()
        return get(context)
    }

    private fun createLocalDeviceId(): String = "smp-${UUID.randomUUID()}"

    private fun defaultDeviceName(localDeviceId: String): String {
        val manufacturer = Build.MANUFACTURER
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        val model = Build.MODEL
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        return listOfNotNull(manufacturer, model)
            .distinctBy { it.lowercase() }
            .joinToString(separator = " ")
            .ifBlank { localDeviceId }
    }
}
