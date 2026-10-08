package io.github.hebadenys.fitnesshub.feature.body

import io.github.hebadenys.fitnesshub.core.body.CanonicalBodyMetricResolver.*
import io.github.hebadenys.fitnesshub.ui.components.TimeRange
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class BodyObservationWindowTest {
    private fun item(at: String, value: Double = 70.0, id: String = at) =
        Observation(Metric.WEIGHT, value, "kg", Instant.parse(at), Source.MANUAL, Method.MEASURED, sourceId = id)

    @Test fun exactTimesAndSeveralEventsOnOneDayAreNeverGrouped() {
        val first = item("2026-10-08T08:00:00.123Z", 70.0, "first")
        val second = item("2026-10-08T08:00:00.456Z", 70.2, "second")
        val result = bodyObservationWindow(listOf(second, first), TimeRange.SEVEN_DAYS,
            LocalDate.of(2026,10,8), ZoneId.of("UTC"))
        assertEquals(listOf(first, second), result)
        assertEquals(2, result.size)
    }

    @Test fun rangeUsesInclusiveLocalDayStartAndExclusiveNextDay() {
        val start = item("2026-10-02T00:00:00Z")
        val before = item("2026-10-01T23:59:59.999Z")
        val after = item("2026-10-09T00:00:00Z")
        assertEquals(listOf(start), bodyObservationWindow(listOf(before, start, after),
            TimeRange.SEVEN_DAYS, LocalDate.of(2026,10,8), ZoneId.of("UTC")))
    }

    @Test fun repeatedDstHourRetainsBothExactSourceInstantsAndZero() {
        val earlier = item("2026-10-25T00:30:00Z", 0.0).copy(metric = Metric.BODY_FAT, unit = "%")
        val later = item("2026-10-25T01:30:00Z", 1.0).copy(metric = Metric.BODY_FAT, unit = "%")
        val result = bodyObservationWindow(listOf(earlier, later), TimeRange.SEVEN_DAYS,
            LocalDate.of(2026,10,25), ZoneId.of("Europe/Rome"))
        assertEquals(2, result.size)
        assertEquals(0.0, result.first().value)
        assertEquals(3600L, result.last().measuredAt.epochSecond - result.first().measuredAt.epochSecond)
        assertTrue(bodyObservationWindow(emptyList(), TimeRange.SEVEN_DAYS).isEmpty())
    }
}
