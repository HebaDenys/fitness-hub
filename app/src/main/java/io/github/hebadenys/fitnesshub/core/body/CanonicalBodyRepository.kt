package io.github.hebadenys.fitnesshub.core.body

import io.github.hebadenys.fitnesshub.core.database.DailySummaryMapper
import io.github.hebadenys.fitnesshub.core.database.HealthBodyFatSampleEntity
import io.github.hebadenys.fitnesshub.core.database.HealthDao
import io.github.hebadenys.fitnesshub.core.database.HealthWeightSampleEntity
import io.github.hebadenys.fitnesshub.core.scale.BodyCompositionEstimateEntity
import io.github.hebadenys.fitnesshub.core.scale.ScaleDao
import io.github.hebadenys.fitnesshub.core.scale.ScaleMeasurementEntity
import io.github.hebadenys.fitnesshub.core.xiaomi.XiaomiJson
import io.github.hebadenys.fitnesshub.core.xiaomi.XiaomiJsonReader
import io.github.hebadenys.fitnesshub.core.xiaomi.XiaomiMethod
import io.github.hebadenys.fitnesshub.core.xiaomi.XiaomiUnit
import io.github.hebadenys.fitnesshub.core.xiaomi.storage.XiaomiArchiveDao
import io.github.hebadenys.fitnesshub.core.xiaomi.storage.XiaomiSnapshotEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class CanonicalBodyDay(
    val date: LocalDate,
    val weight: CanonicalBodyMetricResolver.Observation?,
    val bodyFat: CanonicalBodyMetricResolver.Observation?
)

data class CanonicalBodyData(
    val latestWeight: CanonicalBodyMetricResolver.ResolvedMetric?,
    val latestBodyFat: CanonicalBodyMetricResolver.ResolvedMetric?,
    val weightTimeline: List<CanonicalBodyMetricResolver.Observation>,
    val bodyFatTimeline: List<CanonicalBodyMetricResolver.Observation>,
    val days: List<CanonicalBodyDay>
)

class CanonicalBodyRepository(
    private val healthDao: HealthDao,
    private val scaleDao: ScaleDao,
    private val xiaomiDao: XiaomiArchiveDao,
    private val resolver: CanonicalBodyMetricResolver = CanonicalBodyMetricResolver(),
    private val zone: ZoneId = ZoneId.systemDefault()
) {
    private data class ExactHealthBody(
        val weights: List<HealthWeightSampleEntity>,
        val bodyFats: List<HealthBodyFatSampleEntity>
    )

    fun observe(): Flow<CanonicalBodyData> {
        val exactHealth = combine(
            healthDao.observeWeightSamples(),
            healthDao.observeBodyFatSamples()
        ) { weights, bodyFats -> ExactHealthBody(weights, bodyFats) }

        return combine(
            healthDao.observeDaily(),
            exactHealth,
            scaleDao.observeMeasurements(limit = 2_000),
            scaleDao.observeEstimates(limit = 2_000),
            xiaomiDao.observeSnapshots()
        ) { daily, exact, measurements, estimates, xiaomi ->
            val exactWeightDates = exact.weights.map { LocalDate.parse(it.date) }.toSet()
            val exactFatDates = exact.bodyFats.map { LocalDate.parse(it.date) }.toSet()
            val observations = buildList {
                exact.weights.forEach { row ->
                    add(observation(
                        CanonicalBodyMetricResolver.Metric.WEIGHT, row.kilograms, "kg",
                        Instant.ofEpochMilli(row.timeEpochMillis),
                        CanonicalBodyMetricResolver.Source.HEALTH_CONNECT,
                        CanonicalBodyMetricResolver.Method.MEASURED,
                        "hc-weight:" + row.dataOrigin + ":" + row.recordId,
                        eventKey = "hc-weight:" + row.recordId
                    ))
                }
                exact.bodyFats.forEach { row ->
                    add(observation(
                        CanonicalBodyMetricResolver.Metric.BODY_FAT, row.percentage, "%",
                        Instant.ofEpochMilli(row.timeEpochMillis),
                        CanonicalBodyMetricResolver.Source.HEALTH_CONNECT,
                        CanonicalBodyMetricResolver.Method.MEASURED,
                        "hc-body-fat:" + row.dataOrigin + ":" + row.recordId,
                        eventKey = "hc-body-fat:" + row.recordId
                    ))
                }
                daily.map(DailySummaryMapper::toDomain).forEach { day ->
                    val measuredAt = day.date.atStartOfDay(zone).toInstant()
                    if (day.date !in exactWeightDates) {
                        day.weightKg?.takeIf { it.isFinite() && it > 0.0 }?.let {
                            add(observation(
                                CanonicalBodyMetricResolver.Metric.WEIGHT, it, "kg", measuredAt,
                                CanonicalBodyMetricResolver.Source.HEALTH_CONNECT,
                                CanonicalBodyMetricResolver.Method.UNKNOWN,
                                "hc-day:" + day.date + ":weight"
                            ))
                        }
                    }
                    if (day.date !in exactFatDates) {
                        day.bodyFatPercent?.takeIf { it.isFinite() && it in 0.0..100.0 }?.let {
                            add(observation(
                                CanonicalBodyMetricResolver.Metric.BODY_FAT, it, "%", measuredAt,
                                CanonicalBodyMetricResolver.Source.HEALTH_CONNECT,
                                CanonicalBodyMetricResolver.Method.UNKNOWN,
                                "hc-day:" + day.date + ":body-fat"
                            ))
                        }
                    }
                }
                measurements.forEach { addScaleMeasurement(it) }
                estimates.forEach { addScaleEstimate(it) }
                xiaomi.forEach { addAll(xiaomiObservations(it)) }
            }

        val resolved = resolver.resolve(observations)
        val weight = resolver.timeline(observations, CanonicalBodyMetricResolver.Metric.WEIGHT)
        val fat = resolver.timeline(observations, CanonicalBodyMetricResolver.Metric.BODY_FAT)
        val dates = (weight.map { it.localDate() } + fat.map { it.localDate() }).distinct().sortedDescending()
            CanonicalBodyData(
                latestWeight = resolved[CanonicalBodyMetricResolver.Metric.WEIGHT],
                latestBodyFat = resolved[CanonicalBodyMetricResolver.Metric.BODY_FAT],
                weightTimeline = weight,
                bodyFatTimeline = fat,
                days = dates.map { date ->
                    CanonicalBodyDay(
                        date,
                        weight.filter { it.localDate() == date }.maxByOrNull { it.measuredAt },
                        fat.filter { it.localDate() == date }.maxByOrNull { it.measuredAt }
                    )
                }
            )
        }
    }

    private fun MutableList<CanonicalBodyMetricResolver.Observation>.addScaleMeasurement(row: ScaleMeasurementEntity) {
        row.weightKg?.takeIf { it.isFinite() && it > 0.0 }?.let {
            add(observation(
                CanonicalBodyMetricResolver.Metric.WEIGHT, it, "kg", Instant.ofEpochMilli(row.measuredAtMillis),
                CanonicalBodyMetricResolver.Source.SCALE, CanonicalBodyMetricResolver.Method.MEASURED,
                "scale-weight:" + row.id, eventKey = "scale:" + row.deviceAddress + ":" + row.measuredAtMillis
            ))
        }
    }

    private fun MutableList<CanonicalBodyMetricResolver.Observation>.addScaleEstimate(row: BodyCompositionEstimateEntity) {
        row.bodyFatPercent?.takeIf { it.isFinite() && it in 0.0..100.0 }?.let {
            val method = if (row.provenance == ScaleMeasurementEntity.PROVENANCE_IMPORTED) {
                CanonicalBodyMetricResolver.Method.VENDOR_ESTIMATE
            } else {
                CanonicalBodyMetricResolver.Method.LOCAL_ESTIMATE
            }
            add(observation(
                CanonicalBodyMetricResolver.Metric.BODY_FAT, it, "%", Instant.ofEpochMilli(row.measuredAtMillis),
                CanonicalBodyMetricResolver.Source.SCALE, method, "scale-fat:" + row.id,
                eventKey = "scale:" + row.measuredAtMillis
            ))
        }
    }

    private fun xiaomiObservations(row: XiaomiSnapshotEntity): List<CanonicalBodyMetricResolver.Observation> {
        val at = row.measuredAtMillis?.let(Instant::ofEpochMilli) ?: return emptyList()
        val root = runCatching { XiaomiJsonReader(row.snapshotJson).read() as? XiaomiJson.Object }.getOrNull()
            ?: return emptyList()
        val metrics = root.fields["metrics"] as? XiaomiJson.Object ?: return emptyList()
        return metrics.fields.mapNotNull { (key, raw) ->
            val fields = (raw as? XiaomiJson.Object)?.fields ?: return@mapNotNull null
            val value = (fields["value"] as? XiaomiJson.Number)?.literal?.toDoubleOrNull()
                ?.takeIf { it.isFinite() } ?: return@mapNotNull null
            val unit = (fields["unit"] as? XiaomiJson.Text)?.value ?: return@mapNotNull null
            val method = (fields["method"] as? XiaomiJson.Text)?.value ?: return@mapNotNull null
            when {
                key == "weight" && unit == XiaomiUnit.KG.name && value > 0.0 ->
                    observation(
                        CanonicalBodyMetricResolver.Metric.WEIGHT, value, "kg", at,
                        CanonicalBodyMetricResolver.Source.XIAOMI, CanonicalBodyMetricResolver.Method.MEASURED,
                        row.contentHash + ":weight", row.eventKey
                    )
                key == "bfp" && unit == XiaomiUnit.PERCENT.name && value in 0.0..100.0 ->
                    observation(
                        CanonicalBodyMetricResolver.Metric.BODY_FAT, value, "%", at,
                        CanonicalBodyMetricResolver.Source.XIAOMI,
                        if (method == XiaomiMethod.VENDOR_ESTIMATE.name) CanonicalBodyMetricResolver.Method.VENDOR_ESTIMATE
                        else CanonicalBodyMetricResolver.Method.UNKNOWN,
                        row.contentHash + ":bfp", row.eventKey
                    )
                else -> null
            }
        }
    }

    private fun observation(
        metric: CanonicalBodyMetricResolver.Metric,
        value: Double,
        unit: String,
        measuredAt: Instant,
        source: CanonicalBodyMetricResolver.Source,
        method: CanonicalBodyMetricResolver.Method,
        sourceId: String,
        eventKey: String? = null
    ) = CanonicalBodyMetricResolver.Observation(
        metric, value, unit, measuredAt, source, method,
        CanonicalBodyMetricResolver.Quality.VALID, sourceId, eventKey
    )

    private fun CanonicalBodyMetricResolver.Observation.localDate(): LocalDate =
        measuredAt.atZone(zone).toLocalDate()
}
