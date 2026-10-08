package io.github.hebadenys.fitnesshub.feature.candidate

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.github.hebadenys.fitnesshub.core.model.DailySummary
import io.github.hebadenys.fitnesshub.feature.nutrition.NutritionDateHeader
import io.github.hebadenys.fitnesshub.ui.components.ChartPoint
import io.github.hebadenys.fitnesshub.ui.components.TimeRange
import io.github.hebadenys.fitnesshub.ui.components.TrendChart
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "en-rUS-w360dp-h720dp")
@LooperMode(LooperMode.Mode.PAUSED)
class CandidateUxTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun chartExposesLatestDetailAndAccessiblePointSelection() {
        compose.setContent {
            MaterialTheme {
                TrendChart(
                    title = "Weight",
                    points = listOf(
                        ChartPoint(LocalDate.of(2026, 10, 7), 100.0, DailySummary.PROVENANCE_MEASURED, "Scale"),
                        ChartPoint(LocalDate.of(2026, 10, 8), 99.0, DailySummary.PROVENANCE_MEASURED, "Health Connect")
                    ),
                    selectedRange = TimeRange.SEVEN_DAYS,
                    onRangeSelected = {},
                    valueSuffix = "kg"
                )
            }
        }

        compose.onNodeWithText("Source: Health Connect").assertExists()
        compose.onNodeWithTag("chart_point_selector").performSemanticsAction(SemanticsActions.SetProgress) { action ->
            action(0f)
        }
        compose.onNodeWithText("Source: Scale").assertExists()
    }

    @Test fun nutritionDayNavigationCanMoveBackForwardAndReturnToday() {
        var previous = 0
        var next = 0
        var today = 0
        compose.setContent {
            MaterialTheme {
                NutritionDateHeader(
                    date = LocalDate.now().minusDays(1),
                    onPrevious = { previous++ },
                    onNext = { next++ },
                    onToday = { today++ }
                )
            }
        }

        compose.onNodeWithContentDescription("Previous day").performClick()
        compose.onNodeWithContentDescription("Next day").performClick()
        compose.onNodeWithText("Return to today").performClick()
        compose.runOnIdle {
            assertEquals(1, previous)
            assertEquals(1, next)
            assertEquals(1, today)
        }
    }
}
