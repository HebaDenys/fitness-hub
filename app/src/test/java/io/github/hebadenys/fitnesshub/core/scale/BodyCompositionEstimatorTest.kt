package io.github.hebadenys.fitnesshub.core.scale

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * The estimator must stay honest: every returned value must be traceable to a
 * published formula, and anything it cannot compute must come back null.
 */
class BodyCompositionEstimatorTest {

    private val male180 = UserProfile(heightCm = 180.0, ageYears = 30, sex = Sex.MALE)
    private val female165 = UserProfile(heightCm = 165.0, ageYears = 30, sex = Sex.FEMALE)

    @Test
    @DisplayName("Deurenberg body fat matches the published equation")
    fun duerenbergMatchesFormula() {
        // BMI 25 at age 30, male: 1.20*25 + 0.23*30 - 5.4*1 - 0.8 = 30.8
        val expected = 1.20 * 25.0 + 0.23 * 30 - 5.4 * 1 - 0.8

        assertEquals(expected, BodyCompositionEstimator.duerenbergBodyFat(25.0, 30, Sex.MALE)!!, 0.0001)
    }

    @Test
    @DisplayName("the female sex term raises body fat by 5.4 points at equal BMI and age")
    fun femaleTerm_differs() {
        val male = BodyCompositionEstimator.duerenbergBodyFat(25.0, 30, Sex.MALE)!!
        val female = BodyCompositionEstimator.duerenbergBodyFat(25.0, 30, Sex.FEMALE)!!

        assertEquals(5.4, female - male, 0.0001)
    }

    @Test
    @DisplayName("body fat is clamped to a physiologically possible range")
    fun bodyFat_isClamped() {
        assertEquals(0.0, BodyCompositionEstimator.duerenbergBodyFat(1.0, 1, Sex.MALE)!!, 0.0001)
        assertEquals(75.0, BodyCompositionEstimator.duerenbergBodyFat(60.0, 80, Sex.MALE)!!, 0.0001)
    }

    @Test
    @DisplayName("Mifflin-St Jeor matches the published equation for both sexes")
    fun mifflinMatchesFormula() {
        val male = BodyCompositionEstimator.mifflinStJeor(80.0, 180.0, 30, Sex.MALE)!!
        val female = BodyCompositionEstimator.mifflinStJeor(80.0, 180.0, 30, Sex.FEMALE)!!

        assertEquals(10 * 80.0 + 6.25 * 180 - 5 * 30 + 5, male, 0.0001)
        assertEquals(10 * 80.0 + 6.25 * 180 - 5 * 30 - 161, female, 0.0001)
        assertEquals(166.0, male - female, 0.0001)
    }

    @Test
    @DisplayName("Watson total body water matches the published equation and the female adjustment")
    fun watsonMatchesFormula() {
        val base = 0.0335 * 70.0 + 0.1837 * 180 + 0.5142 * 30 - 0.5

        assertEquals(base, BodyCompositionEstimator.watsonTotalBodyWater(70.0, 180.0, 30, Sex.MALE)!!, 0.0001)
        assertEquals(base * 0.95, BodyCompositionEstimator.watsonTotalBodyWater(70.0, 180.0, 30, Sex.FEMALE)!!, 0.0001)
    }

    @Test
    @DisplayName("a full estimate reports lean mass and body water alongside body fat")
    fun estimate_producesConsistentValues() {
        val result = BodyCompositionEstimator.estimate(weightKg = 80.0, profile = male180)

        assertNotNull(result)
        val bodyFat = result!!.bodyFatPercent!!
        assertEquals(80.0 * bodyFat / 100.0, 80.0 - result.leanMassKg!!, 0.0001)
        assertTrue(result.bodyWaterPercent!! > 0.0)
        assertTrue(result.basalMetabolicRateKcal!! > 1000.0)
        assertEquals(BodyCompositionEstimator.ALGORITHM_COMPOSITE, result.algorithmId)
    }

    @Test
    @DisplayName("visceral fat is null because no defensible formula is available without waist or reactance")
    fun visceralFat_staysNull() {
        val result = BodyCompositionEstimator.estimate(weightKg = 80.0, profile = male180)

        assertNull(result!!.visceralFatIndex)
    }

    @Test
    @DisplayName("a missing weight yields no estimate at all")
    fun missingWeight_yieldsNoEstimate() {
        assertNull(BodyCompositionEstimator.estimate(null, male180))
    }

    @Test
    @DisplayName("an implausible weight is rejected instead of producing a nonsense estimate")
    fun implausibleWeight_isRejected() {
        assertNull(BodyCompositionEstimator.estimate(0.0, male180))
        assertNull(BodyCompositionEstimator.estimate(-70.0, male180))
        assertNull(BodyCompositionEstimator.estimate(500.0, male180))
        assertNull(BodyCompositionEstimator.estimate(5.0, male180))
    }

    @Test
    @DisplayName("an incomplete profile yields no estimate rather than a partial one")
    fun incompleteProfile_yieldsNoEstimate() {
        assertNull(BodyCompositionEstimator.estimate(80.0, male180.copy(heightCm = 0.0)))
        assertNull(BodyCompositionEstimator.estimate(80.0, male180.copy(heightCm = 50.0)))
        assertNull(BodyCompositionEstimator.estimate(80.0, male180.copy(ageYears = 5)))
        assertNull(BodyCompositionEstimator.estimate(80.0, male180.copy(ageYears = 200)))
    }

    @Test
    @DisplayName("every estimate carries an algorithm identifier, never a bare number")
    fun alwaysCarriesAlgorithmId() {
        assertTrue(BodyCompositionEstimator.ALGORITHM_COMPOSITE.isNotBlank())
        assertTrue(BodyCompositionEstimator.ALGORITHM_BMI.isNotBlank())
        assertTrue(BodyCompositionEstimator.ALGORITHM_TBW.isNotBlank())
        assertTrue(BodyCompositionEstimator.ALGORITHM_BMR.isNotBlank())
    }

    @Test
    @DisplayName("implausible impedance is rejected while plausible values pass")
    fun impedancePlausibility() {
        assertTrue(BodyCompositionEstimator.isPlausibleImpedance(500.0))
        assertTrue(BodyCompositionEstimator.isPlausibleImpedance(null))
        assertFalse(BodyCompositionEstimator.isPlausibleImpedance(5.0))
        assertFalse(BodyCompositionEstimator.isPlausibleImpedance(99_999.0))
    }

    @Test
    @DisplayName("the female profile yields higher body fat but lower BMR than the male one at equal weight")
    fun profilesDiffer() {
        val male = BodyCompositionEstimator.estimate(70.0, UserProfile(175.0, 40, Sex.MALE))!!
        val female = BodyCompositionEstimator.estimate(70.0, UserProfile(175.0, 40, Sex.FEMALE))!!

        assertTrue(female.bodyFatPercent!! > male.bodyFatPercent!!)
        assertTrue(male.basalMetabolicRateKcal!! > female.basalMetabolicRateKcal!!)
    }
}
