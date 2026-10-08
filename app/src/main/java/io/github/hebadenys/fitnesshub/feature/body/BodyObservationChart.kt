package io.github.hebadenys.fitnesshub.feature.body

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import io.github.hebadenys.fitnesshub.R
import io.github.hebadenys.fitnesshub.core.body.CanonicalBodyMetricResolver.Observation
import io.github.hebadenys.fitnesshub.ui.components.RangeSelector
import io.github.hebadenys.fitnesshub.ui.components.TimeRange
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

@Composable
internal fun BodyObservationChart(
    observations: List<Observation>,
    range: TimeRange,
    onRange: (TimeRange) -> Unit
) {
    val zone = ZoneId.systemDefault()
    val end = LocalDate.now(zone)
    val first = end.minusDays(range.days.toLong() - 1)
    val startMillis = first.atStartOfDay(zone).toInstant().toEpochMilli()
    val endMillis = end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val points = bodyObservationWindow(observations, range, end, zone)
    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedIndex = points.indices.firstOrNull { bodyObservationKey(points[it], it) == selectedKey }
        ?: points.lastIndex
    val selected = points.getOrNull(selectedIndex)
    val labelStyle = MaterialTheme.typography.bodyMedium
    val numbers = remember { NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2 } }
    val low = (floor((points.minOfOrNull { it.value } ?: 0.0) * 10) / 10 - 0.1).coerceAtLeast(0.0)
    val high = ceil((points.maxOfOrNull { it.value } ?: 0.0) * 10) / 10 + 0.1
    val labels = listOf(high, (low + high) / 2, low).map { numbers.format(it) }
    val measurer = rememberTextMeasurer()
    val labelHeight = measurer.measure("0", labelStyle).size.height.toFloat()
    val labelWidth = with(LocalDensity.current) { labels.maxOf { measurer.measure(it, labelStyle).size.width }.toDp() } + 8.dp
    val cyan = Color(0xFF61C5F5)
    val lime = MaterialTheme.colorScheme.primary
    val axis = MaterialTheme.colorScheme.outlineVariant
    val description = stringResource(R.string.body_chart_exact_description)

    Card(
        Modifier.fillMaxWidth().testTag("body_event_chart"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.body_weight_chart_kg), style = MaterialTheme.typography.titleMedium)
            RangeSelector(range, onRange)
            Text(stringResource(R.string.dashboard_date_interval, first.toString(), end.toString()), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.body_chart_event_count, points.size),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("body_event_count")
            )
            if (points.size >= 2) {
                val difference = points.last().value - points.first().value
                Text(
                    stringResource(R.string.body_chart_change, (if (difference > 0) "+" else "") + numbers.format(difference)),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (points.isEmpty()) {
                Text(stringResource(R.string.body_chart_no_events), style = MaterialTheme.typography.bodyMedium)
            } else {
                Row(Modifier.fillMaxWidth().height(200.dp)) {
                    Column(
                        Modifier.width(labelWidth).fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.End
                    ) { labels.forEach { Text(it, style = labelStyle) } }
                    Canvas(
                        Modifier.weight(1f).fillMaxHeight().padding(start = 8.dp).testTag("body_event_plot")
                            .semantics { contentDescription = description }
                            .pointerInput(points, startMillis, endMillis) {
                                detectTapGestures { position ->
                                    val target = startMillis.toDouble() + (position.x / size.width).coerceIn(0f, 1f).toDouble() * (endMillis - startMillis)
                                    val index = points.indices.minByOrNull { abs(points[it].measuredAt.toEpochMilli() - target) }
                                    index?.let { selectedKey = bodyObservationKey(points[it], it) }
                                }
                            }
                    ) {
                        val top = labelHeight / 2
                        val bottom = size.height - top
                        val span = (high - low).coerceAtLeast(0.1)
                        fun point(index: Int): Offset {
                            val item = points[index]
                            val x = ((item.measuredAt.toEpochMilli() - startMillis).toDouble() / (endMillis - startMillis) * size.width).toFloat()
                            val y = bottom - ((item.value - low) / span * (bottom - top)).toFloat()
                            return Offset(x, y)
                        }
                        repeat(3) {
                            val y = top + (bottom - top) * it / 2
                            drawLine(axis, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                        }
                        points.indices.forEach { index ->
                            if (index > 0) {
                                val previousDay = points[index - 1].measuredAt.atZone(zone).toLocalDate()
                                val day = points[index].measuredAt.atZone(zone).toLocalDate()
                                if (ChronoUnit.DAYS.between(previousDay, day) <= 1) {
                                    drawLine(cyan, point(index - 1), point(index), 2.dp.toPx())
                                }
                            }
                            drawCircle(if (index == selectedIndex) lime else cyan, 3.dp.toPx(), point(index))
                        }
                    }
                }
                if (points.size > 1) {
                    Slider(
                        value = selectedIndex.toFloat(),
                        onValueChange = {
                            val index = it.roundToInt().coerceIn(0, points.lastIndex)
                            selectedKey = bodyObservationKey(points[index], index)
                        },
                        valueRange = 0f..points.lastIndex.toFloat(),
                        steps = (points.size - 2).coerceAtLeast(0),
                        modifier = Modifier.testTag("body_event_selector").semantics { contentDescription = description }
                    )
                }
                selected?.let {
                    Column(Modifier.testTag("body_selected_event").semantics { liveRegion = LiveRegionMode.Polite }) {
                        Text(bodyObservationValue(it), style = MaterialTheme.typography.titleLarge)
                        BodyObservationDetails(it)
                    }
                }
            }
            Text(stringResource(R.string.body_chart_exact_note), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
