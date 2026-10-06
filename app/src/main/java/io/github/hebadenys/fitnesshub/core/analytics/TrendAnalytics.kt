package io.github.hebadenys.fitnesshub.core.analytics

import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.sqrt

/** One day of paired measurements, either of which may be absent. */
data class Observation(val date: LocalDate, val x: Double?, val y: Double?)

/**
 * The statistical layer behind the cross-domain cards.
 *
 * Two deliberate constraints shape everything here:
 *
 * - Fluid measurements (weight, body water) are smoothed before display, because
 *   day-to-day noise otherwise reads as real change.
 * - Correlation is never presented as effect. The maths below measures how two
 *   series move together and nothing more; the caller must carry the
 *   "correlation is not causation" advisory alongside any result.
 */
object TrendAnalytics {

    /**
     * Exponential moving average with smoothing factor derived from [days].
     *
     * Gaps are not interpolated: a missing day carries no weight, so the average
     * reflects only the days actually measured. Points before the first
     * observation are null rather than zero, because a smoothed curve should not
     * claim a value that was never computed.
     */
    fun exponentialMovingAverage(
        observations: List<Observation>,
        days: Int,
        selector: (Observation) -> Double?
    ): Map<LocalDate, Double> {
        require(days > 0) { "smoothing window must be positive" }
        val alpha = 2.0 / (days + 1.0)
        var smoothed: Double? = null
        val result = linkedMapOf<LocalDate, Double>()
        for (observation in observations) {
            val value = selector(observation) ?: continue
            smoothed = if (smoothed == null) value else alpha * value + (1 - alpha) * smoothed
            result[observation.date] = smoothed
        }
        return result
    }

    /** Arithmetic mean over the days that actually carry a value, or null when none do. */
    fun mean(observations: List<Observation>, selector: (Observation) -> Double?): Double? {
        var total = 0.0
        var count = 0
        observations.forEach {
            val value = selector(it) ?: return@forEach
            total += value
            count++
        }
        return if (count == 0) null else total / count
    }

    data class Correlation(
        val coefficient: Double,
        val sampleSize: Int,
        val xLabel: String,
        val yLabel: String
    ) {
        /** Below this, a coefficient is too small to describe as more than no relationship. */
        val isNegligible: Boolean get() = abs(coefficient) < NEGLIGIBLE_THRESHOLD

        val direction: Direction
            get() = when {
                isNegligible -> Direction.NONE
                coefficient > 0 -> Direction.POSITIVE
                else -> Direction.NEGATIVE
            }

        /** Qualitative band, deliberately coarse: a number this small is noise. */
        val strength: Strength
            get() = when {
                isNegligible -> Strength.NONE
                abs(coefficient) >= STRONG_THRESHOLD -> Strength.STRONG
                else -> Strength.MODERATE
            }
    }

    enum class Direction { POSITIVE, NEGATIVE, NONE }
    enum class Strength { NONE, MODERATE, STRONG }

    /**
     * Pearson correlation over the days where both series have a value.
     *
     * Returns null below a three-day overlap, since a coefficient computed from
     * one or two paired points is arithmetically valid and completely
     * meaningless.
     */
    fun correlate(
        observations: List<Observation>,
        x: (Observation) -> Double?,
        y: (Observation) -> Double?,
        xLabel: String,
        yLabel: String
    ): Correlation? {
        val paired = observations.mapNotNull { observation ->
            val xValue = x(observation) ?: return@mapNotNull null
            val yValue = y(observation) ?: return@mapNotNull null
            xValue to yValue
        }
        if (paired.size < MIN_PAIRED_SAMPLES) return null

        val count = paired.size.toDouble()
        val meanX = paired.sumOf { it.first } / count
        val meanY = paired.sumOf { it.second } / count

        var covariance = 0.0
        var varianceX = 0.0
        var varianceY = 0.0
        for ((xValue, yValue) in paired) {
            val dx = xValue - meanX
            val dy = yValue - meanY
            covariance += dx * dy
            varianceX += dx * dx
            varianceY += dy * dy
        }
        val denominator = sqrt(varianceX * varianceY)
        if (denominator == 0.0) return null

        return Correlation(covariance / denominator, paired.size, xLabel, yLabel)
    }

    const val MIN_PAIRED_SAMPLES = 3
    const val NEGLIGIBLE_THRESHOLD = 0.2
    const val STRONG_THRESHOLD = 0.6
}
