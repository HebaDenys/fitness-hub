package io.github.hebadenys.fitnesshub.feature.scale

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.hebadenys.fitnesshub.ui.state.ScreenState
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hebadenys.fitnesshub.R
import io.github.hebadenys.fitnesshub.core.scale.Sex
import io.github.hebadenys.fitnesshub.ui.theme.FitnessHubTheme

/**
 * Scale configuration: bindkey, the physical profile the estimates depend on,
 * and the scan toggle.
 *
 * The bindkey is entered as hex and stored encrypted in the Android Keystore.
 * The app never asks for, and has no way to use, a Xiaomi account.
 */
@Composable
fun ScaleSettingsSection(
    onOpenProfile: () -> Unit,
    viewModel: ScaleViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val bindkeyState by viewModel.bindkeyState.collectAsStateWithLifecycle()
    val historyImportState by viewModel.historyImportState.collectAsStateWithLifecycle()
    val spacing = FitnessHubTheme.spacing
    val context = LocalContext.current

    val scanPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) {
            viewModel.refreshBluetoothState()
            viewModel.toggleScan()
        }
    }

    var bindkeyInput by remember { mutableStateOf("") }
    var historyUserFilter by remember { mutableStateOf("") }

    val historyFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Unable to open history file")
            }.onSuccess { csv ->
                viewModel.importHistoryCsv(csv, historyUserFilter.takeIf { it.isNotBlank() })
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Text(
            text = stringResource(R.string.scale_section),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = stringResource(R.string.scale_section_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier.padding(spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.sm)
            ) {
                Text(
                    text = stringResource(R.string.scale_history_title),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = stringResource(R.string.scale_history_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = historyUserFilter,
                    onValueChange = { historyUserFilter = it },
                    label = { Text(stringResource(R.string.scale_history_user_filter)) },
                    supportingText = { Text(stringResource(R.string.scale_history_user_filter_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        historyFileLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*"))
                    },
                    enabled = historyImportState !is ScaleHistoryImportState.Working,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(
                            if (historyImportState is ScaleHistoryImportState.Working) {
                                R.string.scale_history_importing
                            } else {
                                R.string.scale_history_import
                            }
                        )
                    )
                }
                when (val result = historyImportState) {
                    is ScaleHistoryImportState.Success -> Text(
                        text = stringResource(
                            R.string.scale_history_success,
                            result.imported,
                            result.duplicates,
                            result.skipped
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    is ScaleHistoryImportState.MultipleUsers -> Text(
                        text = stringResource(
                            R.string.scale_history_multiple_users,
                            result.users.joinToString(", ")
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    is ScaleHistoryImportState.Failure -> Text(
                        text = stringResource(R.string.scale_history_failed, result.reason),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    ScaleHistoryImportState.Idle,
                    ScaleHistoryImportState.Working -> Unit
                }
            }
        }

        OutlinedTextField(
            value = bindkeyInput,
            onValueChange = { bindkeyInput = it },
            label = { Text(stringResource(R.string.scale_bindkey_label)) },
            supportingText = { Text(stringResource(R.string.scale_bindkey_supporting)) },
            singleLine = true,
            enabled = uiState is ScreenState.Content &&
                !(uiState as ScreenState.Content).data.isConfigured,
            modifier = Modifier.fillMaxWidth()
        )

        when (bindkeyState) {
            BindkeyResult.Saved -> Text(
                text = stringResource(R.string.scale_bindkey_saved),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            BindkeyResult.Invalid -> Text(
                text = stringResource(R.string.scale_bindkey_invalid),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            BindkeyResult.Idle -> Unit
        }

        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Button(
                onClick = { viewModel.saveBindkey(bindkeyInput) },
                enabled = bindkeyInput.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.action_save)) }
            OutlinedButton(
                onClick = viewModel::clearBindkey,
                enabled = (uiState as? ScreenState.Content)
                    ?.data?.isConfigured == true,
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.scale_bindkey_remove)) }
        }

        Spacer(Modifier.height(spacing.xs))

        OutlinedButton(
            onClick = onOpenProfile,
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.scale_profile_title)) }


        Spacer(Modifier.height(spacing.xs))

        (uiState as? ScreenState.Content)?.data?.let { model ->
            if (!model.bluetoothAvailable) {
                Text(
                    text = stringResource(R.string.scale_bluetooth_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Button(
                onClick = {
                    if (viewModel.hasScanPermission()) {
                        viewModel.toggleScan()
                    } else {
                        scanPermissionLauncher.launch(viewModel.requiredScanPermissions())
                    }
                },
                enabled = model.isConfigured && model.bluetoothAvailable,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(
                        if (model.isScanning) R.string.scale_stop_scanning else R.string.scale_start_scanning
                    )
                )
            }
            if (model.isScanning) {
                Text(
                    text = stringResource(R.string.scale_scanning_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            model.lastMeasurement?.let { measurement ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(spacing.md),
                        verticalArrangement = Arrangement.spacedBy(spacing.xxs)
                    ) {
                        Text(
                            text = stringResource(R.string.scale_last_measurement),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = stringResource(
                                R.string.scale_weight_reading,
                                measurement.weightKg?.toString()
                                    ?: stringResource(R.string.value_unavailable)
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(
                                R.string.scale_impedance_reading,
                                measurement.impedanceOhms?.toString()
                                    ?: stringResource(R.string.value_unavailable)
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        model.lastEstimate?.let { estimate ->
                            estimate.bodyFatPercent?.let {
                                Text(
                                    text = stringResource(R.string.scale_body_fat_reading, it.toString()),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            estimate.bodyWaterPercent?.let {
                                Text(
                                    text = stringResource(R.string.scale_body_water_reading, it.toString()),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            estimate.visceralFatIndex?.let {
                                Text(
                                    text = stringResource(R.string.scale_visceral_fat_reading, it.toString()),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = stringResource(
                                    if (estimate.provenance == io.github.hebadenys.fitnesshub.core.scale.ScaleMeasurementEntity.PROVENANCE_IMPORTED) {
                                        R.string.scale_provenance_imported
                                    } else {
                                        R.string.provenance_estimate
                                    }
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            }
        }
    }
}
