package io.github.hebadenys.fitnesshub.core.xiaomi

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.net.HttpCookie
import java.security.SecureRandom
import java.time.Clock

/**
 * Three-step xiaomiio login adapted from SmartScaleConnect auth.go, MIT/a9e5c04.
 * Challenge detection is implemented; CAPTCHA/2FA continuation is NOT. Never bypass
 * a challenge, blindly retry a password, or reinterpret any vendor error as success.
 */
internal class XiaomiAuthentication(
    private val http: XiaomiHttpExchange,
    private val clock: Clock = Clock.systemUTC(),
    private val random: SecureRandom = SecureRandom()
) {
    suspend fun login(connectionId: String, region: XiaomiRegion, username: String, password: CharArray): XiaomiSession {
        try {
            if (!connectionId.matches(Regex("[A-Za-z0-9_-]{1,128}")) || username.isBlank() ||
                username.length > 320 || username.any { it < ' ' } || password.isEmpty() || password.size > 1024) {
                accessFailure(XiaomiAccessFailure.INVALID_INPUT)
            }
            val start = loginJson(http.execute(XiaomiHttpRequest(XiaomiEndpoint.LOGIN_START, XiaomiUrlPolicy.START)))
            detectChallenge(start)
            val code = number(start, "code")
            // An unauthenticated serviceLogin may legitimately return 70016 plus the login form.
            if (code != 0L && code != 70016L) authCode(code)
            if (text(start, "sid", 32) != "xiaomiio" || text(start, "callback", 8192) != XiaomiUrlPolicy.CALLBACK) {
                accessFailure(XiaomiAccessFailure.UNSAFE_ENDPOINT)
            }
            val sign = text(start, "_sign", 4096)
            val qs = text(start, "qs", 4096)
            val deviceId = ByteArray(16).also(random::nextBytes).joinToString("") { "%02x".format(it.toInt() and 255) }
            val form = XiaomiWireCrypto.form(mapOf(
                "_json" to "true", "hash" to XiaomiWireCrypto.passwordHash(password),
                "sid" to "xiaomiio", "callback" to XiaomiUrlPolicy.CALLBACK,
                "_sign" to sign, "qs" to qs, "user" to username
            ))
            password.fill('\u0000')
            currentCoroutineContext().ensureActive()
            val authenticated = loginJson(http.execute(XiaomiHttpRequest(
                XiaomiEndpoint.LOGIN_PASSWORD, XiaomiUrlPolicy.PASSWORD,
                mapOf("Content-Type" to "application/x-www-form-urlencoded", "Cookie" to "deviceId=$deviceId"), form
            )))
            detectChallenge(authenticated)
            authCode(number(authenticated, "code"))
            val userId = number(authenticated, "userId").takeIf { it > 0 }?.toString()
                ?: accessFailure(XiaomiAccessFailure.PROTOCOL_CHANGED)
            val security = text(authenticated, "ssecurity", 128)
            val securityBytes = XiaomiWireCrypto.unbase64(security, 128)
            try {
                if (securityBytes.size !in 16..64) accessFailure(XiaomiAccessFailure.PROTOCOL_CHANGED)
            } finally { securityBytes.fill(0) }
            val location = text(authenticated, "location", 8192)
            XiaomiUrlPolicy.check(location, XiaomiEndpoint.SERVICE_TICKET)
            // Account cookies, passToken and password hash are never forwarded to the STS hop.
            val ticket = http.execute(XiaomiHttpRequest(XiaomiEndpoint.SERVICE_TICKET, location))
            requireHttpOk(ticket.status) // No implicit redirects, even to a familiar domain.
            val cookies = parseCookies(ticket.setCookies)
            val token = cookies["serviceToken"] ?: accessFailure(XiaomiAccessFailure.PROTOCOL_CHANGED)
            cookies["userId"]?.let {
                if (it.value != userId) accessFailure(XiaomiAccessFailure.PROTOCOL_CHANGED)
            }
            val now = clock.millis()
            if (token.maxAge == 0L || token.hasExpired()) accessFailure(XiaomiAccessFailure.AUTH_REQUIRED)
            val maxAge = if (token.maxAge > 0) minOf(token.maxAge, XiaomiSession.MAX_LOCAL_AGE_MILLIS / 1000) * 1000
                else XiaomiSession.MAX_LOCAL_AGE_MILLIS
            return XiaomiSession(connectionId, region, userId, security, token.value,
                cookies["cUserId"]?.value, now, Math.addExact(now, maxAge))
        } finally { password.fill('\u0000') }
    }

    private fun loginJson(response: XiaomiHttpResponse): Map<String, XiaomiJson> {
        requireHttpOk(response.status)
        if (response.body.size > 64 * 1024) accessFailure(XiaomiAccessFailure.RESPONSE_TOO_LARGE)
        try {
            val body = response.text()
            if (!body.startsWith(PREFIX)) accessFailure(XiaomiAccessFailure.PROTOCOL_CHANGED)
            return (XiaomiJsonReader(body.removePrefix(PREFIX)).read() as? XiaomiJson.Object)?.fields
                ?: accessFailure(XiaomiAccessFailure.PROTOCOL_CHANGED)
        } catch (_: XiaomiProtocolException) { accessFailure(XiaomiAccessFailure.PROTOCOL_CHANGED) }
    }

    private fun detectChallenge(fields: Map<String, XiaomiJson>) {
        fun present(key: String) = (fields[key] as? XiaomiJson.Text)?.value?.isNotBlank() == true
        if (present("captchaUrl")) accessFailure(XiaomiAccessFailure.CAPTCHA_REQUIRED)
        if (present("notificationUrl")) accessFailure(XiaomiAccessFailure.VERIFICATION_REQUIRED)
    }

    private fun authCode(code: Long) {
        if (code == 0L) return
        accessFailure(when (code) {
            70016L -> XiaomiAccessFailure.AUTH_REJECTED
            87001L -> XiaomiAccessFailure.CAPTCHA_REQUIRED
            else -> XiaomiAccessFailure.PROTOCOL_CHANGED
        })
    }

    private fun parseCookies(headers: List<String>): Map<String, HttpCookie> {
        if (headers.size > 16 || headers.sumOf { it.length } > 32 * 1024) accessFailure(XiaomiAccessFailure.RESPONSE_TOO_LARGE)
        val result = mutableMapOf<String, HttpCookie>()
        try {
            for (header in headers) {
                if (header.any { it == '\r' || it == '\n' }) accessFailure(XiaomiAccessFailure.PROTOCOL_CHANGED)
                for (cookie in HttpCookie.parse(header)) {
                    if (cookie.name !in setOf("serviceToken", "userId", "cUserId")) continue
                    val domain = cookie.domain?.lowercase(java.util.Locale.ROOT)?.removePrefix(".")
                    if (domain != null && domain !in setOf("io.mi.com", "api.io.mi.com", "sts.api.io.mi.com")) {
                        accessFailure(XiaomiAccessFailure.UNSAFE_ENDPOINT)
                    }
                    if (result.put(cookie.name, cookie) != null) accessFailure(XiaomiAccessFailure.PROTOCOL_CHANGED)
                }
            }
        } catch (_: IllegalArgumentException) { accessFailure(XiaomiAccessFailure.PROTOCOL_CHANGED) }
        return result
    }

    private fun text(fields: Map<String, XiaomiJson>, key: String, limit: Int): String =
        (fields[key] as? XiaomiJson.Text)?.value?.takeIf { it.isNotBlank() && it.length <= limit && it.none { ch -> ch < ' ' } }
            ?: accessFailure(XiaomiAccessFailure.PROTOCOL_CHANGED)

    private fun number(fields: Map<String, XiaomiJson>, key: String): Long {
        val raw = when (val value = fields[key]) {
            is XiaomiJson.Number -> value.literal
            is XiaomiJson.Text -> value.value
            else -> accessFailure(XiaomiAccessFailure.PROTOCOL_CHANGED)
        }
        return raw.toLongOrNull()?.takeIf { it.toString() == raw }
            ?: accessFailure(XiaomiAccessFailure.PROTOCOL_CHANGED)
    }

    companion object { private const val PREFIX = "&&&START&&&" }
}
