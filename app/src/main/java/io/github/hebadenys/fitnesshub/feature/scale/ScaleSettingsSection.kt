package io.github.hebadenys.fitnesshub.feature.scale

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
    viewModel: ScaleViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val bindkeyState by viewModel.bindkeyState.collectAsStateWithLifecycle()
    val spacing = FitnessHubTheme.spacing

    var bindkeyInput by remember { mutableStateOf("") }
    var heightInput by remember { mutableStateOf("") }
    var ageInput by remember { mutableStateOf("") }
    var sex by remember { mutableStateOf<Sex?>(null) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Text(
            text = stringResource(R.string.scale_section),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = stringResource(R.string.scale_section_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

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
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            BindkeyResult.Invalid -> Text(
                text = stringResource(R.string.scale_bindkey_invalid),
                style = MaterialTheme.typography.bodySmall,
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

        Text(
            text = stringResource(R.string.scale_profile_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = stringResource(R.string.scale_profile_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            OutlinedTextField(
                value = heightInput,
                onValueChange = { heightInput = it },
                label = { Text(stringResource(R.string.scale_profile_height)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = ageInput,
                onValueChange = { ageInput = it },
                label = { Text(stringResource(R.string.scale_profile_age)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Sex.entries.forEach { option ->
                FilterChip(
                    selected = sex == option,
                    onClick = { sex = if (sex == option) null else option },
                    label = {
                        Text(
                            stringResource(
                                if (option == Sex.MALE) R.string.sex_male else R.string.sex_female
                            )
                        )
                    }
                )
            }
        }

        TextButton(
            onClick = {
                viewModel.saveProfile(
                    heightInput.replace(',', '.').toDoubleOrNull(),
                    ageInput.toIntOrNull(),
                    sex
                )
            },
            enabled = heightInput.isNotBlank() && ageInput.isNotBlank() && sex != null
        ) { Text(stringResource(R.string.action_save)) }

        Spacer(Modifier.height(spacing.xs))

        (uiState as? ScreenState.Content)?.data?.let { model ->
            if (!model.bluetoothAvailable) {
                Text(
                    text = stringResource(R.string.scale_bluetooth_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Button(
                onClick = viewModel::toggleScan,
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
                    style = MaterialTheme.typography.bodySmall,
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
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
