package io.github.hebadenys.fitnesshub.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.hebadenys.fitnesshub.core.database.DailySummaryMapper
import io.github.hebadenys.fitnesshub.core.healthconnect.HealthConnectManager
import io.github.hebadenys.fitnesshub.core.model.DailySummary
import io.github.hebadenys.fitnesshub.core.model.HealthMetrics
import io.github.hebadenys.fitnesshub.core.sync.HealthSyncRepository
import io.github.hebadenys.fitnesshub.ui.components.BaselineDelta
import io.github.hebadenys.fitnesshub.ui.components.ChartPoint
import io.github.hebadenys.fitnesshub.ui.components.TimeRange
import io.github.hebadenys.fitnesshub.ui.state.ScreenState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

data class DashboardUiModel(
    val latest: DailySummary?,
    val previous: DailySummary?,
    val recent: List<DailySummary>,
    val stepsDelta: BaselineDelta,
    val activeCaloriesDelta: BaselineDelta,
    val sleepDelta: BaselineDelta,
    val restingHrDelta: BaselineDelta,
    val weightDelta: BaselineDelta,
    val chartPoints: List<ChartPoint>,
    val selectedRange: TimeRange,
    val isSyncing: Boolean = false,
    val syncMessage: String? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    val health: HealthConnectManager,
    private val repo: HealthSyncRepository
) : ViewModel() {

    private val isSyncing = MutableStateFlow(false)
    private val syncMessage = MutableStateFlow<String?>(null)
    private val selectedRange = MutableStateFlow(TimeRange.SEVEN_DAYS)
    private val grantedMetrics = MutableStateFlow<Set<String>>(emptySet())

    init {
        refreshPermissions()
    }

    fun refreshPermissions() {
        viewModelScope.launch {
            grantedMetrics.value = health.grantedMetrics()
        }
    }

    fun setRange(range: TimeRange) {
        selectedRange.value = range
    }

    val uiState: StateFlow<ScreenState<DashboardUiModel>> = combine(
        repo.observeDaily(),
        grantedMetrics,
        isSyncing,
        syncMessage,
        selectedRange
    ) { entities, granted, syncing, message, range ->
        if (granted.isEmpty() && health.client != null) {
            ScreenState.PermissionMissing(missingMetrics = HealthMetrics.ALL)
        } else if (entities.isEmpty()) {
            ScreenState.Empty()
        } else {
            val summaries = entities.map { DailySummaryMapper.toDomain(it) }
            val latest = summaries.firstOrNull()
            val previous = summaries.getOrNull(1)

            val filteredForChart = summaries.take(range.days).reversed()
            val chartPoints = filteredForChart.map {
                ChartPoint(
                    date = it.date,
                    value = it.steps?.toDouble(),
                    provenance = it.provenance
                )
            }

            ScreenState.Content(
                DashboardUiModel(
                    latest = latest,
                    previous = previous,
                    recent = summaries,
                    stepsDelta = calculateLongDelta(latest?.steps, previous?.steps),
                    activeCaloriesDelta = calculateDoubleDelta(latest?.activeCalories, previous?.activeCalories, "kcal"),
                    sleepDelta = calculateLongDelta(latest?.sleepMinutes, previous?.sleepMinutes, "min"),
                    restingHrDelta = calculateLongDelta(latest?.restingHeartRate, previous?.restingHeartRate, "bpm"),
                    weightDelta = calculateDoubleDelta(latest?.weightKg, previous?.weightKg, "kg"),
                    chartPoints = chartPoints,
                    selectedRange = range,
                    isSyncing = syncing,
                    syncMessage = message
                )
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ScreenState.Loading)

    fun sync() {
        if (isSyncing.value) return
        viewModelScope.launch {
            isSyncing.value = true
            syncMessage.value = null
            val result = repo.sync()
            result.fold(
                onSuccess = {
                    syncMessage.value = "SUCCESS"
                },
                onFailure = {
                    syncMessage.value = it.message ?: "ERROR"
                }
            )
            isSyncing.value = false
        }
    }

    private fun calculateLongDelta(current: Long?, previous: Long?, unit: String? = null): BaselineDelta {
        if (current == null || previous == null) return BaselineDelta.None
        val diff = current - previous
        val formatted = if (unit != null) "$diff $unit" else "$diff"
        return when {
            diff > 0 -> BaselineDelta.Positive(formatted)
            diff < 0 -> BaselineDelta.Negative(formatted)
            else -> BaselineDelta.Unchanged
        }
    }

    private fun calculateDoubleDelta(current: Double?, previous: Double?, unit: String): BaselineDelta {
        if (current == null || previous == null) return BaselineDelta.None
        val diff = current - previous
        val formatted = String.format(Locale.US, "%.1f %s", diff, unit)
        return when {
            diff > 0.05 -> BaselineDelta.Positive(formatted)
            diff < -0.05 -> BaselineDelta.Negative(formatted)
            else -> BaselineDelta.Unchanged
        }
    }
}
