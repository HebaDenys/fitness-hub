package io.github.hebadenys.fitnesshub.core.body

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.time.Instant

class CanonicalBodyMetricResolverTest {
    private val resolver = CanonicalBodyMetricResolver()
    private val metric = CanonicalBodyMetricResolver.Metric.WEIGHT

    private fun observation(
        value: Double,
        time: String,
        source: CanonicalBodyMetricResolver.Source,
        method: CanonicalBodyMetricResolver.Method = CanonicalBodyMetricResolver.Method.MEASURED,
        eventKey: String? = null,
        quality: CanonicalBodyMetricResolver.Quality = CanonicalBodyMetricResolver.Quality.VALID
    ) = CanonicalBodyMetricResolver.Observation(
        metric = metric,
        value = value,
        unit = "kg",
        measuredAt = Instant.parse(time),
        source = source,
        method = method,
        quality = quality,
        sourceId = "$source-$time-$value",
        eventKey = eventKey
    )

    @Test
    fun `newer measurement beats older higher-priority source`() {
        val oldXiaomi = observation(
            100.0, "2026-10-01T10:00:00Z",
            CanonicalBodyMetricResolver.Source.XIAOMI
        )
        val newHealthConnect = observation(
            95.0, "2026-10-10T10:00:00Z",
            CanonicalBodyMetricResolver.Source.HEALTH_CONNECT
        )

        val resolved = resolver.resolve(listOf(oldXiaomi, newHealthConnect)).getValue(metric)

        assertSame(newHealthConnect, resolved.observation)
        assertEquals(listOf(oldXiaomi), resolved.alternatives)
    }

    @Test
    fun `same explicit event prefers richer direct source but keeps duplicate alternative`() {
        val time = "2026-10-06T10:00:00Z"
        val hc = observation(
            100.1, time,
            CanonicalBodyMetricResolver.Source.HEALTH_CONNECT,
            CanonicalBodyMetricResolver.Method.UNKNOWN,
            eventKey = "event-1"
        )
        val xiaomi = observation(
            100.0, time,
            CanonicalBodyMetricResolver.Source.XIAOMI,
            CanonicalBodyMetricResolver.Method.MEASURED,
            eventKey = "event-1"
        )

        val resolved = resolver.resolve(listOf(hc, xiaomi)).getValue(metric)

        assertSame(xiaomi, resolved.observation)
        assertEquals(1, resolver.timeline(listOf(hc, xiaomi), metric).size)
        assertEquals(1, resolved.alternatives.size)
    }

    @Test
    fun `two real weigh-ins on same day stay separate and latest wins current value`() {
        val morning = observation(
            100.0, "2026-10-06T08:00:00Z",
            CanonicalBodyMetricResolver.Source.SCALE
        )
        val evening = observation(
            99.4, "2026-10-06T18:00:00Z",
            CanonicalBodyMetricResolver.Source.SCALE
        )

        val timeline = resolver.timeline(listOf(morning, evening), metric)
        val resolved = resolver.resolve(listOf(morning, evening)).getValue(metric)

        assertEquals(listOf(morning, evening), timeline)
        assertSame(evening, resolved.observation)
    }

    @Test
    fun `source priority only breaks equal timestamps when event identity is unknown`() {
        val time = "2026-10-06T10:00:00Z"
        val hc = observation(100.2, time, CanonicalBodyMetricResolver.Source.HEALTH_CONNECT)
        val xiaomi = observation(100.0, time, CanonicalBodyMetricResolver.Source.XIAOMI)

        assertSame(xiaomi, resolver.resolve(listOf(hc, xiaomi)).getValue(metric).observation)
        assertEquals(2, resolver.timeline(listOf(hc, xiaomi), metric).size)
    }

    @Test
    fun `invalid and unknown-quality observations do not become canonical values`() {
        val valid = observation(100.0, "2026-10-06T10:00:00Z", CanonicalBodyMetricResolver.Source.SCALE)
        val invalid = observation(
            1.0, "2026-10-07T10:00:00Z",
            CanonicalBodyMetricResolver.Source.XIAOMI,
            quality = CanonicalBodyMetricResolver.Quality.INVALID
        )
        val unknown = observation(
            90.0, "2026-10-08T10:00:00Z",
            CanonicalBodyMetricResolver.Source.HEALTH_CONNECT,
            quality = CanonicalBodyMetricResolver.Quality.UNKNOWN
        )

        assertSame(valid, resolver.resolve(listOf(valid, invalid, unknown)).getValue(metric).observation)
    }

    @Test
    fun `resolver never mutates original observation objects or list order`() {
        val first = observation(100.0, "2026-10-06T08:00:00Z", CanonicalBodyMetricResolver.Source.SCALE)
        val second = observation(99.5, "2026-10-06T18:00:00Z", CanonicalBodyMetricResolver.Source.HEALTH_CONNECT)
        val input = mutableListOf(first, second)
        val before = input.toList()

        resolver.resolve(input)
        resolver.timeline(input, metric)

        assertEquals(before, input)
        assertSame(first, input[0])
        assertSame(second, input[1])
    }
}
