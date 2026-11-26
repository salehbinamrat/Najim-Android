package com.najim.android.network

import java.net.InetAddress
import org.junit.Assert.assertNull
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
    @Test fun defaultRouteGatewayAccepted() = assertEquals("10.0.3.1", usableIpv4Gateway(true, InetAddress.getByName("10.0.3.1")))
    @Test fun nonDefaultRouteRejected() = assertNull(usableIpv4Gateway(false, InetAddress.getByName("10.0.3.1")))
    @Test fun absentGatewayRejected() = assertNull(usableIpv4Gateway(true, null))
    @Test fun unspecifiedGatewayRejected() = assertNull(usableIpv4Gateway(true, InetAddress.getByName("0.0.0.0")))
    @Test fun ipv6GatewayRejected() = assertNull(usableIpv4Gateway(true, InetAddress.getByName("2001:db8::1")))
    @Test fun loopbackRejected() = assertNull(usableIpv4Gateway(true, InetAddress.getByName("127.0.0.1")))
    @Test fun multicastRejected() = assertNull(usableIpv4Gateway(true, InetAddress.getByName("224.0.0.1")))
    @Test fun broadcastRejected() = assertNull(usableIpv4Gateway(true, InetAddress.getByName("255.255.255.255")))
}
