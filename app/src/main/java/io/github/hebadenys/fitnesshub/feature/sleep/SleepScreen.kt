package io.github.hebadenys.fitnesshub.feature.sleep

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hebadenys.fitnesshub.R
import io.github.hebadenys.fitnesshub.ui.components.ChartSkeleton
import io.github.hebadenys.fitnesshub.ui.components.MetricCard
import io.github.hebadenys.fitnesshub.ui.components.MetricCardSkeleton
import io.github.hebadenys.fitnesshub.ui.components.TrendChart
import io.github.hebadenys.fitnesshub.ui.state.ScreenStateHandler
import io.github.hebadenys.fitnesshub.ui.theme.FitnessHubTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: SleepViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = FitnessHubTheme.spacing

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = viewModel.health.permissionContract
    ) {
        viewModel.refreshPermissions()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.sleep_title),
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
            onGrantPermissions = {
                permissionLauncher.launch(viewModel.health.permissions)
            },
            onAction = onNavigateToSettings,
            loadingContent = {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(spacing.md),
                    verticalArrangement = Arrangement.spacedBy(spacing.md)
                ) {
                    MetricCardSkeleton()
                    MetricCardSkeleton()
                    ChartSkeleton()
                }
            }
        ) { model ->
            val latest = model.latest

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.md)
            ) {
                item {
                    Spacer(modifier = Modifier.height(spacing.xs))
                    Text(
                        text = stringResource(R.string.dashboard_today_section),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { heading() }
                    )
                }

                item {
                    MetricCard(
                        label = stringResource(R.string.metric_sleep),
                        value = latest?.sleepMinutes?.let { minutes ->
                            val hours = minutes / 60
                            val remMin = minutes % 60
                            stringResource(R.string.unit_hours_minutes, hours, remMin)
                        },
                        subtitle = stringResource(R.string.sleep_attribution_note),
                        icon = Icons.Default.DateRange,
                        delta = model.sleepDelta
                    )
                }

                item {
                    MetricCard(
                        label = stringResource(R.string.metric_resting_heart_rate),
                        value = latest?.restingHeartRate?.let { stringResource(R.string.unit_bpm, it) },
                        icon = Icons.Default.Favorite,
                        delta = model.restingHrDelta
                    )
                }

                item {
                    MetricCard(
                        label = stringResource(R.string.metric_oxygen_saturation),
                        value = latest?.oxygenSaturation?.let { stringResource(R.string.unit_percent, it) },
                        icon = Icons.Default.Info
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(spacing.xs))
                    TrendChart(
                        title = stringResource(R.string.chart_sleep_title),
                        points = model.chartPoints,
                        selectedRange = model.selectedRange,
                        onRangeSelected = { viewModel.setRange(it) },
                        chartContentDescription = stringResource(R.string.cd_chart_sleep)
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(spacing.sm))
                    Text(
                        text = stringResource(R.string.sleep_history_section),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { heading() }
                    )
                }

                items(model.history, key = { it.date }) { day ->
                    val sleepStr = day.sleepMinutes?.let { minutes ->
                        val hours = minutes / 60
                        val remMin = minutes % 60
                        stringResource(R.string.unit_hours_minutes, hours, remMin)
                    } ?: stringResource(R.string.value_unavailable)

                    val hrStr = day.restingHeartRate?.let { stringResource(R.string.unit_bpm, it) }
                        ?: stringResource(R.string.value_unavailable)

                    val spo2Str = day.oxygenSaturation?.let { stringResource(R.string.unit_percent, it) }
                        ?: stringResource(R.string.value_unavailable)

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(spacing.md)) {
                            Text(
                                text = day.date.toString(),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(spacing.xs))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = sleepStr,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = hrStr,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = spo2Str,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(spacing.lg))
                }
            }
        }
    }
}
