package com.najim.android.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper

/** Read-only route discovery. Does not perform requests or bind the process. Main-thread lifecycle. */
class WifiDiscovery(context: Context, private val publish: (DiscoveryState) -> Unit) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val gate = DiscoveryRefreshGate()
    private var started = false
    private var wifiListener: ConnectivityManager.NetworkCallback? = null
    private var defaultListener: ConnectivityManager.NetworkCallback? = null

    private fun scheduleRefresh(lifetime: DiscoveryRefreshGate.Lifetime) {
        if (!gate.request(lifetime)) return
        // Do not remove a different callback's queued task. Consume before capture,
        // so a change during capture can schedule exactly one follow-up.
        if (!handler.post {
                if (gate.consume(lifetime) && started) refresh()
            }) gate.consume(lifetime)
    }

    private fun listener(lifetime: DiscoveryRefreshGate.Lifetime) = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = scheduleRefresh(lifetime)
        override fun onLost(network: Network) = scheduleRefresh(lifetime)
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = scheduleRefresh(lifetime)
        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) = scheduleRefresh(lifetime)
    }

    fun start() {
        if (started) return
        started = true
        val lifetime = gate.start()
        val cm = manager
        if (cm != null) {
            // A failed Wi-Fi registration must not prevent default monitoring.
            val wifi = listener(lifetime)
            try {
                val request = NetworkRequest.Builder()
                    // API 21-compatible; do not require INTERNET or VALIDATED.
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
                cm.registerNetworkCallback(request, wifi)
                wifiListener = wifi
            } catch (_: RuntimeException) {
                // Manual refresh and the independent default monitor remain available.
            }
            val default = listener(lifetime)
            try {
                cm.registerDefaultNetworkCallback(default)
                defaultListener = default
            } catch (_: RuntimeException) {
                // Manual refresh and any registered Wi-Fi monitor remain available.
            }
        }
        refresh()
    }

    fun stop() {
        started = false
        // Invalidate before unregistering: callbacks already in flight or queued
        // may still arrive, but cannot acquire the next monitoring lifetime.
        gate.stop()
        val wifi = wifiListener
        val default = defaultListener
        wifiListener = null
        defaultListener = null
        wifi?.let(::unregister)
        default?.let(::unregister)
        publish(DiscoveryState.Unavailable)
    }

    private fun unregister(callback: ConnectivityManager.NetworkCallback) {
        try {
            manager?.unregisterNetworkCallback(callback)
        } catch (_: RuntimeException) {
            // Continue cleanup of the other listener; stale callbacks are gated.
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
            WifiNetworkResolver.capture(cm).state
        } catch (_: RuntimeException) {
            DiscoveryState.Unavailable
        }
        publish(state)
    }
}
