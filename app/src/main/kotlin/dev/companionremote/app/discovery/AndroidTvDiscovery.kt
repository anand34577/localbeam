package dev.companionremote.app.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import kotlin.coroutines.resume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Local mDNS discovery for Android TV Remote Service v2. Different TV
 * firmware generations advertise one of these two service names.
 */
class AndroidTvDiscovery(context: Context) {

    private val appContext = context.applicationContext
    private val nsdManager = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    suspend fun scan(durationMs: Long = 6_000, onDevice: (DiscoveredAtv) -> Unit) {
        val multicastLock = wifiManager.createMulticastLock("cyberremote-androidtv-discovery").apply {
            setReferenceCounted(false)
            acquire()
        }
        try {
            val perServiceDuration = (durationMs / SERVICE_TYPES.size).coerceAtLeast(1_000)
            for (serviceType in SERVICE_TYPES) {
                scanService(serviceType, perServiceDuration, onDevice)
            }
        } finally {
            runCatching { multicastLock.release() }
        }
    }

    suspend fun resolveByName(name: String, timeoutMs: Long = 6_000): DiscoveredAtv? =
        withTimeoutOrNull(timeoutMs) {
            coroutineScope {
                val found = CompletableDeferred<DiscoveredAtv>()
                val scanJob = launch {
                    scan(timeoutMs) { device ->
                        if (device.name == name) found.complete(device)
                    }
                }
                val device = found.await()
                scanJob.cancel()
                device
            }
        }

    @Suppress("DEPRECATION")
    private suspend fun scanService(
        serviceType: String,
        durationMs: Long,
        onDevice: (DiscoveredAtv) -> Unit,
    ) {
        val found = LinkedHashMap<String, NsdServiceInfo>()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                synchronized(found) { found[serviceInfo.serviceName] = serviceInfo }
            }
        }

        try {
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
            val deadline = System.currentTimeMillis() + durationMs
            val resolved = mutableSetOf<String>()
            while (System.currentTimeMillis() < deadline) {
                val pending = synchronized(found) {
                    found.entries.firstOrNull { it.key !in resolved }
                }
                if (pending == null) {
                    delay(150)
                    continue
                }
                resolved.add(pending.key)
                resolve(pending.value)?.let(onDevice)
            }
        } finally {
            runCatching { nsdManager.stopServiceDiscovery(listener) }
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun resolve(info: NsdServiceInfo): DiscoveredAtv? =
        withTimeoutOrNull(5_000) {
            suspendCancellableCoroutine { continuation ->
                nsdManager.resolveService(
                    info,
                    object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            if (continuation.isActive) continuation.resume(null)
                        }

                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            val host = serviceInfo.host?.hostAddress
                            if (host == null) {
                                if (continuation.isActive) continuation.resume(null)
                                return
                            }
                            if (continuation.isActive) {
                                continuation.resume(
                                    DiscoveredAtv(
                                        name = "Android TV · ${serviceInfo.serviceName}",
                                        host = host,
                                        port = 6466,
                                        pairingPort = 6467,
                                        model = serviceInfo.attributes["fn"]?.toString(Charsets.UTF_8),
                                        platform = TvPlatform.AndroidTv,
                                    ),
                                )
                            }
                        }
                    },
                )
            }
        }

    companion object {
        private val SERVICE_TYPES = listOf(
            "_androidtvremote2._tcp.",
            "_androidtvremote._tcp.",
        )
    }
}
