package com.patrick.lrcreader.core.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class SmpSyncPeerStoreTest {

    @Test
    fun deviceRoleFromStoredValue_fallsBackToUnknown() {
        assertEquals(SmpSyncDeviceRole.MAIN, SmpSyncDeviceRole.fromStoredValue("MAIN"))
        assertEquals(SmpSyncDeviceRole.BACKUP, SmpSyncDeviceRole.fromStoredValue("BACKUP"))
        assertEquals(SmpSyncDeviceRole.UNKNOWN, SmpSyncDeviceRole.fromStoredValue(null))
        assertEquals(SmpSyncDeviceRole.UNKNOWN, SmpSyncDeviceRole.fromStoredValue("MAIN_PHONE"))
    }

    @Test
    fun peerInfoEndpoint_requiresHostAndPort() {
        assertFalse(SmpSyncPeerInfo(lastHost = "192.168.1.10").hasKnownEndpoint)
        assertFalse(SmpSyncPeerInfo(lastPort = 4567).hasKnownEndpoint)
        assertTrue(SmpSyncPeerInfo(lastHost = "192.168.1.10", lastPort = 4567).hasKnownEndpoint)
    }

    @Test
    fun peerInfoPairedDevice_acceptsNameIdOrEndpoint() {
        assertFalse(SmpSyncPeerInfo().hasPairedDevice)
        assertTrue(SmpSyncPeerInfo(pairedDeviceName = "Lenovo").hasPairedDevice)
        assertTrue(SmpSyncPeerInfo(pairedDeviceId = "smp-device").hasPairedDevice)
        assertTrue(SmpSyncPeerInfo(lastHost = "192.168.1.10", lastPort = 4567).hasPairedDevice)
    }

    @Test
    fun canRememberDevice_rejectsDifferentKnownPair() {
        val current = SmpSyncPairingState(
            localDeviceId = "local",
            localDeviceName = "Phone",
            preferredRole = SmpSyncDeviceRole.MAIN,
            peer = SmpSyncPeerInfo(pairedDeviceId = "known-backup")
        )

        assertTrue(SmpSyncPeerStore.canRememberDevice(current, null))
        assertTrue(SmpSyncPeerStore.canRememberDevice(current, "known-backup"))
        assertFalse(SmpSyncPeerStore.canRememberDevice(current, "other-device"))
    }
}
