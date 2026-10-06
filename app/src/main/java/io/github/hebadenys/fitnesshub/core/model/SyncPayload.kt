package io.github.hebadenys.fitnesshub.core.model

import java.time.LocalDate

/** Stable metric identifiers used to gate reads on granted permissions. */
object HealthMetrics {
    const val STEPS = "STEPS"
    const val DISTANCE = "DISTANCE"
    const val ACTIVE_CALORIES = "ACTIVE_CALORIES"
    const val TOTAL_CALORIES = "TOTAL_CALORIES"
    const val EXERCISE = "EXERCISE"
    const val HEART_RATE = "HEART_RATE"
    const val RESTING_HEART_RATE = "RESTING_HEART_RATE"
    const val OXYGEN_SATURATION = "OXYGEN_SATURATION"
    const val SLEEP = "SLEEP"
    const val WEIGHT = "WEIGHT"
    const val BODY_FAT = "BODY_FAT"

    /** Every metric the app can import. */
    val ALL: Set<String> = setOf(
        STEPS, DISTANCE, ACTIVE_CALORIES, TOTAL_CALORIES, EXERCISE, HEART_RATE,
        RESTING_HEART_RATE, OXYGEN_SATURATION, SLEEP, WEIGHT, BODY_FAT
    )
}

/**
 * Per-day aggregate values. Each field is null when the aggregation result has no
 * entry for that metric (no data recorded, or the metric was not granted/read).
 */
data class DayAggregate(
    val date: LocalDate,
    val steps: Long? = null,
    val distanceMeters: Double? = null,
    val activeCalories: Double? = null,
    val totalCalories: Double? = null
)

/** Everything read from Health Connect for one time range, normalized to pure types. */
data class RangePayload(
    val sleep: List<SleepInterval> = emptyList(),
    val exercises: List<ExerciseSessionData> = emptyList(),
    val heartRateSamples: List<Sample> = emptyList(),
    val oxygenSamples: List<DecimalSample> = emptyList(),
    val restingHeartRateSamples: List<Sample> = emptyList(),
    val weights: List<DecimalSample> = emptyList(),
    val bodyFats: List<DecimalSample> = emptyList(),
    val metricOrigins: Map<String, List<TimedOrigin>> = emptyMap()
)

/** One page of the Health Connect Changes token flow, normalized to pure types. */
data class HealthChangesPage(
    val tokenExpired: Boolean,
    val hasMore: Boolean,
    val nextToken: String,
    val changeCount: Int,
    val affectedDates: Set<LocalDate>,
    val deletedRecordIds: List<String>,
    val upsertedExerciseIds: List<String>
)
