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
internal class AndroidGatewayTransport(context: Context,
    private val diagnostic: (DiscoveryState.Ready, GatewayDiagnostic, () -> Boolean) -> Unit = { _, _, _ -> }
) {
    private val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    private fun resolve(selected: DiscoveryState.Ready) = try {
        manager?.let { WifiNetworkResolver.resolve(it, selected) }
    } catch (_: RuntimeException) {
        null
    }

    /** Protocol and consent are scoped to this invocation; do not persist or infer consent. */
    fun login(
        selected: DiscoveryState.Ready, username: String, password: String?,
        protocol: LoginProtocol = LoginProtocol.HTTPS_PAP, allowInsecureHttp: Boolean = false,
        cancelled: () -> Boolean = { false }
    ): GatewayLoginResult {
        check(Looper.myLooper() != Looper.getMainLooper()) { "Network I/O requires a worker thread" }
        if (protocol.requiresConsent && !allowInsecureHttp) {
            return GatewayLoginResult.INSECURE_HTTP_NOT_APPROVED
        }
        val transport = createTransport(protocol, allowInsecureHttp, cancelled)
        val current = { if (!cancelled() && resolve(selected) != null) selected else DiscoveryState.Unavailable }
        // Probe the known callback contract first, without credentials; fallback only
        // before a POST and only to the explicitly selected protocol.
        val callback = NajmCallbackFlow(transport::readCallbackStatus, transport::submitCallback,
            current, protocol, allowInsecureHttp)
        callback.login(selected, username, password)?.let { return it }
        return GatewayLoginFlow(
            read = transport::read,
            submit = transport::submit,
            current = { if (!cancelled() && resolve(selected) != null) selected else DiscoveryState.Unavailable },
            protocol = protocol,
            allowInsecureHttp = allowInsecureHttp
        ).login(selected, username, password)
    }

    fun refreshStatus(selected: DiscoveryState.Ready, protocol: LoginProtocol,
                      allowInsecureHttp: Boolean = false, cancelled: () -> Boolean = { false }): SessionStatus {
        check(Looper.myLooper() != Looper.getMainLooper()) { "Network I/O requires a worker thread" }
        val transport = createTransport(protocol, allowInsecureHttp, cancelled)
        return readStatus(selected, transport,
            { if (!cancelled() && resolve(selected) != null) selected else DiscoveryState.Unavailable },
            protocol, allowInsecureHttp, report = { diagnostic(selected, it, cancelled) })
    }

    fun logout(selected: DiscoveryState.Ready, account: String, legacy: Boolean,
               consent: Boolean, cancelled: () -> Boolean): LogoutResult {
        check(Looper.myLooper() != Looper.getMainLooper()) { "Network I/O requires a worker thread" }
        val protocol = if (legacy) LoginProtocol.HTTP_PAP else LoginProtocol.HTTPS_PAP
        val transport = createTransport(protocol, consent && legacy, cancelled)
        val current = { if (!cancelled() && resolve(selected) != null) selected else DiscoveryState.Unavailable }
        var callback = false
        val status: (DiscoveryState.Ready) -> SessionStatus = { network ->
            readStatus(network, transport, current, protocol, consent && legacy) { callback = it }
        }
        return SessionLogoutFlow(status, { network ->
            if (callback) transport.logoutCallback(network) else transport.logout(network)
        }, current, legacy, consent).logout(selected, account)
    }

    private fun readStatus(selected: DiscoveryState.Ready, transport: GatewayTransport,
                           current: () -> DiscoveryState, protocol: LoginProtocol, consent: Boolean,
                           report: (GatewayDiagnostic) -> Unit = {},
                           callbackUsed: (Boolean) -> Unit = {}): SessionStatus {
        if (current() != selected) {
            val vpn = runCatching { manager?.let { WifiNetworkResolver.capture(it).vpnActive } == true }.getOrDefault(false)
            report(GatewayDiagnostic(protocol, if (vpn) GatewayDiagnostic.Kind.VPN else GatewayDiagnostic.Kind.NETWORK_CHANGED))
            return SessionStatus.NetworkChanged
        }
        if (protocol.requiresConsent && !consent) return SessionStatus.ConsentRequired
        val reply = transport.readCallbackStatus(selected)
        val parsed = NajmCallback.status(reply, protocol.requiresConsent)
        if (current() != selected || reply == ExchangeResult.NetworkChanged) {
            report(GatewayDiagnostic(protocol, GatewayDiagnostic.Kind.NETWORK_CHANGED))
            return SessionStatus.NetworkChanged
        }
        val fallback = parsed == null && NajmCallback.allowsTraditionalFallback(reply)
        report(GatewayDiagnostic.from(protocol, reply, parsed, fallback))
        callbackUsed(parsed != null)
        if (parsed != null) return parsed
        if (fallback)
            return SessionStatusFlow(transport::read, current, protocol, consent).refresh(selected)
        return if (reply is ExchangeResult.Received) SessionStatus.Unknown else SessionStatus.Unavailable
    }

    private fun createTransport(protocol: LoginProtocol, allowInsecureHttp: Boolean,
                                cancelled: () -> Boolean): GatewayTransport =
        GatewayTransport(
            stillSelected = { !cancelled() && resolve(it) != null },
            openBound = { expected, endpoint ->
                val network = if (cancelled()) null else resolve(expected)
                // No DNS fallback, no proxy, no custom trust manager or hostname verifier.
                val connection = network?.openConnection(endpoint.toURL(), Proxy.NO_PROXY)
                when (protocol) {
                    LoginProtocol.HTTPS_PAP -> connection as? HttpsURLConnection
                    LoginProtocol.HTTP_PAP, LoginProtocol.HTTP_CHAP ->
                        (connection as? HttpURLConnection)?.takeUnless { it is HttpsURLConnection }
                }
            },
            protocol = protocol,
            allowInsecureHttp = allowInsecureHttp,
            cancelled = cancelled
        )
}
