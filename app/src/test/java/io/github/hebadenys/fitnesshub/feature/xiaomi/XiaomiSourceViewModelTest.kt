package io.github.hebadenys.fitnesshub.feature.xiaomi

import androidx.lifecycle.ViewModelStore
import io.github.hebadenys.fitnesshub.core.xiaomi.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

internal fun sourceOverview(block: XiaomiAccessFailure? = null, selected: Boolean = true, signedIn: Boolean = true): XiaomiSourceOverview {
    val scope = XiaomiScope("fixture", XiaomiRegion.DE, "yunmai.scales.ms103", "10001")
    return XiaomiSourceOverview(block, if (signedIn) XiaomiAccountInfo(scope.connectionId, scope.region, scope.loginUid) else null,
        null, if (selected) XiaomiBoundSource(scope, XiaomiSubject("10001", "11"), "fixture-scale") else null,
        1, 1, 1791287999000L, 1791287998000L, 1791287998000L, false)
}

internal class SourceGatewayFake : XiaomiSourceGateway {
    var current = sourceOverview()
    var syncCalls = 0
    var confirmations = 0
    var loginCalls = 0
    var syncAction: suspend () -> XiaomiHistoryResult = { XiaomiHistoryResult.Completed(1, 1) }
    val candidate = XiaomiSourceCandidate("fixture-key", XiaomiSubject("10001", "11"), "fixture-scale", "yunmai.scales.ms103", "Fixture", 1791287999000L)
    val record = XiaomiRecordDetail("fixture-hash", 1791287999000L, emptyList(), emptyList())
    override suspend fun overview() = current
    override suspend fun login(region: XiaomiRegion, username: String, password: CharArray) { loginCalls++ }
    override suspend fun discover(model: String, older: Boolean) = XiaomiDiscoveryState(listOf(candidate), null, 1, 1, 0)
    override suspend fun confirm(candidateKey: String) { confirmations++; current = sourceOverview() }
    override suspend fun sync(): XiaomiHistoryResult { syncCalls++; return syncAction() }
    override suspend fun history(offset: Int) = XiaomiHistoryView(if (offset == 0) listOf(record) else emptyList(), false)
    override suspend fun disconnect() { current = current.copy(account = null) }
}

@OptIn(ExperimentalCoroutinesApi::class)
class XiaomiSourceViewModelTest {
    private lateinit var holder: ViewModelStore
    private lateinit var gateway: SourceGatewayFake
    private lateinit var model: XiaomiSourceViewModel
    @BeforeEach fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        holder = ViewModelStore()
        gateway = SourceGatewayFake()
        model = XiaomiSourceViewModel(gateway)
        holder.put("test", model)
    }
    @AfterEach fun teardown() { holder.clear(); Dispatchers.resetMain() }

    @Test fun discoveryNeverAutomaticallySelectsOrConfirmsEvenOneCandidate() = runTest {
        gateway.current = sourceOverview(selected = false)
        model.refresh(); advanceUntilIdle()
        model.discover("yunmai.scales.ms103"); advanceUntilIdle()
        assertNull(model.state.value.selectedKey)
        model.confirmSelection(); advanceUntilIdle()
        assertEquals(0, gateway.confirmations)
        model.select("not-discovered")
        assertNull(model.state.value.selectedKey)
        model.select(gateway.candidate.key)
        assertEquals(0, gateway.confirmations)
        model.confirmSelection(); advanceUntilIdle()
        assertEquals(1, gateway.confirmations)
        assertEquals(XiaomiNotice.BOUND, model.state.value.notice)
        assertTrue(model.state.value.canSync)
    }

    @Test fun twoSyncTapsStartOnlyOneOperationAndCancelDoesNotReportSuccess() = runTest {
        val release = CompletableDeferred<Unit>()
        gateway.syncAction = { release.await(); XiaomiHistoryResult.Completed(1, 1) }
        model.refresh(); advanceUntilIdle()
        model.sync(); runCurrent(); model.sync(); runCurrent()
        assertEquals(1, gateway.syncCalls)
        model.cancel(); release.complete(Unit); advanceUntilIdle()
        assertEquals(XiaomiNotice.CANCELLED, model.state.value.notice)
        assertFalse(model.state.value.busy)
    }

    @Test fun nonCooperativeLateResponseCannotOverwriteCancelledState() = runTest {
        val release = CompletableDeferred<Unit>()
        gateway.syncAction = { withContext(NonCancellable) { release.await() }; XiaomiHistoryResult.Completed(1, 1) }
        model.refresh(); advanceUntilIdle()
        model.sync(); runCurrent()
        model.cancel(); release.complete(Unit); advanceUntilIdle()
        assertEquals(XiaomiNotice.CANCELLED, model.state.value.notice)
        assertEquals(XiaomiWork.IDLE, model.state.value.work)
    }

    @Test fun passwordIsClearedOnSuccessAndWhenBusyOperationRejectsLogin() = runTest {
        val key = "fixture-password".toCharArray()
        model.login(XiaomiRegion.DE, "fixture", key); advanceUntilIdle()
        assertTrue(key.all { it == '\u0000' })
        val release = CompletableDeferred<Unit>()
        gateway.syncAction = { release.await(); XiaomiHistoryResult.Completed(1, 1) }
        model.sync(); runCurrent()
        val second = "other-fixture".toCharArray()
        model.login(XiaomiRegion.DE, "fixture", second)
        assertTrue(second.all { it == '\u0000' })
        assertEquals(1, gateway.loginCalls)
        model.cancel(); release.complete(Unit); advanceUntilIdle()
    }

    @Test fun expiredSessionRefreshesConnectionLabelButKeepsSavedHistory() = runTest {
        gateway.syncAction = {
            gateway.current = gateway.current.copy(account = null, sessionProblem = XiaomiAccessFailure.SESSION_EXPIRED)
            throw XiaomiAccessException(XiaomiAccessFailure.SESSION_EXPIRED)
        }
        model.refresh(); advanceUntilIdle()
        model.sync(); advanceUntilIdle()
        assertFalse(model.state.value.canSync)
        assertEquals("SESSION_EXPIRED", model.state.value.errorCode)
        assertEquals(1, model.state.value.records.size)
    }

    @Test fun disconnectKeepsLocalRowsAndClearsSelection() = runTest {
        model.refresh(); advanceUntilIdle()
        model.disconnect(); advanceUntilIdle()
        assertNull(model.state.value.overview!!.account)
        assertNotNull(model.state.value.overview!!.binding)
        assertEquals(1, model.state.value.records.size)
        assertEquals(XiaomiNotice.SIGNED_OUT, model.state.value.notice)
    }

    @Test fun blockedSourceCannotBeSyncedAndUnknownErrorsAreSanitized() = runTest {
        gateway.current = sourceOverview(XiaomiAccessFailure.PRIVATE_SIGNING_REQUIRED)
        model.refresh(); advanceUntilIdle(); model.sync(); advanceUntilIdle()
        assertEquals(0, gateway.syncCalls)
        gateway.current = sourceOverview()
        gateway.syncAction = { error("fixture private token and health values") }
        model.refresh(); advanceUntilIdle(); model.sync(); advanceUntilIdle()
        assertEquals("UNEXPECTED_ERROR", model.state.value.errorCode)
        assertFalse(model.state.value.toString().contains("private token"))
    }

    @Test fun pageBudgetIsShownAsPausedNotFinished() = runTest {
        gateway.syncAction = { XiaomiHistoryResult.Paused(20, 400, 1791287000000) }
        model.refresh(); advanceUntilIdle(); model.sync(); advanceUntilIdle()
        assertEquals(XiaomiNotice.SYNC_PAUSED, model.state.value.notice)
    }
}
