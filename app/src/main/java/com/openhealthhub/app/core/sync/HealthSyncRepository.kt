package com.openhealthhub.app.core.sync
import com.openhealthhub.app.core.database.*
import com.openhealthhub.app.core.healthconnect.HealthConnectManager
import kotlinx.coroutines.flow.Flow
import java.time.*

class HealthSyncRepository(private val health:HealthConnectManager,private val dao:HealthDao){
 fun observeDaily():Flow<List<DailyHealthEntity>> = dao.observeDaily()
 suspend fun sync(days:Int=30):Result<Unit> = runCatching {
  require(health.client!=null){"Health Connect unavailable"}
  require(health.hasPermissions()){"Health Connect permissions missing"}
  val today=LocalDate.now()
  val daily=(0 until days).map{health.snapshot(today.minusDays(it.toLong()))}.map{
   DailyHealthEntity(it.date.toString(),it.steps,it.distanceMeters,it.activeCalories,it.totalCalories,it.sleepMinutes,it.restingHeartRate,it.oxygenSaturation,it.weightKg,it.bodyFatPercent)
  }
  dao.upsertDaily(daily)
  val start=today.minusDays(days.toLong()).atStartOfDay(ZoneId.systemDefault()).toInstant()
  dao.insertExercises(health.exercises(start,Instant.now()).map{
   ExerciseEntity(externalId=it.metadata.id,title=it.title,type=it.exerciseType,startMillis=it.startTime.toEpochMilli(),endMillis=it.endTime.toEpochMilli(),source=it.metadata.dataOrigin.packageName)
  })
 }
}
