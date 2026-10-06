package io.github.hebadenys.fitnesshub.feature.nutrition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.hebadenys.fitnesshub.core.nutrition.FoodEntity
import io.github.hebadenys.fitnesshub.core.nutrition.MealType
import io.github.hebadenys.fitnesshub.core.nutrition.NutritionLabelParser
import io.github.hebadenys.fitnesshub.core.nutrition.NutritionRepository
import io.github.hebadenys.fitnesshub.core.nutrition.NutritionPreferences
import io.github.hebadenys.fitnesshub.ui.state.ScreenState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class NutritionUiModel(
    val date: LocalDate,
    val entries: List<LoggedEntry>,
    val energyKcal: Double?,
    val proteinGrams: Double?,
    val carbsGrams: Double?,
    val fatGrams: Double?,
    val sugarGrams: Double?,
    val catalogEnabled: Boolean
)

data class LoggedEntry(
    val id: Long,
    val name: String,
    val brand: String?,
    val mealType: MealType,
    val servings: Double,
    val energyKcal: Double?,
    val provenance: String
)

sealed interface CaptureState {
    data object Idle : CaptureState
    data object ScanningBarcode : CaptureState
    data object CapturingLabel : CaptureState
    data class BarcodeFound(val barcode: String) : CaptureState
    data object BarcodeUnknown : CaptureState
    data class LabelRead(val draft: NutritionDraft) : CaptureState
}

data class NutritionDraft(
    val name: String,
    val brand: String? = null,
    val barcode: String? = null,
    val servingSizeGrams: Double? = null,
    val energyKcal: Double? = null,
    val proteinGrams: Double? = null,
    val carbsGrams: Double? = null,
    val fatGrams: Double? = null,
    val sugarGrams: Double? = null,
    val fiberGrams: Double? = null,
    val saltGrams: Double? = null,
    val provenance: String
) {
    fun toEntity(): FoodEntity = FoodEntity(
        barcode = barcode,
        name = name,
        brand = brand,
        servingSizeGrams = servingSizeGrams,
        energyKcal = energyKcal,
        proteinGrams = proteinGrams,
        carbsGrams = carbsGrams,
        fatGrams = fatGrams,
        sugarGrams = sugarGrams,
        fiberGrams = fiberGrams,
        saltGrams = saltGrams,
        provenance = provenance
    )
}

@HiltViewModel
class NutritionViewModel @Inject constructor(
    private val repository: NutritionRepository,
    private val preferences: NutritionPreferences
) : ViewModel() {

    private val selectedDate = MutableStateFlow(LocalDate.now())
    private val capture = MutableStateFlow<CaptureState>(CaptureState.Idle)
    private val pendingDraft = MutableStateFlow<NutritionDraft?>(null)
    private val catalogEnabled = MutableStateFlow(false)

    val captureState: StateFlow<CaptureState> = capture.asStateFlow()
    val draft: StateFlow<NutritionDraft?> = pendingDraft.asStateFlow()

    val uiState: StateFlow<ScreenState<NutritionUiModel>> =
        combine(
            selectedDate.flatMapLatest { date ->
                combine(repository.observeEntries(date), repository.observeDaily(date)) { entries, totals ->
                    entries to totals
                }
            },
            catalogEnabled
        ) { (entries, totals), catalog ->
            ScreenState.Content(
                NutritionUiModel(
                    date = selectedDate.value,
                    entries = entries.map { entry ->
                        LoggedEntry(
                            id = entry.id,
                            name = entry.name,
                            brand = entry.brand,
                            mealType = MealType.fromStored(entry.mealType),
                            servings = entry.servings,
                            energyKcal = entry.energyKcal?.times(entry.servings),
                            provenance = FoodEntity.PROVENANCE_MEASURED
                        )
                    },
                    energyKcal = totals?.energyKcal,
                    proteinGrams = totals?.proteinGrams,
                    carbsGrams = totals?.carbsGrams,
                    fatGrams = totals?.fatGrams,
                    sugarGrams = totals?.sugarGrams,
                    catalogEnabled = catalog
                )
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScreenState.Loading)

    init {
        viewModelScope.launch { catalogEnabled.value = preferences.isCatalogEnabled() }
    }

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
    }

    fun setCatalogEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setCatalogEnabled(enabled)
            catalogEnabled.value = enabled
        }
    }

    fun startBarcodeScan() {
        capture.value = CaptureState.ScanningBarcode
    }

    fun startLabelCapture() {
        capture.value = CaptureState.CapturingLabel
    }

    fun dismissCapture() {
        capture.value = CaptureState.Idle
    }

    /** Called by the camera analyzer once a barcode frame is decoded. */
    fun onBarcodeScanned(raw: String) {
        if (capture.value !is CaptureState.ScanningBarcode) return
        viewModelScope.launch {
            when (val resolution = repository.resolveBarcode(raw)) {
                is NutritionRepository.BarcodeResolution.Local -> {
                    capture.value = CaptureState.Idle
                    pendingDraft.value = resolution.food.let {
                        NutritionDraft(
                            name = it.name,
                            brand = it.brand,
                            barcode = it.barcode,
                            servingSizeGrams = it.servingSizeGrams,
                            energyKcal = it.energyKcal,
                            proteinGrams = it.proteinGrams,
                            carbsGrams = it.carbsGrams,
                            fatGrams = it.fatGrams,
                            sugarGrams = it.sugarGrams,
                            fiberGrams = it.fiberGrams,
                            saltGrams = it.saltGrams,
                            provenance = it.provenance
                        )
                    }
                }
                is NutritionRepository.BarcodeResolution.Remote -> {
                    val food = resolution.food
                    pendingDraft.value = NutritionDraft(
                        name = food.name,
                        brand = food.brand,
                        barcode = food.barcode,
                        servingSizeGrams = food.servingSizeGrams,
                        energyKcal = food.energyKcal,
                        proteinGrams = food.proteinGrams,
                        carbsGrams = food.carbsGrams,
                        fatGrams = food.fatGrams,
                        sugarGrams = food.sugarGrams,
                        fiberGrams = food.fiberGrams,
                        saltGrams = food.saltGrams,
                        provenance = food.provenance
                    )
                    capture.value = CaptureState.Idle
                }
                NutritionRepository.BarcodeResolution.Unknown -> {
                    capture.value = CaptureState.BarcodeUnknown
                }
            }
        }
    }

    /** Called by the OCR analyzer once label text is recognised. */
    fun onLabelTextRecognized(rawText: String) {
        if (capture.value !is CaptureState.CapturingLabel) return
        val parsed: NutritionLabelParser.Parsed = repository.parseLabel(rawText)
        if (!parsed.isUsable) {
            capture.value = CaptureState.Idle
            return
        }
        val per100g = repository.normalizeLabel(parsed)
        pendingDraft.value = NutritionDraft(
            name = "",
            servingSizeGrams = per100g.servingSizeGrams,
            energyKcal = per100g.energyKcal,
            proteinGrams = per100g.proteinGrams,
            carbsGrams = per100g.carbsGrams,
            fatGrams = per100g.fatGrams,
            sugarGrams = per100g.sugarGrams,
            fiberGrams = per100g.fiberGrams,
            saltGrams = per100g.saltGrams,
            provenance = FoodEntity.PROVENANCE_LABEL_OCR
        )
        capture.value = CaptureState.Idle
    }

    fun updateDraft(transform: (NutritionDraft) -> NutritionDraft) {
        pendingDraft.value = pendingDraft.value?.let(transform)
    }

    fun startManualEntry() {
        pendingDraft.value = NutritionDraft(name = "", provenance = FoodEntity.PROVENANCE_USER_ENTERED)
    }

    fun discardDraft() {
        pendingDraft.value = null
    }

    /** Saves the confirmed food and logs [servings] of it in [mealType] for the selected day. */
    fun confirmDraft(mealType: MealType, servings: Double) {
        val current = pendingDraft.value ?: return
        if (current.name.isBlank()) return
        viewModelScope.launch {
            val existing = current.barcode?.let { repository.findLocalByBarcode(it) }
            val foodId = if (existing != null) {
                repository.updateFood(current.toEntity().copy(id = existing.id))
                existing.id
            } else {
                repository.saveFood(current.toEntity())
            }
            repository.logEntry(foodId, selectedDate.value, mealType, servings)
            pendingDraft.value = null
        }
    }

    fun removeEntry(id: Long) {
        viewModelScope.launch { repository.deleteEntry(id) }
    }
}
