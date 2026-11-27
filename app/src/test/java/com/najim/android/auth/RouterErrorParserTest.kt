package com.najim.android.auth

import java.net.URI
import org.junit.Assert.*
import org.junit.Test

class RouterErrorParserTest {
    private val origin = URI("https://10.0.3.1/")
    private val form = """<form name="login" action="/login" method="post"><input name="username"><input name="password"></form>"""
    private fun parse(html: String, code: Int = 200, gateway: URI = origin) =
        RouterErrorParser.parse(gateway, ExchangeResult.Received(code, html.toByteArray(Charsets.UTF_8)))
    private fun error(message: String, code: Int = 200) = parse(form + """<p class="info alert">$message</p>""", code)

    @Test fun userNotFound() {
        assertEquals(GatewayLoginResult.ROUTER_USER_NOT_FOUND, error("user not found"))
        assertEquals(GatewayLoginResult.ROUTER_USER_NOT_FOUND, error("رقم الكرت أو اسم المستخدم غير موجود"))
    }

    @Test fun invalidPassword() {
        assertEquals(GatewayLoginResult.ROUTER_INVALID_PASSWORD, error("invalid password"))
        assertEquals(GatewayLoginResult.ROUTER_INVALID_PASSWORD, error("كلمة المرور غير صحيحة"))
    }

    @Test fun invalidCredentials() {
        assertEquals(GatewayLoginResult.ROUTER_INVALID_CREDENTIALS, error("invalid username or password"))
        assertEquals(GatewayLoginResult.ROUTER_INVALID_CREDENTIALS, error("اسم المستخدم أو كلمة المرور غير صحيحة"))
    }

    @Test fun dataLimit() {
        assertEquals(GatewayLoginResult.ROUTER_DATA_LIMIT, error("transfer limit reached"))
        assertEquals(GatewayLoginResult.ROUTER_DATA_LIMIT, error("وصل الكرت إلى حد البيانات المسموح به"))
    }

    @Test fun timeLimit() {
        assertEquals(GatewayLoginResult.ROUTER_TIME_LIMIT, error("no more online time"))
        assertEquals(GatewayLoginResult.ROUTER_TIME_LIMIT, error("وصل الكرت إلى حد وقت الاستخدام"))
    }

    @Test fun accountExpired() {
        assertEquals(GatewayLoginResult.ROUTER_ACCOUNT_EXPIRED, error("account expired"))
        assertEquals(GatewayLoginResult.ROUTER_ACCOUNT_EXPIRED, error("انتهت صلاحية الحساب"))
    }

    @Test fun sessionLimit() {
        assertEquals(GatewayLoginResult.ROUTER_SESSION_LIMIT, error("no more sessions"))
        assertEquals(GatewayLoginResult.ROUTER_SESSION_LIMIT, error("الكرت مستخدم في جهاز آخر أو بلغ حد الجلسات المسموح"))
    }

    @Test fun alreadyLoggedIn() {
        assertEquals(GatewayLoginResult.ROUTER_ALREADY_LOGGED_IN, error("already logged in"))
        assertEquals(GatewayLoginResult.ROUTER_ALREADY_LOGGED_IN, error("الكرت مستخدم حاليا"))
    }

    @Test fun deviceRestricted() {
        assertEquals(GatewayLoginResult.ROUTER_DEVICE_RESTRICTED, error("invalid calling-station-id"))
        assertEquals(GatewayLoginResult.ROUTER_DEVICE_RESTRICTED, error("الجهاز مرفوض بسبب قيد على عنوان الجهاز"))
    }

    @Test fun radiusTimeout() {
        assertEquals(GatewayLoginResult.ROUTER_RADIUS_TIMEOUT, error("radius timeout"))
        assertEquals(GatewayLoginResult.ROUTER_RADIUS_TIMEOUT, error("الحساب لم يستجب، ولا يعني ذلك انتهاء الكرت"))
    }

    @Test fun noValidProfile() {
        assertEquals(GatewayLoginResult.ROUTER_NO_VALID_PROFILE, error("no valid profile found"))
        assertEquals(GatewayLoginResult.ROUTER_NO_VALID_PROFILE, error("لا توجد باقة صالحة حاليا"))
    }

    @Test fun whitespaceAndCaseAreNormalized() =
        assertEquals(GatewayLoginResult.ROUTER_RADIUS_TIMEOUT, error("  RADIUS   TIMEOUT. "))

    @Test fun htmlEntitiesAreDecoded() =
        assertEquals(GatewayLoginResult.ROUTER_INVALID_PASSWORD, error("invalid&nbsp;password"))

    @Test fun knownErrorCanBeReturnedInExplicit403Page() =
        assertEquals(GatewayLoginResult.ROUTER_DEVICE_RESTRICTED, error("invalid calling-station-id", 403))

    @Test fun genericInvalidIsNotWrongPassword() =
        assertEquals(GatewayLoginResult.REQUEST_REJECTED, error("invalid"))

    @Test fun conflictingSpecificErrorsAreNeutral() =
        assertEquals(GatewayLoginResult.REQUEST_REJECTED, parse(form + "<p class='error'>account expired</p><p class='error'>radius timeout</p>"))

    @Test fun knownAndUnknownErrorsAreNeutral() =
        assertEquals(GatewayLoginResult.REQUEST_REJECTED, parse(form + "<p class='error'>account expired</p><p class='error'>custom error</p>"))

    @Test fun rawTemplateIsNotAnExpiryMessage() =
        assertEquals(GatewayLoginResult.REQUEST_REJECTED, error("\$(error)"))

    @Test fun incidentalPageWordsIgnored() =
        assertNull(parse(form + "<p>account expired</p><script>var error='radius timeout';</script>"))

    @Test fun hiddenErrorMarkersIgnored() {
        for (element in listOf(
            "<p class='error' hidden>account expired</p>",
            "<div aria-hidden='true'><p class='error'>account expired</p></div>",
            "<div style='display: none'><p class='error'>account expired</p></div>",
            "<template><p class='error'>account expired</p></template>")) {
            assertNull(parse(form + element))
        }
    }

    @Test fun untrustedFormAndBaseRejected() {
        assertNull(parse(form.replace("/login", "//evil.test/login") + "<p class='error'>account expired</p>"))
        assertNull(parse("<base href='https://evil.test/'>" + form + "<p class='error'>account expired</p>"))
    }

    @Test fun noFormNoClassification() =
        assertNull(parse("<p class='error'>account expired</p>"))

    @Test fun redirectServerErrorAndBare403AreNotVoucherRejectionEvidence() {
        for (code in listOf(302, 404, 429, 500)) assertNull(error("account expired", code))
        assertNull(parse("Forbidden", 403))
    }

    @Test fun invalidGatewayRejected() =
        assertNull(parse(form + "<p class='error'>account expired</p>", gateway = URI("https://example.test/")))

    @Test fun oversizedResponseRejected() = assertNull(parse(form + "<p class='error'>account expired</p>" + "x".repeat(65536)))
    @Test fun hiddenDescendantsCannotProvideSpecificError() {
        for (marker in listOf("<span hidden>account expired</span>",
            "<span aria-hidden='TRUE'>radius timeout</span>",
            "<span style='visibility: hidden'>invalid password</span>")) {
            assertNull(parse(form + "<p class='error'>" + marker + "</p>"))
        }
    }
    @Test fun mixedVisibleAndConcealedWordsCannotCreateSpecificError() {
        assertNull(parse(form + "<p class='error'>account <span hidden>expired</span></p>"))
    }
}
