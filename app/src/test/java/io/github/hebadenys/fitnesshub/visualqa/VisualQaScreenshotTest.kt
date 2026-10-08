package io.github.hebadenys.fitnesshub.visualqa

import android.app.Application
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
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
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.LocalDate

private fun bodyFixture(): CanonicalBodyData {
    val weightOld = CanonicalBodyMetricResolver.Observation(
        CanonicalBodyMetricResolver.Metric.WEIGHT, 100.2, "kg",
        Instant.parse("2026-10-07T08:00:00Z"),
        CanonicalBodyMetricResolver.Source.SCALE,
        CanonicalBodyMetricResolver.Method.MEASURED,
        CanonicalBodyMetricResolver.Quality.VALID,
        "fixture-scale-old"
    )
    val weight = CanonicalBodyMetricResolver.Observation(
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
    val all = listOf(weightOld, weight, fat)
    val resolved = resolver.resolve(all)
    return CanonicalBodyData(
        latestWeight = resolved[CanonicalBodyMetricResolver.Metric.WEIGHT],
        latestBodyFat = resolved[CanonicalBodyMetricResolver.Metric.BODY_FAT],
        weightTimeline = resolver.timeline(all, CanonicalBodyMetricResolver.Metric.WEIGHT),
        bodyFatTimeline = resolver.timeline(all, CanonicalBodyMetricResolver.Metric.BODY_FAT),
        days = listOf(
            CanonicalBodyDay(LocalDate.of(2026, 10, 8), weight, fat),
            CanonicalBodyDay(LocalDate.of(2026, 10, 7), weightOld, null)
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

private fun saveScreenshot(rule: androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>, name: String) {
    rule.waitForIdle()
    val bitmap = rule.onRoot(useUnmergedTree = true).captureToImage().asAndroidBitmap()
    val directory = File(requireNotNull(System.getProperty("fitnesshub.visualQaDir")))
    check(directory.exists() || directory.mkdirs()) { "Could not create visual QA directory" }
    val file = File(directory, name)
    FileOutputStream(file).use { output ->
        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
    }
    check(file.length() > 1_000L) { "Screenshot was unexpectedly small: $name" }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "en-rUS-w360dp-h720dp")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VisualQaCompactTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun dashboardCompactLight() {
        val repo = mock<HealthSyncRepository>()
        whenever(repo.observeDaily()).thenReturn(flowOf(listOf(
            DailyHealthEntity(
                date = "2026-10-08", steps = 9842, activeCalories = 612.0,
                sleepMinutes = 438, restingHeartRate = 58, syncedAt = 1,
                dataOrigins = "fixture.health.source"
            ),
            DailyHealthEntity(date = "2026-10-07", steps = 8100, activeCalories = 550.0, sleepMinutes = 420, restingHeartRate = 60, syncedAt = 1)
        )))
        val body = mock<CanonicalBodyRepository>()
        whenever(body.observe()).thenReturn(flowOf(bodyFixture()))
        val vm = DashboardViewModel(HealthConnectManager(compose.activity), repo, body)

        compose.setContent {
            FitnessHubTheme(darkTheme = false) {
                DashboardScreen(onNavigateToSettings = {}, viewModel = vm)
            }
        }
        compose.waitForIdle()
        saveScreenshot(compose, "dashboard-compact-light.png")
    }

    @Test fun bodyCompactDark() {
        val body = mock<CanonicalBodyRepository>()
        whenever(body.observe()).thenReturn(flowOf(bodyFixture()))
        val dao = mock<HealthDao>()
        val vm = BodyViewModel(HealthConnectManager(compose.activity), body, dao)

        compose.setContent {
            FitnessHubTheme(darkTheme = true) {
                BodyScreen(onNavigateToSettings = {}, viewModel = vm)
            }
        }
        compose.waitForIdle()
        saveScreenshot(compose, "body-compact-dark.png")
    }

    @Test fun nutritionCompactLight() {
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

        compose.setContent {
            FitnessHubTheme(darkTheme = false) { NutritionScreen(viewModel = vm) }
        }
        compose.waitForIdle()
        saveScreenshot(compose, "nutrition-compact-light.png")
    }

    @Test fun interactiveChartSelectedPoint() {
        compose.setContent {
            FitnessHubTheme(darkTheme = false) {
                TrendChart(
                    title = "Synthetic weight trend",
                    points = listOf(
                        ChartPoint(LocalDate.of(2026,10,4), 101.2, DailySummary.PROVENANCE_MEASURED, "Scale"),
                        ChartPoint(LocalDate.of(2026,10,5), 100.7, DailySummary.PROVENANCE_MEASURED, "Scale"),
                        ChartPoint(LocalDate.of(2026,10,6), null),
                        ChartPoint(LocalDate.of(2026,10,7), 100.0, DailySummary.PROVENANCE_MEASURED, "Health Connect"),
                        ChartPoint(LocalDate.of(2026,10,8), 99.4, DailySummary.PROVENANCE_MEASURED, "Manual")
                    ),
                    selectedRange = TimeRange.SEVEN_DAYS,
                    onRangeSelected = {},
                    valueSuffix = "kg"
                )
            }
        }
        saveScreenshot(compose, "chart-interactive.png")
    }

    @Test fun emptyAndErrorStates() {
        compose.setContent {
            FitnessHubTheme {
                ScreenStateHandler<Unit>(state = ScreenState.Empty(), content = {})
            }
        }
        saveScreenshot(compose, "state-empty.png")
        compose.setContent {
            FitnessHubTheme {
                ScreenStateHandler<Unit>(state = ScreenState.Error(message = "Synthetic offline failure"), content = {})
            }
        }
        saveScreenshot(compose, "state-error.png")
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "en-rUS-w840dp-h900dp")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VisualQaWideDarkTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun sourcesWideDark() {
        compose.setContent {
            FitnessHubTheme(darkTheme = true) {
                SettingsHubContent(model = settingsFixture())
            }
        }
        saveScreenshot(compose, "sources-wide-dark.png")
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "en-rUS-w360dp-h720dp")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VisualQaLargeTextTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun onboardingCompactLargeText() {
        compose.setContent {
            FitnessHubTheme(darkTheme = false) {
                val base = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(base.density, 1.5f)) {
                    OnboardingScreen(onContinue = {}, onSkip = {})
                }
            }
        }
        saveScreenshot(compose, "onboarding-compact-large-text.png")
    }
}
