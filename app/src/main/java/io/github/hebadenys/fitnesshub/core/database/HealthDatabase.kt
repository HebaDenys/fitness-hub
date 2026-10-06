package io.github.hebadenys.fitnesshub.core.database
import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName="daily_health")
data class DailyHealthEntity(
 @PrimaryKey val date:String,
 val steps:Long=0,
 val distanceMeters:Double=0.0,
 val activeCalories:Double=0.0,
 val totalCalories:Double=0.0,
 val sleepMinutes:Long?=null,
 val restingHeartRate:Long?=null,
 val oxygenSaturation:Double?=null,
 val weightKg:Double?=null,
 val bodyFatPercent:Double?=null,
 val source:String="health_connect",
 val syncedAt:Long=System.currentTimeMillis()
)

@Entity(tableName="exercise_sessions", indices=[Index(value=["externalId"],unique=true)])
data class ExerciseEntity(
 @PrimaryKey(autoGenerate=true) val id:Long=0,
 val externalId:String,
 val title:String?,
 val type:Int,
 val startMillis:Long,
 val endMillis:Long,
 val source:String
)

@Dao interface HealthDao {
 @Query("SELECT * FROM daily_health ORDER BY date DESC") fun observeDaily():Flow<List<DailyHealthEntity>>
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun upsertDaily(items:List<DailyHealthEntity>)
 @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun insertExercises(items:List<ExerciseEntity>)
}

@Database(entities=[DailyHealthEntity::class,ExerciseEntity::class],version=1,exportSchema=false)
abstract class HealthDatabase:RoomDatabase(){
 abstract fun healthDao():HealthDao
 companion object { fun create(context:Context)=Room.databaseBuilder(context,HealthDatabase::class.java,"fitness-hub.db").build() }
}
