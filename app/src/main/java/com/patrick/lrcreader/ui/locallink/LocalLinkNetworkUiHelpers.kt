package com.patrick.lrcreader.ui.locallink

import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

internal fun findLocalIpv4Address(): String? = findLocalIpv4Addresses().firstOrNull()

internal fun findLocalIpv4Addresses(): List<String> {
    return runCatching {
        Collections.list(NetworkInterface.getNetworkInterfaces())
            .asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { networkInterface ->
                Collections.list(networkInterface.inetAddresses)
                    .asSequence()
                    .filterIsInstance<Inet4Address>()
                    .filter { !it.isLoopbackAddress }
                    .map { address -> networkInterface.name to address.hostAddress.orEmpty() }
            }
            .filter { (_, address) -> address.isNotBlank() }
            .sortedWith(
                compareBy<Pair<String, String>>(
                    { (name, _) ->
                        if (name.contains("wlan", ignoreCase = true) ||
                            name.contains("ap", ignoreCase = true)
                        ) {
                            0
                        } else {
                            1
                        }
                    },
                    { (_, address) ->
                        if (address.startsWith("192.168.") ||
                            address.startsWith("172.") ||
                            address.startsWith("10.")
                        ) {
                            0
                        } else {
                            1
                        }
                    }
                )
            )
            .map { (_, address) -> address }
            .distinct()
            .toList()
    }.getOrDefault(emptyList())
}
