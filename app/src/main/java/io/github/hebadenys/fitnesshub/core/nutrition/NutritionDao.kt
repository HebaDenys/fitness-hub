package io.github.hebadenys.fitnesshub.core.nutrition

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NutritionDao {

    @Query("SELECT * FROM food_items WHERE barcode = :barcode LIMIT 1")
    suspend fun findByBarcode(barcode: String): FoodEntity?

    @Query("SELECT * FROM food_items WHERE id = :id")
    suspend fun findById(id: Long): FoodEntity?

    @Query("SELECT * FROM food_items WHERE name LIKE '%' || :query || '%' ORDER BY name LIMIT :limit")
    suspend fun search(query: String, limit: Int = 50): List<FoodEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(food: FoodEntity): Long

    @Update
    suspend fun update(food: FoodEntity)

    @Query("SELECT * FROM nutrition_entries WHERE date = :date ORDER BY loggedAt")
    fun observeEntries(date: String): Flow<List<NutritionEntryEntity>>

    @Query(
        "SELECT n.id AS id, n.foodId AS foodId, n.date AS date, n.mealType AS mealType, " +
            "n.servings AS servings, n.servingGrams AS servingGrams, n.loggedAt AS loggedAt, " +
            "f.name AS name, f.brand AS brand, f.nutrientBasis AS nutrientBasis, f.energyKcal AS energyKcal, " +
            "f.proteinGrams AS proteinGrams, f.carbsGrams AS carbsGrams, f.fatGrams AS fatGrams, " +
            "f.sugarGrams AS sugarGrams, f.fiberGrams AS fiberGrams, f.saltGrams AS saltGrams " +
            "FROM nutrition_entries n " +
            "INNER JOIN food_items f ON f.id = n.foodId WHERE n.date = :date ORDER BY n.loggedAt"
    )
    fun observeEntriesWithFood(date: String): Flow<List<NutritionEntryWithFood>>

    @Query("SELECT * FROM nutrition_entries WHERE date = :date ORDER BY loggedAt")
    suspend fun entriesOn(date: String): List<NutritionEntryEntity>

    @Insert
    suspend fun insertEntry(entry: NutritionEntryEntity): Long

    @Query("SELECT * FROM nutrition_entries WHERE id = :id")
    suspend fun entryById(id: Long): NutritionEntryEntity?

    @Query("DELETE FROM nutrition_entries WHERE id = :id")
    suspend fun deleteEntry(id: Long)

    @Query("SELECT * FROM nutrition_daily WHERE date = :date")
    fun observeDaily(date: String): Flow<NutritionDailyEntity?>

    @Query("SELECT * FROM nutrition_daily ORDER BY date")
    fun observeAllDaily(): Flow<List<NutritionDailyEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDaily(daily: NutritionDailyEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDaily(daily: List<NutritionDailyEntity>)

    @Query("SELECT * FROM nutrition_daily ORDER BY date")
    suspend fun dumpDaily(): List<NutritionDailyEntity>

    @Query("SELECT * FROM food_items ORDER BY id")
    suspend fun dumpFoods(): List<FoodEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringExisting(food: FoodEntity): Long
}

data class NutritionEntryWithFood(
    val id: Long,
    val foodId: Long,
    val date: String,
    val mealType: String,
    val servings: Double,
    val servingGrams: Double?,
    val loggedAt: Long,
    val name: String,
    val brand: String?,
    val nutrientBasis: String,
    val energyKcal: Double?,
    val proteinGrams: Double?,
    val carbsGrams: Double?,
    val fatGrams: Double?,
    val sugarGrams: Double?,
    val fiberGrams: Double?,
    val saltGrams: Double?
)
