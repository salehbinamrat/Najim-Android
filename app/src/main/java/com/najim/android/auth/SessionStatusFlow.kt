package com.najim.android.auth

import com.najim.android.network.DiscoveryState
import java.net.URI

internal sealed interface SessionStatus {
    data object NetworkChanged : SessionStatus
    data object ConsentRequired : SessionStatus
    data object Unavailable : SessionStatus
    data object Unknown : SessionStatus
    data object LoggedOut : SessionStatus
    class Active(val account: PortalPage.AccountStatus, val legacy: Boolean) : SessionStatus {
        override fun toString() = "SessionStatus.Active(redacted)"
    }
}

/** Read only. Never triggers a login, external probe, logout or redirect. */
internal class SessionStatusFlow(
    private val read: (DiscoveryState.Ready, GatewayPage) -> ExchangeResult,
    private val current: () -> DiscoveryState,
    private val protocol: LoginProtocol = LoginProtocol.HTTPS_PAP,
    private val allowInsecureHttp: Boolean = false
) {
    fun refresh(selected: DiscoveryState.Ready): SessionStatus {
        if (current() != selected) return SessionStatus.NetworkChanged
        if (protocol.requiresConsent && !allowInsecureHttp) return SessionStatus.ConsentRequired
        if (!LoginRequest.isGateway(selected.gateway)) return SessionStatus.Unknown
        val response = read(selected, GatewayPage.STATUS)
        if (current() != selected || response == ExchangeResult.NetworkChanged) return SessionStatus.NetworkChanged
        if (response !is ExchangeResult.Received) return SessionStatus.Unavailable
        val origin = URI(protocol.scheme, null, selected.gateway, -1, "/", null, null)
        val page = PortalParser.parse(origin, origin.resolve("/status"), response.statusCode, response.body)
        val loggedOut = page !is PortalPage.AccountStatus && SessionRedirect.loggedOut(origin, response)
        if (current() != selected) return SessionStatus.NetworkChanged
        return when {
            page is PortalPage.AccountStatus -> SessionStatus.Active(page, protocol.requiresConsent)
            loggedOut -> SessionStatus.LoggedOut
            else -> SessionStatus.Unknown
        }
    }
}

internal object SessionRedirect {
    fun loggedOut(origin: URI, result: ExchangeResult.Received): Boolean {
        if (result.statusCode == 200) {
            // RouterOS can return fstatus.html or an HTML redirect instead of 302.
            // Recognize only an explicit immediate local login redirect or a
            // supported login form. Never navigate or execute page scripts.
            if (result.body.isEmpty() || result.body.size > GatewayTransport.MAX_RESPONSE_BYTES) return false
            val doc = org.jsoup.Jsoup.parse(result.body.toString(Charsets.UTF_8), origin.resolve("/status").toString())
            if (doc.select("base").isNotEmpty()) return false
            val refresh = doc.select("meta[http-equiv=refresh]")
            if (refresh.isNotEmpty()) {
                if (refresh.size != 1 || doc.select("form, script, input").isNotEmpty()) return false
                if (doc.select("a[href]").any { !localLogin(origin, it.attr("href")) }) return false
                val match = Regex("""0\s*;\s*url\s*=\s*(.+)""", RegexOption.IGNORE_CASE)
                    .matchEntire(refresh.single().attr("content").trim()) ?: return false
                return localLogin(origin, match.groupValues[1].trim().removeSurrounding("'").removeSurrounding("\""))
            }
            if (doc.select("a[href], form[action]").any {
                    val attr = if (it.tagName() == "a") "href" else "action"
                    it.attr(attr).substringBefore('?').trimEnd('/').endsWith("/logout", ignoreCase = true)
                }) return false
            val form = PortalParser.parse(origin, origin.resolve("/login"), 200, result.body)
            return form == PortalPage.LoginForm || form is PortalPage.ChapForm
        }
        if (result.statusCode !in setOf(301, 302, 303, 307, 308)) return false
        return localLogin(origin, result.location ?: return false)
    }

    private fun localLogin(origin: URI, raw: String): Boolean {
        if (raw.isBlank() || raw.any { it.isISOControl() || it == '\\' }) return false
        val destination = try { origin.resolve(URI(raw)) } catch (_: java.net.URISyntaxException) {
            return false
        } catch (_: IllegalArgumentException) { return false }
        // Query is evidence only, never used as a destination or forwarded.
        return destination.scheme == origin.scheme && destination.host == origin.host &&
            destination.port == -1 && destination.rawUserInfo == null &&
            destination.rawPath == "/login" && destination.rawFragment == null
    }
}
