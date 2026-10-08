package io.github.hebadenys.fitnesshub.feature.body

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hebadenys.fitnesshub.R
import io.github.hebadenys.fitnesshub.core.body.CanonicalBodyMetricResolver
import io.github.hebadenys.fitnesshub.ui.components.TimeRange
import io.github.hebadenys.fitnesshub.ui.state.ScreenStateHandler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyScreen(
    onNavigateToSettings: () -> Unit,
    onAddMeasurement: () -> Unit = {},
    viewModel: BodyViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(topBar = {
        TopAppBar(
            title = {
                Text(stringResource(R.string.body_title), style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() })
            },
            actions = {
                IconButton(onClick = onAddMeasurement, modifier = Modifier.testTag("body_add_measurement")) {
                    Icon(Icons.Default.Add, stringResource(R.string.body_manual_title), tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onNavigateToSettings) {
                    Icon(Icons.Default.Settings, stringResource(R.string.action_open_settings))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            ScreenStateHandler(state = state) { model ->
                BodyReadyContent(model, viewModel::setRange, onAddMeasurement)
            }
        }
    }
}

@Composable
internal fun BodyReadyContent(model: BodyUiModel, onRange: (TimeRange) -> Unit, onAddMeasurement: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("body_history_list"),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(stringResource(R.string.body_latest_section), style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() })
        }
        item { BodyMeasurementCard(stringResource(R.string.metric_weight), model.latestWeight, prominent = true) }
        item { BodyMeasurementCard(stringResource(R.string.metric_body_fat), model.latestBodyFat) }
        item { BodyObservationChart(model.weightObservations, model.selectedRange, onRange) }
        item {
            Button(onClick = onAddMeasurement, modifier = Modifier.fillMaxWidth().testTag("body_add_measurement_button")) {
                Text(stringResource(R.string.body_manual_title))
            }
        }
        item {
            Text(stringResource(R.string.body_exact_history), style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.body_history_record_count, model.observations.size), style = MaterialTheme.typography.bodyMedium)
        }
        if (model.observations.isEmpty()) {
            item { Text(stringResource(R.string.body_history_empty), style = MaterialTheme.typography.bodyMedium) }
        }
        itemsIndexed(model.observations) { index, value ->
            Column(Modifier.testTag("body_record_$index")) {
                BodyMeasurementCard(
                    stringResource(
                        if (value.metric == CanonicalBodyMetricResolver.Metric.WEIGHT) R.string.metric_weight
                        else R.string.metric_body_fat
                    ),
                    value
                )
            }
        }
    }
}
