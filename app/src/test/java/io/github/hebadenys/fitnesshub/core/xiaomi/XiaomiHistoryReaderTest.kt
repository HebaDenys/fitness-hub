package io.github.hebadenys.fitnesshub.core.xiaomi

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class XiaomiHistoryReaderTest {
    private val scope = XiaomiScope("fixture-connection", XiaomiRegion.DE, "yunmai.scales.ms104", "900001")
    private fun full(before: Long) = (1..20).joinToString(",", "[", "]") {
        """{"model":"yunmai.scales.ms104","uid":900001,"accountId":7,"createTime":${before - it},"data":{"weight":84.25},"fromSource":2}"""
    }

    @Test fun everyPageCommitsBeforeNextRequestAndTerminalPageIsExplicit() = runBlocking {
        val events = mutableListOf<String>()
        val start = 1750000000000L
        var call = 0
        val reader = XiaomiHistoryReader(
            { request -> events += "fetch"; check(request.beforeMillis == start - call * 20L); if (call++ == 0) full(start) else "[]" },
            { _, requested, page -> events += "commit"; check(requested == if (page.records.isEmpty()) start - 20 else start) }
        )
        val result = reader.read(scope, start)
        check(result == XiaomiHistoryResult.Completed(2, 20))
        check(events == listOf("fetch", "commit", "fetch", "commit"))
    }

    @Test fun boundedRunReturnsResumeCursorNotFalseCompletion() = runBlocking {
        val result = XiaomiHistoryReader({ full(it.beforeMillis) }, { _, _, _ -> })
            .read(scope, 1750000000000L, maxPages = 1)
        check(result == XiaomiHistoryResult.Paused(1, 20, 1749999999980L))
    }

    @Test fun persistenceFailureCannotFetchAnotherPageOrAdvance() = runBlocking {
        var requests = 0
        val error = runCatching {
            XiaomiHistoryReader({ requests++; full(it.beforeMillis) }, { _, _, _ -> error("PRIVATE_SENTINEL") })
                .read(scope, 1750000000000L)
        }.exceptionOrNull()
        check(error is XiaomiProtocolException && error.failure == XiaomiFailure.COMMIT_FAILED)
        check(error.cause == null && "PRIVATE_SENTINEL" !in error.toString() && requests == 1)
    }

    @Test fun networkFailureIsNotAnEmptyResultAndIsSanitized() = runBlocking {
        var commits = 0
        val error = runCatching {
            XiaomiHistoryReader({ error("PRIVATE_SENTINEL") }, { _, _, _ -> commits++ }).read(scope, 1750000000000L)
        }.exceptionOrNull()
        check(error is XiaomiProtocolException && error.failure == XiaomiFailure.FETCH_FAILED)
        check(error.cause == null && commits == 0)
    }

    @Test fun cancellationFromEitherBoundaryPropagates() = runBlocking {
        val cancelled = CancellationException("fixture cancellation")
        check(runCatching {
            XiaomiHistoryReader({ throw cancelled }, { _, _, _ -> }).read(scope, 1750000000000L)
        }.exceptionOrNull() === cancelled)
        check(runCatching {
            XiaomiHistoryReader({ "[]" }, { _, _, _ -> throw cancelled }).read(scope, 1750000000000L)
        }.exceptionOrNull() === cancelled)
    }

    @Test fun repeatedCursorNeverReachesCommitter() = runBlocking {
        val start = 1750000000000L
        val page = List(20) { """{"createTime":$start}""" }.joinToString(",", "[", "]")
        var commits = 0
        val error = runCatching {
            XiaomiHistoryReader({ page }, { _, _, _ -> commits++ }).read(scope, start)
        }.exceptionOrNull()
        check(error is XiaomiProtocolException && error.failure == XiaomiFailure.STALLED_CURSOR && commits == 0)
    }
}
