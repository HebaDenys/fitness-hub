package io.github.hebadenys.fitnesshub.core.xiaomi

import android.app.Application
import androidx.room.Room
import io.github.hebadenys.fitnesshub.core.database.HealthDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class XiaomiSourceRepositoryTest {
    private val f get() = XiaomiNetworkFixtures
    private lateinit var db: HealthDatabase
    private lateinit var store: TestXiaomiSessionStore
    private lateinit var client: XiaomiCloudClient
    private lateinit var repository: XiaomiSourceRepository
    private var rows = emptyList<XiaomiJson.Object>()
    private var networkCalls = 0

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), HealthDatabase::class.java).allowMainThreadQueries().build()
        store = TestXiaomiSessionStore().apply { session = f.session() }
        val http = XiaomiHttpExchange { request ->
            networkCalls++
            val form = f.form(request.form!!)
            val key = XiaomiWireCrypto.signedNonce(XiaomiWireCrypto.unbase64(f.security), XiaomiWireCrypto.unbase64(form.getValue("_nonce")))
            val data = XiaomiJsonReader(String(XiaomiWireCrypto.crypt(key, XiaomiWireCrypto.unbase64(form.getValue("data"))))).read() as XiaomiJson.Object
            val before = (data.fields["beginTime"] as XiaomiJson.Number).literal.toLong()
            val page = rows.filter { (it.fields["createTime"] as XiaomiJson.Number).literal.toLong() < before }.take(20)
            f.encrypted(request, sourceResponse(page))
        }
        client = XiaomiCloudClient(http, store, f.allow, f.clock)
        repository = XiaomiSourceRepository(db, client, RoomXiaomiArchive(db, f.clock), f.clock)
    }
    @After fun close() { db.close() }

    private suspend fun choose(account: String = "11") {
        val found = repository.discover(f.scope.model)
        repository.confirm(found.candidates.single { it.subject.accountId == account }.key)
    }

    @Test fun discoveryDoesNotPersistOtherPeopleAndExplicitSelectionImportsOnlyOwner() = runBlocking {
        rows = listOf(sourceRow(account = "11"), sourceRow(account = "12", at = f.clock.millis() - 2000),
            sourceRow(account = "11", at = f.clock.millis() - 3000))
        val found = repository.discover(f.scope.model)
        assertEquals(2, found.candidates.size)
        assertEquals(0, db.xiaomiArchiveDao().snapshotCount())
        assertNull(db.xiaomiArchiveDao().binding())
        repository.confirm(found.candidates.single { it.subject.accountId == "11" }.key)
        assertEquals(0, db.xiaomiArchiveDao().snapshotCount())
        repository.sync()
        assertEquals(2, repository.overview().snapshotCount)
        assertEquals("11", db.xiaomiArchiveDao().binding()!!.subjectAccountId)
        assertEquals(1L, db.xiaomiArchiveDao().checkpoint(f.scope.connectionId)!!.otherRows)
    }

    @Test fun arbitraryOrStaleSelectionCannotCreateBinding() = runBlocking {
        rows = listOf(sourceRow())
        expectAccess(XiaomiAccessFailure.INVALID_INPUT) { repository.confirm("made-up-key") }
        val key = repository.discover(f.scope.model).candidates.single().key
        repository.disconnect()
        expectAccess(XiaomiAccessFailure.INVALID_INPUT) { repository.confirm(key) }
        assertNull(db.xiaomiArchiveDao().binding())
    }

    @Test fun selectedArchiveDoesNotChangeToSecondPersonWithSameDisplayName() = runBlocking {
        rows = listOf(sourceRow(account = "11"), sourceRow(account = "12"))
        choose()
        val other = repository.discover(f.scope.model).candidates.single { it.subject.accountId == "12" }
        var conflict = false
        try { repository.confirm(other.key) } catch (error: XiaomiArchiveException) { conflict = error.reason == ArchiveFailure.BINDING_CONFLICT }
        assertTrue(conflict)
        assertEquals("11", db.xiaomiArchiveDao().binding()!!.subjectAccountId)
    }

    @Test fun disconnectAndRepositoryRecreationKeepLocalHistoryReadableWithoutNetwork() = runBlocking {
        rows = listOf(sourceRow())
        choose(); repository.sync(); repository.disconnect()
        val calls = networkCalls
        val reopened = XiaomiSourceRepository(db, client, RoomXiaomiArchive(db, f.clock), f.clock)
        val overview = reopened.overview()
        assertNull(overview.account)
        assertNotNull(overview.binding)
        assertEquals(1, overview.snapshotCount)
        assertEquals(1, reopened.history().records.size)
        assertEquals(calls, networkCalls)
    }

    @Test fun nativeHistoryPaginationIncludesMoreThanTwentyRecordsAndRetainsMetricMethods() = runBlocking {
        rows = (1..45).map { sourceRow(at = f.clock.millis() - it * 1000) }
        choose(); repository.sync()
        val first = repository.history()
        val second = repository.history(20)
        val last = repository.history(40)
        assertEquals(20, first.records.size)
        assertEquals(20, second.records.size)
        assertEquals(5, last.records.size)
        assertTrue(first.hasMore); assertFalse(last.hasMore)
        assertEquals(45, (first.records + second.records + last.records).map { it.hash }.distinct().size)
        val fat = first.records.first().metrics.single { it.path == "bfp" }
        assertEquals(XiaomiMethod.VENDOR_ESTIMATE, fat.method)
        assertEquals(XiaomiUnit.PERCENT, fat.unit)
        assertNotNull(repository.overview().oldestAtMillis)
        assertNotNull(repository.overview().committedAtMillis)
    }

    @Test fun repeatedManualSyncDoesNotIncreaseSnapshotCount() = runBlocking {
        rows = listOf(sourceRow())
        choose(); repository.sync(); repository.sync()
        assertEquals(1, repository.overview().snapshotCount)
    }

    @Test fun gateBlocksNetworkButDoesNotHideSavedArchive() = runBlocking {
        rows = listOf(sourceRow())
        choose(); repository.sync()
        val runtime = XiaomiCloudRuntime.create(RuntimeEnvironment.getApplication(), db)
        val gated = XiaomiSourceRepository(db, runtime.client, runtime.archive, f.clock)
        assertEquals(XiaomiAccessFailure.PRIVATE_SIGNING_REQUIRED, gated.overview().block)
        assertEquals(1, gated.history().records.size)
        expectAccess(XiaomiAccessFailure.PRIVATE_SIGNING_REQUIRED) { gated.discover(f.scope.model) }
    }

    @Test fun loginRegionCannotChangeOwnerAndWipesPasswordBeforeNetwork() = runBlocking {
        rows = listOf(sourceRow())
        choose()
        val before = networkCalls
        val key = "synthetic-secret".toCharArray()
        expectAccess(XiaomiAccessFailure.SESSION_SCOPE_MISMATCH) { repository.login(XiaomiRegion.US, "fixture", key) }
        assertEquals(before, networkCalls)
        assertTrue(key.all { it == '\u0000' })
    }
}
