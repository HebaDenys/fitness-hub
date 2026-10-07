package io.github.hebadenys.fitnesshub.core.healthconnect

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.changes.DeletionChange
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import io.github.hebadenys.fitnesshub.core.model.DayAggregate
import io.github.hebadenys.fitnesshub.core.model.DecimalSample
import io.github.hebadenys.fitnesshub.core.model.ExerciseSessionData
import io.github.hebadenys.fitnesshub.core.model.HealthChangesPage
import io.github.hebadenys.fitnesshub.core.model.HealthMetrics
import io.github.hebadenys.fitnesshub.core.model.RangePayload
import io.github.hebadenys.fitnesshub.core.model.Sample
import io.github.hebadenys.fitnesshub.core.model.SleepAttribution
import io.github.hebadenys.fitnesshub.core.model.SleepInterval
import io.github.hebadenys.fitnesshub.core.model.TimedOrigin
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.reflect.KClass

/**
 * Health Connect gateway: permission evaluation plus normalized reads.
 * Absent aggregate entries stay null; permission-denied record types read as empty.
 */
class HealthConnectManager(private val context: Context) : HealthDataSource {

    val status get() = HealthConnectClient.getSdkStatus(context)
    val client get() = if (status == HealthConnectClient.SDK_AVAILABLE) HealthConnectClient.getOrCreate(context) else null

    private val recordTypes: Set<KClass<out Record>> = setOf(
        StepsRecord::class, DistanceRecord::class, ActiveCaloriesBurnedRecord::class,
        TotalCaloriesBurnedRecord::class, ExerciseSessionRecord::class, HeartRateRecord::class,
        RestingHeartRateRecord::class, OxygenSaturationRecord::class, SleepSessionRecord::class,
        WeightRecord::class, BodyFatRecord::class
    )

    private val permissionByMetric: Map<String, String> = mapOf(
        HealthMetrics.STEPS to HealthPermission.getReadPermission(StepsRecord::class),
        HealthMetrics.DISTANCE to HealthPermission.getReadPermission(DistanceRecord::class),
        HealthMetrics.ACTIVE_CALORIES to HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthMetrics.TOTAL_CALORIES to HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthMetrics.EXERCISE to HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthMetrics.HEART_RATE to HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthMetrics.RESTING_HEART_RATE to HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthMetrics.OXYGEN_SATURATION to HealthPermission.getReadPermission(OxygenSaturationRecord::class),
        HealthMetrics.SLEEP to HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthMetrics.WEIGHT to HealthPermission.getReadPermission(WeightRecord::class),
        HealthMetrics.BODY_FAT to HealthPermission.getReadPermission(BodyFatRecord::class)
    )

    /** Requested permissions for the UI contract, including optional history access. */
    val permissions: Set<String> = permissionByMetric.values.toSet() +
        HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY

    val permissionContract get() = PermissionController.createRequestPermissionResultContract()

    override suspend fun grantedMetrics(): Set<String> {
        val c = client ?: return emptySet()
        return PermissionFilter.grantedMetrics(c.permissionController.getGrantedPermissions(), permissionByMetric)
    }

    override suspend fun historyAccessGranted(): Boolean {
        val c = client ?: return false
        return PermissionFilter.isHistoryGranted(
            c.permissionController.getGrantedPermissions(),
            HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY
        )
    }

    suspend fun hasPermissions() = grantedMetrics().isNotEmpty()

    override suspend fun aggregateDay(date: LocalDate, metrics: Set<String>): DayAggregate {
        val c = client ?: throw IllegalStateException("Health Connect unavailable")
        val zone = ZoneId.systemDefault()
        val range = TimeRangeFilter.between(
            date.atStartOfDay(zone).toInstant(),
            date.plusDays(1).atStartOfDay(zone).toInstant()
        )
        val requested = buildSet<AggregateMetric<*>> {
            if (HealthMetrics.STEPS in metrics) add(StepsRecord.COUNT_TOTAL)
            if (HealthMetrics.DISTANCE in metrics) add(DistanceRecord.DISTANCE_TOTAL)
            if (HealthMetrics.ACTIVE_CALORIES in metrics) add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
            if (HealthMetrics.TOTAL_CALORIES in metrics) add(TotalCaloriesBurnedRecord.ENERGY_TOTAL)
        }
        if (requested.isEmpty()) return DayAggregate(date)
        val result = try {
            c.aggregate(AggregateRequest(requested, range))
        } catch (e: SecurityException) {
            return DayAggregate(date)
        }
        return DayAggregate(
            date = date,
            steps = result[StepsRecord.COUNT_TOTAL],
            distanceMeters = result[DistanceRecord.DISTANCE_TOTAL]?.inMeters,
            activeCalories = result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories,
            totalCalories = result[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories
        )
    }

    override suspend fun readRange(rangeStart: Instant, rangeEnd: Instant, metrics: Set<String>): RangePayload {
        val c = client ?: throw IllegalStateException("Health Connect unavailable")
        val range = TimeRangeFilter.between(rangeStart, rangeEnd)
        var payload = RangePayload()
        if (HealthMetrics.SLEEP in metrics) {
            payload = payload.copy(sleep = readAll<SleepSessionRecord>(c, range).map {
                SleepInterval(it.metadata.id, it.startTime, it.endTime, it.metadata.dataOrigin.packageName)
            })
        }
        if (HealthMetrics.EXERCISE in metrics) {
            payload = payload.copy(exercises = readAll<ExerciseSessionRecord>(c, range).map {
                ExerciseSessionData(
                    it.metadata.id, it.title, it.exerciseType,
                    it.startTime.toEpochMilli(), it.endTime.toEpochMilli(),
                    it.metadata.dataOrigin.packageName
                )
            })
        }
        if (HealthMetrics.HEART_RATE in metrics) {
            payload = payload.copy(heartRateSamples = readAll<HeartRateRecord>(c, range).flatMap { record ->
                record.samples.map {
                    Sample(it.time.toEpochMilli(), it.beatsPerMinute, record.metadata.dataOrigin.packageName)
                }
            })
        }
        if (HealthMetrics.OXYGEN_SATURATION in metrics) {
            payload = payload.copy(oxygenSamples = readAll<OxygenSaturationRecord>(c, range).map {
                DecimalSample(
                    it.time.toEpochMilli(), it.percentage.value,
                    it.metadata.dataOrigin.packageName, it.metadata.id
                )
            })
        }
        if (HealthMetrics.RESTING_HEART_RATE in metrics) {
            payload = payload.copy(restingHeartRateSamples = readAll<RestingHeartRateRecord>(c, range).map {
                Sample(it.time.toEpochMilli(), it.beatsPerMinute, it.metadata.dataOrigin.packageName)
            })
        }
        if (HealthMetrics.WEIGHT in metrics) {
            payload = payload.copy(weights = readAll<WeightRecord>(c, range).map {
                DecimalSample(
                    it.time.toEpochMilli(), it.weight.inKilograms,
                    it.metadata.dataOrigin.packageName, it.metadata.id
                )
            })
        }
        if (HealthMetrics.BODY_FAT in metrics) {
            payload = payload.copy(bodyFats = readAll<BodyFatRecord>(c, range).map {
                DecimalSample(
                    it.time.toEpochMilli(), it.percentage.value,
                    it.metadata.dataOrigin.packageName, it.metadata.id
                )
            })
        }
        val aggregateKeys = setOf(
            HealthMetrics.STEPS, HealthMetrics.DISTANCE,
            HealthMetrics.ACTIVE_CALORIES, HealthMetrics.TOTAL_CALORIES
        )
        if (metrics.any { it in aggregateKeys }) {
            payload = payload.copy(metricOrigins = buildMetricOrigins(c, range, metrics))
        }
        return payload
    }

    override suspend fun readChanges(token: String): HealthChangesPage {
        val c = client ?: throw IllegalStateException("Health Connect unavailable")
        val response = c.getChanges(token)
        val zone = ZoneId.systemDefault()
        val affectedDates = mutableSetOf<LocalDate>()
        val deleted = mutableListOf<String>()
        val upsertedExercises = mutableListOf<String>()
        for (change in response.changes) {
            when (change) {
                is DeletionChange -> deleted += change.recordId
                is UpsertionChange -> {
                    val record = change.record
                    if (record is ExerciseSessionRecord) upsertedExercises += record.metadata.id
                    if (record is SleepSessionRecord) {
                        affectedDates += record.startTime.atZone(zone).toLocalDate()
                        affectedDates += SleepAttribution.wakeDate(record.endTime, zone)
                    } else {
                        startMillis(record)?.let {
                            affectedDates += Instant.ofEpochMilli(it).atZone(zone).toLocalDate()
                        }
                    }
                }
                else -> Unit
            }
        }
        return HealthChangesPage(
            tokenExpired = response.changesTokenExpired,
            hasMore = response.hasMore,
            nextToken = response.nextChangesToken,
            changeCount = response.changes.size,
            affectedDates = affectedDates,
            deletedRecordIds = deleted,
            upsertedExerciseIds = upsertedExercises
        )
    }

    override suspend fun createChangesToken(): String {
        val c = client ?: throw IllegalStateException("Health Connect unavailable")
        return c.getChangesToken(ChangesTokenRequest(recordTypes, emptySet()))
    }

    private suspend fun buildMetricOrigins(
        c: HealthConnectClient,
        range: TimeRangeFilter,
        metrics: Set<String>
    ): Map<String, List<TimedOrigin>> {
        val origins = mutableMapOf<String, List<TimedOrigin>>()
        if (HealthMetrics.STEPS in metrics) origins[HealthMetrics.STEPS] = originsFor<StepsRecord>(c, range)
        if (HealthMetrics.DISTANCE in metrics) origins[HealthMetrics.DISTANCE] = originsFor<DistanceRecord>(c, range)
        if (HealthMetrics.ACTIVE_CALORIES in metrics) origins[HealthMetrics.ACTIVE_CALORIES] = originsFor<ActiveCaloriesBurnedRecord>(c, range)
        if (HealthMetrics.TOTAL_CALORIES in metrics) origins[HealthMetrics.TOTAL_CALORIES] = originsFor<TotalCaloriesBurnedRecord>(c, range)
        return origins
    }

    private suspend inline fun <reified T : Record> originsFor(
        c: HealthConnectClient,
        range: TimeRangeFilter
    ): List<TimedOrigin> = readAll<T>(c, range).mapNotNull { record ->
        startMillis(record)?.let { TimedOrigin(it, record.metadata.dataOrigin.packageName) }
    }

    /** Pages through every record of type [T] in range; a denied record type reads as empty. */
    private suspend inline fun <reified T : Record> readAll(
        c: HealthConnectClient,
        range: TimeRangeFilter
    ): List<T> = try {
        val records = mutableListOf<T>()
        var pageToken: String? = null
        do {
            val response = c.readRecords(ReadRecordsRequest(T::class, range, emptySet(), true, 1000, pageToken))
            records += response.records
            pageToken = response.pageToken
        } while (!pageToken.isNullOrEmpty())
        records
    } catch (e: SecurityException) {
        emptyList()
    }

    private fun startMillis(record: Record): Long? = when (record) {
        is SleepSessionRecord -> record.startTime.toEpochMilli()
        is ExerciseSessionRecord -> record.startTime.toEpochMilli()
        is HeartRateRecord -> record.startTime.toEpochMilli()
        is StepsRecord -> record.startTime.toEpochMilli()
        is DistanceRecord -> record.startTime.toEpochMilli()
        is ActiveCaloriesBurnedRecord -> record.startTime.toEpochMilli()
        is TotalCaloriesBurnedRecord -> record.startTime.toEpochMilli()
        is WeightRecord -> record.time.toEpochMilli()
        is BodyFatRecord -> record.time.toEpochMilli()
        is OxygenSaturationRecord -> record.time.toEpochMilli()
        is RestingHeartRateRecord -> record.time.toEpochMilli()
        else -> null
    }
}
