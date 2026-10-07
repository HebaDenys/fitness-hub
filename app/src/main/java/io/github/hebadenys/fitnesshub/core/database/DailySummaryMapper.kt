package io.github.hebadenys.fitnesshub.core.database

import io.github.hebadenys.fitnesshub.core.model.DailySummary
import io.github.hebadenys.fitnesshub.core.model.DayAggregate
import io.github.hebadenys.fitnesshub.core.model.DecimalSample
import io.github.hebadenys.fitnesshub.core.model.ExerciseSessionData
import io.github.hebadenys.fitnesshub.core.model.RangePayload
import io.github.hebadenys.fitnesshub.core.model.Sample
import io.github.hebadenys.fitnesshub.core.model.SleepAttribution
import io.github.hebadenys.fitnesshub.core.model.TimedOrigin
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Maps between Health Connect payloads, pure domain summaries, and Room rows. */
object DailySummaryMapper {

    /** Builds one summary per aggregated day: aggregates + rolled-up series values + provenance. */
    fun buildSummaries(
        aggregates: List<DayAggregate>,
        payload: RangePayload,
        zone: ZoneId
    ): List<DailySummary> {
        val sleepByDay = SleepAttribution.minutesByWakeDay(payload.sleep, zone)
        val latestWeight = latestPerDay(payload.weights, zone) { it.timeEpochMillis }
        val latestBodyFat = latestPerDay(payload.bodyFats, zone) { it.timeEpochMillis }
        val latestOxygen = latestPerDay(payload.oxygenSamples, zone) { it.timeEpochMillis }
        val latestResting = latestPerDay(payload.restingHeartRateSamples, zone) { it.timeEpochMillis }
        val origins = mutableMapOf<LocalDate, MutableSet<String>>()
        fun addOrigins(day: LocalDate?, packages: Collection<String>) {
            if (day != null && packages.isNotEmpty()) {
                origins.getOrPut(day) { mutableSetOf() } += packages
            }
        }
        payload.metricOrigins.values.flatten().groupBy { it.localDate(zone) }
            .forEach { (day, points) -> addOrigins(day, points.map { it.dataOrigin }) }
        payload.sleep.forEach { addOrigins(SleepAttribution.wakeDate(it.end, zone), listOf(it.dataOrigin)) }
        val sampleOrigins = payload.weights.map { it.localDate(zone) to it.dataOrigin } +
            payload.bodyFats.map { it.localDate(zone) to it.dataOrigin } +
            payload.oxygenSamples.map { it.localDate(zone) to it.dataOrigin } +
            payload.restingHeartRateSamples.map { it.localDate(zone) to it.dataOrigin }
        sampleOrigins.groupBy({ it.first }, { it.second })
            .forEach { (day, packages) -> addOrigins(day, packages) }

        return aggregates.map { aggregate ->
            DailySummary(
                date = aggregate.date,
                steps = aggregate.steps,
                distanceMeters = aggregate.distanceMeters,
                activeCalories = aggregate.activeCalories,
                totalCalories = aggregate.totalCalories,
                sleepMinutes = sleepByDay[aggregate.date],
                restingHeartRate = latestResting[aggregate.date]?.value,
                oxygenSaturation = latestOxygen[aggregate.date]?.value,
                weightKg = latestWeight[aggregate.date]?.value,
                bodyFatPercent = latestBodyFat[aggregate.date]?.value,
                dataOrigins = origins[aggregate.date] ?: emptySet()
            )
        }
    }

    fun toEntity(summary: DailySummary, syncedAt: Long): DailyHealthEntity = DailyHealthEntity(
        date = summary.date.toString(),
        steps = summary.steps,
        distanceMeters = summary.distanceMeters,
        activeCalories = summary.activeCalories,
        totalCalories = summary.totalCalories,
        sleepMinutes = summary.sleepMinutes,
        restingHeartRate = summary.restingHeartRate,
        oxygenSaturation = summary.oxygenSaturation,
        weightKg = summary.weightKg,
        bodyFatPercent = summary.bodyFatPercent,
        syncedAt = syncedAt,
        dataOrigins = summary.dataOrigins.sorted().joinToString(","),
        provenance = summary.provenance,
        algorithm = summary.algorithm
    )

    fun toDomain(entity: DailyHealthEntity): DailySummary = DailySummary(
        date = LocalDate.parse(entity.date),
        steps = entity.steps,
        distanceMeters = entity.distanceMeters,
        activeCalories = entity.activeCalories,
        totalCalories = entity.totalCalories,
        sleepMinutes = entity.sleepMinutes,
        restingHeartRate = entity.restingHeartRate,
        oxygenSaturation = entity.oxygenSaturation,
        weightKg = entity.weightKg,
        bodyFatPercent = entity.bodyFatPercent,
        dataOrigins = entity.dataOrigins.split(",").filter { it.isNotEmpty() }.toSet(),
        provenance = entity.provenance,
        algorithm = entity.algorithm
    )

    fun toExerciseEntities(sessions: List<ExerciseSessionData>): List<ExerciseEntity> =
        sessions.map {
            ExerciseEntity(
                externalId = it.externalId,
                title = it.title,
                type = it.type,
                startMillis = it.startMillis,
                endMillis = it.endMillis,
                source = it.dataOrigin
            )
        }

    fun heartRateEntities(samples: List<Sample>, zone: ZoneId): List<HeartRateSampleEntity> =
        samples.map {
            HeartRateSampleEntity(
                date = it.localDate(zone).toString(),
                timeEpochMillis = it.timeEpochMillis,
                bpm = it.value,
                dataOrigin = it.dataOrigin
            )
        }

    fun oxygenEntities(samples: List<DecimalSample>, zone: ZoneId): List<OxygenSampleEntity> =
        samples.map {
            OxygenSampleEntity(
                date = it.localDate(zone).toString(),
                timeEpochMillis = it.timeEpochMillis,
                percentage = it.value,
                dataOrigin = it.dataOrigin
            )
        }

    fun restingHeartRateEntities(samples: List<Sample>, zone: ZoneId): List<RestingHeartRateSampleEntity> =
        samples.map {
            RestingHeartRateSampleEntity(
                date = it.localDate(zone).toString(),
                timeEpochMillis = it.timeEpochMillis,
                bpm = it.value,
                dataOrigin = it.dataOrigin
            )
        }

    fun weightEntities(samples: List<DecimalSample>, zone: ZoneId): List<HealthWeightSampleEntity> =
        samples.mapNotNull { sample ->
            val recordId = sample.externalId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val value = sample.value.takeIf { it.isFinite() && it > 0.0 } ?: return@mapNotNull null
            HealthWeightSampleEntity(
                recordId = recordId,
                date = sample.localDate(zone).toString(),
                timeEpochMillis = sample.timeEpochMillis,
                kilograms = value,
                dataOrigin = sample.dataOrigin
            )
        }

    fun bodyFatEntities(samples: List<DecimalSample>, zone: ZoneId): List<HealthBodyFatSampleEntity> =
        samples.mapNotNull { sample ->
            val recordId = sample.externalId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val value = sample.value.takeIf { it.isFinite() && it in 0.0..100.0 } ?: return@mapNotNull null
            HealthBodyFatSampleEntity(
                recordId = recordId,
                date = sample.localDate(zone).toString(),
                timeEpochMillis = sample.timeEpochMillis,
                percentage = value,
                dataOrigin = sample.dataOrigin
            )
        }

    private fun <T> latestPerDay(samples: List<T>, zone: ZoneId, time: (T) -> Long): Map<LocalDate, T> =
        samples.groupBy { Instant.ofEpochMilli(time(it)).atZone(zone).toLocalDate() }
            .mapValues { (_, day) -> day.maxBy(time) }

    private fun TimedOrigin.localDate(zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(timeEpochMillis).atZone(zone).toLocalDate()

    private fun Sample.localDate(zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(timeEpochMillis).atZone(zone).toLocalDate()

    private fun DecimalSample.localDate(zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(timeEpochMillis).atZone(zone).toLocalDate()
}
