package io.github.hebadenys.fitnesshub.feature.dashboard

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import io.github.hebadenys.fitnesshub.R
import io.github.hebadenys.fitnesshub.core.model.DailySummary
import io.github.hebadenys.fitnesshub.ui.components.BaselineDelta
import io.github.hebadenys.fitnesshub.ui.components.TimeRange
import io.github.hebadenys.fitnesshub.ui.state.ScreenState
import io.github.hebadenys.fitnesshub.ui.theme.FitnessHubTheme
import io.github.hebadenys.fitnesshub.ui.theme.PerformanceTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "en-rUS-w360dp-h720dp")
@LooperMode(LooperMode.Mode.PAUSED)
class DashboardPerformanceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun model(day: DailySummary): DashboardUiModel = DashboardUiModel(
        latest = day, previous = null, recent = listOf(day), latestWeight = null,
        stepsDelta = BaselineDelta.None, activeCaloriesDelta = BaselineDelta.None,
        sleepDelta = BaselineDelta.None, restingHrDelta = BaselineDelta.None,
        weightDelta = BaselineDelta.None,
        chartPoints = dashboardStepPoints(listOf(day), TimeRange.SEVEN_DAYS),
        selectedRange = TimeRange.SEVEN_DAYS
    )

    @Test fun staleDailyDataIsLabelledAndEveryDashboardShortcutWorks() {
        var steps = 0
        var body = 0
        var meals = 0
        val day = DailySummary(LocalDate.now().minusDays(2), steps = 0, sleepMinutes = 444)
        compose.setContent {
            FitnessHubTheme {
                PerformanceTheme {
                    DashboardReadyContent(model(day), { body++ }, { meals++ }, { steps++ })
                }
            }
        }
        compose.onNodeWithText("Latest available").assertExists()
        compose.onNodeWithText(day.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))).assertExists()
        compose.onNodeWithTag("dashboard_steps").performClick()
        compose.onNodeWithTag("dashboard_add_body").performScrollTo().performClick()
        compose.onNodeWithTag("dashboard_nutrition").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, steps)
            assertEquals(1, body)
            assertEquals(1, meals)
        }
    }

    @Test fun permissionEmptyStateKeepsManualConnectionsAndPermissionActionsAtDoubleFontScale() {
        var body = 0
        var connections = 0
        var permissions = 0
        compose.setContent {
            FitnessHubTheme {
                PerformanceTheme {
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                        DashboardWelcomeContent(true, { body++ }, { connections++ }, { permissions++ })
                    }
                }
            }
        }
        compose.onNodeWithTag("dashboard_add_body").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("dashboard_connections").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("dashboard_permissions").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(1, body)
            assertEquals(1, connections)
            assertEquals(1, permissions)
        }
    }

    @Test fun ordinaryEmptyStateDoesNotPretendPermissionsAreRequired() {
        compose.setContent {
            FitnessHubTheme { PerformanceTheme { DashboardWelcomeContent(false, {}, {}, {}) } }
        }
        compose.onNodeWithText("Start with your data").assertExists()
        compose.onNodeWithTag("dashboard_permissions").assertDoesNotExist()
        compose.onNodeWithTag("dashboard_add_body").assertIsEnabled()
    }

    @Test fun chartDistinguishesZeroMissingAndSourcesWithTapSliderAndList() {
        val end = LocalDate.of(2026, 10, 8)
        val first = end.minusDays(6)
        val points = dashboardStepPoints(listOf(
            DailySummary(first, steps = 0, dataOrigins = setOf("fixture.source")),
            DailySummary(end, steps = 6000)
        ), TimeRange.SEVEN_DAYS, end)
        compose.setContent {
            FitnessHubTheme {
                PerformanceTheme {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        DashboardStepsChart(points, TimeRange.SEVEN_DAYS, {})
                    }
                }
            }
        }
        compose.onNodeWithTag("steps_coverage").assertTextEquals("2 of 7 days have a step value")
        compose.onNodeWithTag("steps_bars").performScrollTo().performTouchInput { click(Offset(1f, center.y)) }
        compose.onNodeWithText("Daily record sources: fixture.source").assertExists()
        val zeroText = compose.activity.getString(
            R.string.dashboard_selected_steps,
            first.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)), "0"
        )
        compose.onNodeWithText(zeroText).assertExists()
        compose.onNodeWithTag("steps_day_selector").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(1f) }
        val missingText = compose.activity.getString(
            R.string.dashboard_selected_steps,
            first.plusDays(1).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
            compose.activity.getString(R.string.value_unavailable)
        )
        compose.onNodeWithText(missingText).assertExists()
        compose.onNodeWithTag("steps_list_toggle").performScrollTo().performClick()
        compose.onNodeWithTag("steps_day_$first").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("steps_day_${first.plusDays(1)}").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("steps_list_toggle").performScrollTo().performClick()
        compose.onNodeWithTag("steps_day_$first").assertDoesNotExist()
    }

    @Test fun missingPeriodDoesNotRenderNumericTotalOrZeroAxes() {
        val points = dashboardStepPoints(emptyList(), TimeRange.SEVEN_DAYS)
        compose.setContent {
            FitnessHubTheme {
                PerformanceTheme {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        DashboardStepsChart(points, TimeRange.SEVEN_DAYS, {})
                    }
                }
            }
        }
        compose.onNodeWithTag("steps_period_total").assertTextEquals(compose.activity.getString(R.string.value_unavailable))
        compose.onNodeWithTag("steps_bars").assertDoesNotExist()
        compose.onNodeWithTag("steps_coverage").assertTextEquals("0 of 7 days have a step value")
    }

    @Test fun detailRangeAndListSurviveSavedStateAndBackRemainsAvailable() {
        val vm = mock<DashboardViewModel>()
        whenever(vm.uiState).thenReturn(MutableStateFlow<ScreenState<DashboardUiModel>>(
            ScreenState.Content(model(DailySummary(LocalDate.now(), steps = 5000)))
        ))
        var back = 0
        var body = 0
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            FitnessHubTheme { PerformanceTheme { DashboardStepsScreen({ back++ }, { body++ }, vm) } }
        }
        compose.onNodeWithText("30D").performClick()
        compose.onNodeWithTag("steps_coverage").assertTextEquals("1 of 30 days have a step value")
        compose.onNodeWithTag("steps_list_toggle").performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("30D").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("steps_list_toggle").performScrollTo().assertTextContains("Hide daily data list")
        compose.onNodeWithTag("steps_add_body").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Navigate back").performClick()
        compose.runOnIdle { assertEquals(1, back); assertEquals(1, body) }
    }
}
