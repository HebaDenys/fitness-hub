package io.github.hebadenys.fitnesshub.core.sync

import io.github.hebadenys.fitnesshub.core.database.DailyHealthEntity
import io.github.hebadenys.fitnesshub.core.database.ExerciseEntity
import io.github.hebadenys.fitnesshub.core.database.HealthDao
import io.github.hebadenys.fitnesshub.core.healthconnect.HealthDataSource
import io.github.hebadenys.fitnesshub.core.model.DayAggregate
import io.github.hebadenys.fitnesshub.core.model.ExerciseSessionData
import io.github.hebadenys.fitnesshub.core.model.HealthChangesPage
import io.github.hebadenys.fitnesshub.core.model.RangePayload
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.stub
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.time.ZoneId

/**
 * Deduplication of exercise sessions. Health Connect record ids are the identity,
 * so repeated syncs must never insert the same session twice.
 */
class ExerciseDedupTest {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val day: LocalDate = LocalDate.now(zone)
    private val dao: HealthDao = mock()

    private fun session(id: String, hour: Int = 7) = ExerciseSessionData(
        externalId = id,
        title = "Session $id",
        type = 37,
        startMillis = day.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli(),
        endMillis = day.atTime(hour + 1, 0).atZone(zone).toInstant().toEpochMilli(),
        dataOrigin = "com.mi.health"
    )

    private class Source(
        private val exercises: List<ExerciseSessionData>,
        private val storedIds: Set<String>
    ) : HealthDataSource {
        override suspend fun grantedMetrics() = setOf("EXERCISE")
        override suspend fun historyAccessGranted() = false
        override suspend fun aggregateDay(date: LocalDate, metrics: Set<String>) = DayAggregate(date = date)
        override suspend fun readRange(
            rangeStart: java.time.Instant,
            rangeEnd: java.time.Instant,
            metrics: Set<String>
        ) = RangePayload(exercises = exercises)

        override suspend fun readChanges(token: String) =
            HealthChangesPage(false, false, "token", 0, emptySet(), emptyList(), emptyList())

        override suspend fun createChangesToken() = "token"
    }

    private suspend fun sync(exercises: List<ExerciseSessionData>, storedIds: Set<String>): List<ExerciseEntity> {
        whenever(dao.exerciseExternalIds(any(), any())).thenReturn(storedIds.toList())
        whenever(dao.exerciseIdsIn(any())).thenReturn(storedIds.toList())
        whenever(dao.upsertDaily(any<List<DailyHealthEntity>>())).thenReturn(Unit)
        whenever(dao.insertHeartRateSamples(any())).thenReturn(Unit)
        whenever(dao.insertOxygenSamples(any())).thenReturn(Unit)
        whenever(dao.insertRestingHeartRateSamples(any())).thenReturn(Unit)

        val repository = HealthSyncRepository(
            SyncEnvironment(Source(exercises, storedIds), NoTokenStore, AppLogger(writer = { })),
            dao
        )
        repository.sync()

        val captor = argumentCaptor<List<ExerciseEntity>>()
        verify(dao).insertExercises(captor.capture())
        return captor.firstValue
    }

    private object NoTokenStore : SyncTokenStore {
        override suspend fun read(): SyncToken? = null
        override suspend fun write(token: SyncToken) = Unit
    }

    @Test
    @DisplayName("sessions already stored are not inserted again")
    fun alreadyStored_notReinserted() = runTest {
        val inserted = sync(
            exercises = listOf(session("hc-1"), session("hc-2")),
            storedIds = setOf("hc-1", "hc-2")
        )

        assertTrue(inserted.isEmpty(), "a repeated sync must insert nothing new")
    }

    @Test
    @DisplayName("only genuinely new sessions are inserted")
    fun onlyNewSessions_inserted() = runTest {
        val inserted = sync(
            exercises = listOf(session("hc-1"), session("hc-2"), session("hc-3")),
            storedIds = setOf("hc-1", "hc-2")
        )

        assertEquals(listOf("hc-3"), inserted.map { it.externalId })
    }

    @Test
    @DisplayName("a full resync of an empty store inserts every session once")
    fun emptyStore_insertsAll() = runTest {
        val inserted = sync(
            exercises = listOf(session("hc-1"), session("hc-2")),
            storedIds = emptySet()
        )

        assertEquals(2, inserted.size)
        assertEquals(setOf("hc-1", "hc-2"), inserted.map { it.externalId }.toSet())
    }

    @Test
    @DisplayName("inserted sessions keep their source package and timing")
    fun insertedSessions_keepIdentityAndTiming() = runTest {
        val inserted = sync(exercises = listOf(session("hc-1", hour = 6)), storedIds = emptySet())

        val entity = inserted.single()
        assertEquals("hc-1", entity.externalId)
        assertEquals("com.mi.health", entity.source)
        assertEquals(37, entity.type)
        assertTrue(entity.startMillis < entity.endMillis)
    }

    @Test
    @DisplayName("the same session appearing twice in one payload is inserted once")
    fun duplicateWithinPayload_insertedOnce() = runTest {
        val inserted = sync(
            exercises = listOf(session("hc-1"), session("hc-1")),
            storedIds = emptySet()
        )

        assertEquals(1, inserted.size)
    }

    @Test
    @DisplayName("sessions differing only by id are both kept")
    fun distinctIdsBothKept() = runTest {
        val inserted = sync(
            exercises = listOf(session("hc-1", hour = 6), session("hc-2", hour = 6)),
            storedIds = emptySet()
        )

        assertEquals(2, inserted.size)
    }
}
