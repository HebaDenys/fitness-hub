package io.github.hebadenys.fitnesshub.feature.body

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import io.github.hebadenys.fitnesshub.core.body.CanonicalBodyMetricResolver.*
import io.github.hebadenys.fitnesshub.core.healthconnect.HealthConnectManager
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
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "en-rUS-w360dp-h720dp")
@LooperMode(LooperMode.Mode.PAUSED)
class BodyPerformanceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun empty() = BodyUiModel(null, null, emptyList(), BaselineDelta.None, BaselineDelta.None,
        emptyList(), TimeRange.SEVEN_DAYS, false, false)

    private fun vm(entry: MutableStateFlow<ManualBodyEntryState>): BodyViewModel = mock<BodyViewModel>().also {
        whenever(it.health).thenReturn(HealthConnectManager(compose.activity))
        whenever(it.uiState).thenReturn(MutableStateFlow<ScreenState<BodyUiModel>>(ScreenState.Content(empty())))
        whenever(it.manualEntryState).thenReturn(entry)
    }

    @Test fun exactSameDayEventsRemainSelectableAndZeroBodyFatIsVisible() {
        val firstAt = LocalDate.now().atTime(8,0).atZone(ZoneId.systemDefault()).toInstant()
        val first = Observation(Metric.WEIGHT, 70.0, "kg", firstAt, Source.MANUAL, Method.MEASURED, sourceId = "first")
        val last = first.copy(value = 70.4, measuredAt = firstAt.plusMillis(333), sourceId = "second")
        val fat = first.copy(metric = Metric.BODY_FAT, value = 0.0, unit = "%", sourceId = "fat")
        val model = empty().copy(latestWeight = last, latestBodyFat = fat,
            weightObservations = listOf(first, last), observations = listOf(last, first, fat))
        compose.setContent { FitnessHubTheme { PerformanceTheme { BodyReadyContent(model, {}, {}) } } }
        compose.onNodeWithTag("body_history_list").performScrollToNode(hasTestTag("body_event_count"))
        compose.onNodeWithTag("body_event_count").assertTextEquals("2 weight records in this period")
        compose.onNodeWithTag("body_history_list").performScrollToNode(hasTestTag("body_event_selector"))
        compose.onNodeWithTag("body_event_selector").performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
        compose.onNode(hasText(bodyObservationTime(first)) and hasAnyAncestor(hasTestTag("body_selected_event"))).assertExists()
        compose.onNodeWithTag("body_event_selector").performSemanticsAction(SemanticsActions.SetProgress) { it(1f) }
        compose.onNode(hasText(bodyObservationTime(last)) and hasAnyAncestor(hasTestTag("body_selected_event"))).assertExists()
        compose.onNodeWithTag("body_history_list").performScrollToNode(hasTestTag("body_record_2"))
        compose.onNode(hasText("0 %") and hasAnyAncestor(hasTestTag("body_record_2"))).assertExists()
    }

    @Test fun legacyDailySummaryShowsNoInventedClockTimeOrExactPlotPoint() {
        val date = LocalDate.now()
        val legacy = Observation(Metric.WEIGHT, 70.0, "kg", date.atStartOfDay(ZoneId.systemDefault()).toInstant(),
            Source.HEALTH_CONNECT, Method.UNKNOWN, sourceId = "hc-day:$date:weight")
        compose.setContent {
            FitnessHubTheme {
                PerformanceTheme {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        BodyMeasurementCard("Legacy weight", legacy)
                        BodyObservationChart(listOf(legacy), TimeRange.SEVEN_DAYS, {})
                    }
                }
            }
        }
        compose.onNodeWithText("Time unavailable · daily summary").assertExists()
        compose.onAllNodesWithText("00:00", substring = true).assertCountEquals(0)
        compose.onNodeWithTag("body_event_plot").assertDoesNotExist()
        compose.onNodeWithText("1 date-only summaries are in history and excluded from this exact-time chart.").assertExists()
    }

    @Test fun draftSurvivesRecreationAndBackCanCancelOrDiscardWithoutSaving() {
        val model = vm(MutableStateFlow(ManualBodyEntryState.Idle))
        var backs = 0
        val restoration = StateRestorationTester(compose)
        restoration.setContent { FitnessHubTheme { PerformanceTheme { ManualBodyEntryScreen({ backs++ }, model) } } }
        compose.onNodeWithTag("manual_weight").performTextReplacement("83,2")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("manual_weight").assertTextContains("83,2")
        compose.onNodeWithContentDescription("Navigate back").performClick()
        compose.onNodeWithText("Discard this unsaved measurement?").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(0, backs) }
        compose.onNodeWithContentDescription("Navigate back").performClick()
        compose.onNodeWithText("Discard changes").performClick()
        compose.runOnIdle {
            assertEquals(1, backs)
            verify(model, never()).saveManualMeasurement(any(), any(), any())
        }
    }

    @Test fun storageErrorKeepsDraftAndAnnouncesTheFailure() {
        val entry = MutableStateFlow<ManualBodyEntryState>(ManualBodyEntryState.Idle)
        val model = vm(entry)
        compose.setContent { FitnessHubTheme { PerformanceTheme { ManualBodyEntryScreen({}, model) } } }
        compose.onNodeWithTag("manual_weight").performTextReplacement("83,2")
        compose.runOnIdle { entry.value = ManualBodyEntryState.StorageError }
        compose.onNodeWithTag("manual_weight").assertTextContains("83,2")
        compose.onNodeWithTag("manual_notice").performScrollTo()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        compose.onNodeWithTag("manual_save").performScrollTo().assertIsEnabled()
    }

    @Test fun workingLocksTheDraftAndSuccessClearsItBeforeClosing() {
        val entry = MutableStateFlow<ManualBodyEntryState>(ManualBodyEntryState.Idle)
        val model = vm(entry)
        var backs = 0
        compose.setContent { FitnessHubTheme { PerformanceTheme { ManualBodyEntryScreen({ backs++ }, model) } } }
        compose.onNodeWithTag("manual_weight").performTextReplacement("83.2")
        compose.runOnIdle { entry.value = ManualBodyEntryState.Working }
        compose.onNodeWithTag("manual_weight").assertIsNotEnabled()
        compose.onNodeWithTag("manual_save").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithContentDescription("Navigate back").assertIsNotEnabled()
        compose.runOnIdle { entry.value = ManualBodyEntryState.Saved(7) }
        compose.onNodeWithTag("manual_weight").performScrollTo()
        val text = compose.onNodeWithTag("manual_weight").fetchSemanticsNode().config[SemanticsProperties.EditableText].text
        assertEquals("", text)
        compose.onNodeWithTag("manual_close").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, backs) }
    }

    @Test fun manualActionsRemainReachableAtDoubleFontScaleWithoutPermissions() {
        val model = vm(MutableStateFlow(ManualBodyEntryState.Idle))
        compose.setContent {
            FitnessHubTheme {
                PerformanceTheme {
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                        ManualBodyEntryScreen({}, model)
                    }
                }
            }
        }
        compose.onNodeWithTag("manual_save").performScrollTo().assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithTag("manual_close").performScrollTo().assertIsDisplayed().assertIsEnabled()
    }
}
