package io.github.hebadenys.fitnesshub.feature.body

import io.github.hebadenys.fitnesshub.core.body.CanonicalBodyMetricResolver.Observation
import io.github.hebadenys.fitnesshub.core.body.CanonicalBodyMetricResolver.Source
import io.github.hebadenys.fitnesshub.ui.components.TimeRange
import java.time.LocalDate
import java.time.ZoneId

/** Filters only the view; no day grouping or event deduplication is performed here. */
internal fun bodyObservationWindow(
    observations: List<Observation>,
    range: TimeRange,
    end: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault()
): List<Observation> {
    val start = end.minusDays(range.days.toLong() - 1).atStartOfDay(zone).toInstant()
    val until = end.plusDays(1).atStartOfDay(zone).toInstant()
    return observations.filter {
        if (bodyHasExactTime(it)) it.measuredAt >= start && it.measuredAt < until
        else bodyObservationDay(it, zone).let { day ->
            !day.isBefore(end.minusDays(range.days.toLong() - 1)) && !day.isAfter(end)
        }
    }.sortedBy { it.measuredAt }
}

/** Legacy daily rows have a date; their midnight Instant is a repository placeholder. */
internal fun bodyHasExactTime(value: Observation): Boolean =
    !(value.source == Source.HEALTH_CONNECT && value.sourceId?.startsWith("hc-day:") == true)

internal fun bodyObservationDay(value: Observation, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    if (!bodyHasExactTime(value)) {
        runCatching { LocalDate.parse(value.sourceId!!.removePrefix("hc-day:").substringBefore(':')) }
            .getOrElse { value.measuredAt.atZone(zone).toLocalDate() }
    } else value.measuredAt.atZone(zone).toLocalDate()
