package com.najim.android.auth

import com.najim.android.network.DiscoveryState
import java.net.URI
import org.junit.Assert.*
import org.junit.Test

class PortalParserTest {
    private val gateway = URI("https://10.0.3.1")
    private val login = """<form name="login" action="/login" method="post"><input name="username"><input name="password" type="password"></form>"""
    private fun parse(html: String, path: String = "/login", code: Int = 200) =
        PortalParser.parse(gateway, gateway.resolve(path), code, html.toByteArray(Charsets.UTF_8))
    private fun status(user: String) = """<h1>Welcome, $user!</h1><form action="/logout" method="post"></form>"""
    private fun chap(id: String = "\\200", challenge: String = (0..15).joinToString("") { "\\" + it.toString(8).padStart(3, '0') }) =
        """<form name="sendin" action="/login" method="post"><input type="hidden" name="username"><input type="hidden" name="password"></form>
        <script>function doLogin(){ document.sendin.password.value = hexMD5('$id' + document.login.password.value + '$challenge'); }</script>
        """ + login.replace("method=\"post\"", "method=\"post\" onsubmit=\"return doLogin()\"")

    @Test fun simpleLoginAccepted() = assertEquals(PortalPage.LoginForm, parse(login))
    @Test fun usernameOnlyFormAccepted() = assertEquals(PortalPage.LoginForm,
        parse(login.replace("""<input name="password" type="password">""", "")))
    @Test fun externalActionRejected() = assertEquals(PortalPage.Unknown, parse(login.replace("/login", "https://example.test/login")))
    @Test fun downgradeActionRejected() = assertEquals(PortalPage.Unknown, parse(login.replace("/login", "http://10.0.3.1/login")))
    @Test fun queryActionRejected() = assertEquals(PortalPage.Unknown, parse(login.replace("/login", "/login?next=1")))
    @Test fun baseOverrideRejected() = assertEquals(PortalPage.Unknown, parse("<base href='https://example.test/'>" + login))
    @Test fun duplicateFormsRejected() = assertEquals(PortalPage.Unknown, parse(login + login))
    @Test fun unsupportedScriptDoesNotFallBackToPap() = assertEquals(PortalPage.Unknown, parse(login + "<script>customLogin()</script>"))
    @Test fun redirectAndErrorNotParsed() {
        for (code in listOf(302, 403, 500)) assertEquals(PortalPage.Unknown, parse(login, code = code))
    }
    @Test fun oversizedPageRejected() = assertEquals(PortalPage.Unknown, parse(login + "x".repeat(65536)))
    @Test fun chapChallengeMatchesDigestVector() {
        val result = parse(chap()) as PortalPage.ChapForm
        assertEquals("d7a567cb9ba72549c682a6d9857c9b77", result.challenge.response("1234"))
        assertEquals("ChapForm(redacted)", result.toString())
    }
    @Test fun malformedChapNeverFallsBackToPap() {
        for (html in listOf(chap(id = "\\400"), chap(challenge = "\\001"), chap().replace("hexMD5", "otherHash"))) {
            assertEquals(PortalPage.Unknown, parse(html))
        }
    }
    @Test fun chapExternalActionRejected() = assertEquals(PortalPage.Unknown, parse(chap().replaceFirst("/login", "//example.test/login")))
    @Test fun statusNeedsSameOriginLogoutAndExactIdentity() {
        val result = parse(status("00Ab"), "/status") as PortalPage.AccountStatus
        assertEquals("00Ab", result.username)
        assertEquals("AccountStatus(redacted)", result.toString())
        assertEquals(PortalPage.Unknown, parse("<h1>Welcome, 00Ab!</h1>", "/status"))
        assertEquals(PortalPage.Unknown, parse(status("00Ab").replace("/logout", "//example.test/logout"), "/status"))
    }
    @Test fun statusWithLoginFieldsRejected() = assertEquals(PortalPage.Unknown, parse(status("00Ab") + login, "/status"))
    @Test fun conflictingAccountEvidenceRejected() = assertEquals(PortalPage.Unknown,
        parse(status("00Ab") + "<table><tr><td>Username</td><td>other</td></tr></table>", "/status"))
    @Test fun genericInternetSuccessIsNotStatus() = assertEquals(PortalPage.Unknown, parse("<h1>Success</h1>", "/status"))
    @Test fun wrongSourcePageRejected() = assertEquals(PortalPage.Unknown,
        PortalParser.parse(gateway, URI("https://example.test/login"), 200, login.toByteArray()))
    @Test fun statusAtLoginIsNotAccepted() = assertEquals(PortalPage.Unknown, parse(status("00Ab")))
}
