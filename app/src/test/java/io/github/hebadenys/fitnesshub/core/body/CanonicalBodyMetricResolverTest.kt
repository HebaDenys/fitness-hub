package io.github.hebadenys.fitnesshub.core.body

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class CanonicalBodyMetricResolverTest {
    private val resolver = CanonicalBodyMetricResolver()

    @Test
    fun `keeps alternatives and prefers measured observations`() {
        val time = Instant.parse("2026-10-06T10:00:00Z")
        val result = resolver.resolve(listOf(
            CanonicalBodyMetricResolver.Observation(
                CanonicalBodyMetricResolver.Metric.WEIGHT,
                100.0,
                "kg",
                time,
                CanonicalBodyMetricResolver.Source.HEALTH_CONNECT,
                CanonicalBodyMetricResolver.Method.MEASURED
            ),
            CanonicalBodyMetricResolver.Observation(
                CanonicalBodyMetricResolver.Metric.WEIGHT,
                101.0,
                "kg",
                time.plusSeconds(3600),
                CanonicalBodyMetricResolver.Source.XIAOMI,
                CanonicalBodyMetricResolver.Method.MEASURED
            ),
            CanonicalBodyMetricResolver.Observation(
                CanonicalBodyMetricResolver.Metric.WEIGHT,
                99.0,
                "kg",
                time.plusSeconds(7200),
                CanonicalBodyMetricResolver.Source.XIAOMI,
                CanonicalBodyMetricResolver.Method.VENDOR_ESTIMATE
            )
        ))

        assertEquals(101.0, result[CanonicalBodyMetricResolver.Metric.WEIGHT]?.observation?.value)
        assertEquals(2, result[CanonicalBodyMetricResolver.Metric.WEIGHT]?.alternatives?.size)
    }
}
