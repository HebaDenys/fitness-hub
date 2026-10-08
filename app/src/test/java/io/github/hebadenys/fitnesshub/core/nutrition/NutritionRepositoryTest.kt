package io.github.hebadenys.fitnesshub.core.nutrition

import android.app.Application
import io.github.hebadenys.fitnesshub.core.database.HealthDatabase
import io.github.hebadenys.fitnesshub.core.database.testDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class NutritionRepositoryTest {
    private lateinit var db: HealthDatabase
    private lateinit var catalog: FakeCatalog
    private lateinit var repository: NutritionRepository

    private class FakeCatalog(var result: CatalogProduct? = null) : FoodCatalogConnector {
        var calls = 0
        override val sourceId = "synthetic_catalog"
        override suspend fun lookup(barcode: String): CatalogProduct? {
            calls++
            return result
        }
    }

    @Before fun setup() {
        db = testDatabase()
        catalog = FakeCatalog()
        repository = NutritionRepository(db.nutritionDao(), catalog)
    }

    @After fun close() { db.close() }

    @Test fun per100gFoodUsesServingGramsNotServingCount() = runBlocking {
        val foodId = repository.saveFood(FoodEntity(
            name = "Synthetic yogurt",
            servingSizeGrams = 50.0,
            nutrientBasis = NutritionBasis.PER_100G.storedValue,
            energyKcal = 100.0,
            proteinGrams = 10.0,
            provenance = FoodEntity.PROVENANCE_USER_ENTERED
        ))
        val date = LocalDate.of(2026, 10, 8)

        repository.logEntry(foodId, date, MealType.BREAKFAST, servings = 2.0)

        val total = repository.totalsFor(date)!!
        assertEquals(100.0, total.energyKcal!!, 0.001)
        assertEquals(10.0, total.proteinGrams!!, 0.001)
        assertEquals(100.0, db.nutritionDao().entriesOn(date.toString()).single().servingGrams!!, 0.001)
    }

    @Test fun perServingAndLegacyFoodsKeepTheirDeclaredSemantics() = runBlocking {
        val date = LocalDate.of(2026, 10, 8)
        val perServing = repository.saveFood(FoodEntity(
            name = "Synthetic bar",
            nutrientBasis = NutritionBasis.PER_SERVING.storedValue,
            energyKcal = 250.0,
            provenance = FoodEntity.PROVENANCE_USER_ENTERED
        ))
        val legacy = repository.saveFood(FoodEntity(
            name = "Legacy fixture",
            nutrientBasis = NutritionBasis.LEGACY.storedValue,
            energyKcal = 100.0
        ))

        repository.logEntry(perServing, date, MealType.SNACK, 2.0)
        repository.logEntry(legacy, date, MealType.LUNCH, 1.5)

        assertEquals(650.0, repository.totalsFor(date)!!.energyKcal!!, 0.001)
    }

    @Test fun ambiguousOrUnquantifiedPer100gFoodCannotCreateWrongTotals() = runBlocking {
        val date = LocalDate.of(2026, 10, 8)
        val unknown = repository.saveFood(FoodEntity(
            name = "Unknown basis",
            nutrientBasis = NutritionBasis.UNKNOWN.storedValue,
            energyKcal = 100.0
        ))
        val noServingSize = repository.saveFood(FoodEntity(
            name = "Per 100g without amount",
            nutrientBasis = NutritionBasis.PER_100G.storedValue,
            energyKcal = 100.0
        ))

        assertTrue(runCatching {
            repository.logEntry(unknown, date, MealType.SNACK, 1.0)
        }.isFailure)
        assertTrue(runCatching {
            repository.logEntry(noServingSize, date, MealType.SNACK, 1.0)
        }.isFailure)
        assertNull(repository.totalsFor(date))
    }

    @Test fun deletingLastEntryClearsDerivedTotalsWithoutInventingZero() = runBlocking {
        val date = LocalDate.of(2026, 10, 8)
        val foodId = repository.saveFood(FoodEntity(
            name = "Synthetic meal",
            nutrientBasis = NutritionBasis.PER_SERVING.storedValue,
            energyKcal = 500.0
        ))
        val entry = repository.logEntry(foodId, date, MealType.DINNER, 1.0)

        repository.deleteEntry(entry)

        assertNull(repository.totalsFor(date))
        assertEquals(0, db.nutritionDao().observeDaily(date.toString()).first()!!.entryCount)
    }

    @Test fun localBarcodeWinsAndRemoteProductsAreMarkedPer100g() = runBlocking {
        repository.saveFood(FoodEntity(
            barcode = "12345678",
            name = "Local fixture",
            nutrientBasis = NutritionBasis.PER_SERVING.storedValue,
            energyKcal = 42.0
        ))
        val local = repository.resolveBarcode("12345678")
        assertTrue(local is NutritionRepository.BarcodeResolution.Local)
        assertEquals(0, catalog.calls)

        catalog.result = CatalogProduct(
            barcode = "87654321",
            name = "Remote fixture",
            brand = null,
            servingSizeGrams = 30.0,
            energyKcal = 300.0,
            proteinGrams = 12.0,
            carbsGrams = null,
            fatGrams = null,
            sugarGrams = null,
            fiberGrams = null,
            saltGrams = null
        )
        val remote = repository.resolveBarcode("87654321") as NutritionRepository.BarcodeResolution.Remote
        assertEquals(NutritionBasis.PER_100G.storedValue, remote.food.nutrientBasis)
        assertEquals(1, catalog.calls)
    }
}
