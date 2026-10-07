package io.github.hebadenys.fitnesshub.core.body

import java.time.Instant

/**
 * Common selection rules for body metrics.
 *
 * This is intentionally source-agnostic: Xiaomi, Health Connect and manual inputs
 * should become observations first. The resolver selects a view, it never mutates
 * the original measurements.
 */
internal class CanonicalBodyMetricResolver {

    data class Observation(
        val metric: Metric,
        val value: Double,
        val unit: String,
        val measuredAt: Instant,
        val source: Source,
        val method: Method,
        val quality: Quality = Quality.VALID,
        val sourceId: String? = null
    )

    enum class Metric { WEIGHT, BODY_FAT }
    enum class Source { XIAOMI, HEALTH_CONNECT, MANUAL }
    enum class Method { MEASURED, VENDOR_ESTIMATE, LOCAL_ESTIMATE }
    enum class Quality { VALID, UNKNOWN }

    data class ResolvedMetric(
        val metric: Metric,
        val observation: Observation,
        val alternatives: List<Observation>
    )

    fun resolve(observations: List<Observation>): Map<Metric, ResolvedMetric> =
        observations
            .filter { it.quality == Quality.VALID }
            .groupBy { it.metric }
            .mapValues { (metric, values) ->
                val ordered = values.sortedWith(
                    compareByDescending<Observation> { priority(it) }
                        .thenByDescending { it.measuredAt }
                )
                ResolvedMetric(metric, ordered.first(), ordered.drop(1))
            }

    private fun priority(value: Observation): Int = when {
        value.method == Method.MEASURED && value.source == Source.XIAOMI -> 400
        value.method == Method.MEASURED && value.source == Source.HEALTH_CONNECT -> 300
        value.method == Method.MEASURED -> 200
        value.method == Method.VENDOR_ESTIMATE -> 100
        else -> 0
    }
}
