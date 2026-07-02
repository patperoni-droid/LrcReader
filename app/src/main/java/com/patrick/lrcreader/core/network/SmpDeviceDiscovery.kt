package com.patrick.lrcreader.core.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.patrick.lrcreader.core.locallink.LocalLinkMessage
import com.patrick.lrcreader.exo.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.nio.charset.StandardCharsets

data class SmpDiscoveredDevice(
    val deviceId: String,
    val deviceName: String,
    val protocolVersion: Int,
    val appVersion: String,
    val capabilities: Set<String>,
    val hostAddress: String?,
    val port: Int,
    val serviceName: String
)

private const val SMP_NSD_TAG = "SMP_NSD"
private const val SMP_SERVICE_TYPE = "_smp-locallink._tcp"
private const val SMP_CAPABILITY_LYRICS = "lyrics"

private object SmpDeviceIdentity {
    fun deviceId(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: "smp-${Build.FINGERPRINT.hashCode()}"
    }

    fun deviceName(): String {
        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        return listOf(manufacturer, model)
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .joinToString(" ")
            .ifBlank { "SMP" }
    }
}

class SmpDeviceAdvertiser(
    context: Context
) {
    private val appContext = context.applicationContext
    private val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    fun start(port: Int) {
        if (port !in 1..65535) {
            Log.w(SMP_NSD_TAG, "advertiser_invalid_port port=$port")
            return
        }
        if (registrationListener != null) return
        multicastLock = acquireMulticastLock(appContext, "SmpDeviceAdvertiser")
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                Log.d(
                    SMP_NSD_TAG,
                    "advertiser_registered name=${serviceInfo.serviceName} port=${serviceInfo.port}"
                )
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(SMP_NSD_TAG, "advertiser_registration_failed code=$errorCode")
                registrationListener = null
                releaseMulticastLock()
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                Log.d(SMP_NSD_TAG, "advertiser_unregistered name=${serviceInfo.serviceName}")
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(SMP_NSD_TAG, "advertiser_unregistration_failed code=$errorCode")
            }
        }
        registrationListener = listener
        runCatching {
            nsdManager.registerService(buildServiceInfo(port), NsdManager.PROTOCOL_DNS_SD, listener)
        }.onFailure { error ->
            Log.w(SMP_NSD_TAG, "advertiser_start_failed", error)
            registrationListener = null
            releaseMulticastLock()
        }
    }

    fun stop() {
        val listener = registrationListener ?: return
        registrationListener = null
        runCatching { nsdManager.unregisterService(listener) }
        releaseMulticastLock()
    }

    private fun buildServiceInfo(port: Int): NsdServiceInfo {
        val deviceName = SmpDeviceIdentity.deviceName()
        val serviceInfo = NsdServiceInfo()
        serviceInfo.serviceName = "SMP ${deviceName.take(40)}"
        serviceInfo.serviceType = SMP_SERVICE_TYPE
        serviceInfo.port = port
        serviceInfo.setAttribute("deviceId", SmpDeviceIdentity.deviceId(appContext))
        serviceInfo.setAttribute("deviceName", deviceName)
        serviceInfo.setAttribute("protocolVersion", LocalLinkMessage.VERSION.toString())
        serviceInfo.setAttribute("appVersion", BuildConfig.VERSION_NAME)
        serviceInfo.setAttribute("capabilities", SMP_CAPABILITY_LYRICS)
        return serviceInfo
    }

    private fun releaseMulticastLock() {
        runCatching { multicastLock?.release() }
        multicastLock = null
    }
}

class SmpDeviceDiscovery(
    context: Context
) {
    private val appContext = context.applicationContext
    private val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val _devices = MutableStateFlow<List<SmpDiscoveredDevice>>(emptyList())
    val devices: StateFlow<List<SmpDiscoveredDevice>> = _devices
    private val resolvingServiceNames = mutableSetOf<String>()
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    fun start() {
        if (discoveryListener != null) return
        multicastLock = acquireMulticastLock(appContext, "SmpDeviceDiscovery")
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                Log.d(SMP_NSD_TAG, "discovery_started type=$serviceType")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!matchesSmpService(serviceInfo.serviceType)) return
                resolve(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                val lostName = serviceInfo.serviceName
                _devices.update { current ->
                    current.filterNot { it.serviceName == lostName }
                }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(SMP_NSD_TAG, "discovery_stopped type=$serviceType")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(SMP_NSD_TAG, "discovery_start_failed code=$errorCode")
                stop()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(SMP_NSD_TAG, "discovery_stop_failed code=$errorCode")
                stop()
            }
        }
        discoveryListener = listener
        runCatching {
            nsdManager.discoverServices(SMP_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        }.onFailure { error ->
            Log.w(SMP_NSD_TAG, "discovery_start_failed", error)
            discoveryListener = null
            releaseMulticastLock()
        }
    }

    fun stop() {
        val listener = discoveryListener ?: return
        discoveryListener = null
        resolvingServiceNames.clear()
        _devices.value = emptyList()
        runCatching { nsdManager.stopServiceDiscovery(listener) }
        releaseMulticastLock()
    }

    private fun resolve(serviceInfo: NsdServiceInfo) {
        val serviceName = serviceInfo.serviceName
        if (!resolvingServiceNames.add(serviceName)) return
        runCatching {
            nsdManager.resolveService(
                serviceInfo,
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                        resolvingServiceNames.remove(serviceName)
                        Log.w(SMP_NSD_TAG, "resolve_failed name=$serviceName code=$errorCode")
                    }

                    override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                        resolvingServiceNames.remove(serviceName)
                        val device = resolvedInfo.toSmpDevice() ?: return
                        _devices.update { current ->
                            val filtered = current.filterNot {
                                it.deviceId == device.deviceId || it.serviceName == device.serviceName
                            }
                            (filtered + device).sortedBy { it.deviceName.lowercase() }
                        }
                    }
                }
            )
        }.onFailure { error ->
            resolvingServiceNames.remove(serviceName)
            Log.w(SMP_NSD_TAG, "resolve_start_failed name=$serviceName", error)
        }
    }

    private fun releaseMulticastLock() {
        runCatching { multicastLock?.release() }
        multicastLock = null
    }
}

private fun NsdServiceInfo.toSmpDevice(): SmpDiscoveredDevice? {
    val protocolVersion = attribute("protocolVersion")?.toIntOrNull() ?: return null
    if (protocolVersion != LocalLinkMessage.VERSION) return null
    val capabilities = attribute("capabilities")
        ?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.toSet()
        .orEmpty()
    if (SMP_CAPABILITY_LYRICS !in capabilities) return null
    val deviceId = attribute("deviceId")?.takeIf { it.isNotBlank() } ?: serviceName
    val deviceName = attribute("deviceName")?.takeIf { it.isNotBlank() } ?: serviceName
    return SmpDiscoveredDevice(
        deviceId = deviceId,
        deviceName = deviceName,
        protocolVersion = protocolVersion,
        appVersion = attribute("appVersion").orEmpty(),
        capabilities = capabilities,
        hostAddress = host?.hostAddress,
        port = port,
        serviceName = serviceName
    )
}

private fun NsdServiceInfo.attribute(name: String): String? {
    return attributes[name]?.toString(StandardCharsets.UTF_8)?.trim()
}

private fun matchesSmpService(serviceType: String?): Boolean {
    return serviceType
        ?.trimEnd('.')
        ?.equals(SMP_SERVICE_TYPE.trimEnd('.'), ignoreCase = true) == true
}

private fun acquireMulticastLock(
    context: Context,
    tag: String
): WifiManager.MulticastLock? {
    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        ?: return null
    return runCatching {
        wifiManager.createMulticastLock(tag).apply {
            setReferenceCounted(false)
            acquire()
        }
    }.getOrNull()
}
