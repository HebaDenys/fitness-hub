package io.github.hebadenys.fitnesshub.core.xiaomi

internal data class XiaomiBoundSource(
    val scope: XiaomiScope,
    val subject: XiaomiSubject,
    val deviceId: String
) : PrivateXiaomiValue()

internal data class XiaomiSourceOverview(
    val block: XiaomiAccessFailure?,
    val account: XiaomiAccountInfo?,
    val sessionProblem: XiaomiAccessFailure?,
    val binding: XiaomiBoundSource?,
    val snapshotCount: Int,
    val committedPages: Long,
    val committedAtMillis: Long?,
    val oldestAtMillis: Long?,
    val newestAtMillis: Long?,
    val pendingHistory: Boolean
) : PrivateXiaomiValue()

internal data class XiaomiDiscoveryState(
    val candidates: List<XiaomiSourceCandidate>,
    val nextBeforeMillis: Long?,
    val pagesScanned: Int,
    val rowsScanned: Int,
    val unresolvedRows: Int
) : PrivateXiaomiValue()

internal data class XiaomiMetricDetail(
    val path: String,
    val value: String,
    val unit: XiaomiUnit,
    val method: XiaomiMethod,
    val issues: List<String>
) : PrivateXiaomiValue()

internal data class XiaomiRecordDetail(
    val hash: String,
    val atMillis: Long?,
    val metrics: List<XiaomiMetricDetail>,
    val issues: List<String>,
    val unreadable: Boolean = false
) : PrivateXiaomiValue()

internal data class XiaomiHistoryView(val records: List<XiaomiRecordDetail>, val hasMore: Boolean) : PrivateXiaomiValue()

/** Feature boundary accepts explicit actions, never exposes a service session to Compose. */
internal interface XiaomiSourceGateway {
    suspend fun overview(): XiaomiSourceOverview
    suspend fun login(region: XiaomiRegion, username: String, password: CharArray)
    suspend fun loginWithCaptcha(region: XiaomiRegion, username: String, password: CharArray,
        captcha: XiaomiCaptchaResponder) = login(region, username, password)
    suspend fun discover(model: String, older: Boolean = false): XiaomiDiscoveryState
    suspend fun confirm(candidateKey: String)
    suspend fun sync(): XiaomiHistoryResult
    suspend fun history(offset: Int = 0): XiaomiHistoryView
    suspend fun disconnect()
}
