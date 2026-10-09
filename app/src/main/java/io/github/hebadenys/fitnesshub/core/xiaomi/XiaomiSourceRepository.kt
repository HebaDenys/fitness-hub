package io.github.hebadenys.fitnesshub.core.xiaomi

import androidx.room.withTransaction
import io.github.hebadenys.fitnesshub.core.database.HealthDatabase
import io.github.hebadenys.fitnesshub.core.xiaomi.storage.XiaomiBindingEntity
import io.github.hebadenys.fitnesshub.core.xiaomi.storage.XiaomiSnapshotEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Clock
import java.util.UUID

/** UI-facing singleton. Discovery is ephemeral and never writes unselected people into Room. */
internal class XiaomiSourceRepository(
    private val database: HealthDatabase,
    private val client: XiaomiCloudClient,
    private val archive: RoomXiaomiArchive,
    private val clock: Clock = Clock.systemUTC()
) : XiaomiSourceGateway {
    private val lock = Mutex()
    private var generation = 0L
    private var discovery: XiaomiDiscoveryState? = null
    private var discoveredScope: XiaomiScope? = null
    private val dao get() = database.xiaomiArchiveDao()

    override suspend fun overview(): XiaomiSourceOverview {
        var sessionProblem: XiaomiAccessFailure? = null
        val account = try { client.accountInfo() } catch (failure: XiaomiAccessException) {
            sessionProblem = failure.reason; null
        }
        val block = client.networkBlock()
        return database.withTransaction {
            val binding = dao.binding()
            val checkpoint = binding?.let { dao.checkpoint(it.connectionId) }
            val range = binding?.let { dao.range(it.connectionId) }
            XiaomiSourceOverview(block, account, sessionProblem, binding?.toSource(), dao.snapshotCount(),
                checkpoint?.committedPages ?: 0,
                checkpoint?.takeIf { it.committedPages > 0 }?.updatedAtMillis,
                range?.oldest, range?.newest, checkpoint?.nextBeforeMillis != null)
        }
    }

    override suspend fun login(region: XiaomiRegion, username: String, password: CharArray) =
        loginInternal(region, username, password, null)

    override suspend fun loginWithCaptcha(region: XiaomiRegion, username: String, password: CharArray,
        captcha: XiaomiCaptchaResponder) = loginInternal(region, username, password, captcha)

    private suspend fun loginInternal(region: XiaomiRegion, username: String, password: CharArray,
        captcha: XiaomiCaptchaResponder?) {
        try {
            lock.withLock { generation++; discovery = null; discoveredScope = null }
            val binding = dao.binding()
            if (binding != null && binding.region != region.wireName) accessFailure(XiaomiAccessFailure.SESSION_SCOPE_MISMATCH)
            val existing = try { client.accountInfo() } catch (failure: XiaomiAccessException) {
                if (failure.reason !in setOf(XiaomiAccessFailure.SESSION_EXPIRED, XiaomiAccessFailure.SESSION_UNREADABLE)) throw failure
                null
            }
            val connection = binding?.connectionId ?: existing?.connectionId ?: UUID.randomUUID().toString()
            client.login(connection, region, username, password, expectedUserId = binding?.loginUid, captcha = captcha)
        } finally { password.fill('\u0000') }
    }

    override suspend fun discover(model: String, older: Boolean): XiaomiDiscoveryState {
        if (!XiaomiModels.valid(model)) accessFailure(XiaomiAccessFailure.INVALID_INPUT)
        val request = lock.withLock {
            if (!older) { generation++; discovery = null; discoveredScope = null }
            val previous = discovery
            if (older && (previous?.nextBeforeMillis == null || discoveredScope?.model != model)) {
                accessFailure(XiaomiAccessFailure.INVALID_INPUT)
            }
            Triple(generation, previous, if (older) previous!!.nextBeforeMillis!! else clock.millis())
        }
        val page = client.discover(model, request.third)
        currentCoroutineContext().ensureActive()
        return lock.withLock {
            if (generation != request.first || (older && discoveredScope != page.scope)) accessFailure(XiaomiAccessFailure.SESSION_CHANGED)
            val previous = request.second
            if ((previous?.pagesScanned ?: 0) >= 250) throw XiaomiProtocolException(XiaomiFailure.LIMIT_EXCEEDED)
            val candidates = (previous?.candidates.orEmpty() + page.candidates).groupBy { it.key }
                .values.map { rows -> rows.maxBy { it.latestAtMillis } }.sortedBy { it.key }
            if (candidates.size > 512) throw XiaomiProtocolException(XiaomiFailure.LIMIT_EXCEEDED)
            discoveredScope = page.scope
            XiaomiDiscoveryState(candidates, page.nextBeforeMillis, (previous?.pagesScanned ?: 0) + 1,
                (previous?.rowsScanned ?: 0) + page.rowsScanned,
                (previous?.unresolvedRows ?: 0) + page.unresolvedRows).also { discovery = it }
        }
    }

    override suspend fun confirm(candidateKey: String) {
        lock.withLock {
            val candidate = discovery?.candidates?.singleOrNull { it.key == candidateKey }
                ?: accessFailure(XiaomiAccessFailure.INVALID_INPUT)
            val scope = discoveredScope ?: accessFailure(XiaomiAccessFailure.SESSION_CHANGED)
            client.confirmSelection(archive, scope, candidate.subject, candidate.deviceId)
            discovery = null
            discoveredScope = null
        }
    }

    override suspend fun sync(): XiaomiHistoryResult {
        val binding = dao.binding() ?: throw XiaomiArchiveException(ArchiveFailure.BINDING_REQUIRED)
        // Each click has a bounded page budget; incomplete history resumes from its durable checkpoint.
        return client.readHistory(archive, binding.toSource().scope, clock.millis(), maxPages = 20)
    }

    override suspend fun history(offset: Int): XiaomiHistoryView {
        require(offset >= 0) { "Invalid history offset" }
        val rows = database.withTransaction {
            val binding = dao.binding() ?: return@withTransaction emptyList()
            dao.snapshotPage(binding.connectionId, HISTORY_PAGE_SIZE + 1, offset)
        }
        return withContext(Dispatchers.Default) {
            XiaomiHistoryView(rows.take(HISTORY_PAGE_SIZE).map(::detail), rows.size > HISTORY_PAGE_SIZE)
        }
    }

    override suspend fun disconnect() {
        lock.withLock { generation++; discovery = null; discoveredScope = null }
        client.disconnect() // Does not remove the immutable binding or any health records.
    }

    private fun XiaomiBindingEntity.toSource(): XiaomiBoundSource = XiaomiBoundSource(
        XiaomiScope(connectionId, XiaomiRegion.entries.single { it.wireName == region }, model, loginUid),
        XiaomiSubject(subjectUid, subjectAccountId), deviceId)

    private fun detail(row: XiaomiSnapshotEntity): XiaomiRecordDetail = try {
        if (XiaomiSnapshotCodec.sha256(row.snapshotJson) != row.contentHash) throw IllegalArgumentException()
        val fields = (XiaomiJsonReader(row.snapshotJson).read() as XiaomiJson.Object).fields
        if (fields["formatVersion"] != XiaomiJson.Number("1")) throw IllegalArgumentException()
        fun issues(value: XiaomiJson?): List<String> = (value as? XiaomiJson.Array)?.elements.orEmpty()
            .mapNotNull { (it as? XiaomiJson.Text)?.value }.filter { name -> XiaomiIssue.entries.any { it.name == name } }
        val metrics = (fields["metrics"] as XiaomiJson.Object).fields.values.map { value ->
            val metric = (value as XiaomiJson.Object).fields
            val path = (metric["vendorPath"] as XiaomiJson.Text).value
            require(path.matches(Regex("[A-Za-z0-9_.]{1,80}")))
            val normalized = metric["value"] as? XiaomiJson.Number
            val raw = metric["raw"]
            val display = normalized?.literal ?: when (raw) {
                is XiaomiJson.Number -> raw.literal
                is XiaomiJson.Text -> raw.value.takeIf { it.length <= 128 && it.matches(Regex("[-+0-9.eE]+|NaN|-?Infinity")) } ?: "—"
                else -> "—"
            }
            XiaomiMetricDetail(path, display, XiaomiUnit.valueOf((metric["unit"] as XiaomiJson.Text).value),
                XiaomiMethod.valueOf((metric["method"] as XiaomiJson.Text).value), issues(metric["issues"]))
        }
        XiaomiRecordDetail(row.contentHash, row.measuredAtMillis, metrics, issues(fields["issues"]))
    } catch (_: Exception) {
        XiaomiRecordDetail(row.contentHash, row.measuredAtMillis, emptyList(), emptyList(), unreadable = true)
    }

    companion object { const val HISTORY_PAGE_SIZE = 20 }
}
