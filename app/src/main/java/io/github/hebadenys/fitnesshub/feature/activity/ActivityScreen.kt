package io.github.hebadenys.fitnesshub.feature.activity

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
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.Alignment
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
fun ActivityScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: ActivityViewModel = hiltViewModel()
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
                        text = stringResource(R.string.activity_title),
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
                        label = stringResource(R.string.metric_steps),
                        value = latest?.steps?.let { stringResource(R.string.unit_steps, it) },
                        icon = Icons.Default.LocationOn,
                        delta = model.stepsDelta
                    )
                }

                item {
                    MetricCard(
                        label = stringResource(R.string.metric_distance),
                        value = latest?.distanceMeters?.let { stringResource(R.string.unit_km, it / 1000.0) },
                        icon = Icons.Default.LocationOn
                    )
                }

                item {
                    MetricCard(
                        label = stringResource(R.string.metric_active_calories),
                        value = latest?.activeCalories?.let { stringResource(R.string.unit_kcal, it) },
                        icon = Icons.Default.Star
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(spacing.xs))
                    TrendChart(
                        title = stringResource(R.string.chart_steps_title),
                        points = model.chartPoints,
                        selectedRange = model.selectedRange,
                        onRangeSelected = { viewModel.setRange(it) },
                        chartContentDescription = stringResource(R.string.cd_chart_steps)
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(spacing.sm))
                    Text(
                        text = stringResource(R.string.activity_history_section),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { heading() }
                    )
                }

                items(model.history, key = { it.date }) { day ->
                    val stepsStr = day.steps?.let { stringResource(R.string.unit_steps, it) }
                        ?: stringResource(R.string.value_unavailable)
                    val distStr = day.distanceMeters?.let { stringResource(R.string.unit_km, it / 1000.0) }
                        ?: stringResource(R.string.value_unavailable)
                    val calStr = day.activeCalories?.let { stringResource(R.string.unit_kcal, it) }
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
                                    text = stepsStr,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = distStr,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = calStr,
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
