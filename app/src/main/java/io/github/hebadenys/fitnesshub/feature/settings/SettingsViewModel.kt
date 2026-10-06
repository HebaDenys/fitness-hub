package io.github.hebadenys.fitnesshub.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.hebadenys.fitnesshub.core.healthconnect.HealthConnectManager
import io.github.hebadenys.fitnesshub.core.model.HealthMetrics
import io.github.hebadenys.fitnesshub.core.sync.HealthSyncRepository
import io.github.hebadenys.fitnesshub.ui.state.ScreenState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MetricPermissionStatus(
    val metricKey: String,
    val isGranted: Boolean
)

sealed interface SyncOutcome {
    data object Success : SyncOutcome
    data class Failure(val reason: String) : SyncOutcome
}

data class SettingsUiModel(
    val isClientAvailable: Boolean,
    val hasAnyPermission: Boolean,
    val historyGranted: Boolean,
    val metricStatuses: List<MetricPermissionStatus>,
    val isSyncing: Boolean = false,
    val syncOutcome: SyncOutcome? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    val health: HealthConnectManager,
    private val repo: HealthSyncRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ScreenState<SettingsUiModel>>(ScreenState.Loading)
    val uiState: StateFlow<ScreenState<SettingsUiModel>> = _uiState.asStateFlow()

    private val isSyncing = MutableStateFlow(false)
    private val syncOutcome = MutableStateFlow<SyncOutcome?>(null)

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val isAvailable = health.client != null
            if (!isAvailable) {
                _uiState.value = ScreenState.Content(
                    SettingsUiModel(
                        isClientAvailable = false,
                        hasAnyPermission = false,
                        historyGranted = false,
                        metricStatuses = HealthMetrics.ALL.map {
                            MetricPermissionStatus(it, false)
                        },
                        isSyncing = isSyncing.value,
                        syncOutcome = syncOutcome.value
                    )
                )
                return@launch
            }

            val granted = health.grantedMetrics()
            val history = health.historyAccessGranted()

            val metricList = HealthMetrics.ALL.map { metric ->
                MetricPermissionStatus(
                    metricKey = metric,
                    isGranted = metric in granted
                )
            }

            _uiState.value = ScreenState.Content(
                SettingsUiModel(
                    isClientAvailable = true,
                    hasAnyPermission = granted.isNotEmpty(),
                    historyGranted = history,
                    metricStatuses = metricList,
                    isSyncing = isSyncing.value,
                    syncOutcome = syncOutcome.value
                )
            )
        }
    }

    fun sync() {
        if (isSyncing.value) return
        viewModelScope.launch {
            isSyncing.value = true
            syncOutcome.value = null
            val currentContent = (_uiState.value as? ScreenState.Content)?.data
            if (currentContent != null) {
                _uiState.value = ScreenState.Content(currentContent.copy(isSyncing = true))
            }

            val outcome = repo.sync().fold(
                onSuccess = { SyncOutcome.Success },
                onFailure = { SyncOutcome.Failure(it.message ?: it::class.java.simpleName) }
            )

            isSyncing.value = false
            syncOutcome.value = outcome
            refresh()
        }
    }
}
