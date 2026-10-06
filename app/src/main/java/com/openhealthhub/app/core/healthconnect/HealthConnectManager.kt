package com.openhealthhub.app.core.healthconnect
import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.*

class HealthConnectManager(private val context:Context) {
 val status get()=HealthConnectClient.getSdkStatus(context)
 val client get()=if(status==HealthConnectClient.SDK_AVAILABLE) HealthConnectClient.getOrCreate(context) else null
 val permissions:Set<String> = setOf(
  StepsRecord::class, DistanceRecord::class, ActiveCaloriesBurnedRecord::class, TotalCaloriesBurnedRecord::class,
  ExerciseSessionRecord::class, HeartRateRecord::class, RestingHeartRateRecord::class, OxygenSaturationRecord::class,
  SleepSessionRecord::class, WeightRecord::class, BodyFatRecord::class
 ).map(HealthPermission::getReadPermission).toSet()
 val permissionContract get()=PermissionController.createRequestPermissionResultContract()
 suspend fun hasPermissions()=client?.permissionController?.getGrantedPermissions()?.containsAll(permissions)==true

 suspend fun snapshot(date:LocalDate):HealthSnapshot {
  val c=client ?: return HealthSnapshot(date)
  val zone=ZoneId.systemDefault()
  val start=date.atStartOfDay(zone).toInstant()
  val end=date.plusDays(1).atStartOfDay(zone).toInstant()
  val range=TimeRangeFilter.between(start,end)
  val aggregate=c.aggregate(AggregateRequest(setOf(
   StepsRecord.COUNT_TOTAL, DistanceRecord.DISTANCE_TOTAL,
   ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL, TotalCaloriesBurnedRecord.ENERGY_TOTAL
  ),range))
  val sleep=c.readRecords(ReadRecordsRequest(SleepSessionRecord::class,range)).records
  val resting=c.readRecords(ReadRecordsRequest(RestingHeartRateRecord::class,range)).records.lastOrNull()
  val oxygen=c.readRecords(ReadRecordsRequest(OxygenSaturationRecord::class,range)).records.lastOrNull()
  val weight=c.readRecords(ReadRecordsRequest(WeightRecord::class,range)).records.lastOrNull()
  val fat=c.readRecords(ReadRecordsRequest(BodyFatRecord::class,range)).records.lastOrNull()
  return HealthSnapshot(
   date,
   aggregate[StepsRecord.COUNT_TOTAL]?:0,
   aggregate[DistanceRecord.DISTANCE_TOTAL]?.inMeters?:0.0,
   aggregate[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories?:0.0,
   aggregate[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories?:0.0,
   sleep.sumOf { Duration.between(it.startTime,it.endTime).toMinutes() },
   resting?.beatsPerMinute, oxygen?.percentage?.value, weight?.weight?.inKilograms, fat?.percentage?.value
  )
 }
 suspend fun exercises(start:Instant,end:Instant)=client?.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class,TimeRangeFilter.between(start,end)))?.records.orEmpty()
}

data class HealthSnapshot(
 val date:LocalDate,val steps:Long=0,val distanceMeters:Double=0.0,val activeCalories:Double=0.0,val totalCalories:Double=0.0,
 val sleepMinutes:Long=0,val restingHeartRate:Long?=null,val oxygenSaturation:Double?=null,val weightKg:Double?=null,val bodyFatPercent:Double?=null
)
