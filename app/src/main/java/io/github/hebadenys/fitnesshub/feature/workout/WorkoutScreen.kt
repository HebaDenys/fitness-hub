package io.github.hebadenys.fitnesshub.feature.workout

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hebadenys.fitnesshub.R
import io.github.hebadenys.fitnesshub.core.workout.MuscleGroup
import io.github.hebadenys.fitnesshub.core.workout.PersonalRecordDetector
import io.github.hebadenys.fitnesshub.ui.state.ScreenStateHandler
import io.github.hebadenys.fitnesshub.ui.theme.FitnessHubTheme

@Composable
fun WorkoutScreen(viewModel: WorkoutViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val restSeconds by viewModel.restSeconds.collectAsStateWithLifecycle()
    val spacing = FitnessHubTheme.spacing

    var selectedExerciseId by remember { mutableStateOf<Long?>(null) }
    var repsInput by remember { mutableStateOf("") }
    var loadInput by remember { mutableStateOf("") }
    var rpeInput by remember { mutableStateOf("") }
    var rirInput by remember { mutableStateOf("") }
    var isWarmUp by remember { mutableStateOf(false) }
    var showCustomExercise by remember { mutableStateOf(false) }

    ScreenStateHandler(state = uiState, modifier = Modifier.fillMaxSize()) { model ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(spacing.md)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(spacing.md)
        ) {
            Text(
                text = stringResource(R.string.workout_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() }
            )

            if (restSeconds > 0) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(spacing.md),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacing.sm)
                        ) {
                            Icon(Icons.Default.Timer, contentDescription = null)
                            Text(
                                text = stringResource(R.string.workout_rest_remaining, restSeconds),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        TextButton(onClick = viewModel::dismissRestTimer) {
                            Text(stringResource(R.string.action_skip))
                        }
                    }
                }
            }

            if (model.activeSessionId == null) {
                Button(
                    onClick = viewModel::startSession,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.workout_start_session)) }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm)
                ) {
                    Button(onClick = viewModel::endSession, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.workout_end_session))
                    }
                    OutlinedButton(
                        onClick = { viewModel.startRestTimer(WorkoutViewModel.DEFAULT_REST_SECONDS) },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.workout_rest_timer)) }
                }
            }

            if (model.activeSessionId != null) {
                Text(
                    text = stringResource(R.string.workout_log_set),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() }
                )

                LazyColumn(
                    modifier = Modifier.height((model.exercises.size * 52).dp.coerceAtMost(300.dp)),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs)
                ) {
                    items(model.exercises, key = { it.id }) { exercise ->
                        FilterChip(
                            selected = selectedExerciseId == exercise.id,
                            onClick = { selectedExerciseId = exercise.id },
                            label = { Text(exercise.name) }
                        )
                    }
                }

                OutlinedTextField(
                    value = repsInput,
                    onValueChange = { repsInput = it },
                    label = { Text(stringResource(R.string.workout_reps)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = loadInput,
                    onValueChange = { loadInput = it },
                    label = { Text(stringResource(R.string.workout_load_kg)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = rpeInput,
                    onValueChange = { rpeInput = it },
                    label = { Text(stringResource(R.string.workout_rpe)) },
                    supportingText = { Text(stringResource(R.string.workout_rpe_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = rirInput,
                    onValueChange = { rirInput = it },
                    label = { Text(stringResource(R.string.workout_rir)) },
                    supportingText = { Text(stringResource(R.string.workout_rir_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    FilterChip(
                        selected = isWarmUp,
                        onClick = { isWarmUp = !isWarmUp },
                        label = { Text(stringResource(R.string.workout_warm_up)) }
                    )
                    Button(
                        onClick = {
                            val exerciseId = selectedExerciseId ?: return@Button
                            viewModel.logSet(
                                exerciseId = exerciseId,
                                reps = repsInput.toIntOrNull(),
                                loadKg = loadInput.replace(',', '.').toDoubleOrNull(),
                                rpe = rpeInput.replace(',', '.').toDoubleOrNull(),
                                repsInReserve = rirInput.toIntOrNull(),
                                isWarmUp = isWarmUp
                            )
                            repsInput = ""
                            loadInput = ""
                        },
                        enabled = selectedExerciseId != null,
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.action_save)) }
                }
            }

            if (model.activeSets.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.workout_session_sets),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() }
                )
                model.activeSets.forEach { set ->
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
                                Text(set.exerciseName, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = stringResource(
                                        R.string.workout_set_summary,
                                        set.setNumber,
                                        set.reps?.toString() ?: stringResource(R.string.value_unavailable),
                                        set.loadKg?.toString() ?: stringResource(R.string.value_unavailable)
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { viewModel.deleteSet(set.setId) },
                                modifier = Modifier.semantics { contentDescription = "" }
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.workout_delete_set)
                                )
                            }
                        }
                    }
                }
            }

            if (model.records.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.workout_records),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() }
                )
                model.records.take(MAX_VISIBLE_RECORDS).forEach { record ->
                    val name = model.exercises.firstOrNull { it.id == record.exerciseId }?.name
                        ?: stringResource(R.string.workout_unknown_exercise)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(spacing.md)) {
                            Text(name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = stringResource(
                                    recordKindLabel(record.kind),
                                    record.loadKg.toString(),
                                    record.reps.toString()
                                ),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (record.formula != null) {
                                Text(
                                    text = stringResource(
                                        R.string.workout_record_estimated,
                                        record.formula
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            TextButton(onClick = { showCustomExercise = !showCustomExercise }) {
                Text(stringResource(R.string.workout_add_exercise))
            }
            if (showCustomExercise) {
                CustomExerciseForm(onCreate = { name, group ->
                    viewModel.createExercise(name, group, io.github.hebadenys.fitnesshub.core.workout.Equipment.OTHER)
                    showCustomExercise = false
                })
            }

            if (model.oneRepMaxByExercise.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.workout_estimated_one_rm),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() }
                )
                model.oneRepMaxByExercise.entries.take(MAX_VISIBLE_RECORDS).forEach { (exerciseId, estimate) ->
                    val name = model.exercises.firstOrNull { it.id == exerciseId }?.name
                        ?: stringResource(R.string.workout_unknown_exercise)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = stringResource(R.string.unit_kg, estimate.displayKg.toString()),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomExerciseForm(onCreate: (String, MuscleGroup) -> Unit) {
    var name by remember { mutableStateOf("") }
    var group by remember { mutableStateOf(MuscleGroup.FULL_BODY) }
    val spacing = FitnessHubTheme.spacing

    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.workout_exercise_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
            MuscleGroup.entries.take(MUSCLE_GROUP_CHIPS).forEach { option ->
                FilterChip(
                    selected = group == option,
                    onClick = { group = option },
                    label = { Text(muscleGroupLabel(option)) }
                )
            }
        }
        Button(onClick = { onCreate(name, group) }, enabled = name.isNotBlank()) {
            Text(stringResource(R.string.action_save))
        }
    }
}

@Composable
private fun muscleGroupLabel(group: MuscleGroup): String = stringResource(
    when (group) {
        MuscleGroup.CHEST -> R.string.muscle_chest
        MuscleGroup.BACK -> R.string.muscle_back
        MuscleGroup.LEGS -> R.string.muscle_legs
        MuscleGroup.SHOULDERS -> R.string.muscle_shoulders
        MuscleGroup.ARMS -> R.string.muscle_arms
        MuscleGroup.CORE -> R.string.muscle_core
        MuscleGroup.FULL_BODY -> R.string.muscle_full_body
        MuscleGroup.CARDIO -> R.string.muscle_cardio
    }
)

@Composable
private fun recordKindLabel(kind: PersonalRecordDetector.RecordKind): Int = when (kind) {
    PersonalRecordDetector.RecordKind.ONE_REP_MAX -> R.string.workout_record_one_rm
    PersonalRecordDetector.RecordKind.ESTIMATED_THREE_REP_MAX -> R.string.workout_record_three_rm
    PersonalRecordDetector.RecordKind.SET_VOLUME -> R.string.workout_record_volume
}

private const val MAX_VISIBLE_RECORDS = 8
private const val MUSCLE_GROUP_CHIPS = 5
