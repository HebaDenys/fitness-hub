package io.github.hebadenys.fitnesshub.core.sync

import io.github.hebadenys.fitnesshub.core.database.DailyHealthEntity
import io.github.hebadenys.fitnesshub.core.database.HealthBodyFatSampleEntity
import io.github.hebadenys.fitnesshub.core.database.HealthDao
import io.github.hebadenys.fitnesshub.core.database.HealthWeightSampleEntity
import io.github.hebadenys.fitnesshub.core.healthconnect.HealthDataSource
import io.github.hebadenys.fitnesshub.core.model.DayAggregate
import io.github.hebadenys.fitnesshub.core.model.DecimalSample
import io.github.hebadenys.fitnesshub.core.model.ExerciseSessionData
import io.github.hebadenys.fitnesshub.core.model.HealthChangesPage
import io.github.hebadenys.fitnesshub.core.model.HealthMetrics
import io.github.hebadenys.fitnesshub.core.model.RangePayload
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.time.ZoneId

/**
 * Synchronization behaviour: which mode runs, what gets written, and the
 * guarantee that a missing metric is never persisted as a real zero.
 */
class HealthSyncRepositoryTest {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val today: LocalDate = LocalDate.now(zone)
    private val stepsPermission = "android.permission.health.READ_STEPS"

    private val dao: HealthDao = mock()

    private class FakeSource(
        private val granted: Set<String> = setOf(HealthMetrics.STEPS),
        private val historyGranted: Boolean = false,
        private val aggregate: (LocalDate) -> DayAggregate = { DayAggregate(date = it) },
        private val payload: RangePayload = RangePayload(),
        private val existingExerciseIds: Set<String> = emptySet(),
        private val changesPages: List<HealthChangesPage> = emptyList(),
        private val failOnRead: Boolean = false
    ) : HealthDataSource {

        var aggregateCalls = 0
            private set
        var createdTokens = 0
            private set
        var readsByToken = mutableListOf<String>()
            private set

        override suspend fun grantedMetrics(): Set<String> = granted

        override suspend fun historyAccessGranted(): Boolean = historyGranted

        override suspend fun aggregateDay(date: LocalDate, metrics: Set<String>): DayAggregate {
            aggregateCalls++
            return aggregate(date)
        }

        override suspend fun readRange(
            rangeStart: java.time.Instant,
            rangeEnd: java.time.Instant,
            metrics: Set<String>
        ): RangePayload {
            if (failOnRead) throw IllegalStateException("read failed")
            return payload
        }

        override suspend fun readChanges(token: String): HealthChangesPage {
            readsByToken += token
            val page = changesPages.firstOrNull() ?: HealthChangesPage(
                tokenExpired = false,
                hasMore = false,
                nextToken = "next",
                changeCount = 0,
                affectedDates = emptySet(),
                deletedRecordIds = emptyList(),
                upsertedExerciseIds = emptyList()
            )
            return page
        }

        override suspend fun createChangesToken(): String {
            createdTokens++
            return "token-${createdTokens}"
        }

        fun knownExerciseIds(): List<String> = existingExerciseIds.toList()
    }

    private fun repository(
        source: HealthDataSource,
        tokenStore: SyncTokenStore = InMemoryTokenStore(),
        logger: AppLogger = AppLogger(writer = { })
    ) = HealthSyncRepository(SyncEnvironment(source, tokenStore, logger), dao)

    private class InMemoryTokenStore(initial: SyncToken? = null) : SyncTokenStore {
        var stored: SyncToken? = initial
            private set
        var writes = 0
            private set

        override suspend fun read(): SyncToken? = stored

        override suspend fun write(token: SyncToken) {
            stored = token
            writes++
        }
    }

    private suspend fun stubEmptyDao() {
        whenever(dao.exerciseExternalIds(any(), any())).thenReturn(emptyList())
        whenever(dao.exerciseIdsIn(any())).thenReturn(emptyList())
    }

    @Test
    @DisplayName("without a stored token the first sync runs a full pass and mints a new token")
    fun firstSync_isFullAndStoresToken() = runTest {
        stubEmptyDao()
        val source = FakeSource()
        val tokens = InMemoryTokenStore()

        val result = repository(source, tokens).sync()

        assertTrue(result.isSuccess)
        assertEquals("full", result.getOrThrow().mode)
        assertEquals(1, source.createdTokens)
        assertNotNull(tokens.stored)
        assertEquals("token-1", tokens.stored?.value)
    }

    @Test
    @DisplayName("a stored token makes the next sync incremental instead of full")
    fun subsequentSync_isIncremental() = runTest {
        stubEmptyDao()
        val source = FakeSource()
        val tokens = InMemoryTokenStore(SyncToken("existing", HealthSyncRepository.DEFAULT_RANGE_DAYS))

        val result = repository(source, tokens).sync()

        assertTrue(result.isSuccess)
        assertEquals("incremental", result.getOrThrow().mode)
        assertEquals(listOf("existing"), source.readsByToken)
        assertEquals(0, source.createdTokens)
    }

    @Test
    @DisplayName("an expired Changes token falls back to a full resync and replaces the token")
    fun expiredToken_fallsBackToFullResync() = runTest {
        stubEmptyDao()
        val source = FakeSource(
            changesPages = listOf(
                HealthChangesPage(
                    tokenExpired = true,
                    hasMore = false,
                    nextToken = "ignored",
                    changeCount = 0,
                    affectedDates = emptySet(),
                    deletedRecordIds = emptyList(),
                    upsertedExerciseIds = emptyList()
                )
            )
        )
        val tokens = InMemoryTokenStore(SyncToken("stale", HealthSyncRepository.DEFAULT_RANGE_DAYS))

        val result = repository(source, tokens).sync()

        assertEquals("full", result.getOrThrow().mode)
        assertEquals(1, source.createdTokens)
        assertEquals("token-1", tokens.stored?.value)
    }

    @Test
    @DisplayName("a token stored for a different history window forces a full sync")
    fun changedHistoryWindow_forcesFullSync() = runTest {
        stubEmptyDao()
        val source = FakeSource(historyGranted = true)
        val tokens = InMemoryTokenStore(SyncToken("existing", HealthSyncRepository.DEFAULT_RANGE_DAYS))

        val result = repository(source, tokens).sync()

        assertEquals("full", result.getOrThrow().mode)
        assertEquals(HealthSyncRepository.HISTORY_RANGE_DAYS, tokens.stored?.rangeDays)
    }

    @Test
    @DisplayName("metrics with no data are persisted as null, never as zero")
    fun missingMetrics_persistedAsNull() = runTest {
        stubEmptyDao()
        val source = FakeSource(aggregate = { DayAggregate(date = it) })

        val result = repository(source).sync()

        assertTrue(result.isSuccess)
        val captor = argumentCaptor<List<DailyHealthEntity>>()
        verify(dao).upsertDaily(captor.capture())
        val rows = captor.firstValue
        assertTrue(rows.isNotEmpty())
        assertTrue(rows.all { it.steps == null }, "absent steps must not be stored as 0")
        assertTrue(rows.all { it.distanceMeters == null })
        assertTrue(rows.all { it.activeCalories == null })
        assertTrue(rows.all { it.sleepMinutes == null })
    }

    @Test
    @DisplayName("a recorded zero is stored as zero and stays distinguishable from missing data")
    fun recordedZero_persistedAsZero() = runTest {
        stubEmptyDao()
        val source = FakeSource(
            aggregate = { DayAggregate(date = it, steps = 0, activeCalories = 0.0) }
        )

        repository(source).sync()

        val captor = argumentCaptor<List<DailyHealthEntity>>()
        verify(dao).upsertDaily(captor.capture())
        assertTrue(captor.firstValue.all { it.steps == 0L })
    }

    @Test
    @DisplayName("sync fails cleanly when no permission is granted, and writes nothing")
    fun noPermissions_failsWithoutWriting() = runTest {
        stubEmptyDao()
        val source = FakeSource(granted = emptySet())

        val result = repository(source).sync()

        assertTrue(result.isFailure)
        assertEquals(0, source.aggregateCalls)
        verify(dao, never()).upsertDaily(any<List<DailyHealthEntity>>())
    }

    @Test
    @DisplayName("a read failure surfaces as a failed result instead of an exception")
    fun readFailure_isReportedAsFailure() = runTest {
        stubEmptyDao()
        val source = FakeSource(failOnRead = true)

        val result = repository(source).sync()

        assertTrue(result.isFailure)
        assertEquals("read failed", result.exceptionOrNull()?.message)
    }

    @Test
    @DisplayName("partial permissions still import the granted metrics")
    fun partialPermissions_importGrantedMetrics() = runTest {
        stubEmptyDao()
        val requested = mutableListOf<Set<String>>()
        val source = object : HealthDataSource {
            override suspend fun grantedMetrics() = setOf(HealthMetrics.STEPS, HealthMetrics.WEIGHT)
            override suspend fun historyAccessGranted() = false
            override suspend fun aggregateDay(date: LocalDate, metrics: Set<String>): DayAggregate {
                requested += metrics
                return DayAggregate(date = date, steps = 5_000)
            }

            override suspend fun readRange(
                rangeStart: java.time.Instant,
                rangeEnd: java.time.Instant,
                metrics: Set<String>
            ) = RangePayload()

            override suspend fun readChanges(token: String) =
                HealthChangesPage(false, false, "t", 0, emptySet(), emptyList(), emptyList())

            override suspend fun createChangesToken() = "token"
        }

        val result = repository(source).sync()

        assertTrue(result.isSuccess)
        assertTrue(requested.isNotEmpty())
        assertTrue(requested.all { it == setOf(HealthMetrics.STEPS, HealthMetrics.WEIGHT) })
    }

    @Test
    @DisplayName("logs correlate the run by id and never contain a health measurement")
    fun logs_neverContainHealthValues() = runTest {
        stubEmptyDao()
        val lines = mutableListOf<String>()
        val logger = AppLogger(writer = { lines += it })
        val source = FakeSource(
            aggregate = { DayAggregate(date = it, steps = 7_654, distanceMeters = 4_321.5, activeCalories = 250.0) }
        )

        repository(source, logger = logger).sync()

        assertTrue(lines.isNotEmpty(), "the sync must be traceable through logs")
        assertTrue(lines.any { it.contains("sync_start") })
        assertTrue(lines.any { it.contains("sync_complete") })
        val forbidden = listOf("7654", "4321.5", "250.0", "steps=", "weight=", "bpm")
        lines.forEach { line ->
            forbidden.forEach { token ->
                assertFalse(line.contains(token), "log line must not contain '$token': $line")
            }
        }
    }

    @Test
    @DisplayName("a failed sync is logged with the error class only")
    fun failure_logsErrorClassNotMessage() = runTest {
        stubEmptyDao()
        val lines = mutableListOf<String>()
        val source = FakeSource(failOnRead = true)

        repository(source, logger = AppLogger(writer = { lines += it })).sync()

        val failureLine = lines.firstOrNull { it.contains("sync_failed") }
        assertNotNull(failureLine)
        assertTrue(failureLine!!.contains("IllegalStateException"))
        assertFalse(failureLine.contains("read failed"))
    }

    @Test
    @DisplayName("the full pass clears stored vitals series in range before rewriting them")
    fun fullSync_clearsSeriesInRange() = runTest {
        stubEmptyDao()
        val source = FakeSource()

        repository(source).sync()

        verify(dao).deleteHeartRateSamplesBetween(any(), any())
        verify(dao).deleteOxygenSamplesBetween(any(), any())
        verify(dao).deleteRestingHeartRateSamplesBetween(any(), any())
        verify(dao).deleteWeightSamplesBetween(any(), any())
        verify(dao).deleteBodyFatSamplesBetween(any(), any())
    }

    @Test
    @DisplayName("exact Health Connect body records preserve record ids and timestamps")
    fun bodySamples_preserveExactIdentity() = runTest {
        stubEmptyDao()
        val time = 1_791_286_400_123L
        val source = FakeSource(
            granted = setOf(HealthMetrics.WEIGHT, HealthMetrics.BODY_FAT),
            payload = RangePayload(
                weights = listOf(DecimalSample(time, 75.5, "fixture.health", "weight-record-id")),
                bodyFats = listOf(DecimalSample(time + 1_000, 21.5, "fixture.health", "body-fat-record-id"))
            )
        )

        val result = repository(source).sync()

        assertTrue(result.isSuccess)
        verify(dao).insertWeightSamples(listOf(
            HealthWeightSampleEntity(
                "weight-record-id",
                java.time.Instant.ofEpochMilli(time).atZone(zone).toLocalDate().toString(),
                time, 75.5, "fixture.health"
            )
        ))
        verify(dao).insertBodyFatSamples(listOf(
            HealthBodyFatSampleEntity(
                "body-fat-record-id",
                java.time.Instant.ofEpochMilli(time + 1_000).atZone(zone).toLocalDate().toString(),
                time + 1_000, 21.5, "fixture.health"
            )
        ))
        assertEquals(1, result.getOrThrow().counts.weightSamples)
        assertEquals(1, result.getOrThrow().counts.bodyFatSamples)
    }

    @Test
    @DisplayName("an incremental pass does not wipe vitals series it is not rewriting")
    fun incrementalSync_leavesSeriesAlone() = runTest {
        stubEmptyDao()
        val source = FakeSource()
        val tokens = InMemoryTokenStore(SyncToken("existing", HealthSyncRepository.DEFAULT_RANGE_DAYS))

        repository(source, tokens).sync()

        verify(dao, never()).deleteHeartRateSamplesBetween(any(), any())
        verify(dao, never()).deleteWeightSamplesBetween(any(), any())
        verify(dao, never()).deleteBodyFatSamplesBetween(any(), any())
    }

    @Test
    @DisplayName("the stored token is advanced to the next one after an incremental pass")
    fun incrementalSync_advancesToken() = runTest {
        stubEmptyDao()
        val source = FakeSource(
            changesPages = listOf(
                HealthChangesPage(false, false, "advanced", 3, setOf(today), emptyList(), emptyList())
            )
        )
        val tokens = InMemoryTokenStore(SyncToken("existing", HealthSyncRepository.DEFAULT_RANGE_DAYS))

        repository(source, tokens).sync()

        assertEquals("advanced", tokens.stored?.value)
    }

    @Test
    @DisplayName("a deletion of an unknown record id escalates to a full resync")
    fun deletionOfUnknownRecord_escalatesToFullSync() = runTest {
        whenever(dao.exerciseExternalIds(any(), any())).thenReturn(emptyList())
        whenever(dao.exerciseIdsIn(any())).thenReturn(emptyList())
        val source = FakeSource(
            changesPages = listOf(
                HealthChangesPage(false, false, "next", 1, emptySet(), listOf("unknown-id"), emptyList())
            )
        )
        val tokens = InMemoryTokenStore(SyncToken("existing", HealthSyncRepository.DEFAULT_RANGE_DAYS))

        val result = repository(source, tokens).sync()

        assertEquals("full", result.getOrThrow().mode)
        verify(dao).deleteExercisesById(listOf("unknown-id"))
    }

    @Test
    @DisplayName("observeDaily simply delegates to the DAO")
    fun observeDaily_delegatesToDao() = runTest {
        whenever(dao.observeDaily()).thenReturn(kotlinx.coroutines.flow.flowOf(emptyList()))

        repository(FakeSource()).observeDaily()

        verify(dao).observeDaily()
    }
}
