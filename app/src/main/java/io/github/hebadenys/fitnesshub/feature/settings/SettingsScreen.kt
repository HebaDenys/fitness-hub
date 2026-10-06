package io.github.hebadenys.fitnesshub.feature.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hebadenys.fitnesshub.PrivacyRationaleActivity
import io.github.hebadenys.fitnesshub.R
import io.github.hebadenys.fitnesshub.core.model.HealthMetrics
import io.github.hebadenys.fitnesshub.feature.scale.ScaleSettingsSection
import io.github.hebadenys.fitnesshub.ui.state.ScreenStateHandler
import io.github.hebadenys.fitnesshub.ui.theme.FitnessHubTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToInsights: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = FitnessHubTheme.spacing
    val cornerRadius = FitnessHubTheme.cornerRadius
    val dimensions = FitnessHubTheme.dimensions
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = viewModel.health.permissionContract
    ) {
        viewModel.refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        ScreenStateHandler(
            state = uiState,
            modifier = Modifier.padding(padding)
        ) { model ->
            val outcome = model.syncOutcome
            if (outcome != null) {
                val successMsg = stringResource(R.string.dashboard_sync_success)
                val failPattern = stringResource(R.string.dashboard_sync_failed)
                LaunchedEffect(outcome) {
                    val msg = when (outcome) {
                        is SyncOutcome.Success -> successMsg
                        is SyncOutcome.Failure -> failPattern
                    }
                    snackbarHostState.showSnackbar(msg)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.md)
            ) {
                // Section: Health Connect Status
                Text(
                    text = stringResource(R.string.settings_hc_section),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { heading() }
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(spacing.md)) {
                        val statusText = when {
                            !model.isClientAvailable -> stringResource(R.string.settings_hc_unavailable)
                            model.hasAnyPermission -> stringResource(R.string.settings_hc_connected)
                            else -> stringResource(R.string.settings_hc_disconnected)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            if (model.isClientAvailable && !model.hasAnyPermission) {
                                Button(
                                    onClick = { permissionLauncher.launch(viewModel.health.permissions) },
                                    modifier = Modifier.defaultMinSize(minHeight = dimensions.minTouchTarget)
                                ) {
                                    Text(text = stringResource(R.string.action_grant_permissions))
                                }
                            }
                        }

                        if (model.isClientAvailable && model.hasAnyPermission) {
                            Spacer(modifier = Modifier.height(spacing.sm))
                            OutlinedButton(
                                onClick = { permissionLauncher.launch(viewModel.health.permissions) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = dimensions.minTouchTarget)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    modifier = Modifier.size(dimensions.iconSmall)
                                )
                                Spacer(modifier = Modifier.size(spacing.sm))
                                Text(text = stringResource(R.string.action_grant_permissions))
                            }
                        }
                    }
                }

                // Section: Historical Data Access
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(spacing.md),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.settings_history_access),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        PermissionBadge(isGranted = model.historyGranted)
                    }
                }

                Spacer(modifier = Modifier.height(spacing.xs))

                // Section: Per-Metric Permissions
                Text(
                    text = stringResource(R.string.settings_permissions_section),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { heading() }
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(spacing.md)) {
                        model.metricStatuses.forEachIndexed { index, status ->
                            if (index > 0) {
                                Spacer(modifier = Modifier.height(spacing.sm))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = metricLabel(status.metricKey),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                PermissionBadge(isGranted = status.isGranted)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(spacing.xs))

                // Section: Data Synchronization
                Text(
                    text = stringResource(R.string.settings_sync_section),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { heading() }
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(spacing.md)) {
                        Button(
                            onClick = { viewModel.sync() },
                            enabled = model.isClientAvailable && model.hasAnyPermission && !model.isSyncing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = dimensions.minTouchTarget)
                        ) {
                            if (model.isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(dimensions.iconSmall),
                                    strokeWidth = spacing.xxs,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.size(spacing.sm))
                                Text(text = stringResource(R.string.action_syncing))
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(dimensions.iconSmall)
                                )
                                Spacer(modifier = Modifier.size(spacing.sm))
                                Text(text = stringResource(R.string.action_sync_now))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(spacing.xs))

                // Section: Privacy
                Text(
                    text = stringResource(R.string.settings_privacy_section),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { heading() }
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(spacing.md)) {
                        Text(
                            text = stringResource(R.string.settings_privacy_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(spacing.md))
                        OutlinedButton(
                            onClick = {
                                context.startActivity(Intent(context, PrivacyRationaleActivity::class.java))
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = dimensions.minTouchTarget)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(dimensions.iconSmall)
                            )
                            Spacer(modifier = Modifier.size(spacing.sm))
                            Text(text = stringResource(R.string.settings_view_rationale))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(spacing.lg))

                ScaleSettingsSection()

                Spacer(modifier = Modifier.height(spacing.lg))

                InsightsSettingsSection(onClick = onNavigateToInsights)

                Spacer(modifier = Modifier.height(spacing.lg))

                AiSettingsSection()

                Spacer(modifier = Modifier.height(spacing.lg))
            }
        }
    }
}

/**
 * Entry point to the cross-domain view. Insights is not a tab: it is a secondary
 * surface, so it lives in Settings rather than displacing a primary destination
 * in the bottom bar.
 */
@Composable
private fun InsightsSettingsSection(onClick: () -> Unit) {
    val spacing = FitnessHubTheme.spacing
    val dimensions = FitnessHubTheme.dimensions

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(spacing.md)) {
            Text(
                text = stringResource(R.string.insights_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.insights_settings_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(spacing.md))
            OutlinedButton(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = dimensions.minTouchTarget)
            ) {
                Text(text = stringResource(R.string.insights_open))
            }
        }
    }
}

@Composable
private fun PermissionBadge(
    isGranted: Boolean,
    modifier: Modifier = Modifier
) {
    val spacing = FitnessHubTheme.spacing
    val cornerRadius = FitnessHubTheme.cornerRadius
    val dimensions = FitnessHubTheme.dimensions

    val (bg, fg, label, icon) = if (isGranted) {
        Quad(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            MaterialTheme.colorScheme.primary,
            stringResource(R.string.settings_permission_granted),
            Icons.Default.Check
        )
    } else {
        Quad(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            stringResource(R.string.settings_permission_denied),
            Icons.Default.Close
        )
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius.sm))
            .background(bg)
            .padding(horizontal = spacing.sm, vertical = spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.xxs)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(dimensions.iconSmall)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = fg
        )
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

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
