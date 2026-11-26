package com.najim.android.auth

import android.content.Context
import android.net.ConnectivityManager
import android.os.Looper
import com.najim.android.network.DiscoveryState
import com.najim.android.network.WifiNetworkResolver
import java.net.Proxy
import javax.net.ssl.HttpsURLConnection

/** Uses the exact selected Android Network, not the default process route. API 23+. */
internal class AndroidGatewayTransport(context: Context) {
    private val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    private fun resolve(selected: DiscoveryState.Ready) = try {
        manager?.let { WifiNetworkResolver.resolve(it, selected) }
    } catch (_: RuntimeException) {
        null
    }

    private val transport = GatewayTransport(
        stillSelected = { resolve(it) != null },
        openBound = { selected, endpoint ->
            val network = resolve(selected)
            // No DNS fallback, no proxy, no custom trust manager or hostname verifier.
            val connection = network?.openConnection(endpoint.toURL(), Proxy.NO_PROXY)
            if (connection is HttpsURLConnection) connection else null
        }
    )

    fun submit(selected: DiscoveryState.Ready, request: Preparation.Ready): ExchangeResult {
        check(Looper.myLooper() != Looper.getMainLooper()) { "Network I/O requires a worker thread" }
        return transport.submit(selected, request)
    }
}
