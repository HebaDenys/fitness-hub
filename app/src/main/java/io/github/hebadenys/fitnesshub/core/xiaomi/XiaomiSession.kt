package io.github.hebadenys.fitnesshub.core.xiaomi

/** No password or broad account passToken. A service session is still sensitive. */
internal data class XiaomiSession(
    val connectionId: String,
    val region: XiaomiRegion,
    val userId: String,
    val securityBase64: String,
    val serviceToken: String,
    val cUserId: String?,
    val issuedAtMillis: Long,
    val validUntilMillis: Long
) : PrivateXiaomiValue() {
    init {
        if (!connectionId.matches(Regex("[A-Za-z0-9_-]{1,128}")) ||
            userId.toLongOrNull()?.let { it > 0 && it.toString() == userId } != true ||
            !cookieValue(serviceToken, 8192) || cUserId?.let { !cookieValue(it, 512) } == true ||
            issuedAtMillis <= 0 || validUntilMillis <= issuedAtMillis ||
            validUntilMillis - issuedAtMillis > MAX_LOCAL_AGE_MILLIS) {
            accessFailure(XiaomiAccessFailure.PROTOCOL_CHANGED)
        }
        val security = XiaomiWireCrypto.unbase64(securityBase64, 128)
        try {
            if (security.size !in 16..64) accessFailure(XiaomiAccessFailure.PROTOCOL_CHANGED)
        } finally { security.fill(0) }
    }

    fun usableAt(now: Long): Boolean = now >= issuedAtMillis - 300_000 && now < validUntilMillis
    fun matches(scope: XiaomiScope): Boolean = connectionId == scope.connectionId && region == scope.region && userId == scope.loginUid
    fun cookieHeader(): String = "userId=$userId; serviceToken=$serviceToken" + (cUserId?.let { "; cUserId=$it" } ?: "")

    companion object {
        // App-side maximum, NOT a claim about Xiaomi's server expiry or refresh support.
        const val MAX_LOCAL_AGE_MILLIS = 24 * 60 * 60 * 1000L
        fun cookieValue(value: String, limit: Int): Boolean = value.isNotEmpty() && value.length <= limit &&
            value.all { it in '!'..'~' && it !in setOf(';', ',', '"', '\\') }
    }
}

internal interface XiaomiSessionStore {
    suspend fun load(): XiaomiSession?
    suspend fun save(session: XiaomiSession)
    suspend fun clear()
}

internal object XiaomiSessionCodec {
    fun encode(session: XiaomiSession): ByteArray {
        fun text(value: String) = XiaomiJson.Text(value)
        return XiaomiJson.Object(mapOf(
            "version" to XiaomiJson.Number("1"), "connectionId" to text(session.connectionId),
            "region" to text(session.region.wireName), "userId" to text(session.userId),
            "security" to text(session.securityBase64), "serviceToken" to text(session.serviceToken),
            "cUserId" to (session.cUserId?.let(::text) ?: XiaomiJson.Null),
            "issuedAtMillis" to XiaomiJson.Number(session.issuedAtMillis.toString()),
            "validUntilMillis" to XiaomiJson.Number(session.validUntilMillis.toString())
        )).encode().toByteArray(Charsets.UTF_8)
    }

    fun decode(bytes: ByteArray): XiaomiSession {
        if (bytes.size > 16 * 1024) accessFailure(XiaomiAccessFailure.SESSION_UNREADABLE)
        try {
            val root = XiaomiJsonReader(XiaomiHttpResponse(200, bytes).text()).read() as? XiaomiJson.Object
                ?: accessFailure(XiaomiAccessFailure.SESSION_UNREADABLE)
            val fields = root.fields
            if (fields.keys != setOf("version", "connectionId", "region", "userId", "security", "serviceToken", "cUserId", "issuedAtMillis", "validUntilMillis")) {
                accessFailure(XiaomiAccessFailure.SESSION_UNREADABLE)
            }
            fun text(key: String) = (fields[key] as? XiaomiJson.Text)?.value ?: accessFailure(XiaomiAccessFailure.SESSION_UNREADABLE)
            fun number(key: String) = (fields[key] as? XiaomiJson.Number)?.literal?.toLongOrNull() ?: accessFailure(XiaomiAccessFailure.SESSION_UNREADABLE)
            if (number("version") != 1L) accessFailure(XiaomiAccessFailure.SESSION_UNREADABLE)
            val region = XiaomiRegion.entries.singleOrNull { it.wireName == text("region") }
                ?: accessFailure(XiaomiAccessFailure.SESSION_UNREADABLE)
            val cUserId = if (fields["cUserId"] == XiaomiJson.Null) null else text("cUserId")
            return XiaomiSession(text("connectionId"), region, text("userId"), text("security"),
                text("serviceToken"), cUserId, number("issuedAtMillis"), number("validUntilMillis"))
        } catch (_: Exception) {
            accessFailure(XiaomiAccessFailure.SESSION_UNREADABLE)
        }
    }
}
