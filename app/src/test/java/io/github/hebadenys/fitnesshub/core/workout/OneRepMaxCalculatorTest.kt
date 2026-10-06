package io.github.hebadenys.fitnesshub.core.workout

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * The one-rep-maximum formulas are published estimates, so the tests pin them to
 * the published equations rather than to whatever the code happens to produce.
 */
class OneRepMaxCalculatorTest {

    @Test
    @DisplayName("Epley matches the published equation")
    fun epleyMatchesFormula() {
        // 100 kg x 5 reps: 100 * (1 + 5/30) = 116.667
        assertEquals(116.6667, OneRepMaxCalculator.epley(100.0, 5)!!, 0.001)
        assertEquals(100.0 * (1.0 + 10 / 30.0), OneRepMaxCalculator.epley(100.0, 10)!!, 0.001)
    }

    @Test
    @DisplayName("Brzycki matches the published equation")
    fun brzyckiMatchesFormula() {
        // 100 kg x 5 reps: 100 * 36 / 32 = 112.5
        assertEquals(112.5, OneRepMaxCalculator.brzycki(100.0, 5)!!, 0.001)
        assertEquals(100.0 * 36.0 / (37.0 - 3), OneRepMaxCalculator.brzycki(100.0, 3)!!, 0.001)
    }

    @Test
    @DisplayName("Lombardi matches the published equation")
    fun lombardiMatchesFormula() {
        assertEquals(100.0 * Math.pow(5.0, 0.10), OneRepMaxCalculator.lombardi(100.0, 5)!!, 0.001)
        assertEquals(100.0 * Math.pow(10.0, 0.10), OneRepMaxCalculator.lombardi(100.0, 10)!!, 0.001)
    }

    @Test
    @DisplayName("a single rep returns the load itself, marked as measured rather than estimated")
    fun singleRep_isMeasured() {
        listOf("epley", "brzycki", "lombardi").forEach { formula ->
            val result = when (formula) {
                "epley" -> OneRepMaxCalculator.epley(120.0, 1)
                "brzycki" -> OneRepMaxCalculator.brzycki(120.0, 1)
                else -> OneRepMaxCalculator.lombardi(120.0, 1)
            }
            assertEquals(120.0, result!!, 0.0001)
        }

        val best = OneRepMaxCalculator.best(120.0, 1)!!
        assertTrue(best.isMeasured)
        assertEquals(OneRepMaxCalculator.SINGLE_REP, best.formula)
    }

    @Test
    @DisplayName("Brzycki is rejected at 37 reps instead of dividing by zero")
    fun brzyckiUndefinedAt37() {
        assertNull(OneRepMaxCalculator.brzycki(100.0, 37))
        assertNull(OneRepMaxCalculator.brzycki(100.0, 40))
        assertNotNull(OneRepMaxCalculator.brzycki(100.0, 36))
    }

    @Test
    @DisplayName("non-positive load or rep counts yield no estimate rather than a zero")
    fun invalidInput_yieldsNull() {
        assertNull(OneRepMaxCalculator.epley(0.0, 5))
        assertNull(OneRepMaxCalculator.epley(-50.0, 5))
        assertNull(OneRepMaxCalculator.epley(100.0, 0))
        assertNull(OneRepMaxCalculator.epley(100.0, -3))
        assertNull(OneRepMaxCalculator.brzycki(100.0, 0))
        assertNull(OneRepMaxCalculator.lombardi(-1.0, 5))
        assertNull(OneRepMaxCalculator.best(0.0, 5))
        assertNull(OneRepMaxCalculator.best(100.0, 0))
    }

    @Test
    @DisplayName("the chosen formula follows the rep range and is always named")
    fun bestPicksFormulaForRepRange() {
        assertEquals(OneRepMaxCalculator.LOMBARDI, OneRepMaxCalculator.best(100.0, 3)!!.formula)
        assertEquals(OneRepMaxCalculator.EPLEY, OneRepMaxCalculator.best(100.0, 8)!!.formula)
        assertEquals(OneRepMaxCalculator.BRZYCKI, OneRepMaxCalculator.best(100.0, 20)!!.formula)
    }

    @Test
    @DisplayName("a multi-rep best estimate is never presented as a measurement")
    fun multiRepEstimate_isNotMeasured() {
        assertFalse(OneRepMaxCalculator.best(100.0, 5)!!.isMeasured)
    }

    @Test
    @DisplayName("all three formulas are exposed so disagreement stays visible")
    fun allExposesEveryFormula() {
        val estimates = OneRepMaxCalculator.all(100.0, 5)

        assertEquals(3, estimates.size)
        assertEquals(
            setOf(OneRepMaxCalculator.EPLEY, OneRepMaxCalculator.BRZYCKI, OneRepMaxCalculator.LOMBARDI),
            estimates.map { it.formula }.toSet()
        )
        assertTrue(estimates.all { it.oneRepMaxKg > 100.0 })
    }

    @Test
    @DisplayName("the single-rep case in all() is a measured entry alongside the formulas")
    fun allIncludesMeasuredSingle() {
        val estimates = OneRepMaxCalculator.all(100.0, 1)

        assertEquals(4, estimates.size)
        assertTrue(estimates.any { it.isMeasured })
        assertEquals(3, estimates.count { !it.isMeasured })
    }

    @Test
    @DisplayName("display rounding uses plate precision rather than false accuracy")
    fun displayRounding() {
        // Five reps selects Lombardi, so the expected value is not the Epley one.
        assertEquals(117.5, OneRepMaxCalculator.best(100.0, 5)!!.displayKg, 0.0001)
        assertEquals(100.0, OneRepMaxCalculator.best(100.0, 1)!!.displayKg, 0.0001)
    }

    @Test
    @DisplayName("heavier loads produce proportionally heavier estimates")
    fun scalesLinearly() {
        val light = OneRepMaxCalculator.best(50.0, 5)!!.oneRepMaxKg
        val heavy = OneRepMaxCalculator.best(100.0, 5)!!.oneRepMaxKg

        assertEquals(light * 2, heavy, 0.0001)
    }

    @Test
    @DisplayName("more reps at the same load never lowers the estimate")
    fun moreReps_neverLower() {
        var previous = 0.0
        for (reps in 1..12) {
            val estimate = OneRepMaxCalculator.best(100.0, reps)!!.oneRepMaxKg
            assertTrue(estimate >= previous, "estimate dropped at $reps reps")
            previous = estimate
        }
    }
}
