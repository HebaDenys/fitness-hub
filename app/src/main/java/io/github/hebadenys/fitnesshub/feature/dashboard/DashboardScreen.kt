package io.github.hebadenys.fitnesshub.feature.dashboard

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hebadenys.fitnesshub.R
import io.github.hebadenys.fitnesshub.ui.components.ChartSkeleton
import io.github.hebadenys.fitnesshub.ui.components.MetricCardSkeleton
import io.github.hebadenys.fitnesshub.ui.state.ScreenState
import io.github.hebadenys.fitnesshub.ui.state.ScreenStateHandler
import io.github.hebadenys.fitnesshub.ui.theme.FitnessHubTheme

@Composable
fun DashboardScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToBody: () -> Unit = {},
    onNavigateToNutrition: () -> Unit = {},
    onNavigateToSteps: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val permissionLauncher = rememberLauncherForActivityResult(viewModel.health.permissionContract) {
        viewModel.refreshPermissions()
    }
    val message = (uiState as? ScreenState.Content)?.data?.syncMessage
    val success = stringResource(R.string.dashboard_sync_success)
    val failure = stringResource(R.string.dashboard_sync_failed)
    LaunchedEffect(message) {
        if (message != null) snackbar.showSnackbar(if (message == "SUCCESS") success else failure)
    }

    Scaffold(
        topBar = {
            DashboardTopBar(
                isSyncing = (uiState as? ScreenState.Content)?.data?.isSyncing == true,
                onSync = viewModel::sync,
                onNavigateToSettings = onNavigateToSettings
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        // The content slot is padded once, including loading and empty states.
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val state = uiState) {
                ScreenState.Loading -> Column(
                    Modifier.fillMaxSize().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) { MetricCardSkeleton(); MetricCardSkeleton(); ChartSkeleton() }
                is ScreenState.Empty, is ScreenState.PermissionMissing -> DashboardWelcomeContent(
                    permissionMissing = state is ScreenState.PermissionMissing,
                    onAddBody = onNavigateToBody,
                    onConnections = onNavigateToSettings,
                    onGrantPermissions = { permissionLauncher.launch(viewModel.health.permissions) }
                )
                is ScreenState.Content -> DashboardReadyContent(
                    model = state.data,
                    onAddBody = onNavigateToBody,
                    onNutrition = onNavigateToNutrition,
                    onSteps = onNavigateToSteps
                )
                is ScreenState.Error -> ScreenStateHandler<DashboardUiModel>(
                    state = state, onRetry = viewModel::sync, content = {}
                )
            }
        }
    }
}

/** Settings remains available independently of permission, loading and sync state. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DashboardTopBar(
    isSyncing: Boolean,
    onSync: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val spacing = FitnessHubTheme.spacing
    val dimensions = FitnessHubTheme.dimensions
    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 28.sp),
                modifier = Modifier.testTag("dashboard_title").semantics { heading() }
            )
        },
        actions = {
            if (isSyncing) {
                Box(
                    modifier = Modifier.size(dimensions.minTouchTarget),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(dimensions.iconMedium),
                        strokeWidth = spacing.xxs
                    )
                }
            } else {
                IconButton(onClick = onSync) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.action_sync_now),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            IconButton(
                onClick = onNavigateToSettings,
                modifier = Modifier.testTag("dashboard_settings").defaultMinSize(
                    minWidth = dimensions.minTouchTarget,
                    minHeight = dimensions.minTouchTarget
                )
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = stringResource(R.string.action_open_settings),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}
