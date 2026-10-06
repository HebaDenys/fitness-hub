package io.github.hebadenys.fitnesshub.core.database

import io.github.hebadenys.fitnesshub.core.model.DayAggregate
import io.github.hebadenys.fitnesshub.core.model.DailySummary
import io.github.hebadenys.fitnesshub.core.model.DecimalSample
import io.github.hebadenys.fitnesshub.core.model.ExerciseSessionData
import io.github.hebadenys.fitnesshub.core.model.HealthMetrics
import io.github.hebadenys.fitnesshub.core.model.RangePayload
import io.github.hebadenys.fitnesshub.core.model.SleepInterval
import io.github.hebadenys.fitnesshub.core.model.TimedOrigin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Mapping between Health Connect payloads, the pure domain summary, and Room rows.
 * The central rule: an absent measurement stays null and is never turned into 0.
 */
class DailySummaryMapperTest {

    private val zone: ZoneId = ZoneId.of("Europe/Rome")
    private val day: LocalDate = LocalDate.of(2026, 3, 10)
    private val origin = "com.mi.health"

    private fun millis(date: LocalDate, hour: Int, minute: Int = 0): Long =
        date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun summaryFor(
        aggregate: DayAggregate,
        payload: RangePayload = RangePayload()
    ) = DailySummaryMapper.buildSummaries(listOf(aggregate), payload, zone).single()

    @Test
    @DisplayName("metrics with no recorded data stay null instead of becoming zero")
    fun absentAggregates_stayNull() {
        val summary = summaryFor(DayAggregate(date = day))

        assertNull(summary.steps)
        assertNull(summary.distanceMeters)
        assertNull(summary.activeCalories)
        assertNull(summary.totalCalories)
        assertNull(summary.sleepMinutes)
    }

    @Test
    @DisplayName("a recorded zero survives as zero and is not confused with missing data")
    fun recordedZero_staysZero() {
        val aggregate = DayAggregate(
            date = day,
            steps = 0,
            distanceMeters = 0.0,
            activeCalories = 0.0,
            totalCalories = 0.0
        )

        val summary = summaryFor(aggregate)

        assertEquals(0L, summary.steps)
        assertEquals(0.0, summary.distanceMeters)
        assertEquals(0.0, summary.activeCalories)
    }

    @Test
    @DisplayName("aggregate values are carried through unchanged")
    fun aggregateValues_carriedThrough() {
        val aggregate = DayAggregate(
            date = day,
            steps = 8_432,
            distanceMeters = 5_120.5,
            activeCalories = 310.0,
            totalCalories = 2_050.0
        )

        val summary = summaryFor(aggregate)

        assertEquals(8_432L, summary.steps)
        assertEquals(5_120.5, summary.distanceMeters)
        assertEquals(310.0, summary.activeCalories)
        assertEquals(2_050.0, summary.totalCalories)
    }

    @Test
    @DisplayName("the latest weigh-in of the day wins, earlier ones do not overwrite it")
    fun latestWeightOfDay_wins() {
        val payload = RangePayload(
            weights = listOf(
                DecimalSample(millis(day, 7), 81.2, origin),
                DecimalSample(millis(day, 19), 80.4, origin),
                DecimalSample(millis(day, 12), 80.9, origin)
            )
        )

        val summary = summaryFor(DayAggregate(date = day), payload)

        assertEquals(80.4, summary.weightKg)
    }

    @Test
    @DisplayName("each metric is attributed to its own day, not the newest day overall")
    fun metricsSplitByDay() {
        val previous = day.minusDays(1)
        val payload = RangePayload(
            weights = listOf(
                DecimalSample(millis(previous, 8), 81.0, origin),
                DecimalSample(millis(day, 8), 80.5, origin)
            )
        )

        val summaries = DailySummaryMapper.buildSummaries(
            listOf(DayAggregate(date = previous), DayAggregate(date = day)),
            payload,
            zone
        ).associateBy { it.date }

        assertEquals(81.0, summaries.getValue(previous).weightKg)
        assertEquals(80.5, summaries.getValue(day).weightKg)
    }

    @Test
    @DisplayName("sleep minutes land on the wake day, not the calendar night")
    fun sleepAttributedToWakeDay() {
        val payload = RangePayload(
            sleep = listOf(
                SleepInterval(
                    externalId = "s1",
                    start = millis(day, 23).let { java.time.Instant.ofEpochMilli(it) },
                    end = java.time.Instant.ofEpochMilli(millis(day.plusDays(1), 7)),
                    dataOrigin = origin
                )
            )
        )

        val summary = summaryFor(DayAggregate(date = day.plusDays(1)), payload)

        assertEquals(480L, summary.sleepMinutes)
    }

    @Test
    @DisplayName("provenance of every contributing source is preserved on the summary")
    fun provenanceCollectedFromAllSources() {
        val payload = RangePayload(
            weights = listOf(DecimalSample(millis(day, 8), 80.5, "com.mi.health")),
            bodyFats = listOf(DecimalSample(millis(day, 8), 21.0, "com.xiaomi.scale")),
            metricOrigins = mapOf(
                HealthMetrics.STEPS to listOf(TimedOrigin(millis(day, 12), "com.google.android.apps.healthdata"))
            )
        )

        val summary = summaryFor(DayAggregate(date = day, steps = 100), payload)

        assertEquals(
            setOf("com.mi.health", "com.xiaomi.scale", "com.google.android.apps.healthdata"),
            summary.dataOrigins
        )
    }

    @Test
    @DisplayName("a day without any record carries no provenance")
    fun dayWithoutSources_hasNoOrigins() {
        val summary = summaryFor(DayAggregate(date = day))

        assertTrue(summary.dataOrigins.isEmpty())
    }

    @Test
    @DisplayName("round-tripping through Room preserves values, provenance and algorithm tags")
    fun entityRoundTrip_isLossless() {
        val summary = DailySummaryMapper.buildSummaries(
            listOf(
                DayAggregate(
                    date = day,
                    steps = 4_321,
                    distanceMeters = 2_100.25,
                    activeCalories = 180.5,
                    totalCalories = 1_900.0
                )
            ),
            RangePayload(weights = listOf(DecimalSample(millis(day, 7, 30), 79.9, origin))),
            zone
        ).single().copy(
            dataOrigins = setOf(origin, "com.xiaomi.scale"),
            provenance = DailySummary.PROVENANCE_ESTIMATE,
            algorithm = "openScale_v1"
        )

        val entity = DailySummaryMapper.toEntity(summary, syncedAt = 1_700_000_000_000)
        val restored = DailySummaryMapper.toDomain(entity)

        assertEquals(summary.date, restored.date)
        assertEquals(summary.steps, restored.steps)
        assertEquals(summary.distanceMeters, restored.distanceMeters)
        assertEquals(summary.activeCalories, restored.activeCalories)
        assertEquals(summary.totalCalories, restored.totalCalories)
        assertEquals(summary.sleepMinutes, restored.sleepMinutes)
        assertEquals(summary.weightKg, restored.weightKg)
        assertEquals(summary.dataOrigins, restored.dataOrigins)
        assertEquals(DailySummary.PROVENANCE_ESTIMATE, restored.provenance)
        assertEquals("openScale_v1", restored.algorithm)
        assertEquals(1_700_000_000_000, entity.syncedAt)
    }

    @Test
    @DisplayName("measured rows default to MEASURED provenance and no algorithm")
    fun measuredRows_defaultProvenance() {
        val entity = DailySummaryMapper.toEntity(
            DailySummaryMapper.buildSummaries(listOf(DayAggregate(date = day)), RangePayload(), zone).single(),
            syncedAt = 0L
        )

        assertEquals(DailySummary.PROVENANCE_MEASURED, entity.provenance)
        assertNull(entity.algorithm)
    }

    @Test
    @DisplayName("null metrics survive the Room round trip as null, never as 0")
    fun nullMetrics_surviveRoundTrip() {
        val entity = DailySummaryMapper.toEntity(
            DailySummaryMapper.buildSummaries(listOf(DayAggregate(date = day)), RangePayload(), zone).single(),
            syncedAt = 0L
        )

        val restored = DailySummaryMapper.toDomain(entity)

        assertNull(restored.steps)
        assertNull(restored.distanceMeters)
        assertNull(restored.weightKg)
    }

    @Test
    @DisplayName("exercise sessions keep their external id and source for deduplication")
    fun exerciseMapping_keepsIdentity() {
        val sessions = listOf(
            ExerciseSessionData(
                externalId = "hc-1",
                title = "Morning run",
                type = 37,
                startMillis = millis(day, 7),
                endMillis = millis(day, 8),
                dataOrigin = origin
            )
        )

        val entities = DailySummaryMapper.toExerciseEntities(sessions)

        assertEquals(1, entities.size)
        assertEquals("hc-1", entities.single().externalId)
        assertEquals(origin, entities.single().source)
        assertEquals(37, entities.single().type)
        assertEquals(millis(day, 7), entities.single().startMillis)
        assertEquals(millis(day, 8), entities.single().endMillis)
    }

    @Test
    @DisplayName("vitals series keep their timestamp, value and origin per sample")
    fun seriesSamples_keepTimestampAndOrigin() {
        val payload = RangePayload(
            heartRateSamples = listOf(io.github.hebadenys.fitnesshub.core.model.Sample(millis(day, 9), 61, origin)),
            oxygenSamples = listOf(DecimalSample(millis(day, 9), 97.0, origin)),
            restingHeartRateSamples = listOf(io.github.hebadenys.fitnesshub.core.model.Sample(millis(day, 3), 52, origin))
        )

        assertEquals(61L, DailySummaryMapper.heartRateEntities(payload.heartRateSamples, zone).single().bpm)
        assertEquals(millis(day, 9), DailySummaryMapper.heartRateEntities(payload.heartRateSamples, zone).single().timeEpochMillis)
        assertEquals(97.0, DailySummaryMapper.oxygenEntities(payload.oxygenSamples, zone).single().percentage)
        assertEquals(52L, DailySummaryMapper.restingHeartRateEntities(payload.restingHeartRateSamples, zone).single().bpm)
        assertEquals(day.toString(), DailySummaryMapper.oxygenEntities(payload.oxygenSamples, zone).single().date)
    }

    @Test
    @DisplayName("every daily row in a multi-day sync is produced")
    fun multipleDays_allMapped() {
        val dates = (0 until 5).map { day.minusDays(it.toLong()) }

        val summaries = DailySummaryMapper.buildSummaries(
            dates.map { DayAggregate(date = it, steps = 1_000) },
            RangePayload(),
            zone
        )

        assertEquals(5, summaries.size)
        assertEquals(dates.toSet(), summaries.map { it.date }.toSet())
        assertNotNull(summaries.first().steps)
    }
}
