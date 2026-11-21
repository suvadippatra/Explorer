package com.troikoss.continuum_explorer.server.omnios

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import com.troikoss.continuum_explorer.server.ServerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.net.Inet4Address
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

enum class OmniOSPeerRole {
    HOST,
    RECEIVER
}

data class DiscoveredOmniOSPeer(
    val serviceName: String,
    val deviceName: String,
    val ip: String,
    val port: Int,
    val role: OmniOSPeerRole,
    val lastSeenMs: Long = System.currentTimeMillis()
)

/**
 * Dual-role LAN NSD/mDNS discovery engine for OmniOS App-to-App Casting.
 *
 * - Advertises `_omnios._tcp.` when this device is running as a Casting Host.
 * - Advertises `_omnios-rx._tcp.` when this device is listening in Receiver Mode.
 * - Browses both service types on the local Wi-Fi network so Hosts can find Receivers
 *   and Receivers can find Hosts automatically without typing IP addresses.
 */
object OmniOSDiscoveryManager {
    private const val TAG = "OmniOSDiscovery"
    const val SERVICE_TYPE_HOST = "_omnios._tcp."
    const val SERVICE_TYPE_RECEIVER = "_omnios-rx._tcp."

    private val resolveExecutor = Executors.newSingleThreadExecutor()
    private val legacyResolveQueue = ConcurrentLinkedQueue<Pair<NsdServiceInfo, OmniOSPeerRole>>()
    private val isLegacyResolving = AtomicBoolean(false)

    @Volatile
    private var nsdManager: NsdManager? = null

    @Volatile
    private var multicastLock: WifiManager.MulticastLock? = null

    @Volatile
    private var hostRegListener: NsdManager.RegistrationListener? = null

    @Volatile
    private var receiverRegListener: NsdManager.RegistrationListener? = null

    @Volatile
    private var hostBrowseListener: NsdManager.DiscoveryListener? = null

    @Volatile
    private var receiverBrowseListener: NsdManager.DiscoveryListener? = null

    @Volatile
    private var registeredHostServiceName: String? = null

    @Volatile
    private var registeredReceiverServiceName: String? = null

    @Volatile
    private var registeredHostPort: Int = -1

    @Volatile
    private var registeredReceiverPort: Int = -1

    private val activeInfoCallbacks = mutableListOf<Any>()

    private val _discoveredHosts = MutableStateFlow<List<DiscoveredOmniOSPeer>>(emptyList())
    val discoveredHosts: StateFlow<List<DiscoveredOmniOSPeer>> = _discoveredHosts.asStateFlow()

    private val _discoveredReceivers = MutableStateFlow<List<DiscoveredOmniOSPeer>>(emptyList())
    val discoveredReceivers: StateFlow<List<DiscoveredOmniOSPeer>> = _discoveredReceivers.asStateFlow()

    private val _isAdvertisingHost = MutableStateFlow(false)
    val isAdvertisingHost: StateFlow<Boolean> = _isAdvertisingHost.asStateFlow()

    private val _isAdvertisingReceiver = MutableStateFlow(false)
    val isAdvertisingReceiver: StateFlow<Boolean> = _isAdvertisingReceiver.asStateFlow()

    private val _isScanningHosts = MutableStateFlow(false)
    val isScanningHosts: StateFlow<Boolean> = _isScanningHosts.asStateFlow()

    private val _isScanningReceivers = MutableStateFlow(false)
    val isScanningReceivers: StateFlow<Boolean> = _isScanningReceivers.asStateFlow()

    fun getLocalDeviceLabel(): String {
        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        val raw = if (model.startsWith(manufacturer, ignoreCase = true)) {
            model
        } else {
            "$manufacturer $model".trim()
        }
        return raw.ifBlank { "Android Device" }.take(32)
    }

    private fun ensureNsd(context: Context): NsdManager? {
        if (nsdManager == null) {
            nsdManager = context.applicationContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
        }
        return nsdManager
    }

    private fun acquireMulticastLock(context: Context) {
        try {
            if (multicastLock == null) {
                val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                multicastLock = wm?.createMulticastLock("Continuum:OmniOS_NSD")?.apply {
                    setReferenceCounted(false)
                }
            }
            if (multicastLock?.isHeld == false) {
                multicastLock?.acquire()
            }
        } catch (e: Exception) {
            Log.w(TAG, "MulticastLock unavailable: ${e.message}")
        }
    }

    private fun releaseMulticastLockIfIdle() {
        if (!_isScanningHosts.value && !_isScanningReceivers.value) {
            try {
                if (multicastLock?.isHeld == true) {
                    multicastLock?.release()
                }
            } catch (_: Exception) {}
        }
    }

    fun registerHostService(context: Context, port: Int) {
        unregisterHostService()
        val manager = ensureNsd(context) ?: return
        val cleanName = "OmniOS-${getLocalDeviceLabel().replace(Regex("[^A-Za-z0-9\\- ]"), "")}".take(40)
        registeredHostPort = port
        val info = NsdServiceInfo().apply {
            serviceName = cleanName
            serviceType = SERVICE_TYPE_HOST
            setPort(port)
            setAttribute("role", "host")
            setAttribute("device", getLocalDeviceLabel())
        }

        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                registeredHostServiceName = serviceInfo.serviceName
                _isAdvertisingHost.value = true
                Log.i(TAG, "Registered OmniOS Host NSD: ${serviceInfo.serviceName} :$port")
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                _isAdvertisingHost.value = false
                Log.w(TAG, "Host NSD registration failed: $errorCode")
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                registeredHostServiceName = null
                _isAdvertisingHost.value = false
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                _isAdvertisingHost.value = false
            }
        }

        hostRegListener = listener
        try {
            manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register Host NSD: ${e.message}")
            hostRegListener = null
        }
    }

    fun unregisterHostService() {
        val listener = hostRegListener ?: return
        hostRegListener = null
        registeredHostServiceName = null
        registeredHostPort = -1
        _isAdvertisingHost.value = false
        try {
            nsdManager?.unregisterService(listener)
        } catch (_: Exception) {}
    }

    fun registerReceiverService(context: Context, port: Int) {
        unregisterReceiverService()
        val manager = ensureNsd(context) ?: return
        val cleanName = "OmniOS-RX-${getLocalDeviceLabel().replace(Regex("[^A-Za-z0-9\\- ]"), "")}".take(42)
        registeredReceiverPort = port
        val info = NsdServiceInfo().apply {
            serviceName = cleanName
            serviceType = SERVICE_TYPE_RECEIVER
            setPort(port)
            setAttribute("role", "receiver")
            setAttribute("device", getLocalDeviceLabel())
        }

        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                registeredReceiverServiceName = serviceInfo.serviceName
                _isAdvertisingReceiver.value = true
                Log.i(TAG, "Registered OmniOS Receiver NSD: ${serviceInfo.serviceName} :$port")
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                _isAdvertisingReceiver.value = false
                Log.w(TAG, "Receiver NSD registration failed: $errorCode")
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                registeredReceiverServiceName = null
                _isAdvertisingReceiver.value = false
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                _isAdvertisingReceiver.value = false
            }
        }

        receiverRegListener = listener
        try {
            manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register Receiver NSD: ${e.message}")
            receiverRegListener = null
        }
    }

    fun unregisterReceiverService() {
        val listener = receiverRegListener ?: return
        receiverRegListener = null
        registeredReceiverServiceName = null
        registeredReceiverPort = -1
        _isAdvertisingReceiver.value = false
        try {
            nsdManager?.unregisterService(listener)
        } catch (_: Exception) {}
    }

    fun startDiscoveringHosts(context: Context) {
        if (hostBrowseListener != null) return
        val manager = ensureNsd(context) ?: return
        acquireMulticastLock(context)
        val listener = createDiscoveryListener(OmniOSPeerRole.HOST)
        hostBrowseListener = listener
        try {
            manager.discoverServices(SERVICE_TYPE_HOST, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start host NSD scan: ${e.message}")
            hostBrowseListener = null
            _isScanningHosts.value = false
        }
    }

    fun stopDiscoveringHosts() {
        val listener = hostBrowseListener ?: return
        hostBrowseListener = null
        _isScanningHosts.value = false
        try {
            nsdManager?.stopServiceDiscovery(listener)
        } catch (_: Exception) {}
        releaseMulticastLockIfIdle()
    }

    fun startDiscoveringReceivers(context: Context) {
        if (receiverBrowseListener != null) return
        val manager = ensureNsd(context) ?: return
        acquireMulticastLock(context)
        val listener = createDiscoveryListener(OmniOSPeerRole.RECEIVER)
        receiverBrowseListener = listener
        try {
            manager.discoverServices(SERVICE_TYPE_RECEIVER, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start receiver NSD scan: ${e.message}")
            receiverBrowseListener = null
            _isScanningReceivers.value = false
        }
    }

    fun stopDiscoveringReceivers() {
        val listener = receiverBrowseListener ?: return
        receiverBrowseListener = null
        _isScanningReceivers.value = false
        try {
            nsdManager?.stopServiceDiscovery(listener)
        } catch (_: Exception) {}
        releaseMulticastLockIfIdle()
    }

    fun refreshDiscovery(context: Context, scanHosts: Boolean, scanReceivers: Boolean) {
        if (scanHosts) {
            stopDiscoveringHosts()
            _discoveredHosts.value = emptyList()
            startDiscoveringHosts(context)
        }
        if (scanReceivers) {
            stopDiscoveringReceivers()
            _discoveredReceivers.value = emptyList()
            startDiscoveringReceivers(context)
        }
    }

    fun stopAllDiscovery() {
        stopDiscoveringHosts()
        stopDiscoveringReceivers()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            synchronized(activeInfoCallbacks) {
                for (cb in activeInfoCallbacks) {
                    try {
                        @Suppress("UNCHECKED_CAST")
                        nsdManager?.unregisterServiceInfoCallback(cb as NsdManager.ServiceInfoCallback)
                    } catch (_: Exception) {}
                }
                activeInfoCallbacks.clear()
            }
        }
    }

    private fun createDiscoveryListener(role: OmniOSPeerRole): NsdManager.DiscoveryListener {
        return object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                if (role == OmniOSPeerRole.HOST) _isScanningHosts.value = true
                else _isScanningReceivers.value = true
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                val name = serviceInfo.serviceName ?: return
                if (role == OmniOSPeerRole.HOST && name == registeredHostServiceName) return
                if (role == OmniOSPeerRole.RECEIVER && name == registeredReceiverServiceName) return
                resolvePeer(serviceInfo, role)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                val lostName = serviceInfo.serviceName ?: return
                if (role == OmniOSPeerRole.HOST) {
                    _discoveredHosts.update { list -> list.filterNot { it.serviceName == lostName } }
                } else {
                    _discoveredReceivers.update { list -> list.filterNot { it.serviceName == lostName } }
                }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                if (role == OmniOSPeerRole.HOST) _isScanningHosts.value = false
                else _isScanningReceivers.value = false
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "NSD start discovery failed ($serviceType): $errorCode")
                if (role == OmniOSPeerRole.HOST) {
                    hostBrowseListener = null
                    _isScanningHosts.value = false
                } else {
                    receiverBrowseListener = null
                    _isScanningReceivers.value = false
                }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                if (role == OmniOSPeerRole.HOST) _isScanningHosts.value = false
                else _isScanningReceivers.value = false
            }
        }
    }

    private fun resolvePeer(serviceInfo: NsdServiceInfo, role: OmniOSPeerRole) {
        val manager = nsdManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                val callback = object : NsdManager.ServiceInfoCallback {
                    override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {}

                    override fun onServiceUpdated(updatedInfo: NsdServiceInfo) {
                        val ipv4 = updatedInfo.hostAddresses.firstOrNull { it is Inet4Address }?.hostAddress
                            ?: updatedInfo.hostAddresses.firstOrNull()?.hostAddress
                            ?: return
                        recordResolvedPeer(updatedInfo, ipv4, updatedInfo.port, role)
                        try {
                            manager.unregisterServiceInfoCallback(this)
                        } catch (_: Exception) {}
                        synchronized(activeInfoCallbacks) {
                            activeInfoCallbacks.remove(this)
                        }
                    }

                    override fun onServiceLost() {}
                    override fun onServiceInfoCallbackUnregistered() {}
                }
                synchronized(activeInfoCallbacks) {
                    activeInfoCallbacks.add(callback)
                }
                manager.registerServiceInfoCallback(serviceInfo, resolveExecutor, callback)
            } catch (e: Exception) {
                Log.w(TAG, "API 34+ NSD resolve error: ${e.message}")
            }
        } else {
            legacyResolveQueue.add(serviceInfo to role)
            drainLegacyResolveQueue()
        }
    }

    @Suppress("DEPRECATION")
    private fun drainLegacyResolveQueue() {
        if (!isLegacyResolving.compareAndSet(false, true)) return
        val next = legacyResolveQueue.poll()
        if (next == null) {
            isLegacyResolving.set(false)
            return
        }
        val (serviceInfo, role) = next
        val manager = nsdManager
        if (manager == null) {
            isLegacyResolving.set(false)
            return
        }
        try {
            manager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                    isLegacyResolving.set(false)
                    drainLegacyResolveQueue()
                }

                override fun onServiceResolved(resolved: NsdServiceInfo) {
                    val hostIp = resolved.host?.hostAddress
                    if (!hostIp.isNullOrBlank()) {
                        recordResolvedPeer(resolved, hostIp, resolved.port, role)
                    }
                    isLegacyResolving.set(false)
                    drainLegacyResolveQueue()
                }
            })
        } catch (_: Exception) {
            isLegacyResolving.set(false)
            drainLegacyResolveQueue()
        }
    }

    private fun recordResolvedPeer(
        resolved: NsdServiceInfo,
        ip: String,
        port: Int,
        role: OmniOSPeerRole
    ) {
        if (port <= 0) return
        val localIp = ServerManager.resolveLocalIpAddress()
        if (ip == localIp && ((role == OmniOSPeerRole.HOST && port == registeredHostPort) ||
                (role == OmniOSPeerRole.RECEIVER && port == registeredReceiverPort))
        ) {
            return
        }

        val attrDevice = try {
            resolved.attributes?.get("device")?.decodeToString()?.trim()
        } catch (_: Exception) {
            null
        }
        val cleanDisplayName = attrDevice?.takeIf { it.isNotBlank() }
            ?: resolved.serviceName
                .removePrefix("OmniOS-RX-")
                .removePrefix("OmniOS-")
                .trim()
                .ifBlank { ip }

        val peer = DiscoveredOmniOSPeer(
            serviceName = resolved.serviceName,
            deviceName = cleanDisplayName,
            ip = ip,
            port = port,
            role = role
        )

        val targetFlow = if (role == OmniOSPeerRole.HOST) _discoveredHosts else _discoveredReceivers
        targetFlow.update { current ->
            val filtered = current.filterNot { it.ip == peer.ip && it.port == peer.port }
            (filtered + peer).sortedBy { it.deviceName.lowercase() }
        }
    }
}
