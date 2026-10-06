package io.github.hebadenys.fitnesshub.core.xiaomi

import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Locale

/** Stable codes only: never retain response bodies, URLs, cookies or chained exceptions. */
internal enum class XiaomiAccessFailure {
    PRIVATE_SIGNING_REQUIRED, INVALID_INPUT, UNSAFE_ENDPOINT, REDIRECT_REJECTED,
    PROTOCOL_CHANGED, AUTH_REJECTED, CAPTCHA_REQUIRED, VERIFICATION_REQUIRED,
    AUTH_REQUIRED, RATE_LIMITED, REMOTE_UNAVAILABLE, NETWORK_ERROR, TLS_ERROR,
    RESPONSE_TOO_LARGE, SESSION_MISSING, SESSION_EXPIRED, SESSION_UNREADABLE,
    SESSION_SCOPE_MISMATCH, SESSION_CHANGED, STORAGE_ERROR
}

internal class XiaomiAccessException(val reason: XiaomiAccessFailure) : Exception(reason.name)
internal fun accessFailure(reason: XiaomiAccessFailure): Nothing = throw XiaomiAccessException(reason)

/** Production assembly stays closed until FH-SAFE-03 is explicitly resolved. */
internal fun interface XiaomiNetworkGate {
    fun requireAllowed()

    companion object {
        val AwaitingPrivateSigning = XiaomiNetworkGate {
            accessFailure(XiaomiAccessFailure.PRIVATE_SIGNING_REQUIRED)
        }
    }
}

internal enum class XiaomiEndpoint { LOGIN_START, LOGIN_PASSWORD, SERVICE_TICKET, SCALE_HISTORY }

internal data class XiaomiHttpRequest(
    val endpoint: XiaomiEndpoint,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val form: String? = null
) : PrivateXiaomiValue() {
    val method: String get() = if (endpoint in setOf(XiaomiEndpoint.LOGIN_PASSWORD, XiaomiEndpoint.SCALE_HISTORY)) "POST" else "GET"
    val responseLimit: Int get() = if (endpoint == XiaomiEndpoint.SCALE_HISTORY) 2 * 1024 * 1024 else 64 * 1024
}

internal data class XiaomiHttpResponse(
    val status: Int,
    val body: ByteArray = byteArrayOf(),
    val setCookies: List<String> = emptyList(),
    val location: String? = null
) : PrivateXiaomiValue() {
    fun text(): String = try {
        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(body)).toString()
    } catch (_: Exception) {
        accessFailure(XiaomiAccessFailure.PROTOCOL_CHANGED)
    }
}

internal fun interface XiaomiHttpExchange {
    suspend fun execute(request: XiaomiHttpRequest): XiaomiHttpResponse
}

/** Exact origins AND paths. No wildcard mi.com trust, custom port, userinfo or encoded path. */
internal object XiaomiUrlPolicy {
    const val START = "https://account.xiaomi.com/pass/serviceLogin?_json=true&sid=xiaomiio"
    const val PASSWORD = "https://account.xiaomi.com/pass/serviceLoginAuth2"
    const val CALLBACK = "https://sts.api.io.mi.com/sts"
    private val regions = setOf("de", "i2", "ru", "sg", "us")

    fun check(url: String, endpoint: XiaomiEndpoint) {
        if (url.length > 8192 || url.any { it <= ' ' || it > '~' || it == '\\' }) unsafe()
        val uri = try { URI(url) } catch (_: Exception) { unsafe() }
        if (uri.scheme != "https" || uri.rawUserInfo != null || uri.port != -1 || uri.rawFragment != null) unsafe()
        val host = uri.host?.lowercase(Locale.ROOT) ?: unsafe()
        val path = uri.rawPath
        val allowed = when (endpoint) {
            XiaomiEndpoint.LOGIN_START -> host == "account.xiaomi.com" && path == "/pass/serviceLogin" && uri.rawQuery == "_json=true&sid=xiaomiio"
            XiaomiEndpoint.LOGIN_PASSWORD -> host == "account.xiaomi.com" && path == "/pass/serviceLoginAuth2" && uri.rawQuery == null
            XiaomiEndpoint.SERVICE_TICKET -> host == "sts.api.io.mi.com" && path == "/sts"
            XiaomiEndpoint.SCALE_HISTORY -> uri.rawQuery == null && (
                host == "api.io.mi.com" && path == "/app/eco/scale/getData" ||
                    regions.any { host == "$it.api.io.mi.com" } && path == "/app/eco/common/scale/getUserDataByPage")
        }
        if (!allowed) unsafe()
    }

    fun check(request: XiaomiHttpRequest) {
        check(request.url, request.endpoint)
        if ((request.method == "POST") != (request.form != null) || (request.form?.length ?: 0) > 64 * 1024) {
            accessFailure(XiaomiAccessFailure.INVALID_INPUT)
        }
        val allowed = when (request.endpoint) {
            XiaomiEndpoint.LOGIN_START, XiaomiEndpoint.SERVICE_TICKET -> setOf("accept")
            XiaomiEndpoint.LOGIN_PASSWORD -> setOf("accept", "cookie", "content-type")
            XiaomiEndpoint.SCALE_HISTORY -> setOf("accept", "cookie", "content-type", "miot-request-model")
        }
        request.headers.forEach { (key, value) ->
            if (key.lowercase(Locale.ROOT) !in allowed || value.length > 16384 || value.any { it < ' ' || it > '~' }) {
                accessFailure(XiaomiAccessFailure.INVALID_INPUT)
            }
        }
    }

    private fun unsafe(): Nothing = accessFailure(XiaomiAccessFailure.UNSAFE_ENDPOINT)
}

internal fun requireHttpOk(status: Int) {
    if (status == 200) return
    accessFailure(when (status) {
        401, 403 -> XiaomiAccessFailure.AUTH_REQUIRED
        429 -> XiaomiAccessFailure.RATE_LIMITED
        in 300..399 -> XiaomiAccessFailure.REDIRECT_REJECTED
        in 500..599 -> XiaomiAccessFailure.REMOTE_UNAVAILABLE
        else -> XiaomiAccessFailure.PROTOCOL_CHANGED
    })
}
