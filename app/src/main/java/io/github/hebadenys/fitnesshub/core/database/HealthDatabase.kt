package io.github.hebadenys.fitnesshub.core.database

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import io.github.hebadenys.fitnesshub.core.model.DailySummary
import io.github.hebadenys.fitnesshub.core.nutrition.FoodEntity
import io.github.hebadenys.fitnesshub.core.nutrition.NutritionDao
import io.github.hebadenys.fitnesshub.core.nutrition.NutritionDailyEntity
import io.github.hebadenys.fitnesshub.core.nutrition.NutritionEntryEntity
import io.github.hebadenys.fitnesshub.core.scale.BodyCompositionEstimateEntity
import io.github.hebadenys.fitnesshub.core.scale.ScaleDao
import io.github.hebadenys.fitnesshub.core.scale.ScaleMeasurementEntity
import io.github.hebadenys.fitnesshub.core.scale.UserProfileEntity
import io.github.hebadenys.fitnesshub.core.workout.WorkoutDao
import io.github.hebadenys.fitnesshub.core.workout.WorkoutExerciseEntity
import io.github.hebadenys.fitnesshub.core.workout.WorkoutSessionEntity
import io.github.hebadenys.fitnesshub.core.workout.WorkoutSetEntity
import io.github.hebadenys.fitnesshub.core.workout.WorkoutTemplateEntity
import io.github.hebadenys.fitnesshub.core.workout.WorkoutTemplateExerciseEntity
import io.github.hebadenys.fitnesshub.core.xiaomi.storage.SourceIdentityEntity
import io.github.hebadenys.fitnesshub.core.xiaomi.storage.XiaomiBindingEntity
import io.github.hebadenys.fitnesshub.core.xiaomi.storage.XiaomiSnapshotEntity
import io.github.hebadenys.fitnesshub.core.xiaomi.storage.XiaomiCheckpointEntity
import io.github.hebadenys.fitnesshub.core.xiaomi.storage.XiaomiArchiveDao
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "daily_health")
data class DailyHealthEntity(
    @PrimaryKey val date: String,
    val steps: Long? = null,
    val distanceMeters: Double? = null,
    val activeCalories: Double? = null,
    val totalCalories: Double? = null,
    val sleepMinutes: Long? = null,
    val restingHeartRate: Long? = null,
    val oxygenSaturation: Double? = null,
    val weightKg: Double? = null,
    val bodyFatPercent: Double? = null,
    val syncedAt: Long = System.currentTimeMillis(),
    val dataOrigins: String = "",
    @ColumnInfo(defaultValue = "MEASURED") val provenance: String = DailySummary.PROVENANCE_MEASURED,
    val algorithm: String? = null
)

@Entity(tableName = "exercise_sessions", indices = [Index(value = ["externalId"], unique = true)])
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val externalId: String,
    val title: String?,
    val type: Int,
    val startMillis: Long,
    val endMillis: Long,
    val source: String,
    @ColumnInfo(defaultValue = "MEASURED") val provenance: String = DailySummary.PROVENANCE_MEASURED,
    val algorithm: String? = null
)

@Entity(tableName = "heart_rate_samples", indices = [Index(value = ["timeEpochMillis", "dataOrigin"], unique = true)])
data class HeartRateSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val timeEpochMillis: Long,
    val bpm: Long,
    val dataOrigin: String,
    @ColumnInfo(defaultValue = "MEASURED") val provenance: String = DailySummary.PROVENANCE_MEASURED,
    val algorithm: String? = null
)

@Entity(tableName = "oxygen_saturation_readings", indices = [Index(value = ["timeEpochMillis", "dataOrigin"], unique = true)])
data class OxygenSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val timeEpochMillis: Long,
    val percentage: Double,
    val dataOrigin: String,
    @ColumnInfo(defaultValue = "MEASURED") val provenance: String = DailySummary.PROVENANCE_MEASURED,
    val algorithm: String? = null
)

@Entity(tableName = "resting_heart_rate_readings", indices = [Index(value = ["timeEpochMillis", "dataOrigin"], unique = true)])
data class RestingHeartRateSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val timeEpochMillis: Long,
    val bpm: Long,
    val dataOrigin: String,
    @ColumnInfo(defaultValue = "MEASURED") val provenance: String = DailySummary.PROVENANCE_MEASURED,
    val algorithm: String? = null
)

@Entity(
    tableName = "hc_weight_samples",
    indices = [Index(value = ["timeEpochMillis", "dataOrigin"])]
)
data class HealthWeightSampleEntity(
    @PrimaryKey val recordId: String,
    val date: String,
    val timeEpochMillis: Long,
    val kilograms: Double,
    val dataOrigin: String
)

@Entity(
    tableName = "hc_body_fat_samples",
    indices = [Index(value = ["timeEpochMillis", "dataOrigin"])]
)
data class HealthBodyFatSampleEntity(
    @PrimaryKey val recordId: String,
    val date: String,
    val timeEpochMillis: Long,
    val percentage: Double,
    val dataOrigin: String
)

@Dao
interface HealthDao {
    @Query("SELECT * FROM daily_health ORDER BY date DESC")
    fun observeDaily(): Flow<List<DailyHealthEntity>>
    @Query("SELECT * FROM hc_weight_samples ORDER BY timeEpochMillis DESC, recordId")
    fun observeWeightSamples(): Flow<List<HealthWeightSampleEntity>>
    @Query("SELECT * FROM hc_body_fat_samples ORDER BY timeEpochMillis DESC, recordId")
    fun observeBodyFatSamples(): Flow<List<HealthBodyFatSampleEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDaily(items: List<DailyHealthEntity>)
    @Query("SELECT * FROM daily_health ORDER BY date")
    suspend fun dumpDaily(): List<DailyHealthEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExercises(items: List<ExerciseEntity>)
    @Query("SELECT externalId FROM exercise_sessions WHERE endMillis >= :startMillis AND startMillis <= :endMillis")
    suspend fun exerciseExternalIds(startMillis: Long, endMillis: Long): List<String>
    @Query("SELECT externalId FROM exercise_sessions WHERE externalId IN (:ids)")
    suspend fun exerciseIdsIn(ids: List<String>): List<String>
    @Query("DELETE FROM exercise_sessions WHERE externalId IN (:ids)")
    suspend fun deleteExercisesById(ids: List<String>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHeartRateSamples(items: List<HeartRateSampleEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOxygenSamples(items: List<OxygenSampleEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRestingHeartRateSamples(items: List<RestingHeartRateSampleEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWeightSamples(items: List<HealthWeightSampleEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBodyFatSamples(items: List<HealthBodyFatSampleEntity>)
    @Query("DELETE FROM heart_rate_samples WHERE timeEpochMillis >= :startMillis AND timeEpochMillis < :endMillis")
    suspend fun deleteHeartRateSamplesBetween(startMillis: Long, endMillis: Long)
    @Query("DELETE FROM oxygen_saturation_readings WHERE timeEpochMillis >= :startMillis AND timeEpochMillis < :endMillis")
    suspend fun deleteOxygenSamplesBetween(startMillis: Long, endMillis: Long)
    @Query("DELETE FROM resting_heart_rate_readings WHERE timeEpochMillis >= :startMillis AND timeEpochMillis < :endMillis")
    suspend fun deleteRestingHeartRateSamplesBetween(startMillis: Long, endMillis: Long)
    @Query("DELETE FROM hc_weight_samples WHERE timeEpochMillis >= :startMillis AND timeEpochMillis < :endMillis")
    suspend fun deleteWeightSamplesBetween(startMillis: Long, endMillis: Long)
    @Query("DELETE FROM hc_body_fat_samples WHERE timeEpochMillis >= :startMillis AND timeEpochMillis < :endMillis")
    suspend fun deleteBodyFatSamplesBetween(startMillis: Long, endMillis: Long)
    @Query("SELECT * FROM hc_weight_samples ORDER BY timeEpochMillis, recordId")
    suspend fun dumpWeightSamples(): List<HealthWeightSampleEntity>
    @Query("SELECT * FROM hc_body_fat_samples ORDER BY timeEpochMillis, recordId")
    suspend fun dumpBodyFatSamples(): List<HealthBodyFatSampleEntity>
}

@Database(
    entities = [
        DailyHealthEntity::class, ExerciseEntity::class, HeartRateSampleEntity::class,
        OxygenSampleEntity::class, RestingHeartRateSampleEntity::class,
        HealthWeightSampleEntity::class, HealthBodyFatSampleEntity::class,
        FoodEntity::class, NutritionEntryEntity::class, NutritionDailyEntity::class,
        ScaleMeasurementEntity::class, BodyCompositionEstimateEntity::class, UserProfileEntity::class,
        WorkoutExerciseEntity::class, WorkoutSessionEntity::class, WorkoutSetEntity::class,
        WorkoutTemplateEntity::class, WorkoutTemplateExerciseEntity::class,
        SourceIdentityEntity::class, XiaomiBindingEntity::class, XiaomiSnapshotEntity::class, XiaomiCheckpointEntity::class
    ],
    version = 7,
    exportSchema = true
)
abstract class HealthDatabase : RoomDatabase() {
    abstract fun healthDao(): HealthDao
    abstract fun nutritionDao(): NutritionDao
    abstract fun scaleDao(): ScaleDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun xiaomiArchiveDao(): XiaomiArchiveDao

    companion object {
        fun create(context: Context): HealthDatabase =
            Room.databaseBuilder(context, HealthDatabase::class.java, "fitness-hub.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .build()
    }
}
