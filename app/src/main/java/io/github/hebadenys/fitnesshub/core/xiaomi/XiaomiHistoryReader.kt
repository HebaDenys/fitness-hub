package io.github.hebadenys.fitnesshub.core.xiaomi

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Authenticated read-only transport; tests use synthetic responses. */
internal fun interface XiaomiPageSource {
    suspend fun fetch(request: XiaomiScaleRequest): String
}

/** Atomically commit the page and its checkpoint, outside network work. */
internal fun interface XiaomiPageCommitter {
    suspend fun commit(scope: XiaomiScope, requestedBeforeMillis: Long, page: XiaomiScalePage)
}

internal sealed class XiaomiHistoryResult : PrivateXiaomiValue() {
    data class Completed(val pages: Int, val rowsReceived: Int) : XiaomiHistoryResult()
    data class Paused(val pages: Int, val rowsReceived: Int, val nextBeforeMillis: Long) : XiaomiHistoryResult()
}

/** Bounded sequential backfill. Never treats errors or a page budget as empty history. */
internal class XiaomiHistoryReader(
    private val source: XiaomiPageSource,
    private val committer: XiaomiPageCommitter,
    private val protocol: XiaomiScaleProtocol = XiaomiScaleProtocol()
) {
    suspend fun read(scope: XiaomiScope, beforeMillis: Long, maxPages: Int = 100): XiaomiHistoryResult {
        require(maxPages in 1..5_000) { "Invalid page budget" }
        var cursor = beforeMillis
        var rows = 0
        for (index in 0 until maxPages) {
            currentCoroutineContext().ensureActive()
            val request = XiaomiScaleRequest.history(scope, cursor)
            val response = try {
                source.fetch(request)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (failure: XiaomiAccessException) { throw failure // Preserve safe auth/rate-limit/session codes.
            } catch (_: Exception) { throw XiaomiProtocolException(XiaomiFailure.FETCH_FAILED) }
            currentCoroutineContext().ensureActive()
            val page = withContext(Dispatchers.Default) { protocol.parse(response, scope, cursor) }
            try {
                committer.commit(scope, cursor, page)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { throw XiaomiProtocolException(XiaomiFailure.COMMIT_FAILED) }
            rows += page.records.size
            val next = page.nextBeforeMillis ?: return XiaomiHistoryResult.Completed(index + 1, rows)
            cursor = next
        }
        return XiaomiHistoryResult.Paused(maxPages, rows, cursor)
    }
}
