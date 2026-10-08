package io.github.hebadenys.fitnesshub.feature.scale

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
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
import io.github.hebadenys.fitnesshub.core.scale.ScaleHistoryCsvImporter
import io.github.hebadenys.fitnesshub.core.scale.ScaleHistoryImportResult
import io.github.hebadenys.fitnesshub.core.scale.Sex
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

sealed interface ScaleHistoryImportState {
    data object Idle : ScaleHistoryImportState
    data object Working : ScaleHistoryImportState
    data class Success(val imported: Int, val duplicates: Int, val skipped: Int) : ScaleHistoryImportState
    data class MultipleUsers(val users: List<String>) : ScaleHistoryImportState
    data class Failure(val reason: String) : ScaleHistoryImportState
}

enum class ProfileSaveResult { IDLE, SAVING, SAVED, SAVED_ESTIMATES_PENDING, INVALID, FAILED }

internal fun validScaleProfile(heightCm: Double?, ageYears: Int?, sex: Sex?): Boolean =
    heightCm != null && heightCm.isFinite() && heightCm > 100.0 && heightCm <= 300.0 &&
        ageYears != null && ageYears in 10..120 && sex != null

sealed interface BindkeyResult {
    data object Idle : BindkeyResult
    data object Saved : BindkeyResult
    data object Invalid : BindkeyResult
}

@HiltViewModel
class ScaleViewModel @Inject constructor(
    private val connector: S400ScaleConnector,
    private val dao: ScaleDao,
    private val historyImporter: ScaleHistoryCsvImporter,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val configured = MutableStateFlow(false)
    private val scanning = MutableStateFlow(false)
    private val bluetoothAvailable = MutableStateFlow(false)
    private val bindkeyResult = MutableStateFlow<BindkeyResult>(BindkeyResult.Idle)
    private val profileResult = MutableStateFlow(ProfileSaveResult.IDLE)
    val profileSaveResult: StateFlow<ProfileSaveResult> = profileResult.asStateFlow()

    private val historyImportResult = MutableStateFlow<ScaleHistoryImportState>(ScaleHistoryImportState.Idle)

    val bindkeyState: StateFlow<BindkeyResult> = bindkeyResult.asStateFlow()
    val historyImportState: StateFlow<ScaleHistoryImportState> = historyImportResult.asStateFlow()

    private var scanner: ScaleBleScanner? = null

    init {
        viewModelScope.launch {
            configured.value = connector.isConfigured()
            bluetoothAvailable.value = probeBluetoothAvailability()
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

    fun dismissProfileResult() {
        if (profileResult.value != ProfileSaveResult.SAVING) profileResult.value = ProfileSaveResult.IDLE
    }

    fun saveProfile(heightCm: Double?, ageYears: Int?, sex: Sex?) {
        if (profileResult.value == ProfileSaveResult.SAVING) return
        if (!validScaleProfile(heightCm, ageYears, sex)) {
            profileResult.value = ProfileSaveResult.INVALID
            return
        }
        profileResult.value = ProfileSaveResult.SAVING
        viewModelScope.launch {
            var stored = false
            try {
                connector.saveProfile(heightCm, ageYears, sex)
                stored = true
                dao.measurementsSince(0L).forEach { measurement ->
                    connector.estimateComposition(Instant.ofEpochMilli(measurement.measuredAtMillis))
                }
                profileResult.value = ProfileSaveResult.SAVED
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                profileResult.value = if (stored) ProfileSaveResult.SAVED_ESTIMATES_PENDING else ProfileSaveResult.FAILED
            }
        }
    }

    fun requiredScanPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun hasScanPermission(): Boolean =
        requiredScanPermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }

    fun refreshBluetoothState() {
        bluetoothAvailable.value = probeBluetoothAvailability()
    }

    private fun probeBluetoothAvailability(): Boolean =
        ScaleBleScanner(context, viewModelScope) { _, _ -> }.isBluetoothAvailable()

    fun importHistoryCsv(csv: String, userFilter: String?) {
        if (historyImportResult.value is ScaleHistoryImportState.Working) return
        viewModelScope.launch {
            historyImportResult.value = ScaleHistoryImportState.Working
            historyImportResult.value = when (val result = historyImporter.import(csv, userFilter)) {
                is ScaleHistoryImportResult.Success -> ScaleHistoryImportState.Success(
                    imported = result.importedRows,
                    duplicates = result.duplicateRows,
                    skipped = result.skippedRows
                )
                is ScaleHistoryImportResult.MultipleUsers -> ScaleHistoryImportState.MultipleUsers(result.users)
                is ScaleHistoryImportResult.Failure -> ScaleHistoryImportState.Failure(result.reason)
            }
        }
    }

    fun dismissHistoryImportResult() {
        historyImportResult.value = ScaleHistoryImportState.Idle
    }

    fun toggleScan() {
        refreshBluetoothState()
        if (!hasScanPermission()) return
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
