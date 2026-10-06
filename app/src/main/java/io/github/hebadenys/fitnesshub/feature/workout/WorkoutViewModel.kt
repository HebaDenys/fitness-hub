package io.github.hebadenys.fitnesshub.feature.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.hebadenys.fitnesshub.core.workout.Equipment
import io.github.hebadenys.fitnesshub.core.workout.MuscleGroup
import io.github.hebadenys.fitnesshub.core.workout.OneRepMaxCalculator
import io.github.hebadenys.fitnesshub.core.workout.PersonalRecordDetector
import io.github.hebadenys.fitnesshub.core.workout.SessionWithSets
import io.github.hebadenys.fitnesshub.core.workout.WorkoutExerciseEntity
import io.github.hebadenys.fitnesshub.core.workout.WorkoutRepository
import io.github.hebadenys.fitnesshub.core.workout.WorkoutSessionEntity
import io.github.hebadenys.fitnesshub.ui.state.ScreenState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WorkoutUiModel(
    val exercises: List<WorkoutExerciseEntity>,
    val activeSessionId: Long?,
    val activeSets: List<SessionWithSets>,
    val recentSessions: List<WorkoutSessionEntity>,
    val records: List<PersonalRecordDetector.Record>,
    val oneRepMaxByExercise: Map<Long, OneRepMaxCalculator.Estimate>,
    val restSecondsRemaining: Int
)

@HiltViewModel
class WorkoutViewModel @Inject constructor(
    private val repository: WorkoutRepository
) : ViewModel() {

    private val activeSessionId = MutableStateFlow<Long?>(null)
    private val restRemaining = MutableStateFlow(0)
    private val records = MutableStateFlow<List<PersonalRecordDetector.Record>>(emptyList())
    private val oneRepMax = MutableStateFlow<Map<Long, OneRepMaxCalculator.Estimate>>(emptyMap())
    private var restJob: Job? = null

    val restSeconds: StateFlow<Int> = restRemaining.asStateFlow()

    init {
        viewModelScope.launch {
            repository.seedBuiltInExercises()
            refreshDerived()
        }
    }

    private val sessionSets = activeSessionId.flatMapLatest { id ->
        if (id == null) kotlinx.coroutines.flow.flowOf(emptyList())
        else repository.observeSessionSets(id)
    }

    val uiState: StateFlow<ScreenState<WorkoutUiModel>> =
        combine(
            repository.observeExercises(),
            sessionSets,
            repository.observeSessions(limit = 20),
            combine(records, oneRepMax, restRemaining, activeSessionId) { r, o, rest, session ->
                DerivedState(r, o, rest, session)
            }
        ) { exercises, sets, sessions, derived ->
            ScreenState.Content(
                WorkoutUiModel(
                    exercises = exercises,
                    activeSessionId = derived.sessionId,
                    activeSets = sets,
                    recentSessions = sessions,
                    records = derived.records,
                    oneRepMaxByExercise = derived.oneRepMax,
                    restSecondsRemaining = derived.restRemaining
                )
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScreenState.Loading)

    fun startSession() {
        if (activeSessionId.value != null) return
        viewModelScope.launch {
            activeSessionId.value = repository.startSession()
        }
    }

    fun endSession() {
        val sessionId = activeSessionId.value ?: return
        stopRestTimer()
        viewModelScope.launch {
            repository.endSession(sessionId)
            activeSessionId.value = null
            refreshDerived()
        }
    }

    fun logSet(
        exerciseId: Long,
        reps: Int?,
        loadKg: Double?,
        rpe: Double?,
        repsInReserve: Int?,
        isWarmUp: Boolean
    ) {
        val sessionId = activeSessionId.value ?: return
        viewModelScope.launch {
            repository.logSet(sessionId, exerciseId, reps, loadKg, rpe, repsInReserve, isWarmUp)
            refreshDerived()
            startRestTimer(DEFAULT_REST_SECONDS)
        }
    }

    fun deleteSet(setId: Long) {
        viewModelScope.launch {
            repository.deleteSet(setId)
            refreshDerived()
        }
    }

    fun createExercise(name: String, muscleGroup: MuscleGroup, equipment: Equipment) {
        viewModelScope.launch { repository.createExercise(name, muscleGroup, equipment) }
    }

    fun archiveExercise(id: Long) {
        viewModelScope.launch { repository.archiveExercise(id) }
    }

    fun startRestTimer(seconds: Int) {
        restJob?.cancel()
        restRemaining.value = seconds
        restJob = viewModelScope.launch {
            var remaining = seconds
            while (remaining > 0) {
                delay(1_000)
                remaining -= 1
                restRemaining.value = remaining
            }
        }
    }

    fun dismissRestTimer() {
        stopRestTimer()
    }

    private fun stopRestTimer() {
        restJob?.cancel()
        restJob = null
        restRemaining.value = 0
    }

    private suspend fun refreshDerived() {
        records.value = repository.currentRecords()
        oneRepMax.value = repository.allEstimatedOneRepMax()
    }

    private data class DerivedState(
        val records: List<PersonalRecordDetector.Record>,
        val oneRepMax: Map<Long, OneRepMaxCalculator.Estimate>,
        val restRemaining: Int,
        val sessionId: Long?
    )

    companion object {
        const val DEFAULT_REST_SECONDS = 90
    }
}
