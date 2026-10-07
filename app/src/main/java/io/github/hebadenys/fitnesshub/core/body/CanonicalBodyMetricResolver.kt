package io.github.hebadenys.fitnesshub.core.body

import java.time.Instant

/**
 * Pure selection rules for body metrics.
 *
 * Recency is the primary rule across different events. Source/method priority is
 * used only to choose between observations of the same explicitly identified
 * event, or as a deterministic tie-break when timestamps are exactly equal.
 * Original observations are never mutated or deleted.
 */
class CanonicalBodyMetricResolver {

    data class Observation(
        val metric: Metric,
        val value: Double,
        val unit: String,
        val measuredAt: Instant,
        val source: Source,
        val method: Method,
        val quality: Quality = Quality.VALID,
        val sourceId: String? = null,
        val eventKey: String? = null
    )

    enum class Metric { WEIGHT, BODY_FAT }
    enum class Source { XIAOMI, HEALTH_CONNECT, SCALE, MANUAL }
    enum class Method { MEASURED, VENDOR_ESTIMATE, LOCAL_ESTIMATE, UNKNOWN }
    enum class Quality { VALID, UNKNOWN, INVALID }

    data class ResolvedMetric(
        val metric: Metric,
        val observation: Observation,
        val alternatives: List<Observation>
    )

    /**
     * Latest canonical value per metric.
     *
     * Important: a month-old Xiaomi value cannot beat a newer Health Connect
     * value merely because Xiaomi has richer provenance.
     */
    fun resolve(observations: List<Observation>): Map<Metric, ResolvedMetric> =
        observations
            .filter { it.quality == Quality.VALID && it.value.isFinite() }
            .groupBy { it.metric }
            .mapValues { (metric, values) ->
                val winners = eventWinners(values)
                val selected = winners.maxWithOrNull(
                    compareBy<Observation> { it.measuredAt }
                        .thenBy { priority(it) }
                ) ?: error("No valid observations")
                val alternatives = values.toMutableList().also { it.remove(selected) }
                    .sortedByDescending { it.measuredAt }
                ResolvedMetric(metric, selected, alternatives)
            }

    /**
     * Canonical event timeline for one metric. Only records carrying the same
     * non-null [Observation.eventKey] are collapsed. Records without a proven
     * event identity are always kept as distinct observations.
     */
    fun timeline(observations: List<Observation>, metric: Metric): List<Observation> =
        eventWinners(
            observations.filter {
                it.metric == metric && it.quality == Quality.VALID && it.value.isFinite()
            }
        ).sortedWith(compareBy<Observation> { it.measuredAt }.thenBy { priority(it) })

    private fun eventWinners(values: List<Observation>): List<Observation> {
        val explicit = values.filter { it.eventKey != null }.groupBy { it.eventKey!! }
            .values.map { sameEvent ->
                sameEvent.maxWithOrNull(
                    compareBy<Observation> { priority(it) }.thenBy { it.measuredAt }
                )!!
            }
        return explicit + values.filter { it.eventKey == null }
    }

    private fun priority(value: Observation): Int {
        val source = when (value.source) {
            Source.XIAOMI -> 400
            Source.SCALE -> 300
            Source.HEALTH_CONNECT -> 200
            Source.MANUAL -> 100
        }
        val method = when (value.method) {
            Method.MEASURED -> 40
            Method.VENDOR_ESTIMATE -> 30
            Method.UNKNOWN -> 20
            Method.LOCAL_ESTIMATE -> 10
        }
        return source + method
    }
}
