package io.github.hebadenys.fitnesshub.core.xiaomi

import io.github.hebadenys.fitnesshub.core.xiaomi.storage.XiaomiSnapshotEntity
import java.security.MessageDigest

/** Versioned local representation of a parsed, already sanitized record. No HTTP/session data. */
internal object XiaomiSnapshotCodec {
    fun encode(record: XiaomiScaleRecord, receivedAtMillis: Long): XiaomiSnapshotEntity {
        val scope = obj("region" to text(record.scope.region.wireName), "loginUid" to text(record.scope.loginUid), "model" to text(record.scope.model))
        val subject = record.subject ?: throw XiaomiArchiveException(ArchiveFailure.AMBIGUOUS_IDENTITY)
        val identity = obj(
            "scope" to scope,
            "subject" to obj("uid" to text(subject.uid), "accountId" to text(subject.accountId)),
            "deviceId" to text(record.deviceId), "serial" to text(record.serial),
            "model" to text(record.model), "fromSource" to number(record.fromSource),
            "createTimeMillis" to number(record.createTimeMillis), "measuredAtMillis" to number(record.measuredAt?.toEpochMilli())
        )
        val metrics = XiaomiJson.Object(record.metrics.mapValues { (_, metric) ->
            obj("vendorPath" to text(metric.vendorPath), "value" to number(metric.value),
                "raw" to metric.raw, "unit" to text(metric.unit.name), "method" to text(metric.method.name),
                "declaredUnit" to text(metric.declaredUnit), "issues" to issues(metric.issues))
        })
        val payload = XiaomiJson.Object(identity.fields + mapOf(
            "formatVersion" to number(1), "parserVersion" to text(record.parserVersion),
            "acquisitionMethod" to text("XIAOMI_CLOUD"), "dataVersion" to number(record.dataVersion),
            "metrics" to metrics, "vendorFields" to record.vendorFields, "issues" to issues(record.issues)
        )).encode()
        XiaomiJsonReader(payload).read()
        return XiaomiSnapshotEntity(connectionId = record.scope.connectionId, contentHash = sha256(payload),
            eventKey = sha256(identity.encode()), createTimeMillis = record.createTimeMillis,
            measuredAtMillis = record.measuredAt?.toEpochMilli(), receivedAtMillis = receivedAtMillis,
            parserVersion = record.parserVersion, snapshotJson = payload)
    }

    fun pageHash(scope: XiaomiScope, requested: Long, page: XiaomiScalePage): String = sha256(obj(
        "connection" to text(scope.connectionId), "requested" to number(requested),
        "next" to number(page.nextBeforeMillis), "terminal" to XiaomiJson.Bool(page.terminalByShortPage),
        // Digests include annotations/dataVersion as well as metrics. No other person's raw payload is stored.
        "rows" to XiaomiJson.Array(page.records.map { text(encode(it, 0).contentHash) })
    ).encode())

    fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun obj(vararg values: Pair<String, XiaomiJson>) = XiaomiJson.Object(mapOf(*values))
    private fun text(value: String?): XiaomiJson = value?.let(XiaomiJson::Text) ?: XiaomiJson.Null
    private fun number(value: Number?): XiaomiJson {
        require(value !is Double || value.isFinite()) { "Non-finite numeric field" }
        return value?.let { XiaomiJson.Number(it.toString()) } ?: XiaomiJson.Null
    }
    private fun issues(values: Set<XiaomiIssue>) = XiaomiJson.Array(values.map { it.name }.sorted().map(XiaomiJson::Text))
}
