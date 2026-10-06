package io.github.hebadenys.fitnesshub.core.nutrition

/**
 * Parses nutrition facts out of raw OCR text captured from a product label.
 *
 * Labels vary wildly between producers, so the parser works on normalised
 * lines and is intentionally conservative: a nutrient is only returned when it
 * is actually found, and the caller stores it as unknown otherwise. Values
 * carry [PROVENANCE_LABEL_OCR] so the UI can show they are read from the label
 * rather than measured.
 */
object NutritionLabelParser {

    private val ENERGY = listOf(
        Regex("""\b(?:energy|energia|energ[yi]ici|calories|calorie)\b\D{0,15}?([\d.,]+)""", RegexOption.IGNORE_CASE),
        Regex("""([\d.,]+)\s*k(?:cal|cal|j|joule)\b""", RegexOption.IGNORE_CASE)
    )
    private val PROTEIN = listOf(
        Regex("""\b(protein|proteine|proteínas|eiweiß)\b\D{0,10}?([\d.,]+)""", RegexOption.IGNORE_CASE)
    )
    private val CARBS = listOf(
        Regex("""\b(carbohydrates?|carboidrati|carbidrati|kohlenhydrate)\b\D{0,15}?([\d.,]+)""", RegexOption.IGNORE_CASE)
    )
    private val SUGAR = listOf(
        Regex("""\b(sugars?|zuccheri|zucker)\b\D{0,10}?([\d.,]+)""", RegexOption.IGNORE_CASE)
    )
    private val FAT = listOf(
        Regex("""\b(fat|fats|grassi|lipidi|fett)\b\D{0,10}?([\d.,]+)""", RegexOption.IGNORE_CASE)
    )
    private val FIBER = listOf(
        Regex("""\b(fiber|fibre|fibra|ballaststoffe)\b\D{0,10}?([\d.,]+)""", RegexOption.IGNORE_CASE)
    )
    private val SALT = listOf(
        Regex("""\b(salt|sale|salz)\b\D{0,10}?([\d.,]+)""", RegexOption.IGNORE_CASE)
    )

    /** Serving basis: per 100 g, per 100 ml, or per serving. */
    private val PER_100 = Regex("""per\s*100\s*(g|gr|ml)\b""", RegexOption.IGNORE_CASE)
    private val PER_SERVING = Regex("""per\s*(serving|portion|rzportion)\b""", RegexOption.IGNORE_CASE)

    enum class Basis { PER_100G, PER_SERVING, UNKNOWN }

    data class Parsed(
        val energyKcal: Double?,
        val proteinGrams: Double?,
        val carbsGrams: Double?,
        val fatGrams: Double?,
        val sugarGrams: Double?,
        val fiberGrams: Double?,
        val saltGrams: Double?,
        val basis: Basis,
        val servingSizeGrams: Double?,
        val servingLabel: String? = null,
        val matchedFieldCount: Int
    ) {
        val isUsable: Boolean get() = matchedFieldCount > 0
    }

    fun parse(rawText: String): Parsed {
        val normalized = rawText.replace(' ', ' ').replace('\n', ' ').replace(Regex("\\s+"), " ")

        val basis = when {
            PER_100.containsMatchIn(normalized) -> Basis.PER_100G
            PER_SERVING.containsMatchIn(normalized) -> Basis.PER_SERVING
            else -> Basis.UNKNOWN
        }

        val values = listOf(
            firstNumber(ENERGY, normalized),
            firstNumber(PROTEIN, normalized),
            firstNumber(CARBS, normalized),
            firstNumber(FAT, normalized),
            firstNumber(SUGAR, normalized),
            firstNumber(FIBER, normalized),
            firstNumber(SALT, normalized)
        )
        val matched = values.count { it != null }

        return Parsed(
            energyKcal = values[0],
            proteinGrams = values[1],
            carbsGrams = values[2],
            fatGrams = values[3],
            sugarGrams = values[4],
            fiberGrams = values[5],
            saltGrams = values[6],
            basis = basis,
            servingSizeGrams = servingSizeGrams(normalized),
            matchedFieldCount = matched
        )
    }

    /**
     * Converts a parsed label to per-100 g values so totals are comparable
     * across foods. When the basis is unknown the values are returned as read,
     * because guessing a basis would silently misreport every nutrient.
     */
    fun toPer100g(parsed: Parsed, servingGrams: Double?): Parsed {
        val knownServing = servingGrams ?: parsed.servingSizeGrams
        if (parsed.basis != Basis.PER_SERVING || knownServing == null || knownServing <= 0.0) {
            return parsed
        }
        val factor = 100.0 / knownServing
        return parsed.copy(
            energyKcal = parsed.energyKcal?.times(factor),
            proteinGrams = parsed.proteinGrams?.times(factor),
            carbsGrams = parsed.carbsGrams?.times(factor),
            fatGrams = parsed.fatGrams?.times(factor),
            sugarGrams = parsed.sugarGrams?.times(factor),
            fiberGrams = parsed.fiberGrams?.times(factor),
            saltGrams = parsed.saltGrams?.times(factor),
            basis = Basis.PER_100G
        )
    }

    private val SERVING_SIZE = Regex("""(?:serving size|portion size|porzione)\s*[:\s]*([\d.,]+)\s*(g|gr|ml)""", RegexOption.IGNORE_CASE)

    private fun servingSizeGrams(text: String): Double? =
        SERVING_SIZE.find(text)?.let { match ->
            val amount = match.groupValues[1].toNumberOrNull()
            when (match.groupValues[2].lowercase()) {
                "g", "gr" -> amount
                "ml" -> amount
                else -> amount
            }
        }

    private fun firstNumber(patterns: List<Regex>, text: String): Double? =
        patterns.firstNotNullOfOrNull { pattern ->
            pattern.find(text)?.groupValues?.lastOrNull { it.isNotEmpty() }?.toNumberOrNull()
        }

    /** Handles both decimal comma and decimal dot, which labels use interchangeably. */
    private fun String.toNumberOrNull(): Double? =
        replace(",", ".").replace(" ", "").toDoubleOrNull()
}
