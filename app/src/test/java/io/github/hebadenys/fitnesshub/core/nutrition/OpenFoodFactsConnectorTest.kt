package io.github.hebadenys.fitnesshub.core.nutrition

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class OpenFoodFactsConnectorTest {
    private val connector = OpenFoodFactsConnector(enabledProvider = { true })

    @Test fun v2StatusIsReadFromRootAndUsableProductIsParsed() {
        val body = """
            {
              "code":"12345678",
              "status":1,
              "status_verbose":"product found",
              "product":{
                "product_name":"Synthetic oats",
                "brands":"Fixture",
                "serving_size":"40 g",
                "nutriments":{
                  "energy-kcal_100g":380,
                  "proteins_100g":13.5,
                  "carbohydrates_100g":62,
                  "fat_100g":7
                }
              }
            }
        """.trimIndent()

        val product = connector.parseResponse(body, "12345678")

        requireNotNull(product)
        assertEquals("Synthetic oats", product.name)
        assertEquals(40.0, product.servingSizeGrams)
        assertEquals(380.0, product.energyKcal)
        assertEquals(13.5, product.proteinGrams)
    }

    @Test fun v2StatusZeroOrIncompleteProductFallsBackToManual() {
        assertNull(connector.parseResponse("""{"status":0,"product":{}}""", "12345678"))
        assertNull(connector.parseResponse(
            """{"status":1,"product":{"product_name":"No nutrition","nutriments":{}}}""",
            "12345678"
        ))
        assertNull(connector.parseResponse("not-json", "12345678"))
    }

    @Test fun disabledCatalogNeverAttemptsRemoteLookup() = runBlocking {
        val disabled = OpenFoodFactsConnector(enabledProvider = { false })
        assertNull(disabled.lookup("3017624010701"))
    }
}
