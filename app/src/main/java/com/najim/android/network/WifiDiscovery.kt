package com.najim.android.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import java.net.Inet4Address

/** Read-only route discovery. Does not perform requests or bind the process. Main-thread lifecycle. */
class WifiDiscovery(context: Context, private val publish: (DiscoveryState) -> Unit) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var started = false
    private var wifiRegistered = false
    private var defaultRegistered = false
    private val refreshTask = Runnable { if (started) refresh() }

    private fun scheduleRefresh() {
        handler.removeCallbacks(refreshTask)
        handler.post(refreshTask)
    }

    private fun listener() = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = scheduleRefresh()
        override fun onLost(network: Network) = scheduleRefresh()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = scheduleRefresh()
        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) = scheduleRefresh()
    }

    private val wifiListener = listener()
    private val defaultListener = listener()

    fun start() {
        if (started) return
        started = true
        val cm = manager
        if (cm != null) {
            try {
                val request = NetworkRequest.Builder().clearCapabilities()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
                cm.registerNetworkCallback(request, wifiListener)
                wifiRegistered = true
                cm.registerDefaultNetworkCallback(defaultListener)
                defaultRegistered = true
            } catch (_: RuntimeException) {
                // Manual refresh remains available if monitoring cannot be registered.
            }
        }
        refresh()
    }

    fun stop() {
        started = false
        handler.removeCallbacks(refreshTask)
        if (wifiRegistered) unregister(wifiListener)
        if (defaultRegistered) unregister(defaultListener)
        wifiRegistered = false
        defaultRegistered = false
        publish(DiscoveryState.Unavailable)
    }

    private fun unregister(callback: ConnectivityManager.NetworkCallback) {
        try {
            manager?.unregisterNetworkCallback(callback)
        } catch (_: IllegalArgumentException) {
            // Already unregistered.
        }
    }

    fun refresh() {
        if (!started) return
        val cm = manager
        if (cm == null) {
            publish(DiscoveryState.Unavailable)
            return
        }
        val state = try {
            snapshot(cm)
        } catch (_: RuntimeException) {
            DiscoveryState.Unavailable
        }
        publish(state)
    }

    @Suppress("DEPRECATION") // API 24: include Wi-Fi even when cellular is the default network.
    private fun snapshot(cm: ConnectivityManager): DiscoveryState {
        val activeId = cm.activeNetwork?.networkHandle
        val links = cm.allNetworks.mapNotNull { network ->
            val caps = cm.getNetworkCapabilities(network) ?: return@mapNotNull null
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return@mapNotNull null
            val gateways = cm.getLinkProperties(network)?.routes.orEmpty()
                .filter { it.isDefaultRoute && it.hasGateway() }
                .mapNotNull { route ->
                    (route.gateway as? Inet4Address)?.takeUnless {
                        it.isAnyLocalAddress || it.isLoopbackAddress || it.isMulticastAddress
                    }?.hostAddress
                }
            WifiLink(network.networkHandle, gateways)
        }
        return WifiSelector.select(links, activeId)
    }
}
