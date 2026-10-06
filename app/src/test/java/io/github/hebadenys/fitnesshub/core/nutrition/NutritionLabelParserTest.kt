package io.github.hebadenys.fitnesshub.core.nutrition

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * The label parser is the only place raw OCR text turns into numbers, so it
 * must stay conservative: a field it cannot read has to come back null rather
 * than a plausible-looking default.
 */
class NutritionLabelParserTest {

    private val parser = NutritionLabelParser

    private val italianLabel = """
        Valori nutrizionali medi per 100 g
        Energia 524 kcal
        Proteine 9,2 g
        Carboidrati 60,5 g
        di cui zuccheri 12,3 g
        Grassi 22,1 g
        Fibre 4,1 g
        Sale 0,7 g
    """.trimIndent()

    @Test
    @DisplayName("reads every nutrient from a standard per-100 g table")
    fun parsesStandardTable() {
        val parsed = parser.parse(italianLabel)

        assertEquals(524.0, parsed.energyKcal)
        assertEquals(9.2, parsed.proteinGrams)
        assertEquals(60.5, parsed.carbsGrams)
        assertEquals(12.3, parsed.sugarGrams)
        assertEquals(22.1, parsed.fatGrams)
        assertEquals(4.1, parsed.fiberGrams)
        assertEquals(0.7, parsed.saltGrams)
        assertEquals(NutritionLabelParser.Basis.PER_100G, parsed.basis)
        assertEquals(7, parsed.matchedFieldCount)
    }

    @Test
    @DisplayName("decimal commas are accepted, since labels use them")
    fun acceptsDecimalComma() {
        val parsed = parser.parse("Protein 12,75 g")

        assertEquals(12.75, parsed.proteinGrams)
    }

    @Test
    @DisplayName("nutrients absent from the label stay null instead of becoming zero")
    fun absentNutrients_stayNull() {
        val parsed = parser.parse("Energy 100 kcal Protein 3 g")

        assertEquals(100.0, parsed.energyKcal)
        assertEquals(3.0, parsed.proteinGrams)
        assertNull(parsed.saltGrams)
        assertNull(parsed.fiberGrams)
        assertFalse(parsed.isUsable && parsed.saltGrams == 0.0)
    }

    @Test
    @DisplayName("unreadable text yields no fields and is reported as unusable")
    fun garbageText_isUnusable() {
        val parsed = parser.parse("BUON APPETITO")

        assertEquals(0, parsed.matchedFieldCount)
        assertFalse(parsed.isUsable)
        assertNull(parsed.energyKcal)
    }

    @Test
    @DisplayName("English labels are read as well as Italian ones")
    fun parsesEnglishLabel() {
        val parsed = parser.parse(
            """
            Nutrition facts per 100 g
            Energy 250 kcal
            Fat 10 g
            Saturates 4 g
            Carbohydrate 30 g
            Sugars 5 g
            Protein 12 g
            Salt 1 g
            """.trimIndent()
        )

        assertEquals(250.0, parsed.energyKcal)
        assertEquals(12.0, parsed.proteinGrams)
        assertEquals(30.0, parsed.carbsGrams)
        assertEquals(10.0, parsed.fatGrams)
        assertEquals(5.0, parsed.sugarGrams)
        assertEquals(1.0, parsed.saltGrams)
    }

    @Test
    @DisplayName("a per-serving label is normalised to per-100 g when the serving weight is known")
    fun perServing_normalisedToPer100g() {
        val parsed = parser.parse(
            """
            Nutrition facts per serving 30 g
            Energy 150 kcal
            Protein 4 g
            """.trimIndent()
        )

        assertEquals(NutritionLabelParser.Basis.PER_SERVING, parsed.basis)

        val per100g = parser.toPer100g(parsed, servingGrams = 30.0)

        assertEquals(NutritionLabelParser.Basis.PER_100G, per100g.basis)
        assertEquals(500.0, per100g.energyKcal!!, 0.01)
        assertEquals(13.333, per100g.proteinGrams!!, 0.01)
    }

    @Test
    @DisplayName("an unknown basis is never rescaled, because guessing would misreport every nutrient")
    fun unknownBasis_isNotRescaled() {
        val parsed = parser.parse("Energy 150 kcal Protein 4 g")

        val per100g = parser.toPer100g(parsed, servingGrams = 30.0)

        assertEquals(NutritionLabelParser.Basis.UNKNOWN, per100g.basis)
        assertEquals(150.0, per100g.energyKcal)
    }

    @Test
    @DisplayName("a per-serving label without a known weight is left untouched")
    fun perServingWithoutWeight_isNotRescaled() {
        val parsed = parser.parse("per serving Energy 150 kcal")

        val per100g = parser.toPer100g(parsed, servingGrams = null)

        assertEquals(150.0, per100g.energyKcal)
    }

    @Test
    @DisplayName("the serving size is extracted when the label states it")
    fun extractsServingSize() {
        val parsed = parser.parse("Serving size 30 g Energy 150 kcal")

        assertNotNull(parsed.servingSizeGrams)
        assertEquals(30.0, parsed.servingSizeGrams)
    }

    @Test
    @DisplayName("line breaks and repeated spaces inside the label do not break parsing")
    fun toleratesMessyWhitespace() {
        val parsed = parser.parse("Energy\n  524    kcal\n\nProtein  9,2 g")

        assertEquals(524.0, parsed.energyKcal)
        assertEquals(9.2, parsed.proteinGrams)
    }

    @Test
    @DisplayName("a value without a unit still yields the number rather than dropping the field")
    fun readsValueWithoutUnit() {
        val parsed = parser.parse("Protein: 9,2")

        assertEquals(9.2, parsed.proteinGrams)
    }

    @Test
    @DisplayName("a usable label is one with at least one recognised field")
    fun usableWhenAnyFieldMatched() {
        assertTrue(parser.parse("Salt 0,5 g").isUsable)
        assertFalse(parser.parse("").isUsable)
    }
}
