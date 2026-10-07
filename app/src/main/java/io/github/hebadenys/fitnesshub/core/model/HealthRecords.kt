package io.github.hebadenys.fitnesshub.core.model

import java.time.Instant

/** A sleep session interval normalized from Health Connect, with its source package. */
data class SleepInterval(
    val externalId: String,
    val start: Instant,
    val end: Instant,
    val dataOrigin: String
)

/** An exercise session normalized from Health Connect. */
data class ExerciseSessionData(
    val externalId: String,
    val title: String?,
    val type: Int,
    val startMillis: Long,
    val endMillis: Long,
    val dataOrigin: String
)

/** A whole-number sample reading (heart rate, resting heart rate). */
data class Sample(
    val timeEpochMillis: Long,
    val value: Long,
    val dataOrigin: String
)

/** A decimal sample reading (SpO2, weight, body fat). */
data class DecimalSample(
    val timeEpochMillis: Long,
    val value: Double,
    val dataOrigin: String,
    val externalId: String? = null
)

/** A timestamp plus its source package, used to track aggregate metric origins. */
data class TimedOrigin(
    val timeEpochMillis: Long,
    val dataOrigin: String
)
