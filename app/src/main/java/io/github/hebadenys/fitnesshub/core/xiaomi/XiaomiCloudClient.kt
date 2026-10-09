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

/** One instance per app: all authenticated operations share lifecycle and session ownership. */
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

    fun networkBlock(): XiaomiAccessFailure? = try {
        gate.requireAllowed(); null
    } catch (failure: XiaomiAccessException) { failure.reason }

    /** Reading connection status never makes a network request or reveals service secrets. */
    suspend fun accountInfo(): XiaomiAccountInfo? = lock.withLock {
        if (sessionBlocked) return@withLock null
        val session = sessions.load() ?: return@withLock null
        checkExpiry(session)
        XiaomiAccountInfo(session.connectionId, session.region, session.userId)
    }

    suspend fun login(connectionId: String, region: XiaomiRegion, username: String, password: CharArray,
        expectedUserId: String? = null, captcha: XiaomiCaptchaResponder? = null): Unit = try {
        operation { epoch ->
            loginLock.withLock {
                readLock.withLock {
                    assertCurrent(epoch)
                    gate.requireAllowed()
                    val session = XiaomiAuthentication(http, clock, captcha = captcha).login(connectionId, region, username, password)
                    lock.withLock {
                        checkCurrent(epoch)
                        if (expectedUserId != null && session.userId != expectedUserId) {
                            accessFailure(XiaomiAccessFailure.SESSION_SCOPE_MISMATCH)
                        }
                        val previous = if (sessionBlocked) null else try {
                            sessions.load()
                        } catch (failure: XiaomiAccessException) {
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
        }
    } finally { password.fill('\u0000') }

    suspend fun discover(model: String, beforeMillis: Long): XiaomiDiscoveryPage = operation { epoch ->
        readLock.withLock {
            gate.requireAllowed()
            if (!XiaomiModels.valid(model)) accessFailure(XiaomiAccessFailure.INVALID_INPUT)
            val session = lock.withLock { checkCurrent(epoch); requireSession() }
            val scope = XiaomiScope(session.connectionId, session.region, model, session.userId)
            withSafeErrors(epoch) {
                val source = XiaomiAuthenticatedPageSource(http, session, scope, clock)
                val page = XiaomiDiscoveryReader(clock).read(source, scope, beforeMillis)
                assertCurrent(epoch)
                page
            }
        }
    }

    /** Selection is checked again against the currently usable account, not a stale screen. */
    suspend fun confirmSelection(archive: RoomXiaomiArchive, scope: XiaomiScope, subject: XiaomiSubject, deviceId: String) =
        operation { epoch ->
            lock.withLock {
                gate.requireAllowed()
                checkCurrent(epoch)
                requireSession(scope)
                archive.confirmBinding(scope, subject, deviceId)
                Unit
            }
        }

    suspend fun readHistory(archive: RoomXiaomiArchive, scope: XiaomiScope, beforeMillis: Long, maxPages: Int = 100): XiaomiHistoryResult =
        operation { epoch ->
            readLock.withLock {
                gate.requireAllowed()
                val session = lock.withLock { checkCurrent(epoch); requireSession(scope) }
                val source = XiaomiAuthenticatedPageSource(http, session, scope, clock)
                val guarded = XiaomiPageSource { request ->
                    assertCurrent(epoch)
                    val response = source.fetch(request)
                    assertCurrent(epoch)
                    response
                }
                withSafeErrors(epoch) { archive.read(guarded, scope, beforeMillis, maxPages) }
            }
        }

    /** Call only while holding lock. Never regenerate or silently switch a service session. */
    private suspend fun requireSession(scope: XiaomiScope? = null): XiaomiSession {
        if (sessionBlocked) accessFailure(XiaomiAccessFailure.SESSION_MISSING)
        val session = sessions.load() ?: accessFailure(XiaomiAccessFailure.SESSION_MISSING)
        if (scope != null && !session.matches(scope)) accessFailure(XiaomiAccessFailure.SESSION_SCOPE_MISMATCH)
        checkExpiry(session)
        return session
    }

    private suspend fun checkExpiry(session: XiaomiSession) {
        if (!session.usableAt(clock.millis())) {
            sessionBlocked = true
            sessions.clear()
            accessFailure(XiaomiAccessFailure.SESSION_EXPIRED)
        }
    }

    private suspend fun <T> withSafeErrors(epoch: Long, block: suspend () -> T): T = try {
        block()
    } catch (failure: XiaomiAccessException) {
        if (failure.reason == XiaomiAccessFailure.AUTH_REQUIRED) lock.withLock {
            if (generation == epoch) { sessionBlocked = true; sessions.clear() }
        }
        throw failure
    }

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

/** Owner-approved publisher trust; public TEST/debug builds remain blocked. */
internal class XiaomiCloudRuntime private constructor(val client: XiaomiCloudClient, val archive: RoomXiaomiArchive) {
    companion object {
        fun create(context: Context, database: HealthDatabase): XiaomiCloudRuntime {
            val gate = XiaomiPrivateSigningGate(context)
            val store = XiaomiProtectedSessionStore(XiaomiAtomicSessionFile(context), XiaomiSessionCipher(XiaomiAndroidSessionKeys()))
            return XiaomiCloudRuntime(XiaomiCloudClient(XiaomiHttpsTransport(gate), store, gate), RoomXiaomiArchive(database))
        }
    }
}
