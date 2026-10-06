package io.github.hebadenys.fitnesshub.core.nutrition

/**
 * Optional, opt-in barcode lookup.
 *
 * Implemented by the Open Food Facts connector; the rest of the app only knows
 * this interface, so nutrition logging works fully offline and the network
 * dependency stays isolated and disableable.
 */
interface FoodCatalogConnector {

    /**
     * Looks up a product by barcode. Returns null when the catalog is disabled,
     * unreachable, or has no product — never a partially fabricated result.
     */
    suspend fun lookup(barcode: String): CatalogProduct?

    /** Source identifier stored as provenance on foods retrieved from here. */
    val sourceId: String
}

data class CatalogProduct(
    val barcode: String,
    val name: String?,
    val brand: String?,
    val servingSizeGrams: Double?,
    val energyKcal: Double?,
    val proteinGrams: Double?,
    val carbsGrams: Double?,
    val fatGrams: Double?,
    val sugarGrams: Double?,
    val fiberGrams: Double?,
    val saltGrams: Double?
) {
    /** True when the catalog supplied at least one usable nutrient. */
    val isUsable: Boolean get() = !name.isNullOrBlank() && energyKcal != null
}
