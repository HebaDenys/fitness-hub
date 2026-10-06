package io.github.hebadenys.fitnesshub.feature.body

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.hebadenys.fitnesshub.core.database.DailySummaryMapper
import io.github.hebadenys.fitnesshub.core.healthconnect.HealthConnectManager
import io.github.hebadenys.fitnesshub.core.model.DailySummary
import io.github.hebadenys.fitnesshub.core.model.HealthMetrics
import io.github.hebadenys.fitnesshub.core.scale.ScaleDao
import io.github.hebadenys.fitnesshub.core.scale.ScaleMeasurementEntity
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
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject

data class BodyUiModel(
    val latest: DailySummary?,
    val previous: DailySummary?,
    val history: List<DailySummary>,
    val weightDelta: BaselineDelta,
    val bodyFatDelta: BaselineDelta,
    val chartPoints: List<ChartPoint>,
    val selectedRange: TimeRange
)

@HiltViewModel
class BodyViewModel @Inject constructor(
    val health: HealthConnectManager,
    private val repo: HealthSyncRepository,
    private val scaleDao: ScaleDao
) : ViewModel() {

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

    val uiState: StateFlow<ScreenState<BodyUiModel>> = combine(
        repo.observeDaily(),
        scaleDao.observeMeasurements(limit = 500),
        scaleDao.observeEstimates(limit = 500),
        grantedMetrics,
        selectedRange
    ) { entities, scaleMeasurements, scaleEstimates, granted, range ->
        val required = setOf(HealthMetrics.WEIGHT, HealthMetrics.BODY_FAT)
        val hasLocalScaleData = scaleMeasurements.isNotEmpty() || scaleEstimates.isNotEmpty()

        if (granted.none { it in required } && health.client != null && !hasLocalScaleData) {
            ScreenState.PermissionMissing(missingMetrics = required)
        } else if (entities.isEmpty() && !hasLocalScaleData) {
            ScreenState.Empty()
        } else {
            val zone = ZoneId.systemDefault()
            val healthByDate = entities
                .map(DailySummaryMapper::toDomain)
                .associateBy { it.date }

            val scaleByDate = scaleMeasurements
                .groupBy { Instant.ofEpochMilli(it.measuredAtMillis).atZone(zone).toLocalDate() }
                .mapValues { (_, rows) -> rows.maxBy { it.measuredAtMillis } }

            val estimateByDate = scaleEstimates
                .groupBy { Instant.ofEpochMilli(it.measuredAtMillis).atZone(zone).toLocalDate() }
                .mapValues { (_, rows) -> rows.maxBy { it.measuredAtMillis } }

            val dates = (healthByDate.keys + scaleByDate.keys + estimateByDate.keys)
                .distinct()
                .sortedDescending()

            val summaries = dates.map { date ->
                val base = healthByDate[date] ?: DailySummary(date = date)
                val scale = scaleByDate[date]
                val estimate = estimateByDate[date]
                val imported = scale?.provenance == ScaleMeasurementEntity.PROVENANCE_IMPORTED ||
                    estimate?.provenance == ScaleMeasurementEntity.PROVENANCE_IMPORTED

                val bodyFat = when {
                    estimate?.provenance == ScaleMeasurementEntity.PROVENANCE_IMPORTED ->
                        estimate.bodyFatPercent ?: base.bodyFatPercent
                    base.bodyFatPercent != null -> base.bodyFatPercent
                    else -> estimate?.bodyFatPercent
                }

                val provenance = when {
                    imported -> DailySummary.PROVENANCE_IMPORTED
                    bodyFat != null && base.bodyFatPercent == null && estimate != null ->
                        DailySummary.PROVENANCE_ESTIMATE
                    else -> base.provenance
                }

                base.copy(
                    weightKg = scale?.weightKg ?: base.weightKg,
                    bodyFatPercent = bodyFat,
                    dataOrigins = base.dataOrigins + listOfNotNull(scale?.deviceAddress),
                    provenance = provenance,
                    algorithm = when {
                        imported -> estimate?.algorithm ?: scale?.algorithm
                        provenance == DailySummary.PROVENANCE_ESTIMATE -> estimate?.algorithm
                        else -> base.algorithm
                    }
                )
            }

            val latest = summaries.firstOrNull()
            val previous = summaries.getOrNull(1)
            val filtered = summaries.take(range.days).reversed()
            val chartPoints = filtered.map {
                ChartPoint(
                    date = it.date,
                    value = it.weightKg,
                    provenance = it.provenance
                )
            }

            ScreenState.Content(
                BodyUiModel(
                    latest = latest,
                    previous = previous,
                    history = summaries,
                    weightDelta = calculateDoubleDelta(latest?.weightKg, previous?.weightKg, "kg"),
                    bodyFatDelta = calculateDoubleDelta(latest?.bodyFatPercent, previous?.bodyFatPercent, "%"),
                    chartPoints = chartPoints,
                    selectedRange = range
                )
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ScreenState.Loading)

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
