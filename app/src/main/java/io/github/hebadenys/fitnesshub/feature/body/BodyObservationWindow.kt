package io.github.hebadenys.fitnesshub.feature.body

import io.github.hebadenys.fitnesshub.core.body.CanonicalBodyMetricResolver.Observation
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
    return observations.filter { it.measuredAt >= start && it.measuredAt < until }.sortedBy { it.measuredAt }
}
