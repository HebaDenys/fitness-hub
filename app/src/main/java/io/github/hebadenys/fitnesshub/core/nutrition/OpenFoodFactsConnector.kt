package io.github.hebadenys.fitnesshub.core.nutrition

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Open Food Facts product lookup, opt-in and cached locally.
 *
 * This is the only network call in the nutrition feature and it is guarded by
 * [isEnabled]; with the feature switched off the app is entirely offline. Any
 * failure returns null so the caller falls back to manual entry instead of
 * inventing values.
 */
class OpenFoodFactsConnector(
    private val enabledProvider: suspend () -> Boolean,
    private val userAgent: String = "FitnessHub/0.2.0 (local-first Android app)"
) : FoodCatalogConnector {

    override val sourceId: String = FoodEntity.PROVENANCE_OPEN_FOOD_FACTS

    override suspend fun lookup(barcode: String): CatalogProduct? {
        if (!enabledProvider()) return null
        val digits = barcode.filter { it.isDigit() }
        if (digits.length < 8) return null
        return withContext(Dispatchers.IO) { request(digits) }
    }

    private fun request(barcode: String): CatalogProduct? = try {
        val url = URL("$BASE_URL/api/v2/product/$barcode?fields=$FIELDS")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                null
            } else {
                connection.inputStream.bufferedReader().use { body ->
                    val product = JSONObject(body.readText()).optJSONObject("product")
                    if (product == null || product.optInt("status", 0) == 0) null
                    else product.toCatalogProduct(barcode)
                }
            }
        } finally {
            connection.disconnect()
        }
    } catch (error: Exception) {
        null
    }

    private fun JSONObject.toCatalogProduct(barcode: String): CatalogProduct {
        val nutriments = optJSONObject("nutriments") ?: JSONObject()
        return CatalogProduct(
            barcode = barcode,
            name = optStringOrNull("product_name") ?: optStringOrNull("product_name_it"),
            brand = optStringOrNull("brands"),
            servingSizeGrams = optStringOrNull("serving_size")?.toServingGrams(),
            energyKcal = nutriments.numberOrNull("energy-kcal_100g"),
            proteinGrams = nutriments.numberOrNull("proteins_100g"),
            carbsGrams = nutriments.numberOrNull("carbohydrates_100g"),
            fatGrams = nutriments.numberOrNull("fat_100g"),
            sugarGrams = nutriments.numberOrNull("sugars_100g"),
            fiberGrams = nutriments.numberOrNull("fiber_100g"),
            saltGrams = nutriments.numberOrNull("salt_100g")
        )
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).trim().takeIf { it.isNotEmpty() && it.lowercase() != "unknown" }
    }

    private fun JSONObject.numberOrNull(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        return when (val value = opt(key)) {
            is Number -> value.toDouble()
            is String -> value.replace(",", ".").toDoubleOrNull()
            else -> null
        }
    }

    private fun String.toServingGrams(): Double? {
        val match = Regex("""([\d.,]+)\s*(g|gr|ml)""", RegexOption.IGNORE_CASE).find(this) ?: return null
        return match.groupValues[1].replace(",", ".").toDoubleOrNull()
    }

    private companion object {
        const val BASE_URL = "https://world.openfoodfacts.org"
        const val FIELDS = "code,product_name,product_name_it,brands,serving_size," +
            "nutriments,serving_quantity"
    }
}
