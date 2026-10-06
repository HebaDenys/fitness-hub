package io.github.hebadenys.fitnesshub.core.xiaomi

import android.content.Context
import io.github.hebadenys.fitnesshub.core.database.HealthDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Clock

/** Coordinates protocol, authenticated HTTP, protected session and Room committer. */
internal class XiaomiCloudClient(
    private val http: XiaomiHttpExchange,
    private val sessions: XiaomiSessionStore,
    private val gate: XiaomiNetworkGate,
    private val clock: Clock = Clock.systemUTC()
) {
    private val lock = Mutex()
    private val loginLock = Mutex()
    private val readLock = Mutex()
    private val operations = mutableSetOf<Job>()
    private var generation = 0L
    private var sessionBlocked = false

    suspend fun login(connectionId: String, region: XiaomiRegion, username: String, password: CharArray): Unit = try {
        operation { epoch ->
            loginLock.withLock {
                assertCurrent(epoch)
                gate.requireAllowed()
                val session = XiaomiAuthentication(http, clock).login(connectionId, region, username, password)
                lock.withLock {
                    checkCurrent(epoch)
                    val previous = if (sessionBlocked) null else try {
                        sessions.load()
                    } catch (failure: XiaomiAccessException) {
                        // A verified fresh login can replace an unreadable, already discarded session.
                        if (failure.reason != XiaomiAccessFailure.SESSION_UNREADABLE) throw failure
                        null
                    }
                    if (previous != null && (previous.userId != session.userId || previous.region != region || previous.connectionId != connectionId)) {
                        accessFailure(XiaomiAccessFailure.SESSION_SCOPE_MISMATCH)
                    }
                    sessions.save(session)
                    sessionBlocked = false
                }
            }
        }
    } finally { password.fill('\u0000') }

    suspend fun readHistory(archive: RoomXiaomiArchive, scope: XiaomiScope, beforeMillis: Long, maxPages: Int = 100): XiaomiHistoryResult =
        operation { epoch ->
            readLock.withLock {
                gate.requireAllowed()
                val session = lock.withLock {
                    checkCurrent(epoch)
                    if (sessionBlocked) accessFailure(XiaomiAccessFailure.SESSION_MISSING)
                    val current = sessions.load() ?: accessFailure(XiaomiAccessFailure.SESSION_MISSING)
                    if (!current.matches(scope)) accessFailure(XiaomiAccessFailure.SESSION_SCOPE_MISMATCH)
                    if (!current.usableAt(clock.millis())) {
                        sessionBlocked = true
                        sessions.clear()
                        accessFailure(XiaomiAccessFailure.SESSION_EXPIRED)
                    }
                    current
                }
                val source = XiaomiAuthenticatedPageSource(http, session, scope, clock)
                val guarded = XiaomiPageSource { request ->
                    assertCurrent(epoch)
                    val response = source.fetch(request)
                    assertCurrent(epoch)
                    response
                }
                try {
                    archive.read(guarded, scope, beforeMillis, maxPages)
                } catch (failure: XiaomiAccessException) {
                    if (failure.reason == XiaomiAccessFailure.AUTH_REQUIRED) {
                        lock.withLock {
                            if (generation == epoch) {
                                sessionBlocked = true
                                sessions.clear()
                            }
                        }
                    }
                    throw failure
                }
            }
        }

    /** Cancel running AND queued operations; no database mutation or remote deletion. */
    suspend fun disconnect() = withContext(NonCancellable) {
        lock.withLock {
            generation++
            sessionBlocked = true
            operations.forEach { it.cancel(CancellationException("Xiaomi session disconnected")) }
            sessions.clear()
        }
    }

    private suspend fun assertCurrent(epoch: Long) = lock.withLock { checkCurrent(epoch) }
    private suspend fun checkCurrent(epoch: Long) {
        currentCoroutineContext().ensureActive()
        if (generation != epoch) accessFailure(XiaomiAccessFailure.SESSION_CHANGED)
    }

    private suspend fun <T> operation(block: suspend (Long) -> T): T = coroutineScope {
        val job = currentCoroutineContext().job
        val epoch = lock.withLock { operations.add(job); generation }
        try { block(epoch) } finally {
            withContext(NonCancellable) { lock.withLock { operations.remove(job) } }
        }
    }
}

/**
 * Production assembly stays blocked until the approved private-signing policy exists.
 * Reuse one runtime per app. No UI, build flag or silent default enables real credentials.
 * Tests inject synthetic transports/gates directly, never a real account.
 */
internal class XiaomiCloudRuntime private constructor(val client: XiaomiCloudClient, val archive: RoomXiaomiArchive) {
    companion object {
        fun create(context: Context, database: HealthDatabase): XiaomiCloudRuntime {
            val gate = XiaomiNetworkGate.AwaitingPrivateSigning
            val store = XiaomiProtectedSessionStore(XiaomiAtomicSessionFile(context), XiaomiSessionCipher(XiaomiAndroidSessionKeys()))
            return XiaomiCloudRuntime(XiaomiCloudClient(XiaomiHttpsTransport(gate), store, gate), RoomXiaomiArchive(database))
        }
    }
}
