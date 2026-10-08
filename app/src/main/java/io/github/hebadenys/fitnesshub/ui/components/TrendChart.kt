package io.github.hebadenys.fitnesshub.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import io.github.hebadenys.fitnesshub.R
import io.github.hebadenys.fitnesshub.core.model.DailySummary
import io.github.hebadenys.fitnesshub.ui.theme.FitnessHubTheme
import io.github.hebadenys.fitnesshub.ui.theme.ProvenanceEstimateContainerDark
import io.github.hebadenys.fitnesshub.ui.theme.ProvenanceEstimateContainerLight
import io.github.hebadenys.fitnesshub.ui.theme.ProvenanceEstimateTextDark
import io.github.hebadenys.fitnesshub.ui.theme.ProvenanceEstimateTextLight
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

data class ChartPoint(
    val date: LocalDate,
    val value: Double?,
    val provenance: String? = null,
    val sourceLabel: String? = null
)

/**
 * Trend chart for one metric.
 *
 * Missing days are drawn as gaps: the line breaks instead of bridging or
 * dropping to zero, because an absent day is unknown, not a measurement of 0.
 */
@Composable
fun TrendChart(
    title: String,
    points: List<ChartPoint>,
    selectedRange: TimeRange,
    onRangeSelected: (TimeRange) -> Unit,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    chartContentDescription: String = title,
    valueSuffix: String? = null
) {
    val spacing = FitnessHubTheme.spacing
    val cornerRadius = FitnessHubTheme.cornerRadius
    val dimensions = FitnessHubTheme.dimensions
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f

    val hasEstimate = points.any { it.provenance == DailySummary.PROVENANCE_ESTIMATE }
    val hasAnyValue = points.any { it.value != null }

    val axisColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = labelColor)
    val textMeasurer = rememberTextMeasurer()

    // A dense day axis so a missing day occupies its own slot and reads as a gap.
    val dates = remember(points, selectedRange) {
        val last = points.maxOfOrNull { it.date } ?: LocalDate.now()
        val first = last.minusDays((selectedRange.days - 1).toLong())
        generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.toList()
    }
    val valuesByDate = remember(points) { points.associate { it.date to it.value } }
    val presentValues = dates.mapNotNull { valuesByDate[it] }
    val recordedPoints = remember(points, dates) {
        val allowed = dates.toSet()
        points.filter { it.date in allowed && it.value != null }.sortedBy { it.date }
    }
    var selectedEpochDay by rememberSaveable(title) { mutableStateOf<Long?>(null) }
    LaunchedEffect(recordedPoints, selectedRange) {
        if (recordedPoints.none { it.date.toEpochDay() == selectedEpochDay }) {
            selectedEpochDay = recordedPoints.lastOrNull()?.date?.toEpochDay()
        }
    }
    val selectedPoint = recordedPoints.firstOrNull { it.date.toEpochDay() == selectedEpochDay }
        ?: recordedPoints.lastOrNull()

    val segments = remember(dates, valuesByDate) { buildSegments(dates, valuesByDate) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("d/M", Locale.getDefault()) }
    val valueFormatter = remember { DecimalFormat() }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics { this.contentDescription = chartContentDescription },
        shape = RoundedCornerShape(cornerRadius.md),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (hasEstimate) {
                    val estContainer =
                        if (isDark) ProvenanceEstimateContainerDark else ProvenanceEstimateContainerLight
                    val estText =
                        if (isDark) ProvenanceEstimateTextDark else ProvenanceEstimateTextLight
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(cornerRadius.xs))
                            .background(estContainer)
                            .padding(horizontal = spacing.xs, vertical = spacing.xxs)
                    ) {
                        Text(
                            text = stringResource(R.string.provenance_estimate),
                            style = MaterialTheme.typography.labelSmall,
                            color = estText
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(spacing.sm))

            RangeSelector(
                selectedRange = selectedRange,
                onRangeSelected = onRangeSelected
            )

            Spacer(modifier = Modifier.height(spacing.md))

            if (!hasAnyValue) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(dimensions.chartHeight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.state_empty_title),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val minValue = presentValues.min()
                val maxValue = presentValues.max()

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(dimensions.chartHeight)
                ) {
                    val labelWidth = maxValueLabelWidth(textMeasurer, valueFormatter, maxValue)
                    val axisLabelHeight = textMeasurer.measure("0", labelStyle).size.height
                    val plotLeft = labelWidth
                    val plotRight = size.width
                    val plotTop = 8f
                    val plotBottom = size.height - axisLabelHeight - 8f
                    val plotHeight = plotBottom - plotTop
                    val plotWidth = plotRight - plotLeft
                    if (plotHeight <= 0f || plotWidth <= 0f || dates.size < 2) return@Canvas

                    val span = (maxValue - minValue).takeIf { abs(it) > 1e-9 } ?: 1.0

                    fun xFor(index: Int): Float =
                        plotLeft + plotWidth * (index.toFloat() / (dates.size - 1).toFloat())

                    fun yFor(value: Double): Float {
                        val normalized = ((value - minValue) / span).toFloat()
                        return plotBottom - normalized * plotHeight
                    }

                    // Baseline and top guide, so an empty-looking chart still has a scale.
                    drawLine(
                        color = axisColor,
                        start = Offset(plotLeft, plotBottom),
                        end = Offset(plotRight, plotBottom),
                        strokeWidth = 1f
                    )
                    if (span > 1e-9) {
                        drawLine(
                            color = axisColor.copy(alpha = 0.5f),
                            start = Offset(plotLeft, plotTop),
                            end = Offset(plotRight, plotTop),
                            strokeWidth = 1f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                        )
                    }

                    drawScaleLabels(
                        textMeasurer = textMeasurer,
                        labelStyle = labelStyle,
                        minValue = minValue,
                        maxValue = maxValue,
                        plotLeft = plotLeft,
                        plotTop = plotTop,
                        plotBottom = plotBottom,
                        format = valueFormatter
                    )

                    segments.forEach { segment ->
                        if (segment.size == 1) {
                            val (index, value) = segment.first()
                            drawCircle(
                                color = lineColor,
                                radius = 3f,
                                center = Offset(xFor(index), yFor(value))
                            )
                        } else {
                            val path = Path()
                            segment.forEachIndexed { position, (index, value) ->
                                val point = Offset(xFor(index), yFor(value))
                                if (position == 0) path.moveTo(point.x, point.y)
                                else path.lineTo(point.x, point.y)
                            }
                            drawPath(
                                path = path,
                                color = lineColor,
                                style = Stroke(width = 2.5f, cap = StrokeCap.Round)
                            )
                            segment.forEach { (index, value) ->
                                drawCircle(
                                    color = lineColor,
                                    radius = 2.5f,
                                    center = Offset(xFor(index), yFor(value))
                                )
                            }
                        }
                    }

                    drawDateLabels(
                        textMeasurer = textMeasurer,
                        labelStyle = labelStyle,
                        dates = dates,
                        dateFormatter = dateFormatter,
                        plotLeft = plotLeft,
                        plotRight = plotRight,
                        plotBottom = plotBottom
                    )
                }

                if (recordedPoints.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(spacing.sm))
                    if (recordedPoints.size > 1) {
                        val selectedIndex = recordedPoints.indexOf(selectedPoint).coerceAtLeast(0)
                        Slider(
                            value = selectedIndex.toFloat(),
                            onValueChange = { raw ->
                                val index = raw.roundToInt().coerceIn(0, recordedPoints.lastIndex)
                                selectedEpochDay = recordedPoints[index].date.toEpochDay()
                            },
                            valueRange = 0f..recordedPoints.lastIndex.toFloat(),
                            steps = (recordedPoints.size - 2).coerceAtLeast(0),
                            modifier = Modifier.testTag("chart_point_selector")
                        )
                        Text(
                            text = stringResource(R.string.chart_scrub_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    selectedPoint?.value?.let { selectedValue ->
                        Card(
                            modifier = Modifier.fillMaxWidth().testTag("chart_selected_point"),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                        ) {
                            Column(
                                Modifier.padding(spacing.sm),
                                verticalArrangement = Arrangement.spacedBy(spacing.xxs)
                            ) {
                                val suffix = valueSuffix?.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""
                                Text(
                                    stringResource(
                                        R.string.chart_selected_value,
                                        selectedPoint.date.format(dateFormatter),
                                        valueFormatter.format(selectedValue),
                                        suffix
                                    ),
                                    style = MaterialTheme.typography.titleSmall
                                )
                                selectedPoint.sourceLabel?.let { source ->
                                    Text(
                                        stringResource(R.string.chart_selected_source, source),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                selectedPoint.provenance?.let { provenance ->
                                    Text(
                                        stringResource(
                                            R.string.chart_selected_provenance,
                                            stringResource(
                                                if (provenance == DailySummary.PROVENANCE_ESTIMATE)
                                                    R.string.provenance_estimate
                                                else R.string.provenance_measured
                                            )
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(spacing.xs))

                Text(
                    text = stringResource(R.string.chart_gaps_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/**
 * Splits the day axis into runs of consecutive recorded days. A gap in the data
 * ends the run, so the line is never drawn across an unknown day.
 */
private fun buildSegments(
    dates: List<LocalDate>,
    valuesByDate: Map<LocalDate, Double?>
): List<List<Pair<Int, Double>>> {
    val segments = mutableListOf<List<Pair<Int, Double>>>()
    var current = mutableListOf<Pair<Int, Double>>()
    dates.forEachIndexed { index, date ->
        val value = valuesByDate[date]
        if (value != null) {
            current += index to value
        } else if (current.isNotEmpty()) {
            segments += current
            current = mutableListOf()
        }
    }
    if (current.isNotEmpty()) segments += current
    return segments
}

private fun maxValueLabelWidth(
    textMeasurer: TextMeasurer,
    format: DecimalFormat,
    maxValue: Double
): Float = textMeasurer.measure(format.format(maxValue)).size.width + 12f

private fun DrawScope.drawScaleLabels(
    textMeasurer: TextMeasurer,
    labelStyle: TextStyle,
    minValue: Double,
    maxValue: Double,
    plotLeft: Float,
    plotTop: Float,
    plotBottom: Float,
    format: DecimalFormat
) {
    val topLabel = textMeasurer.measure(format.format(maxValue), labelStyle)
    drawText(
        textLayoutResult = topLabel,
        topLeft = Offset(plotLeft - topLabel.size.width - 8f, plotTop - topLabel.size.height / 2f)
    )
    if (maxValue != minValue) {
        val bottomLabel = textMeasurer.measure(format.format(minValue), labelStyle)
        drawText(
            textLayoutResult = bottomLabel,
            topLeft = Offset(plotLeft - bottomLabel.size.width - 8f, plotBottom - bottomLabel.size.height / 2f)
        )
    }
}

private fun DrawScope.drawDateLabels(
    textMeasurer: TextMeasurer,
    labelStyle: TextStyle,
    dates: List<LocalDate>,
    dateFormatter: DateTimeFormatter,
    plotLeft: Float,
    plotRight: Float,
    plotBottom: Float
) {
    if (dates.isEmpty()) return
    val indices = dateLabelIndices(dates.size)
    indices.forEach { index ->
        val label = textMeasurer.measure(dates[index].format(dateFormatter), labelStyle)
        val fraction = index.toFloat() / (dates.size - 1).coerceAtLeast(1).toFloat()
        val centerX = plotLeft + (plotRight - plotLeft) * fraction
        val x = (centerX - label.size.width / 2f).coerceIn(plotLeft, plotRight - label.size.width)
        drawText(textLayoutResult = label, topLeft = Offset(x, plotBottom + 6f))
    }
}

/** Keeps the axis readable: first and last day plus evenly spaced interior ticks. */
private fun dateLabelIndices(count: Int): List<Int> {
    if (count <= 1) return listOf(0)
    if (count <= 4) return (0 until count).toList()
    val interior = 2
    val step = (count - 1).toFloat() / (interior + 1)
    val ticks = (0..interior).map { (it * step).roundToInt() } + (count - 1)
    return ticks.distinct()
}

/** Compact number formatting so axis labels stay short and locale-consistent. */
private class DecimalFormat {
    fun format(value: Double): String = when {
        abs(value) >= 10_000 -> String.format(Locale.getDefault(), "%.1fk", value / 1000.0)
        abs(value) >= 100 -> String.format(Locale.getDefault(), "%.0f", value)
        else -> String.format(Locale.getDefault(), "%.1f", value)
    }
}
