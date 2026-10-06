package io.github.hebadenys.fitnesshub.core.xiaomi

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.security.SecureRandom
import java.time.Clock

/** Scope-bound, read-only adapter used by the existing paginated Room reader. */
internal class XiaomiAuthenticatedPageSource(
    private val http: XiaomiHttpExchange,
    private val session: XiaomiSession,
    private val scope: XiaomiScope,
    private val clock: Clock = Clock.systemUTC(),
    private val random: SecureRandom = SecureRandom()
) : XiaomiPageSource {
    override suspend fun fetch(request: XiaomiScaleRequest): String {
        if (!session.matches(scope)) accessFailure(XiaomiAccessFailure.SESSION_SCOPE_MISMATCH)
        if (!session.usableAt(clock.millis())) accessFailure(XiaomiAccessFailure.SESSION_EXPIRED)
        if (request != XiaomiScaleRequest.history(scope, request.beforeMillis)) accessFailure(XiaomiAccessFailure.UNSAFE_ENDPOINT)
        val security = XiaomiWireCrypto.unbase64(session.securityBase64, 128)
        val nonce = XiaomiWireCrypto.nonce(clock, random)
        val key = try { XiaomiWireCrypto.signedNonce(security, nonce) } finally { security.fill(0) }
        try {
            val rawHash = XiaomiWireCrypto.signature(request.signaturePath, request.dataJson, null, key)
            val data = XiaomiWireCrypto.b64(XiaomiWireCrypto.crypt(key, request.dataJson.toByteArray(Charsets.UTF_8)))
            val hash = XiaomiWireCrypto.b64(XiaomiWireCrypto.crypt(key, rawHash.toByteArray(Charsets.UTF_8)))
            val form = XiaomiWireCrypto.form(mapOf("data" to data, "rc4_hash__" to hash,
                "signature" to XiaomiWireCrypto.signature(request.signaturePath, data, hash, key),
                "_nonce" to XiaomiWireCrypto.b64(nonce)))
            val response = http.execute(XiaomiHttpRequest(XiaomiEndpoint.SCALE_HISTORY,
                request.baseUrl + request.signaturePath,
                mapOf("Content-Type" to "application/x-www-form-urlencoded", "Cookie" to session.cookieHeader(),
                    "MIOT-REQUEST-MODEL" to scope.model), form))
            requireHttpOk(response.status)
            currentCoroutineContext().ensureActive()
            if (response.body.size > 2 * 1024 * 1024) accessFailure(XiaomiAccessFailure.RESPONSE_TOO_LARGE)
            val encrypted = XiaomiWireCrypto.unbase64(response.text())
            val plaintext = try { XiaomiWireCrypto.crypt(key, encrypted) } finally { encrypted.fill(0); response.body.fill(0) }
            try {
                val text = XiaomiHttpResponse(200, plaintext).text()
                val root = XiaomiJsonReader(text).read() as? XiaomiJson.Object
                    ?: accessFailure(XiaomiAccessFailure.PROTOCOL_CHANGED)
                val code = (root.fields["code"] as? XiaomiJson.Number)?.literal?.toLongOrNull()
                    ?: accessFailure(XiaomiAccessFailure.PROTOCOL_CHANGED)
                // Upstream does not establish all vendor error-code meanings. Do not guess them.
                if (code != 0L) accessFailure(XiaomiAccessFailure.PROTOCOL_CHANGED)
                return text
            } catch (_: XiaomiProtocolException) {
                accessFailure(XiaomiAccessFailure.PROTOCOL_CHANGED)
            } finally { plaintext.fill(0) }
        } finally { key.fill(0); nonce.fill(0) }
    }
}
