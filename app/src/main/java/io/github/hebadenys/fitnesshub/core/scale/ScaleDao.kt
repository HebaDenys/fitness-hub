package io.github.hebadenys.fitnesshub.core.scale

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ScaleDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMeasurement(measurement: ScaleMeasurementEntity): Long

    @Upsert
    suspend fun upsertEstimate(estimate: BodyCompositionEstimateEntity)

    @Upsert
    suspend fun upsertProfile(profile: UserProfileEntity)

    @Query("SELECT * FROM scale_measurements ORDER BY measuredAtMillis DESC LIMIT :limit")
    fun observeMeasurements(limit: Int = 100): Flow<List<ScaleMeasurementEntity>>

    @Query("SELECT * FROM scale_measurements WHERE measuredAtMillis >= :sinceMillis ORDER BY measuredAtMillis DESC")
    suspend fun measurementsSince(sinceMillis: Long): List<ScaleMeasurementEntity>

    @Query("SELECT * FROM body_composition_estimates ORDER BY measuredAtMillis DESC LIMIT :limit")
    fun observeEstimates(limit: Int = 100): Flow<List<BodyCompositionEstimateEntity>>

    @Query("SELECT * FROM body_composition_estimates WHERE measuredAtMillis >= :sinceMillis ORDER BY measuredAtMillis DESC")
    suspend fun estimatesSince(sinceMillis: Long): List<BodyCompositionEstimateEntity>

    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun observeProfile(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE id = 1")
    suspend fun profile(): UserProfileEntity?

    @Query("SELECT * FROM scale_measurements ORDER BY measuredAtMillis")
    suspend fun dumpAll(): List<ScaleMeasurementEntity>

    /** Restore relies on the unique (device, timestamp) index to skip duplicates. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun restoreAll(measurements: List<ScaleMeasurementEntity>): List<Long>
}
