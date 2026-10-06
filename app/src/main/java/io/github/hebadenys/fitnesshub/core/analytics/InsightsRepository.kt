package io.github.hebadenys.fitnesshub.core.analytics

import io.github.hebadenys.fitnesshub.core.database.HealthDao
import io.github.hebadenys.fitnesshub.core.nutrition.NutritionDao
import io.github.hebadenys.fitnesshub.core.workout.WorkoutDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate

/**
 * A cross-domain insight, always carrying its own disclaimer.
 *
 * Every card is a statistical observation about how two series move together.
 * None establishes cause, and the advisory travels with the data so a caller
 * cannot render the headline number without it.
 */
data class InsightCard(
    val id: String,
    val title: String,
    val summary: String,
    val correlation: TrendAnalytics.Correlation?,
    val primarySeries: List<SmoothedPoint>,
    val sampleSize: Int
)

data class SmoothedPoint(val date: LocalDate, val value: Double?)

data class InsightsUiData(
    val cards: List<InsightCard>,
    val weightTrend: List<SmoothedPoint>,
    val intakeTrend: List<SmoothedPoint>
)

/**
 * Builds the cross-domain view.
 *
 * Fluid measurements are smoothed before being drawn, so day-to-day scale noise
 * does not read as real change. Correlation is computed on the raw daily values:
 * smoothing two series before correlating them would manufacture agreement the
 * data does not contain.
 */
class InsightsRepository(
    private val healthDao: HealthDao,
    private val nutritionDao: NutritionDao,
    private val workoutDao: WorkoutDao
) {

    fun observeInsights(): Flow<InsightsUiData> =
        combine(
            healthDao.observeDaily(),
            nutritionDao.observeAllDaily(),
            workoutDao.observeDailyVolume()
        ) { daily, nutrition, volume ->
            val index = linkedMapOf<LocalDate, MutableMap<String, Double?>>()
            fun row(date: LocalDate): MutableMap<String, Double?> =
                index.getOrPut(date) { linkedMapOf() }

            daily.forEach { entity ->
                val date = entity.date.toLocalDateOrNull() ?: return@forEach
                row(date)[KEY_WEIGHT] = entity.weightKg
                row(date)[KEY_SLEEP] = entity.sleepMinutes?.toDouble()
            }
            nutrition.forEach { totals ->
                val date = totals.date.toLocalDateOrNull() ?: return@forEach
                row(date)[KEY_INTAKE] = totals.energyKcal
            }
            volume.forEach { entry ->
                val date = entry.date.toLocalDateOrNull() ?: return@forEach
                row(date)[KEY_VOLUME] = entry.volume
            }

            val weightAndIntake = index.entries.sortedBy { it.key }.map { (date, values) ->
                Observation(date, values[KEY_WEIGHT], values[KEY_INTAKE])
            }
            val sleepAndVolume = index.entries.sortedBy { it.key }.map { (date, values) ->
                Observation(date, values[KEY_SLEEP], values[KEY_VOLUME])
            }

            InsightsUiData(
                cards = listOf(
                    InsightCard(
                        id = CARD_WEIGHT_VS_INTAKE,
                        title = "Weight vs logged calories",
                        summary = CORRELATION_DISCLAIMER,
                        correlation = TrendAnalytics.correlate(
                            weightAndIntake, { it.x }, { it.y }, "weight", "calories"
                        ),
                        primarySeries = smooth(weightAndIntake, { it.x }),
                        sampleSize = weightAndIntake.count { it.x != null && it.y != null }
                    ),
                    InsightCard(
                        id = CARD_SLEEP_VS_VOLUME,
                        title = "Sleep vs training volume",
                        summary = CORRELATION_DISCLAIMER,
                        correlation = TrendAnalytics.correlate(
                            sleepAndVolume, { it.x }, { it.y }, "sleep", "training volume"
                        ),
                        primarySeries = smooth(sleepAndVolume, { it.x }),
                        sampleSize = sleepAndVolume.count { it.x != null && it.y != null }
                    )
                ),
                weightTrend = smooth(weightAndIntake, { it.x }),
                intakeTrend = smooth(weightAndIntake, { it.y })
            )
        }

    /** Seven-day smoothing: enough to flatten daily scale noise without lagging a real trend. */
    private fun smooth(
        observations: List<Observation>,
        selector: (Observation) -> Double?,
        days: Int = DEFAULT_SMOOTHING_DAYS
    ): List<SmoothedPoint> {
        val carry = observations.map { Observation(it.date, selector(it), null) }
        val smoothed = TrendAnalytics.exponentialMovingAverage(carry, days) { it.x }
        return observations.map { SmoothedPoint(it.date, smoothed[it.date]) }
    }

    private fun String.toLocalDateOrNull(): LocalDate? = runCatching { LocalDate.parse(this) }.getOrNull()

    companion object {
        const val DEFAULT_SMOOTHING_DAYS = 7

        /**
         * Carried on every correlation card. Phrased as an invariant of the
         * finding rather than as advice, so it cannot be mistaken for a caveat.
         */
        const val CORRELATION_DISCLAIMER =
            "These two series move together across the days shown. That is an " +
                "association, not a cause: changing one would not necessarily " +
                "change the other."

        const val CARD_WEIGHT_VS_INTAKE = "weight_vs_intake"
        const val CARD_SLEEP_VS_VOLUME = "sleep_vs_volume"

        private const val KEY_WEIGHT = "weightKg"
        private const val KEY_INTAKE = "intakeKcal"
        private const val KEY_SLEEP = "sleepMinutes"
        private const val KEY_VOLUME = "volumeKg"
    }
}
