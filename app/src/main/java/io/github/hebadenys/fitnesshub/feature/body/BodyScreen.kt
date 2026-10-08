package io.github.hebadenys.fitnesshub.feature.body

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hebadenys.fitnesshub.R
import io.github.hebadenys.fitnesshub.core.body.CanonicalBodyMetricResolver
import io.github.hebadenys.fitnesshub.core.body.ManualBodyInputError
import io.github.hebadenys.fitnesshub.core.body.formatManualBodyTimestamp
import io.github.hebadenys.fitnesshub.ui.components.ChartSkeleton
import io.github.hebadenys.fitnesshub.ui.components.MetricCard
import io.github.hebadenys.fitnesshub.ui.components.MetricCardSkeleton
import io.github.hebadenys.fitnesshub.ui.components.TrendChart
import io.github.hebadenys.fitnesshub.ui.state.ScreenStateHandler
import io.github.hebadenys.fitnesshub.ui.theme.FitnessHubTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: BodyViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val manualState by viewModel.manualEntryState.collectAsStateWithLifecycle()
    val spacing = FitnessHubTheme.spacing

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = viewModel.health.permissionContract
    ) { viewModel.refreshPermissions() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.body_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        ScreenStateHandler(
            state = uiState,
            modifier = Modifier.padding(padding),
            loadingContent = {
                Column(
                    modifier = Modifier.fillMaxSize().padding(spacing.md),
                    verticalArrangement = Arrangement.spacedBy(spacing.md)
                ) {
                    MetricCardSkeleton()
                    MetricCardSkeleton()
                    ChartSkeleton()
                }
            }
        ) { model ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.md)
            ) {
                item {
                    Spacer(Modifier.height(spacing.xs))
                    ManualBodyEntryCard(
                        state = manualState,
                        healthConnectAvailable = model.healthConnectAvailable,
                        hasHealthConnectPermission = model.hasHealthConnectBodyPermission,
                        onGrantPermissions = { permissionLauncher.launch(viewModel.health.permissions) },
                        onSave = viewModel::saveManualMeasurement,
                        onDismissState = viewModel::dismissManualEntryState
                    )
                }

                item {
                    Text(
                        text = stringResource(R.string.body_latest_section),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { heading() }
                    )
                }

                item {
                    val value = model.latestWeight
                    MetricCard(
                        label = stringResource(R.string.metric_weight),
                        value = value?.value?.let { stringResource(R.string.unit_kg, it) },
                        icon = Icons.Default.Home,
                        delta = model.weightDelta,
                        provenance = value?.toProvenance(),
                        algorithm = value?.method?.takeIf { it == CanonicalBodyMetricResolver.Method.LOCAL_ESTIMATE }?.name,
                        subtitle = value?.let {
                            stringResource(
                                R.string.body_measurement_source,
                                formatInstant(it.measuredAt),
                                it.sourceLabel()
                            )
                        }
                    )
                }

                item {
                    val value = model.latestBodyFat
                    MetricCard(
                        label = stringResource(R.string.metric_body_fat),
                        value = value?.value?.let { stringResource(R.string.unit_percent, it) },
                        icon = Icons.Default.Info,
                        delta = model.bodyFatDelta,
                        provenance = value?.toProvenance(),
                        algorithm = value?.method?.takeIf { it == CanonicalBodyMetricResolver.Method.LOCAL_ESTIMATE }?.name,
                        subtitle = value?.let {
                            stringResource(
                                R.string.body_measurement_source,
                                formatInstant(it.measuredAt),
                                it.sourceLabel()
                            )
                        }
                    )
                }

                item {
                    TrendChart(
                        title = stringResource(R.string.chart_weight_title),
                        points = model.chartPoints,
                        selectedRange = model.selectedRange,
                        onRangeSelected = viewModel::setRange,
                        chartContentDescription = stringResource(R.string.cd_chart_weight),
                        valueSuffix = "kg"
                    )
                }

                item {
                    Text(
                        text = stringResource(R.string.body_history_section),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { heading() }
                    )
                }

                if (model.history.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.body_history_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                items(model.history, key = { it.date }) { day ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(spacing.md),
                            verticalArrangement = Arrangement.spacedBy(spacing.sm)
                        ) {
                            Text(day.date.toString(), style = MaterialTheme.typography.titleMedium)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                BodyHistoryValue(
                                    label = stringResource(R.string.metric_weight),
                                    value = day.weight?.value?.let { stringResource(R.string.unit_kg, it) }
                                        ?: stringResource(R.string.value_unavailable),
                                    source = day.weight?.sourceLabel()
                                )
                                BodyHistoryValue(
                                    label = stringResource(R.string.metric_body_fat),
                                    value = day.bodyFat?.value?.let { stringResource(R.string.unit_percent, it) }
                                        ?: stringResource(R.string.value_unavailable),
                                    source = day.bodyFat?.sourceLabel()
                                )
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(spacing.lg)) }
            }
        }
    }
}

@Composable
internal fun ManualBodyEntryCard(
    state: ManualBodyEntryState,
    healthConnectAvailable: Boolean,
    hasHealthConnectPermission: Boolean,
    onGrantPermissions: () -> Unit,
    onSave: (String, String, String) -> Unit,
    onDismissState: () -> Unit
) {
    val spacing = FitnessHubTheme.spacing
    val zone = ZoneId.systemDefault()
    var weight by remember { mutableStateOf("") }
    var bodyFat by remember { mutableStateOf("") }
    var timestamp by remember { mutableStateOf(formatManualBodyTimestamp(Instant.now(), zone)) }

    LaunchedEffect(state) {
        if (state is ManualBodyEntryState.Saved) {
            weight = ""
            bodyFat = ""
            timestamp = formatManualBodyTimestamp(Instant.now(), zone)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().testTag("manual_body_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm)
        ) {
            Text(stringResource(R.string.body_manual_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.body_manual_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = weight,
                onValueChange = { weight = it },
                label = { Text(stringResource(R.string.body_manual_weight)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("manual_weight")
            )
            OutlinedTextField(
                value = bodyFat,
                onValueChange = { bodyFat = it },
                label = { Text(stringResource(R.string.body_manual_body_fat)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("manual_body_fat")
            )
            OutlinedTextField(
                value = timestamp,
                onValueChange = { timestamp = it },
                label = { Text(stringResource(R.string.body_manual_timestamp)) },
                supportingText = { Text(stringResource(R.string.body_manual_timestamp_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("manual_timestamp")
            )

            Button(
                onClick = { onSave(weight, bodyFat, timestamp) },
                enabled = state !is ManualBodyEntryState.Working,
                modifier = Modifier.fillMaxWidth().testTag("manual_save")
            ) {
                if (state is ManualBodyEntryState.Working) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = spacing.sm))
                }
                Text(stringResource(R.string.body_manual_save))
            }

            when (state) {
                ManualBodyEntryState.Idle, ManualBodyEntryState.Working -> Unit
                is ManualBodyEntryState.Saved -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            stringResource(R.string.body_manual_saved),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(onClick = onDismissState) {
                            Text(stringResource(R.string.action_dismiss))
                        }
                    }
                }
                is ManualBodyEntryState.Invalid -> {
                    Text(
                        stringResource(manualErrorString(state.error)),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                ManualBodyEntryState.StorageError -> {
                    Text(
                        stringResource(R.string.body_manual_storage_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (healthConnectAvailable && !hasHealthConnectPermission) {
                Text(
                    stringResource(R.string.body_manual_hc_optional),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = onGrantPermissions,
                    modifier = Modifier.fillMaxWidth().testTag("body_optional_health_connect")
                ) {
                    Text(stringResource(R.string.body_manual_connect_hc))
                }
            }
        }
    }
}

private fun manualErrorString(error: ManualBodyInputError): Int = when (error) {
    ManualBodyInputError.MISSING_VALUES -> R.string.body_manual_error_missing
    ManualBodyInputError.INVALID_WEIGHT -> R.string.body_manual_error_weight
    ManualBodyInputError.INVALID_BODY_FAT -> R.string.body_manual_error_body_fat
    ManualBodyInputError.INVALID_TIMESTAMP -> R.string.body_manual_error_timestamp
    ManualBodyInputError.FUTURE_TIMESTAMP -> R.string.body_manual_error_future
}

@Composable
private fun BodyHistoryValue(label: String, value: String, source: String?) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
        source?.let {
            Text(
                stringResource(R.string.body_history_source, it),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun formatInstant(value: Instant): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault())
        .format(value)
