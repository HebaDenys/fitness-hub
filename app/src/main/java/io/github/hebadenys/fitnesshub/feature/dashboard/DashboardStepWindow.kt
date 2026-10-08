package io.github.hebadenys.fitnesshub.feature.dashboard

import io.github.hebadenys.fitnesshub.core.model.DailySummary
import io.github.hebadenys.fitnesshub.ui.components.ChartPoint
import io.github.hebadenys.fitnesshub.ui.components.TimeRange
import java.time.LocalDate

/** Calendar slots are retained even when the source has no row or no step value. */
internal fun dashboardStepPoints(
    summaries: List<DailySummary>,
    range: TimeRange,
    end: LocalDate = LocalDate.now()
): List<ChartPoint> {
    val byDate = summaries.associateBy { it.date }
    val first = end.minusDays(range.days.toLong() - 1)
    return List(range.days) { offset ->
        val date = first.plusDays(offset.toLong())
        val day = byDate[date]
        ChartPoint(
            date = date,
            value = day?.steps?.toDouble(),
            provenance = day?.provenance,
            sourceLabel = day?.dataOrigins?.takeIf { it.isNotEmpty() }?.sorted()?.joinToString(", ")
        )
    }
}
