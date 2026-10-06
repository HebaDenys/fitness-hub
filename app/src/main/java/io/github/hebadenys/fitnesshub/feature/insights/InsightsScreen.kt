package io.github.hebadenys.fitnesshub.feature.insights

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
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
                weightCsv = content.weightCsv,
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
    weightCsv: String,
    onCreate: (String) -> Unit,
    onRestore: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var passphrase by remember { mutableStateOf("") }
    var archive by remember { mutableStateOf("") }
    var pendingBackup by remember { mutableStateOf<String?>(null) }
    var fileStatus by remember { mutableStateOf<String?>(null) }

    val saveBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val payload = pendingBackup
        if (uri != null && payload != null) {
            fileStatus = runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                    writer.write(payload)
                } ?: error("Unable to open destination")
                context.getString(R.string.backup_file_saved)
            }.getOrElse { context.getString(R.string.backup_file_failed) }
        }
        pendingBackup = null
    }

    val openBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            fileStatus = runCatching {
                archive = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                    reader.readText()
                } ?: error("Unable to open backup")
                context.getString(R.string.backup_file_loaded)
            }.getOrElse { context.getString(R.string.backup_file_failed) }
        }
    }

    val saveCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            fileStatus = runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                    writer.write(weightCsv)
                } ?: error("Unable to open destination")
                context.getString(R.string.export_csv_saved)
            }.getOrElse { context.getString(R.string.backup_file_failed) }
        }
    }

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
                    Button(
                        onClick = {
                            pendingBackup = state.archive
                            saveBackupLauncher.launch("FitnessHub-backup.fhub")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.backup_save_file))
                    }
                    SelectionContainer {
                        Text(
                            text = state.archive.take(160) + if (state.archive.length > 160) "…" else "",
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

            Button(
                onClick = { openBackupLauncher.launch(arrayOf("application/octet-stream", "text/plain", "*/*")) },
                enabled = state !is BackupState.Working,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.backup_open_file))
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

            Text(
                text = stringResource(R.string.export_csv_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = { saveCsvLauncher.launch("FitnessHub-weight.csv") },
                enabled = weightCsv.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.export_weight_csv))
            }

            fileStatus?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
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
