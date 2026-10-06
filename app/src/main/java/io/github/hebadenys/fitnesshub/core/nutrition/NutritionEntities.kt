package io.github.hebadenys.fitnesshub.core.nutrition

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A food the user can log.
 *
 * Every nutritional field is nullable on purpose: an unlabelled product with no
 * known calories must stay unknown rather than being recorded as 0 kcal.
 * [provenance] records how the values were obtained, so estimated or
 * user-typed figures are never presented as manufacturer data.
 */
@Entity(
    tableName = "food_items",
    indices = [Index(value = ["barcode"], unique = true)]
)
data class FoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val barcode: String? = null,
    val name: String,
    val brand: String? = null,
    val servingSizeGrams: Double? = null,
    val servingLabel: String? = null,
    val energyKcal: Double? = null,
    val proteinGrams: Double? = null,
    val carbsGrams: Double? = null,
    val fatGrams: Double? = null,
    val sugarGrams: Double? = null,
    val fiberGrams: Double? = null,
    val saltGrams: Double? = null,
    @ColumnInfo(defaultValue = "MEASURED") val provenance: String = PROVENANCE_MEASURED,
    val algorithm: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val PROVENANCE_MEASURED = "MEASURED"
        const val PROVENANCE_LABEL_OCR = "LABEL_OCR"
        const val PROVENANCE_OPEN_FOOD_FACTS = "OPEN_FOOD_FACTS"
        const val PROVENANCE_USER_ENTERED = "USER_ENTERED"
    }
}

/** One logged intake of a food, tied to a meal and a day. */
@Entity(
    tableName = "nutrition_entries",
    indices = [Index(value = ["date", "mealType"]), Index(value = ["foodId"])]
)
data class NutritionEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val foodId: Long,
    val date: String,
    val mealType: String,
    val servings: Double,
    val servingGrams: Double?,
    val loggedAt: Long = System.currentTimeMillis()
)

/**
 * Daily nutrition totals derived from the day's entries.
 * A field is null when no logged entry provided that nutrient, so an absent
 * value is never displayed as zero.
 */
@Entity(tableName = "nutrition_daily")
data class NutritionDailyEntity(
    @PrimaryKey val date: String,
    val energyKcal: Double? = null,
    val proteinGrams: Double? = null,
    val carbsGrams: Double? = null,
    val fatGrams: Double? = null,
    val sugarGrams: Double? = null,
    val fiberGrams: Double? = null,
    val saltGrams: Double? = null,
    val entryCount: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)

enum class MealType(val storedValue: String) {
    BREAKFAST("BREAKFAST"),
    LUNCH("LUNCH"),
    DINNER("DINNER"),
    SNACK("SNACK");

    companion object {
        fun fromStored(value: String): MealType =
            entries.firstOrNull { it.storedValue == value } ?: SNACK
    }
}
