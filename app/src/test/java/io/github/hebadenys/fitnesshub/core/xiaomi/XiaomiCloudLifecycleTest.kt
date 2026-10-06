package io.github.hebadenys.fitnesshub.core.xiaomi

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class XiaomiCloudLifecycleTest {
    private val f get() = XiaomiNetworkFixtures

    @Test fun disconnectCancelsQueuedLoginAndCannotBeUndoneByLateResponse() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val store = TestXiaomiSessionStore()
        var calls = 0
        val http = XiaomiHttpExchange {
            calls++
            withContext(NonCancellable) { started.complete(Unit); release.await() }
            f.start()
        }
        val client = XiaomiCloudClient(http, store, f.allow, f.clock)
        val firstPassword = "first-fixture".toCharArray()
        val secondPassword = "second-fixture".toCharArray()
        val first = async(start = CoroutineStart.UNDISPATCHED) { client.login("fixture", XiaomiRegion.DE, "fixture", firstPassword) }
        started.await()
        val second = async(start = CoroutineStart.UNDISPATCHED) { client.login("fixture", XiaomiRegion.DE, "fixture", secondPassword) }
        client.disconnect()
        release.complete(Unit)
        first.join(); second.join()
        assertTrue(first.isCancelled)
        assertTrue(second.isCancelled)
        assertEquals(1, calls)
        assertNull(store.session)
        assertTrue(firstPassword.all { it == '\u0000' })
        assertTrue(secondPassword.all { it == '\u0000' })
    }

    @Test fun changingAccountDoesNotSilentlyReplaceExistingSession() = runTest {
        val store = TestXiaomiSessionStore().apply { session = f.session() }
        val http = XiaomiHttpExchange { request -> when (request.endpoint) {
            XiaomiEndpoint.LOGIN_START -> f.start()
            XiaomiEndpoint.LOGIN_PASSWORD -> f.authenticated()
            XiaomiEndpoint.SERVICE_TICKET -> f.ticket()
            else -> error("not expected")
        } }
        val client = XiaomiCloudClient(http, store, f.allow, f.clock)
        expectAccess(XiaomiAccessFailure.SESSION_SCOPE_MISMATCH) {
            client.login("different-connection", XiaomiRegion.US, "fixture", "fixture-password".toCharArray())
        }
        assertEquals(f.session(), store.session)
    }

    @Test fun localClockRollbackOrExpiryCannotExtendSessionForever() {
        val session = f.session()
        assertTrue(session.usableAt(session.issuedAtMillis))
        assertFalse(session.usableAt(session.validUntilMillis))
        assertFalse(session.usableAt(session.issuedAtMillis - 300_001))
    }
}
