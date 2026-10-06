package io.github.hebadenys.fitnesshub.core.xiaomi

import android.app.Application
import androidx.room.Room
import io.github.hebadenys.fitnesshub.core.database.HealthDatabase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.io.File

/** Real generated Room/SQLite + synthetic HTTP, NOT a real Xiaomi account or Android hardware. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class XiaomiCloudRoomTest {
    private lateinit var database: HealthDatabase
    private val f get() = XiaomiNetworkFixtures
    private lateinit var archive: RoomXiaomiArchive
    private lateinit var store: TestXiaomiSessionStore

    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), HealthDatabase::class.java)
            .allowMainThreadQueries().build()
        archive = RoomXiaomiArchive(database, f.clock)
        store = TestXiaomiSessionStore()
    }
    @After fun close() { database.close() }

    private fun history(): String = """{"code":0,"result":[{"model":"yunmai.scales.ms103","fromSource":1,"uid":10001,"accountId":11,"did":"fixture-device","sn":"fixture-serial","dataVersion":1,"createTime":${f.clock.millis() - 1000},"data":{"weight":73.5,"bodyFat":18.0}}]}"""
    private fun snapshots(): Long = database.openHelper.readableDatabase.query("SELECT count(*) FROM xiaomi_snapshots").use {
        assertTrue(it.moveToFirst()); it.getLong(0)
    }
    private fun loginHttp(request: XiaomiHttpRequest): XiaomiHttpResponse = when (request.endpoint) {
        XiaomiEndpoint.LOGIN_START -> f.start()
        XiaomiEndpoint.LOGIN_PASSWORD -> f.authenticated()
        XiaomiEndpoint.SERVICE_TICKET -> f.ticket()
        XiaomiEndpoint.SCALE_HISTORY -> f.encrypted(request, history())
    }

    @Test fun loginThenAuthenticatedReadThenReplayUsesSameRoomArchiveWithoutDuplicates() = runBlocking {
        val client = XiaomiCloudClient(XiaomiHttpExchange(::loginHttp), store, f.allow, f.clock)
        client.login(f.scope.connectionId, f.scope.region, "fixture@example.test", "fixture-password".toCharArray())
        archive.confirmBinding(f.scope, XiaomiSubject("10001", "11"), "fixture-device")
        client.readHistory(archive, f.scope, f.clock.millis())
        client.readHistory(archive, f.scope, f.clock.millis())
        assertEquals(1L, snapshots())
        assertNotNull(database.xiaomiArchiveDao().binding())
        assertEquals(null, database.xiaomiArchiveDao().checkpoint(f.scope.connectionId)!!.nextBeforeMillis)
    }

    @Test fun disconnectCancelsInflightReadButKeepsPreviouslyCommittedMeasurements() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var block = false
        val http = XiaomiHttpExchange { request ->
            if (block && request.endpoint == XiaomiEndpoint.SCALE_HISTORY) {
                started.complete(Unit)
                release.await()
            }
            loginHttp(request)
        }
        val client = XiaomiCloudClient(http, store, f.allow, f.clock)
        client.login(f.scope.connectionId, f.scope.region, "fixture", "fixture-password".toCharArray())
        archive.confirmBinding(f.scope, XiaomiSubject("10001", "11"), "fixture-device")
        client.readHistory(archive, f.scope, f.clock.millis())
        block = true
        val pending = async(Dispatchers.Default) { client.readHistory(archive, f.scope, f.clock.millis()) }
        withTimeout(10_000) { started.await() }
        client.disconnect()
        pending.join()
        assertTrue(pending.isCancelled)
        assertNull(store.load())
        assertEquals(1L, snapshots())
        expectAccess(XiaomiAccessFailure.SESSION_MISSING) { client.readHistory(archive, f.scope, f.clock.millis()) }
    }

    @Test fun unauthorizedHttpClearsSessionWithoutClearingBinding() = runBlocking {
        store.save(f.session())
        archive.confirmBinding(f.scope, XiaomiSubject("10001", "11"), "fixture-device")
        val client = XiaomiCloudClient(XiaomiHttpExchange { XiaomiHttpResponse(401) }, store, f.allow, f.clock)
        expectAccess(XiaomiAccessFailure.AUTH_REQUIRED) { client.readHistory(archive, f.scope, f.clock.millis()) }
        assertNull(store.load())
        assertNotNull(database.xiaomiArchiveDao().binding())
        assertEquals(0L, snapshots())
    }

    @Test fun productionAssemblyRefusesRealLoginAndWipesPasswordWithoutNetwork() = runBlocking {
        val runtime = XiaomiCloudRuntime.create(RuntimeEnvironment.getApplication(), database)
        val password = "fixture-only".toCharArray()
        expectAccess(XiaomiAccessFailure.PRIVATE_SIGNING_REQUIRED) { runtime.client.login("fixture", XiaomiRegion.DE, "fixture", password) }
        assertTrue(password.all { it == '\u0000' })
        assertEquals(0L, snapshots())
    }

    @Test fun atomicSessionFileLivesOutsideBackupAndRoundTripsWithInjectedAesKey() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val file = XiaomiAtomicSessionFile(context)
        file.delete()
        val keys = TestXiaomiKeys()
        val sessions = XiaomiProtectedSessionStore(file, XiaomiSessionCipher(keys))
        try {
            sessions.save(f.session())
            val path = File(context.noBackupFilesDir, "xiaomi-session-v1.bin")
            assertTrue(path.exists())
            assertFalse(String(path.readBytes(), Charsets.ISO_8859_1).contains("fixture-service-token"))
            assertEquals(f.session(), XiaomiProtectedSessionStore(XiaomiAtomicSessionFile(context), XiaomiSessionCipher(keys)).load())
            sessions.clear()
            assertFalse(path.exists())
        } finally { file.delete() }
    }
}
