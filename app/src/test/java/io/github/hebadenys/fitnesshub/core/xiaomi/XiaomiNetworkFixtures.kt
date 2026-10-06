package io.github.hebadenys.fitnesshub.core.xiaomi

import java.net.URLDecoder
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Entirely synthetic protocol material; never replace with real account responses. */
internal object XiaomiNetworkFixtures {
    val clock: Clock = Clock.fixed(Instant.parse("2026-10-06T12:00:00Z"), ZoneOffset.UTC)
    val scope = XiaomiScope("fixture-connection", XiaomiRegion.DE, "yunmai.scales.ms103", "10001")
    val security = XiaomiWireCrypto.b64(ByteArray(16) { it.toByte() })
    fun session() = XiaomiSession(scope.connectionId, scope.region, scope.loginUid, security,
        "fixture-service-token", "fixture-c-user", clock.millis(), clock.millis() + 3_600_000)
    fun response(json: String) = XiaomiHttpResponse(200, ("&&&START&&&" + json).toByteArray())
    fun start() = response("""{"code":0,"sid":"xiaomiio","_sign":"fixture-sign","qs":"%3Fsid%3Dxiaomiio","callback":"https://sts.api.io.mi.com/sts"}""")
    fun authenticated(location: String = "https://sts.api.io.mi.com/sts?ticket=fixture-ticket") = response(
        """{"code":0,"userId":10001,"ssecurity":"$security","passToken":"not-to-be-stored","location":"$location"}""")
    fun ticket() = XiaomiHttpResponse(200, setCookies = listOf(
        "serviceToken=fixture-service-token; Domain=.io.mi.com; Path=/; Max-Age=3600; Secure; HttpOnly",
        "userId=10001; Domain=.io.mi.com; Path=/", "cUserId=fixture-c-user; Domain=.io.mi.com; Path=/"
    ))
    fun form(value: String) = value.split('&').associate {
        val (key, field) = it.split('=', limit = 2)
        URLDecoder.decode(key, "UTF-8") to URLDecoder.decode(field, "UTF-8")
    }
    fun encrypted(request: XiaomiHttpRequest, json: String): XiaomiHttpResponse {
        val nonce = XiaomiWireCrypto.unbase64(form(request.form!!).getValue("_nonce"))
        val key = XiaomiWireCrypto.signedNonce(XiaomiWireCrypto.unbase64(security), nonce)
        return XiaomiHttpResponse(200, XiaomiWireCrypto.b64(XiaomiWireCrypto.crypt(key, json.toByteArray())).toByteArray())
    }
    val allow = XiaomiNetworkGate { /* ONLY for synthetic test transports. */ }
}
