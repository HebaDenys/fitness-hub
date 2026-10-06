package io.github.hebadenys.fitnesshub.core.scale

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One weigh-in captured passively from a scale.
 *
 * Only hardware-reported values are stored here. The unique index on
 * (device address, measuredAtMillis) makes re-scanning the same broadcast
 * idempotent, which matters because the scale keeps advertising for several
 * seconds after someone steps off.
 */
@Entity(
    tableName = "scale_measurements",
    indices = [Index(value = ["deviceAddress", "measuredAtMillis"], unique = true)]
)
data class ScaleMeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceAddress: String,
    val measuredAtMillis: Long,
    val weightKg: Double?,
    val impedanceOhms: Double?,
    val heartRateBpm: Long?,
    val profileSlot: Int?,
    @ColumnInfo(defaultValue = "MEASURED") val provenance: String = PROVENANCE_MEASURED,
    val algorithm: String? = null
) {
    companion object {
        const val PROVENANCE_MEASURED = "MEASURED"
        const val PROVENANCE_ESTIMATE = "ESTIMATE"
        const val PROVENANCE_IMPORTED = "IMPORTED"
    }
}

/** The user's own height, age and sex, required to estimate body composition. */
@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val heightCm: Double?,
    val ageYears: Int?,
    val sex: String?
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}

/**
 * A body composition estimate derived from a raw weigh-in.
 *
 * [algorithmId] is mandatory: an estimate without a named formula would be
 * indistinguishable from a measurement, which the project forbids.
 */
@Entity(
    tableName = "body_composition_estimates",
    indices = [Index(value = ["measuredAtMillis"], unique = true)]
)
data class BodyCompositionEstimateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val measuredAtMillis: Long,
    val bodyFatPercent: Double?,
    val leanMassKg: Double?,
    val bodyWaterPercent: Double?,
    val basalMetabolicRateKcal: Double?,
    val visceralFatIndex: Double?,
    @ColumnInfo(defaultValue = "ESTIMATE") val provenance: String = ScaleMeasurementEntity.PROVENANCE_ESTIMATE,
    val algorithm: String
)
