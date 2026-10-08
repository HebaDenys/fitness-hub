package io.github.hebadenys.fitnesshub.feature.body

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.hebadenys.fitnesshub.core.body.CanonicalBodyDay
import io.github.hebadenys.fitnesshub.core.body.CanonicalBodyMetricResolver
import io.github.hebadenys.fitnesshub.core.body.CanonicalBodyRepository
import io.github.hebadenys.fitnesshub.core.body.ManualBodyInputError
import io.github.hebadenys.fitnesshub.core.body.ManualBodyInputResult
import io.github.hebadenys.fitnesshub.core.body.parseManualBodyInput
import io.github.hebadenys.fitnesshub.core.database.HealthDao
import io.github.hebadenys.fitnesshub.core.database.ManualBodyMeasurementEntity
import io.github.hebadenys.fitnesshub.core.healthconnect.HealthConnectManager
import io.github.hebadenys.fitnesshub.core.model.DailySummary
import io.github.hebadenys.fitnesshub.core.model.HealthMetrics
import io.github.hebadenys.fitnesshub.ui.components.BaselineDelta
import io.github.hebadenys.fitnesshub.ui.components.ChartPoint
import io.github.hebadenys.fitnesshub.ui.components.TimeRange
import io.github.hebadenys.fitnesshub.ui.state.ScreenState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject

data class BodyUiModel(
    val latestWeight: CanonicalBodyMetricResolver.Observation?,
    val latestBodyFat: CanonicalBodyMetricResolver.Observation?,
    val history: List<CanonicalBodyDay>,
    val weightDelta: BaselineDelta,
    val bodyFatDelta: BaselineDelta,
    val chartPoints: List<ChartPoint>,
    val selectedRange: TimeRange,
    val healthConnectAvailable: Boolean,
    val hasHealthConnectBodyPermission: Boolean,
    val weightObservations: List<CanonicalBodyMetricResolver.Observation> = emptyList(),
    val observations: List<CanonicalBodyMetricResolver.Observation> = emptyList()
)

sealed interface ManualBodyEntryState {
    data object Idle : ManualBodyEntryState
    data object Working : ManualBodyEntryState
    data class Saved(val id: Long) : ManualBodyEntryState
    data class Invalid(val error: ManualBodyInputError) : ManualBodyEntryState
    data object StorageError : ManualBodyEntryState
}

@HiltViewModel
class BodyViewModel @Inject constructor(
    val health: HealthConnectManager,
    private val body: CanonicalBodyRepository,
    private val healthDao: HealthDao
) : ViewModel() {

    private val selectedRange = MutableStateFlow(TimeRange.SEVEN_DAYS)
    private val grantedMetrics = MutableStateFlow<Set<String>>(emptySet())
    private val manualEntry = MutableStateFlow<ManualBodyEntryState>(ManualBodyEntryState.Idle)

    val manualEntryState: StateFlow<ManualBodyEntryState> = manualEntry.asStateFlow()

    init { refreshPermissions() }

    fun refreshPermissions() {
        viewModelScope.launch { grantedMetrics.value = health.grantedMetrics() }
    }

    fun setRange(range: TimeRange) { selectedRange.value = range }

    val uiState: StateFlow<ScreenState<BodyUiModel>> = combine(
        body.observe(),
        grantedMetrics,
        selectedRange
    ) { data, granted, range ->
        val weights = data.weightTimeline
        val fats = data.bodyFatTimeline
        val latestWeight = data.latestWeight?.observation
        val latestFat = data.latestBodyFat?.observation
        val previousWeight = weights.asReversed().firstOrNull { it != latestWeight }
        val previousFat = fats.asReversed().firstOrNull { it != latestFat }
        val cutoff = LocalDate.now(ZoneId.systemDefault()).minusDays(range.days.toLong() - 1)
        val chartPoints = data.days
            .filter { !it.date.isBefore(cutoff) && !it.date.isAfter(LocalDate.now(ZoneId.systemDefault())) }
            .sortedBy { it.date }
            .map { day ->
                ChartPoint(
                    date = day.date,
                    value = day.weight?.value,
                    provenance = day.weight?.toProvenance(),
                    sourceLabel = day.weight?.sourceLabel()
                )
            }

        ScreenState.Content(
            BodyUiModel(
                latestWeight = latestWeight,
                latestBodyFat = latestFat,
                history = data.days,
                weightDelta = delta(latestWeight?.value, previousWeight?.value, "kg"),
                bodyFatDelta = delta(latestFat?.value, previousFat?.value, "%"),
                chartPoints = chartPoints,
                selectedRange = range,
                healthConnectAvailable = health.client != null,
                hasHealthConnectBodyPermission =
                    HealthMetrics.WEIGHT in granted || HealthMetrics.BODY_FAT in granted,
                weightObservations = bodyObservationWindow(weights, range),
                observations = (weights + fats).sortedByDescending { it.measuredAt }
            )
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ScreenState.Loading)

    fun saveManualMeasurement(weightRaw: String, bodyFatRaw: String, timestampRaw: String) {
        if (manualEntry.value is ManualBodyEntryState.Working) return
        when (val parsed = parseManualBodyInput(weightRaw, bodyFatRaw, timestampRaw)) {
            is ManualBodyInputResult.Invalid -> {
                manualEntry.value = ManualBodyEntryState.Invalid(parsed.error)
            }
            is ManualBodyInputResult.Valid -> {
                manualEntry.value = ManualBodyEntryState.Working
                viewModelScope.launch {
                    val input = parsed.input
                    try {
                        val id = healthDao.insertManualBodyMeasurement(
                            ManualBodyMeasurementEntity(
                                measuredAtMillis = input.measuredAtMillis,
                                weightKg = input.weightKg,
                                bodyFatPercent = input.bodyFatPercent
                            )
                        )
                        manualEntry.value = ManualBodyEntryState.Saved(id)
                    } catch (cancelled: CancellationException) {
                        manualEntry.value = ManualBodyEntryState.Idle
                        throw cancelled
                    } catch (_: Exception) {
                        manualEntry.value = ManualBodyEntryState.StorageError
                    }
                }
            }
        }
    }

    fun dismissManualEntryState() {
        if (manualEntry.value !is ManualBodyEntryState.Working) {
            manualEntry.value = ManualBodyEntryState.Idle
        }
    }

    private fun delta(current: Double?, previous: Double?, unit: String): BaselineDelta {
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

internal fun CanonicalBodyMetricResolver.Observation.toProvenance(): String =
    if (method == CanonicalBodyMetricResolver.Method.LOCAL_ESTIMATE ||
        method == CanonicalBodyMetricResolver.Method.VENDOR_ESTIMATE
    ) DailySummary.PROVENANCE_ESTIMATE else DailySummary.PROVENANCE_MEASURED

internal fun CanonicalBodyMetricResolver.Observation.sourceLabel(): String = when (source) {
    CanonicalBodyMetricResolver.Source.XIAOMI -> "Xiaomi"
    CanonicalBodyMetricResolver.Source.HEALTH_CONNECT -> "Health Connect"
    CanonicalBodyMetricResolver.Source.SCALE -> "Scale"
    CanonicalBodyMetricResolver.Source.MANUAL -> "Manual"
}
