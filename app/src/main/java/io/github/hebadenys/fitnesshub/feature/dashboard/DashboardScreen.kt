package io.github.hebadenys.fitnesshub.feature.dashboard

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = FitnessHubTheme.spacing
    val dimensions = FitnessHubTheme.dimensions
    val snackbarHostState = remember { SnackbarHostStateState() }

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
                        text = stringResource(R.string.dashboard_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() }
                    )
                },
                actions = {
                    val contentState = (uiState as? io.github.hebadenys.fitnesshub.ui.state.ScreenState.Content)?.data
                    if (contentState?.isSyncing == true) {
                        Box(
                            modifier = Modifier
                                .padding(spacing.sm)
                                .size(dimensions.minTouchTarget),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(dimensions.iconMedium),
                                strokeWidth = spacing.xxs
                            )
                        }
                    } else {
                        IconButton(
                            onClick = { viewModel.sync() },
                            modifier = Modifier.defaultMinSize(
                                minWidth = dimensions.minTouchTarget,
                                minHeight = dimensions.minTouchTarget
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.action_sync_now),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState.hostState) }
    ) { padding ->
        ScreenStateHandler(
            state = uiState,
            modifier = Modifier.padding(padding),
            onGrantPermissions = {
                permissionLauncher.launch(viewModel.health.permissions)
            },
            onAction = {
                viewModel.sync()
            },
            onRetry = {
                viewModel.sync()
            },
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
            if (model.syncMessage != null) {
                val successMsg = stringResource(R.string.dashboard_sync_success)
                val failPattern = stringResource(R.string.dashboard_sync_failed, model.syncMessage)
                LaunchedEffect(model.syncMessage) {
                    val msg = if (model.syncMessage == "SUCCESS") successMsg else failPattern
                    snackbarHostState.hostState.showSnackbar(msg)
                }
            }

            val d = model.latest

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.md)
            ) {
                // Section: Today's Summary
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.dashboard_today_section),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { heading() }
                    )

                    val dateText = when (d?.date) {
                        LocalDate.now() -> stringResource(R.string.dashboard_date_today)
                        LocalDate.now().minusDays(1) -> stringResource(R.string.dashboard_date_yesterday)
                        else -> d?.date?.toString() ?: ""
                    }
                    if (dateText.isNotEmpty()) {
                        Text(
                            text = dateText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Steps Card
                MetricCard(
                    label = stringResource(R.string.metric_steps),
                    value = d?.steps?.let { stringResource(R.string.unit_steps, it) },
                    icon = Icons.Default.LocationOn,
                    delta = model.stepsDelta
                )

                // Active Calories Card
                MetricCard(
                    label = stringResource(R.string.metric_active_calories),
                    value = d?.activeCalories?.let { stringResource(R.string.unit_kcal, it) },
                    icon = Icons.Default.Star,
                    delta = model.activeCaloriesDelta
                )

                // Sleep Card
                MetricCard(
                    label = stringResource(R.string.metric_sleep),
                    value = d?.sleepMinutes?.let { minutes ->
                        val hours = minutes / 60
                        val remMin = minutes % 60
                        stringResource(R.string.unit_hours_minutes, hours, remMin)
                    },
                    icon = Icons.Default.DateRange,
                    delta = model.sleepDelta
                )

                // Resting Heart Rate Card
                MetricCard(
                    label = stringResource(R.string.metric_resting_heart_rate),
                    value = d?.restingHeartRate?.let { stringResource(R.string.unit_bpm, it) },
                    icon = Icons.Default.Favorite,
                    delta = model.restingHrDelta
                )

                // Weight Card
                MetricCard(
                    label = stringResource(R.string.metric_weight),
                    value = d?.weightKg?.let { stringResource(R.string.unit_kg, it) },
                    icon = Icons.Default.Home,
                    delta = model.weightDelta,
                    provenance = d?.provenance,
                    algorithm = d?.algorithm
                )

                Spacer(modifier = Modifier.height(spacing.sm))

                // Trends Section
                Text(
                    text = stringResource(R.string.dashboard_trends_section),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { heading() }
                )

                TrendChart(
                    title = stringResource(R.string.chart_steps_title),
                    points = model.chartPoints,
                    selectedRange = model.selectedRange,
                    onRangeSelected = { viewModel.setRange(it) },
                    chartContentDescription = stringResource(R.string.cd_chart_steps)
                )

                Spacer(modifier = Modifier.height(spacing.lg))
            }
        }
    }
}

private class SnackbarHostStateState {
    val hostState = SnackbarHostState()
}
