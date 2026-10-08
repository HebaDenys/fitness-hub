package io.github.hebadenys.fitnesshub

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.github.hebadenys.fitnesshub.core.healthconnect.HealthConnectManager
import io.github.hebadenys.fitnesshub.feature.dashboard.DashboardScreen
import io.github.hebadenys.fitnesshub.feature.dashboard.DashboardTopBar
import io.github.hebadenys.fitnesshub.feature.dashboard.DashboardUiModel
import io.github.hebadenys.fitnesshub.feature.dashboard.DashboardViewModel
import io.github.hebadenys.fitnesshub.feature.onboarding.OnboardingStore
import io.github.hebadenys.fitnesshub.feature.onboarding.OnboardingViewModel
import io.github.hebadenys.fitnesshub.feature.settings.SettingsScreen
import io.github.hebadenys.fitnesshub.feature.settings.SettingsUiModel
import io.github.hebadenys.fitnesshub.feature.settings.SettingsViewModel
import io.github.hebadenys.fitnesshub.ui.state.ScreenState
import io.github.hebadenys.fitnesshub.ui.theme.FitnessHubTheme
import io.github.hebadenys.fitnesshub.ui.theme.PerformanceTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "en-rUS-w360dp-h720dp")
@LooperMode(LooperMode.Mode.PAUSED)
class SettingsNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun dashboard(state: MutableStateFlow<ScreenState<DashboardUiModel>>): DashboardViewModel =
        mock<DashboardViewModel>().also {
            whenever(it.health).thenReturn(HealthConnectManager(compose.activity))
            whenever(it.uiState).thenReturn(state)
        }

    private fun settings(): SettingsViewModel = mock<SettingsViewModel>().also {
        whenever(it.uiState).thenReturn(MutableStateFlow<ScreenState<SettingsUiModel>>(
            ScreenState.Content(SettingsUiModel(
                isClientAvailable = false,
                hasAnyPermission = false,
                historyGranted = false,
                metricStatuses = emptyList()
            ))
        ))
    }

    @Composable
    private fun App(dashboard: DashboardViewModel, settings: SettingsViewModel) {
        FitnessHubTheme {
            FitnessHubAppRoot(
                dashboardContent = { openSettings ->
                    DashboardScreen(onNavigateToSettings = openSettings, viewModel = dashboard)
                },
                settingsContent = { back, navigate ->
                    SettingsScreen(
                        onBack = back,
                        onNavigateToHealthConnect = {},
                        onNavigateToXiaomi = {},
                        onNavigateToScale = {},
                        onNavigateToInsights = {},
                        onNavigateToAi = {},
                        onNavigateToProfile = { navigate("settings/profile") },
                        onNavigateToManualBody = {},
                        viewModel = settings
                    )
                },
                profileContent = { back ->
                    Column {
                        Text("Profile route")
                        Button(onClick = back) { Text("Back to settings") }
                    }
                }
            )
        }
    }

    @Test fun gearIsAvailableInLoadingEmptyPermissionAndErrorStates() {
        val state = MutableStateFlow<ScreenState<DashboardUiModel>>(ScreenState.Loading)
        val vm = dashboard(state)
        var opened = 0
        compose.setContent {
            FitnessHubTheme {
                PerformanceTheme {
                    DashboardScreen(onNavigateToSettings = { opened++ }, viewModel = vm)
                }
            }
        }
        val states = listOf(
            ScreenState.Loading, ScreenState.Empty(),
            ScreenState.PermissionMissing(), ScreenState.Error(message = "Synthetic error")
        )
        states.forEach { next ->
            compose.runOnIdle { state.value = next }
            compose.onNodeWithTag("dashboard_settings").assertIsDisplayed().assertIsEnabled().performClick()
        }
        compose.runOnIdle { assertEquals(states.size, opened) }
    }

    @Test fun syncingNeverHidesOrDisablesSettings() {
        val syncing = mutableStateOf(false)
        var opened = 0
        compose.setContent {
            FitnessHubTheme {
                PerformanceTheme {
                    DashboardTopBar(syncing.value, onSync = {}, onNavigateToSettings = { opened++ })
                }
            }
        }
        compose.onNodeWithTag("dashboard_settings").performClick()
        compose.runOnIdle { syncing.value = true }
        compose.onNodeWithTag("dashboard_settings").assertIsDisplayed().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(2, opened) }
    }

    @Test fun realSettingsOpensAndBackRestoresDashboardWithoutResettingSetup() {
        val store = OnboardingStore(compose.activity.applicationContext)
        OnboardingViewModel(store).complete()
        val vm = dashboard(MutableStateFlow(ScreenState.Empty()))
        val settings = settings()
        compose.setContent { App(vm, settings) }

        repeat(2) {
            compose.onNodeWithTag("main_navigation").assertExists()
            compose.onNodeWithTag("dashboard_settings").performClick()
            compose.onNodeWithTag("settings_hub").assertExists()
            compose.onNodeWithTag("main_navigation").assertDoesNotExist()
            compose.onNodeWithContentDescription("Navigate back").performClick()
            compose.onNodeWithTag("dashboard_settings").assertIsDisplayed()
        }
        compose.runOnIdle {
            assertTrue(OnboardingViewModel(OnboardingStore(compose.activity.applicationContext)).completed.value)
        }
    }

    @Test fun profileRouteReturnsToSettingsRatherThanRestartingOnboarding() {
        val vm = dashboard(MutableStateFlow(ScreenState.Empty()))
        val settings = settings()
        compose.setContent { App(vm, settings) }
        compose.onNodeWithTag("dashboard_settings").performClick()
        compose.onNodeWithTag("settings_profile").performClick()
        compose.onNodeWithText("Profile route").assertExists()
        compose.onNodeWithText("Back to settings").performClick()
        compose.onNodeWithTag("settings_hub").assertExists()
        compose.onNodeWithContentDescription("Navigate back").performClick()
        compose.onNodeWithTag("dashboard_settings").assertIsDisplayed()
    }

    @Test fun settingsAndBackStackSurviveSavedStateRecreation() {
        val vm = dashboard(MutableStateFlow(ScreenState.Empty()))
        val settings = settings()
        val restoration = StateRestorationTester(compose)
        restoration.setContent { App(vm, settings) }

        compose.onNodeWithTag("dashboard_settings").performClick()
        compose.onNodeWithTag("settings_hub").assertExists()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("settings_hub").assertExists()
        compose.onNodeWithContentDescription("Navigate back").performClick()
        compose.onNodeWithTag("dashboard_settings").assertIsDisplayed()
        compose.onNodeWithTag("main_navigation").assertExists()
    }
}
