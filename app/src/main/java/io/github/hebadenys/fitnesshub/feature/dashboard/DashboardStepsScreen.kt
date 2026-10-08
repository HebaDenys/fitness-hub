package io.github.hebadenys.fitnesshub.feature.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hebadenys.fitnesshub.R
import io.github.hebadenys.fitnesshub.ui.components.ChartSkeleton
import io.github.hebadenys.fitnesshub.ui.components.TimeRange
import io.github.hebadenys.fitnesshub.ui.state.ScreenState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardStepsScreen(
    onBack: () -> Unit,
    onAddBody: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var range by rememberSaveable { mutableStateOf(TimeRange.SEVEN_DAYS) }
    val content = (state as? ScreenState.Content)?.data
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.metric_steps)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_navigate_back))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(20.dp).testTag("dashboard_steps_screen"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (state is ScreenState.Loading) {
                ChartSkeleton()
            } else {
                DashboardStepsChart(
                    points = dashboardStepPoints(content?.recent.orEmpty(), range),
                    range = range,
                    onRangeSelected = { range = it }
                )
                if (state is ScreenState.PermissionMissing) {
                    Text(stringResource(R.string.state_permission_description), style = MaterialTheme.typography.bodyMedium)
                }
                if (state is ScreenState.Error) {
                    Text(stringResource(R.string.state_error_title), color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = viewModel::sync) { Text(stringResource(R.string.action_retry)) }
                }
            }
            Button(onClick = onAddBody, modifier = Modifier.fillMaxWidth().testTag("steps_add_body")) {
                Text(stringResource(R.string.settings_manual_body_title))
            }
        }
    }
}
