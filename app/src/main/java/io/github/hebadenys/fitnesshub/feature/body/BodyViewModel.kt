package io.github.hebadenys.fitnesshub.feature.body

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.hebadenys.fitnesshub.core.body.CanonicalBodyDay
import io.github.hebadenys.fitnesshub.core.body.CanonicalBodyMetricResolver
import io.github.hebadenys.fitnesshub.core.body.CanonicalBodyRepository
import io.github.hebadenys.fitnesshub.core.healthconnect.HealthConnectManager
import io.github.hebadenys.fitnesshub.core.model.DailySummary
import io.github.hebadenys.fitnesshub.core.model.HealthMetrics
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
    val selectedRange: TimeRange
)

@HiltViewModel
class BodyViewModel @Inject constructor(
    val health: HealthConnectManager,
    private val body: CanonicalBodyRepository
) : ViewModel() {

    private val selectedRange = MutableStateFlow(TimeRange.SEVEN_DAYS)
    private val grantedMetrics = MutableStateFlow<Set<String>>(emptySet())

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
        val required = setOf(HealthMetrics.WEIGHT, HealthMetrics.BODY_FAT)
        val hasBodyData = data.latestWeight != null || data.latestBodyFat != null

        if (!hasBodyData && granted.none { it in required } && health.client != null) {
            ScreenState.PermissionMissing(missingMetrics = required)
        } else if (!hasBodyData) {
            ScreenState.Empty()
        } else {
            val weights = data.weightTimeline
            val fats = data.bodyFatTimeline
            val latestWeight = data.latestWeight?.observation
            val latestFat = data.latestBodyFat?.observation
            val previousWeight = weights.asReversed().firstOrNull { it != latestWeight }
            val previousFat = fats.asReversed().firstOrNull { it != latestFat }
            val cutoff = LocalDate.now(ZoneId.systemDefault()).minusDays(range.days.toLong() - 1)
            val chartPoints = data.days
                .filter { !it.date.isBefore(cutoff) }
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
                    selectedRange = range
                )
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ScreenState.Loading)

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
