package io.github.hebadenys.fitnesshub.core.nutrition

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * Nutrition logging: local foods, entries, and derived daily totals.
 *
 * Totals are summed only over nutrients that at least one entry actually
 * provides, so a day with no protein data reports null protein rather than 0.
 */
class NutritionRepository(
    private val dao: NutritionDao,
    private val catalog: FoodCatalogConnector,
    private val parser: NutritionLabelParser = NutritionLabelParser
) {

    fun observeEntries(date: LocalDate): Flow<List<NutritionEntryWithFood>> =
        dao.observeEntriesWithFood(date.toString())

    fun observeDaily(date: LocalDate): Flow<NutritionDailyEntity?> =
        dao.observeDaily(date.toString())

    suspend fun searchFoods(query: String): List<FoodEntity> = dao.search(query)

    /** Returns the locally stored food for a barcode, if the user already confirmed one. */
    suspend fun findLocalByBarcode(barcode: String): FoodEntity? = dao.findByBarcode(barcode)

    suspend fun findFood(id: Long): FoodEntity? = dao.findById(id)

    /**
     * Resolves a scanned barcode without inventing data: local storage first,
     * then the opt-in catalog, then null so the UI can offer manual entry.
     */
    suspend fun resolveBarcode(barcode: String): BarcodeResolution {
        dao.findByBarcode(barcode)?.let { return BarcodeResolution.Local(it) }
        val product = catalog.lookup(barcode)
            ?: return BarcodeResolution.Unknown
        return BarcodeResolution.Remote(
            FoodEntity(
                barcode = barcode,
                name = product.name.orEmpty(),
                brand = product.brand,
                servingSizeGrams = product.servingSizeGrams,
                nutrientBasis = NutritionBasis.PER_100G.storedValue,
                energyKcal = product.energyKcal,
                proteinGrams = product.proteinGrams,
                carbsGrams = product.carbsGrams,
                fatGrams = product.fatGrams,
                sugarGrams = product.sugarGrams,
                fiberGrams = product.fiberGrams,
                saltGrams = product.saltGrams,
                provenance = FoodEntity.PROVENANCE_OPEN_FOOD_FACTS,
                algorithm = catalog.sourceId
            )
        )
    }

    /** Saves a food the user confirmed, from OCR or manual entry. */
    suspend fun saveFood(food: FoodEntity): Long = dao.insert(food)

    suspend fun updateFood(food: FoodEntity) = dao.update(food)

    /** Saves an OCR result without storing it yet, so the user can confirm the fields first. */
    fun parseLabel(rawText: String): NutritionLabelParser.Parsed = parser.parse(rawText)

    fun normalizeLabel(parsed: NutritionLabelParser.Parsed): NutritionLabelParser.Parsed =
        parser.toPer100g(parsed, parsed.servingSizeGrams)

    suspend fun logEntry(
        foodId: Long,
        date: LocalDate,
        mealType: MealType,
        servings: Double
    ): Long {
        val food = dao.findById(foodId) ?: throw IllegalArgumentException("Unknown food id $foodId")
        require(servings.isFinite() && servings > 0.0) { "Servings must be positive" }
        val basis = NutritionBasis.fromStored(food.nutrientBasis)
        require(basis != NutritionBasis.UNKNOWN) { "Nutrition basis must be confirmed" }
        if (basis == NutritionBasis.PER_100G) {
            require(food.servingSizeGrams?.let { it.isFinite() && it > 0.0 } == true) {
                "Serving size is required for per-100g food"
            }
        }
        val id = dao.insertEntry(
            NutritionEntryEntity(
                foodId = foodId,
                date = date.toString(),
                mealType = mealType.storedValue,
                servings = servings,
                servingGrams = food.servingSizeGrams?.times(servings)
            )
        )
        recomputeTotals(date)
        return id
    }

    suspend fun deleteEntry(id: Long) {
        val date = dao.entryById(id)?.date
        dao.deleteEntry(id)
        date?.let { recomputeTotals(LocalDate.parse(it)) }
    }

    /** Recomputes a day's totals from its entries, preserving null for unknown nutrients. */
    suspend fun recomputeTotals(date: LocalDate) {
        val entries = dao.entriesOn(date.toString())
        if (entries.isEmpty()) {
            dao.upsertDaily(
                NutritionDailyEntity(
                    date = date.toString(),
                    energyKcal = null, proteinGrams = null, carbsGrams = null,
                    fatGrams = null, sugarGrams = null, fiberGrams = null,
                    saltGrams = null, entryCount = 0
                )
            )
            return
        }
        val portions = entries.mapNotNull { entry ->
            dao.findById(entry.foodId)?.let { food -> food to entry }
        }
        dao.upsertDaily(
            NutritionDailyEntity(
                date = date.toString(),
                energyKcal = portions.sumKnown { food -> food.energyKcal },
                proteinGrams = portions.sumKnown { food -> food.proteinGrams },
                carbsGrams = portions.sumKnown { food -> food.carbsGrams },
                fatGrams = portions.sumKnown { food -> food.fatGrams },
                sugarGrams = portions.sumKnown { food -> food.sugarGrams },
                fiberGrams = portions.sumKnown { food -> food.fiberGrams },
                saltGrams = portions.sumKnown { food -> food.saltGrams },
                entryCount = entries.size
            )
        )
    }

    data class DailyTotals(
        val date: LocalDate,
        val energyKcal: Double?,
        val proteinGrams: Double?,
        val carbsGrams: Double?,
        val fatGrams: Double?,
        val sugarGrams: Double?
    )

    suspend fun totalsFor(date: LocalDate): DailyTotals? {
        val snapshot = dao.observeDaily(date.toString()).first() ?: return null
        if (snapshot.entryCount == 0) return null
        return DailyTotals(
            date = date,
            energyKcal = snapshot.energyKcal,
            proteinGrams = snapshot.proteinGrams,
            carbsGrams = snapshot.carbsGrams,
            fatGrams = snapshot.fatGrams,
            sugarGrams = snapshot.sugarGrams
        )
    }

    sealed interface BarcodeResolution {
        data class Local(val food: FoodEntity) : BarcodeResolution
        data class Remote(val food: FoodEntity) : BarcodeResolution
        data object Unknown : BarcodeResolution
    }

    companion object {
        /**
         * Sums a nutrient across portions, scaled by servings, and returns null
         * when no portion carries it: absent data must never total to zero.
         */
        internal fun scaledValue(
            value: Double?,
            basisValue: String,
            servings: Double,
            servingGrams: Double?
        ): Double? {
            val nutrient = value?.takeIf { it.isFinite() } ?: return null
            val factor = when (NutritionBasis.fromStored(basisValue)) {
                NutritionBasis.PER_100G -> servingGrams?.takeIf { it.isFinite() && it > 0.0 }?.div(100.0)
                NutritionBasis.PER_SERVING, NutritionBasis.LEGACY -> servings.takeIf { it.isFinite() && it > 0.0 }
                NutritionBasis.UNKNOWN -> null
            } ?: return null
            return nutrient * factor
        }

        internal fun List<Pair<FoodEntity, NutritionEntryEntity>>.sumKnown(
            selector: (FoodEntity) -> Double?
        ): Double? {
            var total = 0.0
            var seen = false
            forEach { (food, entry) ->
                val scaled = scaledValue(
                    selector(food),
                    food.nutrientBasis,
                    entry.servings,
                    entry.servingGrams
                ) ?: return@forEach
                total += scaled
                seen = true
            }
            return if (seen) total else null
        }
    }
}
