package com.najim.android.auth

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI

/** Evidence from one bounded portal document. It is not proof of server identity. */
internal sealed interface PortalPage {
    data object Unknown : PortalPage
    data object LoginForm : PortalPage
    class ChapForm(val challenge: ChapChallenge) : PortalPage {
        override fun toString() = "ChapForm(redacted)"
    }
    class AccountStatus(val username: String, val metrics: SessionMetrics = SessionMetrics()) : PortalPage {
        override fun toString() = "AccountStatus(redacted)"
    }
}

/** Offline HTML parsing only. Never executes scripts, fetches resources, or follows URLs. */
internal object PortalParser {
    private val chapExpression = Regex(
        """hexMD5\s*\(\s*'((?:\\[0-7]{3})+)'\s*\+\s*document\.login\.password\.value\s*\+\s*'((?:\\[0-7]{3})+)'\s*\)"""
    )
    private val octalByte = Regex("""\\([0-7]{3})""")
    private val welcome = Regex("""Welcome,?\s+(.+)!""")

    fun parse(gateway: URI, page: URI, code: Int, html: ByteArray): PortalPage {
        if (code != 200 || html.isEmpty() || html.size > GatewayTransport.MAX_RESPONSE_BYTES) {
            return PortalPage.Unknown
        }
        if (!sameOrigin(gateway, page) || page.rawQuery != null ||
            page.rawFragment != null || page.rawPath !in setOf("/login", "/status")) {
            return PortalPage.Unknown
        }
        val doc = Jsoup.parse(html.toString(Charsets.UTF_8), page.toString())
        if (doc.select("base").isNotEmpty()) return PortalPage.Unknown
        val refresh = doc.select("meta[http-equiv=refresh]")
        // Numeric refresh intervals are inert; URL redirects are rejected.
        if (refresh.isNotEmpty() && (page.rawPath != "/status" ||
                refresh.any { !it.attr("content").trim().matches(Regex("[0-9]{1,9}")) })) return PortalPage.Unknown
        return when (page.rawPath) {
            "/login" -> parseLogin(doc, gateway, page)
            "/status" -> parseStatus(doc, gateway, page)
            else -> PortalPage.Unknown
        }
    }

    private fun parseLogin(doc: Document, gateway: URI, page: URI): PortalPage {
        val forms = doc.select("form").toList()
        val login = forms.filter { it.attr("name") == "login" }.singleOrNull()
            ?: return PortalPage.Unknown
        if (!validForm(login, gateway, page) || !StatusFields.visible(login) ||
            login.select("input[name=username]").size != 1 ||
            !usableUsername(login.select("input[name=username]").single()) ||
            login.select("input[name=password]").size > 1) {
            return PortalPage.Unknown
        }
        // Form overrides and disabled credentials are not supported. Never infer a usable
        // form from hidden templates or inputs assigned to a different form.
        if (doc.select("[formaction], input[form], button[form]").isNotEmpty() ||
            login.select("input[name=password]").any { it.hasAttr("disabled") }) return PortalPage.Unknown
        val scripts = doc.select("script").joinToString("\n") { it.data() }
        val sendin = forms.filter { it.attr("name") == "sendin" }
        val chapMarked = sendin.isNotEmpty() || scripts.contains("hexMD5", ignoreCase = true) ||
            login.attr("onsubmit").contains("doLogin", ignoreCase = true) ||
            doc.select("script[src]").any { it.attr("src").substringBefore('?').endsWith("md5.js", ignoreCase = true) }
        if (!chapMarked) {
            if (forms.size != 1 || login.hasAttr("onsubmit") ||
                doc.select("script, [onclick], [onload], [onchange], [oninput]").isNotEmpty()) {
                return PortalPage.Unknown
            }
            return PortalPage.LoginForm
        }
        val hidden = sendin.singleOrNull() ?: return PortalPage.Unknown
        if (forms.size != 2 || !validForm(hidden, gateway, page) ||
            hidden.select("input[type=hidden][name=username]").size != 1 ||
            hidden.select("input[type=hidden][name=password]").size != 1 ||
            login.select("input[name=password]").size != 1 ||
            login.attr("onsubmit").trim().trimEnd(';') != "return doLogin()" ||
            scripts.contains("eval(")) return PortalPage.Unknown

        val match = chapExpression.findAll(scripts).toList().singleOrNull() ?: return PortalPage.Unknown
        val id = decodeOctal(match.groupValues[1], 1) ?: return PortalPage.Unknown
        val challenge = decodeOctal(match.groupValues[2], 16) ?: return PortalPage.Unknown
        return PortalPage.ChapForm(ChapChallenge(id.single().toInt() and 255, challenge))
    }

    private fun parseStatus(doc: Document, gateway: URI, page: URI): PortalPage {
        if (doc.select("input[name=username], input[name=password]").isNotEmpty()) return PortalPage.Unknown
        val logout = doc.select("a[href], form[action]").filter(StatusFields::visible).any {
            val attribute = if (it.tagName() == "a") "href" else "action"
            localPath(it.attr(attribute), gateway, page, "/logout")
        }
        if (!logout) return PortalPage.Unknown
        val headings = doc.select("h1").filter(StatusFields::visibleEvidence)
            .mapNotNull { welcome.matchEntire(it.text())?.groupValues?.get(1) }
        val rows = StatusFields.rows(doc).filter { it.first in setOf("user", "username", "اسم المستخدم") }
            .map { it.second }
        val evidence = (headings + rows).distinct().singleOrNull() ?: return PortalPage.Unknown
        if (com.najim.android.domain.VoucherInput.validate(evidence).error != null ||
            com.najim.android.domain.VoucherInput.normalize(evidence) != evidence) {
            return PortalPage.Unknown
        }
        return PortalPage.AccountStatus(evidence, StatusFields.parse(doc))
    }

    internal fun usableUsername(input: Element): Boolean =
        StatusFields.visible(input) && !input.hasAttr("disabled") &&
            input.attr("type").lowercase() in setOf("", "text", "tel", "number", "email", "password", "search")

    internal fun validForm(form: Element, gateway: URI, page: URI) =
        form.attr("method").equals("post", ignoreCase = true) &&
            localPath(form.attr("action"), gateway, page, "/login")

    private fun localPath(raw: String, gateway: URI, page: URI, path: String): Boolean {
        if (raw.isBlank() || raw.any { it.isISOControl() || it == '\\' }) return false
        val uri = try { page.resolve(URI(raw)) } catch (_: IllegalArgumentException) {
            return false
        } catch (_: java.net.URISyntaxException) { return false }
        return sameOrigin(gateway, uri) && uri.rawPath == path &&
            uri.rawQuery == null && uri.rawFragment == null
    }

    private fun sameOrigin(gateway: URI, other: URI): Boolean =
        gateway.scheme in setOf("http", "https") && other.scheme == gateway.scheme &&
            gateway.host != null && other.host == gateway.host &&
            gateway.port == -1 && other.port == -1 &&
            gateway.rawUserInfo == null && other.rawUserInfo == null

    private fun decodeOctal(raw: String, size: Int): ByteArray? {
        if (raw.length != size * 4) return null
        val bytes = octalByte.findAll(raw).map { it.groupValues[1].toInt(8) }.toList()
        if (bytes.size != size || bytes.any { it !in 0..255 }) return null
        return bytes.map { it.toByte() }.toByteArray()
    }
}
