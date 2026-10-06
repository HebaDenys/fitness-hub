package io.github.hebadenys.fitnesshub.core.xiaomi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Clock

/** UI-safe account metadata: deliberately no cookie, security key, password or session DTO. */
internal data class XiaomiAccountInfo(
    val connectionId: String,
    val region: XiaomiRegion,
    val userId: String
) : PrivateXiaomiValue() {
    fun scope(model: String) = XiaomiScope(connectionId, region, model, userId)
}

internal data class XiaomiSourceCandidate(
    val key: String,
    val subject: XiaomiSubject,
    val deviceId: String,
    val model: String,
    val displayName: String?,
    val latestAtMillis: Long
) : PrivateXiaomiValue()

/** A page of observed identities, not a claim to enumerate a Xiaomi household/device catalogue. */
internal data class XiaomiDiscoveryPage(
    val scope: XiaomiScope,
    val candidates: List<XiaomiSourceCandidate>,
    val nextBeforeMillis: Long?,
    val rowsScanned: Int,
    val unresolvedRows: Int
) : PrivateXiaomiValue()

internal object XiaomiModels {
    val knownProtocolModels = listOf("yunmai.scales.ms103", "yunmai.scales.ms104")
    fun valid(model: String) = model.length <= 128 && model.matches(Regex("[a-z0-9]+\\.[a-z0-9]+\\.[a-z0-9_]+"))
}

/**
 * Discovery uses the existing, verified read-only history endpoint. No invented family API.
 * Only metadata from this response is kept in memory. No other person's record is persisted.
 * Optional names come from data.user.name documented by SmartScaleConnect a9e5c04/client.go.
 */
internal class XiaomiDiscoveryReader(private val clock: Clock = Clock.systemUTC()) {
    suspend fun read(source: XiaomiPageSource, scope: XiaomiScope, beforeMillis: Long): XiaomiDiscoveryPage {
        val response = source.fetch(XiaomiScaleRequest.history(scope, beforeMillis))
        return withContext(Dispatchers.Default) {
            val page = XiaomiScaleProtocol(clock).parse(response, scope, beforeMillis)
            val root = XiaomiJsonReader(response).read()
            val rows = when (root) {
                is XiaomiJson.Array -> root.elements
                is XiaomiJson.Object -> (root.fields["result"] as XiaomiJson.Array).elements
                else -> throw XiaomiProtocolException(XiaomiFailure.UNEXPECTED_RESPONSE)
            }
            var unresolved = 0
            val candidates = linkedMapOf<String, XiaomiSourceCandidate>()
            page.records.forEachIndexed { index, record ->
                val subject = record.subject
                val device = record.deviceId
                if (subject == null || device == null || record.model != scope.model) {
                    unresolved++
                    return@forEachIndexed
                }
                val key = candidateKey(scope, subject, device)
                val candidate = XiaomiSourceCandidate(key, subject, device, record.model,
                    displayName(rows[index]), record.measuredAt?.toEpochMilli() ?: record.createTimeMillis)
                val previous = candidates[key]
                if (previous == null || candidate.latestAtMillis > previous.latestAtMillis) candidates[key] = candidate
            }
            XiaomiDiscoveryPage(scope, candidates.values.toList(), page.nextBeforeMillis, rows.size, unresolved)
        }
    }

    private fun displayName(row: XiaomiJson): String? {
        val data = (row as? XiaomiJson.Object)?.fields?.get("data")
        val body = when (data) {
            is XiaomiJson.Object -> data
            is XiaomiJson.Text -> XiaomiJsonReader(data.value).read() as? XiaomiJson.Object
            else -> null
        }
        val user = body?.fields?.get("user") as? XiaomiJson.Object
        val text = (user?.fields?.get("name") as? XiaomiJson.Text)?.value ?: return null
        // Reject control/bidi/format characters, rather than presenting misleading identity labels.
        return text.trim().takeIf { it.isNotEmpty() && it.length <= 80 && it.none { ch ->
            Character.isISOControl(ch) || Character.getType(ch) == Character.FORMAT.toInt()
        } }
    }

    companion object {
        fun candidateKey(scope: XiaomiScope, subject: XiaomiSubject, device: String): String =
            XiaomiSnapshotCodec.sha256(XiaomiJson.Object(mapOf(
                "connection" to XiaomiJson.Text(scope.connectionId), "region" to XiaomiJson.Text(scope.region.wireName),
                "login" to XiaomiJson.Text(scope.loginUid), "model" to XiaomiJson.Text(scope.model),
                "uid" to XiaomiJson.Text(subject.uid), "account" to XiaomiJson.Text(subject.accountId),
                "device" to XiaomiJson.Text(device)
            )).encode())
    }
}
