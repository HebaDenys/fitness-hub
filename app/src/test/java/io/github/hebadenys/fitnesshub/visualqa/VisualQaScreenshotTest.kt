package io.github.hebadenys.fitnesshub.visualqa

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import io.github.hebadenys.fitnesshub.core.body.CanonicalBodyData
import io.github.hebadenys.fitnesshub.core.body.CanonicalBodyDay
import io.github.hebadenys.fitnesshub.core.body.CanonicalBodyMetricResolver
import io.github.hebadenys.fitnesshub.core.body.CanonicalBodyRepository
import io.github.hebadenys.fitnesshub.core.database.DailyHealthEntity
import io.github.hebadenys.fitnesshub.core.database.HealthDao
import io.github.hebadenys.fitnesshub.core.healthconnect.HealthConnectManager
import io.github.hebadenys.fitnesshub.core.model.DailySummary
import io.github.hebadenys.fitnesshub.core.model.HealthMetrics
import io.github.hebadenys.fitnesshub.core.nutrition.NutritionBasis
import io.github.hebadenys.fitnesshub.core.nutrition.NutritionDailyEntity
import io.github.hebadenys.fitnesshub.core.nutrition.NutritionEntryWithFood
import io.github.hebadenys.fitnesshub.core.nutrition.NutritionPreferences
import io.github.hebadenys.fitnesshub.core.nutrition.NutritionRepository
import io.github.hebadenys.fitnesshub.core.sync.HealthSyncRepository
import io.github.hebadenys.fitnesshub.feature.body.BodyScreen
import io.github.hebadenys.fitnesshub.feature.body.BodyViewModel
import io.github.hebadenys.fitnesshub.feature.dashboard.DashboardScreen
import io.github.hebadenys.fitnesshub.feature.dashboard.DashboardViewModel
import io.github.hebadenys.fitnesshub.feature.nutrition.NutritionScreen
import io.github.hebadenys.fitnesshub.feature.nutrition.NutritionViewModel
import io.github.hebadenys.fitnesshub.feature.onboarding.OnboardingScreen
import io.github.hebadenys.fitnesshub.feature.settings.MetricPermissionStatus
import io.github.hebadenys.fitnesshub.feature.settings.SettingsHubContent
import io.github.hebadenys.fitnesshub.feature.settings.SettingsUiModel
import io.github.hebadenys.fitnesshub.ui.components.ChartPoint
import io.github.hebadenys.fitnesshub.ui.components.TimeRange
import io.github.hebadenys.fitnesshub.ui.components.TrendChart
import io.github.hebadenys.fitnesshub.ui.state.ScreenState
import io.github.hebadenys.fitnesshub.ui.state.ScreenStateHandler
import io.github.hebadenys.fitnesshub.ui.theme.FitnessHubTheme
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Instant
import java.time.LocalDate

/**
 * Layout/semantics QA across real production composables.
 *
 * Pixel screenshots are intentionally not asserted here: captureToImage timed
 * out in this unit-test environment in runs 37847324194 and 37847946367, with
 * both default and native Robolectric renderers. Physical/emulator screenshot
 * inspection remains a separate device gate.
 */
private fun bodyFixture(): CanonicalBodyData {
    val older = CanonicalBodyMetricResolver.Observation(
        CanonicalBodyMetricResolver.Metric.WEIGHT, 100.2, "kg",
        Instant.parse("2026-10-07T08:00:00Z"),
        CanonicalBodyMetricResolver.Source.SCALE,
        CanonicalBodyMetricResolver.Method.MEASURED,
        CanonicalBodyMetricResolver.Quality.VALID,
        "fixture-scale-old"
    )
    val latest = CanonicalBodyMetricResolver.Observation(
        CanonicalBodyMetricResolver.Metric.WEIGHT, 99.4, "kg",
        Instant.parse("2026-10-08T18:00:00Z"),
        CanonicalBodyMetricResolver.Source.MANUAL,
        CanonicalBodyMetricResolver.Method.MEASURED,
        CanonicalBodyMetricResolver.Quality.VALID,
        "fixture-manual"
    )
    val fat = CanonicalBodyMetricResolver.Observation(
        CanonicalBodyMetricResolver.Metric.BODY_FAT, 19.6, "%",
        Instant.parse("2026-10-08T18:00:00Z"),
        CanonicalBodyMetricResolver.Source.MANUAL,
        CanonicalBodyMetricResolver.Method.MEASURED,
        CanonicalBodyMetricResolver.Quality.VALID,
        "fixture-manual-fat"
    )
    val resolver = CanonicalBodyMetricResolver()
    val all = listOf(older, latest, fat)
    val resolved = resolver.resolve(all)
    return CanonicalBodyData(
        resolved[CanonicalBodyMetricResolver.Metric.WEIGHT],
        resolved[CanonicalBodyMetricResolver.Metric.BODY_FAT],
        resolver.timeline(all, CanonicalBodyMetricResolver.Metric.WEIGHT),
        resolver.timeline(all, CanonicalBodyMetricResolver.Metric.BODY_FAT),
        listOf(
            CanonicalBodyDay(LocalDate.of(2026, 10, 8), latest, fat),
            CanonicalBodyDay(LocalDate.of(2026, 10, 7), older, null)
        )
    )
}

private fun settingsFixture() = SettingsUiModel(
    isClientAvailable = true,
    hasAnyPermission = true,
    historyGranted = true,
    metricStatuses = HealthMetrics.ALL.map {
        MetricPermissionStatus(it, it in setOf(HealthMetrics.STEPS, HealthMetrics.SLEEP, HealthMetrics.WEIGHT))
    },
    observedOrigins = listOf("fixture.health.source"),
    lastLocalSyncMillis = Instant.parse("2026-10-08T18:00:00Z").toEpochMilli()
)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "en-rUS-w360dp-h720dp")
@LooperMode(LooperMode.Mode.PAUSED)
class VisualQaCompactSemanticsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun dashboardComposesAtCompactWidthWithSyntheticData() {
        val repo = mock<HealthSyncRepository>()
        whenever(repo.observeDaily()).thenReturn(flowOf(listOf(
            DailyHealthEntity(
                date = "2026-10-08", steps = 9842, activeCalories = 612.0,
                sleepMinutes = 438, restingHeartRate = 58, syncedAt = 1,
                dataOrigins = "fixture.health.source"
            ),
            DailyHealthEntity(
                date = "2026-10-07", steps = 8100, activeCalories = 550.0,
                sleepMinutes = 420, restingHeartRate = 60, syncedAt = 1
            )
        )))
        val body = mock<CanonicalBodyRepository>()
        whenever(body.observe()).thenReturn(flowOf(bodyFixture()))
        val vm = DashboardViewModel(HealthConnectManager(compose.activity), repo, body)

        compose.setContent {
            FitnessHubTheme(darkTheme = false) {
                DashboardScreen(onNavigateToSettings = {}, viewModel = vm)
            }
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("dashboard_title").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("dashboard_title").assertExists()
        compose.onNodeWithText("99.4 kg").assertExists()
        compose.onNodeWithText("Steps").assertExists()
    }

    @Test fun bodyComposesDarkAndManualEntryIsAvailableWithoutHealthConnect() {
        val body = mock<CanonicalBodyRepository>()
        whenever(body.observe()).thenReturn(flowOf(bodyFixture()))
        val vm = BodyViewModel(HealthConnectManager(compose.activity), body, mock<HealthDao>())

        compose.setContent {
            FitnessHubTheme(darkTheme = true) {
                BodyScreen(onNavigateToSettings = {}, viewModel = vm)
            }
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("body_add_measurement").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("body_add_measurement").assertExists()
        compose.onNodeWithTag("body_add_measurement").assertIsEnabled()
        compose.onNode(hasText("99.4 kg") and hasAnyAncestor(hasTestTag("body_latest_weight"))).assertIsDisplayed()
    }

    @Test fun nutritionComposesAndRemoteCatalogIsExplicitlyDisabled() {
        val repo = mock<NutritionRepository>()
        whenever(repo.observeEntries(any())).thenReturn(flowOf(listOf(
            NutritionEntryWithFood(
                id = 1, foodId = 1, date = LocalDate.now().toString(), mealType = "LUNCH",
                servings = 1.0, servingGrams = 150.0, loggedAt = 1,
                name = "Synthetic rice bowl", brand = "Fixture",
                nutrientBasis = NutritionBasis.PER_SERVING.storedValue,
                energyKcal = 520.0, proteinGrams = 28.0, carbsGrams = 62.0,
                fatGrams = 16.0, sugarGrams = 4.0, fiberGrams = 6.0, saltGrams = 1.2
            )
        )))
        whenever(repo.observeDaily(any())).thenReturn(flowOf(
            NutritionDailyEntity(
                date = LocalDate.now().toString(), energyKcal = 520.0,
                proteinGrams = 28.0, carbsGrams = 62.0, fatGrams = 16.0,
                sugarGrams = 4.0, entryCount = 1
            )
        ))
        val vm = NutritionViewModel(repo, NutritionPreferences(compose.activity.applicationContext))

        compose.setContent { FitnessHubTheme { NutritionScreen(viewModel = vm) } }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Synthetic rice bowl").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Synthetic rice bowl").assertExists()
        compose.onNodeWithTag("nutrition_catalog_disabled").assertHasNoClickAction()
    }

    @Test fun interactiveChartExposesSelectedValuesAndSources() {
        compose.setContent {
            FitnessHubTheme {
                TrendChart(
                    title = "Synthetic weight trend",
                    points = listOf(
                        ChartPoint(LocalDate.of(2026,10,4), 101.2, DailySummary.PROVENANCE_MEASURED, "Scale"),
                        ChartPoint(LocalDate.of(2026,10,6), null),
                        ChartPoint(LocalDate.of(2026,10,8), 99.4, DailySummary.PROVENANCE_MEASURED, "Manual")
                    ),
                    selectedRange = TimeRange.SEVEN_DAYS,
                    onRangeSelected = {},
                    valueSuffix = "kg"
                )
            }
        }
        compose.onNodeWithText("Source: Manual").assertExists()
        compose.onNodeWithTag("chart_point_selector")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
        compose.onNodeWithText("Source: Scale").assertExists()
    }

    @Test fun emptyStateRemainsDistinct() {
        compose.setContent {
            FitnessHubTheme {
                ScreenStateHandler<Unit>(state = ScreenState.Empty(), content = {})
            }
        }
        compose.onNodeWithText("No health data available").assertExists()
    }

    @Test fun errorStateRemainsDistinct() {
        compose.setContent {
            FitnessHubTheme {
                ScreenStateHandler<Unit>(
                    state = ScreenState.Error(message = "Synthetic offline failure"),
                    content = {}
                )
            }
        }
        compose.onNodeWithText("Something went wrong").assertExists()
        compose.onNodeWithText("Synthetic offline failure").assertExists()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "en-rUS-w840dp-h900dp")
@LooperMode(LooperMode.Mode.PAUSED)
class VisualQaWideDarkSemanticsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun sourcesHubComposesWideDarkAndUnavailableAiCannotBeOpened() {
        compose.setContent {
            FitnessHubTheme(darkTheme = true) { SettingsHubContent(settingsFixture()) }
        }
        compose.onNodeWithTag("settings_hub").assertExists()
        compose.onNodeWithTag("settings_hub").performScrollToNode(hasTestTag("source_health_connect"))
        compose.onNodeWithTag("source_health_connect").assertExists()
        compose.onNodeWithTag("settings_hub").performScrollToNode(hasTestTag("source_xiaomi"))
        compose.onNodeWithTag("source_xiaomi").assertExists()
        compose.onNodeWithTag("settings_hub").performScrollToNode(hasTestTag("source_scale"))
        compose.onNodeWithTag("source_scale").assertExists()
        compose.onNodeWithTag("settings_hub").performScrollToNode(hasTestTag("settings_ai"))
        compose.onNodeWithTag("settings_ai").assertIsNotEnabled()
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "en-rUS-w360dp-h720dp")
@LooperMode(LooperMode.Mode.PAUSED)
class VisualQaLargeTextSemanticsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun onboardingActionsRemainReachableAtLargeTextScale() {
        compose.setContent {
            FitnessHubTheme {
                val base = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(base.density, 1.5f)) {
                    OnboardingScreen(onContinue = {}, onSkip = {})
                }
            }
        }
        compose.onNodeWithTag("onboarding").performScrollToNode(hasTestTag("onboarding_skip"))
        compose.onNodeWithTag("onboarding_skip").assertIsEnabled()
        compose.onNodeWithTag("onboarding_continue").assertExists()
    }
}
