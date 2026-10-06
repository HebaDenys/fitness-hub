package io.github.hebadenys.fitnesshub.core.xiaomi

import java.time.Clock
import java.time.Instant

// Protocol and mappings adapted from SmartScaleConnect (MIT), Alexey Khit, 2025.
// Pinned reference and full notice: docs/connectors/xiaomi-protocol.md and assets/licenses/.
internal enum class XiaomiRegion(val wireName: String) { CN("cn"), DE("de"), I2("i2"), RU("ru"), SG("sg"), US("us") }

internal abstract class PrivateXiaomiValue {
    final override fun toString(): String = "${javaClass.simpleName}(<redacted>)"
}

internal data class XiaomiScope(
    val connectionId: String,
    val region: XiaomiRegion,
    val model: String,
    val loginUid: String
) : PrivateXiaomiValue() {
    init {
        if (!connectionId.matches(Regex("[A-Za-z0-9_-]{1,128}")) ||
            !model.matches(Regex("[a-z0-9]+\\.[a-z0-9]+\\.[a-z0-9_]+")) ||
            loginUid.toLongOrNull()?.let { it > 0 && it.toString() == loginUid } != true
        ) throw XiaomiProtocolException(XiaomiFailure.INVALID_SCOPE)
    }
}

/** Describes a read-only request. It is NOT an HTTP client and contains no auth cookies. */
internal data class XiaomiScaleRequest(
    val baseUrl: String,
    val signaturePath: String,
    val modelHeader: String,
    val dataJson: String,
    val beforeMillis: Long
) : PrivateXiaomiValue() {
    companion object {
        fun history(scope: XiaomiScope, beforeMillis: Long): XiaomiScaleRequest {
            if (beforeMillis <= 1) throw XiaomiProtocolException(XiaomiFailure.INVALID_CURSOR)
            fun number(value: Long) = XiaomiJson.Number(value.toString())
            val time = mapOf("endTime" to number(1), "beginTime" to number(beforeMillis))
            val cn = scope.region == XiaomiRegion.CN
            val data = if (cn) mapOf(
                "param" to XiaomiJson.Object(time), "model" to XiaomiJson.Text(scope.model),
                "uid" to number(scope.loginUid.toLong()), "did" to number(0)
            ) else time + mapOf(
                "model" to XiaomiJson.Text(scope.model), "uid" to XiaomiJson.Text(scope.loginUid),
                "did" to number(0), "accountId" to number(0)
            )
            return XiaomiScaleRequest(
                baseUrl = if (cn) "https://api.io.mi.com/app" else "https://${scope.region.wireName}.api.io.mi.com/app",
                signaturePath = if (cn) "/eco/scale/getData" else "/eco/common/scale/getUserDataByPage",
                modelHeader = scope.model, dataJson = XiaomiJson.Object(data).encode(), beforeMillis = beforeMillis
            )
        }
    }
}

internal data class XiaomiSubject(val uid: String, val accountId: String) : PrivateXiaomiValue()
internal enum class XiaomiMethod { VENDOR_REPORTED_WEIGHT, VENDOR_ESTIMATE, VENDOR_REPORTED_VITAL, UNKNOWN }
internal enum class XiaomiUnit { KG, PERCENT, INDEX, YEARS, BPM, KCAL_PERIOD_UNVERIFIED, UNKNOWN }
internal enum class XiaomiIssue {
    IDENTITY_INCOMPLETE, IDENTITY_CONFLICT, DEVICE_ID_MISSING, MODEL_MISMATCH, MODEL_UNVERIFIED,
    UNSUPPORTED_FORMAT, INVALID_VALUE, INVALID_PERCENTAGE, NONPOSITIVE_VALUE,
    UNIT_UNVERIFIED, TIMESTAMP_UNRESOLVED, FUTURE_TIMESTAMP, VALUE_CONFLICT, OMITTED_UNCLASSIFIED_FIELD
}

/** Transport is always Xiaomi Cloud; measurement method and unit are independent. */
internal data class XiaomiMetric(
    val vendorPath: String,
    val value: Double?,
    val unit: XiaomiUnit,
    val method: XiaomiMethod,
    val raw: XiaomiJson,
    val issues: Set<XiaomiIssue> = emptySet(),
    val declaredUnit: String? = null
) : PrivateXiaomiValue()

/** A protocol result, not a DB row. Never assign a local profile using display name or weight. */
internal data class XiaomiScaleRecord(
    val scope: XiaomiScope,
    val subject: XiaomiSubject?,
    val deviceId: String?,
    val serial: String?,
    val model: String,
    val fromSource: Int,
    val dataVersion: Long?,
    val createTimeMillis: Long,
    val measuredAt: Instant?,
    val metrics: Map<String, XiaomiMetric>,
    val vendorFields: XiaomiJson.Object,
    val issues: Set<XiaomiIssue>
) : PrivateXiaomiValue() {
    // dataVersion's revision semantics and sn's uniqueness are NOT proven by upstream.
    // Persistence must add a confirmed subject binding and a separate identity policy.
    val parserVersion: String get() = XiaomiScaleProtocol.VERSION
}

internal data class XiaomiScalePage(
    val records: List<XiaomiScaleRecord>,
    val nextBeforeMillis: Long?,
    val terminalByShortPage: Boolean
) : PrivateXiaomiValue()

/** Accepts decrypted response JSON only. No credentials, HTTP, UI, Room or side effects. */
internal class XiaomiScaleProtocol(private val clock: Clock = Clock.systemUTC()) {
    fun parse(response: String, scope: XiaomiScope, beforeMillis: Long): XiaomiScalePage {
        if (beforeMillis <= 1) fail(XiaomiFailure.INVALID_CURSOR)
        val elements = resultArray(XiaomiJsonReader(response).read()).elements
        if (elements.size > PAGE_SIZE) fail(XiaomiFailure.UNEXPECTED_PAGE_SIZE)
        val rows = elements.map { it as? XiaomiJson.Object ?: fail(XiaomiFailure.UNEXPECTED_RESPONSE) }
        val times = rows.map { row ->
            integer(row.fields["createTime"])?.takeIf { it > 0 && it <= beforeMillis }
                ?: fail(XiaomiFailure.INVALID_TIMESTAMP)
        }
        if (times.zipWithNext().any { (a, b) -> b > a }) fail(XiaomiFailure.UNORDERED_PAGE)
        val next = if (rows.size == PAGE_SIZE) times.last() else null
        if (next != null && next >= beforeMillis) fail(XiaomiFailure.STALLED_CURSOR)
        return XiaomiScalePage(rows.map { parseRecord(it, scope) }, next, rows.size < PAGE_SIZE)
    }

    private fun resultArray(root: XiaomiJson): XiaomiJson.Array {
        if (root is XiaomiJson.Array) return root // result already extracted by a transport
        val envelope = root as? XiaomiJson.Object ?: fail(XiaomiFailure.UNEXPECTED_RESPONSE)
        val code = integer(envelope.fields["code"]) ?: fail(XiaomiFailure.UNEXPECTED_RESPONSE)
        if (code != 0L) fail(XiaomiFailure.VENDOR_ERROR) // Never echo vendor message or headers.
        return envelope.fields["result"] as? XiaomiJson.Array ?: fail(XiaomiFailure.UNEXPECTED_RESPONSE)
    }

    private fun parseRecord(row: XiaomiJson.Object, scope: XiaomiScope): XiaomiScaleRecord {
        val fields = row.fields
        val issues = mutableSetOf<XiaomiIssue>()
        val model = text(fields["model"]) ?: fail(XiaomiFailure.UNEXPECTED_RESPONSE)
        if (model != scope.model) issues += XiaomiIssue.MODEL_MISMATCH
        val verifiedModel = model == scope.model && model in KNOWN_MODELS
        if (model !in KNOWN_MODELS) issues += XiaomiIssue.MODEL_UNVERIFIED
        val source = integer(fields["fromSource"])?.takeIf { it in 0..Int.MAX_VALUE }?.toInt()
            ?: fail(XiaomiFailure.UNEXPECTED_RESPONSE)
        val data = nested(fields["data"])
        val uid = id(fields["uid"])
        val account = id(fields["accountId"])
        val user = data.fields["user"] as? XiaomiJson.Object
        // A login UID, household membership or display name must never substitute for a subject ID.
        var subject = if (uid != null && account != null) XiaomiSubject(uid, account) else null
        if (subject == null) issues += XiaomiIssue.IDENTITY_INCOMPLETE
        val nestedAccount = user?.fields?.get("accountId")?.let(::id)
        if (nestedAccount != null && account != null && nestedAccount != account) {
            issues += XiaomiIssue.IDENTITY_CONFLICT
            subject = null
        }
        val device = text(fields["did"])
        if (device == null) issues += XiaomiIssue.DEVICE_ID_MISSING
        val created = integer(fields["createTime"]) ?: fail(XiaomiFailure.INVALID_TIMESTAMP)
        val time = when (source) {
            1, 2 -> created // Inner time is seconds OR milliseconds in upstream; do not use it.
            3 -> integer(data.fields["time"])
            else -> { issues += XiaomiIssue.UNSUPPORTED_FORMAT; null }
        }
        // This protocol is for post-2000 consumer scale data. Seconds are not silently multiplied.
        val measuredAt = time?.takeIf { it >= MIN_MEASUREMENT_MILLIS && it <= MAX_MEASUREMENT_MILLIS }
            ?.let(Instant::ofEpochMilli)
        if (measuredAt == null) issues += XiaomiIssue.TIMESTAMP_UNRESOLVED
        if (time != null && measuredAt == null) issues += XiaomiIssue.INVALID_VALUE
        if (measuredAt != null && measuredAt.isAfter(clock.instant())) issues += XiaomiIssue.FUTURE_TIMESTAMP
        val body = if (source == 3 && data.fields["bodyResData"] != null &&
            data.fields["bodyResData"] != XiaomiJson.Null && data.fields["bodyResData"] != XiaomiJson.Text("")) {
            nested(data.fields["bodyResData"])
        } else null
        val selected = linkedMapOf<String, XiaomiMetric>()
        for ((key, definition) in DEFINITIONS) {
            val rawTop = data.fields[key]
            val rawBody = body?.fields?.get(key)
            val raw = rawBody ?: rawTop ?: continue
            val path = if (rawBody != null) "bodyResData.$key" else key
            val metricIssues = mutableSetOf<XiaomiIssue>()
            if (rawBody != null && rawTop != null && rawBody != rawTop && decimal(rawBody) != decimal(rawTop)) {
                metricIssues += XiaomiIssue.VALUE_CONFLICT
            }
            val supported = verifiedModel && source in 1..3
            val declaredUnit = data.fields["${key}Unit"] ?: data.fields["unit"]
            val unitMismatch = declaredUnit != null && declaredUnit != XiaomiJson.Null
            val rawUnit = (declaredUnit as? XiaomiJson.Text)?.value?.takeIf { UNIT_TEXT.matches(it) }
            // There is no verified override convention: an extra unit field requires review.
            val unit = if (supported && !unitMismatch) definition.unit else XiaomiUnit.UNKNOWN
            if (unit == XiaomiUnit.UNKNOWN || unit == XiaomiUnit.KCAL_PERIOD_UNVERIFIED) metricIssues += XiaomiIssue.UNIT_UNVERIFIED
            var value = decimal(raw)
            if (raw != XiaomiJson.Null && value == null) metricIssues += XiaomiIssue.INVALID_VALUE
            if (value != null && definition.unit == XiaomiUnit.PERCENT && value !in 0.0..100.0) {
                metricIssues += XiaomiIssue.INVALID_PERCENTAGE; value = null
            }
            if (value != null && (value < 0 || definition.positive && value <= 0)) {
                metricIssues += XiaomiIssue.NONPOSITIVE_VALUE; value = null
            }
            if (!supported || unitMismatch) value = null
            // Keep no free-text value where a numeric field is expected (could contain a token).
            val safeRaw = when (raw) {
                is XiaomiJson.Number, XiaomiJson.Null -> raw
                is XiaomiJson.Text -> if (NUMERIC_TEXT.matches(raw.value) || raw.value in INVALID_NUMBERS) raw else XiaomiJson.Null
                else -> XiaomiJson.Null
            }
            if (safeRaw == XiaomiJson.Null && raw != XiaomiJson.Null) issues += XiaomiIssue.OMITTED_UNCLASSIFIED_FIELD
            selected[key] = XiaomiMetric(path, value, unit, definition.method, safeRaw, metricIssues, rawUnit)
        }
        val safe = sanitize(data, issues) as? XiaomiJson.Object ?: XiaomiJson.Object(emptyMap())
        val safeBody = body?.let { sanitize(it, issues) }
        val payload = if (safeBody != null) XiaomiJson.Object(safe.fields + ("bodyResData" to safeBody)) else safe
        return XiaomiScaleRecord(
            scope, subject, device, text(fields["sn"]), model, source,
            integer(fields["dataVersion"]), created, measuredAt, selected.toMap(), payload, issues.toSet()
        )
    }

    /** Numeric extensions remain round-trippable, without inventing their units or meaning. */
    private fun sanitize(value: XiaomiJson, issues: MutableSet<XiaomiIssue>): XiaomiJson? = when (value) {
        is XiaomiJson.Object -> XiaomiJson.Object(value.fields.mapNotNull { (key, child) ->
            val safeKey = key.matches(Regex("[A-Za-z][A-Za-z0-9_]{0,63}")) &&
                DENIED.none { key.lowercase(java.util.Locale.ROOT).contains(it) }
            if (!safeKey || key in IDENTITY_FIELDS || key == "bodyResData") {
                issues += XiaomiIssue.OMITTED_UNCLASSIFIED_FIELD; null
            } else if ((key == "unit" || key.endsWith("Unit")) && child is XiaomiJson.Text && UNIT_TEXT.matches(child.value)) {
                key to child
            } else sanitize(child, issues)?.let { key to it }
        }.toMap())
        is XiaomiJson.Array -> XiaomiJson.Array(value.elements.map { sanitize(it, issues) ?: XiaomiJson.Null })
        is XiaomiJson.Text -> if (NUMERIC_TEXT.matches(value.value) || value.value in INVALID_NUMBERS) value else {
            issues += XiaomiIssue.OMITTED_UNCLASSIFIED_FIELD; null
        }
        is XiaomiJson.Number, is XiaomiJson.Bool, XiaomiJson.Null -> value
    }

    private fun nested(value: XiaomiJson?): XiaomiJson.Object = when (value) {
        is XiaomiJson.Text -> XiaomiJsonReader(value.value).read() as? XiaomiJson.Object ?: fail(XiaomiFailure.UNEXPECTED_RESPONSE)
        is XiaomiJson.Object -> value
        else -> fail(XiaomiFailure.UNEXPECTED_RESPONSE)
    }

    private fun text(value: XiaomiJson?): String? = (value as? XiaomiJson.Text)?.value
        ?.takeIf { it.isNotBlank() && it.length <= 128 && it.none { ch -> ch < ' ' } }

    private fun id(value: XiaomiJson?): String? = integer(value)?.takeIf { it >= 0 }?.toString()
    private fun integer(value: XiaomiJson?): Long? {
        val raw = when (value) { is XiaomiJson.Number -> value.literal; is XiaomiJson.Text -> value.value; else -> return null }
        return raw.takeIf { it.matches(Regex("-?(0|[1-9][0-9]{0,18})")) }?.toLongOrNull()
    }
    private fun decimal(value: XiaomiJson?): Double? {
        val raw = when (value) { is XiaomiJson.Number -> value.literal; is XiaomiJson.Text -> value.value; else -> return null }
        if (raw.length > XiaomiJsonReader.MAX_NUMBER || !NUMERIC_TEXT.matches(raw)) return null
        return raw.toDoubleOrNull()?.takeIf { it.isFinite() }
    }
    private fun fail(reason: XiaomiFailure): Nothing = throw XiaomiProtocolException(reason)

    private data class Definition(val unit: XiaomiUnit, val method: XiaomiMethod, val positive: Boolean = false)
    companion object {
        const val VERSION = "xiaomi-scale-v1"
        const val PAGE_SIZE = 20 // Upstream short-page termination heuristic, not an API completeness guarantee.
        const val MIN_MEASUREMENT_MILLIS = 946_684_800_000L
        const val MAX_MEASUREMENT_MILLIS = 253_402_300_799_999L
        private val KNOWN_MODELS = setOf("yunmai.scales.ms103", "yunmai.scales.ms104")
        private val NUMERIC_TEXT = Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")
        private val UNIT_TEXT = Regex("[A-Za-z0-9_%/ .-]{1,24}")
        private val INVALID_NUMBERS = setOf("NaN", "Infinity", "-Infinity")
        private val DENIED = listOf("token", "password", "passwd", "secret", "cookie", "authorization", "credential", "signature", "security", "nonce", "apikey", "api_key", "privatekey", "private_key")
        private val IDENTITY_FIELDS = setOf("user", "uid", "miid", "duid", "accountId", "deviceId", "name", "birth", "icon", "reportFrom", "mid")
        private val DEFINITIONS = linkedMapOf(
            "weight" to Definition(XiaomiUnit.KG, XiaomiMethod.VENDOR_REPORTED_WEIGHT, true),
            "heartRate" to Definition(XiaomiUnit.BPM, XiaomiMethod.VENDOR_REPORTED_VITAL, true),
            "bmi" to Definition(XiaomiUnit.INDEX, XiaomiMethod.VENDOR_ESTIMATE, true),
            "bfp" to Definition(XiaomiUnit.PERCENT, XiaomiMethod.VENDOR_ESTIMATE),
            "bwp" to Definition(XiaomiUnit.PERCENT, XiaomiMethod.VENDOR_ESTIMATE),
            "bmc" to Definition(XiaomiUnit.KG, XiaomiMethod.VENDOR_ESTIMATE),
            "slm" to Definition(XiaomiUnit.KG, XiaomiMethod.VENDOR_ESTIMATE),
            "smm" to Definition(XiaomiUnit.KG, XiaomiMethod.VENDOR_ESTIMATE),
            "pm" to Definition(XiaomiUnit.KG, XiaomiMethod.VENDOR_ESTIMATE),
            "ma" to Definition(XiaomiUnit.YEARS, XiaomiMethod.VENDOR_ESTIMATE),
            "bt" to Definition(XiaomiUnit.INDEX, XiaomiMethod.VENDOR_ESTIMATE),
            "vfl" to Definition(XiaomiUnit.INDEX, XiaomiMethod.VENDOR_ESTIMATE),
            "sbc" to Definition(XiaomiUnit.INDEX, XiaomiMethod.VENDOR_ESTIMATE),
            "bmr" to Definition(XiaomiUnit.KCAL_PERIOD_UNVERIFIED, XiaomiMethod.VENDOR_ESTIMATE, true),
            "pp" to Definition(XiaomiUnit.PERCENT, XiaomiMethod.VENDOR_ESTIMATE),
            "slp" to Definition(XiaomiUnit.PERCENT, XiaomiMethod.VENDOR_ESTIMATE),
            "bmcp" to Definition(XiaomiUnit.PERCENT, XiaomiMethod.VENDOR_ESTIMATE),
            "bfm" to Definition(XiaomiUnit.KG, XiaomiMethod.VENDOR_ESTIMATE),
            "ffm" to Definition(XiaomiUnit.KG, XiaomiMethod.VENDOR_ESTIMATE),
            "bwm" to Definition(XiaomiUnit.KG, XiaomiMethod.VENDOR_ESTIMATE)
        )
    }
}
