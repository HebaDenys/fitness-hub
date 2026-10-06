package io.github.hebadenys.fitnesshub.core.workout

import kotlin.math.roundToInt

/**
 * One-repetition-maximum estimates.
 *
 * Every formula here is a published estimate, never a measurement: the true
 * 1RM can only be found by attempting it. Each result carries the identifier of
 * the formula that produced it so the UI can name it, in line with the rule
 * that an estimate must never look like a measurement.
 */
object OneRepMaxCalculator {

    const val EPLEY = "epley"
    const val BRZYCKI = "brzycki"
    const val LOMBARDI = "lombardi"

    /**
     * Epley: `1RM = weight * (1 + reps / 30)`.
     * Tends to read slightly high at low rep counts, which is why it is only
     * sensible from about 2 reps upward.
     */
    fun epley(loadKg: Double, reps: Int): Double? {
        if (loadKg <= 0.0 || reps <= 0) return null
        if (reps == 1) return loadKg
        return loadKg * (1.0 + reps / 30.0)
    }

    /**
     * Brzycki: `1RM = weight * 36 / (37 - reps)`.
     * Undefined at 37 reps, so it is rejected there rather than dividing by zero.
     */
    fun brzycki(loadKg: Double, reps: Int): Double? {
        if (loadKg <= 0.0 || reps <= 0) return null
        if (reps == 1) return loadKg
        if (reps >= 37) return null
        return loadKg * 36.0 / (37.0 - reps)
    }

    /**
     * Lombardi: `1RM = weight * reps^0.10`.
     * Included because it behaves better than Epley in the 3-8 rep range that
     * most hypertrophy work lands in.
     */
    fun lombardi(loadKg: Double, reps: Int): Double? {
        if (loadKg <= 0.0 || reps <= 0) return null
        if (reps == 1) return loadKg
        return loadKg * Math.pow(reps.toDouble(), 0.10)
    }

    /**
     * The best-supported estimate for [reps] at [loadKg].
     *
     * Single reps are a real observation, not an estimate, so the load is
     * returned as-is. Beyond that the formula is chosen for the rep range
     * rather than averaged, because averaging two biased estimates does not
     * make either less biased.
     */
    fun best(loadKg: Double, reps: Int): Estimate? {
        if (loadKg <= 0.0 || reps <= 0) return null
        if (reps == 1) return Estimate(loadKg, SINGLE_REP, isMeasured = true)
        val candidate = when {
            reps in 2..5 -> lombardi(loadKg, reps)
            reps in 6..12 -> epley(loadKg, reps)
            else -> brzycki(loadKg, reps)
        } ?: return null
        val formula = when {
            reps in 2..5 -> LOMBARDI
            reps in 6..12 -> EPLEY
            else -> BRZYCKI
        }
        return Estimate(candidate, formula, isMeasured = false)
    }

    /** All three formulas side by side, so a disagreement between them stays visible. */
    fun all(loadKg: Double, reps: Int): List<Estimate> {
        if (loadKg <= 0.0 || reps <= 0) return emptyList()
        val estimates = mutableListOf<Estimate>()
        if (reps == 1) estimates += Estimate(loadKg, SINGLE_REP, isMeasured = true)
        epley(loadKg, reps)?.let { estimates += Estimate(it, EPLEY, isMeasured = false) }
        brzycki(loadKg, reps)?.let { estimates += Estimate(it, BRZYCKI, isMeasured = false) }
        lombardi(loadKg, reps)?.let { estimates += Estimate(it, LOMBARDI, isMeasured = false) }
        return estimates
    }

    const val SINGLE_REP = "measured_single"

    data class Estimate(
        val oneRepMaxKg: Double,
        val formula: String,
        val isMeasured: Boolean
    ) {
        /** Rounded to a realistic plate precision, never implying false accuracy. */
        val displayKg: Double get() = (oneRepMaxKg * 10).roundToInt() / 10.0
    }
}
