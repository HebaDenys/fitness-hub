package io.github.hebadenys.fitnesshub.core.model

import java.time.LocalDate

/**
 * Pure domain representation of one calendar day of health data.
 * Missing or permission-denied metrics are null; a recorded zero stays zero.
 */
data class DailySummary(
    val date: LocalDate,
    val steps: Long? = null,
    val distanceMeters: Double? = null,
    val activeCalories: Double? = null,
    val totalCalories: Double? = null,
    val sleepMinutes: Long? = null,
    val restingHeartRate: Long? = null,
    val oxygenSaturation: Double? = null,
    val weightKg: Double? = null,
    val bodyFatPercent: Double? = null,
    val dataOrigins: Set<String> = emptySet(),
    val provenance: String = PROVENANCE_MEASURED,
    val algorithm: String? = null
) {
    companion object {
        const val PROVENANCE_MEASURED = "MEASURED"
        const val PROVENANCE_ESTIMATE = "ESTIMATE"
    }
}
