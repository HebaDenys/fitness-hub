package io.github.hebadenys.fitnesshub.core.scale

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** One validated CSV row. This is not a Room table. */
data class ScaleHistoryRow(
    val measurement: ScaleMeasurementEntity,
    val composition: BodyCompositionEstimateEntity?
)

@Dao
interface ScaleDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMeasurement(measurement: ScaleMeasurementEntity): Long

    @Query("SELECT * FROM body_composition_estimates WHERE measuredAtMillis = :timestamp LIMIT 1")
    suspend fun compositionAt(timestamp: Long): BodyCompositionEstimateEntity?

    @Upsert
    suspend fun saveEstimateRow(estimate: BodyCompositionEstimateEntity)

    /** Match the unique timestamp before upserting by primary key. Never overwrite vendor history. */
    @Transaction
    suspend fun upsertEstimate(estimate: BodyCompositionEstimateEntity) {
        val existing = compositionAt(estimate.measuredAtMillis)
        if (existing?.provenance == ScaleMeasurementEntity.PROVENANCE_IMPORTED) return
        saveEstimateRow(estimate.copy(id = existing?.id ?: estimate.id))
    }

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertImportedComposition(estimate: BodyCompositionEstimateEntity)

    /**
     * Import the complete validated batch in one Room transaction. Repeated
     * measurements are a no-op, including their composition. A conflicting
     * timestamp from another source aborts rather than attaching the wrong
     * composition. Room rolls back the batch if any statement fails.
     */
    @Transaction
    suspend fun importHistoryRows(rows: List<ScaleHistoryRow>): Int {
        var inserted = 0
        for (row in rows) {
            if (insertMeasurement(row.measurement) == -1L) continue
            row.composition?.let { insertImportedComposition(it) }
            inserted++
        }
        return inserted
    }

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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun restoreAll(measurements: List<ScaleMeasurementEntity>): List<Long>
}
