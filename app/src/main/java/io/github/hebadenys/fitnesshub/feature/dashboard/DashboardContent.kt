package io.github.hebadenys.fitnesshub.feature.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hebadenys.fitnesshub.R
import io.github.hebadenys.fitnesshub.core.model.DailySummary
import io.github.hebadenys.fitnesshub.feature.body.sourceLabel
import io.github.hebadenys.fitnesshub.feature.body.toProvenance
import io.github.hebadenys.fitnesshub.ui.components.MetricCard
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun DashboardReadyContent(
    model: DashboardUiModel,
    onAddBody: () -> Unit,
    onNutrition: () -> Unit,
    onSteps: () -> Unit
) {
    val day = model.latest
    val today = LocalDate.now()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).testTag("dashboard_content"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            stringResource(if (day == null || day.date == today) R.string.dashboard_today_section else R.string.dashboard_latest_available),
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            (day?.date ?: today).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Card(
            onClick = onSteps,
            modifier = Modifier.fillMaxWidth().testTag("dashboard_steps"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.metric_steps), style = MaterialTheme.typography.titleLarge)
                Text(
                    day?.steps?.let { NumberFormat.getIntegerInstance().format(it) }
                        ?: stringResource(R.string.value_unavailable),
                    style = MaterialTheme.typography.headlineLarge.copy(fontSize = 40.sp, lineHeight = 48.sp)
                )
                Text(stringResource(R.string.dashboard_open_steps), style = MaterialTheme.typography.bodyMedium)
            }
        }
        val sleep = day?.sleepMinutes?.let { stringResource(R.string.unit_hours_minutes, it / 60, it % 60) }
            ?: stringResource(R.string.value_unavailable)
        val weight = model.latestWeight?.value?.let { stringResource(R.string.unit_kg, it) }
            ?: stringResource(R.string.value_unavailable)
        val weightSource = model.latestWeight?.let {
            stringResource(
                R.string.body_measurement_source,
                it.measuredAt.atZone(ZoneId.systemDefault()).toLocalDate().toString(),
                it.sourceLabel()
            )
        }
        BoxWithConstraints {
            val twoColumns = maxWidth >= 320.dp && LocalDensity.current.fontScale <= 1.2f
            if (twoColumns) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DashboardMetricTile(stringResource(R.string.metric_sleep), sleep, Modifier.weight(1f))
                    DashboardMetricTile(
                        stringResource(R.string.metric_weight), weight, Modifier.weight(1f),
                        subtitle = weightSource,
                        estimate = model.latestWeight?.toProvenance() == DailySummary.PROVENANCE_ESTIMATE
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DashboardMetricTile(stringResource(R.string.metric_sleep), sleep)
                    DashboardMetricTile(
                        stringResource(R.string.metric_weight), weight,
                        subtitle = weightSource,
                        estimate = model.latestWeight?.toProvenance() == DailySummary.PROVENANCE_ESTIMATE
                    )
                }
            }
        }
        DashboardStepsChart(model.chartPoints, model.selectedRange, onRangeSelected = {}, compact = true, onOpen = onSteps)
        Button(onClick = onAddBody, modifier = Modifier.fillMaxWidth().testTag("dashboard_add_body")) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            Text(stringResource(R.string.settings_manual_body_title))
        }
        OutlinedButton(onClick = onNutrition, modifier = Modifier.fillMaxWidth().testTag("dashboard_nutrition")) {
            Text(stringResource(R.string.dashboard_open_meals))
        }
        MetricCard(
            label = stringResource(R.string.metric_active_calories),
            value = day?.activeCalories?.let { stringResource(R.string.unit_kcal, it) },
            icon = Icons.Default.Star, delta = model.activeCaloriesDelta
        )
        MetricCard(
            label = stringResource(R.string.metric_resting_heart_rate),
            value = day?.restingHeartRate?.let { stringResource(R.string.unit_bpm, it) },
            icon = Icons.Default.Favorite, delta = model.restingHrDelta
        )
    }
}

@Composable
private fun DashboardMetricTile(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    estimate: Boolean = false
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineMedium)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (estimate) Text(stringResource(R.string.provenance_estimate), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
internal fun DashboardWelcomeContent(
    permissionMissing: Boolean,
    onAddBody: () -> Unit,
    onConnections: () -> Unit,
    onGrantPermissions: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).testTag("dashboard_empty"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(stringResource(R.string.dashboard_today_section), style = MaterialTheme.typography.headlineLarge)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    stringResource(if (permissionMissing) R.string.state_permission_title else R.string.dashboard_start_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() }
                )
                Text(stringResource(R.string.dashboard_start_description), style = MaterialTheme.typography.bodyLarge)
                Button(onClick = onAddBody, modifier = Modifier.fillMaxWidth().testTag("dashboard_add_body")) {
                    Text(stringResource(R.string.settings_manual_body_title))
                }
                OutlinedButton(onClick = onConnections, modifier = Modifier.fillMaxWidth().testTag("dashboard_connections")) {
                    Text(stringResource(R.string.dashboard_open_connections))
                }
                if (permissionMissing) {
                    TextButton(onClick = onGrantPermissions, modifier = Modifier.fillMaxWidth().testTag("dashboard_permissions")) {
                        Text(stringResource(R.string.action_grant_permissions))
                    }
                }
            }
        }
        listOf(R.string.metric_steps, R.string.metric_sleep, R.string.metric_weight).forEach {
            DashboardMetricTile(stringResource(it), stringResource(R.string.value_unavailable))
        }
        Text(stringResource(R.string.dashboard_settings_available), style = MaterialTheme.typography.bodyMedium)
    }
}
