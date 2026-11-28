package com.najim.android.network

import org.junit.Assert.*
import org.junit.Test

class NetworkActionScopeTest {
    private val wifi = DiscoveryState.Ready(7L, "10.0.0.1")
    private val scope = NetworkActionScope(wifi, 3)

    @Test fun sameRenderedSelectionAllowed() {
        assertTrue(scope.permits(wifi.copy(), 3, true))
    }

    @Test fun sameGatewayDifferentHandleRejected() {
        assertFalse(scope.permits(wifi.copy(networkId = 8L), 3, true))
    }

    @Test fun changedGatewayRejected() {
        assertFalse(scope.permits(wifi.copy(gateway = "10.0.0.2"), 3, true))
    }

    @Test fun returnToSameNetworkDoesNotResurrectOldForm() {
        assertFalse(scope.permits(wifi, 5, true))
        assertTrue(NetworkActionScope(wifi, 5).permits(wifi, 5, true))
    }

    @Test fun stoppedOrUnavailableCannotPerformNetworkAction() {
        assertFalse(scope.permits(wifi, 3, false))
        assertFalse(scope.permits(DiscoveryState.VpnBlocked, 3, true))
        assertFalse(NetworkActionScope(DiscoveryState.NoWifi, 3)
            .permits(DiscoveryState.NoWifi, 3, true))
    }
}
