package com.najim.android.network

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

/** One shared route-selection policy for discovery and credential transport. */
internal object WifiNetworkResolver {
    class Snapshot(val state: DiscoveryState, val network: Network?, val vpnActive: Boolean)

    @Suppress("DEPRECATION") // allNetworks supports captive Wi-Fi even with cellular default.
    fun capture(cm: ConnectivityManager): Snapshot {
        val active = cm.activeNetwork
        val activeCaps = active?.let { cm.getNetworkCapabilities(it) }
        val initialVpn = activeCaps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        if (initialVpn) return Snapshot(DiscoveryState.VpnBlocked, null, true)
        if (active != null && activeCaps == null)
            return Snapshot(DiscoveryState.Unavailable, null, false)
        val candidates = cm.allNetworks.mapNotNull { network ->
            val caps = cm.getNetworkCapabilities(network) ?: return@mapNotNull null
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return@mapNotNull null
            val gateways = cm.getLinkProperties(network)?.routes.orEmpty()
                .mapNotNull { route -> usableIpv4Gateway(route.isDefaultRoute, route.gateway) }
            network to WifiLink(network.networkHandle, gateways)
        }
        // The default route can change while link properties are being read.
        // Do not publish the mixed snapshot; a subsequent callback can refresh it.
        val activeAfter = cm.activeNetwork
        val capsAfter = activeAfter?.let { cm.getNetworkCapabilities(it) }
        val vpnActive = capsAfter?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        val activeStable = active == activeAfter &&
            (activeAfter == null || capsAfter != null) &&
            (activeCaps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ==
                capsAfter?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
        val state = WifiSelector.select(candidates.map { it.second }, active?.networkHandle,
            vpnActive, activeStable)
        val network = (state as? DiscoveryState.Ready)?.let { ready ->
            candidates.firstOrNull { it.second.id == ready.networkId }?.first
        }
        return Snapshot(state, network, vpnActive)
    }

    fun resolve(cm: ConnectivityManager, expected: DiscoveryState.Ready): Network? {
        val snapshot = capture(cm)
        if (snapshot.vpnActive || snapshot.state != expected) return null
        return snapshot.network
    }
}
