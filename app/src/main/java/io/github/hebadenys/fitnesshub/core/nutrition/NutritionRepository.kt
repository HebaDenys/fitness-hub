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
            dao.findById(entry.foodId)?.let { food -> food to entry.servings }
        }
        dao.upsertDaily(
            NutritionDailyEntity(
                date = date.toString(),
                energyKcal = portions.sumKnown { food, _ -> food.energyKcal },
                proteinGrams = portions.sumKnown { food, _ -> food.proteinGrams },
                carbsGrams = portions.sumKnown { food, _ -> food.carbsGrams },
                fatGrams = portions.sumKnown { food, _ -> food.fatGrams },
                sugarGrams = portions.sumKnown { food, _ -> food.sugarGrams },
                fiberGrams = portions.sumKnown { food, _ -> food.fiberGrams },
                saltGrams = portions.sumKnown { food, _ -> food.saltGrams },
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
        internal fun List<Pair<FoodEntity, Double>>.sumKnown(
            selector: (FoodEntity, Double) -> Double?
        ): Double? {
            var total = 0.0
            var seen = false
            forEach { (food, servings) ->
                val value = selector(food, servings) ?: return@forEach
                total += value * servings
                seen = true
            }
            return if (seen) total else null
        }
    }
}
