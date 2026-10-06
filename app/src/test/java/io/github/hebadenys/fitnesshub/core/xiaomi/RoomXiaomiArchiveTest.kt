package io.github.hebadenys.fitnesshub.core.xiaomi

import android.app.Application
import androidx.room.withTransaction
import io.github.hebadenys.fitnesshub.core.database.HealthDatabase
import io.github.hebadenys.fitnesshub.core.database.testDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class RoomXiaomiArchiveTest {
    private lateinit var db: HealthDatabase
    private val scope = XiaomiScope("fixture-connection", XiaomiRegion.DE, "yunmai.scales.ms104", "10001")
    private val subject = XiaomiSubject("10001", "10")
    private val clock = Clock.fixed(Instant.parse("2026-02-01T00:00:00Z"), ZoneOffset.UTC)
    private val before = clock.millis()
    private val measured = Instant.parse("2026-01-01T08:00:00Z").toEpochMilli()
    private val protocol = XiaomiScaleProtocol(clock)
    @Before fun setUp() { db = testDatabase() }
    @After fun tearDown() { db.close() }
    private fun store() = RoomXiaomiArchive(db, clock)
    private suspend fun bind() { store().confirmBinding(scope, subject, "fixture-scale") }
    private fun row(time: Long = measured, account: String = "10", weight: String = "75.5", version: Int = 1) =
        """{"model":"yunmai.scales.ms104","uid":10001,"accountId":$account,"did":"fixture-scale","createTime":$time,"fromSource":1,"dataVersion":$version,"sn":"synthetic-serial","data":{"weight":$weight,"bfp":22.5,"pp":14.2,"bmr":1700,"customIndex":4,"user":{"name":"Same display name","accountId":"$account"}}}"""
    private fun page(vararg rows: String) = protocol.parse(rows.joinToString(",", "[", "]"), scope, before)
    private suspend fun expect(reason: ArchiveFailure, block: suspend () -> Unit) {
        try { block(); fail("Expected archive rejection") } catch (error: XiaomiArchiveException) { assertEquals(reason, error.reason) }
    }

    @Test fun bindingSurvivesReopenAndRejectsAnotherPersonOrConnection() = runBlocking {
        db.close()
        val name = UUID.randomUUID().toString()
        db = testDatabase(name)
        bind()
        val owner = db.xiaomiArchiveDao().identity()!!
        db.close(); db = testDatabase(name)
        bind()
        assertEquals(owner, db.xiaomiArchiveDao().identity())
        expect(ArchiveFailure.BINDING_CONFLICT) { store().confirmBinding(scope, XiaomiSubject("10001", "11"), "fixture-scale") }
        expect(ArchiveFailure.BINDING_CONFLICT) { store().confirmBinding(scope.copy(connectionId = "different"), subject, "fixture-scale") }
        expect(ArchiveFailure.BINDING_CONFLICT) { store().confirmBinding(scope, subject, "different-scale") }
        assertEquals("10", db.xiaomiArchiveDao().binding()!!.subjectAccountId)
    }

    @Test fun sameNameDoesNotMergeSubjectsAndAllMetricProvenanceIsRetained() = runBlocking {
        bind()
        val ticket = store().startOrResume(scope, before)
        store().committer(ticket).commit(scope, before, page(row(), row(time = measured - 1, account = "11")))
        val snapshot = db.xiaomiArchiveDao().snapshots(scope.connectionId).single()
        val json = XiaomiJsonReader(snapshot.snapshotJson).read() as XiaomiJson.Object
        val metrics = json.fields["metrics"] as XiaomiJson.Object
        val fat = metrics.fields["bfp"] as XiaomiJson.Object
        assertEquals(XiaomiJson.Text("VENDOR_ESTIMATE"), fat.fields["method"])
        assertEquals(XiaomiJson.Text("PERCENT"), fat.fields["unit"])
        assertTrue(snapshot.snapshotJson.contains("customIndex"))
        assertFalse(snapshot.snapshotJson.contains("Same display name"))
        assertEquals(1L, db.xiaomiArchiveDao().checkpoint(scope.connectionId)!!.otherRows)
        assertEquals(1L, db.xiaomiArchiveDao().checkpoint(scope.connectionId)!!.selectedRows)
        assertFalse(snapshot.toString().contains("75.5"))
    }

    @Test fun replayIsIdempotentEvenWithConcurrentCommitters() = runBlocking {
        bind()
        val ticket = store().startOrResume(scope, before)
        val committer = store().committer(ticket)
        val batch = page(row())
        List(4) { async(Dispatchers.Default) { committer.commit(scope, before, batch) } }.awaitAll()
        assertEquals(1, db.xiaomiArchiveDao().snapshotCount())
        assertEquals(1L, db.xiaomiArchiveDao().checkpoint(scope.connectionId)!!.committedPages)
    }

    @Test fun sameWeightAtDifferentTimesRemainsTwoObservations() = runBlocking {
        bind()
        val ticket = store().startOrResume(scope, before)
        store().committer(ticket).commit(scope, before, page(row(), row(time = measured - 1_000)))
        assertEquals(2, db.xiaomiArchiveDao().snapshotCount())
    }

    @Test fun changedContentPreservesBothCandidateVersions() = runBlocking {
        bind()
        val first = store().startOrResume(scope, before)
        store().committer(first).commit(scope, before, page(row()))
        val second = store().startOrResume(scope, before)
        store().committer(second).commit(scope, before, page(row(weight = "76.0", version = 2)))
        val rows = db.xiaomiArchiveDao().snapshots(scope.connectionId)
        assertEquals(2, rows.size)
        assertEquals(1, rows.map { it.eventKey }.distinct().size)
        expect(ArchiveFailure.STALE_GENERATION) { store().committer(first).commit(scope, before, page(row())) }
    }

    @Test fun alteredPageCannotUseTheReplayAcknowledgement() = runBlocking {
        bind()
        val ticket = store().startOrResume(scope, before)
        store().committer(ticket).commit(scope, before, page(row()))
        expect(ArchiveFailure.STALE_CURSOR) { store().committer(ticket).commit(scope, before, page(row(version = 2))) }
        assertEquals(1, db.xiaomiArchiveDao().snapshotCount())
    }

    @Test fun failureWritingCheckpointRollsBackInsertedSnapshots() = runBlocking {
        bind()
        val ticket = store().startOrResume(scope, before)
        val checkpoint = db.xiaomiArchiveDao().checkpoint(scope.connectionId)
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_checkpoint BEFORE INSERT ON xiaomi_checkpoints BEGIN SELECT RAISE(ABORT, 'synthetic injected failure'); END")
        try { store().committer(ticket).commit(scope, before, page(row())); fail("Expected SQLite failure") } catch (_: android.database.sqlite.SQLiteException) { }
        assertEquals(0, db.xiaomiArchiveDao().snapshotCount())
        assertEquals(checkpoint, db.xiaomiArchiveDao().checkpoint(scope.connectionId))
    }

    @Test fun cancelledTransactionDoesNotLeaveRecordsOrAdvanceCursor() = runBlocking {
        bind()
        val ticket = store().startOrResume(scope, before)
        try {
            db.withTransaction {
                store().committer(ticket).commit(scope, before, page(row()))
                throw CancellationException("synthetic cancellation")
            }
        } catch (_: CancellationException) { }
        assertEquals(0, db.xiaomiArchiveDao().snapshotCount())
        assertEquals(before, db.xiaomiArchiveDao().checkpoint(scope.connectionId)!!.nextBeforeMillis)
    }

    @Test fun unknownIdentityDoesNotBecomeTheSelectedPerson() = runBlocking {
        bind()
        val ticket = store().startOrResume(scope, before)
        val unknown = row().replace("\"accountId\":10,", "")
        expect(ArchiveFailure.AMBIGUOUS_IDENTITY) { store().committer(ticket).commit(scope, before, page(row(), unknown)) }
        assertEquals(0, db.xiaomiArchiveDao().snapshotCount())
        assertEquals(before, db.xiaomiArchiveDao().checkpoint(scope.connectionId)!!.nextBeforeMillis)
    }

    @Test fun resumeAfterProcessReopenUsesCommittedCursorAndReader() = runBlocking {
        db.close()
        val name = UUID.randomUUID().toString()
        db = testDatabase(name)
        bind()
        val rows = List(20) { row(time = measured - it * 1_000L) }
        val firstResult = store().read(XiaomiPageSource { rows.joinToString(",", "[", "]") }, scope, before, maxPages = 1)
        assertTrue(firstResult is XiaomiHistoryResult.Paused)
        val expectedCursor = measured - 19_000
        db.close(); db = testDatabase(name)
        val result = store().read(XiaomiPageSource { request ->
            assertEquals(expectedCursor, request.beforeMillis)
            "[]"
        }, scope, before + 1_000)
        assertTrue(result is XiaomiHistoryResult.Completed)
        assertEquals(20, db.xiaomiArchiveDao().snapshotCount())
        assertNull(db.xiaomiArchiveDao().checkpoint(scope.connectionId)!!.nextBeforeMillis)
    }

    @Test fun missingBindingFailsBeforeAnyRecordIsStored() = runBlocking {
        expect(ArchiveFailure.BINDING_REQUIRED) { store().startOrResume(scope, before) }
        assertEquals(0, db.xiaomiArchiveDao().snapshotCount())
    }
}
