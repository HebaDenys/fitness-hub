package io.github.hebadenys.fitnesshub.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hebadenys.fitnesshub.R
import io.github.hebadenys.fitnesshub.core.model.HealthMetrics
import io.github.hebadenys.fitnesshub.ui.state.ScreenStateHandler
import io.github.hebadenys.fitnesshub.ui.theme.FitnessHubTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthConnectSourceScreen(
    onBack: () -> Unit,
    onManualBody: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val permissionLauncher = rememberLauncherForActivityResult(viewModel.health.permissionContract) {
        viewModel.refresh()
    }
    val snackbar = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_hc_section)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_navigate_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        ScreenStateHandler(state = state, modifier = Modifier.padding(padding)) { model ->
            model.syncOutcome?.let { outcome ->
                val success = stringResource(R.string.dashboard_sync_success)
                val failure = stringResource(R.string.dashboard_sync_failed)
                LaunchedEffect(outcome) {
                    snackbar.showSnackbar(if (outcome is SyncOutcome.Success) success else failure)
                }
            }
            HealthConnectSourceContent(
                model = model,
                onPermissions = { permissionLauncher.launch(viewModel.health.permissions) },
                onSync = viewModel::sync,
                onRefresh = viewModel::refresh,
                onManualBody = onManualBody
            )
        }
    }
}

@Composable
internal fun HealthConnectSourceContent(
    model: SettingsUiModel,
    onPermissions: () -> Unit = {},
    onSync: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onManualBody: () -> Unit = {}
) {
    val spacing = FitnessHubTheme.spacing
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("health_connect_source"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(spacing.md)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (model.hasAnyPermission)
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Text(
                        text = stringResource(
                            when {
                                !model.isClientAvailable -> R.string.settings_hc_unavailable
                                model.hasAnyPermission -> R.string.settings_hc_connected
                                else -> R.string.settings_hc_disconnected
                            }
                        ),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        stringResource(
                            R.string.source_health_permissions_count,
                            model.grantedMetricCount,
                            HealthMetrics.ALL.size
                        )
                    )
                    Text(
                        model.lastLocalSyncMillis?.let {
                            stringResource(R.string.source_health_last_sync, formatSourceTime(it))
                        } ?: stringResource(R.string.source_health_never_sync),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Text(stringResource(R.string.source_hc_apps_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.source_hc_apps_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        stringResource(
                            if (model.historyGranted) R.string.source_hc_history_granted else R.string.source_hc_history_limited
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        if (model.observedOrigins.isNotEmpty()) {
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Column(Modifier.padding(spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        Text(stringResource(R.string.source_hc_observed_origins), style = MaterialTheme.typography.titleMedium)
                        model.observedOrigins.forEach { origin ->
                            Text(origin, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            stringResource(R.string.source_hc_observed_origins_hint),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Button(
                    onClick = onPermissions,
                    enabled = model.isClientAvailable && !model.isSyncing,
                    modifier = Modifier.fillMaxWidth().testTag("health_permissions")
                ) {
                    Text(stringResource(R.string.source_hc_manage_access))
                }
                OutlinedButton(
                    onClick = onRefresh,
                    enabled = !model.isSyncing,
                    modifier = Modifier.weight(1f).testTag("health_check_access")
                ) {
                    Text(stringResource(R.string.source_hc_check_access))
                }
            }
        }

        item {
            Button(
                onClick = onSync,
                enabled = model.isClientAvailable && model.hasAnyPermission && !model.isSyncing,
                modifier = Modifier.fillMaxWidth().testTag("health_sync")
            ) {
                if (model.isSyncing) CircularProgressIndicator(modifier = Modifier.padding(end = spacing.sm))
                else Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.padding(end = spacing.sm))
                Text(stringResource(if (model.isSyncing) R.string.action_syncing else R.string.action_sync_now))
            }
        }

        if (!model.isClientAvailable || !model.hasAnyPermission) {
            item {
                Text(
                    stringResource(R.string.settings_manual_body_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = onManualBody,
                    modifier = Modifier.fillMaxWidth().testTag("health_manual_body")
                ) { Text(stringResource(R.string.settings_manual_body_title)) }
            }
        }

        item {
            Text(stringResource(R.string.source_hc_permissions_title), style = MaterialTheme.typography.titleLarge)
        }

        items(model.metricStatuses, key = { it.metricKey }) { status ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)) {
                Column(
                    Modifier.fillMaxWidth().padding(spacing.md),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm)
                ) {
                    Text(metricLabel(status.metricKey), style = MaterialTheme.typography.bodyMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (status.isGranted) Icons.Default.Check else Icons.Default.Close,
                            contentDescription = null,
                            tint = if (status.isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            stringResource(
                                if (status.isGranted) R.string.settings_permission_granted
                                else R.string.settings_permission_denied
                            ),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun metricLabel(metricKey: String): String = when (metricKey) {
    HealthMetrics.STEPS -> stringResource(R.string.metric_steps)
    HealthMetrics.DISTANCE -> stringResource(R.string.metric_distance)
    HealthMetrics.ACTIVE_CALORIES -> stringResource(R.string.metric_active_calories)
    HealthMetrics.TOTAL_CALORIES -> stringResource(R.string.metric_total_calories)
    HealthMetrics.EXERCISE -> stringResource(R.string.metric_exercise)
    HealthMetrics.HEART_RATE -> stringResource(R.string.metric_heart_rate)
    HealthMetrics.RESTING_HEART_RATE -> stringResource(R.string.metric_resting_heart_rate)
    HealthMetrics.OXYGEN_SATURATION -> stringResource(R.string.metric_oxygen_saturation)
    HealthMetrics.SLEEP -> stringResource(R.string.metric_sleep)
    HealthMetrics.WEIGHT -> stringResource(R.string.metric_weight)
    HealthMetrics.BODY_FAT -> stringResource(R.string.metric_body_fat)
    else -> metricKey
}

@Composable
private fun formatSourceTime(value: Long): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(value))
