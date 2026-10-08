package io.github.hebadenys.fitnesshub.core.body

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId

class ManualBodyInputTest {
    private val zone = ZoneId.of("UTC")
    private val now = Instant.parse("2026-10-08T21:30:00Z")

    @Test fun acceptsWeightBodyFatAndHistoricalTimestamp() {
        val result = parseManualBodyInput("99,4", "20.5", "2026-10-08 18:00", zone, now)
        require(result is ManualBodyInputResult.Valid)
        assertEquals(99.4, result.input.weightKg!!, 0.001)
        assertEquals(20.5, result.input.bodyFatPercent!!, 0.001)
        assertEquals(Instant.parse("2026-10-08T18:00:00Z").toEpochMilli(), result.input.measuredAtMillis)
    }

    @Test fun acceptsOnlyOneMetric() {
        val weight = parseManualBodyInput("80", "", "2026-10-08 18:00", zone, now)
        val fat = parseManualBodyInput("", "18", "2026-10-08 18:00", zone, now)
        assertTrue(weight is ManualBodyInputResult.Valid)
        assertTrue(fat is ManualBodyInputResult.Valid)
    }

    @Test fun rejectsMissingImplausibleOrFutureValues() {
        assertEquals(ManualBodyInputError.MISSING_VALUES,
            (parseManualBodyInput("", "", "2026-10-08 18:00", zone, now) as ManualBodyInputResult.Invalid).error)
        assertEquals(ManualBodyInputError.INVALID_WEIGHT,
            (parseManualBodyInput("501", "", "2026-10-08 18:00", zone, now) as ManualBodyInputResult.Invalid).error)
        assertEquals(ManualBodyInputError.INVALID_BODY_FAT,
            (parseManualBodyInput("", "101", "2026-10-08 18:00", zone, now) as ManualBodyInputResult.Invalid).error)
        assertEquals(ManualBodyInputError.FUTURE_TIMESTAMP,
            (parseManualBodyInput("80", "", "2026-10-09 18:00", zone, now) as ManualBodyInputResult.Invalid).error)
        assertEquals(ManualBodyInputError.INVALID_TIMESTAMP,
            (parseManualBodyInput("80", "", "not-a-time", zone, now) as ManualBodyInputResult.Invalid).error)
    }
}
