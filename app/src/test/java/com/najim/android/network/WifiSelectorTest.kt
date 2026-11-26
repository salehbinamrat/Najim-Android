package com.najim.android.network

import org.junit.Assert.assertEquals
import org.junit.Test

class WifiSelectorTest {
    private val first = WifiLink(1L, listOf("10.0.3.1"))
    private val second = WifiLink(2L, listOf("192.168.88.1"))

    @Test fun noWifi() = assertEquals(DiscoveryState.NoWifi, WifiSelector.select(emptyList(), 9L))
    @Test fun activeWifiPreferred() = assertEquals(DiscoveryState.Ready(2L, "192.168.88.1"), WifiSelector.select(listOf(first, second), 2L))
    @Test fun cellularDefaultDoesNotHideWifi() = assertEquals(DiscoveryState.Ready(1L, "10.0.3.1"), WifiSelector.select(listOf(first), 9L))
    @Test fun multipleWifiWithoutActiveIsAmbiguous() = assertEquals(DiscoveryState.AmbiguousWifi, WifiSelector.select(listOf(first, second), 9L))
    @Test fun noFixedGatewayFallback() = assertEquals(DiscoveryState.NoGateway, WifiSelector.select(listOf(WifiLink(1L, emptyList())), 1L))
    @Test fun multipleGatewaysAreAmbiguous() = assertEquals(DiscoveryState.AmbiguousGateway, WifiSelector.select(listOf(WifiLink(1L, listOf("10.0.0.1", "10.0.0.2"))), 1L))
    @Test fun duplicateRoutesAreNotAmbiguous() = assertEquals(DiscoveryState.Ready(1L, "10.0.3.1"), WifiSelector.select(listOf(WifiLink(1L, listOf("10.0.3.1", "10.0.3.1"))), 1L))
    @Test fun switchNetworkDoesNotKeepOldGateway() = assertEquals(DiscoveryState.Ready(2L, "192.168.88.1"), WifiSelector.select(listOf(second), 1L))
}
