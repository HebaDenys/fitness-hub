package io.github.hebadenys.fitnesshub.feature.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import io.github.hebadenys.fitnesshub.R
import io.github.hebadenys.fitnesshub.ui.components.ChartPoint
import io.github.hebadenys.fitnesshub.ui.components.RangeSelector
import io.github.hebadenys.fitnesshub.ui.components.TimeRange
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

/** Dashboard-only bars, with equivalent selectable dates and a full text list. */
@Composable
internal fun DashboardStepsChart(
    points: List<ChartPoint>,
    range: TimeRange,
    onRangeSelected: (TimeRange) -> Unit,
    compact: Boolean = false,
    onOpen: () -> Unit = {}
) {
    var selectedEpoch by rememberSaveable { mutableStateOf<Long?>(null) }
    var showList by rememberSaveable { mutableStateOf(false) }
    val selectedIndex = points.indexOfFirst { it.date.toEpochDay() == selectedEpoch }
        .takeIf { it >= 0 } ?: points.lastIndex
    val selected = points.getOrNull(selectedIndex)
    val known = points.mapNotNull { it.value }
    val total = known.takeIf { it.isNotEmpty() }?.sum()
    val max = (known.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
    val number = remember { NumberFormat.getIntegerInstance() }
    val dateFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    val selectedValue = selected?.value?.let(number::format) ?: stringResource(R.string.value_unavailable)
    val selectedDescription = selected?.let {
        stringResource(R.string.dashboard_selected_steps, it.date.format(dateFormat), selectedValue)
    }.orEmpty()
    val cyan = Color(0xFF61C5F5)
    val lime = MaterialTheme.colorScheme.primary
    val axis = MaterialTheme.colorScheme.outlineVariant
    val chartDescription = stringResource(R.string.cd_chart_steps)

    Card(
        modifier = Modifier.fillMaxWidth().testTag("dashboard_steps_chart"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (compact) {
                TextButton(onClick = onOpen, modifier = Modifier.testTag("dashboard_steps_detail")) {
                    Text(stringResource(R.string.dashboard_last_days, range.days), style = MaterialTheme.typography.titleMedium)
                }
            } else {
                RangeSelector(range, onRangeSelected)
                Text(stringResource(R.string.dashboard_recorded_steps), style = MaterialTheme.typography.titleMedium)
                Text(
                    total?.let(number::format) ?: stringResource(R.string.value_unavailable),
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.testTag("steps_period_total")
                )
                Text(
                    stringResource(R.string.dashboard_days_coverage, known.size, points.size),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("steps_coverage")
                )
            }
            if (known.isEmpty()) {
                Text(stringResource(R.string.dashboard_no_steps_period), style = MaterialTheme.typography.bodyMedium)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("0", style = MaterialTheme.typography.bodyMedium)
                Text(number.format(known.maxOrNull() ?: 0.0), style = MaterialTheme.typography.bodyMedium)
            }
            Canvas(
                Modifier.fillMaxWidth().height(if (compact) 140.dp else 200.dp)
                    .testTag("steps_bars")
                    .semantics { contentDescription = chartDescription }
                    .pointerInput(points) {
                        detectTapGestures { position ->
                            if (compact) {
                                onOpen()
                            } else if (points.isNotEmpty() && size.width > 0) {
                                val index = (position.x / size.width * points.size).toInt().coerceIn(0, points.lastIndex)
                                selectedEpoch = points[index].date.toEpochDay()
                            }
                        }
                    }
            ) {
                if (points.isEmpty()) return@Canvas
                val slot = size.width / points.size
                val barWidth = (slot * 0.68f).coerceAtLeast(1f)
                val bottom = size.height - 2.dp.toPx()
                repeat(3) { line ->
                    val y = bottom * line / 2f
                    drawLine(axis, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                }
                points.forEachIndexed { index, point ->
                    point.value?.let { value ->
                        val barHeight = ((value / max).coerceIn(0.0, 1.0) * (bottom - 2.dp.toPx())).toFloat()
                            .coerceAtLeast(2.dp.toPx())
                        drawRect(
                            color = if (index == selectedIndex) lime else cyan,
                            topLeft = Offset(index * slot + (slot - barWidth) / 2, bottom - barHeight),
                            size = Size(barWidth, barHeight)
                        )
                    }
                }
            }
            if (points.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(
                            R.string.dashboard_date_interval,
                            points.first().date.format(dateFormat), points.last().date.format(dateFormat)
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(stringResource(R.string.chart_gaps_note), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (!compact && selected != null) {
                if (points.size > 1) {
                    Slider(
                        value = selectedIndex.toFloat(),
                        onValueChange = { value ->
                            selectedEpoch = points[value.roundToInt().coerceIn(0, points.lastIndex)].date.toEpochDay()
                        },
                        valueRange = 0f..points.lastIndex.toFloat(),
                        steps = (points.size - 2).coerceAtLeast(0),
                        modifier = Modifier.testTag("steps_day_selector").semantics {
                            contentDescription = chartDescription
                            stateDescription = selectedDescription
                        }
                    )
                }
                Column(
                    Modifier.fillMaxWidth().testTag("steps_selected_day").semantics { liveRegion = LiveRegionMode.Polite },
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(selectedDescription, style = MaterialTheme.typography.titleMedium)
                    Text(
                        selected.sourceLabel?.let { stringResource(R.string.dashboard_daily_sources, it) }
                            ?: stringResource(R.string.dashboard_source_unreported),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = { showList = !showList }, modifier = Modifier.fillMaxWidth().testTag("steps_list_toggle")) {
                    Text(stringResource(if (showList) R.string.dashboard_hide_list else R.string.dashboard_show_list))
                }
                if (showList) {
                    points.forEach { point ->
                        TextButton(
                            onClick = { selectedEpoch = point.date.toEpochDay() },
                            modifier = Modifier.fillMaxWidth().testTag("steps_day_${point.date}")
                        ) {
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    stringResource(
                                        R.string.dashboard_selected_steps,
                                        point.date.format(dateFormat),
                                        point.value?.let(number::format) ?: stringResource(R.string.value_unavailable)
                                    ),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    point.sourceLabel?.let { stringResource(R.string.dashboard_daily_sources, it) }
                                        ?: stringResource(R.string.dashboard_source_unreported),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
