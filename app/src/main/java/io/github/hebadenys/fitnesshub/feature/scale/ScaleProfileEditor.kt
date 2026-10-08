package io.github.hebadenys.fitnesshub.feature.scale

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.hebadenys.fitnesshub.R
import io.github.hebadenys.fitnesshub.core.scale.Sex

/** Shared editor for the existing Room profile. No account or additional profile is created. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ScaleProfileEditor(
    model: ScaleUiModel,
    result: ProfileSaveResult,
    onSave: (Double?, Int?, Sex?) -> Unit,
    onEdit: () -> Unit = {},
    onDirtyChange: (Boolean) -> Unit = {}
) {
    var height by rememberSaveable { mutableStateOf(model.heightCm?.toString().orEmpty()) }
    var age by rememberSaveable { mutableStateOf(model.ageYears?.toString().orEmpty()) }
    var sexName by rememberSaveable { mutableStateOf(model.sex?.name) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    val parsedHeight = height.trim().replace(',', '.').toDoubleOrNull()
    val parsedAge = age.trim().toIntOrNull()
    val sex = sexName?.let { runCatching { Sex.valueOf(it) }.getOrNull() }
    val heightValid = parsedHeight != null && parsedHeight.isFinite() && parsedHeight > 100.0 && parsedHeight <= 300.0
    val ageValid = parsedAge != null && parsedAge in 10..120
    val saving = result == ProfileSaveResult.SAVING
    val dirty = parsedHeight != model.heightCm || parsedAge != model.ageYears || sex != model.sex ||
        (height.isNotBlank() && parsedHeight == null) || (age.isNotBlank() && parsedAge == null)

    LaunchedEffect(dirty) { onDirtyChange(dirty) }

    Card(
        modifier = Modifier.fillMaxWidth().testTag("local_profile_editor"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.scale_profile_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() }
            )
            Text(stringResource(R.string.profile_local_description), style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(R.string.profile_estimates_notice),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = height,
                onValueChange = { height = it.take(16); onEdit() },
                label = { Text(stringResource(R.string.scale_profile_height)) },
                supportingText = { Text(stringResource(R.string.profile_height_hint), style = MaterialTheme.typography.bodyMedium) },
                isError = attempted && !heightValid,
                enabled = !saving,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().testTag("profile_height")
            )
            OutlinedTextField(
                value = age,
                onValueChange = { age = it.take(4); onEdit() },
                label = { Text(stringResource(R.string.scale_profile_age)) },
                supportingText = { Text(stringResource(R.string.profile_age_hint), style = MaterialTheme.typography.bodyMedium) },
                isError = attempted && !ageValid,
                enabled = !saving,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().testTag("profile_age")
            )
            Text(stringResource(R.string.profile_sex_label), style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Sex.entries.forEach { option ->
                    FilterChip(
                        selected = sex == option,
                        enabled = !saving,
                        onClick = { sexName = option.name; onEdit() },
                        label = {
                            Text(stringResource(if (option == Sex.MALE) R.string.sex_male else R.string.sex_female))
                        },
                        modifier = Modifier.testTag("profile_sex_${option.name}")
                    )
                }
            }
            if (attempted && (!heightValid || !ageValid || sex == null) || result == ProfileSaveResult.INVALID) {
                Text(
                    stringResource(R.string.profile_validation_error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("profile_validation").semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
            Button(
                onClick = {
                    attempted = true
                    if (heightValid && ageValid && sex != null) onSave(parsedHeight, parsedAge, sex)
                },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth().testTag("profile_save")
            ) {
                Text(stringResource(if (saving) R.string.profile_saving else R.string.action_save))
            }
            val message = when (result) {
                ProfileSaveResult.SAVED -> R.string.profile_saved
                ProfileSaveResult.SAVED_ESTIMATES_PENDING -> R.string.profile_saved_estimates_pending
                ProfileSaveResult.FAILED -> R.string.profile_save_failed
                else -> null
            }
            message?.let {
                Text(
                    stringResource(it),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (result == ProfileSaveResult.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("profile_save_status").semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
        }
    }
}
