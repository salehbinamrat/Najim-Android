package com.najim.android.auth

import org.jsoup.Jsoup
import java.net.URI

/**
 * Classifies only explicit messages in a bounded response to the current local login POST.
 * Never returns raw gateway text, guesses from a whole page, or logs credentials.
 */
internal object RouterErrorParser {
    private const val ERROR_MARKERS = ".error, #error, .alert-danger, .info.alert, [role=alert], [data-hotspot-error]"
    private val spaces = Regex("""[\s\u00A0]+""")
    private val messages = mapOf(
        "user not found" to GatewayLoginResult.ROUTER_USER_NOT_FOUND,
        "username not found" to GatewayLoginResult.ROUTER_USER_NOT_FOUND,
        "رقم الكرت أو اسم المستخدم غير موجود" to GatewayLoginResult.ROUTER_USER_NOT_FOUND,
        "رقم الكرت غير موجود" to GatewayLoginResult.ROUTER_USER_NOT_FOUND,
        "invalid password" to GatewayLoginResult.ROUTER_INVALID_PASSWORD,
        "incorrect password" to GatewayLoginResult.ROUTER_INVALID_PASSWORD,
        "كلمة المرور غير صحيحة" to GatewayLoginResult.ROUTER_INVALID_PASSWORD,
        "invalid username or password" to GatewayLoginResult.ROUTER_INVALID_CREDENTIALS,
        "اسم المستخدم أو كلمة المرور غير صحيحة" to GatewayLoginResult.ROUTER_INVALID_CREDENTIALS,
        "transfer limit reached" to GatewayLoginResult.ROUTER_DATA_LIMIT,
        "traffic limit reached" to GatewayLoginResult.ROUTER_DATA_LIMIT,
        "data limit reached" to GatewayLoginResult.ROUTER_DATA_LIMIT,
        "وصل الكرت إلى حد البيانات المسموح به" to GatewayLoginResult.ROUTER_DATA_LIMIT,
        "انتهى رصيد البيانات" to GatewayLoginResult.ROUTER_DATA_LIMIT,
        "no more online time" to GatewayLoginResult.ROUTER_TIME_LIMIT,
        "uptime limit reached" to GatewayLoginResult.ROUTER_TIME_LIMIT,
        "session time limit reached" to GatewayLoginResult.ROUTER_TIME_LIMIT,
        "وصل الكرت إلى حد وقت الاستخدام" to GatewayLoginResult.ROUTER_TIME_LIMIT,
        "account expired" to GatewayLoginResult.ROUTER_ACCOUNT_EXPIRED,
        "credit expired" to GatewayLoginResult.ROUTER_ACCOUNT_EXPIRED,
        "انتهت صلاحية الحساب" to GatewayLoginResult.ROUTER_ACCOUNT_EXPIRED,
        "انتهت صلاحية الكرت" to GatewayLoginResult.ROUTER_ACCOUNT_EXPIRED,
        "no more sessions" to GatewayLoginResult.ROUTER_SESSION_LIMIT,
        "simultaneous session limit reached" to GatewayLoginResult.ROUTER_SESSION_LIMIT,
        "الكرت مستخدم في جهاز آخر أو بلغ حد الجلسات المسموح" to GatewayLoginResult.ROUTER_SESSION_LIMIT,
        "already logged in" to GatewayLoginResult.ROUTER_ALREADY_LOGGED_IN,
        "user already logged in" to GatewayLoginResult.ROUTER_ALREADY_LOGGED_IN,
        "الكرت مستخدم حاليا" to GatewayLoginResult.ROUTER_ALREADY_LOGGED_IN,
        "invalid calling-station-id" to GatewayLoginResult.ROUTER_DEVICE_RESTRICTED,
        "invalid mac address" to GatewayLoginResult.ROUTER_DEVICE_RESTRICTED,
        "الجهاز مرفوض بسبب قيد على عنوان الجهاز" to GatewayLoginResult.ROUTER_DEVICE_RESTRICTED,
        "radius timeout" to GatewayLoginResult.ROUTER_RADIUS_TIMEOUT,
        "radius server is not responding" to GatewayLoginResult.ROUTER_RADIUS_TIMEOUT,
        "radius server not responding" to GatewayLoginResult.ROUTER_RADIUS_TIMEOUT,
        "الحساب لم يستجب، ولا يعني ذلك انتهاء الكرت" to GatewayLoginResult.ROUTER_RADIUS_TIMEOUT,
        "no valid profile found" to GatewayLoginResult.ROUTER_NO_VALID_PROFILE,
        "لا توجد باقة صالحة حاليا" to GatewayLoginResult.ROUTER_NO_VALID_PROFILE
    )

    fun parse(origin: URI, response: ExchangeResult.Received): GatewayLoginResult? {
        if (response.statusCode !in setOf(200, 400, 401, 403) ||
            response.body.isEmpty() || response.body.size > GatewayTransport.MAX_RESPONSE_BYTES) return null
        if (origin.scheme !in setOf("http", "https") ||
            origin.host?.let(LoginRequest::isGateway) != true ||
            origin.port != -1 || origin.rawUserInfo != null ||
            origin.rawQuery != null || origin.rawFragment != null) return null
        val page = origin.resolve("/login")
        val doc = Jsoup.parse(response.body.toString(Charsets.UTF_8), page.toString())
        if (doc.select("base, meta[http-equiv=refresh]").isNotEmpty()) return null
        val login = doc.select("form").filter { it.attr("name") == "login" }.singleOrNull() ?: return null
        if (!PortalParser.validForm(login, origin, page) || !StatusFields.visible(login) ||
            login.select("input[name=username]").size != 1 ||
            !PortalParser.usableUsername(login.select("input[name=username]").single())) return null

        val explicit = doc.select(ERROR_MARKERS).toList().filter(StatusFields::visibleEvidence)
            .map { normalize(it.text()) }.filter { it.isNotEmpty() }.distinct()
        if (explicit.isEmpty()) return null
        // Unknown/contradictory visible messages cannot establish a specific rejection reason.
        val known = explicit.map { messages[it] ?: GatewayLoginResult.REQUEST_REJECTED }.distinct()
        return known.singleOrNull() ?: GatewayLoginResult.REQUEST_REJECTED
    }

    private fun normalize(value: String): String =
        spaces.replace(value.trim(), " ").trimEnd('.', '!', '،', '。').trim().lowercase()
}
