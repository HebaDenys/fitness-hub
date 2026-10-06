package io.github.hebadenys.fitnesshub.core.analytics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDate

class TrendAnalyticsTest {

    private val start = LocalDate.of(2026, 3, 1)

    private fun series(vararg values: Double?): List<Observation> =
        values.mapIndexed { index, value -> Observation(start.plusDays(index.toLong()), value, value) }

    private fun pairs(vararg values: Pair<Double?, Double?>): List<Observation> =
        values.mapIndexed { index, (x, y) -> Observation(start.plusDays(index.toLong()), x, y) }

    @Test
    @DisplayName("the first smoothed point equals the first measured value")
    fun emaStartsAtFirstValue() {
        val smoothed = TrendAnalytics.exponentialMovingAverage(series(80.0, 81.0, 80.5), 7) { it.x }

        assertEquals(80.0, smoothed[start]!!, 0.0001)
    }

    @Test
    @DisplayName("smoothing lags a sudden change instead of tracking it exactly")
    fun emaSmoothsNoise() {
        val raw = series(80.0, 81.0, 79.9, 80.5, 80.2)
        val smoothed = TrendAnalytics.exponentialMovingAverage(raw, 7) { it.x }

        assertTrue(smoothed[start.plusDays(2)]!! > 79.9)
        assertTrue(smoothed[start.plusDays(2)]!! < 80.5)
    }

    @Test
    @DisplayName("a longer window reacts more slowly than a shorter one")
    fun longerWindowReactsLess() {
        val raw = series(80.0, 90.0)
        val fast = TrendAnalytics.exponentialMovingAverage(raw, 3) { it.x }[start.plusDays(1)]!!
        val slow = TrendAnalytics.exponentialMovingAverage(raw, 14) { it.x }[start.plusDays(1)]!!

        assertTrue(slow < fast)
        assertTrue(slow > 80.0)
    }

    @Test
    @DisplayName("missing days produce no smoothed point rather than a zero")
    fun missingDays_areNotZeroed() {
        val observations = listOf(
            Observation(start, 80.0, 80.0),
            Observation(start.plusDays(1), null, null),
            Observation(start.plusDays(2), 81.0, 81.0)
        )

        val smoothed = TrendAnalytics.exponentialMovingAverage(observations, 7) { it.x }

        assertFalse(smoothed.containsKey(start.plusDays(1)))
        // The gap carries no weight, so the curve keeps smoothing across it:
        // 0.25 * 81 + 0.75 * 80 = 80.25, not a reset to 81.
        assertEquals(80.25, smoothed[start.plusDays(2)]!!, 0.0001)
    }

    @Test
    @DisplayName("an all-missing series produces no points at all")
    fun allMissing_yieldsEmptyMap() {
        val observations = (0..4).map { Observation(start.plusDays(it.toLong()), null, null) }

        assertTrue(TrendAnalytics.exponentialMovingAverage(observations, 7) { it.x }.isEmpty())
    }

    @Test
    @DisplayName("a non-positive smoothing window is rejected")
    fun invalidWindow_isRejected() {
        try {
            TrendAnalytics.exponentialMovingAverage(series(80.0), 0) { it.x }
            org.junit.jupiter.api.Assertions.fail<Unit>("expected an exception")
        } catch (error: IllegalArgumentException) {
            assertNotNull(error.message)
        }
    }

    @Test
    @DisplayName("mean ignores missing days and returns null when nothing was measured")
    fun mean_ignoresGaps() {
        val observations = listOf(
            Observation(start, 80.0, 80.0),
            Observation(start.plusDays(1), null, null),
            Observation(start.plusDays(2), 82.0, 82.0)
        )

        assertEquals(81.0, TrendAnalytics.mean(observations) { it.x }!!, 0.0001)
        assertNull(TrendAnalytics.mean(listOf(Observation(start, null, null))) { it.x })
    }

    @Test
    @DisplayName("a perfectly positive relationship gives a coefficient of one")
    fun perfectPositiveCorrelation() {
        val observations = pairs(1.0 to 10.0, 2.0 to 20.0, 3.0 to 30.0, 4.0 to 40.0)

        val correlation = TrendAnalytics.correlate(observations, { it.x }, { it.y }, "x", "y")!!
        assertEquals(1.0, correlation.coefficient, 0.0001)
        assertEquals(TrendAnalytics.Direction.POSITIVE, correlation.direction)
        assertEquals(TrendAnalytics.Strength.STRONG, correlation.strength)
        assertEquals(4, correlation.sampleSize)
    }

    @Test
    @DisplayName("a perfectly negative relationship gives a coefficient of minus one")
    fun perfectNegativeCorrelation() {
        val observations = pairs(1.0 to 40.0, 2.0 to 30.0, 3.0 to 20.0, 4.0 to 10.0)

        val correlation = TrendAnalytics.correlate(observations, { it.x }, { it.y }, "x", "y")!!
        assertEquals(-1.0, correlation.coefficient, 0.0001)
        assertEquals(TrendAnalytics.Direction.NEGATIVE, correlation.direction)
    }

    @Test
    @DisplayName("a weak relationship is reported as no direction at all")
    fun weakRelationship_readsAsNone() {
        val weak = TrendAnalytics.Correlation(0.1, 30, "calories", "weight")

        assertTrue(weak.isNegligible)
        assertEquals(TrendAnalytics.Direction.NONE, weak.direction)
        assertEquals(TrendAnalytics.Strength.NONE, weak.strength)
    }

    @Test
    @DisplayName("a moderate relationship is separated from a strong one")
    fun strengthBands() {
        val moderate = TrendAnalytics.Correlation(0.45, 30, "sleep", "volume")
        val strong = TrendAnalytics.Correlation(0.85, 30, "sleep", "volume")
        val negative = TrendAnalytics.Correlation(-0.7, 30, "sleep", "volume")

        assertEquals(TrendAnalytics.Strength.MODERATE, moderate.strength)
        assertEquals(TrendAnalytics.Direction.POSITIVE, moderate.direction)
        assertEquals(TrendAnalytics.Strength.STRONG, strong.strength)
        assertEquals(TrendAnalytics.Direction.NEGATIVE, negative.direction)
    }

    @Test
    @DisplayName("a real moderate relationship computed from data is reported as moderate")
    fun computedModerate_isModerate() {
        val observations = (1..10).map {
            Observation(start.plusDays(it.toLong()), it * 0.1 + 5, it * 0.8 + 2 + (it % 3))
        }

        val correlation = TrendAnalytics.correlate(observations, { it.x }, { it.y }, "x", "y")

        assertNotNull(correlation)
        assertTrue(correlation!!.coefficient > TrendAnalytics.NEGLIGIBLE_THRESHOLD)
        assertTrue(correlation.coefficient < 1.0)
    }

    @Test
    @DisplayName("fewer than three paired days yields no coefficient at all")
    fun tooFewSamples_yieldsNull() {
        val two = pairs(1.0 to 2.0, 2.0 to 4.0)
        val three = pairs(1.0 to 2.0, 2.0 to 4.0, 3.0 to 6.0)

        assertNull(TrendAnalytics.correlate(two, { it.x }, { it.y }, "x", "y"))
        assertNotNull(TrendAnalytics.correlate(three, { it.x }, { it.y }, "x", "y"))
    }

    @Test
    @DisplayName("only days where both series have a value are used")
    fun pairing_requiresBothValues() {
        val observations = pairs(
            1.0 to 10.0,
            2.0 to null,
            3.0 to 30.0,
            4.0 to 40.0,
            null to 99.0
        )

        val correlation = TrendAnalytics.correlate(observations, { it.x }, { it.y }, "x", "y")

        assertEquals(3, correlation!!.sampleSize)
        assertEquals(1.0, correlation.coefficient, 0.0001)
    }

    @Test
    @DisplayName("a flat series yields no coefficient, because there is no variation to relate")
    fun flatSeries_yieldsNull() {
        val observations = pairs(5.0 to 1.0, 5.0 to 2.0, 5.0 to 3.0, 5.0 to 9.0)

        assertNull(TrendAnalytics.correlate(observations, { it.x }, { it.y }, "x", "y"))
    }

    @Test
    @DisplayName("no overlapping days yields no coefficient")
    fun noOverlap_yieldsNull() {
        val observations = pairs(1.0 to null, 2.0 to null, null to 3.0, null to 4.0)

        assertNull(TrendAnalytics.correlate(observations, { it.x }, { it.y }, "x", "y"))
    }

    @Test
    @DisplayName("the coefficient always stays within minus one and one")
    fun coefficientStaysInRange() {
        val observations = (0..20).map {
            Observation(start.plusDays(it.toLong()), it * 1.7 % 9.0, it * 3.1 % 7.0)
        }

        val coefficient = TrendAnalytics.correlate(observations, { it.x }, { it.y }, "x", "y")!!.coefficient

        assertTrue(coefficient <= 1.0 && coefficient >= -1.0)
    }

    @Test
    @DisplayName("the correlation carries the labels needed to warn the reader")
    fun correlationCarriesLabels() {
        val correlation = TrendAnalytics.correlate(
            pairs(1.0 to 2.0, 2.0 to 4.0, 3.0 to 6.0),
            { it.x }, { it.y }, "Calories", "Weight"
        )!!

        assertEquals("Calories", correlation.xLabel)
        assertEquals("Weight", correlation.yLabel)
    }

    @Test
    @DisplayName("correlation is symmetric: swapping the axes does not flip the sign")
    fun correlationIsSymmetric() {
        val observations = pairs(1.0 to 5.0, 2.0 to 3.0, 3.0 to 8.0, 4.0 to 2.0, 5.0 to 6.0)

        val forward = TrendAnalytics.correlate(observations, { it.x }, { it.y }, "a", "b")!!
        val reversed = TrendAnalytics.correlate(observations, { it.y }, { it.x }, "b", "a")!!

        assertEquals(forward.coefficient, reversed.coefficient, 0.0001)
    }
}
