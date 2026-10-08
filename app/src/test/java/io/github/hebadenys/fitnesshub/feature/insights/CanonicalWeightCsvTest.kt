package io.github.hebadenys.fitnesshub.feature.insights

import io.github.hebadenys.fitnesshub.core.body.CanonicalBodyMetricResolver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class CanonicalWeightCsvTest {
    private fun point(value: Double, time: String, source: CanonicalBodyMetricResolver.Source) =
        CanonicalBodyMetricResolver.Observation(
            metric = CanonicalBodyMetricResolver.Metric.WEIGHT,
            value = value,
            unit = "kg",
            measuredAt = Instant.parse(time),
            source = source,
            method = CanonicalBodyMetricResolver.Method.MEASURED,
            quality = CanonicalBodyMetricResolver.Quality.VALID
        )

    @Test fun exportUsesRawCanonicalEventsIncludingMultipleSameDayMeasurements() {
        val csv = canonicalWeightCsv(listOf(
            point(100.0, "2026-10-08T08:00:00Z", CanonicalBodyMetricResolver.Source.SCALE),
            point(99.4, "2026-10-08T18:00:00Z", CanonicalBodyMetricResolver.Source.HEALTH_CONNECT)
        ))
        val lines = csv.lines()

        assertEquals(3, lines.size)
        assertEquals("measuredAt,weightKg,unit,source,method,quality", lines.first())
        assertTrue(lines[1].contains("2026-10-08T08:00:00Z,100.00,kg,SCALE,MEASURED,VALID"))
        assertTrue(lines[2].contains("2026-10-08T18:00:00Z,99.40,kg,HEALTH_CONNECT,MEASURED,VALID"))
    }
}
