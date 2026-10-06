package io.github.hebadenys.fitnesshub.feature.nutrition

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hebadenys.fitnesshub.R
import io.github.hebadenys.fitnesshub.core.nutrition.FoodEntity
import io.github.hebadenys.fitnesshub.core.nutrition.MealType
import io.github.hebadenys.fitnesshub.ui.components.MetricCard
import io.github.hebadenys.fitnesshub.ui.state.ScreenStateHandler
import io.github.hebadenys.fitnesshub.ui.theme.FitnessHubTheme

@Composable
fun NutritionScreen(viewModel: NutritionViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val captureState by viewModel.captureState.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val spacing = FitnessHubTheme.spacing

    ScreenStateHandler(state = uiState, modifier = Modifier.fillMaxSize()) { model ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(spacing.md)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            Text(
                text = stringResource(R.string.nutrition_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() }
            )

            NutritionQuickActions(
                onScanBarcode = viewModel::startBarcodeScan,
                onCaptureLabel = viewModel::startLabelCapture,
                onManualEntry = viewModel::startManualEntry
            )

            if (captureState !is CaptureState.Idle) {
                NutritionCaptureOverlay(
                    state = captureState,
                    onBarcode = viewModel::onBarcodeScanned,
                    onLabelText = viewModel::onLabelTextRecognized,
                    onDismiss = viewModel::dismissCapture
                )
            }

            if (captureState is CaptureState.BarcodeUnknown) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = stringResource(R.string.nutrition_barcode_unknown),
                        modifier = Modifier.padding(spacing.md),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Text(
                text = stringResource(R.string.nutrition_daily_totals),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                MetricCard(
                    label = stringResource(R.string.metric_energy),
                    value = model.energyKcal?.let { stringResource(R.string.unit_kcal, it) },
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                MetricCard(
                    label = stringResource(R.string.metric_protein),
                    value = model.proteinGrams?.let { stringResource(R.string.unit_grams, it) },
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    label = stringResource(R.string.metric_carbs),
                    value = model.carbsGrams?.let { stringResource(R.string.unit_grams, it) },
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                MetricCard(
                    label = stringResource(R.string.metric_fat),
                    value = model.fatGrams?.let { stringResource(R.string.unit_grams, it) },
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    label = stringResource(R.string.metric_sugar),
                    value = model.sugarGrams?.let { stringResource(R.string.unit_grams, it) },
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                text = stringResource(R.string.nutrition_logged_items),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() }
            )

            if (model.entries.isEmpty()) {
                Text(
                    text = stringResource(R.string.nutrition_empty_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.height((model.entries.size * 88).dp.coerceAtMost(400.dp)),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm)
                ) {
                    items(model.entries, key = { it.id }) { entry ->
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
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(entry.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = stringResource(
                                            R.string.nutrition_entry_summary,
                                            mealTypeLabel(entry.mealType),
                                            entry.servings.toString(),
                                            entry.energyKcal?.let { stringResource(R.string.unit_kcal, it) }
                                                ?: stringResource(R.string.value_unavailable)
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.removeEntry(entry.id) },
                                    modifier = Modifier.semantics {
                                        contentDescription = ""
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.action_remove_entry)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.nutrition_catalog_title), style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = stringResource(R.string.nutrition_catalog_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = model.catalogEnabled,
                    onCheckedChange = viewModel::setCatalogEnabled
                )
            }
        }
    }

    draft?.let { current ->
        NutritionDraftDialog(
            draft = current,
            onChange = viewModel::updateDraft,
            onConfirm = { mealType, servings -> viewModel.confirmDraft(mealType, servings) },
            onDismiss = viewModel::discardDraft
        )
    }
}

@Composable
private fun mealTypeLabel(mealType: MealType): String = stringResource(
    when (mealType) {
        MealType.BREAKFAST -> R.string.meal_breakfast
        MealType.LUNCH -> R.string.meal_lunch
        MealType.DINNER -> R.string.meal_dinner
        MealType.SNACK -> R.string.meal_snack
    }
)

@Composable
private fun NutritionDraftDialog(
    draft: NutritionDraft,
    onChange: ((NutritionDraft) -> NutritionDraft) -> Unit,
    onConfirm: (MealType, Double) -> Unit,
    onDismiss: () -> Unit
) {
    var mealType by remember { mutableStateOf(MealType.SNACK) }
    var servings by remember { mutableStateOf("1") }
    val spacing = FitnessHubTheme.spacing

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.nutrition_confirm_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(spacing.sm)
            ) {
                if (draft.provenance != FoodEntity.PROVENANCE_USER_ENTERED) {
                    Text(
                        text = stringResource(R.string.nutrition_confirm_provenance, provenanceLabel(draft.provenance)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { value -> onChange { it.copy(name = value) } },
                    label = { Text(stringResource(R.string.nutrition_field_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                NumericField(
                    label = stringResource(R.string.metric_energy),
                    value = draft.energyKcal,
                    suffix = "kcal",
                    onChange = { value -> onChange { it.copy(energyKcal = value) } }
                )
                NumericField(
                    label = stringResource(R.string.metric_protein),
                    value = draft.proteinGrams,
                    suffix = "g",
                    onChange = { value -> onChange { it.copy(proteinGrams = value) } }
                )
                NumericField(
                    label = stringResource(R.string.metric_carbs),
                    value = draft.carbsGrams,
                    suffix = "g",
                    onChange = { value -> onChange { it.copy(carbsGrams = value) } }
                )
                NumericField(
                    label = stringResource(R.string.metric_fat),
                    value = draft.fatGrams,
                    suffix = "g",
                    onChange = { value -> onChange { it.copy(fatGrams = value) } }
                )
                Text(
                    text = stringResource(R.string.nutrition_choose_meal),
                    style = MaterialTheme.typography.labelLarge
                )
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    MealType.entries.forEach { type ->
                        FilterChip(
                            selected = mealType == type,
                            onClick = { mealType = type },
                            label = { Text(mealTypeLabel(type)) }
                        )
                    }
                }
                OutlinedTextField(
                    value = servings,
                    onValueChange = { servings = it },
                    label = { Text(stringResource(R.string.nutrition_field_servings)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val amount = servings.replace(',', '.').toDoubleOrNull() ?: 1.0
                    onConfirm(mealType, if (amount > 0.0) amount else 1.0)
                },
                enabled = draft.name.isNotBlank()
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun NumericField(
    label: String,
    value: Double?,
    suffix: String,
    onChange: (Double?) -> Unit
) {
    OutlinedTextField(
        value = value?.toString() ?: "",
        onValueChange = { text -> onChange(text.replace(',', '.').toDoubleOrNull()) },
        label = { Text("$label ($suffix)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun provenanceLabel(provenance: String): String = stringResource(
    when (provenance) {
        FoodEntity.PROVENANCE_LABEL_OCR -> R.string.provenance_label_ocr
        FoodEntity.PROVENANCE_OPEN_FOOD_FACTS -> R.string.provenance_open_food_facts
        FoodEntity.PROVENANCE_USER_ENTERED -> R.string.provenance_user_entered
        else -> R.string.provenance_measured
    }
)
