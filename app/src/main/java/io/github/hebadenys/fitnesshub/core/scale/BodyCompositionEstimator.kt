package io.github.hebadenys.fitnesshub.core.scale

/** The physical profile needed to turn raw measurements into body composition estimates. */
data class UserProfile(
    val heightCm: Double,
    val ageYears: Int,
    val sex: Sex
)

enum class Sex {
    FEMALE,
    MALE;

    val isMale: Boolean get() = this == MALE
}

data class BodyComposition(
    val bodyFatPercent: Double?,
    val leanMassKg: Double?,
    val bodyWaterPercent: Double?,
    val basalMetabolicRateKcal: Double?,
    val visceralFatIndex: Double?,
    val algorithmId: String
)

/**
 * Estimates body composition from a raw weigh-in.
 *
 * Every value produced here is a published community formula, never a vendor
 * number: the scale broadcasts only weight, impedance and heart rate, so the
 * roughly twenty-five indices Xiaomi shows in its app never travel over the air
 * and are not reproduced here.
 *
 * Each estimate is returned with the identifier of the formula that produced
 * it, and anything that cannot be computed honestly is returned as null rather
 * than filled in. In particular no impedance-based lean mass is derived: single
 * frequency bioelectrical impedance needs reactance, which MiBeacon does not
 * broadcast, and a resistance-only equation would be a guess.
 */
object BodyCompositionEstimator {

    const val ALGORITHM_BMI = "deurenberg_bmi_v1"
    const val ALGORITHM_TBW = "watson_tbw_v1"
    const val ALGORITHM_BMR = "mifflin_st_jeor_v1"
    const val ALGORITHM_COMPOSITE = "composite_bmi_tbw_bmr_v1"

    /**
     * @return null when [weightKg] is missing or implausible, or the profile is
     * incomplete; a null result means "unknown", never zero.
     */
    fun estimate(weightKg: Double?, profile: UserProfile): BodyComposition? {
        if (weightKg == null || weightKg <= MIN_PLAUSIBLE_WEIGHT_KG || weightKg > MAX_PLAUSIBLE_WEIGHT_KG) return null
        if (profile.heightCm <= MIN_PLAUSIBLE_HEIGHT_CM || profile.ageYears !in MIN_AGE..MAX_AGE) return null

        val heightM = profile.heightCm / 100.0
        val bmi = weightKg / (heightM * heightM)

        val bodyFat = duerenbergBodyFat(bmi, profile.ageYears, profile.sex)
        val fatMassKg = bodyFat?.let { weightKg * it / 100.0 }
        val leanMass = fatMassKg?.let { weightKg - it }

        val totalBodyWaterLitres = watsonTotalBodyWater(weightKg, heightCm = profile.heightCm, ageYears = profile.ageYears, sex = profile.sex)
        val bodyWaterPercent = totalBodyWaterLitres?.let { litres ->
            // 1 kg of water is 1 L, so total water mass in kg divided by body mass.
            litres / weightKg * 100.0
        }
        val bmr = mifflinStJeor(weightKg, profile.heightCm, profile.ageYears, profile.sex)

        return BodyComposition(
            bodyFatPercent = bodyFat,
            leanMassKg = leanMass,
            bodyWaterPercent = bodyWaterPercent,
            basalMetabolicRateKcal = bmr,
            visceralFatIndex = null,
            algorithmId = ALGORITHM_COMPOSITE
        )
    }

    /**
     * Deurenberg body fat percentage from body mass index:
     * `BF% = 1.20 * BMI + 0.23 * age - 5.4 * sex - 0.8`, where sex is 1 for
     * male and 0 for female. Published in Br J Nutr 1991.
     */
    fun duerenbergBodyFat(bmi: Double, ageYears: Int, sex: Sex): Double? {
        if (bmi <= 0.0) return null
        val sexTerm = if (sex.isMale) 1.0 else 0.0
        val percentage = 1.20 * bmi + 0.23 * ageYears - 5.4 * sexTerm - 0.8
        return percentage.coerceIn(0.0, 75.0)
    }

    /**
     * Watson total body water in litres, computed for fat-free mass and
     * converted to litres of water:
     * `TBW(fat-free mass, L) = 0.0335 * FFM + 0.1837 * height + 0.5142 * age - 0.5`
     * with 0.95 applied for women.
     */
    fun watsonTotalBodyWater(
        weightKg: Double,
        heightCm: Double,
        ageYears: Int,
        sex: Sex,
        fatFreeMassKg: Double = weightKg
    ): Double? {
        if (fatFreeMassKg <= 0.0 || heightCm <= 0.0) return null
        val litres = 0.0335 * fatFreeMassKg + 0.1837 * heightCm + 0.5142 * ageYears - 0.5
        val adjusted = if (sex.isMale) litres else litres * 0.95
        return adjusted.takeIf { it > 0.0 }
    }

    /**
     * Mifflin-St Jeor basal metabolic rate in kcal/day:
     * `BMR = 10 * weight + 6.25 * height - 5 * age + 5` for men and `- 161` for
     * women. Published in Am J Clin Nutr 1990.
     */
    fun mifflinStJeor(weightKg: Double, heightCm: Double, ageYears: Int, sex: Sex): Double? {
        if (weightKg <= 0.0 || heightCm <= 0.0) return null
        val base = 10.0 * weightKg + 6.25 * heightCm - 5.0 * ageYears
        return (if (sex.isMale) base + 5.0 else base - 161.0).takeIf { it > 0.0 }
    }

    /** Plausibility guard shared with the scale connector: impedance outside this range is treated as noise. */
    fun isPlausibleImpedance(ohms: Double?): Boolean =
        ohms == null || ohms in MIN_PLAUSIBLE_IMPEDANCE_OHMS..MAX_PLAUSIBLE_IMPEDANCE_OHMS

    private const val MIN_PLAUSIBLE_WEIGHT_KG = 10.0
    private const val MAX_PLAUSIBLE_WEIGHT_KG = 350.0
    private const val MIN_PLAUSIBLE_HEIGHT_CM = 100.0
    private const val MIN_AGE = 10
    private const val MAX_AGE = 120
    private const val MIN_PLAUSIBLE_IMPEDANCE_OHMS = 100.0
    private const val MAX_PLAUSIBLE_IMPEDANCE_OHMS = 1_500.0
}
