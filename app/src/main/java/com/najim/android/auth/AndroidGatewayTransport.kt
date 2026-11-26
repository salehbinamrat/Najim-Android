package com.najim.android.auth

import android.content.Context
import android.net.ConnectivityManager
import android.os.Looper
import com.najim.android.network.DiscoveryState
import com.najim.android.network.WifiNetworkResolver
import java.net.HttpURLConnection
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

    /** Protocol and consent are scoped to this invocation; do not persist or infer consent. */
    fun login(
        selected: DiscoveryState.Ready, username: String, password: String?,
        protocol: LoginProtocol = LoginProtocol.HTTPS_PAP, allowInsecureHttp: Boolean = false
    ): GatewayLoginResult {
        check(Looper.myLooper() != Looper.getMainLooper()) { "Network I/O requires a worker thread" }
        if (protocol.requiresConsent && !allowInsecureHttp) {
            return GatewayLoginResult.INSECURE_HTTP_NOT_APPROVED
        }
        val transport = GatewayTransport(
            stillSelected = { resolve(it) != null },
            openBound = { expected, endpoint ->
                val network = resolve(expected)
                // No DNS fallback, no proxy, no custom trust manager or hostname verifier.
                val connection = network?.openConnection(endpoint.toURL(), Proxy.NO_PROXY)
                when (protocol) {
                    LoginProtocol.HTTPS_PAP -> connection as? HttpsURLConnection
                    LoginProtocol.HTTP_PAP, LoginProtocol.HTTP_CHAP ->
                        (connection as? HttpURLConnection)?.takeUnless { it is HttpsURLConnection }
                }
            },
            protocol = protocol,
            allowInsecureHttp = allowInsecureHttp
        )
        return GatewayLoginFlow(
            read = transport::read,
            submit = transport::submit,
            current = { if (resolve(selected) != null) selected else DiscoveryState.Unavailable },
            protocol = protocol,
            allowInsecureHttp = allowInsecureHttp
        ).login(selected, username, password)
    }
}
