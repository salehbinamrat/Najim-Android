package com.najim.android.network

import java.net.InetAddress
import java.net.Inet4Address

sealed interface DiscoveryState {
    data object NoWifi : DiscoveryState
    data object AmbiguousWifi : DiscoveryState
    data object NoGateway : DiscoveryState
    data object AmbiguousGateway : DiscoveryState
    data object Unavailable : DiscoveryState
    data class Ready(val networkId: Long, val gateway: String) : DiscoveryState
}

internal data class WifiLink(val id: Long, val defaultIpv4Gateways: List<String>)

internal object WifiSelector {
    fun select(links: List<WifiLink>, activeId: Long?): DiscoveryState {
        val wifi = links.firstOrNull { it.id == activeId } ?: when (links.size) {
            0 -> return DiscoveryState.NoWifi
            1 -> links.single()
            else -> return DiscoveryState.AmbiguousWifi
        }
        val gateways = wifi.defaultIpv4Gateways.distinct()
        return when (gateways.size) {
            0 -> DiscoveryState.NoGateway
            1 -> DiscoveryState.Ready(wifi.id, gateways.single())
            else -> DiscoveryState.AmbiguousGateway
        }
    }
}

/** No RouteInfo.hasGateway() dependency; API 21 route getters are sufficient. */
internal fun usableIpv4Gateway(isDefault: Boolean, address: InetAddress?): String? {
    if (!isDefault || address !is Inet4Address) return null
    if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isMulticastAddress) return null
    if (address.address.all { it.toInt() == -1 }) return null
    return address.hostAddress
}
