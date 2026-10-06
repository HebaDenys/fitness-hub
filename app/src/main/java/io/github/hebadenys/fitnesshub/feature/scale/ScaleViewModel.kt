package io.github.hebadenys.fitnesshub.feature.scale

import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.hebadenys.fitnesshub.core.scale.BodyCompositionEstimateEntity
import io.github.hebadenys.fitnesshub.core.scale.S400ScaleConnector
import io.github.hebadenys.fitnesshub.core.scale.ScaleBleScanner
import io.github.hebadenys.fitnesshub.core.scale.ScaleDao
import io.github.hebadenys.fitnesshub.core.scale.ScaleMeasurementEntity
import io.github.hebadenys.fitnesshub.core.scale.Sex
import io.github.hebadenys.fitnesshub.ui.state.ScreenState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class ScaleUiModel(
    val isConfigured: Boolean,
    val isScanning: Boolean,
    val bluetoothAvailable: Boolean,
    val lastMeasurement: ScaleMeasurementEntity?,
    val lastEstimate: BodyCompositionEstimateEntity?,
    val heightCm: Double?,
    val ageYears: Int?,
    val sex: Sex?
)

sealed interface BindkeyResult {
    data object Idle : BindkeyResult
    data object Saved : BindkeyResult
    data object Invalid : BindkeyResult
}

@HiltViewModel
class ScaleViewModel @Inject constructor(
    private val connector: S400ScaleConnector,
    private val dao: ScaleDao,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val configured = MutableStateFlow(false)
    private val scanning = MutableStateFlow(false)
    private val bluetoothAvailable = MutableStateFlow(false)
    private val bindkeyResult = MutableStateFlow<BindkeyResult>(BindkeyResult.Idle)

    val bindkeyState: StateFlow<BindkeyResult> = bindkeyResult.asStateFlow()

    private var scanner: ScaleBleScanner? = null

    init {
        viewModelScope.launch {
            configured.value = connector.isConfigured()
            bluetoothAvailable.value = runCatching {
                (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter?.isEnabled
            }.getOrDefault(false) == true
        }
    }

    val uiState: StateFlow<ScreenState<ScaleUiModel>> =
        combine(
            dao.observeMeasurements(limit = 1),
            dao.observeEstimates(limit = 1),
            dao.observeProfile(),
            combine(configured, scanning, bluetoothAvailable) { isConfigured, isScanning, available ->
                Triple(isConfigured, isScanning, available)
            }
        ) { measurements, estimates, profile, flags ->
            ScreenState.Content(
                ScaleUiModel(
                    isConfigured = flags.first,
                    isScanning = flags.second,
                    bluetoothAvailable = flags.third,
                    lastMeasurement = measurements.firstOrNull(),
                    lastEstimate = estimates.firstOrNull(),
                    heightCm = profile?.heightCm,
                    ageYears = profile?.ageYears,
                    sex = profile?.sex?.let { stored -> runCatching { Sex.valueOf(stored) }.getOrNull() }
                )
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScreenState.Loading)

    fun saveBindkey(hex: String) {
        viewModelScope.launch {
            val saved = connector.configureBindkey(hex.trim())
            bindkeyResult.value = if (saved) BindkeyResult.Saved else BindkeyResult.Invalid
            if (saved) configured.value = true
        }
    }

    fun clearBindkey() {
        viewModelScope.launch {
            stopScan()
            connector.clearBindkey()
            configured.value = false
            bindkeyResult.value = BindkeyResult.Idle
        }
    }

    fun dismissBindkeyResult() {
        bindkeyResult.value = BindkeyResult.Idle
    }

    fun saveProfile(heightCm: Double?, ageYears: Int?, sex: Sex?) {
        viewModelScope.launch {
            connector.saveProfile(heightCm, ageYears, sex)
            dao.measurementsSince(0L).forEach { measurement ->
                connector.estimateComposition(Instant.ofEpochMilli(measurement.measuredAtMillis))
            }
        }
    }

    fun toggleScan() {
        if (scanning.value) stopScan() else startScan()
    }

    private fun startScan() {
        if (!configured.value) return
        val instance = scanner ?: ScaleBleScanner(context, viewModelScope) { data, measuredAt ->
            viewModelScope.launch { connector.ingestAdvertisement(data, measuredAt) }
        }.also { created -> scanner = created }
        scanning.value = instance.start()
    }

    fun stopScan() {
        scanner?.stop()
        scanner = null
        scanning.value = false
    }

    override fun onCleared() {
        scanner?.stop()
        super.onCleared()
    }
}
