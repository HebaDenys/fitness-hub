package io.github.hebadenys.fitnesshub.feature.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hebadenys.fitnesshub.R
import io.github.hebadenys.fitnesshub.core.analytics.InsightCard
import io.github.hebadenys.fitnesshub.core.analytics.TrendAnalytics
import io.github.hebadenys.fitnesshub.ui.components.ChartPoint
import io.github.hebadenys.fitnesshub.ui.components.ChartSkeleton
import io.github.hebadenys.fitnesshub.ui.components.TimeRange
import io.github.hebadenys.fitnesshub.ui.components.TrendChart

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(
    onNavigateBack: () -> Unit,
    viewModel: InsightsViewModel = hiltViewModel()
) {
    val model by viewModel.uiState.collectAsStateWithLifecycle()
    val backupState by viewModel.backup.collectAsStateWithLifecycle()
    var range by remember { mutableStateOf(TimeRange.SEVEN_DAYS) }
    val onRangeSelected: (TimeRange) -> Unit = { range = it }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.insights_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        val content = model
        if (content == null) {
            ChartSkeleton()
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            content.cards.forEach { card ->
                InsightCardView(card, range, onRangeSelected)
            }

            BackupSection(
                state = backupState,
                onCreate = viewModel::createBackup,
                onRestore = viewModel::restoreBackup,
                onDismiss = viewModel::dismissBackupState
            )
        }
    }
}

@Composable
private fun InsightCardView(
    card: InsightCard,
    range: TimeRange,
    onRangeSelected: (TimeRange) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = card.title, style = MaterialTheme.typography.titleMedium)

            // Sample size is shown next to the headline: a correlation drawn
            // from three days is not the same claim as one drawn from sixty,
            // and the number has to be visible for that to be honest.
            Text(
                text = stringResource(R.string.insights_sample_size, card.sampleSize),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            TrendChart(
                title = stringResource(R.string.insights_chart_title, card.title),
                points = card.primarySeries.map { ChartPoint(it.date, it.value) },
                selectedRange = range,
                onRangeSelected = onRangeSelected
            )

            Text(
                text = card.correlation?.let { correlation ->
                    stringResource(
                        R.string.insights_correlation_value,
                        correlation.coefficient,
                        stringResource(correlation.strength.labelRes()),
                        correlation.xLabel,
                        correlation.yLabel
                    )
                } ?: stringResource(R.string.insights_correlation_none),
                style = MaterialTheme.typography.bodyMedium
            )

            // The advisory is part of the finding, not an optional footnote:
            // it renders from the model on every card, so it cannot be dropped.
            Text(
                text = stringResource(R.string.insights_advisory),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BackupSection(
    state: BackupState,
    onCreate: (String) -> Unit,
    onRestore: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var passphrase by remember { mutableStateOf("") }
    var archive by remember { mutableStateOf("") }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.backup_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.backup_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = passphrase,
                onValueChange = { passphrase = it },
                label = { Text(stringResource(R.string.backup_passphrase)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = { onCreate(passphrase) },
                enabled = passphrase.isNotEmpty() && state !is BackupState.Working,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.backup_create))
            }

            when (state) {
                is BackupState.Working -> CircularProgressIndicator()
                is BackupState.Created -> {
                    Text(
                        text = stringResource(R.string.backup_created),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    // Selectable so the archive can be copied out: it is the
                    // user's only way off the device.
                    SelectionContainer {
                        Text(
                            text = state.archive,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                is BackupState.Restored -> Text(
                    text = stringResource(R.string.backup_restored, state.rows),
                    style = MaterialTheme.typography.bodyMedium
                )
                is BackupState.Failed -> Text(
                    text = stringResource(R.string.backup_failed, state.reason),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                BackupState.Idle -> Unit
            }

            OutlinedTextField(
                value = archive,
                onValueChange = { archive = it },
                label = { Text(stringResource(R.string.backup_archive)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { onRestore(archive, passphrase) },
                enabled = archive.isNotEmpty() && passphrase.isNotEmpty() && state !is BackupState.Working,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.backup_restore))
            }

            if (state !is BackupState.Idle) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.action_dismiss))
                }
            }
        }
    }
}

/** Qualitative bands stay localized: the coefficient is data, the label is ours. */
private fun TrendAnalytics.Strength.labelRes(): Int = when (this) {
    TrendAnalytics.Strength.STRONG -> R.string.insights_strength_strong
    TrendAnalytics.Strength.MODERATE -> R.string.insights_strength_moderate
    TrendAnalytics.Strength.NONE -> R.string.insights_strength_none
}
